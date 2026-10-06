package com.paqroute.backend.controller;

import com.paqroute.backend.dto.response.EstadoOperacionResponseDTO;
import com.paqroute.backend.service.OperacionService;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Controller REST del estado de la operación (alimenta al visualizador).
 */
@RestController
@RequestMapping("/api/operacion")
public class OperacionController {

    private final OperacionService operacionService;

    public OperacionController(OperacionService operacionService) {
        this.operacionService = operacionService;
    }

    @GetMapping("/estado")
    public EstadoOperacionResponseDTO estado(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime momento) {
        return operacionService.estado(momento);
    }
}