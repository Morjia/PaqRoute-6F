package paqroute;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * GRASP con Busqueda por Vecindad Variable (GRASP-VNS) aplicado a un TICK de planificacion del
 * simulador de PaqRoute: igual que AntColonySystemVRP, decide que rutas despachar ahora con flota
 * fija, 3 almacenes con stock y entregas parciales, pero mediante construccion golosa aleatorizada
 * (RCL) seguida de busqueda de vecindad variable (relocate, swap, 2-opt, cambio de tipo de vehiculo).
 */
public final class GraspVnsVRP {

    private final List<Pedido> pedidos;
    private final List<Vehiculo> vehiculosLibres;
    private final ContextoPlanificacion ctx;
    private final Random rnd;

    private final double alfaGRASP;
    private final int maxIteracionesGRASP;
    private final int numVecindades = 4;

    public GraspVnsVRP(List<Pedido> pedidosPendientes, List<Vehiculo> vehiculosLibres,
                        ContextoPlanificacion ctx, long semilla,
                        double alfaGRASP, int maxIteracionesGRASP) {
        this.pedidos = pedidosPendientes;
        this.vehiculosLibres = vehiculosLibres;
        this.ctx = ctx;
        this.rnd = new Random(semilla);
        this.alfaGRASP = alfaGRASP;
        this.maxIteracionesGRASP = maxIteracionesGRASP;
    }

    public Solucion resolverTick() {
        Solucion mejorGlobal = null;
        double costoMejorGlobal = Double.MAX_VALUE;

        for (int it = 0; it < maxIteracionesGRASP; it++) {
            Estado estado = new Estado();
            construccionGolosaAleatorizada(estado);
            busquedaVND(estado);

            double costo = estado.solucion.costoTotal();
            if (costo < costoMejorGlobal) {
                costoMejorGlobal = costo;
                mejorGlobal = estado.solucion;
            }
        }
        return mejorGlobal != null ? mejorGlobal : new Solucion();
    }

    /** Estado mutable de una construccion+mejora: solucion parcial, vehiculos aun libres y stock restante. */
    private final class Estado {
        final Solucion solucion = new Solucion();
        final List<Vehiculo> vehiculosLibresRestantes;
        final Map<Almacen.Id, Integer> stockLocal = new HashMap<>();
        final Map<Pedido, Integer> restanteLocal = new HashMap<>();

        Estado() {
            vehiculosLibresRestantes = new ArrayList<>(vehiculosLibres);
            for (Almacen a : ctx.almacenes) stockLocal.put(a.id, a.stockDisponible());
            for (Pedido p : pedidos) restanteLocal.put(p, p.cantidadPendiente);
        }
    }

    // ---------------------------------------------------------------
    // FASE 1: Construccion golosa aleatorizada (GRASP)
    // ---------------------------------------------------------------

    private static final class Candidata {
        Ruta rutaExistente;
        Almacen almacenNuevaRuta;
        Vehiculo vehiculoNuevaRuta;
        int cantidad;
        double costoMarginal;
    }

    private void construccionGolosaAleatorizada(Estado estado) {
        List<Pedido> ordenPrioridad = new ArrayList<>(pedidos);
        ordenPrioridad.sort((a, b) -> a.fechaLimite.compareTo(b.fechaLimite));

        for (Pedido p : ordenPrioridad) {
            while (estado.restanteLocal.get(p) > 0) {
                List<Candidata> candidatas = generarCandidatas(estado, p);
                if (candidatas.isEmpty()) break; // no cabe (ahora); el resto queda pendiente para otro tick

                candidatas.sort((a, b) -> Double.compare(a.costoMarginal, b.costoMarginal));
                int tamRCL = Math.max(1, (int) Math.round(alfaGRASP * candidatas.size()));
                Candidata elegida = candidatas.get(rnd.nextInt(tamRCL));
                aplicarCandidata(estado, p, elegida);
            }
        }
    }

