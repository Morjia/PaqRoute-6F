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
 * Ejecutor por lotes del Informe de Diseno de Experimento: Experimento 1 (consumo de SLA) y
 * Experimento 2 (carga maxima sostenible U*), comparando ACS y GRASP-VNS sobre 20 bloques de un
 * dia (00:00-24:00) elegidos entre los mas cargados que ambos algoritmos procesan sin colapsar.
 *
 * Ver README.md ("Experimentos del Informe de Diseno de Experimento") para las opciones de linea
 * de comando y los archivos de salida. Corresponde al Anexo 3 del informe (items 2 y 4: modo de
 * bloque y ejecutor por lotes; los items 1, 3 y 5 -- hora de llegada/completado, factor de carga
 * lambda y correccion de semilla por paso -- ya viven en Pedido/RutaUtil/Simulador).
 */
public final class Experimentos {

    private static final Duration TICK = Duration.ofMinutes(30);
    // Los plazos mas largos (36h) pueden vencer hasta 36h despues del cierre de la ventana del bloque;
    // el Simulador ya cubre esto por si solo, avanzando el reloj hasta que se entrega todo o colapsa,
    // sin necesidad de un limite explicito aqui.

    private record Opciones(Path salida, int numBloques, int semillas, int hilos, LocalDate desde, LocalDate hasta,
                             int maxCandidatos, double lambdaMax, boolean soloExp1) {}

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

        Map<LocalDate, List<Pedido>> pedidosPorDia = agruparPorDia(todosPedidos, opt.desde(), opt.hasta());

        List<LocalDate> candidatos = new ArrayList<>(pedidosPorDia.keySet());
        candidatos.sort(Comparator.<LocalDate>comparingInt(d -> pedidosPorDia.get(d).size()).reversed());
        if (opt.maxCandidatos() > 0 && candidatos.size() > opt.maxCandidatos()) {
            candidatos = candidatos.subList(0, opt.maxCandidatos());
        }
        System.out.printf("Dias candidatos (ordenados por carga, %s a %s): %d%n", opt.desde(), opt.hasta(), candidatos.size());

        ExecutorService pool = Executors.newFixedThreadPool(opt.hilos());
        List<CorridaCsv> corridas = new ArrayList<>();
        List<Bloque> bloquesAceptados = new ArrayList<>();
        List<String[]> descartados = new ArrayList<>();

        try {
            for (LocalDate dia : candidatos) {
                if (bloquesAceptados.size() >= opt.numBloques()) break;

                List<Pedido> pedidosDia = pedidosPorDia.get(dia);
                LocalDateTime inicioBloque = dia.atStartOfDay();
                int unidadesBloque = pedidosDia.stream().mapToInt(p -> p.cantidadTotal).sum();

                // Corridas de seleccion = tambien las corridas del Experimento 1 (lambda = 1.0).
                Map<Simulador.Algoritmo, List<ResultadoCorrida>> porAlgoritmo = new TreeMap<>();
                boolean algunColapso = false;
                String motivoDescarte = null;
                for (Simulador.Algoritmo alg : Simulador.Algoritmo.values()) {
                    List<Future<ResultadoCorrida>> futuros = new ArrayList<>();
                    for (int s = 1; s <= opt.semillas(); s++) {
                        long semilla = s;
                        futuros.add(pool.submit(correr(pedidosDia, bloqueos, mantenimientos, inicioBloque, alg, semilla, 1.0)));
                    }
                    List<ResultadoCorrida> resultados = new ArrayList<>();
                    for (Future<ResultadoCorrida> f : futuros) resultados.add(f.get());
                    porAlgoritmo.put(alg, resultados);
                    for (ResultadoCorrida r : resultados) {
                        if (r.resultado.colapso) {
                            algunColapso = true;
                            motivoDescarte = alg + " colapsa (semilla " + r.semilla + ", " + r.resultado.momentoColapso + ")";
                        }
                    }
                }

                for (Map.Entry<Simulador.Algoritmo, List<ResultadoCorrida>> e : porAlgoritmo.entrySet()) {
                    for (ResultadoCorrida r : e.getValue()) {
                        corridas.add(CorridaCsv.de(1, dia, e.getKey(), r.semilla, 1.0, r.resultado, unidadesBloque));
                    }
                }

                if (algunColapso) {
                    descartados.add(new String[]{dia.toString(), motivoDescarte});
                    System.out.println("Descartado " + dia + ": " + motivoDescarte);
                    continue;
                }

                Bloque bloque = new Bloque(dia, inicioBloque, unidadesBloque, pedidosDia.size());
                bloque.metricaSlaPromedio.put(Simulador.Algoritmo.ACS, promedio(porAlgoritmo.get(Simulador.Algoritmo.ACS)));
                bloque.metricaSlaPromedio.put(Simulador.Algoritmo.GRASP_VNS, promedio(porAlgoritmo.get(Simulador.Algoritmo.GRASP_VNS)));
                bloquesAceptados.add(bloque);
                System.out.printf("Aceptado %s (bloque %d/%d, %d pedidos, %d unidades)%n",
                        dia, bloquesAceptados.size(), opt.numBloques(), pedidosDia.size(), unidadesBloque);
            }

            if (bloquesAceptados.size() < opt.numBloques()) {
                System.out.printf("ADVERTENCIA: solo se encontraron %d de %d bloques pedidos (agotados los candidatos).%n",
                        bloquesAceptados.size(), opt.numBloques());
            }

            if (!opt.soloExp1()) {
                for (Bloque bloque : bloquesAceptados) {
                    for (Simulador.Algoritmo alg : Simulador.Algoritmo.values()) {
                        LambdaEstrella le = buscarLambdaEstrella(pool, pedidosPorDia.get(bloque.dia), bloqueos,
                                mantenimientos, bloque.inicioBloque, alg, opt.semillas(), opt.lambdaMax(), corridas, bloque.unidades);
                        bloque.uEstrella.put(alg, le.lambdaEstrella * bloque.unidades);
                        bloque.lambdaEstrella.put(alg, le.lambdaEstrella);
                        System.out.printf("  U* %s %s: lambda*=%.3f -> U*=%.1f (monotonia %s)%n",
                                bloque.dia, alg, le.lambdaEstrella, le.lambdaEstrella * bloque.unidades,
                                le.monotoniaVerificada ? "OK" : "NO VERIFICADA");
                    }
                }
            }
        } finally {
            pool.shutdown();
        }

