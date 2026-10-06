package com.paqroute.backend.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Conjunto de rutas decididas en un tick de planificación (no toda la simulación).
 */
public final class Solucion {

    public final List<Ruta> rutas = new ArrayList<>();

    public double costoTotal() {
        double total = 0.0;
        for (Ruta r : rutas) total += r.costo();
        return total;
    }

    public int totalEntregas() {
        int total = 0;
        for (Ruta r : rutas) total += r.secuencia.size();
        return total;
    }
}