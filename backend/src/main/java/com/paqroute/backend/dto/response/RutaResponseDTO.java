package com.paqroute.backend.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: DTO de salida de una ruta planificada.
 */
@Getter
@Builder
public class RutaResponseDTO {
    private String vehiculoId;
    private String vehiculoTipo;
    private String almacenDespacho;
    private String almacenRetorno;
    private int cargaTotal;
    private double distanciaIdaKm;
    private double distanciaRetornoKm;
    private LocalDateTime horaFinUltimaEntrega;
    private LocalDateTime horaLlegadaRetorno;
    private List<EntregaResponseDTO> paradas;
}