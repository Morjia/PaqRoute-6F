package com.paqroute.backend.controller;

import com.paqroute.backend.dto.request.EscenarioRequestDTO;
import com.paqroute.backend.dto.request.SimulacionRequestDTO;
import com.paqroute.backend.dto.response.ResultadoSimulacionResponseDTO;
import com.paqroute.backend.service.EscenarioService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Controller REST del escenario de colapso (alias) y lista de algoritmos.
 */
@RestController
@RequestMapping("/api/simulacion")
public class SimulacionController {

    private final EscenarioService escenarioService;

    public SimulacionController(EscenarioService escenarioService) {
        this.escenarioService = escenarioService;
    }

    @PostMapping("/colapso")
    public ResultadoSimulacionResponseDTO ejecutarColapso(@Valid @RequestBody SimulacionRequestDTO requestDTO) {
        final EscenarioRequestDTO escenario = new EscenarioRequestDTO();
        escenario.setTipo("COLAPSO");
        escenario.setAlgoritmo(requestDTO.getAlgoritmo());
        escenario.setDesde(requestDTO.getDesde());
        escenario.setLambda(requestDTO.getLambda() != null ? requestDTO.getLambda() : 1.0);
        escenario.setAcsMejorado(requestDTO.getAcsMejorado());
        escenario.setReplicas(1);
        return escenarioService.ejecutarSincrono(escenario).getResultados().get(0);
    }

    @GetMapping("/algoritmos")
    public List<String> listarAlgoritmos() {
        return escenarioService.algoritmos();
    }
}