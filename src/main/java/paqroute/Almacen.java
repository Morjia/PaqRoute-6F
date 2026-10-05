package paqroute;

/** Almacen de despacho del producto P. El central tiene capacidad infinita; los intermedios, 1000 unidades con recarga diaria. */
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

    /** Descuenta stock al despachar una carga. El almacen central nunca se agota (capacidad infinita). */
    public void retirar(int cantidad) {
        if (id == Id.CENTRAL) return;
        if (cantidad > stock) {
            throw new IllegalStateException("Stock insuficiente en almacen " + id + ": pidio " + cantidad + ", hay " + stock);
        }
        stock -= cantidad;
    }

    /** Recarga instantanea diaria: el almacen vuelve a su capacidad maxima. */
    public void recargar() {
        this.stock = capacidadMaxima;
    }

    @Override
    public String toString() {
        return id + ubicacion.toString() + "[stock=" + (capacidadMaxima == Integer.MAX_VALUE ? "inf" : stock) + "]";
    }
}
