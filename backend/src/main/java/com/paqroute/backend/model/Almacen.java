package com.paqroute.backend.model;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Almacén de despacho del producto P. El central tiene capacidad infinita; los
 *              intermedios, 1000 unidades con recarga diaria.
 */
public final class Almacen {

    public enum Id { CENTRAL, NOROESTE, ESTE }

    public final Id id;
    public final Punto ubicacion;
    public final int capacidadMaxima; // Integer.MAX_VALUE para el central (infinito)
    private int stock;

    public Almacen(Id id, Punto ubicacion, int capacidadMaxima) {
        this.id = id;
        this.ubicacion = ubicacion;
        this.capacidadMaxima = capacidadMaxima;
        this.stock = capacidadMaxima;
    }

    public int stockDisponible() {
        return stock;
    }

    public boolean tieneStock(int cantidad) {
        return stock >= cantidad;
    }

    /** Descuenta stock al despachar una carga. El almacén central nunca se agota (capacidad infinita). */
    public void retirar(int cantidad) {
        if (id == Id.CENTRAL) return;
        if (cantidad > stock) {
            throw new IllegalStateException("Stock insuficiente en almacen " + id + ": pidio " + cantidad + ", hay " + stock);
        }
        stock -= cantidad;
    }

    /** Recarga instantánea diaria: el almacén vuelve a su capacidad máxima. */
    public void recargar() {
        this.stock = capacidadMaxima;
    }

    public String getIdAlmacen() {
        return id.name();
    }

    public String getTipoAlmacen() {
        return id == Id.CENTRAL ? "Central" : "Intermedio";
    }

    public double getLatitudUbicacion() {
        return ubicacion.x;
    }

    public double getLongitudUbicacion() {
        return ubicacion.y;
    }

    public int getInventarioActual() {
        return stock;
    }

    /** Restablece el inventario (recarga diaria). */
    public void recargarInventario() {
        recargar();
    }

    /** Descuenta inventario si hay y confirma. */
    public boolean despacharPaquetes(int cantidad) {
        if (!tieneStock(cantidad)) return false;
        retirar(cantidad);
        return true;
    }

    /** Porcentaje de ocupación del almacén. */
    public double getNivelSaturacion() {
        double cap = capacidadMaxima == Integer.MAX_VALUE ? 0.0 : capacidadMaxima;
        return cap <= 0 ? 0.0 : stock * 100.0 / cap;
    }

    @Override
    public String toString() {
        return id + ubicacion.toString() + "[stock=" + (capacidadMaxima == Integer.MAX_VALUE ? "inf" : stock) + "]";
    }
}