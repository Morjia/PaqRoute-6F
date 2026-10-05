package paqroute;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Memoria de feromonas de ACS entre ticks (variante opcional, desactivada por defecto).
 *
 * Sin memoria, cada tick arranca con la matriz de feromonas en su valor inicial (feromonaInicial).
 * Con memoria, al terminar un tick se guarda el REFUERZO aprendido (feromona final normalizada por
 * feromonaInicial, menos el nivel base al que la evaporacion deja las aristas no reforzadas) solo
 * para las aristas claramente reforzadas, y el tick siguiente parte de feromonaInicial * (1 +
 * retencion * refuerzo) en esas aristas, siempre que ambos pedidos sigan pendientes. Los pedidos se
 * identifican por identidad de objeto (el simulador reutiliza los mismos Pedido entre ticks); el
 * nodo 0 (inicio de ruta) tiene id 0.
 *
 * Una instancia pertenece a UNA simulacion (no es segura entre hilos ni se comparte entre corridas).
 */
public final class MemoriaFeromonas {

    /** Solo se recuerdan aristas cuyo refuerzo supera el nivel base en al menos esta fraccion de feromonaInicial. */
    static final double UMBRAL_REFUERZO = 0.5;

    private final IdentityHashMap<Pedido, Integer> ids = new IdentityHashMap<>();
    private int siguienteId = 1; // 0 = nodo virtual "inicio de ruta"
    private Map<Long, Double> refuerzo = new HashMap<>();

    private static long clave(int a, int b) {
        int lo = Math.min(a, b), hi = Math.max(a, b);
        return ((long) lo << 32) | (hi & 0xffffffffL);
    }

    private int idDe(Pedido p) {
        return ids.computeIfAbsent(p, k -> siguienteId++);
    }

    /** Siembra la matriz del tick actual con lo recordado; solo aristas cuyos dos extremos siguen pendientes. */
    void cargarEn(double[][] feromona, Map<Pedido, Integer> indice, double feromonaInicial, double retencion) {
        if (refuerzo.isEmpty()) return;
        Map<Integer, Integer> nodoPorId = new HashMap<>();
        nodoPorId.put(0, 0);
        for (Map.Entry<Pedido, Integer> e : indice.entrySet()) {
            Integer id = ids.get(e.getKey());
            if (id != null) nodoPorId.put(id, e.getValue());
        }
        for (Map.Entry<Long, Double> e : refuerzo.entrySet()) {
            int a = (int) (e.getKey() >>> 32);
            int b = (int) (e.getKey() & 0xffffffffL);
            Integer na = nodoPorId.get(a), nb = nodoPorId.get(b);
            if (na == null || nb == null) continue;
            double valor = feromonaInicial * (1.0 + retencion * e.getValue());
            feromona[na][nb] = valor;
            feromona[nb][na] = valor;
        }
    }

    /** Reemplaza la memoria por el refuerzo de este tick (lo de pedidos que ya no estan pendientes se olvida). */
    void guardarDesde(double[][] feromona, List<Pedido> pedidos, double feromonaInicial, double nivelBase) {
        int n = pedidos.size();
        int[] idNodo = new int[n + 1];
        idNodo[0] = 0;
        for (int i = 0; i < n; i++) idNodo[i + 1] = idDe(pedidos.get(i));

        Map<Long, Double> nuevo = new HashMap<>();
        for (int i = 0; i <= n; i++) {
            for (int j = i + 1; j <= n; j++) {
                double exceso = feromona[i][j] / feromonaInicial - nivelBase;
                if (exceso >= UMBRAL_REFUERZO) nuevo.put(clave(idNodo[i], idNodo[j]), exceso);
            }
        }
        refuerzo = nuevo;
    }
}
