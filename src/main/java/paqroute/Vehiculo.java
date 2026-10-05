package paqroute;

import java.time.LocalDateTime;

/** Unidad de transporte concreta de la flota fija (id TTNN, p.ej. TA01, TM07, TB12). */
public final class Vehiculo {

    public enum Estado { LIBRE, EN_RUTA, MANTENIMIENTO }

    public final String id;
    public final TipoVehiculo tipo;
    public Punto posicionActual;
    public LocalDateTime disponibleDesde;
    public Estado estado;

    public Vehiculo(String id, TipoVehiculo tipo, Punto posicionInicial, LocalDateTime disponibleDesde) {
        this.id = id;
        this.tipo = tipo;
        this.posicionActual = posicionInicial;
        this.disponibleDesde = disponibleDesde;
        this.estado = Estado.LIBRE;
    }

    public boolean estaLibreEn(LocalDateTime momento) {
        return estado == Estado.LIBRE && !momento.isBefore(disponibleDesde);
    }

    @Override
    public String toString() {
        return id;
    }
}
