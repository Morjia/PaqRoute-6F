package com.paqroute.backend.model;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Registro del archivo mensual de envíos (ventas.aaaamm.txt):
 *              ##d##h##m:posX,posY,cIdCliente,qq,hl. Con entregas parciales, cantidadPendiente
 *              se descuenta a medida que se programan las entregas.
 */
public final class Pedido {

    public final String idCliente;
    public final Punto ubicacion;
    public final int cantidadTotal;
    public final LocalDateTime momentoLlegada;
    public final double horasLimite;
    public final LocalDateTime fechaLimite;

    public int cantidadPendiente;
    /** Llegada de la última parte entregada; null mientras el pedido no esté completo. */
    public LocalDateTime horaCompletado;

    public Pedido(String idCliente, Punto ubicacion, int cantidadTotal,
                   LocalDateTime momentoLlegada, double horasLimite) {
        this.idCliente = idCliente;
        this.ubicacion = ubicacion;
        this.cantidadTotal = cantidadTotal;
        this.momentoLlegada = momentoLlegada;
        this.horasLimite = horasLimite;
        this.fechaLimite = momentoLlegada.plusMinutes(Math.round(horasLimite * 60));
        this.cantidadPendiente = cantidadTotal;
    }

    /** Copia fresca (sin avance) con cantidad = ceil(cantidad * lambda); horario, ubicación y plazo no cambian. */
    public Pedido copiaEscalada(double lambda) {
        int cantidad = lambda == 1.0 ? cantidadTotal : (int) Math.ceil(cantidadTotal * lambda - 1e-9);
        return new Pedido(idCliente, ubicacion, cantidad, momentoLlegada, horasLimite);
    }

    /** Porcentaje del plazo consumido al completarse: (horaCompletado - llegada) / plazo * 100. */
    public double consumoSlaPct() {
        double minutos = Duration.between(momentoLlegada, horaCompletado).toSeconds() / 60.0;
        return minutos / (horasLimite * 60.0) * 100.0;
    }

    public boolean completo() {
        return cantidadPendiente <= 0;
    }

    public boolean incumplido(LocalDateTime momentoActual) {
        return !completo() && momentoActual.isAfter(fechaLimite);
    }

    public static final double PLAZO_REGULAR_HORAS = 36;

    /** Identificador del pedido (la especificación distingue codigoPedido e idCliente). */
    public String getCodigoPedido() {
        return idCliente;
    }

    /** Cantidad de productos P. */
    public int getCantidadProductos() {
        return cantidadTotal;
    }

    /** Tipo de plazo: "Regular" (36h) o "Priorizado" (4/8/12/18h). */
    public String getTipoPlazo() {
        return horasLimite == PLAZO_REGULAR_HORAS ? "Regular" : "Priorizado";
    }

    public int getPlazoMaximoHoras() {
        return (int) Math.round(horasLimite);
    }

    public boolean esPriorizado() {
        return horasLimite != PLAZO_REGULAR_HORAS;
    }

    public LocalDateTime getFechaHoraRegistro() {
        return momentoLlegada;
    }

    /** Estado del pedido como cadena (Entregado / Pendiente). */
    public String getEstado() {
        return completo() ? "Entregado" : "Pendiente";
    }

    public boolean esPlazoVencido(LocalDateTime horaActual) {
        return incumplido(horaActual);
    }

    public boolean validarPlazoCritico() {
        return horasLimite <= 8;
    }

    @Override
    public String toString() {
        return idCliente + ubicacion + "(pend=" + cantidadPendiente + "/" + cantidadTotal + ", limite=" + fechaLimite + ")";
    }
}