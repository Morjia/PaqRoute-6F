package com.paqroute.backend.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Registro del archivo mensual de bloqueos (bloqueo.aamm.txt):
 *              ##d##h##m-##d##h##m:x1,y1,x2,y2,...,xn,yn. Es una poligonal abierta: los tramos
 *              bloqueados son cada par de puntos consecutivos.
 */
public final class Bloqueo {

    public final LocalDateTime inicio;
    public final LocalDateTime fin;
    public final List<Punto> poligonal;

    public Bloqueo(LocalDateTime inicio, LocalDateTime fin, List<Punto> poligonal) {
        this.inicio = inicio;
        this.fin = fin;
        this.poligonal = poligonal;
    }

    public boolean vigenteEn(LocalDateTime momento) {
        return !momento.isBefore(inicio) && momento.isBefore(fin);
    }

    public Set<Arista> aristasBloqueadas() {
        Set<Arista> aristas = new HashSet<>();
        for (int i = 0; i < poligonal.size() - 1; i++) {
            aristas.add(Arista.de(poligonal.get(i), poligonal.get(i + 1)));
        }
        return aristas;
    }

    public static Set<Arista> aristasVigentesEn(List<Bloqueo> bloqueos, LocalDateTime momento) {
        Set<Arista> vigentes = new HashSet<>();
        for (Bloqueo b : bloqueos) {
            if (b.vigenteEn(momento)) vigentes.addAll(b.aristasBloqueadas());
        }
        return vigentes;
    }
}