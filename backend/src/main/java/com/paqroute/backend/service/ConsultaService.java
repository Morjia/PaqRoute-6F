package com.paqroute.backend.service;

import com.paqroute.backend.dto.response.ResumenDatosResponseDTO;
import com.paqroute.backend.engine.Simulador;
import com.paqroute.backend.repository.BloqueoRepository;
import com.paqroute.backend.repository.MantenimientoRepository;
import com.paqroute.backend.repository.PedidoRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Servicio de consulta simple sobre los datos cargados en memoria.
 */
@Service
public class ConsultaService {

    private final PedidoRepository pedidoRepository;
    private final BloqueoRepository bloqueoRepository;
    private final MantenimientoRepository mantenimientoRepository;

    public ConsultaService(PedidoRepository pedidoRepository,
                           BloqueoRepository bloqueoRepository,
                           MantenimientoRepository mantenimientoRepository) {
        this.pedidoRepository = pedidoRepository;
        this.bloqueoRepository = bloqueoRepository;
        this.mantenimientoRepository = mantenimientoRepository;
    }

    public ResumenDatosResponseDTO resumen() {
        return ResumenDatosResponseDTO.builder()
                .totalPedidosVentas(pedidoRepository.total())
                .totalVehiculos(Simulador.totalVehiculos())
                .totalAlmacenes(Simulador.crearAlmacenes().size())
                .totalBloqueos(bloqueoRepository.total())
                .totalMantenimientos(mantenimientoRepository.total())
                .build();
    }

    public int bloqueosVigentes(LocalDateTime momento) {
        return bloqueoRepository.vigentesEn(momento);
    }
}