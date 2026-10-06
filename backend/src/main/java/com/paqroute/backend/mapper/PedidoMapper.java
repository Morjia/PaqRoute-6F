package com.paqroute.backend.mapper;

import com.paqroute.backend.dto.response.PedidoResponseDTO;
import com.paqroute.backend.model.Pedido;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Convierte entidades Pedido en DTOs de respuesta.
 */
public final class PedidoMapper {

    private PedidoMapper() {
    }

    public static PedidoResponseDTO toResponseDTO(Pedido pedido) {
        return PedidoResponseDTO.builder()
                .codigo(pedido.idCliente)
                .idCliente(pedido.idCliente)
                .x(pedido.ubicacion.x)
                .y(pedido.ubicacion.y)
                .cantidadTotal(pedido.cantidadTotal)
                .cantidadPendiente(pedido.cantidadPendiente)
                .horasLimite(pedido.horasLimite)
                .momentoLlegada(pedido.momentoLlegada)
                .fechaLimite(pedido.fechaLimite)
                .completo(pedido.completo())
                .build();
    }
}