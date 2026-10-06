package com.paqroute.backend.dto.response;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: DTO de salida de un registro del historial de replanificación.
 */
@Getter
@Builder
public class HistorialReplanificacionResponseDTO {
    private Integer id;
    private Integer incidenciaId;
    private String tipo;
    private String detalle;
    private int rutasEvaluadas;
    private LocalDateTime fecha;
}