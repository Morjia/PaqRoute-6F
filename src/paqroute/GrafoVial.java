package paqroute;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Cuadricula vial de PaqRoute (70 km x 50 km, nodos cada 1 km, sin diagonales, doble sentido).
 * Calcula la distancia mas corta entre dos nodos mediante BFS (todos los tramos miden 1 km),
 * excluyendo los tramos bloqueados vigentes en el instante consultado: un arco que conduce a un
 * tramo bloqueado simplemente se excluye de la busqueda, por lo que la ruta resultante nunca lo
 * atraviesa (equivalente a la regla de "vuelta en U": la unidad jamas entra a ese tramo).
 */
public final class GrafoVial {

    public static final int ANCHO_X = 70;
    public static final int ALTO_Y = 50;

    private final Map<Punto, int[]> cacheDistancias = new HashMap<>();
    private Set<Arista> bloqueosVigentesActuales;

    /** Debe llamarse al cambiar el conjunto de tramos bloqueados vigentes (p.ej. en cada tick de simulacion). */
    public void actualizarBloqueosVigentes(Set<Arista> bloqueosVigentes) {
        this.bloqueosVigentesActuales = bloqueosVigentes;
        this.cacheDistancias.clear();
    }

    public int distanciaKm(Punto origen, Punto destino) {
        if (origen.equals(destino)) return 0;
        int[] distancias = distanciasDesde(origen);
        int dist = distancias[destino.idNodo(ANCHO_X)];
        if (dist == Integer.MAX_VALUE) {
            throw new IllegalStateException("No existe camino entre " + origen + " y " + destino
                    + " con los bloqueos vigentes (no deberia ocurrir: los bloqueos son poligonos abiertos)");
        }
        return dist;
    }

    /** BFS de un origen hacia todos los nodos de la cuadricula, respetando los bloqueos vigentes. Se cachea por origen. */
    private int[] distanciasDesde(Punto origen) {
        int[] cacheada = cacheDistancias.get(origen);
        if (cacheada != null) return cacheada;

        int totalNodos = (ANCHO_X + 1) * (ALTO_Y + 1);
        int[] dist = new int[totalNodos];
        Arrays.fill(dist, Integer.MAX_VALUE);
        int idOrigen = origen.idNodo(ANCHO_X);
        dist[idOrigen] = 0;

        Deque<Punto> cola = new ArrayDeque<>();
        cola.add(origen);
        while (!cola.isEmpty()) {
            Punto actual = cola.poll();
            int distActual = dist[actual.idNodo(ANCHO_X)];
            for (Punto vecino : vecinos(actual)) {
                if (bloqueosVigentesActuales != null
                        && bloqueosVigentesActuales.contains(Arista.de(actual, vecino))) {
                    continue; // tramo bloqueado: no se puede atravesar
                }
                int idVecino = vecino.idNodo(ANCHO_X);
                if (dist[idVecino] == Integer.MAX_VALUE) {
                    dist[idVecino] = distActual + 1;
                    cola.add(vecino);
                }
            }
        }
        cacheDistancias.put(origen, dist);
        return dist;
    }

    private Punto[] vecinos(Punto p) {
        java.util.List<Punto> lista = new java.util.ArrayList<>(4);
        if (p.x > 0) lista.add(new Punto(p.x - 1, p.y));
        if (p.x < ANCHO_X) lista.add(new Punto(p.x + 1, p.y));
        if (p.y > 0) lista.add(new Punto(p.x, p.y - 1));
        if (p.y < ALTO_Y) lista.add(new Punto(p.x, p.y + 1));
        return lista.toArray(new Punto[0]);
    }
}
