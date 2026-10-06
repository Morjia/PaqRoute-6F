package com.paqroute.backend.controller;

import com.paqroute.backend.dto.request.PlanificarRutasRequestDTO;
import com.paqroute.backend.dto.response.RutasPlanificadasResponseDTO;
import com.paqroute.backend.service.PlanificadorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Controller REST de planificación de rutas (one-shot).
 */
@RestController
@RequestMapping("/api/rutas")
public class RutaController {

    private final PlanificadorService planificadorService;

    public RutaController(PlanificadorService planificadorService) {
        this.planificadorService = planificadorService;
    }

    @PostMapping("/planificar")
    public RutasPlanificadasResponseDTO planificar(@Valid @RequestBody PlanificarRutasRequestDTO requestDTO) {
        return planificadorService.planificar(requestDTO);
    }
}