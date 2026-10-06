package com.paqroute.backend.exception;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: DTO estándar para respuestas de error de la API.
 */
@Getter
@Builder
public class ApiErrorResponse {
    private LocalDateTime fechaHora;
    private int estado;
    private String error;
    private String mensaje;
    private String ruta;
}
