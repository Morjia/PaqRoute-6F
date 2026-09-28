package paqroute;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Version 2 de la afinacion de hiperparametros (ver paqroute.Afinacion, version 1): la misma
 * grilla chica de ACS (beta x q0) y GRASP-VNS (alfaGRASP x iteraciones), pero optimizando el
 * TIEMPO HASTA EL COLAPSO (metrica del Experimento 2 nuevo, que reemplaza a U*) en vez de la
 * carga maxima sostenible U*.
 *
 * Un "escenario" aqui es una fecha de inicio (flota libre, almacenes llenos, sin pedidos
 * pendientes) desde la que se deja correr el historico real (lambda=1, sin escalar) hasta que
 * colapsa o se agota el historico disponible (censura). Es la misma logica que usaria el
 * Experimento 2 final, pero con menos escenarios (mas barato) porque esto es solo afinacion.
 *
 * Igual que en Afinacion.java, la muestra de afinacion (fechas de inicio) se toma de un rango
 * DISJUNTO del que usaria el Experimento 2 final, para no afinar sobre la misma muestra que
 * despues se reporta. Ademas se agrega una etapa de verificacion de generalizacion sobre un
 * segundo conjunto de escenarios, en un tercer rango de fechas.
 *
 * Salida en --salida (por defecto afinacion_colapso/):
 *   afinacion_colapso_corridas.csv     (grilla: una fila por combinacion+escenario)
 *   afinacion_colapso_resumen.csv      (grilla: Tc promedio por combinacion, de mejor a peor)
 *   afinacion_colapso_verificacion.csv (verificacion: original vs mejor combinacion, por escenario)
 */
public final class AfinacionColapso {

    private static final Duration TICK = Duration.ofMinutes(30);

    private record Opciones(Path salida, LocalDate tuningDesde, LocalDate tuningHasta,
                             LocalDate verifDesde, LocalDate verifHasta, int escenariosTuning,
                             int escenariosVerif, long semillaMuestreo, int hilos) {}

    // Misma grilla que Afinacion.java (version U*), para que el hallazgo sea comparable.
    private static final double[] GRID_BETA = {1.5, 2.0, 3.0};
    private static final double[] GRID_Q0 = {0.7, 0.9};
    private static final double[] GRID_ALFA_GRASP = {0.15, 0.3, 0.5};
    private static final int[] GRID_ITER_GRASP = {5, 10};

    private record Combo(String algoritmo, String paramA, String paramB, Simulador.ParametrosAlgoritmo params) {}

