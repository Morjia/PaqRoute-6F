package paqroute;

import java.util.ArrayList;
import java.util.List;

/** Conjunto de rutas decididas en un tick de planificacion (no toda la simulacion, solo esta pasada). */
public final class Solucion {

    public final List<Ruta> rutas = new ArrayList<>();

    public double costoTotal() {
        double total = 0.0;
        for (Ruta r : rutas) total += r.costo();
        return total;
    }

    public int totalEntregas() {
        int total = 0;
        for (Ruta r : rutas) total += r.secuencia.size();
        return total;
    }
}
