package paqroute;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Demo del componente planificador de PaqRoute: lee los archivos reales de pedidos, bloqueos y
 * mantenimiento, y corre el escenario de colapso logistico con ambos algoritmos metaheuristicos
 * candidatos (ACS y GRASP-VNS), reportando en que momento colapsa cada uno y con que desempeno.
 *
 * No se consideran averias/fallas de unidades de transporte (excluidas de esta corrida).
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);

        Path carpetaVentas = Path.of(args.length > 0 ? args[0]
                : "E:/PUCP/FACU INF/26-2/PDDS/archivos de entrada/ventas v20260909");
        Path carpetaBloqueos = Path.of(args.length > 1 ? args[1]
                : "E:/PUCP/FACU INF/26-2/PDDS/archivos de entrada/bloqueos v20260909");
        Path carpetaMantenimiento = Path.of(args.length > 2 ? args[2]
                : "E:/PUCP/FACU INF/26-2/PDDS/archivos de entrada/planes mantenimiento 26-28");

        System.out.println("Leyendo bloqueos de: " + carpetaBloqueos);
        List<Bloqueo> bloqueos = LectorBloqueos.leerCarpeta(carpetaBloqueos);
        System.out.println("Bloqueos leidos: " + bloqueos.size());

        System.out.println("Leyendo mantenimiento de: " + carpetaMantenimiento);
        List<Mantenimiento> mantenimientos = LectorMantenimiento.leerCarpeta(carpetaMantenimiento);
        System.out.println("Registros de mantenimiento leidos: " + mantenimientos.size());

        Duration tick = Duration.ofMinutes(30);

        ejecutarEscenario("ACS", Simulador.Algoritmo.ACS, carpetaVentas, bloqueos, mantenimientos, tick);
        ejecutarEscenario("GRASP-VNS", Simulador.Algoritmo.GRASP_VNS, carpetaVentas, bloqueos, mantenimientos, tick);
    }

    private static void ejecutarEscenario(String nombre, Simulador.Algoritmo algoritmo, Path carpetaVentas,
                                           List<Bloqueo> bloqueos, List<Mantenimiento> mantenimientos,
                                           Duration tick) throws Exception {
        System.out.println();
        System.out.println("=== Escenario de colapso: " + nombre + " ===");

        List<Pedido> pedidos = LectorPedidos.leerCarpeta(carpetaVentas); // copia fresca (Pedido es mutable)
        System.out.println("Pedidos disponibles: " + pedidos.size());

        long t0 = System.nanoTime();
        Simulador simulador = new Simulador(pedidos, bloqueos, mantenimientos, tick, algoritmo, 1L);
        ResultadoSimulacion resultado = simulador.ejecutar();
        long t1 = System.nanoTime();

        System.out.println(resultado);
        System.out.printf("Tiempo de computo: %.1f s%n", (t1 - t0) / 1e9);
    }
}
