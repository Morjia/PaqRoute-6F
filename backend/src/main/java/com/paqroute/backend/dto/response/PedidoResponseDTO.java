package com.paqroute.backend.dto.response;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: DTO de salida para un pedido.
 */
@Getter
@Builder
public class PedidoResponseDTO {
    private String codigo;
    private String idCliente;
    private int x;
    private int y;
    private int cantidadTotal;
    private int cantidadPendiente;
    private double horasLimite;
    private LocalDateTime momentoLlegada;
    private LocalDateTime fechaLimite;
    private boolean completo;
}