    private record ResultadoEscenario(LocalDate fechaInicio, double tcDias, boolean censurado) {}

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);
        Opciones opt = parsearOpciones(args);
        Files.createDirectories(opt.salida());

        System.out.println("Leyendo datos de data/ventas, data/bloqueos y data/mantenimiento...");
        List<Pedido> todosPedidos = LectorPedidos.leerCarpeta(Path.of("data/ventas"));
        List<Bloqueo> bloqueos = LectorBloqueos.leerCarpeta(Path.of("data/bloqueos"));
        List<Mantenimiento> mantenimientos = LectorMantenimiento.leerCarpeta(Path.of("data/mantenimiento"));
        System.out.printf("Pedidos: %d, bloqueos: %d, mantenimiento: %d%n",
                todosPedidos.size(), bloqueos.size(), mantenimientos.size());

        List<LocalDate> fechasTuning = muestrearFechas(opt.tuningDesde(), opt.tuningHasta(),
                opt.escenariosTuning(), opt.semillaMuestreo());
        List<LocalDate> fechasVerif = muestrearFechas(opt.verifDesde(), opt.verifHasta(),
                opt.escenariosVerif(), opt.semillaMuestreo() + 1);
        System.out.println("Escenarios de afinacion (tuning): " + fechasTuning);
        System.out.println("Escenarios de verificacion: " + fechasVerif);

        // ACS "estandar" = ACS_mejorado (memoria retencion 0.3 + busqueda local VND + tipo de
        // vehiculo ampliado), igual que Experimentos.PARAMS_ACS_MEJORADO. La grilla de beta/q0 se
        // aplica ENCIMA de esas 3 mejoras, no sobre el ACS original sin mejoras.
        List<Combo> combosAcs = new ArrayList<>();
        for (double beta : GRID_BETA) {
            for (double q0 : GRID_Q0) {
                var params = new Simulador.ParametrosAlgoritmo(5, 5, 1.0, beta, 0.1, q0, 0.5, 10)
                        .conMemoriaAcs(0.3).conBusquedaLocalAcs(true).conTipoVehiculoAmpliadoAcs(true);
                combosAcs.add(new Combo("ACS", fmt(beta), fmt(q0), params));
            }
        }
        List<Combo> combosGrasp = new ArrayList<>();
        for (double alfaGrasp : GRID_ALFA_GRASP) {
            for (int iterGrasp : GRID_ITER_GRASP) {
                var params = new Simulador.ParametrosAlgoritmo(5, 5, 1.0, 2.0, 0.1, 0.9, alfaGrasp, iterGrasp);
                combosGrasp.add(new Combo("GRASP_VNS", fmt(alfaGrasp), String.valueOf(iterGrasp), params));
            }
        }

        ExecutorService pool = Executors.newFixedThreadPool(opt.hilos());
        List<String[]> filasCorridas = new ArrayList<>();
        List<String[]> filasVerif = new ArrayList<>();

        try {
            // ---------------- Etapa 1: grilla, optimizando Tc promedio sobre fechasTuning ----------------
            Map_ acumulado = new Map_();
            for (Combo c : combosAcs) evaluarCombo(pool, todosPedidos, bloqueos, mantenimientos, fechasTuning,
                    Simulador.Algoritmo.ACS, c, filasCorridas, acumulado);
            for (Combo c : combosGrasp) evaluarCombo(pool, todosPedidos, bloqueos, mantenimientos, fechasTuning,
                    Simulador.Algoritmo.GRASP_VNS, c, filasCorridas, acumulado);

            escribirCorridas(opt.salida().resolve("afinacion_colapso_corridas.csv"), filasCorridas);
            List<String[]> resumen = escribirResumen(opt.salida().resolve("afinacion_colapso_resumen.csv"), acumulado);

            Combo mejorAcs = mejorCombo(combosAcs, acumulado);
            Combo mejorGrasp = mejorCombo(combosGrasp, acumulado);
            System.out.println();
            System.out.println("Mejor combinacion ACS (por Tc promedio): beta=" + mejorAcs.paramA() + " q0=" + mejorAcs.paramB());
            System.out.println("Mejor combinacion GRASP-VNS (por Tc promedio): alfa=" + mejorGrasp.paramA() + " iter=" + mejorGrasp.paramB());

            // ---------------- Etapa 2: verificacion de generalizacion sobre fechasVerif ----------------
            // "Original" de ACS = ACS_mejorado con beta/q0 de linea base (2.0/0.9), NO el ACS sin
            // mejoras: las 3 mejoras (memoria+VND+tipo ampliado) se tratan como el ACS estandar.
            var paramsAcsOriginal = new Simulador.ParametrosAlgoritmo(5, 5, 1.0, 2.0, 0.1, 0.9, 0.5, 10)
                    .conMemoriaAcs(0.3).conBusquedaLocalAcs(true).conTipoVehiculoAmpliadoAcs(true);
            // "Original" de GRASP-VNS = el valor original del informe (alfa=0.3, iter=5), ANTES de
            // la afinacion por U* que lo cambio a 0.5/10 (que es Simulador.ParametrosAlgoritmo.DEFAULT).
            var paramsGraspOriginal = new Simulador.ParametrosAlgoritmo(5, 5, 1.0, 2.0, 0.1, 0.9, 0.3, 5);

            verificar(pool, todosPedidos, bloqueos, mantenimientos, fechasVerif, Simulador.Algoritmo.ACS,
                    "original(beta=2.0,q0=0.9,mejoras=on)", paramsAcsOriginal, filasVerif);
            verificar(pool, todosPedidos, bloqueos, mantenimientos, fechasVerif, Simulador.Algoritmo.ACS,
                    "candidato(beta=" + mejorAcs.paramA() + ",q0=" + mejorAcs.paramB() + ",mejoras=on)", mejorAcs.params(), filasVerif);
            verificar(pool, todosPedidos, bloqueos, mantenimientos, fechasVerif, Simulador.Algoritmo.GRASP_VNS,
                    "original(alfa=0.3,iter=5)", paramsGraspOriginal, filasVerif);
            verificar(pool, todosPedidos, bloqueos, mantenimientos, fechasVerif, Simulador.Algoritmo.GRASP_VNS,
                    "candidato(alfa=" + mejorGrasp.paramA() + ",iter=" + mejorGrasp.paramB() + ")", mejorGrasp.params(), filasVerif);

            escribirVerificacion(opt.salida().resolve("afinacion_colapso_verificacion.csv"), filasVerif);
        } finally {
            pool.shutdown();
        }

        System.out.println();
        System.out.println("Listo. Salida en: " + opt.salida().toAbsolutePath());
    }

    // ---------------------------------------------------------------- Muestreo de fechas de inicio

    private static List<LocalDate> muestrearFechas(LocalDate desde, LocalDate hasta, int n, long semilla) {
        int rango = (int) (hasta.toEpochDay() - desde.toEpochDay()) + 1;
        Random rnd = new Random(semilla);
        java.util.LinkedHashSet<LocalDate> fechas = new java.util.LinkedHashSet<>();
        while (fechas.size() < n && fechas.size() < rango) {
            fechas.add(desde.plusDays(rnd.nextInt(rango)));
        }
        List<LocalDate> lista = new ArrayList<>(fechas);
        lista.sort(java.util.Comparator.naturalOrder());
        return lista;
    }

    // ---------------------------------------------------------------- Una corrida (un escenario)

    private static Callable<ResultadoEscenario> correr(List<Pedido> todosPedidos, List<Bloqueo> bloqueos,
                                                         List<Mantenimiento> mantenimientos, LocalDate fechaInicio,
                                                         Simulador.Algoritmo alg, Simulador.ParametrosAlgoritmo params,
                                                         long semilla) {
        return () -> {
            LocalDateTime inicio = fechaInicio.atStartOfDay();
            List<Pedido> pedidosEscenario = new ArrayList<>();
            for (Pedido p : todosPedidos) {
                if (!p.momentoLlegada.isBefore(inicio)) pedidosEscenario.add(p.copiaEscalada(1.0));
            }
            Simulador sim = new Simulador(pedidosEscenario, bloqueos, mantenimientos, TICK, alg, semilla, inicio, params);
            ResultadoSimulacion r = sim.ejecutar();
            if (r.colapso) {
                double dias = Duration.between(inicio, r.momentoColapso).toMinutes() / 60.0 / 24.0;
                return new ResultadoEscenario(fechaInicio, dias, false);
            } else {
                LocalDateTime finHorizonte = pedidosEscenario.isEmpty()
                        ? inicio : pedidosEscenario.get(pedidosEscenario.size() - 1).momentoLlegada;
                double dias = Duration.between(inicio, finHorizonte).toMinutes() / 60.0 / 24.0;
                return new ResultadoEscenario(fechaInicio, dias, true); // censurado: no colapso en el historico disponible
            }
        };
    }

    // ---------------------------------------------------------------- Etapa 1: grilla

    /** Acumulador simple algoritmo|paramA|paramB -> (suma Tc, cuenta, censurados). */
    private static final class Map_ extends java.util.TreeMap<String, double[]> {}

    private static void evaluarCombo(ExecutorService pool, List<Pedido> todosPedidos, List<Bloqueo> bloqueos,
                                      List<Mantenimiento> mantenimientos, List<LocalDate> fechas,
                                      Simulador.Algoritmo alg, Combo c, List<String[]> filasCorridas,
                                      Map_ acumulado) throws Exception {
        List<Future<ResultadoEscenario>> futuros = new ArrayList<>();
        for (int i = 0; i < fechas.size(); i++) {
            long semilla = i + 1;
            futuros.add(pool.submit(correr(todosPedidos, bloqueos, mantenimientos, fechas.get(i), alg, c.params(), semilla)));
        }
        String clave = c.algoritmo() + "|" + c.paramA() + "|" + c.paramB();
        double[] acc = acumulado.computeIfAbsent(clave, k -> new double[3]); // suma, cuenta, censurados
        for (Future<ResultadoEscenario> f : futuros) {
            ResultadoEscenario r = f.get();
            filasCorridas.add(new String[]{c.algoritmo(), c.paramA(), c.paramB(), r.fechaInicio().toString(),
                    fmt(r.tcDias()), String.valueOf(r.censurado())});
            acc[0] += r.tcDias();
            acc[1] += 1;
            if (r.censurado()) acc[2] += 1;
            System.out.printf(Locale.US, "  %s %s=%s %s=%s inicio=%s -> Tc=%.1f dias%s%n",
                    c.algoritmo(), c.algoritmo().equals("ACS") ? "beta" : "alfa", c.paramA(),
                    c.algoritmo().equals("ACS") ? "q0" : "iter", c.paramB(), r.fechaInicio(), r.tcDias(),
                    r.censurado() ? " (censurado)" : "");
        }
    }

    private static Combo mejorCombo(List<Combo> combos, Map_ acumulado) {
        Combo mejor = null;
        double mejorProm = Double.NEGATIVE_INFINITY;
        for (Combo c : combos) {
            double[] acc = acumulado.get(c.algoritmo() + "|" + c.paramA() + "|" + c.paramB());
            double prom = acc[0] / acc[1];
            if (prom > mejorProm) {
                mejorProm = prom;
                mejor = c;
            }
        }
        return mejor;
    }

    // ---------------------------------------------------------------- Etapa 2: verificacion

    private static void verificar(ExecutorService pool, List<Pedido> todosPedidos, List<Bloqueo> bloqueos,
                                   List<Mantenimiento> mantenimientos, List<LocalDate> fechas,
                                   Simulador.Algoritmo alg, String etiquetaConfig, Simulador.ParametrosAlgoritmo params,
                                   List<String[]> filasVerif) throws Exception {
        List<Future<ResultadoEscenario>> futuros = new ArrayList<>();
        for (int i = 0; i < fechas.size(); i++) {
            long semilla = i + 1;
            futuros.add(pool.submit(correr(todosPedidos, bloqueos, mantenimientos, fechas.get(i), alg, params, semilla)));
        }
        double suma = 0;
        int n = 0;
        for (Future<ResultadoEscenario> f : futuros) {
            ResultadoEscenario r = f.get();
            filasVerif.add(new String[]{alg.toString(), etiquetaConfig, r.fechaInicio().toString(),
                    fmt(r.tcDias()), String.valueOf(r.censurado())});
            suma += r.tcDias();
            n++;
            System.out.printf(Locale.US, "  [verif] %s %s inicio=%s -> Tc=%.1f dias%s%n",
                    alg, etiquetaConfig, r.fechaInicio(), r.tcDias(), r.censurado() ? " (censurado)" : "");
        }
        System.out.printf(Locale.US, "  [verif] %s %s -> Tc promedio = %.1f dias (%d escenarios)%n%n",
                alg, etiquetaConfig, suma / n, n);
    }

    // ---------------------------------------------------------------- CSVs

    private static String fmt(double d) {
        return String.format(Locale.US, "%.4f", d);
    }

    private static void escribirCorridas(Path archivo, List<String[]> filas) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(archivo))) {
            w.println("algoritmo,param_a,param_b,fecha_inicio,tc_dias,censurado");
            for (String[] fila : filas) w.println(String.join(",", fila));
        }
    }

    private static List<String[]> escribirResumen(Path archivo, Map_ acumulado) throws IOException {
        List<Map.Entry<String, double[]>> filas = new ArrayList<>(acumulado.entrySet());
        filas.sort((a, b) -> Double.compare(b.getValue()[0] / b.getValue()[1], a.getValue()[0] / a.getValue()[1]));
        List<String[]> resultado = new ArrayList<>();
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(archivo))) {
            w.println("algoritmo,param_a,param_b,tc_promedio_dias,escenarios,censurados");
            for (Map.Entry<String, double[]> e : filas) {
                String[] partes = e.getKey().split("\\|");
                double promedio = e.getValue()[0] / e.getValue()[1];
                w.printf(Locale.US, "%s,%s,%s,%.4f,%d,%d%n", partes[0], partes[1], partes[2], promedio,
                        (int) e.getValue()[1], (int) e.getValue()[2]);
                resultado.add(new String[]{partes[0], partes[1], partes[2], fmt(promedio)});
            }
        }
        return resultado;
    }

    private static void escribirVerificacion(Path archivo, List<String[]> filas) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(archivo))) {
            w.println("algoritmo,configuracion,fecha_inicio,tc_dias,censurado");
            for (String[] fila : filas) w.println(String.join(",", fila));
        }
    }

    // ---------------------------------------------------------------- Opciones de linea de comando

    private static Opciones parsearOpciones(String[] args) {
        Path salida = Path.of("afinacion_colapso");
        LocalDate tuningDesde = LocalDate.of(2026, 9, 1);
        LocalDate tuningHasta = LocalDate.of(2027, 4, 30);
        LocalDate verifDesde = LocalDate.of(2027, 5, 1);
        LocalDate verifHasta = LocalDate.of(2028, 6, 30);
        int escenariosTuning = 2;
        int escenariosVerif = 4;
        long semillaMuestreo = 42;
        int hilos = Runtime.getRuntime().availableProcessors();

        for (String arg : args) {
            if (!arg.startsWith("--")) continue;
            int igual = arg.indexOf('=');
            String clave = igual < 0 ? arg.substring(2) : arg.substring(2, igual);
            String valor = igual < 0 ? "" : arg.substring(igual + 1);
            switch (clave) {
                case "salida" -> salida = Path.of(valor);
                case "tuning-desde" -> tuningDesde = LocalDate.parse(valor);
                case "tuning-hasta" -> tuningHasta = LocalDate.parse(valor);
                case "verif-desde" -> verifDesde = LocalDate.parse(valor);
                case "verif-hasta" -> verifHasta = LocalDate.parse(valor);
                case "escenarios-tuning" -> escenariosTuning = Integer.parseInt(valor);
                case "escenarios-verif" -> escenariosVerif = Integer.parseInt(valor);
                case "semilla-muestreo" -> semillaMuestreo = Long.parseLong(valor);
                case "hilos" -> hilos = Integer.parseInt(valor);
                default -> System.out.println("Opcion desconocida ignorada: --" + clave);
            }
        }
        return new Opciones(salida, tuningDesde, tuningHasta, verifDesde, verifHasta, escenariosTuning,
                escenariosVerif, semillaMuestreo, Math.max(1, hilos));
    }
}
