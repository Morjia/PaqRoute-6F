package com.paqroute.backend.mapper;

import com.paqroute.backend.dto.response.HistorialSimulacionResponseDTO;
import com.paqroute.backend.model.HistorialSimulacion;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Convierte entidades HistorialSimulacion en DTOs de respuesta.
 */
public final class HistorialSimulacionMapper {

    private HistorialSimulacionMapper() {
    }

    public static HistorialSimulacionResponseDTO toResponseDTO(HistorialSimulacion h) {
        return HistorialSimulacionResponseDTO.builder()
                .id(h.getId())
                .tipo(h.getTipo())
                .algoritmo(h.getAlgoritmo())
                .semilla(h.getSemilla())
                .lambda(h.getLambda())
                .desde(h.getDesde())
                .fechaEjecucion(h.getFechaEjecucion())
                .colapso(h.isColapso())
                .momentoColapso(h.getMomentoColapso())
                .pedidosIngresados(h.getPedidosIngresados())
                .pedidosCompletadosATiempo(h.getPedidosCompletadosATiempo())
                .costoAcumuladoSoles(h.getCostoAcumuladoSoles())
                .kilometrosRecorridos(h.getKilometrosRecorridos())
                .consumoSlaPromedioPct(h.getConsumoSlaPromedioPct())
                .build();
    }
}