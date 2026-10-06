package com.paqroute.backend.config;

import com.paqroute.backend.exception.ArchivoInvalidoException;
import com.paqroute.backend.service.CargaDatosService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Carga los archivos .txt configurados al arrancar la aplicación.
 */
@Component
public class SeedDataConfig implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeedDataConfig.class);

    private final CargaDatosService cargaDatosService;

    public SeedDataConfig(CargaDatosService cargaDatosService) {
        this.cargaDatosService = cargaDatosService;
    }

    @Override
    public void run(String... args) {
        try {
            cargaDatosService.cargarTodo();
        } catch (ArchivoInvalidoException e) {
            LOGGER.error("No se pudieron cargar los datos iniciales: {}", e.getMessage());
        }
    }
}