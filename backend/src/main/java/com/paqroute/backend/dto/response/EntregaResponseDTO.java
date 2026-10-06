package com.paqroute.backend.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: DTO de salida de una parada (entrega) dentro de una ruta.
 */
@Getter
@Builder
public class EntregaResponseDTO {
    private String pedidoId;
    private int cantidad;
    private boolean parcial;
}