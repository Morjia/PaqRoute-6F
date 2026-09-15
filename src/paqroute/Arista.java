package paqroute;

import java.util.Objects;

/** Tramo de calle entre dos nodos adyacentes de la cuadricula (no dirigido: doble sentido). */
public final class Arista {

    public final Punto a;
    public final Punto b;

    private Arista(Punto a, Punto b) {
        this.a = a;
        this.b = b;
    }

    /** Forma canonica: no importa el orden en que se pase a y b. */
    public static Arista de(Punto p1, Punto p2) {
        if (p1.x < p2.x || (p1.x == p2.x && p1.y < p2.y)) return new Arista(p1, p2);
        return new Arista(p2, p1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Arista)) return false;
        Arista ar = (Arista) o;
        return a.equals(ar.a) && b.equals(ar.b);
    }

    @Override
    public int hashCode() {
        return Objects.hash(a, b);
    }
}
