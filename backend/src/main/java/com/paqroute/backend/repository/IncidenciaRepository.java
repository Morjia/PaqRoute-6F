package com.paqroute.backend.repository;

import com.paqroute.backend.model.HistorialReplanificacion;
import com.paqroute.backend.model.Incidencia;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Repository;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Repositorio en memoria de incidencias registradas y su historial de replanificación.
 */
@Repository
public class IncidenciaRepository {

    private final List<Incidencia> incidencias = new ArrayList<>();
    private final List<HistorialReplanificacion> historial = new ArrayList<>();
    private final AtomicInteger secuencia = new AtomicInteger(0);

    public synchronized Incidencia guardar(Incidencia incidencia) {
        if (incidencia.getId() == null) {
            incidencia.setId(secuencia.incrementAndGet());
        }
        incidencias.add(incidencia);
        return incidencia;
    }

    public synchronized HistorialReplanificacion registrarReplanificacion(HistorialReplanificacion registro) {
        registro.setId(secuencia.incrementAndGet());
        historial.add(registro);
        return registro;
    }

    public synchronized List<Incidencia> listarIncidencias() {
        return new ArrayList<>(incidencias);
    }

    public synchronized List<HistorialReplanificacion> listarHistorial() {
        return new ArrayList<>(historial);
    }
}