package paqroute;

/** Flota heterogenea de PaqRoute: codigo (para el id TTNN), capacidad (paquetes), velocidad (km/h) y costo por km (S/). */
public enum TipoVehiculo {

    AUTO("TA", 24, 40.0, 8.00),
    MOTO("TM", 8, 25.0, 6.00),
    BICICLETA("TB", 4, 12.0, 3.00);

    public final String codigo;
    public final int capacidad;
    public final double velocidadKmH;
    public final double costoPorKm;

    TipoVehiculo(String codigo, int capacidad, double velocidadKmH, double costoPorKm) {
        this.codigo = codigo;
        this.capacidad = capacidad;
        this.velocidadKmH = velocidadKmH;
        this.costoPorKm = costoPorKm;
    }

    public static TipoVehiculo porCodigo(String codigo) {
        for (TipoVehiculo t : values()) if (t.codigo.equals(codigo)) return t;
        throw new IllegalArgumentException("Codigo de tipo de vehiculo desconocido: " + codigo);
    }
}
