package com.paqroute.backend.engine;

import com.paqroute.backend.model.Almacen;
import com.paqroute.backend.model.Bloqueo;
import com.paqroute.backend.model.ContextoPlanificacion;
import com.paqroute.backend.model.Entrega;
import com.paqroute.backend.model.Mantenimiento;
import com.paqroute.backend.model.Pedido;
import com.paqroute.backend.model.Punto;
import com.paqroute.backend.model.Ruta;
import com.paqroute.backend.model.Solucion;
import com.paqroute.backend.model.UnidadTransporte;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Motor de simulación del escenario de colapso logístico: avanza el reloj en pasos
 *              fijos (tick), ingresa los pedidos del histórico a medida que "llegan", y en cada
 *              tick invoca al algoritmo metaheurístico elegido (ACS o GRASP-VNS) para decidir qué
 *              rutas despachar con la flota, los almacenes y los bloqueos vigentes. Se detiene
 *              apenas un pedido incumple su plazo sin haber sido completado (punto de colapso).
 *              No se consideran averías de unidades.
 */
public final class Simulador {

    public enum Algoritmo { ACS, GRASP_VNS }

    /** Listener invocado en cada tick de la simulación (para publicar el avance en tiempo real). */
    public interface TickListener {
        void onTick(TickEvent evento);
    }

    /** Snapshot de un tick: hora, flota, pendientes y rutas planificadas en ese paso. */
    public record TickEvent(LocalDateTime horaActual, List<UnidadTransporte> flota, List<Pedido> pendientes,
                            List<Ruta> rutasPlanificadas) {
    }

    /**
     * Hiperparámetros de cada metaheurística. Los valores adoptados de GRASP-VNS (alfaGrasp=0.5,
     * maxIteracionesGrasp=10) son los que generalizaron en la afinación; el resto conserva los
     * valores originales del informe. retencionMemoriaAcs activa la variante experimental con
     * memoria de feromonas entre ticks (0.0 = ACS original). busquedaLocalAcs activa la variante
     * experimental con VND sobre la mejor solución del tick. tipoVehiculoAmpliadoAcs activa la
     * variante que prueba todos los tipos de vehículo libre al abrir una ruta.
     */
    public record ParametrosAlgoritmo(int numHormigas, int numIteracionesAcs, double alfaAcs, double betaAcs,
                                       double rho, double q0, double alfaGrasp, int maxIteracionesGrasp,
                                       double retencionMemoriaAcs, boolean busquedaLocalAcs,
                                       boolean tipoVehiculoAmpliadoAcs) {
        public static final ParametrosAlgoritmo DEFAULT =
                new ParametrosAlgoritmo(5, 5, 1.0, 2.0, 0.1, 0.9, 0.5, 10);

        public ParametrosAlgoritmo(int numHormigas, int numIteracionesAcs, double alfaAcs, double betaAcs,
                                    double rho, double q0, double alfaGrasp, int maxIteracionesGrasp) {
            this(numHormigas, numIteracionesAcs, alfaAcs, betaAcs, rho, q0, alfaGrasp, maxIteracionesGrasp,
                    0.0, false, false);
        }

        public ParametrosAlgoritmo conMemoriaAcs(double retencion) {
            return new ParametrosAlgoritmo(numHormigas, numIteracionesAcs, alfaAcs, betaAcs, rho, q0,
                    alfaGrasp, maxIteracionesGrasp, retencion, busquedaLocalAcs, tipoVehiculoAmpliadoAcs);
        }

        public ParametrosAlgoritmo conBusquedaLocalAcs(boolean activa) {
            return new ParametrosAlgoritmo(numHormigas, numIteracionesAcs, alfaAcs, betaAcs, rho, q0,
                    alfaGrasp, maxIteracionesGrasp, retencionMemoriaAcs, activa, tipoVehiculoAmpliadoAcs);
        }

        public ParametrosAlgoritmo conTipoVehiculoAmpliadoAcs(boolean activa) {
            return new ParametrosAlgoritmo(numHormigas, numIteracionesAcs, alfaAcs, betaAcs, rho, q0,
                    alfaGrasp, maxIteracionesGrasp, retencionMemoriaAcs, busquedaLocalAcs, activa);
        }
    }

