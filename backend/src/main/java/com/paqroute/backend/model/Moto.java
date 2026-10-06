package com.paqroute.backend.model;

import com.paqroute.backend.enums.TipoVehiculo;
import java.time.LocalDateTime;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Unidad de transporte tipo moto (capacidad 8, velocidad 25 km/h, S/ 6 por km).
 */
public final class Moto extends UnidadTransporte {

    public Moto(String idUnidad, Punto posicionInicial, LocalDateTime disponibleDesde) {
        super(idUnidad, TipoVehiculo.MOTO, posicionInicial, disponibleDesde);
    }
}