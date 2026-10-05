package paqroute;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Evaluacion de una secuencia de entregas (ida desde un origen, parada por parada) para un tipo
 * de vehiculo concreto, considerando la cuadricula con bloqueos vigentes (GrafoVial) y el instante
 * real en que arranca la ruta. Centraliza las reglas de negocio para que ACS y GRASP-VNS evaluen
 * las soluciones de la misma forma.
 */
public final class RutaUtil {

    public static final double HORAS_SERVICIO_POR_PARADA = 1.0;

    private RutaUtil() {}

    public record Evaluacion(boolean factible, double distanciaIdaKm, LocalDateTime horaFinUltimaEntrega) {
        static final Evaluacion INFACTIBLE = new Evaluacion(false, 0.0, null);
    }

    /**
     * Recorre origen -> entrega1 -> entrega2 -> ... verificando capacidad del vehiculo y que cada
     * entrega llegue antes de la fechaLimite de su pedido. No incluye el tramo de regreso al
     * almacen (eso se evalua aparte, al cerrar la ruta, con distanciaRetornoKm).
     */
    public static Evaluacion evaluar(List<Entrega> secuencia, TipoVehiculo tipo, Punto origen,
                                      LocalDateTime horaInicio, GrafoVial grafo) {
        int carga = 0;
        for (Entrega e : secuencia) carga += e.cantidad;
        if (carga > tipo.capacidad) return Evaluacion.INFACTIBLE;

        double distanciaTotal = 0.0;
        Punto actual = origen;
        LocalDateTime horaActual = horaInicio;
        for (Entrega e : secuencia) {
            int distanciaKm = grafo.distanciaKm(actual, e.pedido.ubicacion);
            distanciaTotal += distanciaKm;
            double horasViaje = distanciaKm / tipo.velocidadKmH;
            horaActual = horaActual.plusSeconds(Math.round(horasViaje * 3600));
            if (horaActual.isAfter(e.pedido.fechaLimite)) return Evaluacion.INFACTIBLE;
            horaActual = horaActual.plusMinutes(Math.round(HORAS_SERVICIO_POR_PARADA * 60));
            actual = e.pedido.ubicacion;
        }
        return new Evaluacion(true, distanciaTotal, horaActual);
    }

    /** Hora de llegada (antes del servicio) a cada parada de la secuencia, con las mismas reglas de evaluar(). */
    public static List<LocalDateTime> horasLlegada(List<Entrega> secuencia, TipoVehiculo tipo, Punto origen,
                                                    LocalDateTime horaInicio, GrafoVial grafo) {
        List<LocalDateTime> llegadas = new java.util.ArrayList<>(secuencia.size());
        Punto actual = origen;
        LocalDateTime horaActual = horaInicio;
        for (Entrega e : secuencia) {
            int distanciaKm = grafo.distanciaKm(actual, e.pedido.ubicacion);
            horaActual = horaActual.plusSeconds(Math.round(distanciaKm / tipo.velocidadKmH * 3600));
            llegadas.add(horaActual);
            horaActual = horaActual.plusMinutes(Math.round(HORAS_SERVICIO_POR_PARADA * 60));
            actual = e.pedido.ubicacion;
        }
        return llegadas;
    }

    public static double distanciaRetornoKm(Punto ultimoPunto, Almacen almacen, GrafoVial grafo) {
        return grafo.distanciaKm(ultimoPunto, almacen.ubicacion);
    }

    public static double costoRuta(double distanciaIdaKm, double distanciaRetornoKm, TipoVehiculo tipo) {
        return (distanciaIdaKm + distanciaRetornoKm) * tipo.costoPorKm;
    }
}
