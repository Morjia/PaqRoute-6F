package com.paqroute.backend.dto.response;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: DTO de salida del resultado de una simulación de colapso.
 */
@Getter
@Builder
public class ResultadoSimulacionResponseDTO {
    private boolean colapso;
    private LocalDateTime momentoColapso;
    private String clientePedidoColapsado;
    private int pedidosIngresados;
    private int pedidosCompletadosATiempo;
    private int entregasTotales;
    private int entregasParciales;
    private double costoAcumuladoSoles;
    private double kilometrosRecorridos;
    private int ticksSimulados;
    private Double consumoSlaPromedioPct;
    private int invocacionesAlgoritmo;
    private double tiempoAlgoritmoPromedioMs;
    private long tiempoAlgoritmoMaximoMs;
}