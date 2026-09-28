package paqroute;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Exploracion (NO forma parte de la comparacion final del informe): ACS con memoria de feromonas
 * + busqueda local COMBINADAS, comparado con cada mejora por separado, ACS original y GRASP-VNS,
 * en U* sobre los dias de afinacion (2026-09-01 a 2027-04-30, fuera de los 20 bloques finales).
 *
 * Mismo patron que ExploracionMemoriaAcs / ExploracionBusquedaLocalAcs. Retencion de memoria fija
 * en 0.3 (la que menos empeoro sola, ver exploracion_memoria/) -- combinarla con busqueda local
 * solo tiene sentido si esa retencion no es ya, por si sola, la peor opcion.
 *
 * Salida en --salida (por defecto exploracion_combinada/): combinada_corridas.csv y
 * combinada_resumen.csv.
 */
public final class ExploracionCombinadaAcs {

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);
        Path salida = Path.of("exploracion_combinada");
        LocalDate desde = LocalDate.of(2026, 9, 1);
        LocalDate hasta = LocalDate.of(2027, 4, 30);
        int numDias = 5;
        int semillas = 3;
        int hilos = Runtime.getRuntime().availableProcessors();
        double lambdaMax = 64.0;
        double retencion = 0.3;
        for (String arg : args) {
            if (!arg.startsWith("--")) continue;
            int igual = arg.indexOf('=');
            String clave = igual < 0 ? arg.substring(2) : arg.substring(2, igual);
            String valor = igual < 0 ? "" : arg.substring(igual + 1);
            switch (clave) {
                case "salida" -> salida = Path.of(valor);
                case "dias" -> numDias = Integer.parseInt(valor);
                case "semillas" -> semillas = Integer.parseInt(valor);
                case "hilos" -> hilos = Integer.parseInt(valor);
                case "desde" -> desde = LocalDate.parse(valor);
                case "hasta" -> hasta = LocalDate.parse(valor);
                case "retencion" -> retencion = Double.parseDouble(valor);
                default -> System.out.println("Opcion desconocida ignorada: --" + clave);
            }
        }
        Files.createDirectories(salida);

        List<Pedido> todos = LectorPedidos.leerCarpeta(Path.of("data/ventas"));
        List<Bloqueo> bloqueos = LectorBloqueos.leerCarpeta(Path.of("data/bloqueos"));
        List<Mantenimiento> mant = LectorMantenimiento.leerCarpeta(Path.of("data/mantenimiento"));

        Map<LocalDate, List<Pedido>> porDia = new TreeMap<>();
        for (Pedido p : todos) {
            LocalDate d = p.momentoLlegada.toLocalDate();
            if (d.isBefore(desde) || d.isAfter(hasta)) continue;
            porDia.computeIfAbsent(d, k -> new ArrayList<>()).add(p);
        }
        List<LocalDate> candidatos = new ArrayList<>(porDia.keySet());
        candidatos.sort(Comparator.<LocalDate>comparingInt(d -> porDia.get(d).size()).reversed());

        Simulador.ParametrosAlgoritmo base = Simulador.ParametrosAlgoritmo.DEFAULT;
        Map<String, Simulador.Algoritmo> algoritmoDe = new LinkedHashMap<>();
        Map<String, Simulador.ParametrosAlgoritmo> paramsDe = new LinkedHashMap<>();
        algoritmoDe.put("ACS", Simulador.Algoritmo.ACS);
        paramsDe.put("ACS", base);
        algoritmoDe.put("ACS_memoria", Simulador.Algoritmo.ACS);
        paramsDe.put("ACS_memoria", base.conMemoriaAcs(retencion));
        algoritmoDe.put("ACS_local", Simulador.Algoritmo.ACS);
        paramsDe.put("ACS_local", base.conBusquedaLocalAcs(true));
        algoritmoDe.put("ACS_memoria_local", Simulador.Algoritmo.ACS);
        paramsDe.put("ACS_memoria_local", base.conMemoriaAcs(retencion).conBusquedaLocalAcs(true));
        algoritmoDe.put("GRASP_VNS", Simulador.Algoritmo.GRASP_VNS);
        paramsDe.put("GRASP_VNS", base);

        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, hilos));
        List<String[]> filas = new ArrayList<>();
        try {
            List<LocalDate> dias = new ArrayList<>();
            for (LocalDate dia : candidatos) {
                if (dias.size() >= numDias) break;
                List<Pedido> pd = porDia.get(dia);
                LocalDateTime ini = dia.atStartOfDay();
                boolean ok = true;
                for (String et : List.of("ACS", "GRASP_VNS")) {
                    ok &= Afinacion.sobrevive(pool, pd, bloqueos, mant, ini, algoritmoDe.get(et), paramsDe.get(et), semillas, 1.0);
                }
                if (ok) {
                    dias.add(dia);
                    System.out.println("Dia aceptado: " + dia + " (" + pd.size() + " pedidos)");
                }
            }

            for (Map.Entry<String, Simulador.Algoritmo> e : algoritmoDe.entrySet()) {
                String et = e.getKey();
                for (LocalDate dia : dias) {
                    List<Pedido> pd = porDia.get(dia);
                    LocalDateTime ini = dia.atStartOfDay();
                    double u;
                    if (!Afinacion.sobrevive(pool, pd, bloqueos, mant, ini, e.getValue(), paramsDe.get(et), semillas, 1.0)) {
                        u = 0.0;
                    } else {
                        u = Afinacion.buscarUEstrella(pool, pd, bloqueos, mant, ini, e.getValue(), paramsDe.get(et), semillas, lambdaMax);
                    }
                    filas.add(new String[]{et, dia.toString(), String.format(Locale.US, "%.4f", u)});
                    System.out.printf("  %-18s %s -> U*=%.1f%n", et, dia, u);
                }
            }
        } finally {
            pool.shutdown();
        }

        escribir(salida, filas);
    }

    private static void escribir(Path salida, List<String[]> filas) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(salida.resolve("combinada_corridas.csv")))) {
            w.println("variante,fecha_bloque,u_estrella");
            for (String[] f : filas) w.println(String.join(",", f));
        }
        Map<String, double[]> acc = new LinkedHashMap<>();
        for (String[] f : filas) {
            double[] a = acc.computeIfAbsent(f[0], k -> new double[2]);
            a[0] += Double.parseDouble(f[2]);
            a[1] += 1;
        }
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(salida.resolve("combinada_resumen.csv")))) {
            w.println("variante,u_estrella_promedio,dias");
            System.out.println();
            System.out.println("Resumen (U* promedio sobre los dias de afinacion):");
            for (Map.Entry<String, double[]> e : acc.entrySet()) {
                double prom = e.getValue()[0] / e.getValue()[1];
                w.printf(Locale.US, "%s,%.4f,%d%n", e.getKey(), prom, (int) e.getValue()[1]);
                System.out.printf(Locale.US, "  %-18s %.1f%n", e.getKey(), prom);
            }
        }
    }
}
