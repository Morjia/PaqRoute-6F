package com.paqroute.backend.algorithm;

import com.paqroute.backend.model.Incidencia;
import com.paqroute.backend.model.Pedido;
import com.paqroute.backend.model.Ruta;
import com.paqroute.backend.model.UnidadTransporte;
import java.util.List;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Contrato común de las estrategias de enrutamiento (patrón Strategy). La
 *              implementan AntColonySystemVRP (ACS) y GraspVnsVRP (GRASP-VNS), las dos
 *              metaheurísticas del planificador.
 */
public interface IEstrategiaEnrutamiento {

    List<Ruta> generarRutas(List<Pedido> pedidos, List<UnidadTransporte> flota);

    List<Ruta> replanificar(Ruta rutaAfectada, Incidencia incidencia);
}