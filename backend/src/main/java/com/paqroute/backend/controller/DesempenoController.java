package com.paqroute.backend.controller;

import com.paqroute.backend.dto.response.KpiResponseDTO;
import com.paqroute.backend.exception.ResourceNotFoundException;
import com.paqroute.backend.service.DesempenoService;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Controller REST del desempeño de las operaciones (KPIs y reportes exportables).
 */
@RestController
@RequestMapping("/api/desempeno")
public class DesempenoController {

    private final DesempenoService desempenoService;

    public DesempenoController(DesempenoService desempenoService) {
        this.desempenoService = desempenoService;
    }

    @GetMapping("/kpis")
    public KpiResponseDTO kpis(@RequestParam String algoritmo) {
        final KpiResponseDTO kpi = desempenoService.kpis(algoritmo);
        if (kpi == null) {
            throw new ResourceNotFoundException("No hay corridas para el algoritmo " + algoritmo);
        }
        return kpi;
    }

    @GetMapping("/comparativa")
    public List<KpiResponseDTO> comparativa() {
        return desempenoService.comparativa();
    }

    @GetMapping("/reportes")
    public ResponseEntity<Object> reportes(@RequestParam(defaultValue = "json") String formato) {
        if ("csv".equalsIgnoreCase(formato)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"paqroute-reporte.csv\"")
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .body(desempenoService.reporteCsv());
        }
        return ResponseEntity.ok(desempenoService.reporteJson());
    }
}