package com.paqroute.backend.controller;

import com.paqroute.backend.dto.response.ResumenDatosResponseDTO;
import com.paqroute.backend.service.ConsultaService;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Controller REST de consulta simple sobre los datos cargados.
 */
@RestController
@RequestMapping("/api/datos")
public class ConsultaController {

    private final ConsultaService consultaService;

    public ConsultaController(ConsultaService consultaService) {
        this.consultaService = consultaService;
    }

    @GetMapping("/resumen")
    public ResumenDatosResponseDTO resumen() {
        return consultaService.resumen();
    }

    @GetMapping("/bloqueos/vigentes")
    public Map<String, Object> bloqueosVigentes(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime momento) {
        final LocalDateTime instante = momento != null ? momento : LocalDateTime.now();
        return Map.of("momento", instante, "vigentes", consultaService.bloqueosVigentes(instante));
    }
}