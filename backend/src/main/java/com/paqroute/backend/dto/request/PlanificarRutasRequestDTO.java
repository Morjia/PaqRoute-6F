package com.paqroute.backend.dto.request;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: DTO de entrada para una planificación "one-shot" de rutas en un instante dado.
 */
@Getter
@Setter
public class PlanificarRutasRequestDTO {

    /** ACS o GRASP_VNS (default ACS). */
    private String algoritmo = "ACS";

    /** Instante de planificación; si es nulo se usa la llegada del primer pedido. */
    private LocalDateTime hora;

    /** Variante experimental ACS mejorado. */
    private Boolean acsMejorado = false;
}