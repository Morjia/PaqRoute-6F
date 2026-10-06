package com.paqroute.backend.repository;

import com.paqroute.backend.model.HistorialSimulacion;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Repository;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Repositorio en memoria del historial de simulaciones ejecutadas.
 */
@Repository
public class HistorialSimulacionRepository {

    private final List<HistorialSimulacion> historial = new ArrayList<>();
    private final AtomicInteger secuencia = new AtomicInteger(0);

    public synchronized HistorialSimulacion guardar(HistorialSimulacion registro) {
        if (registro.getId() == null) {
            registro.setId(secuencia.incrementAndGet());
        }
        historial.add(registro);
        return registro;
    }

    public synchronized List<HistorialSimulacion> listar() {
        List<HistorialSimulacion> copia = new ArrayList<>(historial);
        copia.sort(Comparator.comparing(HistorialSimulacion::getFechaEjecucion));
        return copia;
    }

    public synchronized List<HistorialSimulacion> porAlgoritmo(String algoritmo) {
        List<HistorialSimulacion> resultado = new ArrayList<>();
        for (HistorialSimulacion h : historial) {
            if (h.getAlgoritmo().equalsIgnoreCase(algoritmo)) resultado.add(h);
        }
        resultado.sort(Comparator.comparing(HistorialSimulacion::getFechaEjecucion));
        return resultado;
    }
}