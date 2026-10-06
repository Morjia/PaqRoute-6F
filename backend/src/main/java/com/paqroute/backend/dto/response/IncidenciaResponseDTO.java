package com.paqroute.backend.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: DTO de salida para una incidencia registrada.
 */
@Getter
@Builder
public class IncidenciaResponseDTO {
    private Integer id;
    private String tipo;
    private String motivo;
    private Double duracionEstimadaHoras;
    private LocalDateTime desde;
    private String vehiculoId;
    private boolean resuelta;
    private LocalDateTime fechaRegistro;
    private List<List<Integer>> poligonal;
}