package com.paqroute.backend.controller;

import com.paqroute.backend.service.EscenarioService;
import java.util.Map;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Canal de control STOMP de las simulaciones en streaming: pausa, reanudación
 *              y velocidad (milisegundos entre ticks).
 */
@Controller
public class SimulacionControlController {

    private final EscenarioService escenarioService;
    private final SimpMessagingTemplate messagingTemplate;

    public SimulacionControlController(EscenarioService escenarioService, SimpMessagingTemplate messagingTemplate) {
        this.escenarioService = escenarioService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/simulacion/{id}/control")
    public void controlar(@DestinationVariable String id,
                          Map<String, Object> payload) {
        final String comando = payload.get("comando") != null ? String.valueOf(payload.get("comando")) : "";
        final Long valor = payload.get("valor") instanceof Number numero ? numero.longValue() : null;
        escenarioService.controlar(id, comando, valor);
        messagingTemplate.convertAndSend("/topic/simulacion/" + id + "/control",
                (Object) Map.of("comando", comando, "aplicado", true));
    }
}