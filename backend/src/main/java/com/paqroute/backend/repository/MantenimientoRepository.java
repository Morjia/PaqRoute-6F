package com.paqroute.backend.repository;

import com.paqroute.backend.model.Mantenimiento;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Repositorio en memoria del mantenimiento preventivo de vehículos
 *              (mant.preventivo.aa.m1-m2.txt).
 */
@Repository
public class MantenimientoRepository {

    private List<Mantenimiento> base = List.of();

    public void reemplazar(List<Mantenimiento> mantenimientos) {
        this.base = new ArrayList<>(mantenimientos);
    }

    public List<Mantenimiento> listarTodos() {
        return base;
    }

    public int total() {
        return base.size();
    }
}