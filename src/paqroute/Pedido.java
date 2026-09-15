package paqroute;

import java.time.LocalDateTime;

/**
 * Registro del archivo mensual de envios (ventas.aaaamm.txt):
 * ##d##h##m:posX,posY,cIdCliente,qq,hl
 * Ejemplo: 11d13h31m:45,43,c9167,12,36
 *
 * Como ahora se permiten entregas parciales, un mismo pedido puede repartirse en varias
 * Entrega distintas (posiblemente en vehiculos y momentos distintos); cantidadPendiente
 * se va descontando a medida que se programan esas entregas.
 */
public final class Pedido {

    public final String idCliente;
    public final Punto ubicacion;
    public final int cantidadTotal;
    public final LocalDateTime momentoLlegada;
    public final double horasLimite;
    public final LocalDateTime fechaLimite;

    public int cantidadPendiente;

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

    public boolean completo() {
        return cantidadPendiente <= 0;
    }

    public boolean incumplido(LocalDateTime momentoActual) {
        return !completo() && momentoActual.isAfter(fechaLimite);
    }

    @Override
    public String toString() {
        return idCliente + ubicacion + "(pend=" + cantidadPendiente + "/" + cantidadTotal + ", limite=" + fechaLimite + ")";
    }
}
