package com.paqroute.backend.model;

import com.paqroute.backend.enums.TipoIncidencia;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Incidencia operativa (bloqueo de calle o falla vehicular) registrada en tiempo de
 *              ejecución; se mezcla en las corridas de simulación para disparar replanificación.
 */
public final class Incidencia {

    private Integer id;
    private TipoIncidencia tipo;
    private String motivo;
    /** Duración estimada de la afectación, en horas. */
    private double duracionEstimadaHoras;
    private LocalDateTime desde;
    /** Polilínea de puntos bloqueados (solo BLOQUEO_CALLE). */
    private List<Punto> poligonal;
    /** Vehículo afectado (solo FALLA_VEHICULAR). */
    private String vehiculoId;
    private boolean resuelta = false;
    private LocalDateTime fechaRegistro;

    public String getIdIncidencia() {
        return id != null ? String.valueOf(id) : null;
    }

    public String getTipoIncidencia() {
        return tipo != null ? tipo.name() : null;
    }

    /** Duración estimada en minutos. */
    public int getDuracionEstimada() {
        return (int) Math.round(duracionEstimadaHoras * 60);
    }

    /** Coordenada del incidente (primer punto de la polilínea, si aplica). */
    public double getLatitud() {
        return poligonal != null && !poligonal.isEmpty() ? poligonal.get(0).x : 0.0;
    }

    public double getLongitud() {
        return poligonal != null && !poligonal.isEmpty() ? poligonal.get(0).y : 0.0;
    }

    public boolean estaActiva() {
        return !resuelta;
    }

    public void resolverIncidencia() {
        this.resuelta = true;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public TipoIncidencia getTipo() {
        return tipo;
    }

    public void setTipo(TipoIncidencia tipo) {
        this.tipo = tipo;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public double getDuracionEstimadaHoras() {
        return duracionEstimadaHoras;
    }

    public void setDuracionEstimadaHoras(double duracionEstimadaHoras) {
        this.duracionEstimadaHoras = duracionEstimadaHoras;
    }

    public LocalDateTime getDesde() {
        return desde;
    }

    public void setDesde(LocalDateTime desde) {
        this.desde = desde;
    }

    public List<Punto> getPoligonal() {
        return poligonal;
    }

    public void setPoligonal(List<Punto> poligonal) {
        this.poligonal = poligonal;
    }

    public String getVehiculoId() {
        return vehiculoId;
    }

    public void setVehiculoId(String vehiculoId) {
        this.vehiculoId = vehiculoId;
    }

    public boolean isResuelta() {
        return resuelta;
    }

    public void setResuelta(boolean resuelta) {
        this.resuelta = resuelta;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(LocalDateTime fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }
}