    private static final int NUM_AUTOS = 10;
    private static final int NUM_MOTOS = 15;
    private static final int NUM_BICICLETAS = 12;

    /** Almacenes de la simulación: central infinito y dos intermedios con stock limitado. */
    public static List<Almacen> crearAlmacenes() {
        return List.of(
                new Almacen(Almacen.Id.CENTRAL, new Punto(27, 14), Integer.MAX_VALUE),
                new Almacen(Almacen.Id.NOROESTE, new Punto(12, 38), 1000),
                new Almacen(Almacen.Id.ESTE, new Punto(57, 27), 1000));
    }

    /** Cantidad de unidades de la flota fija. */
    public static int totalVehiculos() {
        return NUM_AUTOS + NUM_MOTOS + NUM_BICICLETAS;
    }

    /** Flota fija completa (10 autos, 15 motos, 12 bicicletas) desde un instante y posición iniciales. */
    public static List<UnidadTransporte> construirFlota(Punto posicionInicial, LocalDateTime momentoInicial) {
        List<UnidadTransporte> lista = new ArrayList<>();
        for (int i = 1; i <= NUM_AUTOS; i++) lista.add(crearUnidad(String.format("TA%02d", i), com.paqroute.backend.enums.TipoVehiculo.AUTO, posicionInicial, momentoInicial));
        for (int i = 1; i <= NUM_MOTOS; i++) lista.add(crearUnidad(String.format("TM%02d", i), com.paqroute.backend.enums.TipoVehiculo.MOTO, posicionInicial, momentoInicial));
        for (int i = 1; i <= NUM_BICICLETAS; i++) lista.add(crearUnidad(String.format("TB%02d", i), com.paqroute.backend.enums.TipoVehiculo.BICICLETA, posicionInicial, momentoInicial));
        return lista;
    }

    /** Crea la subclase concreta segun el tipo: Auto, Moto o Bicicleta. */
    public static UnidadTransporte crearUnidad(String id, com.paqroute.backend.enums.TipoVehiculo tipo, Punto posicionInicial, LocalDateTime momentoInicial) {
        return switch (tipo) {
            case AUTO -> new com.paqroute.backend.model.Auto(id, posicionInicial, momentoInicial);
            case MOTO -> new com.paqroute.backend.model.Moto(id, posicionInicial, momentoInicial);
            case BICICLETA -> new com.paqroute.backend.model.Bicicleta(id, posicionInicial, momentoInicial);
        };
    }

    private final List<Pedido> pedidosOrdenados;
    private final List<Bloqueo> bloqueos;
    private final List<Mantenimiento> mantenimientos;
    private final List<Almacen> almacenes;
    private final List<UnidadTransporte> flota;
    private final GrafoVial grafo = new GrafoVial();
    private final Duration duracionTick;
    private final Algoritmo algoritmo;
    private final long semilla;
    private final LocalDateTime inicio;
    private final ParametrosAlgoritmo parametros;
    /** Solo no-null en la variante experimental ACS con memoria (retencionMemoriaAcs > 0). */
    private final MemoriaFeromonas memoriaAcs;

    public Simulador(List<Pedido> pedidos, List<Bloqueo> bloqueos, List<Mantenimiento> mantenimientos,
                      Duration duracionTick, Algoritmo algoritmo, long semilla) {
        this(pedidos, bloqueos, mantenimientos, duracionTick, algoritmo, semilla,
                pedidos.isEmpty() ? LocalDateTime.now() : pedidos.get(0).momentoLlegada);
    }

    /** {@code inicio}: instante en que arranca el reloj con toda la flota libre en el almacén central. */
    public Simulador(List<Pedido> pedidos, List<Bloqueo> bloqueos, List<Mantenimiento> mantenimientos,
                      Duration duracionTick, Algoritmo algoritmo, long semilla, LocalDateTime inicio) {
        this(pedidos, bloqueos, mantenimientos, duracionTick, algoritmo, semilla, inicio, ParametrosAlgoritmo.DEFAULT);
    }