    private List<Candidata> generarCandidatas(Estado estado, Pedido p) {
        List<Candidata> candidatas = new ArrayList<>();
        int restante = estado.restanteLocal.get(p);

        for (Ruta ruta : estado.solucion.rutas) {
            int espacioLibre = ruta.vehiculo.tipo.capacidad - ruta.cargaTotal();
            if (espacioLibre <= 0) continue;
            int cantidad = Math.min(restante, espacioLibre);
            List<Entrega> tentativa = new ArrayList<>(ruta.secuencia);
            tentativa.add(new Entrega(p, cantidad));
            RutaUtil.Evaluacion antes = RutaUtil.evaluar(ruta.secuencia, ruta.vehiculo.tipo, ruta.almacenDespacho.ubicacion, ctx.horaActual, ctx.grafo);
            RutaUtil.Evaluacion despues = RutaUtil.evaluar(tentativa, ruta.vehiculo.tipo, ruta.almacenDespacho.ubicacion, ctx.horaActual, ctx.grafo);
            if (!despues.factible()) continue;

            Candidata c = new Candidata();
            c.rutaExistente = ruta;
            c.cantidad = cantidad;
            c.costoMarginal = (despues.distanciaIdaKm() - antes.distanciaIdaKm()) * ruta.vehiculo.tipo.costoPorKm;
            candidatas.add(c);
        }

        Almacen almacenCercano = almacenMasCercanoConStockLocal(estado, p.ubicacion);
        if (almacenCercano != null) {
            for (Vehiculo libre : estado.vehiculosLibresRestantes) {
                int cantidad = Math.min(restante, libre.tipo.capacidad);
                RutaUtil.Evaluacion ev = RutaUtil.evaluar(List.of(new Entrega(p, cantidad)), libre.tipo,
                        almacenCercano.ubicacion, ctx.horaActual, ctx.grafo);
                if (!ev.factible()) continue;
                double distanciaRetorno = RutaUtil.distanciaRetornoKm(p.ubicacion, almacenCercano, ctx.grafo);
                Candidata c = new Candidata();
                c.almacenNuevaRuta = almacenCercano;
                c.vehiculoNuevaRuta = libre;
                c.cantidad = cantidad;
                c.costoMarginal = (ev.distanciaIdaKm() + distanciaRetorno) * libre.tipo.costoPorKm;
                candidatas.add(c);
            }
        }
        return candidatas;
    }

    private void aplicarCandidata(Estado estado, Pedido p, Candidata elegida) {
        if (elegida.rutaExistente != null) {
            elegida.rutaExistente.secuencia.add(new Entrega(p, elegida.cantidad));
            recalcularRuta(elegida.rutaExistente);
        } else {
            Ruta nueva = new Ruta(elegida.vehiculoNuevaRuta, elegida.almacenNuevaRuta, ctx.horaActual);
            nueva.secuencia.add(new Entrega(p, elegida.cantidad));
            recalcularRuta(nueva);
            estado.solucion.rutas.add(nueva);
            estado.vehiculosLibresRestantes.remove(elegida.vehiculoNuevaRuta);
            estado.stockLocal.merge(elegida.almacenNuevaRuta.id, -elegida.cantidad, Integer::sum);
        }
        estado.restanteLocal.put(p, estado.restanteLocal.get(p) - elegida.cantidad);
    }

    private Almacen almacenMasCercanoConStockLocal(Estado estado, Punto punto) {
        Almacen mejor = null;
        int mejorDistancia = Integer.MAX_VALUE;
        for (Almacen a : ctx.almacenes) {
            if (estado.stockLocal.getOrDefault(a.id, 0) <= 0) continue;
            int d = ctx.grafo.distanciaKm(punto, a.ubicacion);
            if (d < mejorDistancia) { mejorDistancia = d; mejor = a; }
        }
        return mejor;
    }

