package com.paqroute.backend.service;

import com.paqroute.backend.loader.LectorBloqueosTxt;
import com.paqroute.backend.loader.LectorMantenimientoTxt;
import com.paqroute.backend.loader.LectorPedidosTxt;
import com.paqroute.backend.repository.BloqueoRepository;
import com.paqroute.backend.repository.MantenimientoRepository;
import com.paqroute.backend.repository.PedidoRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Orquesta la carga inicial de los archivos .txt reales de ventas, bloqueos y
 *              mantenimiento preventivo en los repositorios en memoria.
 */
@Service
public class CargaDatosService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CargaDatosService.class);

    private final PedidoRepository pedidoRepository;
    private final BloqueoRepository bloqueoRepository;
    private final MantenimientoRepository mantenimientoRepository;
    private final NotificacionService notificacionService;

    private final String directorio;
    private final String carpetaVentas;
    private final String carpetaBloqueos;
    private final String carpetaMantenimiento;

    public CargaDatosService(PedidoRepository pedidoRepository,
                             BloqueoRepository bloqueoRepository,
                             MantenimientoRepository mantenimientoRepository,
                             NotificacionService notificacionService,
                             @Value("${paqroute.data.directorio:../data}") String directorio,
                             @Value("${paqroute.data.carpeta-ventas:ventas}") String carpetaVentas,
                             @Value("${paqroute.data.carpeta-bloqueos:bloqueos}") String carpetaBloqueos,
                             @Value("${paqroute.data.carpeta-mantenimiento:mantenimiento}") String carpetaMantenimiento) {
        this.pedidoRepository = pedidoRepository;
        this.bloqueoRepository = bloqueoRepository;
        this.mantenimientoRepository = mantenimientoRepository;
        this.notificacionService = notificacionService;
        this.directorio = directorio;
        this.carpetaVentas = carpetaVentas;
        this.carpetaBloqueos = carpetaBloqueos;
        this.carpetaMantenimiento = carpetaMantenimiento;
    }

    /** Carga todos los .txt reales. Si el directorio no existe, registra WARN y continúa. */
    public void cargarTodo() {
        final Path base = Path.of(directorio);
        if (!Files.isDirectory(base)) {
            notificacionService.registrarAdvertencia("Directorio de datos no encontrado: "
                    + base.toAbsolutePath() + " (la aplicación arranca sin datos)");
            return;
        }

        final Path ventas = base.resolve(carpetaVentas);
        final Path bloqueos = base.resolve(carpetaBloqueos);
        final Path mantenimiento = base.resolve(carpetaMantenimiento);

        if (Files.isDirectory(ventas)) {
            try {
                pedidoRepository.reemplazar(LectorPedidosTxt.leerCarpeta(ventas));
            } catch (IOException | IllegalArgumentException e) {
                LOGGER.error("No se pudieron cargar las ventas desde {}", ventas, e);
            }
        } else {
            LOGGER.warn("Carpeta de ventas no encontrada: {}", ventas);
        }

        if (Files.isDirectory(bloqueos)) {
            try {
                bloqueoRepository.reemplazar(LectorBloqueosTxt.leerCarpeta(bloqueos));
            } catch (IOException | IllegalArgumentException e) {
                LOGGER.error("No se pudieron cargar los bloqueos desde {}", bloqueos, e);
            }
        } else {
            LOGGER.warn("Carpeta de bloqueos no encontrada: {}", bloqueos);
        }

        if (Files.isDirectory(mantenimiento)) {
            try {
                mantenimientoRepository.reemplazar(LectorMantenimientoTxt.leerCarpeta(mantenimiento));
            } catch (IOException | IllegalArgumentException e) {
                LOGGER.error("No se pudo cargar el mantenimiento desde {}", mantenimiento, e);
            }
        } else {
            LOGGER.warn("Carpeta de mantenimiento no encontrada: {}", mantenimiento);
        }

        notificacionService.registrarEvento("Datos cargados: " + pedidoRepository.total()
                + " pedidos (ventas), " + bloqueoRepository.total() + " bloqueos, "
                + mantenimientoRepository.total() + " mantenimientos");
        LOGGER.info("Carga de datos desde {}", base.toAbsolutePath());
    }
}