    public Simulador(List<Pedido> pedidos, List<Bloqueo> bloqueos, List<Mantenimiento> mantenimientos,
                      Duration duracionTick, Algoritmo algoritmo, long semilla, LocalDateTime inicio,
                      ParametrosAlgoritmo parametros) {
        this.inicio = inicio;
        this.pedidosOrdenados = pedidos;
        this.bloqueos = bloqueos;
        this.mantenimientos = mantenimientos;
        this.duracionTick = duracionTick;
        this.algoritmo = algoritmo;
        this.semilla = semilla;
        this.parametros = parametros;
        this.memoriaAcs = (algoritmo == Algoritmo.ACS && parametros.retencionMemoriaAcs() > 0.0)
                ? new MemoriaFeromonas() : null;

        this.almacenes = crearAlmacenes();
        this.flota = construirFlota(almacenes.get(0).ubicacion, inicio);
    }

    public ResultadoSimulacion ejecutar() {
        return ejecutar(null, 0L);
    }

    /**
     * Ejecuta la simulación. Si {@code listener} no es nulo, se invoca en cada tick (por ejemplo,
     * para publicar el avance por WebSocket/STOMP); con {@code esperaEntreTicksMs} se puede frenar
     * el ritmo de envío. Con valores nulos/0 se ejecuta a toda velocidad.
     */
    public ResultadoSimulacion ejecutar(TickListener listener, long esperaEntreTicksMs) {
        ResultadoSimulacion resultado = new ResultadoSimulacion();
        if (pedidosOrdenados.isEmpty()) return resultado;

        LocalDateTime horaActual = inicio;
        LocalDateTime horaAnterior = horaActual.minusDays(1);
        int indiceSiguientePedido = 0;
        List<Pedido> pendientes = new ArrayList<>();
        List<Ruta> ultimasRutas = new ArrayList<>();

        while (true) {
            if (!horaActual.toLocalDate().equals(horaAnterior.toLocalDate())) {
                for (Almacen a : almacenes) if (a.id != Almacen.Id.CENTRAL) a.recargar();
            }

            while (indiceSiguientePedido < pedidosOrdenados.size()
                    && !pedidosOrdenados.get(indiceSiguientePedido).momentoLlegada.isAfter(horaActual)) {
                pendientes.add(pedidosOrdenados.get(indiceSiguientePedido));
                indiceSiguientePedido++;
                resultado.pedidosIngresados++;
            }
            pendientes.removeIf(Pedido::completo);

            List<UnidadTransporte> vehiculosLibres = new ArrayList<>();
            for (UnidadTransporte v : flota) {
                if (enMantenimiento(v, horaActual)) continue;
                if (v.estado == UnidadTransporte.Estado.EN_RUTA && !horaActual.isBefore(v.disponibleDesde)) v.estado = UnidadTransporte.Estado.LIBRE;
                if (v.estado == UnidadTransporte.Estado.LIBRE) vehiculosLibres.add(v);
            }

            if (!pendientes.isEmpty() && !vehiculosLibres.isEmpty()) {
                var bloqueosVigentes = Bloqueo.aristasVigentesEn(bloqueos, horaActual);
                grafo.actualizarBloqueosVigentes(bloqueosVigentes);
                ContextoPlanificacion ctx = new ContextoPlanificacion(almacenes, grafo, horaActual);

                long inicioTa = System.nanoTime();
                Solucion solucion = planificarTick(pendientes, vehiculosLibres, ctx, resultado.ticksSimulados);
                long ta = (System.nanoTime() - inicioTa) / 1_000_000;
                resultado.invocacionesAlgoritmo++;
                resultado.tiempoAlgoritmoTotalMs += ta;
                resultado.tiempoAlgoritmoMaximoMs = Math.max(resultado.tiempoAlgoritmoMaximoMs, ta);

                aplicarSolucion(solucion, resultado, grafo, horaActual);
                ultimasRutas = solucion.rutas;
            }

            for (Pedido p : pendientes) {
                if (p.incumplido(horaActual)) {
                    resultado.colapso = true;
                    resultado.momentoColapso = horaActual;
                    resultado.clientePedidoColapsado = p.idCliente + " " + p.ubicacion
                            + " (pendiente " + p.cantidadPendiente + "/" + p.cantidadTotal + ", limite " + p.fechaLimite + ")";
                    emitirTick(listener, new TickEvent(horaActual, flota, pendientes, ultimasRutas));
                    return resultado;
                }
            }

            emitirTick(listener, new TickEvent(horaActual, flota, pendientes, ultimasRutas));
            dormirEntreTicks(esperaEntreTicksMs);

            resultado.ticksSimulados++;
            horaAnterior = horaActual;
            horaActual = horaActual.plus(duracionTick);

            if (indiceSiguientePedido >= pedidosOrdenados.size() && pendientes.stream().allMatch(Pedido::completo)) {
                calcularMetricasSla(resultado);
                return resultado;
            }
        }
    }

