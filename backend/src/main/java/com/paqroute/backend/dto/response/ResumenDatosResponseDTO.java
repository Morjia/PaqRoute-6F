package com.paqroute.backend.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: DTO de salida con el resumen de los datos cargados en memoria.
 */
@Getter
@Builder
public class ResumenDatosResponseDTO {
    private int totalPedidosVentas;
    private int totalVehiculos;
    private int totalAlmacenes;
    private int totalBloqueos;
    private int totalMantenimientos;
}