        escribirCorridas(opt.salida().resolve("resultados_corridas.csv"), corridas);
        escribirTabla2(opt.salida().resolve("tabla2_bloques.csv"), bloquesAceptados);
        escribirLargoExp1(opt.salida().resolve("resultados_bloques_exp1.csv"), bloquesAceptados);
        if (!opt.soloExp1()) {
            escribirLargoExp2(opt.salida().resolve("resultados_bloques_exp2.csv"), bloquesAceptados);
        }
        escribirDescartados(opt.salida().resolve("dias_descartados.csv"), descartados);

        System.out.println();
        System.out.println("Listo. Bloques aceptados: " + bloquesAceptados.size() + ", descartados: " + descartados.size());
        System.out.println("Salida en: " + opt.salida().toAbsolutePath());
    }

    // ---------------------------------------------------------------- Experimento 2: lambda*

    private record LambdaEstrella(double lambdaEstrella, boolean monotoniaVerificada) {}

    private static LambdaEstrella buscarLambdaEstrella(ExecutorService pool, List<Pedido> pedidosDia,
                                                         List<Bloqueo> bloqueos, List<Mantenimiento> mantenimientos,
                                                         LocalDateTime inicioBloque, Simulador.Algoritmo alg,
                                                         int semillas, double lambdaMax, List<CorridaCsv> corridas,
                                                         int unidadesBloque) throws Exception {
        double sobrevive = 1.0; // lambda = 1 ya se sabe que sobrevive (paso el filtro del Experimento 1).
        double colapsaEn = -1.0;
        double lambda = 2.0;
        while (lambda <= lambdaMax) {
            if (sobreviveLambda(pool, pedidosDia, bloqueos, mantenimientos, inicioBloque, alg, semillas, lambda, corridas, unidadesBloque)) {
                sobrevive = lambda;
                lambda *= 2.0;
            } else {
                colapsaEn = lambda;
                break;
            }
        }
        if (colapsaEn < 0) {
            // No coloapso ni siquiera en lambdaMax: se reporta lambdaMax como cota inferior de lambda*.
            return new LambdaEstrella(sobrevive, false);
        }

        // Biseccion entre el ultimo valor que sobrevive y el primero que colapsa.
        while (colapsaEn - sobrevive >= 0.05) {
            double medio = (sobrevive + colapsaEn) / 2.0;
            if (sobreviveLambda(pool, pedidosDia, bloqueos, mantenimientos, inicioBloque, alg, semillas, medio, corridas, unidadesBloque)) {
                sobrevive = medio;
            } else {
                colapsaEn = medio;
            }
        }

        // Verificacion de monotonia: lambda* + 0.1 y lambda* + 0.2 deberian colapsar tambien.
        boolean masPuntoUno = !sobreviveLambda(pool, pedidosDia, bloqueos, mantenimientos, inicioBloque, alg, semillas,
                sobrevive + 0.1, corridas, unidadesBloque);
        boolean masPuntoDos = !sobreviveLambda(pool, pedidosDia, bloqueos, mantenimientos, inicioBloque, alg, semillas,
                sobrevive + 0.2, corridas, unidadesBloque);
        return new LambdaEstrella(sobrevive, masPuntoUno && masPuntoDos);
    }

    /** Un lambda "sobrevive" solo si ninguna de las semillas colapsa; al primer colapso se dejan de correr las restantes. */
    private static boolean sobreviveLambda(ExecutorService pool, List<Pedido> pedidosDia, List<Bloqueo> bloqueos,
                                            List<Mantenimiento> mantenimientos, LocalDateTime inicioBloque,
                                            Simulador.Algoritmo alg, int semillas, double lambda,
                                            List<CorridaCsv> corridas, int unidadesBloque) throws Exception {
        List<Future<ResultadoCorrida>> futuros = new ArrayList<>();
        for (int s = 1; s <= semillas; s++) {
            long semilla = s;
            futuros.add(pool.submit(correr(pedidosDia, bloqueos, mantenimientos, inicioBloque, alg, semilla, lambda)));
        }
        boolean sobrevive = true;
        for (Future<ResultadoCorrida> f : futuros) {
            ResultadoCorrida r = f.get();
            synchronized (corridas) {
                corridas.add(CorridaCsv.de(2, inicioBloque.toLocalDate(), alg, r.semilla, lambda, r.resultado, unidadesBloque));
            }
            if (r.resultado.colapso) sobrevive = false;
        }
        return sobrevive;
    }

    // ---------------------------------------------------------------- Una corrida

    private record ResultadoCorrida(long semilla, ResultadoSimulacion resultado) {}

    private static Callable<ResultadoCorrida> correr(List<Pedido> pedidosDia, List<Bloqueo> bloqueos,
                                                       List<Mantenimiento> mantenimientos, LocalDateTime inicioBloque,
                                                       Simulador.Algoritmo alg, long semilla, double lambda) {
        return () -> {
            List<Pedido> pedidosCorrida = new ArrayList<>(pedidosDia.size());
            for (Pedido p : pedidosDia) pedidosCorrida.add(p.copiaEscalada(lambda));
            Simulador sim = new Simulador(pedidosCorrida, bloqueos, mantenimientos, TICK, alg, semilla, inicioBloque);
            return new ResultadoCorrida(semilla, sim.ejecutar());
        };
    }

    private static double promedio(List<ResultadoCorrida> corridas) {
        double suma = 0.0;
        for (ResultadoCorrida r : corridas) suma += r.resultado.consumoSlaPromedioPct;
        return suma / corridas.size();
    }

    // ---------------------------------------------------------------- Agrupar pedidos por dia

    private static Map<LocalDate, List<Pedido>> agruparPorDia(List<Pedido> pedidos, LocalDate desde, LocalDate hasta) {
        Map<LocalDate, List<Pedido>> mapa = new TreeMap<>();
        for (Pedido p : pedidos) {
            LocalDate dia = p.momentoLlegada.toLocalDate();
            if (dia.isBefore(desde) || dia.isAfter(hasta)) continue;
            mapa.computeIfAbsent(dia, d -> new ArrayList<>()).add(p);
        }
        return mapa;
    }

    // ---------------------------------------------------------------- Modelo de un bloque aceptado

    private static final class Bloque {
        final LocalDate dia;
        final LocalDateTime inicioBloque;
        final int unidades;
        final int totalPedidos;
        final Map<Simulador.Algoritmo, Double> metricaSlaPromedio = new TreeMap<>();
        final Map<Simulador.Algoritmo, Double> lambdaEstrella = new TreeMap<>();
        final Map<Simulador.Algoritmo, Double> uEstrella = new TreeMap<>();

        Bloque(LocalDate dia, LocalDateTime inicioBloque, int unidades, int totalPedidos) {
            this.dia = dia;
            this.inicioBloque = inicioBloque;
            this.unidades = unidades;
            this.totalPedidos = totalPedidos;
        }
    }

    // ---------------------------------------------------------------- CSVs

    /** Una fila del Anexo 1 (resultados_corridas.csv): una corrida = un (bloque, algoritmo, semilla, lambda). */
    private record CorridaCsv(int experimento, LocalDate fechaBloque, Simulador.Algoritmo algoritmo, long semilla,
                               double lambda, boolean colapso, double metricaSla, double costoPorPedido, int unidadesBloque) {
        static CorridaCsv de(int experimento, LocalDate fechaBloque, Simulador.Algoritmo algoritmo, long semilla,
                              double lambda, ResultadoSimulacion r, int unidadesBloque) {
            return new CorridaCsv(experimento, fechaBloque, algoritmo, semilla, lambda, r.colapso,
                    r.consumoSlaPromedioPct, r.costoPorPedido(), unidadesBloque);
        }
    }

    private static void escribirCorridas(Path archivo, List<CorridaCsv> corridas) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(archivo))) {
            w.println("experimento,fecha_bloque,algoritmo,semilla,lambda,colapso,metrica_sla,costo_por_pedido,unidades_bloque");
            for (CorridaCsv c : corridas) {
                w.printf(Locale.US, "%d,%s,%s,%d,%.4f,%s,%s,%s,%d%n",
                        c.experimento(), c.fechaBloque(), c.algoritmo(), c.semilla(), c.lambda(), c.colapso(),
                        c.colapso() ? "" : String.format(Locale.US, "%.4f", c.metricaSla()),
                        Double.isNaN(c.costoPorPedido()) ? "" : String.format(Locale.US, "%.4f", c.costoPorPedido()),
                        c.unidadesBloque());
            }
        }
    }

    private static void escribirTabla2(Path archivo, List<Bloque> bloques) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(archivo))) {
            w.println("FechaInicio,FechaFin,Unidades,TotalPedidos,Metrica_ACS,Metrica_GRASP,Diferencia");
            for (Bloque b : bloques) {
                double acs = b.metricaSlaPromedio.get(Simulador.Algoritmo.ACS);
                double grasp = b.metricaSlaPromedio.get(Simulador.Algoritmo.GRASP_VNS);
                w.printf(Locale.US, "%s,%s,%d,%d,%.4f,%.4f,%.4f%n",
                        b.dia, b.dia, b.unidades, b.totalPedidos, acs, grasp, acs - grasp);
            }
        }
    }

    private static void escribirLargoExp1(Path archivo, List<Bloque> bloques) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(archivo))) {
            w.println("fecha_bloque,algoritmo,valor");
            for (Bloque b : bloques) {
                for (Simulador.Algoritmo alg : Simulador.Algoritmo.values()) {
                    w.printf(Locale.US, "%s,%s,%.4f%n", b.dia, alg, b.metricaSlaPromedio.get(alg));
                }
            }
        }
    }

    private static void escribirLargoExp2(Path archivo, List<Bloque> bloques) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(archivo))) {
            w.println("fecha_bloque,algoritmo,valor");
            for (Bloque b : bloques) {
                for (Simulador.Algoritmo alg : Simulador.Algoritmo.values()) {
                    Double u = b.uEstrella.get(alg);
                    if (u != null) w.printf(Locale.US, "%s,%s,%.4f%n", b.dia, alg, u);
                }
            }
        }
    }

    private static void escribirDescartados(Path archivo, List<String[]> descartados) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(archivo))) {
            w.println("fecha,motivo");
            for (String[] fila : descartados) {
                w.printf("%s,\"%s\"%n", fila[0], fila[1].replace("\"", "'"));
            }
        }
    }

    // ---------------------------------------------------------------- Opciones de linea de comando

    private static Opciones parsearOpciones(String[] args) {
        Path salida = Path.of("resultados");
        int numBloques = 20;
        int semillas = 5;
        int hilos = Runtime.getRuntime().availableProcessors();
        LocalDate desde = LocalDate.of(2026, 9, 1);
        LocalDate hasta = LocalDate.of(2028, 12, 31);
        int maxCandidatos = -1;
        double lambdaMax = 64.0;
        boolean soloExp1 = false;

        for (String arg : args) {
            if (!arg.startsWith("--")) continue;
            int igual = arg.indexOf('=');
            String clave = igual < 0 ? arg.substring(2) : arg.substring(2, igual);
            String valor = igual < 0 ? "" : arg.substring(igual + 1);
            switch (clave) {
                case "salida" -> salida = Path.of(valor);
                case "bloques" -> numBloques = Integer.parseInt(valor);
                case "semillas" -> semillas = Integer.parseInt(valor);
                case "hilos" -> hilos = Integer.parseInt(valor);
                case "desde" -> desde = LocalDate.parse(valor);
                case "hasta" -> hasta = LocalDate.parse(valor);
                case "max-candidatos" -> maxCandidatos = Integer.parseInt(valor);
                case "lambda-max" -> lambdaMax = Double.parseDouble(valor);
                case "solo-exp1" -> soloExp1 = true;
                default -> System.out.println("Opcion desconocida ignorada: --" + clave);
            }
        }
        return new Opciones(salida, numBloques, semillas, Math.max(1, hilos), desde, hasta, maxCandidatos, lambdaMax, soloExp1);
    }
}
