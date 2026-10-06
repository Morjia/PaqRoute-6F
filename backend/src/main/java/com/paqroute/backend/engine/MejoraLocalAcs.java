package com.paqroute.backend.engine;

import com.paqroute.backend.model.ContextoPlanificacion;
import com.paqroute.backend.model.Entrega;
import com.paqroute.backend.model.Punto;
import com.paqroute.backend.model.Ruta;
import com.paqroute.backend.model.Solucion;
import com.paqroute.backend.model.UnidadTransporte;
import java.util.ArrayList;
import java.util.List;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Búsqueda de vecindad variable (VND) aplicada, de forma OPCIONAL, a la mejor
 *              solución que arma ACS en un tick. Usa las mismas 4 vecindades que GraspVnsVRP
 *              (relocate, swap, 2-opt, cambio de tipo de vehículo) en el mismo orden y política.
 */
final class MejoraLocalAcs {

    private MejoraLocalAcs() {}

    static void mejorar(Solucion solucion, List<UnidadTransporte> vehiculosLibresOriginal, ContextoPlanificacion ctx) {
        if (solucion.rutas.isEmpty()) return;
        List<UnidadTransporte> vehiculosLibresRestantes = new ArrayList<>(vehiculosLibresOriginal);
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

    private static int stockDisponible(com.paqroute.backend.model.Almacen almacen, List<Ruta> rutas, Ruta excluir) {
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

                    boolean cambiaAlmacen = destino != origen && destino.almacenDespacho.id != origen.almacenDespacho.id;
                    if (cambiaAlmacen && stockDisponible(destino.almacenDespacho, rutas, destino) < destino.cargaTotal() + entrega.cantidad) continue;

                    for (int j = 0; j <= destino.secuencia.size(); j++) {
                        if (destino == origen && (j == i || j == i + 1)) continue;

                        List<Entrega> nuevaOrigen = new ArrayList<>(origen.secuencia);
                        nuevaOrigen.remove(i);
                        List<Entrega> nuevaDestino = (destino == origen) ? nuevaOrigen : new ArrayList<>(destino.secuencia);
                        int posInsercion = (destino == origen && j > i) ? j - 1 : j;
                        nuevaDestino.add(posInsercion, entrega);
                        if (nuevaOrigen.isEmpty()) continue;

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

    private static boolean intentarCambioTipoVehiculo(Solucion solucion, List<UnidadTransporte> vehiculosLibresRestantes, ContextoPlanificacion ctx) {
        com.paqroute.backend.enums.TipoVehiculo[] porCosto = {com.paqroute.backend.enums.TipoVehiculo.BICICLETA, com.paqroute.backend.enums.TipoVehiculo.MOTO, com.paqroute.backend.enums.TipoVehiculo.AUTO};
        for (Ruta ruta : solucion.rutas) {
            for (com.paqroute.backend.enums.TipoVehiculo candidato : porCosto) {
                if (candidato == ruta.vehiculo.tipo) break;
                if (candidato.costoPorKm >= ruta.vehiculo.tipo.costoPorKm) continue;
                if (ruta.cargaTotal() > candidato.capacidad) continue;

                UnidadTransporte libre = vehiculosLibresRestantes.stream().filter(v -> v.tipo == candidato).findFirst().orElse(null);
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
        com.paqroute.backend.model.Almacen retorno = ctx.almacenMasCercanoConStock(puntoFinal, 1);
        if (retorno == null) retorno = ctx.almacenes.stream().filter(a -> a.id == com.paqroute.backend.model.Almacen.Id.CENTRAL).findFirst().orElseThrow();
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