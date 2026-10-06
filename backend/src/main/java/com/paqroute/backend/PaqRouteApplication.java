package com.paqroute.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Punto de entrada de la aplicación Spring Boot del backend de PaqRoute.
 */
@SpringBootApplication
@EnableAsync
public class PaqRouteApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaqRouteApplication.class, args);
    }
}
