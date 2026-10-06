package com.paqroute.backend.controller;

import com.paqroute.backend.dto.request.EscenarioRequestDTO;
import com.paqroute.backend.dto.response.EscenarioResponseDTO;
import com.paqroute.backend.util.MotorSimulacion;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Controller REST del motor de simulación (modos y alertas de saturación).
 */
@RestController
@RequestMapping("/api/motor")
public class MotorSimulacionController {

    private final MotorSimulacion motorSimulacion;

    public MotorSimulacionController(MotorSimulacion motorSimulacion) {
        this.motorSimulacion = motorSimulacion;
    }

    @GetMapping("/modos")
    public List<String> modos() {
        return motorSimulacion.modos();
    }

    @GetMapping("/alerta-saturacion")
    public Map<String, Object> alertaSaturacion() {
        return motorSimulacion.emitirAlertaSaturacion();
    }

    @PostMapping("/ejecutar/{modo}")
    public EscenarioResponseDTO ejecutar(@PathVariable String modo,
                                         @Valid @RequestBody EscenarioRequestDTO requestDTO) {
        return switch (modo.toUpperCase()) {
            case "DIA_ADIA" -> motorSimulacion.iniciarSimulacionDiaADia(requestDTO);
            case "CINCO_DIAS" -> motorSimulacion.iniciarSimulacion5D(requestDTO);
            case "COLAPSO" -> motorSimulacion.iniciarSimulacionColapso(requestDTO);
            default -> throw new IllegalArgumentException("Modo de simulación inválido: " + modo
                    + " (esperado DIA_ADIA, CINCO_DIAS o COLAPSO)");
        };
    }
}