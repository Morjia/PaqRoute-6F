package com.paqroute.backend.model;

import com.paqroute.backend.enums.TipoVehiculo;
import java.time.LocalDateTime;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Clase abstracta de la flota (diagrama del diseño de clases). Una unidad de
 *              transporte (auto, moto o bicicleta) realiza la distribución física bajo turnos.
 *              Expone los atributos de la especificación (idUnidad, tipoUnidad, capacidadPaquetes,
 *              velocidadKmh, costoKm, disponible) y conserva el estado operativo del motor.
 */
public abstract class UnidadTransporte {

    public enum Estado { LIBRE, EN_RUTA, MANTENIMIENTO, FALLA }

    public final String idUnidad;
    public final TipoVehiculo tipo;
    public Punto posicionActual;
    public LocalDateTime disponibleDesde;
    public Estado estado;

    public UnidadTransporte(String idUnidad, TipoVehiculo tipo, Punto posicionInicial, LocalDateTime disponibleDesde) {
        this.idUnidad = idUnidad;
        this.tipo = tipo;
        this.posicionActual = posicionInicial;
        this.disponibleDesde = disponibleDesde;
        this.estado = Estado.LIBRE;
    }

    public String getIdUnidad() {
        return idUnidad;
    }

    /** Tipo de unidad: AUTO, MOTO o BICICLETA. */
    public String getTipoUnidad() {
        return tipo.name();
    }

    /** Capacidad en paquetes: 24 (auto), 8 (moto), 4 (bicicleta). */
    public int getCapacidadMaxima() {
        return tipo.capacidad;
    }

    public double getVelocidadPromedio() {
        return tipo.velocidadKmH;
    }

    public double getCostoPorKm() {
        return tipo.costoPorKm;
    }

    public boolean isDisponible() {
        return estado == Estado.LIBRE;
    }

    /** Estado operativo como cadena legible (Activa, En tránsito, En Mantenimiento, Falla). */
    public String getEstado() {
        return switch (estado) {
            case LIBRE -> "Activa";
            case EN_RUTA -> "En tránsito";
            case MANTENIMIENTO -> "En Mantenimiento";
            case FALLA -> "Falla";
        };
    }

    /** Espacio libre restante de la unidad. */
    public int getCapacidadDisponible(int ocupacion) {
        return Math.max(0, tipo.capacidad - ocupacion);
    }

    /** Costo de un tramo según el costo por km. */
    public double calcularCostoViaje(double distanciaKm) {
        return distanciaKm * tipo.costoPorKm;
    }

    /** Cambia el estado a "Falla". */
    public void reportarFalla() {
        this.estado = Estado.FALLA;
    }

    /** Reposiciona la unidad en un punto de la cuadrícula. */
    public void moverHacia(Punto destino) {
        this.posicionActual = destino;
    }

    public boolean estaLibreEn(LocalDateTime momento) {
        return estado == Estado.LIBRE && !momento.isBefore(disponibleDesde);
    }

    @Override
    public String toString() {
        return idUnidad;
    }
}