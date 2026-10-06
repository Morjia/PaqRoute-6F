package com.paqroute.backend.mapper;

import com.paqroute.backend.dto.response.ResultadoSimulacionResponseDTO;
import com.paqroute.backend.engine.ResultadoSimulacion;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Convierte un ResultadoSimulacion en su DTO de respuesta.
 */
public final class ResultadoSimulacionMapper {

    private ResultadoSimulacionMapper() {
    }

    public static ResultadoSimulacionResponseDTO toResponseDTO(ResultadoSimulacion r) {
        return ResultadoSimulacionResponseDTO.builder()
                .colapso(r.colapso)
                .momentoColapso(r.momentoColapso)
                .clientePedidoColapsado(r.clientePedidoColapsado)
                .pedidosIngresados(r.pedidosIngresados)
                .pedidosCompletadosATiempo(r.pedidosCompletadosATiempo)
                .entregasTotales(r.entregasTotales)
                .entregasParciales(r.entregasParciales)
                .costoAcumuladoSoles(r.costoAcumuladoSoles)
                .kilometrosRecorridos(r.kilometrosRecorridos)
                .ticksSimulados(r.ticksSimulados)
                .consumoSlaPromedioPct(Double.isNaN(r.consumoSlaPromedioPct) ? null : r.consumoSlaPromedioPct)
                .invocacionesAlgoritmo(r.invocacionesAlgoritmo)
                .tiempoAlgoritmoPromedioMs(r.tiempoAlgoritmoPromedioMs())
                .tiempoAlgoritmoMaximoMs(r.tiempoAlgoritmoMaximoMs)
                .build();
    }
}