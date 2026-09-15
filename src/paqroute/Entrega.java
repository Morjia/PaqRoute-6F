package paqroute;

/**
 * Una parada de una ruta: entrega (total o parcial) de un pedido. Cada Entrega, sin importar
 * la cantidad que transporte, consume 1 hora de acondicionamiento (dato de la situacion autentica).
 */
public final class Entrega {

    public final Pedido pedido;
    public final int cantidad;

    public Entrega(Pedido pedido, int cantidad) {
        if (cantidad <= 0) throw new IllegalArgumentException("La cantidad de una entrega debe ser positiva");
        this.pedido = pedido;
        this.cantidad = cantidad;
    }

    public boolean esParcial() {
        return cantidad < pedido.cantidadTotal;
    }

    @Override
    public String toString() {
        return pedido.idCliente + "(" + cantidad + (esParcial() ? "/parcial" : "") + ")";
    }
}
