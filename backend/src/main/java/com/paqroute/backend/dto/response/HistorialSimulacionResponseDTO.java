package com.paqroute.backend.dto.response;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: DTO de salida de un registro del historial de simulaciones.
 */
@Getter
@Builder
public class HistorialSimulacionResponseDTO {
    private Integer id;
    private String tipo;
    private String algoritmo;
    private long semilla;
    private double lambda;
    private LocalDateTime desde;
    private LocalDateTime fechaEjecucion;
    private boolean colapso;
    private LocalDateTime momentoColapso;
    private int pedidosIngresados;
    private int pedidosCompletadosATiempo;
    private double costoAcumuladoSoles;
    private double kilometrosRecorridos;
    private Double consumoSlaPromedioPct;
}