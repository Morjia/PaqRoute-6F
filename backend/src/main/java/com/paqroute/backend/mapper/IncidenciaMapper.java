package com.paqroute.backend.mapper;

import com.paqroute.backend.dto.response.HistorialReplanificacionResponseDTO;
import com.paqroute.backend.dto.response.IncidenciaResponseDTO;
import com.paqroute.backend.model.HistorialReplanificacion;
import com.paqroute.backend.model.Incidencia;
import com.paqroute.backend.model.Punto;
import java.util.ArrayList;
import java.util.List;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Convierte entidades Incidencia/HistorialReplanificacion en DTOs de respuesta.
 */
public final class IncidenciaMapper {

    private IncidenciaMapper() {
    }

    public static IncidenciaResponseDTO toResponseDTO(Incidencia incidencia) {
        return IncidenciaResponseDTO.builder()
                .id(incidencia.getId())
                .tipo(incidencia.getTipo() != null ? incidencia.getTipo().name() : null)
                .motivo(incidencia.getMotivo())
                .duracionEstimadaHoras(incidencia.getDuracionEstimadaHoras())
                .desde(incidencia.getDesde())
                .vehiculoId(incidencia.getVehiculoId())
                .resuelta(incidencia.isResuelta())
                .fechaRegistro(incidencia.getFechaRegistro())
                .poligonal(poligonalA(incidencia.getPoligonal()))
                .build();
    }

    public static HistorialReplanificacionResponseDTO toHistorialResponseDTO(HistorialReplanificacion registro) {
        return HistorialReplanificacionResponseDTO.builder()
                .id(registro.getId())
                .incidenciaId(registro.getIncidenciaId())
                .tipo(registro.getTipo())
                .detalle(registro.getDetalle())
                .rutasEvaluadas(registro.getRutasEvaluadas())
                .fecha(registro.getFecha())
                .build();
    }

    private static List<List<Integer>> poligonalA(List<Punto> poligonal) {
        if (poligonal == null) return null;
        List<List<Integer>> resultado = new ArrayList<>();
        for (Punto p : poligonal) {
            resultado.add(List.of(p.x, p.y));
        }
        return resultado;
    }
}