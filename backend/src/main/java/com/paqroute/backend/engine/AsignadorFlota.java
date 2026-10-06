package com.paqroute.backend.engine;

import com.paqroute.backend.enums.TipoVehiculo;
import com.paqroute.backend.model.ContextoPlanificacion;
import com.paqroute.backend.model.Pedido;
import com.paqroute.backend.model.Punto;
import java.time.Duration;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Regla de asignación de capacidades y flota heterogénea: entre los tipos de
 *              vehículo que, viajando desde el origen de despacho, aún puedan cumplir el plazo
 *              restante del pedido semilla, recomienda el de MENOR COSTO TOTAL ESTIMADO — no
 *              simplemente el más barato por km. Un vehículo barato pero de poca capacidad puede
 *              necesitar varios viajes de ida y vuelta, que terminan costando más que un solo viaje.
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
        return mejor != null ? mejor : TipoVehiculo.AUTO;
    }
}