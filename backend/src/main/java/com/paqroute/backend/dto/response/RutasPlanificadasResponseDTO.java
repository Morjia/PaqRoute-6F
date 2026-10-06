package com.paqroute.backend.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: DTO de salida del resultado de una planificación one-shot.
 */
@Getter
@Builder
public class RutasPlanificadasResponseDTO {
    private int totalRutas;
    private int totalEntregas;
    private List<RutaResponseDTO> rutas;
}