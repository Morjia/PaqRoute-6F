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
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Experimento 2 (version final, tiempo hasta el colapso): genera N escenarios pareados
 * (misma fecha de inicio + misma semilla para ambos algoritmos) y corre cada uno con ACS
 * "estandar" (= ACS_mejorado: memoria de feromonas retencion 0.3 + busqueda local VND + tipo de
 * vehiculo ampliado, igual que Experimentos.PARAMS_ACS_MEJORADO) y con GRASP-VNS (parametros por
 * defecto actuales, alfa=0.5/iter=10), hasta el histórico completo, sin escalar carga (lambda=1).
 *
 * Cada escenario: flota libre y almacenes llenos en la fecha de inicio, se deja correr el
 * historico real desde ahi. La variable de interes es Tc = tiempo hasta el colapso (en dias); si
 * el escenario llega al final del historico disponible sin colapsar, Tc queda CENSURADO (se marca
 * censurado=true y Tc = dias hasta el ultimo pedido del historico).
 *
 * Esta clase SOLO genera los datos (no corre ninguna prueba estadistica) -- el analisis pareado
 * (Wilcoxon / log-rank si hay censura) se hace aparte, sobre el CSV de salida.
 *
 * Salida en --salida (por defecto resultados_exp2_colapso/): resultados_exp2_colapso.csv, una
 * fila por (escenario, algoritmo).
 */
public final class ExperimentoColapso {

    private static final Duration TICK = Duration.ofMinutes(30);

    private record Opciones(Path salida, LocalDate desde, LocalDate hasta, int escenarios,
                             long semillaMuestreo, int hilos) {}

    private record Fila(int escenarioId, LocalDate fechaInicio, String algoritmo, long semilla,
                         double tcDias, boolean censurado, int pedidosIngresados, int pedidosCompletadosATiempo,
                         int entregasTotales, int entregasParciales, double costoAcumuladoSoles,
                         double kilometrosRecorridos, int ticksSimulados, double taPromedioMs, long taMaximoMs) {}

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

        List<LocalDate> fechas = muestrearFechas(opt.desde(), opt.hasta(), opt.escenarios(), opt.semillaMuestreo());
        System.out.println("Escenarios (" + fechas.size() + "): " + fechas);

        // ACS "estandar" = ACS_mejorado (memoria 0.3 + busqueda local VND + tipo vehiculo ampliado),
        // igual que Experimentos.PARAMS_ACS_MEJORADO.
        var paramsAcs = Simulador.ParametrosAlgoritmo.DEFAULT
                .conMemoriaAcs(0.3).conBusquedaLocalAcs(true).conTipoVehiculoAmpliadoAcs(true);
        var paramsGrasp = Simulador.ParametrosAlgoritmo.DEFAULT; // alfa=0.5, iter=10

