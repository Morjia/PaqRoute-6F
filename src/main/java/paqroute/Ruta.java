package paqroute;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Ruta de reparto concreta: un vehiculo real, despachado desde un almacen real, que visita una secuencia de entregas. */
public final class Ruta {

    public Vehiculo vehiculo;
    public final Almacen almacenDespacho;
    public final LocalDateTime horaInicio;
    public final List<Entrega> secuencia = new ArrayList<>();

    public Almacen almacenRetorno;
    public double distanciaIdaKm;
    public double distanciaRetornoKm;
    public LocalDateTime horaFinUltimaEntrega;
    public LocalDateTime horaLlegadaRetorno;

    public Ruta(Vehiculo vehiculo, Almacen almacenDespacho, LocalDateTime horaInicio) {
        this.vehiculo = vehiculo;
        this.almacenDespacho = almacenDespacho;
        this.horaInicio = horaInicio;
    }

    public int cargaTotal() {
        int total = 0;
        for (Entrega e : secuencia) total += e.cantidad;
        return total;
    }

    public double costo() {
        return RutaUtil.costoRuta(distanciaIdaKm, distanciaRetornoKm, vehiculo.tipo);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(vehiculo.id + " desde " + almacenDespacho.id);
        for (Entrega e : secuencia) sb.append(" -> ").append(e);
        sb.append(" -> ").append(almacenRetorno.id);
        return sb.toString();
    }
}