    /** Recalcula distancia/hora de una ruta tras insertar o modificar su secuencia de entregas. */
    private void recalcularRuta(Ruta ruta) {
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

    // ---------------------------------------------------------------
    // FASE 2: Busqueda de Vecindad Variable (VND)
    // ---------------------------------------------------------------

    private void busquedaVND(Estado estado) {
        int k = 1;
        while (k <= numVecindades) {
            boolean mejoro = switch (k) {
                case 1 -> intentarRelocate(estado);
                case 2 -> intentarSwap(estado);
                case 3 -> intentarDosOpt(estado);
                case 4 -> intentarCambioTipoVehiculo(estado);
                default -> false;
            };
            k = mejoro ? 1 : k + 1;
        }
    }

    private boolean intentarRelocate(Estado estado) {
        List<Ruta> rutas = estado.solucion.rutas;
        for (Ruta origen : rutas) {
            for (int i = 0; i < origen.secuencia.size(); i++) {
                Entrega entrega = origen.secuencia.get(i);
                for (Ruta destino : rutas) {
                    if (destino == origen && origen.secuencia.size() == 1) continue;
                    int espacioLibre = destino.vehiculo.tipo.capacidad - destino.cargaTotal()
                            + (destino == origen ? entrega.cantidad : 0);
                    if (espacioLibre < entrega.cantidad) continue;
                    for (int j = 0; j <= destino.secuencia.size(); j++) {
                        if (destino == origen && (j == i || j == i + 1)) continue;

                        List<Entrega> nuevaOrigen = new ArrayList<>(origen.secuencia);
                        nuevaOrigen.remove(i);
                        List<Entrega> nuevaDestino = (destino == origen) ? nuevaOrigen : new ArrayList<>(destino.secuencia);
                        int posInsercion = (destino == origen && j > i) ? j - 1 : j;
                        nuevaDestino.add(posInsercion, entrega);
                        if (nuevaOrigen.isEmpty()) continue; // una ruta no puede quedar vacia

                        double costoAntes = costoSecuencia(origen, origen.secuencia) + (destino == origen ? 0 : costoSecuencia(destino, destino.secuencia));
                        RutaUtil.Evaluacion evOrigen = RutaUtil.evaluar(nuevaOrigen, origen.vehiculo.tipo, origen.almacenDespacho.ubicacion, ctx.horaActual, ctx.grafo);
                        RutaUtil.Evaluacion evDestino = (destino == origen) ? evOrigen
                                : RutaUtil.evaluar(nuevaDestino, destino.vehiculo.tipo, destino.almacenDespacho.ubicacion, ctx.horaActual, ctx.grafo);
                        if (!evOrigen.factible() || !evDestino.factible()) continue;

                        double costoDespues = evOrigen.distanciaIdaKm() * origen.vehiculo.tipo.costoPorKm
                                + (destino == origen ? 0 : evDestino.distanciaIdaKm() * destino.vehiculo.tipo.costoPorKm);
                        if (costoDespues < costoAntes - 1e-9) {
                            origen.secuencia.clear(); origen.secuencia.addAll(nuevaOrigen);
                            if (destino != origen) { destino.secuencia.clear(); destino.secuencia.addAll(nuevaDestino); }
                            recalcularRuta(origen);
                            if (destino != origen) recalcularRuta(destino);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean intentarSwap(Estado estado) {
        List<Ruta> rutas = estado.solucion.rutas;
        for (int a = 0; a < rutas.size(); a++) {
            Ruta rutaA = rutas.get(a);
            for (int b = a + 1; b < rutas.size(); b++) {
                Ruta rutaB = rutas.get(b);
                double costoAntes = costoSecuencia(rutaA, rutaA.secuencia) + costoSecuencia(rutaB, rutaB.secuencia);

                for (int i = 0; i < rutaA.secuencia.size(); i++) {
                    for (int j = 0; j < rutaB.secuencia.size(); j++) {
                        Entrega ei = rutaA.secuencia.get(i);
                        Entrega ej = rutaB.secuencia.get(j);
                        int cargaA = rutaA.cargaTotal() - ei.cantidad + ej.cantidad;
                        int cargaB = rutaB.cargaTotal() - ej.cantidad + ei.cantidad;
                        if (cargaA > rutaA.vehiculo.tipo.capacidad || cargaB > rutaB.vehiculo.tipo.capacidad) continue;

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
                            recalcularRuta(rutaA); recalcularRuta(rutaB);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean intentarDosOpt(Estado estado) {
        for (Ruta ruta : estado.solucion.rutas) {
            int m = ruta.secuencia.size();
            if (m < 3) continue;
            double costoAntes = costoSecuencia(ruta, ruta.secuencia);
            for (int i = 0; i < m - 1; i++) {
                for (int j = i + 1; j < m; j++) {
                    List<Entrega> nueva = new ArrayList<>(ruta.secuencia);
                    java.util.Collections.reverse(nueva.subList(i, j + 1));
                    RutaUtil.Evaluacion ev = RutaUtil.evaluar(nueva, ruta.vehiculo.tipo, ruta.almacenDespacho.ubicacion, ctx.horaActual, ctx.grafo);
                    if (!ev.factible()) continue;
                    double costoDespues = ev.distanciaIdaKm() * ruta.vehiculo.tipo.costoPorKm;
                    if (costoDespues < costoAntes - 1e-9) {
                        ruta.secuencia.clear(); ruta.secuencia.addAll(nueva);
                        recalcularRuta(ruta);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** N4: asignacion de capacidades y flota heterogenea -- migra una ruta a un vehiculo libre mas barato si sigue siendo factible. */
    private boolean intentarCambioTipoVehiculo(Estado estado) {
        TipoVehiculo[] porCosto = {TipoVehiculo.BICICLETA, TipoVehiculo.MOTO, TipoVehiculo.AUTO};
        for (Ruta ruta : estado.solucion.rutas) {
            for (TipoVehiculo candidato : porCosto) {
                if (candidato == ruta.vehiculo.tipo) break;
                if (candidato.costoPorKm >= ruta.vehiculo.tipo.costoPorKm) continue;
                if (ruta.cargaTotal() > candidato.capacidad) continue;

                Vehiculo libre = estado.vehiculosLibresRestantes.stream().filter(v -> v.tipo == candidato).findFirst().orElse(null);
                if (libre == null) continue;
                RutaUtil.Evaluacion ev = RutaUtil.evaluar(ruta.secuencia, candidato, ruta.almacenDespacho.ubicacion, ctx.horaActual, ctx.grafo);
                if (!ev.factible()) continue;

                estado.vehiculosLibresRestantes.remove(libre);
                estado.vehiculosLibresRestantes.add(ruta.vehiculo);
                ruta.vehiculo = libre;
                recalcularRuta(ruta);
                return true;
            }
        }
        return false;
    }

    private double costoSecuencia(Ruta ruta, List<Entrega> secuencia) {
        RutaUtil.Evaluacion ev = RutaUtil.evaluar(secuencia, ruta.vehiculo.tipo, ruta.almacenDespacho.ubicacion, ctx.horaActual, ctx.grafo);
        return ev.distanciaIdaKm() * ruta.vehiculo.tipo.costoPorKm;
    }
}
