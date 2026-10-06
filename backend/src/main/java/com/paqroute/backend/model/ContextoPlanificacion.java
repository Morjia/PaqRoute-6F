package com.paqroute.backend.model;

import com.paqroute.backend.engine.GrafoVial;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Estado del mundo en el instante de un tick de planificación: almacenes, grafo
 *              con bloqueos vigentes y hora actual.
 */
public final class ContextoPlanificacion {

    public final List<Almacen> almacenes;
    public final GrafoVial grafo;
    public final LocalDateTime horaActual;

    public ContextoPlanificacion(List<Almacen> almacenes, GrafoVial grafo, LocalDateTime horaActual) {
        this.almacenes = almacenes;
        this.grafo = grafo;
        this.horaActual = horaActual;
    }

    /** Almacén más cercano a un punto que tenga al menos la cantidad de stock pedida (el central siempre califica). */
    public Almacen almacenMasCercanoConStock(Punto punto, int cantidadNecesaria) {
        Almacen mejor = null;
        int mejorDistancia = Integer.MAX_VALUE;
        for (Almacen a : almacenes) {
            if (!a.tieneStock(cantidadNecesaria)) continue;
            int distancia = grafo.distanciaKm(punto, a.ubicacion);
            if (distancia < mejorDistancia) {
                mejorDistancia = distancia;
                mejor = a;
            }
        }
        return mejor;
    }
}