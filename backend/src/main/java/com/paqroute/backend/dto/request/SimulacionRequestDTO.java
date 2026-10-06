package com.paqroute.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: DTO de entrada para ejecutar el escenario de colapso con el simulador.
 */
@Getter
@Setter
public class SimulacionRequestDTO {

    /** ACS o GRASP_VNS. */
    @NotBlank
    private String algoritmo;

    /** Duración del tick en minutos (por defecto 30). */
    private Integer tickMinutos = 30;

    /** Semilla del generador aleatorio (por defecto 1). */
    private Long semilla = 1L;

    /** Instante de inicio de la simulación; si es nulo, se usa la llegada del primer pedido. */
    private LocalDateTime desde;

    /** Factor de carga: cantidad = ceil(qq * lambda) (por defecto 1.0). */
    private Double lambda = 1.0;

    /** Si es true, usa la variante experimental ACS mejorado (memoria 0.3 + VND + tipo ampliado). */
    private Boolean acsMejorado = false;
}