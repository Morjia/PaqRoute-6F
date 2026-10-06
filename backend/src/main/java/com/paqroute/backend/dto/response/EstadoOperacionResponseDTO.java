package com.paqroute.backend.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: DTO de salida del estado de la operación en un instante (alimenta al visualizador).
 */
@Getter
@Builder
public class EstadoOperacionResponseDTO {
    private LocalDateTime momento;
    private int pedidosPendientes;
    private int bloqueosVigentes;
    private List<VehiculoEstadoDTO> vehiculos;
    private List<AlmacenEstadoDTO> almacenes;
}