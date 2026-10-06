package com.paqroute.backend.controller;

import com.paqroute.backend.dto.request.IncidenciaRequestDTO;
import com.paqroute.backend.dto.response.HistorialReplanificacionResponseDTO;
import com.paqroute.backend.dto.response.IncidenciaResponseDTO;
import com.paqroute.backend.mapper.IncidenciaMapper;
import com.paqroute.backend.model.Incidencia;
import com.paqroute.backend.service.IncidenciaService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Controller REST de incidencias (bloqueos y fallas) y su historial de replanificación.
 */
@RestController
@RequestMapping("/api/incidencias")
public class IncidenciaController {

    private final IncidenciaService incidenciaService;

    public IncidenciaController(IncidenciaService incidenciaService) {
        this.incidenciaService = incidenciaService;
    }

    @PostMapping
    public ResponseEntity<IncidenciaResponseDTO> registrar(@Valid @RequestBody IncidenciaRequestDTO requestDTO) {
        final Incidencia incidencia = incidenciaService.registrar(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(IncidenciaMapper.toResponseDTO(incidencia));
    }

    @GetMapping
    public List<IncidenciaResponseDTO> listar() {
        return incidenciaService.listar().stream()
                .map(IncidenciaMapper::toResponseDTO)
                .toList();
    }

    @GetMapping("/historial")
    public List<HistorialReplanificacionResponseDTO> historial() {
        return incidenciaService.historial().stream()
                .map(IncidenciaMapper::toHistorialResponseDTO)
                .toList();
    }
}