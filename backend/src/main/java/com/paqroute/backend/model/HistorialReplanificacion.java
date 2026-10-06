package com.paqroute.backend.model;

import java.time.LocalDateTime;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Registro de una replanificación disparada por una incidencia (log de eventos).
 */
public final class HistorialReplanificacion {

    private Integer id;
    private Integer incidenciaId;
    private String tipo;
    private String detalle;
    private int rutasEvaluadas;
    private LocalDateTime fecha;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getIncidenciaId() {
        return incidenciaId;
    }

    public void setIncidenciaId(Integer incidenciaId) {
        this.incidenciaId = incidenciaId;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getDetalle() {
        return detalle;
    }

    public void setDetalle(String detalle) {
        this.detalle = detalle;
    }

    public int getRutasEvaluadas() {
        return rutasEvaluadas;
    }

    public void setRutasEvaluadas(int rutasEvaluadas) {
        this.rutasEvaluadas = rutasEvaluadas;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }
}