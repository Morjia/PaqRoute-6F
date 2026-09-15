package paqroute;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Registro de un archivo bimensual de mantenimiento preventivo (mant.preventivo.aa.m1-m2.txt):
 * aaaammdd:TTNN
 * Ejemplo: 20260901:TA01
 *
 * La unidad no esta disponible para programacion de rutas desde las 00:00 de esa fecha hasta que
 * termina su mantenimiento; la duracion depende del tipo: bicicleta 1 turno (8h), moto 1 dia (24h),
 * auto 2 dias (48h).
 */
public final class Mantenimiento {

    public final LocalDate fecha;
    public final String vehiculoId;
    public final LocalDateTime inicio;
    public final LocalDateTime fin;

    public Mantenimiento(LocalDate fecha, String vehiculoId) {
        this.fecha = fecha;
        this.vehiculoId = vehiculoId;
        this.inicio = fecha.atStartOfDay();
        TipoVehiculo tipo = TipoVehiculo.porCodigo(vehiculoId.substring(0, 2));
        long horasDuracion = switch (tipo) {
            case BICICLETA -> 8L;
            case MOTO -> 24L;
            case AUTO -> 48L;
        };
        this.fin = inicio.plusHours(horasDuracion);
    }

    public boolean vigenteEn(LocalDateTime momento) {
        return !momento.isBefore(inicio) && momento.isBefore(fin);
    }
}
