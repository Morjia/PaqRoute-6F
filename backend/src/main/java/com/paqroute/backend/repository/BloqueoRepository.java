package com.paqroute.backend.repository;

import com.paqroute.backend.model.Bloqueo;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Repositorio en memoria de los bloqueos de calles (bloqueo.aamm.txt).
 */
@Repository
public class BloqueoRepository {

    private List<Bloqueo> base = List.of();

    public void reemplazar(List<Bloqueo> bloqueos) {
        this.base = new ArrayList<>(bloqueos);
    }

    public List<Bloqueo> listarTodos() {
        return base;
    }

    public int total() {
        return base.size();
    }

    /** Cantidad de bloqueos vigentes en un instante dado. */
    public int vigentesEn(LocalDateTime momento) {
        int count = 0;
        for (Bloqueo b : base) {
            if (b.vigenteEn(momento)) count++;
        }
        return count;
    }
}