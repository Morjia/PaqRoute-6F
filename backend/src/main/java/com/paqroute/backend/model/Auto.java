package com.paqroute.backend.model;

import com.paqroute.backend.enums.TipoVehiculo;
import java.time.LocalDateTime;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Unidad de transporte tipo automóvil (capacidad 24, velocidad 40 km/h, S/ 8 por km).
 */
public final class Auto extends UnidadTransporte {

    public Auto(String idUnidad, Punto posicionInicial, LocalDateTime disponibleDesde) {
        super(idUnidad, TipoVehiculo.AUTO, posicionInicial, disponibleDesde);
    }
}