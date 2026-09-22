package paqroute;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Barrido de hiperparametros de ACS y GRASP-VNS, buscando la combinacion que maximiza la carga
 * maxima sostenible U* (la metrica del Experimento 2), de forma SEPARADA de los 20 bloques usados
 * en la comparacion final (Experimentos.java) para no afinar sobre el mismo conjunto que despues
 * se usa para comparar -- eso seria "tuning" con fuga de datos, tan invalido como retocar el
 * algoritmo a mano para forzar un resultado.
 *
 * Por eso los dias de afinacion se toman de un rango de fechas que Experimentos.java NO toco en la
 * corrida de los 20 bloques (ver README): por defecto 2026-09-01 a 2027-04-30, dejando 2027-05-01
 * en adelante -- que es donde cayeron los bloques aceptados y descartados de la comparacion final --
 * intacto para esa comparacion.
 *
 * Salida en --salida (por defecto afinacion/): afinacion_corridas.csv (una fila por dia probado con
 * cada combinacion) y afinacion_resumen.csv (promedio de U* por combinacion, ordenado de mejor a peor).
 */
public final class Afinacion {

    private static final Duration TICK = Duration.ofMinutes(30);

    private record Opciones(Path salida, LocalDate desde, LocalDate hasta, int numDias, int semillas,
                             int hilos, double lambdaMax) {}

