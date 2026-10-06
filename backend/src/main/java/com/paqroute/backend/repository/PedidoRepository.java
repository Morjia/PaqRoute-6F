package com.paqroute.backend.repository;

import com.paqroute.backend.model.Pedido;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Repositorio en memoria de los pedidos (ventas.aaaamm.txt) cargados al arranque.
 */
@Repository
public class PedidoRepository {

    private List<Pedido> base = List.of();

    public void reemplazar(List<Pedido> pedidos) {
        this.base = new ArrayList<>(pedidos);
    }

    public List<Pedido> listarTodos() {
        return base;
    }

    public int total() {
        return base.size();
    }

    /** Copia fresca sin avanzar (Pedido es mutable): necesaria para cada corrida de simulación. */
    public List<Pedido> copiaFresca() {
        List<Pedido> copia = new ArrayList<>(base.size());
        for (Pedido p : base) copia.add(p.copiaEscalada(1.0));
        return copia;
    }

    /** Registra un pedido individual (alta por API). */
    public synchronized void registrar(Pedido pedido) {
        this.base = new ArrayList<>(base);
        base.add(pedido);
    }

    /** Clientes distintos presentes en las ventas cargadas (idCliente -> nº de pedidos). */
    public java.util.Map<String, Long> clientes() {
        java.util.Map<String, Long> mapa = new java.util.TreeMap<>();
        for (Pedido p : base) mapa.merge(p.idCliente, 1L, Long::sum);
        return mapa;
    }
}