package paqroute;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Igual que {@link Main} (escenario de colapso sobre TODO el historico, operacion continua), pero
 * repetido con varias semillas y con variantes de ACS con memoria de feromonas entre ticks. Main
 * corre una sola semilla (1); aqui se ve cuanto varia el momento del primer colapso entre semillas.
 *
 * Uso: java -cp out paqroute.MainSemillas [--semillas=5] [--memoria=0.3,0.6,0.9] [--hilos=N]
 * Salida: consola + exploracion_memoria/main_semillas.csv
 */
public final class MainSemillas {

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);
        int semillas = 5;
        int hilos = Math.min(8, Runtime.getRuntime().availableProcessors());
        List<Double> memorias = List.of();
        boolean incluirLocal = false;
        Double combinadoRetencion = null;
        boolean incluirTipoAmpliado = false;
        boolean incluirAcsMejorado = false;
        for (String arg : args) {
            if (!arg.startsWith("--")) continue;
            int igual = arg.indexOf('=');
            String clave = igual < 0 ? arg.substring(2) : arg.substring(2, igual);
            String valor = igual < 0 ? "" : arg.substring(igual + 1);
            switch (clave) {
                case "semillas" -> semillas = Integer.parseInt(valor);
                case "hilos" -> hilos = Integer.parseInt(valor);
                case "memoria" -> {
                    memorias = new ArrayList<>();
                    for (String s : valor.split(",")) if (!s.isBlank()) memorias.add(Double.parseDouble(s.trim()));
                }
                case "local" -> incluirLocal = true;
                case "combinado" -> combinadoRetencion = valor.isBlank() ? 0.3 : Double.parseDouble(valor);
                case "tipo-ampliado" -> incluirTipoAmpliado = true;
                case "acs-mejorado" -> incluirAcsMejorado = true;
                default -> System.out.println("Opcion desconocida ignorada: --" + clave);
            }
        }

        List<Pedido> base = LectorPedidos.leerCarpeta(Path.of("data/ventas"));
        List<Bloqueo> bloqueos = LectorBloqueos.leerCarpeta(Path.of("data/bloqueos"));
        List<Mantenimiento> mant = LectorMantenimiento.leerCarpeta(Path.of("data/mantenimiento"));
        System.out.printf("Pedidos: %d | semillas: %d | hilos: %d%n", base.size(), semillas, hilos);

        Map<String, Simulador.Algoritmo> algoritmoDe = new LinkedHashMap<>();
        Map<String, Simulador.ParametrosAlgoritmo> paramsDe = new LinkedHashMap<>();
        Simulador.ParametrosAlgoritmo def = Simulador.ParametrosAlgoritmo.DEFAULT;
        algoritmoDe.put("ACS", Simulador.Algoritmo.ACS);
        paramsDe.put("ACS", def);
        for (double r : memorias) {
            String et = String.format("ACS_memoria_%.1f", r);
            algoritmoDe.put(et, Simulador.Algoritmo.ACS);
            paramsDe.put(et, def.conMemoriaAcs(r));
        }
        if (incluirLocal) {
            algoritmoDe.put("ACS_local", Simulador.Algoritmo.ACS);
            paramsDe.put("ACS_local", def.conBusquedaLocalAcs(true));
        }
        if (combinadoRetencion != null) {
            String et = String.format("ACS_memoria_local_%.1f", combinadoRetencion);
            algoritmoDe.put(et, Simulador.Algoritmo.ACS);
            paramsDe.put(et, def.conMemoriaAcs(combinadoRetencion).conBusquedaLocalAcs(true));
        }
        if (incluirTipoAmpliado) {
            algoritmoDe.put("ACS_tipoAmpliado", Simulador.Algoritmo.ACS);
            paramsDe.put("ACS_tipoAmpliado", def.conTipoVehiculoAmpliadoAcs(true));
        }
        if (incluirAcsMejorado) {
            // Las 3 mejoras juntas: memoria (retencion 0.3) + busqueda local (VND) + tipo de vehiculo
            // ampliado. Mismos parametros que Experimentos.PARAMS_ACS_MEJORADO.
            algoritmoDe.put("ACS_mejorado", Simulador.Algoritmo.ACS);
            paramsDe.put("ACS_mejorado", def.conMemoriaAcs(0.3).conBusquedaLocalAcs(true).conTipoVehiculoAmpliadoAcs(true));
        }
        algoritmoDe.put("GRASP_VNS", Simulador.Algoritmo.GRASP_VNS);
        paramsDe.put("GRASP_VNS", def);

        LocalDateTime inicio = base.get(0).momentoLlegada; // igual que Main (constructor de 6 argumentos)
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, hilos));
        Map<String, List<Future<ResultadoSimulacion>>> futuros = new LinkedHashMap<>();
        try {
            for (String et : algoritmoDe.keySet()) {
                List<Future<ResultadoSimulacion>> lista = new ArrayList<>();
                for (int s = 1; s <= semillas; s++) {
                    final long semilla = s;
                    final String variante = et;
                    lista.add(pool.submit(() -> {
                        List<Pedido> copia = new ArrayList<>(base.size());
                        for (Pedido p : base) copia.add(p.copiaEscalada(1.0)); // Pedido es mutable: copia fresca
                        return new Simulador(copia, bloqueos, mant, Duration.ofMinutes(30),
                                algoritmoDe.get(variante), semilla, inicio, paramsDe.get(variante)).ejecutar();
                    }));
                }
                futuros.put(et, lista);
            }

            Files.createDirectories(Path.of("exploracion_memoria"));
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(Path.of("exploracion_memoria/main_semillas.csv")))) {
                w.println("variante,semilla,colapso,momento_colapso,pedidos_ingresados");
                for (Map.Entry<String, List<Future<ResultadoSimulacion>>> e : futuros.entrySet()) {
                    List<LocalDateTime> momentos = new ArrayList<>();
                    System.out.println();
                    System.out.println("== " + e.getKey() + " ==");
                    int s = 1;
                    for (Future<ResultadoSimulacion> f : e.getValue()) {
                        ResultadoSimulacion r = f.get();
                        System.out.printf("  semilla %d -> %s (pedidos ingresados: %d)%n", s,
                                r.colapso ? r.momentoColapso.toString() : "SIN COLAPSO", r.pedidosIngresados);
                        w.printf("%s,%d,%s,%s,%d%n", e.getKey(), s, r.colapso,
                                r.colapso ? r.momentoColapso : "", r.pedidosIngresados);
                        momentos.add(r.colapso ? r.momentoColapso : null);
                        s++;
                    }
                    momentos.sort(Comparator.nullsLast(Comparator.naturalOrder()));
                    LocalDateTime mediana = momentos.get(momentos.size() / 2);
                    System.out.println("  mediana del momento de colapso: " + (mediana == null ? "SIN COLAPSO" : mediana));
                }
            }
        } finally {
            pool.shutdown();
        }
    }
}
