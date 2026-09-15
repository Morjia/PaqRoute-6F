package paqroute;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Motor de simulacion del escenario de colapso logistico: avanza el reloj en pasos fijos (tick),
 * va ingresando los pedidos del historico/proyectado a medida que "llegan", y en cada tick invoca
 * al algoritmo metaheuristico elegido (ACS o GRASP-VNS) para decidir que rutas despachar con la
 * flota, los almacenes y los bloqueos vigentes en ese instante. Se detiene apenas un pedido
 * incumple su plazo (fechaLimite) sin haber sido completado -- ese es el punto de colapso.
 *
 * No se consideran averias/fallas de unidades de transporte (fuera de alcance de esta corrida);
 * si se consideran bloqueos de calles y mantenimiento preventivo programado.
 *
 * <p>Mapeo con los conceptos de Sa/Ta/Sc del curso:
 * <ul>
 *   <li><b>Sc (salto de consumo)</b>: es {@code duracionTick} -- cuanto tiempo de pedidos se avanza
 *       entre una ejecucion del algoritmo y la siguiente.</li>
 *   <li><b>Ta (tiempo de algoritmo)</b>: se mide con {@code System.nanoTime()} alrededor de cada
 *       llamada a {@code planificarTick(...)} y se acumula en {@link ResultadoSimulacion}.</li>
 *   <li><b>Sa (salto de algoritmo)</b>: en este simulador, Sa coincide con Ta -- no hay espera
 *       artificial entre ticks, se corre tan rapido como el hardware lo permite. Esto es lo
 *       correcto para los escenarios de simulacion 5D y de colapso (se busca terminar cuanto
 *       antes). Para un escenario de tiempo real habria que forzar Sa a coincidir con el reloj
 *       de pared real (dormir el hilo hasta que pase el Sa correspondiente aunque Ta haya sido
 *       mas corto); este simulador no lo hace porque no lo necesita para el escenario de colapso.</li>
 * </ul>
 */
public final class Simulador {

    public enum Algoritmo { ACS, GRASP_VNS }

    private static final int NUM_AUTOS = 10;
    private static final int NUM_MOTOS = 15;
    private static final int NUM_BICICLETAS = 12;

    private final List<Pedido> pedidosOrdenados;
    private final List<Bloqueo> bloqueos;
    private final List<Mantenimiento> mantenimientos;
    private final List<Almacen> almacenes;
    private final List<Vehiculo> flota;
    private final GrafoVial grafo = new GrafoVial();
    private final Duration duracionTick; // Sc: salto de consumo
    private final Algoritmo algoritmo;
    private final long semilla;

    public Simulador(List<Pedido> pedidos, List<Bloqueo> bloqueos, List<Mantenimiento> mantenimientos,
                      Duration duracionTick, Algoritmo algoritmo, long semilla) {
        this.pedidosOrdenados = pedidos;
        this.bloqueos = bloqueos;
        this.mantenimientos = mantenimientos;
        this.duracionTick = duracionTick;
        this.algoritmo = algoritmo;
        this.semilla = semilla;

        this.almacenes = List.of(
                new Almacen(Almacen.Id.CENTRAL, new Punto(27, 14), Integer.MAX_VALUE),
                new Almacen(Almacen.Id.NOROESTE, new Punto(12, 38), 1000),
                new Almacen(Almacen.Id.ESTE, new Punto(57, 27), 1000));

        this.flota = construirFlota(pedidos.isEmpty() ? LocalDateTime.now() : pedidos.get(0).momentoLlegada);
    }

    private List<Vehiculo> construirFlota(LocalDateTime momentoInicial) {
        List<Vehiculo> lista = new ArrayList<>();
        Punto central = almacenes.get(0).ubicacion;
        for (int i = 1; i <= NUM_AUTOS; i++) lista.add(new Vehiculo(String.format("TA%02d", i), TipoVehiculo.AUTO, central, momentoInicial));
        for (int i = 1; i <= NUM_MOTOS; i++) lista.add(new Vehiculo(String.format("TM%02d", i), TipoVehiculo.MOTO, central, momentoInicial));
        for (int i = 1; i <= NUM_BICICLETAS; i++) lista.add(new Vehiculo(String.format("TB%02d", i), TipoVehiculo.BICICLETA, central, momentoInicial));
        return lista;
    }

