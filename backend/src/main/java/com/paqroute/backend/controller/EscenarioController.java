package com.paqroute.backend.controller;

import com.paqroute.backend.dto.request.EscenarioRequestDTO;
import com.paqroute.backend.dto.response.EscenarioResponseDTO;
import com.paqroute.backend.dto.response.HistorialSimulacionResponseDTO;
import com.paqroute.backend.mapper.HistorialSimulacionMapper;
import com.paqroute.backend.service.EscenarioService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Controller REST de configuración y ejecución de escenarios.
 */
@RestController
@RequestMapping("/api/escenarios")
public class EscenarioController {

    private final EscenarioService escenarioService;

    public EscenarioController(EscenarioService escenarioService) {
        this.escenarioService = escenarioService;
    }

    @PostMapping("/ejecutar")
    public EscenarioResponseDTO ejecutar(@Valid @RequestBody EscenarioRequestDTO requestDTO) {
        return escenarioService.ejecutarSincrono(requestDTO);
    }

    @PostMapping("/ejecutar/streaming")
    public Map<String, Object> ejecutarStreaming(@Valid @RequestBody EscenarioRequestDTO requestDTO) {
        final String id = escenarioService.iniciarStreaming(requestDTO);
        return Map.of("id", id, "estado", "EN_PROCESO", "topic", "/topic/simulacion/" + id);
    }

    @GetMapping("/historial")
    public List<HistorialSimulacionResponseDTO> historial() {
        return escenarioService.historial().stream()
                .map(HistorialSimulacionMapper::toResponseDTO)
                .toList();
    }

    @GetMapping("/tipos")
    public List<String> tipos() {
        return escenarioService.tipos();
    }

    @GetMapping("/algoritmos")
    public List<String> algoritmos() {
        return escenarioService.algoritmos();
    }
}