package com.paqroute.backend.mapper;

import com.paqroute.backend.dto.response.EntregaResponseDTO;
import com.paqroute.backend.dto.response.RutaResponseDTO;
import com.paqroute.backend.model.Entrega;
import com.paqroute.backend.model.Ruta;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Convierte entidades Ruta en DTOs de respuesta (visión del planificador).
 */
public final class RutaMapper {

    private RutaMapper() {
    }

    public static RutaResponseDTO toResponseDTO(Ruta ruta) {
        return RutaResponseDTO.builder()
                .vehiculoId(ruta.vehiculo.idUnidad)
                .vehiculoTipo(ruta.vehiculo.tipo.name())
                .almacenDespacho(ruta.almacenDespacho.id.name())
                .almacenRetorno(ruta.almacenRetorno != null ? ruta.almacenRetorno.id.name() : null)
                .cargaTotal(ruta.cargaTotal())
                .distanciaIdaKm(ruta.distanciaIdaKm)
                .distanciaRetornoKm(ruta.distanciaRetornoKm)
                .horaFinUltimaEntrega(ruta.horaFinUltimaEntrega)
                .horaLlegadaRetorno(ruta.horaLlegadaRetorno)
                .paradas(ruta.secuencia.stream().map(RutaMapper::toEntregaResponseDTO).collect(Collectors.toList()))
                .build();
    }

    public static EntregaResponseDTO toEntregaResponseDTO(Entrega entrega) {
        return EntregaResponseDTO.builder()
                .pedidoId(entrega.pedido.idCliente)
                .cantidad(entrega.cantidad)
                .parcial(entrega.esParcial())
                .build();
    }

    public static List<RutaResponseDTO> listar(List<Ruta> rutas) {
        return rutas.stream().map(RutaMapper::toResponseDTO).collect(Collectors.toList());
    }
}