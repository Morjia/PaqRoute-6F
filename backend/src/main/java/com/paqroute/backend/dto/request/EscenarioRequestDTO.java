package com.paqroute.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: DTO de entrada para ejecutar un escenario de simulación.
 */
@Getter
@Setter
public class EscenarioRequestDTO {

    /** OPERACION_DIARIA | CINCO_DIAS | COLAPSO. */
    @NotBlank
    private String tipo;

    /** ACS o GRASP_VNS (default ACS). */
    private String algoritmo = "ACS";

    /** Inicio de la simulación; si es nulo se usa la llegada del primer pedido. */
    private LocalDateTime desde;

    /** Duración en horas (aplica a OPERACION_DIARIA y CINCO_DIAS). */
    private Double duracionHoras;

    /** Factor de carga (cantidad = ceil(qq*lambda)). */
    private Double lambda = 1.0;

    /** Número de réplicas (semillas 1..N). */
    private Integer replicas = 1;

    /** Variante experimental ACS mejorado. */
    private Boolean acsMejorado = false;

    /** Milisegundos de espera entre ticks (streaming en tiempo real). */
    private Long pausaMs = 0L;
}