package com.paqroute.backend.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: DTO de salida de un indicador clave de desempeño (KPI).
 */
@Getter
@Builder
public class KpiResponseDTO {
    private String algoritmo;
    private int pedidosIngresados;
    private int pedidosCompletados;
    private double porcentajeCumplimiento;
    private int entregasTotales;
    private int entregasParciales;
    private double porcentajeEntregasParciales;
    private double costoTotalSoles;
    private double kilometrosRecorridos;
    private double costoPorKm;
    private double costoPorPedido;
}