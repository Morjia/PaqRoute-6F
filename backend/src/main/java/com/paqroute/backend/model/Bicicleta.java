package com.paqroute.backend.model;

import com.paqroute.backend.enums.TipoVehiculo;
import java.time.LocalDateTime;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Unidad de transporte tipo bicicleta (capacidad 4, velocidad 12 km/h, S/ 3 por km).
 */
public final class Bicicleta extends UnidadTransporte {

    public Bicicleta(String idUnidad, Punto posicionInicial, LocalDateTime disponibleDesde) {
        super(idUnidad, TipoVehiculo.BICICLETA, posicionInicial, disponibleDesde);
    }
}