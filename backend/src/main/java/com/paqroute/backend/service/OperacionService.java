package com.paqroute.backend.service;

import com.paqroute.backend.dto.response.AlmacenEstadoDTO;
import com.paqroute.backend.dto.response.EstadoOperacionResponseDTO;
import com.paqroute.backend.dto.response.VehiculoEstadoDTO;
import com.paqroute.backend.engine.Simulador;
import com.paqroute.backend.model.Almacen;
import com.paqroute.backend.model.Pedido;
import com.paqroute.backend.model.UnidadTransporte;
import com.paqroute.backend.repository.BloqueoRepository;
import com.paqroute.backend.repository.PedidoRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Snapshot del estado de la operación en un instante (alimenta al visualizador):
 *              flota, almacenes, pedidos pendientes y bloqueos vigentes.
 */
@Service
public class OperacionService {

    private final PedidoRepository pedidoRepository;
    private final BloqueoRepository bloqueoRepository;

    public OperacionService(PedidoRepository pedidoRepository, BloqueoRepository bloqueoRepository) {
        this.pedidoRepository = pedidoRepository;
        this.bloqueoRepository = bloqueoRepository;
    }

    public EstadoOperacionResponseDTO estado(LocalDateTime momento) {
        final LocalDateTime instante = momento != null ? momento : LocalDateTime.now();
        final List<Almacen> almacenes = Simulador.crearAlmacenes();
        final List<UnidadTransporte> flota = Simulador.construirFlota(almacenes.get(0).ubicacion, instante);

        List<VehiculoEstadoDTO> vehiculos = new ArrayList<>();
        for (UnidadTransporte v : flota) {
            vehiculos.add(VehiculoEstadoDTO.builder()
                    .id(v.idUnidad)
                    .tipo(v.tipo.name())
                    .estado(v.estado.name())
                    .x(v.posicionActual.x)
                    .y(v.posicionActual.y)
                    .disponibleDesde(v.disponibleDesde != null ? v.disponibleDesde.toString() : null)
                    .build());
        }

        List<AlmacenEstadoDTO> almacenesDto = new ArrayList<>();
        for (Almacen a : almacenes) {
            int capacidad = a.capacidadMaxima == Integer.MAX_VALUE ? 0 : a.capacidadMaxima;
            almacenesDto.add(AlmacenEstadoDTO.builder()
                    .id(a.id.name())
                    .capacidadMaxima(capacidad)
                    .stock(a.stockDisponible() == Integer.MAX_VALUE ? 0 : a.stockDisponible())
                    .ocupacion(0)
                    .build());
        }

        int pendientes = 0;
        for (Pedido p : pedidoRepository.listarTodos()) {
            if (p.momentoLlegada.isAfter(instante)) continue;
            if (p.completo()) continue;
            pendientes++;
        }

        return EstadoOperacionResponseDTO.builder()
                .momento(instante)
                .pedidosPendientes(pendientes)
                .bloqueosVigentes(bloqueoRepository.vigentesEn(instante))
                .vehiculos(vehiculos)
                .almacenes(almacenesDto)
                .build();
    }
}