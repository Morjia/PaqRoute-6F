package paqroute;

import java.util.ArrayList;
import java.util.List;

/**
 * Busqueda de vecindad variable (VND) aplicada, de forma OPCIONAL, a la mejor solucion que arma ACS
 * en un tick (variante experimental "ACS con busqueda local"; activar con
 * {@link Simulador.ParametrosAlgoritmo#conBusquedaLocalAcs}).
 *
 * Usa las mismas 4 vecindades que GraspVnsVRP -- relocate, swap, 2-opt, cambio de tipo de vehiculo --
 * en el mismo orden y con la misma politica (primera mejora, reinicia en N1 tras cada mejora), para
 * que una eventual mejora de ACS se pueda atribuir a tener busqueda local y no a una vecindad
 * distinta o una politica distinta.
 *
 * A diferencia de GraspVnsVRP (que lleva la cuenta de stock de forma incremental durante la
 * construccion), aqui el stock disponible de cada almacen se recalcula desde cero antes de cada
 * movimiento que cambia de almacen, sumando la carga de las rutas que quedarian despachando desde
 * el -- para no arrastrar errores de contabilidad incremental (ver AntColonySystemVRP/GraspVnsVRP).
 */
final class MejoraLocalAcs {

    private MejoraLocalAcs() {}

    /** Mejora {@code solucion} in-place. {@code vehiculosLibresOriginal} es la flota libre del tick completo. */
    static void mejorar(Solucion solucion, List<Vehiculo> vehiculosLibresOriginal, ContextoPlanificacion ctx) {
        if (solucion.rutas.isEmpty()) return;
        List<Vehiculo> vehiculosLibresRestantes = new ArrayList<>(vehiculosLibresOriginal);
        for (Ruta r : solucion.rutas) vehiculosLibresRestantes.remove(r.vehiculo);

        int k = 1;
        while (k <= 4) {
            boolean mejoro = switch (k) {
                case 1 -> intentarRelocate(solucion, ctx);
                case 2 -> intentarSwap(solucion, ctx);
                case 3 -> intentarDosOpt(solucion, ctx);
                case 4 -> intentarCambioTipoVehiculo(solucion, vehiculosLibresRestantes, ctx);
                default -> false;
            };
            k = mejoro ? 1 : k + 1;
        }
    }

    /** Stock que le queda a {@code almacen} sin contar lo que ya usa {@code excluir} (si se le da mas, cabe). */
    private static int stockDisponible(Almacen almacen, List<Ruta> rutas, Ruta excluir) {
        int comprometido = 0;
        for (Ruta r : rutas) {
            if (r == excluir) continue;
            if (r.almacenDespacho.id == almacen.id) comprometido += r.cargaTotal();
        }
        return almacen.stockDisponible() - comprometido;
    }