    private void emitirTick(TickListener listener, TickEvent evento) {
        if (listener == null) return;
        try {
            listener.onTick(evento);
        } catch (RuntimeException e) {
            // No se detiene la simulación por un error del observador.
        }
    }

    private void dormirEntreTicks(long esperaMs) {
        if (esperaMs <= 0) return;
        try {
            Thread.sleep(esperaMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Simulación interrumpida por el controlador", e);
        }
    }

    private boolean enMantenimiento(UnidadTransporte v, LocalDateTime momento) {
        for (Mantenimiento m : mantenimientos) {
            if (m.vehiculoId.equals(v.idUnidad) && m.vigenteEn(momento)) return true;
        }
        return false;
    }

    private void calcularMetricasSla(ResultadoSimulacion resultado) {
        double suma = 0.0;
        int n = 0;
        for (Pedido p : pedidosOrdenados) {
            if (p.horaCompletado == null) continue;
            suma += p.consumoSlaPct();
            n++;
        }
        resultado.pedidosCompletos = n;
        resultado.consumoSlaPromedioPct = n == 0 ? Double.NaN : suma / n;
    }

    private Solucion planificarTick(List<Pedido> pendientes, List<UnidadTransporte> vehiculosLibres, ContextoPlanificacion ctx, int numeroTick) {
        long semilla = this.semilla * 1_000_003L + numeroTick;
        return switch (algoritmo) {
            case ACS -> new AntColonySystemVRP(pendientes, vehiculosLibres, ctx, semilla,
                    parametros.numHormigas(), parametros.numIteracionesAcs(), parametros.alfaAcs(),
                    parametros.betaAcs(), parametros.rho(), parametros.q0(),
                    memoriaAcs, parametros.retencionMemoriaAcs(), parametros.busquedaLocalAcs(),
                    parametros.tipoVehiculoAmpliadoAcs())
                    .resolverTick();
            case GRASP_VNS -> new GraspVnsVRP(pendientes, vehiculosLibres, ctx, semilla,
                    parametros.alfaGrasp(), parametros.maxIteracionesGrasp())
                    .resolverTick();
        };
    }

    private void aplicarSolucion(Solucion solucion, ResultadoSimulacion resultado, GrafoVial grafoVigente, LocalDateTime horaActual) {
        for (Ruta ruta : solucion.rutas) {
            ruta.almacenDespacho.retirar(ruta.cargaTotal());
            List<LocalDateTime> llegadas = RutaUtil.horasLlegada(ruta.secuencia, ruta.vehiculo.tipo,
                    ruta.almacenDespacho.ubicacion, horaActual, grafoVigente);
            for (int i = 0; i < ruta.secuencia.size(); i++) {
                Entrega e = ruta.secuencia.get(i);
                LocalDateTime llegada = llegadas.get(i);
                if (e.pedido.horaCompletado == null || llegada.isAfter(e.pedido.horaCompletado)) e.pedido.horaCompletado = llegada;
                e.pedido.cantidadPendiente -= e.cantidad;
                resultado.entregasTotales++;
                if (e.esParcial()) resultado.entregasParciales++;
                if (e.pedido.completo()) resultado.pedidosCompletadosATiempo++;
            }
            ruta.vehiculo.estado = UnidadTransporte.Estado.EN_RUTA;
            ruta.vehiculo.disponibleDesde = ruta.horaLlegadaRetorno;
            ruta.vehiculo.posicionActual = ruta.almacenRetorno.ubicacion;

            resultado.costoAcumuladoSoles += ruta.costo();
            resultado.kilometrosRecorridos += ruta.distanciaIdaKm + ruta.distanciaRetornoKm;
        }
    }
}