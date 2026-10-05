package paqroute;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Corrida puntual: GRASP-VNS con los hiperparametros ORIGINALES del informe (alfa=0.3, iter=5,
 * antes de la afinacion por U* que los cambio a 0.5/10) sobre los 4 escenarios de verificacion de
 * AfinacionColapso, para comparar contra el default actual (0.5/10) en la metrica Tc. */
public final class VerifGraspOriginal {
    private static final Duration TICK = Duration.ofMinutes(30);

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);
        List<Pedido> todosPedidos = LectorPedidos.leerCarpeta(Path.of("data/ventas"));
        List<Bloqueo> bloqueos = LectorBloqueos.leerCarpeta(Path.of("data/bloqueos"));
        List<Mantenimiento> mantenimientos = LectorMantenimiento.leerCarpeta(Path.of("data/mantenimiento"));

        List<LocalDate> fechas = List.of(
                LocalDate.of(2027, 12, 28), LocalDate.of(2028, 2, 2),
                LocalDate.of(2028, 3, 26), LocalDate.of(2028, 6, 5));

        var paramsOriginal = new Simulador.ParametrosAlgoritmo(5, 5, 1.0, 2.0, 0.1, 0.9, 0.3, 5);

        ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        List<Future<double[]>> futuros = new ArrayList<>();
        for (int i = 0; i < fechas.size(); i++) {
            LocalDate fecha = fechas.get(i);
            long semilla = i + 1;
            futuros.add(pool.submit(() -> {
                LocalDateTime inicio = fecha.atStartOfDay();
                List<Pedido> pedidosEscenario = new ArrayList<>();
                for (Pedido p : todosPedidos) if (!p.momentoLlegada.isBefore(inicio)) pedidosEscenario.add(p.copiaEscalada(1.0));
                Simulador sim = new Simulador(pedidosEscenario, bloqueos, mantenimientos, TICK,
                        Simulador.Algoritmo.GRASP_VNS, semilla, inicio, paramsOriginal);
                ResultadoSimulacion r = sim.ejecutar();
                double dias;
                boolean censurado;
                if (r.colapso) {
                    dias = Duration.between(inicio, r.momentoColapso).toMinutes() / 60.0 / 24.0;
                    censurado = false;
                } else {
                    LocalDateTime fin = pedidosEscenario.isEmpty() ? inicio : pedidosEscenario.get(pedidosEscenario.size() - 1).momentoLlegada;
                    dias = Duration.between(inicio, fin).toMinutes() / 60.0 / 24.0;
                    censurado = true;
                }
                System.out.printf(Locale.US, "  GRASP_VNS original(alfa=0.3,iter=5) inicio=%s -> Tc=%.1f dias%s%n",
                        fecha, dias, censurado ? " (censurado)" : "");
                return new double[]{dias};
            }));
        }
        double suma = 0;
        for (Future<double[]> f : futuros) suma += f.get()[0];
        pool.shutdown();
        System.out.printf(Locale.US, "  GRASP_VNS original(alfa=0.3,iter=5) -> Tc promedio = %.1f dias (%d escenarios)%n",
                suma / fechas.size(), fechas.size());
    }
}