    // Grilla chica: valores alrededor de los defaults del informe (ACS beta=2.0,q0=0.9; GRASP alfa=0.3,iter=5).
    private static final double[] GRID_BETA = {1.5, 2.0, 3.0};
    private static final double[] GRID_Q0 = {0.7, 0.9};
    private static final double[] GRID_ALFA_GRASP = {0.15, 0.3, 0.5};
    private static final int[] GRID_ITER_GRASP = {5, 10};

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);
        Opciones opt = parsearOpciones(args);
        Files.createDirectories(opt.salida());

        System.out.println("Leyendo datos de data/ventas, data/bloqueos y data/mantenimiento...");
        List<Pedido> todosPedidos = LectorPedidos.leerCarpeta(Path.of("data/ventas"));
        List<Bloqueo> bloqueos = LectorBloqueos.leerCarpeta(Path.of("data/bloqueos"));
        List<Mantenimiento> mantenimientos = LectorMantenimiento.leerCarpeta(Path.of("data/mantenimiento"));

        Map<LocalDate, List<Pedido>> pedidosPorDia = new TreeMap<>();
        for (Pedido p : todosPedidos) {
            LocalDate dia = p.momentoLlegada.toLocalDate();
            if (dia.isBefore(opt.desde()) || dia.isAfter(opt.hasta())) continue;
            pedidosPorDia.computeIfAbsent(dia, d -> new ArrayList<>()).add(p);
        }
        List<LocalDate> candidatos = new ArrayList<>(pedidosPorDia.keySet());
        candidatos.sort(Comparator.<LocalDate>comparingInt(d -> pedidosPorDia.get(d).size()).reversed());

        ExecutorService pool = Executors.newFixedThreadPool(opt.hilos());
        List<LocalDate> diasAfinacion = new ArrayList<>();
        List<String[]> corridas = new ArrayList<>(); // algoritmo, param_a, param_b, fecha_bloque, u_estrella

        try {
            // 1) Elegir dias de afinacion: los mas cargados del rango que sobreviven a lambda=1 con
            //    los parametros por defecto, con ambos algoritmos (mismo filtro que en Experimentos,
            //    pero aqui es solo para tener una base de comparacion valida, no la muestra del informe).
            for (LocalDate dia : candidatos) {
                if (diasAfinacion.size() >= opt.numDias()) break;
                List<Pedido> pedidosDia = pedidosPorDia.get(dia);
                LocalDateTime inicioBloque = dia.atStartOfDay();
                boolean sobreviveAcs = sobrevive(pool, pedidosDia, bloqueos, mantenimientos, inicioBloque,
                        Simulador.Algoritmo.ACS, Simulador.ParametrosAlgoritmo.DEFAULT, opt.semillas(), 1.0);
                boolean sobreviveGrasp = sobrevive(pool, pedidosDia, bloqueos, mantenimientos, inicioBloque,
                        Simulador.Algoritmo.GRASP_VNS, Simulador.ParametrosAlgoritmo.DEFAULT, opt.semillas(), 1.0);
                if (sobreviveAcs && sobreviveGrasp) {
                    diasAfinacion.add(dia);
                    System.out.println("Dia de afinacion aceptado: " + dia + " (" + pedidosDia.size() + " pedidos)");
                } else {
                    System.out.println("Descartado para afinacion (colapsa en baseline lambda=1): " + dia);
                }
            }
            if (diasAfinacion.size() < opt.numDias()) {
                System.out.printf("ADVERTENCIA: solo se encontraron %d de %d dias de afinacion.%n",
                        diasAfinacion.size(), opt.numDias());
            }

            // 2) Grilla ACS: beta x q0.
            for (double beta : GRID_BETA) {
                for (double q0 : GRID_Q0) {
                    var params = new Simulador.ParametrosAlgoritmo(5, 5, 1.0, beta, 0.1, q0, 0.3, 5);
                    for (LocalDate dia : diasAfinacion) {
                        double uEstrella = buscarUEstrella(pool, pedidosPorDia.get(dia), bloqueos, mantenimientos,
                                dia.atStartOfDay(), Simulador.Algoritmo.ACS, params, opt.semillas(), opt.lambdaMax());
                        corridas.add(new String[]{"ACS", fmt(beta), fmt(q0), dia.toString(), fmt(uEstrella)});
                        System.out.printf("  ACS beta=%.2f q0=%.2f %s -> U*=%.1f%n", beta, q0, dia, uEstrella);
                    }
                }
            }

            // 3) Grilla GRASP-VNS: alfaGRASP x maxIteracionesGRASP.
            for (double alfaGrasp : GRID_ALFA_GRASP) {
                for (int iterGrasp : GRID_ITER_GRASP) {
                    var params = new Simulador.ParametrosAlgoritmo(5, 5, 1.0, 2.0, 0.1, 0.9, alfaGrasp, iterGrasp);
                    for (LocalDate dia : diasAfinacion) {
                        double uEstrella = buscarUEstrella(pool, pedidosPorDia.get(dia), bloqueos, mantenimientos,
                                dia.atStartOfDay(), Simulador.Algoritmo.GRASP_VNS, params, opt.semillas(), opt.lambdaMax());
                        corridas.add(new String[]{"GRASP_VNS", fmt(alfaGrasp), String.valueOf(iterGrasp), dia.toString(), fmt(uEstrella)});
                        System.out.printf("  GRASP-VNS alfa=%.2f iter=%d %s -> U*=%.1f%n", alfaGrasp, iterGrasp, dia, uEstrella);
                    }
                }
            }
        } finally {
            pool.shutdown();
        }

        escribirCorridas(opt.salida().resolve("afinacion_corridas.csv"), corridas);
        escribirResumen(opt.salida().resolve("afinacion_resumen.csv"), corridas);

        System.out.println();
        System.out.println("Listo. Salida en: " + opt.salida().toAbsolutePath());
    }

    // ------------------------------------------------------------- Evaluacion de un dia+combo

    /** true si el bloque sobrevive con lambda dado (ninguna de las semillas colapsa). */
    private static boolean sobrevive(ExecutorService pool, List<Pedido> pedidosDia, List<Bloqueo> bloqueos,
                                      List<Mantenimiento> mantenimientos, LocalDateTime inicioBloque,
                                      Simulador.Algoritmo alg, Simulador.ParametrosAlgoritmo params,
                                      int semillas, double lambda) throws Exception {
        List<Future<Boolean>> futuros = new ArrayList<>();
        for (int s = 1; s <= semillas; s++) {
            long semilla = s;
            futuros.add(pool.submit(() -> {
                List<Pedido> copia = new ArrayList<>(pedidosDia.size());
                for (Pedido p : pedidosDia) copia.add(p.copiaEscalada(lambda));
                Simulador sim = new Simulador(copia, bloqueos, mantenimientos, TICK, alg, semilla, inicioBloque, params);
                return !sim.ejecutar().colapso;
            }));
        }
        boolean todasSobreviven = true;
        for (Future<Boolean> f : futuros) if (!f.get()) todasSobreviven = false;
        return todasSobreviven;
    }

    /** Duplicacion + biseccion (igual criterio que Experimentos), con los parametros del combo evaluado. */
    private static double buscarUEstrella(ExecutorService pool, List<Pedido> pedidosDia, List<Bloqueo> bloqueos,
                                           List<Mantenimiento> mantenimientos, LocalDateTime inicioBloque,
                                           Simulador.Algoritmo alg, Simulador.ParametrosAlgoritmo params,
                                           int semillas, double lambdaMax) throws Exception {
        int unidadesBloque = pedidosDia.stream().mapToInt(p -> p.cantidadTotal).sum();

        double sobreviveLambda = 1.0;
        double colapsaLambda = -1.0;
        double lambda = 2.0;
        while (lambda <= lambdaMax) {
            if (sobrevive(pool, pedidosDia, bloqueos, mantenimientos, inicioBloque, alg, params, semillas, lambda)) {
                sobreviveLambda = lambda;
                lambda *= 2.0;
            } else {
                colapsaLambda = lambda;
                break;
            }
        }
        if (colapsaLambda < 0) return sobreviveLambda * unidadesBloque; // cota inferior (no colapso ni en lambdaMax)

        while (colapsaLambda - sobreviveLambda >= 0.05) {
            double medio = (sobreviveLambda + colapsaLambda) / 2.0;
            if (sobrevive(pool, pedidosDia, bloqueos, mantenimientos, inicioBloque, alg, params, semillas, medio)) {
                sobreviveLambda = medio;
            } else {
                colapsaLambda = medio;
            }
        }
        return sobreviveLambda * unidadesBloque;
    }

    // ------------------------------------------------------------- CSVs

    private static String fmt(double d) {
        return String.format(Locale.US, "%.4f", d);
    }

    private static void escribirCorridas(Path archivo, List<String[]> corridas) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(archivo))) {
            w.println("algoritmo,param_a,param_b,fecha_bloque,u_estrella");
            for (String[] fila : corridas) w.println(String.join(",", fila));
        }
    }

    private static void escribirResumen(Path archivo, List<String[]> corridas) throws IOException {
        // Clave = algoritmo+param_a+param_b; promedia u_estrella sobre los dias de afinacion.
        Map<String, double[]> acumulado = new TreeMap<>(); // suma, cuenta
        Map<String, String[]> etiqueta = new TreeMap<>();
        for (String[] fila : corridas) {
            String clave = fila[0] + "|" + fila[1] + "|" + fila[2];
            double[] acc = acumulado.computeIfAbsent(clave, k -> new double[2]);
            acc[0] += Double.parseDouble(fila[4]);
            acc[1] += 1;
            etiqueta.putIfAbsent(clave, new String[]{fila[0], fila[1], fila[2]});
        }
        List<Map.Entry<String, double[]>> filas = new ArrayList<>(acumulado.entrySet());
        filas.sort((a, b) -> Double.compare(b.getValue()[0] / b.getValue()[1], a.getValue()[0] / a.getValue()[1]));

        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(archivo))) {
            w.println("algoritmo,param_a,param_b,u_estrella_promedio,dias_evaluados");
            for (Map.Entry<String, double[]> e : filas) {
                String[] et = etiqueta.get(e.getKey());
                double promedio = e.getValue()[0] / e.getValue()[1];
                w.printf(Locale.US, "%s,%s,%s,%.4f,%d%n", et[0], et[1], et[2], promedio, (int) e.getValue()[1]);
            }
        }
    }

    // ------------------------------------------------------------- Opciones de linea de comando

    private static Opciones parsearOpciones(String[] args) {
        Path salida = Path.of("afinacion");
        LocalDate desde = LocalDate.of(2026, 9, 1);
        LocalDate hasta = LocalDate.of(2027, 4, 30); // rango disjunto de los 20 bloques finales (2027-05-01 en adelante)
        int numDias = 5;
        int semillas = 2;
        int hilos = Runtime.getRuntime().availableProcessors();
        double lambdaMax = 64.0;

        for (String arg : args) {
            if (!arg.startsWith("--")) continue;
            int igual = arg.indexOf('=');
            String clave = igual < 0 ? arg.substring(2) : arg.substring(2, igual);
            String valor = igual < 0 ? "" : arg.substring(igual + 1);
            switch (clave) {
                case "salida" -> salida = Path.of(valor);
                case "desde" -> desde = LocalDate.parse(valor);
                case "hasta" -> hasta = LocalDate.parse(valor);
                case "dias" -> numDias = Integer.parseInt(valor);
                case "semillas" -> semillas = Integer.parseInt(valor);
                case "hilos" -> hilos = Integer.parseInt(valor);
                case "lambda-max" -> lambdaMax = Double.parseDouble(valor);
                default -> System.out.println("Opcion desconocida ignorada: --" + clave);
            }
        }
        return new Opciones(salida, desde, hasta, numDias, semillas, Math.max(1, hilos), lambdaMax);
    }
}
