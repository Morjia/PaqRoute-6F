package com.paqroute.backend.engine;

import com.paqroute.backend.model.Pedido;
import java.time.LocalDateTime;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Resumen del escenario de colapso: en qué momento, y con qué desempeño acumulado hasta ahí.
 */
public final class ResultadoSimulacion {
    public boolean colapso;
    public LocalDateTime momentoColapso;
    public String clientePedidoColapsado;
    public int pedidosIngresados;
    public int pedidosCompletadosATiempo;
    public int entregasTotales;
    public int entregasParciales;
    public double costoAcumuladoSoles;
    public double kilometrosRecorridos;
    public int ticksSimulados;

    /** Solo se llena si no hubo colapso: consumo promedio de SLA (%) sobre los pedidos del bloque. */
    public double consumoSlaPromedioPct = Double.NaN;
    public int pedidosCompletos;

    public double costoPorPedido() {
        return pedidosCompletos == 0 ? Double.NaN : costoAcumuladoSoles / pedidosCompletos;
    }

    /** Ta (tiempo de algoritmo): tiempo real medido en cada invocación del algoritmo, en ms. */
    public int invocacionesAlgoritmo;
    public long tiempoAlgoritmoTotalMs;
    public long tiempoAlgoritmoMaximoMs;

    public double tiempoAlgoritmoPromedioMs() {
        return invocacionesAlgoritmo == 0 ? 0.0 : (double) tiempoAlgoritmoTotalMs / invocacionesAlgoritmo;
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.US,
                "colapso=%s momento=%s cliente=%s | pedidos ingresados=%d completados a tiempo=%d | " +
                "entregas=%d (parciales=%d) | costo acumulado=S/ %.2f | km recorridos=%.1f | ticks=%d | " +
                "Ta promedio=%.1f ms, Ta maximo=%d ms (%d invocaciones)",
                colapso, momentoColapso, clientePedidoColapsado, pedidosIngresados, pedidosCompletadosATiempo,
                entregasTotales, entregasParciales, costoAcumuladoSoles, kilometrosRecorridos, ticksSimulados,
                tiempoAlgoritmoPromedioMs(), tiempoAlgoritmoMaximoMs, invocacionesAlgoritmo);
    }
}