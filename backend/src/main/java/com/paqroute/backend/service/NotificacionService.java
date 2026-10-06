package com.paqroute.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Registro de eventos internos del dominio (reemplaza a la auditoría de CODA).
 */
@Service
public class NotificacionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificacionService.class);

    public void registrarEvento(String mensaje) {
        LOGGER.info("[evento] {}", mensaje);
    }

    public void registrarAdvertencia(String mensaje) {
        LOGGER.warn("[evento] {}", mensaje);
    }
}
