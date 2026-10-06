package com.paqroute.backend.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: DTO de entrada para registrar un pedido individual.
 */
@Getter
@Setter
public class PedidoRegistroRequestDTO {

    /** Identificador del cliente; si va en blanco se genera uno (código único). */
    private String idCliente;

    @NotNull
    @Min(0)
    @Max(70)
    private Integer x;

    @NotNull
    @Min(0)
    @Max(50)
    private Integer y;

    @NotNull
    @Min(1)
    private Integer cantidad;

    @NotNull
    @Positive
    private Double horasLimite;
}