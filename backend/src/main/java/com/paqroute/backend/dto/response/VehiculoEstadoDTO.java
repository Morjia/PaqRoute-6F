package com.paqroute.backend.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: DTO de salida del estado de una unidad de transporte (visualizador).
 */
@Getter
@Builder
public class VehiculoEstadoDTO {
    private String id;
    private String tipo;
    private String estado;
    private int x;
    private int y;
    private String disponibleDesde;
}