        ExecutorService pool = Executors.newFixedThreadPool(opt.hilos());
        List<Future<Fila>> futuros = new ArrayList<>();
        try {
            for (int i = 0; i < fechas.size(); i++) {
                int escenarioId = i + 1;
                LocalDate fecha = fechas.get(i);
                long semilla = escenarioId; // misma semilla para ambos algoritmos -> pareo por escenario
                futuros.add(pool.submit(correr(todosPedidos, bloqueos, mantenimientos, escenarioId, fecha,
                        Simulador.Algoritmo.ACS, "ACS", paramsAcs, semilla)));
                futuros.add(pool.submit(correr(todosPedidos, bloqueos, mantenimientos, escenarioId, fecha,
                        Simulador.Algoritmo.GRASP_VNS, "GRASP_VNS", paramsGrasp, semilla)));
            }
            List<Fila> filas = new ArrayList<>();
            for (Future<Fila> f : futuros) {
                Fila r = f.get();
                filas.add(r);
                System.out.printf(Locale.US, "  escenario %2d (%s) %-9s -> Tc=%.2f dias%s | pedidos=%d completados=%d costo=S/ %.0f km=%.0f%n",
                        r.escenarioId(), r.fechaInicio(), r.algoritmo(), r.tcDias(), r.censurado() ? " (censurado)" : "",
                        r.pedidosIngresados(), r.pedidosCompletadosATiempo(), r.costoAcumuladoSoles(), r.kilometrosRecorridos());
            }
            filas.sort(java.util.Comparator.<Fila>comparingInt(Fila::escenarioId).thenComparing(Fila::algoritmo));
            escribirCsv(opt.salida().resolve("resultados_exp2_colapso.csv"), filas);
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

    // ---------------------------------------------------------------- Una corrida (un escenario+algoritmo)

    private static Callable<Fila> correr(List<Pedido> todosPedidos, List<Bloqueo> bloqueos,
                                          List<Mantenimiento> mantenimientos, int escenarioId, LocalDate fechaInicio,
                                          Simulador.Algoritmo alg, String etiquetaAlg, Simulador.ParametrosAlgoritmo params,
                                          long semilla) {
        return () -> {
            LocalDateTime inicio = fechaInicio.atStartOfDay();
            List<Pedido> pedidosEscenario = new ArrayList<>();
            for (Pedido p : todosPedidos) {
                if (!p.momentoLlegada.isBefore(inicio)) pedidosEscenario.add(p.copiaEscalada(1.0));
            }
            Simulador sim = new Simulador(pedidosEscenario, bloqueos, mantenimientos, TICK, alg, semilla, inicio, params);
            ResultadoSimulacion r = sim.ejecutar();

            double tcDias;
            boolean censurado;
            if (r.colapso) {
                tcDias = Duration.between(inicio, r.momentoColapso).toMinutes() / 60.0 / 24.0;
                censurado = false;
            } else {
                LocalDateTime finHorizonte = pedidosEscenario.isEmpty()
                        ? inicio : pedidosEscenario.get(pedidosEscenario.size() - 1).momentoLlegada;
                tcDias = Duration.between(inicio, finHorizonte).toMinutes() / 60.0 / 24.0;
                censurado = true;
            }
            return new Fila(escenarioId, fechaInicio, etiquetaAlg, semilla, tcDias, censurado,
                    r.pedidosIngresados, r.pedidosCompletadosATiempo, r.entregasTotales, r.entregasParciales,
                    r.costoAcumuladoSoles, r.kilometrosRecorridos, r.ticksSimulados,
                    r.tiempoAlgoritmoPromedioMs(), r.tiempoAlgoritmoMaximoMs);
        };
    }

    // ---------------------------------------------------------------- CSV

    private static void escribirCsv(Path archivo, List<Fila> filas) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(archivo))) {
            w.println("escenario_id,fecha_inicio,algoritmo,semilla,tc_dias,censurado,pedidos_ingresados," +
                    "pedidos_completados_a_tiempo,entregas_totales,entregas_parciales,costo_acumulado_soles," +
                    "km_recorridos,ticks_simulados,ta_promedio_ms,ta_maximo_ms");
            for (Fila f : filas) {
                w.printf(Locale.US, "%d,%s,%s,%d,%.4f,%s,%d,%d,%d,%d,%.2f,%.2f,%d,%.3f,%d%n",
                        f.escenarioId(), f.fechaInicio(), f.algoritmo(), f.semilla(), f.tcDias(), f.censurado(),
                        f.pedidosIngresados(), f.pedidosCompletadosATiempo(), f.entregasTotales(), f.entregasParciales(),
                        f.costoAcumuladoSoles(), f.kilometrosRecorridos(), f.ticksSimulados(), f.taPromedioMs(), f.taMaximoMs());
            }
        }
    }

    // ---------------------------------------------------------------- Opciones de linea de comando

    private static Opciones parsearOpciones(String[] args) {
        Path salida = Path.of("resultados_exp2_colapso");
        LocalDate desde = LocalDate.of(2026, 1, 1);
        LocalDate hasta = LocalDate.of(2028, 6, 30);
        int escenarios = 30;
        long semillaMuestreo = 123;
        int hilos = Runtime.getRuntime().availableProcessors();

        for (String arg : args) {
            if (!arg.startsWith("--")) continue;
            int igual = arg.indexOf('=');
            String clave = igual < 0 ? arg.substring(2) : arg.substring(2, igual);
            String valor = igual < 0 ? "" : arg.substring(igual + 1);
            switch (clave) {
                case "salida" -> salida = Path.of(valor);
                case "desde" -> desde = LocalDate.parse(valor);
                case "hasta" -> hasta = LocalDate.parse(valor);
                case "escenarios" -> escenarios = Integer.parseInt(valor);
                case "semilla-muestreo" -> semillaMuestreo = Long.parseLong(valor);
                case "hilos" -> hilos = Integer.parseInt(valor);
                default -> System.out.println("Opcion desconocida ignorada: --" + clave);
            }
        }
        return new Opciones(salida, desde, hasta, escenarios, semillaMuestreo, Math.max(1, hilos));
    }
}
