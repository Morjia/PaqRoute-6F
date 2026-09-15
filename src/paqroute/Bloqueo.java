package paqroute;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Registro del archivo mensual de bloqueos (bloqueo.aamm.txt):
 * ##d##h##m-##d##h##m:x1,y1,x2,y2,x3,y3,...,xn,yn
 * Ejemplo: 01d06h00m-01d15h00m:31,21,34,21
 *
 * Es una poligonal abierta: los tramos bloqueados son cada par de puntos consecutivos.
 */
public final class Bloqueo {

    public final LocalDateTime inicio;
    public final LocalDateTime fin;
    public final List<Punto> poligonal;

    public Bloqueo(LocalDateTime inicio, LocalDateTime fin, List<Punto> poligonal) {
        this.inicio = inicio;
        this.fin = fin;
        this.poligonal = poligonal;
    }

    public boolean vigenteEn(LocalDateTime momento) {
        return !momento.isBefore(inicio) && momento.isBefore(fin);
    }

    public Set<Arista> aristasBloqueadas() {
        Set<Arista> aristas = new HashSet<>();
        for (int i = 0; i < poligonal.size() - 1; i++) {
            aristas.add(Arista.de(poligonal.get(i), poligonal.get(i + 1)));
        }
        return aristas;
    }

    public static Set<Arista> aristasVigentesEn(List<Bloqueo> bloqueos, LocalDateTime momento) {
        Set<Arista> vigentes = new HashSet<>();
        for (Bloqueo b : bloqueos) {
            if (b.vigenteEn(momento)) vigentes.addAll(b.aristasBloqueadas());
        }
        return vigentes;
    }
}
