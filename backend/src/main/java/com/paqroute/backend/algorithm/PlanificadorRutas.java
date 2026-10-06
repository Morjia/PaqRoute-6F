package com.paqroute.backend.algorithm;

import com.paqroute.backend.exception.BusinessException;
import com.paqroute.backend.model.Incidencia;
import com.paqroute.backend.model.Pedido;
import com.paqroute.backend.model.Ruta;
import com.paqroute.backend.model.UnidadTransporte;
import java.util.ArrayList;
import java.util.List;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Orquestador de planificación/replanificación (diseño de clases). Delega en la
 *              estrategia de enrutamiento seleccionada (ACS o GRASP-VNS) y "genera" rutas.
 */
public final class PlanificadorRutas {

    private IEstrategiaEnrutamiento estrategia;
    private List<Pedido> pedidosActuales = List.of();
    private List<UnidadTransporte> flotaActual = List.of();
    private List<Ruta> ultimasRutas = new ArrayList<>();

    public void setEstrategia(IEstrategiaEnrutamiento estrategia) {
        this.estrategia = estrategia;
    }

    public IEstrategiaEnrutamiento getEstrategia() {
        return estrategia;
    }

    /** Ejecuta la planificación con las entradas ya asignadas. */
    public void ejecutarPlanificacion() {
        ejecutarPlanificacion(pedidosActuales, flotaActual);
    }

    /** Ejecuta la planificación generando las rutas con la estrategia. */
    public List<Ruta> ejecutarPlanificacion(List<Pedido> pedidos, List<UnidadTransporte> flota) {
        if (estrategia == null) {
            throw new BusinessException("No se ha definido una estrategia de enrutamiento");
        }
        this.pedidosActuales = new ArrayList<>(pedidos);
        this.flotaActual = new ArrayList<>(flota);
        this.ultimasRutas = new ArrayList<>(estrategia.generarRutas(pedidos, flota));
        return new ArrayList<>(ultimasRutas);
    }

    /** Replanifica las rutas afectadas ante una incidencia. */
    public List<Ruta> gestionarIncidencia(Incidencia incidencia) {
        if (estrategia == null) {
            throw new BusinessException("No se ha definido una estrategia de enrutamiento");
        }
        List<Ruta> replanificadas = new ArrayList<>();
        for (Ruta ruta : ultimasRutas) {
            replanificadas.addAll(estrategia.replanificar(ruta, incidencia));
        }
        this.ultimasRutas = replanificadas;
        return new ArrayList<>(ultimasRutas);
    }

    public List<Ruta> getUltimasRutas() {
        return new ArrayList<>(ultimasRutas);
    }
}