package com.paqroute.backend.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Ruta de reparto concreta: un vehículo real, despachado desde un almacén real,
 *              que visita una secuencia de entregas.
 */
public final class Ruta {

    public UnidadTransporte vehiculo;
    public final Almacen almacenDespacho;
    public final LocalDateTime horaInicio;
    public final List<Entrega> secuencia = new ArrayList<>();

    public Almacen almacenRetorno;
    public double distanciaIdaKm;
    public double distanciaRetornoKm;
    public LocalDateTime horaFinUltimaEntrega;
    public LocalDateTime horaLlegadaRetorno;

    public Ruta(UnidadTransporte vehiculo, Almacen almacenDespacho, LocalDateTime horaInicio) {
        this.vehiculo = vehiculo;
        this.almacenDespacho = almacenDespacho;
        this.horaInicio = horaInicio;
    }

    public int cargaTotal() {
        int total = 0;
        for (Entrega e : secuencia) total += e.cantidad;
        return total;
    }

    public double costo() {
        return com.paqroute.backend.engine.RutaUtil.costoRuta(distanciaIdaKm, distanciaRetornoKm, vehiculo.tipo);
    }

    public String getCodigoRuta() {
        return vehiculo != null ? vehiculo.idUnidad : "RUTA-?";
    }

    public UnidadTransporte getVehiculoAsignado() {
        return vehiculo;
    }

    public Almacen getAlmacenOrigen() {
        return almacenDespacho;
    }

    /** Pedidos (destinos) de la ruta, sin duplicados por entrega parcial. */
    public List<Pedido> getPedidosAsignados() {
        List<Pedido> pedidos = new ArrayList<>();
        for (Entrega e : secuencia) {
            if (!pedidos.contains(e.pedido)) pedidos.add(e.pedido);
        }
        return pedidos;
    }

    public LocalDateTime getTiempoEstimadoFin() {
        return horaFinUltimaEntrega;
    }

    public double getCostoTotalRuta() {
        return costo();
    }

    public double calcularCostoAcumulado() {
        return costo();
    }

    /** Agrega un pedido solo si no excede la capacidad de la unidad. */
    public boolean agregarPedido(Pedido pedido) {
        int espacioLibre = vehiculo.tipo.capacidad - cargaTotal();
        if (espacioLibre <= 0) return false;
        int cantidad = Math.min(pedido.cantidadPendiente, espacioLibre);
        if (cantidad <= 0) return false;
        secuencia.add(new Entrega(pedido, cantidad));
        return true;
    }

    /** Recalcular tiempos ante una incidencia (la replanificaci�n la ejecuta el motor). */
    public void recalcularTiempos(Incidencia incidencia) {
        // El motor de ruteo vuelve a evaluar la secuencia con los bloqueos vigentes.
    }

    /** Finaliza la ruta: pedidos a "Entregado" y libera la unidad. */
    public void finalizarRuta() {
        for (Entrega e : secuencia) {
            e.pedido.cantidadPendiente = 0;
            e.pedido.horaCompletado = horaFinUltimaEntrega;
        }
        if (vehiculo != null) {
            vehiculo.estado = UnidadTransporte.Estado.LIBRE;
            vehiculo.disponibleDesde = horaInicio;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(vehiculo.idUnidad + " desde " + almacenDespacho.id);
        for (Entrega e : secuencia) sb.append(" -> ").append(e);
        sb.append(" -> ").append(almacenRetorno.id);
        return sb.toString();
    }
}