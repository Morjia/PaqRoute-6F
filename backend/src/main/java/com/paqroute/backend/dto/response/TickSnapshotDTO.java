package com.paqroute.backend.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: DTO publicado por WebSocket en cada tick de la simulación en tiempo real.
 */
@Getter
@Builder
public class TickSnapshotDTO {
    private String simulacionId;
    private LocalDateTime horaActual;
    private boolean colapso;
    private int pedidosPendientes;
    private List<VehiculoEstadoDTO> vehiculos;
    private List<RutaResponseDTO> rutas;
}