    public ResultadoSimulacion ejecutar() {
        ResultadoSimulacion resultado = new ResultadoSimulacion();
        if (pedidosOrdenados.isEmpty()) return resultado;

        LocalDateTime horaActual = pedidosOrdenados.get(0).momentoLlegada;
        LocalDateTime horaAnterior = horaActual.minusDays(1);
        int indiceSiguientePedido = 0;
        List<Pedido> pendientes = new ArrayList<>();

        while (true) {
            // Recarga instantanea diaria de los almacenes intermedios al cruzar la medianoche.
            if (!horaActual.toLocalDate().equals(horaAnterior.toLocalDate())) {
                for (Almacen a : almacenes) if (a.id != Almacen.Id.CENTRAL) a.recargar();
            }

            // Ingreso de pedidos cuyo momento de llegada ya se cumplio.
            while (indiceSiguientePedido < pedidosOrdenados.size()
                    && !pedidosOrdenados.get(indiceSiguientePedido).momentoLlegada.isAfter(horaActual)) {
                pendientes.add(pedidosOrdenados.get(indiceSiguientePedido));
                indiceSiguientePedido++;
                resultado.pedidosIngresados++;
            }
            pendientes.removeIf(Pedido::completo);

            // Vehiculos libres: no en mantenimiento y ya disponibles.
            List<Vehiculo> vehiculosLibres = new ArrayList<>();
            for (Vehiculo v : flota) {
                if (enMantenimiento(v, horaActual)) continue;
                if (v.estado == Vehiculo.Estado.EN_RUTA && !horaActual.isBefore(v.disponibleDesde)) v.estado = Vehiculo.Estado.LIBRE;
                if (v.estado == Vehiculo.Estado.LIBRE) vehiculosLibres.add(v);
            }

            if (!pendientes.isEmpty() && !vehiculosLibres.isEmpty()) {
                var bloqueosVigentes = Bloqueo.aristasVigentesEn(bloqueos, horaActual);
                grafo.actualizarBloqueosVigentes(bloqueosVigentes);
                ContextoPlanificacion ctx = new ContextoPlanificacion(almacenes, grafo, horaActual);

                long inicioTa = System.nanoTime();
                Solucion solucion = planificarTick(pendientes, vehiculosLibres, ctx);
                long ta = (System.nanoTime() - inicioTa) / 1_000_000; // Ta de esta invocacion, en ms
                resultado.invocacionesAlgoritmo++;
                resultado.tiempoAlgoritmoTotalMs += ta;
                resultado.tiempoAlgoritmoMaximoMs = Math.max(resultado.tiempoAlgoritmoMaximoMs, ta);

                aplicarSolucion(solucion, resultado);
            }

            // Criterio de colapso: algun pedido pendiente ya supero su plazo.
            for (Pedido p : pendientes) {
                if (p.incumplido(horaActual)) {
                    resultado.colapso = true;
                    resultado.momentoColapso = horaActual;
                    resultado.clientePedidoColapsado = p.idCliente + " " + p.ubicacion
                            + " (pendiente " + p.cantidadPendiente + "/" + p.cantidadTotal + ", limite " + p.fechaLimite + ")";
                    return resultado;
                }
            }

            resultado.ticksSimulados++;
            horaAnterior = horaActual;
            horaActual = horaActual.plus(duracionTick);

            if (indiceSiguientePedido >= pedidosOrdenados.size() && pendientes.stream().allMatch(Pedido::completo)) {
                return resultado; // se agoto el historico disponible sin llegar al colapso
            }
        }
    }

    private boolean enMantenimiento(Vehiculo v, LocalDateTime momento) {
        for (Mantenimiento m : mantenimientos) {
            if (m.vehiculoId.equals(v.id) && m.vigenteEn(momento)) return true;
        }
        return false;
    }

    private Solucion planificarTick(List<Pedido> pendientes, List<Vehiculo> vehiculosLibres, ContextoPlanificacion ctx) {
        return switch (algoritmo) {
            case ACS -> new AntColonySystemVRP(pendientes, vehiculosLibres, ctx, semilla,
                    /*numHormigas*/ 5, /*numIteraciones*/ 5, /*alfa*/ 1.0, /*beta*/ 2.0, /*rho*/ 0.1, /*q0*/ 0.9)
                    .resolverTick();
            case GRASP_VNS -> new GraspVnsVRP(pendientes, vehiculosLibres, ctx, semilla,
                    /*alfaGRASP*/ 0.3, /*maxIteracionesGRASP*/ 5)
                    .resolverTick();
        };
    }

    private void aplicarSolucion(Solucion solucion, ResultadoSimulacion resultado) {
        for (Ruta ruta : solucion.rutas) {
            ruta.almacenDespacho.retirar(ruta.cargaTotal());
            for (Entrega e : ruta.secuencia) {
                e.pedido.cantidadPendiente -= e.cantidad;
                resultado.entregasTotales++;
                if (e.esParcial()) resultado.entregasParciales++;
                if (e.pedido.completo()) resultado.pedidosCompletadosATiempo++;
            }
            ruta.vehiculo.estado = Vehiculo.Estado.EN_RUTA;
            ruta.vehiculo.disponibleDesde = ruta.horaLlegadaRetorno;
            ruta.vehiculo.posicionActual = ruta.almacenRetorno.ubicacion;

            resultado.costoAcumuladoSoles += ruta.costo();
            resultado.kilometrosRecorridos += ruta.distanciaIdaKm + ruta.distanciaRetornoKm;
        }
    }
}
