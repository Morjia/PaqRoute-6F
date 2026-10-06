package com.paqroute.backend.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: DTO de salida de la ejecución de un escenario (síncrono o referencia a streaming).
 */
@Getter
@Builder
public class EscenarioResponseDTO {
    private Integer idHistorial;
    private String tipo;
    private String algoritmo;
    private int replicas;
    private String estado; // EJECUTADO | EN_PROCESO
    private List<ResultadoSimulacionResponseDTO> resultados;
}