    private static boolean intentarRelocate(Solucion solucion, ContextoPlanificacion ctx) {
        List<Ruta> rutas = solucion.rutas;
        for (Ruta origen : rutas) {
            for (int i = 0; i < origen.secuencia.size(); i++) {
                Entrega entrega = origen.secuencia.get(i);
                for (Ruta destino : rutas) {
                    if (destino == origen && origen.secuencia.size() == 1) continue;
                    int espacioLibre = destino.vehiculo.tipo.capacidad - destino.cargaTotal()
                            + (destino == origen ? entrega.cantidad : 0);
                    if (espacioLibre < entrega.cantidad) continue;

                    // stockDisponible(...,destino) excluye lo que destino YA usa, asi que hay que compararlo
                    // contra la carga TOTAL que destino tendria despues del movimiento, no solo la entrega.
                    boolean cambiaAlmacen = destino != origen && destino.almacenDespacho.id != origen.almacenDespacho.id;
                    if (cambiaAlmacen && stockDisponible(destino.almacenDespacho, rutas, destino) < destino.cargaTotal() + entrega.cantidad) continue;

                    for (int j = 0; j <= destino.secuencia.size(); j++) {
                        if (destino == origen && (j == i || j == i + 1)) continue;

                        List<Entrega> nuevaOrigen = new ArrayList<>(origen.secuencia);
                        nuevaOrigen.remove(i);
                        List<Entrega> nuevaDestino = (destino == origen) ? nuevaOrigen : new ArrayList<>(destino.secuencia);
                        int posInsercion = (destino == origen && j > i) ? j - 1 : j;
                        nuevaDestino.add(posInsercion, entrega);
                        if (nuevaOrigen.isEmpty()) continue; // una ruta no puede quedar vacia

                        double costoAntes = costoSecuencia(origen, origen.secuencia, ctx)
                                + (destino == origen ? 0 : costoSecuencia(destino, destino.secuencia, ctx));
                        RutaUtil.Evaluacion evOrigen = RutaUtil.evaluar(nuevaOrigen, origen.vehiculo.tipo, origen.almacenDespacho.ubicacion, ctx.horaActual, ctx.grafo);
                        RutaUtil.Evaluacion evDestino = (destino == origen) ? evOrigen
                                : RutaUtil.evaluar(nuevaDestino, destino.vehiculo.tipo, destino.almacenDespacho.ubicacion, ctx.horaActual, ctx.grafo);
                        if (!evOrigen.factible() || !evDestino.factible()) continue;

                        double costoDespues = evOrigen.distanciaIdaKm() * origen.vehiculo.tipo.costoPorKm
                                + (destino == origen ? 0 : evDestino.distanciaIdaKm() * destino.vehiculo.tipo.costoPorKm);
                        if (costoDespues < costoAntes - 1e-9) {
                            origen.secuencia.clear(); origen.secuencia.addAll(nuevaOrigen);
                            if (destino != origen) { destino.secuencia.clear(); destino.secuencia.addAll(nuevaDestino); }
                            recalcularRuta(origen, ctx);
                            if (destino != origen) recalcularRuta(destino, ctx);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean intentarSwap(Solucion solucion, ContextoPlanificacion ctx) {
        List<Ruta> rutas = solucion.rutas;
        for (int a = 0; a < rutas.size(); a++) {
            Ruta rutaA = rutas.get(a);
            for (int b = a + 1; b < rutas.size(); b++) {
                Ruta rutaB = rutas.get(b);
                double costoAntes = costoSecuencia(rutaA, rutaA.secuencia, ctx) + costoSecuencia(rutaB, rutaB.secuencia, ctx);

                for (int i = 0; i < rutaA.secuencia.size(); i++) {
                    for (int j = 0; j < rutaB.secuencia.size(); j++) {
                        Entrega ei = rutaA.secuencia.get(i);
                        Entrega ej = rutaB.secuencia.get(j);
                        int cargaA = rutaA.cargaTotal() - ei.cantidad + ej.cantidad;
                        int cargaB = rutaB.cargaTotal() - ej.cantidad + ei.cantidad;
                        if (cargaA > rutaA.vehiculo.tipo.capacidad || cargaB > rutaB.vehiculo.tipo.capacidad) continue;

                        boolean cambiaAlmacen = rutaA.almacenDespacho.id != rutaB.almacenDespacho.id;
                        if (cambiaAlmacen) {
                            int dispA = stockDisponible(rutaA.almacenDespacho, rutas, rutaA);
                            int dispB = stockDisponible(rutaB.almacenDespacho, rutas, rutaB);
                            if (dispA < cargaA || dispB < cargaB) continue;
                        }

                        List<Entrega> nuevaA = new ArrayList<>(rutaA.secuencia); nuevaA.set(i, ej);
                        List<Entrega> nuevaB = new ArrayList<>(rutaB.secuencia); nuevaB.set(j, ei);
                        RutaUtil.Evaluacion evA = RutaUtil.evaluar(nuevaA, rutaA.vehiculo.tipo, rutaA.almacenDespacho.ubicacion, ctx.horaActual, ctx.grafo);
                        RutaUtil.Evaluacion evB = RutaUtil.evaluar(nuevaB, rutaB.vehiculo.tipo, rutaB.almacenDespacho.ubicacion, ctx.horaActual, ctx.grafo);
                        if (!evA.factible() || !evB.factible()) continue;

                        double costoDespues = evA.distanciaIdaKm() * rutaA.vehiculo.tipo.costoPorKm
                                + evB.distanciaIdaKm() * rutaB.vehiculo.tipo.costoPorKm;
                        if (costoDespues < costoAntes - 1e-9) {
                            rutaA.secuencia.clear(); rutaA.secuencia.addAll(nuevaA);
                            rutaB.secuencia.clear(); rutaB.secuencia.addAll(nuevaB);
                            recalcularRuta(rutaA, ctx); recalcularRuta(rutaB, ctx);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean intentarDosOpt(Solucion solucion, ContextoPlanificacion ctx) {
        for (Ruta ruta : solucion.rutas) {
            int m = ruta.secuencia.size();
            if (m < 3) continue;
            double costoAntes = costoSecuencia(ruta, ruta.secuencia, ctx);
            for (int i = 0; i < m - 1; i++) {
                for (int j = i + 1; j < m; j++) {
                    List<Entrega> nueva = new ArrayList<>(ruta.secuencia);
                    java.util.Collections.reverse(nueva.subList(i, j + 1));
                    RutaUtil.Evaluacion ev = RutaUtil.evaluar(nueva, ruta.vehiculo.tipo, ruta.almacenDespacho.ubicacion, ctx.horaActual, ctx.grafo);
                    if (!ev.factible()) continue;
                    double costoDespues = ev.distanciaIdaKm() * ruta.vehiculo.tipo.costoPorKm;
                    if (costoDespues < costoAntes - 1e-9) {
                        ruta.secuencia.clear(); ruta.secuencia.addAll(nueva);
                        recalcularRuta(ruta, ctx);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** N4: migra una ruta a un vehiculo libre mas barato si sigue siendo factible (no cambia el almacen de despacho). */
    private static boolean intentarCambioTipoVehiculo(Solucion solucion, List<Vehiculo> vehiculosLibresRestantes, ContextoPlanificacion ctx) {
        TipoVehiculo[] porCosto = {TipoVehiculo.BICICLETA, TipoVehiculo.MOTO, TipoVehiculo.AUTO};
        for (Ruta ruta : solucion.rutas) {
            for (TipoVehiculo candidato : porCosto) {
                if (candidato == ruta.vehiculo.tipo) break;
                if (candidato.costoPorKm >= ruta.vehiculo.tipo.costoPorKm) continue;
                if (ruta.cargaTotal() > candidato.capacidad) continue;

                Vehiculo libre = vehiculosLibresRestantes.stream().filter(v -> v.tipo == candidato).findFirst().orElse(null);
                if (libre == null) continue;
                RutaUtil.Evaluacion ev = RutaUtil.evaluar(ruta.secuencia, candidato, ruta.almacenDespacho.ubicacion, ctx.horaActual, ctx.grafo);
                if (!ev.factible()) continue;

                vehiculosLibresRestantes.remove(libre);
                vehiculosLibresRestantes.add(ruta.vehiculo);
                ruta.vehiculo = libre;
                recalcularRuta(ruta, ctx);
                return true;
            }
        }
        return false;
    }

    private static void recalcularRuta(Ruta ruta, ContextoPlanificacion ctx) {
        RutaUtil.Evaluacion ev = RutaUtil.evaluar(ruta.secuencia, ruta.vehiculo.tipo, ruta.almacenDespacho.ubicacion, ctx.horaActual, ctx.grafo);
        ruta.distanciaIdaKm = ev.distanciaIdaKm();
        ruta.horaFinUltimaEntrega = ev.horaFinUltimaEntrega();
        Punto puntoFinal = ruta.secuencia.get(ruta.secuencia.size() - 1).pedido.ubicacion;
        Almacen retorno = ctx.almacenMasCercanoConStock(puntoFinal, 1);
        if (retorno == null) retorno = ctx.almacenes.stream().filter(a -> a.id == Almacen.Id.CENTRAL).findFirst().orElseThrow();
        ruta.almacenRetorno = retorno;
        ruta.distanciaRetornoKm = RutaUtil.distanciaRetornoKm(puntoFinal, retorno, ctx.grafo);
        double horasRetorno = ruta.distanciaRetornoKm / ruta.vehiculo.tipo.velocidadKmH;
        ruta.horaLlegadaRetorno = ruta.horaFinUltimaEntrega.plusSeconds(Math.round(horasRetorno * 3600));
    }

    private static double costoSecuencia(Ruta ruta, List<Entrega> secuencia, ContextoPlanificacion ctx) {
        RutaUtil.Evaluacion ev = RutaUtil.evaluar(secuencia, ruta.vehiculo.tipo, ruta.almacenDespacho.ubicacion, ctx.horaActual, ctx.grafo);
        return ev.distanciaIdaKm() * ruta.vehiculo.tipo.costoPorKm;
    }
}
