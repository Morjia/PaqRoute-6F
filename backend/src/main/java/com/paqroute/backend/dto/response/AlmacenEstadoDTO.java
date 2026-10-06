package com.paqroute.backend.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: DTO de salida del estado de un almacén (capacidad y ocupación).
 */
@Getter
@Builder
public class AlmacenEstadoDTO {
    private String id;
    private int capacidadMaxima;
    private int stock;
    private int ocupacion;
}