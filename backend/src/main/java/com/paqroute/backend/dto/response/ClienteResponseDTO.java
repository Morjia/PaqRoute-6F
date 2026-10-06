package com.paqroute.backend.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: DTO de salida con el resumen de un cliente.
 */
@Getter
@Builder
public class ClienteResponseDTO {
    private String idCliente;
    private Long totalPedidos;
}