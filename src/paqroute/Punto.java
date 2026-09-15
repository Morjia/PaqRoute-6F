package paqroute;

import java.util.Objects;

/** Nodo (esquina) de la cuadricula vial. Distancia = Manhattan, ya que no hay calles diagonales ni curvas. */
public final class Punto {

    public final int x;
    public final int y;

    public Punto(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int distanciaManhattan(Punto otro) {
        return Math.abs(this.x - otro.x) + Math.abs(this.y - otro.y);
    }

    /** Identificador entero unico del nodo dentro de la cuadricula (usado por GrafoVial). */
    public int idNodo(int anchoX) {
        return y * (anchoX + 1) + x;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Punto)) return false;
        Punto p = (Punto) o;
        return x == p.x && y == p.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }
}
