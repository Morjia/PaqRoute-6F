package com.paqroute.backend.model;

import com.paqroute.backend.engine.ResultadoSimulacion;
import java.time.LocalDateTime;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Registro de una ejecución de escenario (historial de simulaciones) con su resultado.
 */
public final class HistorialSimulacion {

    private Integer id;
    private String tipo;
    private String algoritmo;
    private long semilla;
    private double lambda;
    private LocalDateTime desde;
    private LocalDateTime fechaEjecucion;
    private boolean colapso;
    private LocalDateTime momentoColapso;
    private String clientePedidoColapsado;
    private int pedidosIngresados;
    private int pedidosCompletadosATiempo;
    private int entregasTotales;
    private int entregasParciales;
    private double costoAcumuladoSoles;
    private double kilometrosRecorridos;
    private int invocacionesAlgoritmo;
    private double tiempoAlgoritmoPromedioMs;
    private long tiempoAlgoritmoMaximoMs;
    private Double consumoSlaPromedioPct;

    public static HistorialSimulacion desdeResultado(Integer id, String tipo, String algoritmo, long semilla,
                                                      double lambda, LocalDateTime desde, ResultadoSimulacion r) {
        HistorialSimulacion h = new HistorialSimulacion();
        h.id = id;
        h.tipo = tipo;
        h.algoritmo = algoritmo;
        h.semilla = semilla;
        h.lambda = lambda;
        h.desde = desde;
        h.fechaEjecucion = LocalDateTime.now();
        h.colapso = r.colapso;
        h.momentoColapso = r.momentoColapso;
        h.clientePedidoColapsado = r.clientePedidoColapsado;
        h.pedidosIngresados = r.pedidosIngresados;
        h.pedidosCompletadosATiempo = r.pedidosCompletadosATiempo;
        h.entregasTotales = r.entregasTotales;
        h.entregasParciales = r.entregasParciales;
        h.costoAcumuladoSoles = r.costoAcumuladoSoles;
        h.kilometrosRecorridos = r.kilometrosRecorridos;
        h.invocacionesAlgoritmo = r.invocacionesAlgoritmo;
        h.tiempoAlgoritmoPromedioMs = r.tiempoAlgoritmoPromedioMs();
        h.tiempoAlgoritmoMaximoMs = r.tiempoAlgoritmoMaximoMs;
        h.consumoSlaPromedioPct = Double.isNaN(r.consumoSlaPromedioPct) ? null : r.consumoSlaPromedioPct;
        return h;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTipo() {
        return tipo;
    }

    public String getAlgoritmo() {
        return algoritmo;
    }

    public long getSemilla() {
        return semilla;
    }

    public double getLambda() {
        return lambda;
    }

    public LocalDateTime getDesde() {
        return desde;
    }

    public LocalDateTime getFechaEjecucion() {
        return fechaEjecucion;
    }

    public boolean isColapso() {
        return colapso;
    }

    public LocalDateTime getMomentoColapso() {
        return momentoColapso;
    }

    public String getClientePedidoColapsado() {
        return clientePedidoColapsado;
    }

    public int getPedidosIngresados() {
        return pedidosIngresados;
    }

    public int getPedidosCompletadosATiempo() {
        return pedidosCompletadosATiempo;
    }

    public int getEntregasTotales() {
        return entregasTotales;
    }

    public int getEntregasParciales() {
        return entregasParciales;
    }

    public double getCostoAcumuladoSoles() {
        return costoAcumuladoSoles;
    }

    public double getKilometrosRecorridos() {
        return kilometrosRecorridos;
    }

    public int getInvocacionesAlgoritmo() {
        return invocacionesAlgoritmo;
    }

    public double getTiempoAlgoritmoPromedioMs() {
        return tiempoAlgoritmoPromedioMs;
    }

    public long getTiempoAlgoritmoMaximoMs() {
        return tiempoAlgoritmoMaximoMs;
    }

    public Double getConsumoSlaPromedioPct() {
        return consumoSlaPromedioPct;
    }
}