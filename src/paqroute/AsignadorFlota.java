package paqroute;

import java.time.Duration;

/**
 * Regla de asignacion de capacidades y flota heterogenea: entre los tipos de vehiculo que, viajando
 * desde el origen de despacho, aun puedan cumplir el plazo restante del pedido semilla, recomienda
 * el de MENOR COSTO TOTAL ESTIMADO -- no simplemente el mas barato por km. Esto importa porque un
 * vehiculo barato pero de poca capacidad (p.ej. bicicleta, 4 paquetes) puede necesitar varios viajes
 * de ida y vuelta para cubrir un pedido grande, y esos viajes repetidos pueden terminar costando mas
 * que un solo viaje en un vehiculo de mayor capacidad.
 */
public final class AsignadorFlota {

    private static final TipoVehiculo[] ORDEN_POR_COSTO = {
            TipoVehiculo.BICICLETA, TipoVehiculo.MOTO, TipoVehiculo.AUTO
    };

    private AsignadorFlota() {}

    public static TipoVehiculo tipoRecomendado(Pedido pedidoSemilla, Punto origen, ContextoPlanificacion ctx) {
        int distanciaKm = ctx.grafo.distanciaKm(origen, pedidoSemilla.ubicacion);
        double horasRestantes = Duration.between(ctx.horaActual, pedidoSemilla.fechaLimite).toMinutes() / 60.0;
        double horasViajeDisponibles = Math.max(horasRestantes - RutaUtil.HORAS_SERVICIO_POR_PARADA, 0.1);
        double velocidadRequeridaKmH = distanciaKm / horasViajeDisponibles;

        TipoVehiculo mejor = null;
        double mejorCostoEstimado = Double.MAX_VALUE;
        for (TipoVehiculo tipo : ORDEN_POR_COSTO) {
            if (tipo.velocidadKmH < velocidadRequeridaKmH) continue;
            int viajesNecesarios = (int) Math.ceil(pedidoSemilla.cantidadPendiente / (double) tipo.capacidad);
            double costoEstimado = viajesNecesarios * 2.0 * distanciaKm * tipo.costoPorKm;
            if (costoEstimado < mejorCostoEstimado) {
                mejorCostoEstimado = costoEstimado;
                mejor = tipo;
            }
        }
        return mejor != null ? mejor : TipoVehiculo.AUTO; // ninguno alcanza la velocidad requerida: se usa el mas rapido
    }
}
