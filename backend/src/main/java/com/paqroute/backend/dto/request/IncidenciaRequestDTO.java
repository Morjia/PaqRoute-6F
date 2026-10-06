package com.paqroute.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: DTO de entrada para registrar una incidencia (bloqueo o falla vehicular).
 */
@Getter
@Setter
public class IncidenciaRequestDTO {

    /** BLOQUEO_CALLE | FALLA_VEHICULAR. */
    @NotBlank
    private String tipo;

    private String motivo;

    /** Duración estimada de la afectación, en horas. */
    private Double duracionEstimadaHoras;

    /** Inicio de la afectación; si es nulo se usa la fecha/hora actual. */
    private LocalDateTime desde;

    /** Pares [x,y] de la polilínea bloqueada (solo BLOQUEO_CALLE). */
    private List<List<Integer>> poligonal;

    /** Vehículo afectado (solo FALLA_VEHICULAR). */
    private String vehiculoId;
}