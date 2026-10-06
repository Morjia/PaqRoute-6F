package com.paqroute.backend.engine;

import com.paqroute.backend.algorithm.IEstrategiaEnrutamiento;
import com.paqroute.backend.enums.TipoVehiculo;
import com.paqroute.backend.model.Almacen;
import com.paqroute.backend.model.ContextoPlanificacion;
import com.paqroute.backend.model.Entrega;
import com.paqroute.backend.model.Incidencia;
import com.paqroute.backend.model.Pedido;
import com.paqroute.backend.model.Punto;
import com.paqroute.backend.model.Ruta;
import com.paqroute.backend.model.Solucion;
import com.paqroute.backend.model.UnidadTransporte;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Autor: Equipo PaqRap - PaqRoute
 * Fecha de creación: 2026-10-06
 * Descripción: Ant Colony System (ACS) aplicado a un TICK de planificación del simulador de
 *              PaqRoute: dado el estado actual (pedidos pendientes, vehículos libres, almacenes
 *              con stock y bloqueos vigentes), decide qué rutas despachar AHORA, con flota fija,
 *              despacho/retorno desde cualquiera de los 3 almacenes con stock, y soporte de
 *              entregas parciales. Grafo interno: nodo 0 = "inicio de ruta" (virtual), nodos 1..n
 *              = pedidos del tick.
 */
public final class AntColonySystemVRP implements IEstrategiaEnrutamiento {

    private final List<Pedido> pedidos;
    private final List<UnidadTransporte> vehiculosLibres;
    private final ContextoPlanificacion ctx;
    private final Random rnd;

    private final int numHormigas;
    private final int numIteraciones;
    private final double alfa, beta, rho, q0;

    private final int n;
    private final Map<Pedido, Integer> indice = new HashMap<>();
    private final double[][] feromona;
    private final double feromonaInicial;

    /** Variante con memoria entre ticks (null = sin memoria, comportamiento original). */
    private final MemoriaFeromonas memoria;
    /** Variante con búsqueda de vecindad variable sobre la mejor solución del tick. */
    private final boolean busquedaLocal;
    /** Variante experimental: prueba TODOS los tipos de vehículo libres, no solo desde el recomendado hacia arriba. */
    private final boolean tipoVehiculoAmpliado;

    public AntColonySystemVRP(List<Pedido> pedidosPendientes, List<UnidadTransporte> vehiculosLibres,
                               ContextoPlanificacion ctx, long semilla,
                               int numHormigas, int numIteraciones,
                               double alfa, double beta, double rho, double q0) {
        this(pedidosPendientes, vehiculosLibres, ctx, semilla, numHormigas, numIteraciones,
                alfa, beta, rho, q0, null, 0.0, false, false);
    }

    public AntColonySystemVRP(List<Pedido> pedidosPendientes, List<UnidadTransporte> vehiculosLibres,
                               ContextoPlanificacion ctx, long semilla,
                               int numHormigas, int numIteraciones,
                               double alfa, double beta, double rho, double q0,
                               MemoriaFeromonas memoria, double retencionMemoria, boolean busquedaLocal,
                               boolean tipoVehiculoAmpliado) {
        this.memoria = memoria;
        this.busquedaLocal = busquedaLocal;
        this.tipoVehiculoAmpliado = tipoVehiculoAmpliado;
        this.pedidos = pedidosPendientes;
        this.vehiculosLibres = vehiculosLibres;
        this.ctx = ctx;
        this.rnd = new Random(semilla);
        this.numHormigas = numHormigas;
        this.numIteraciones = numIteraciones;
        this.alfa = alfa;
        this.beta = beta;
        this.rho = rho;
        this.q0 = q0;

        this.n = pedidos.size();
        for (int i = 0; i < n; i++) indice.put(pedidos.get(i), i + 1); // 0 = inicio de ruta (virtual)

        double costoReferencia = Math.max(costoHeuristicaVecinoCercano(), 1e-6);
        this.feromonaInicial = 1.0 / (Math.max(n, 1) * costoReferencia);
        this.feromona = new double[n + 1][n + 1];
        for (double[] fila : feromona) java.util.Arrays.fill(fila, feromonaInicial);
        if (memoria != null) memoria.cargarEn(feromona, indice, feromonaInicial, retencionMemoria);
    }

    private double costoHeuristicaVecinoCercano() {
        if (pedidos.isEmpty()) return 1.0;
        List<Pedido> restantes = new ArrayList<>(pedidos);
        Punto actual = ctx.almacenes.get(0).ubicacion;
        double total = 0.0;
        while (!restantes.isEmpty()) {
            Pedido masCercano = restantes.get(0);
            int mejorDist = ctx.grafo.distanciaKm(actual, masCercano.ubicacion);
            for (Pedido p : restantes) {
                int d = ctx.grafo.distanciaKm(actual, p.ubicacion);
                if (d < mejorDist) { mejorDist = d; masCercano = p; }
            }
            total += mejorDist;
            actual = masCercano.ubicacion;
            restantes.remove(masCercano);
        }
        return total;
    }

    public Solucion resolverTick() {
        Solucion mejorGlobal = null;
        double costoMejorGlobal = Double.MAX_VALUE;

        for (int it = 0; it < numIteraciones; it++) {
            Solucion mejorIteracion = null;
            double costoMejorIteracion = Double.MAX_VALUE;

            for (int h = 0; h < numHormigas; h++) {
                Solucion sol = construirSolucion();
                double costo = sol.costoTotal();
                if (costo < costoMejorIteracion) {
                    costoMejorIteracion = costo;
                    mejorIteracion = sol;
                }
            }
            if (costoMejorIteracion < costoMejorGlobal) {
                costoMejorGlobal = costoMejorIteracion;
                mejorGlobal = mejorIteracion;
            }
            actualizarFeromonaGlobal(mejorGlobal, costoMejorGlobal);
        }
        if (memoria != null) {
            memoria.guardarDesde(feromona, pedidos, feromonaInicial, Math.pow(1 - rho, numIteraciones));
        }
        if (busquedaLocal && mejorGlobal != null) {
            MejoraLocalAcs.mejorar(mejorGlobal, vehiculosLibres, ctx);
        }
        return mejorGlobal != null ? mejorGlobal : new Solucion();
    }

    /** Construcción de una solución (varias rutas) por una hormiga, sin mutar el estado real del mundo. */
    private Solucion construirSolucion() {
        List<UnidadTransporte> vehiculosLocal = new ArrayList<>(vehiculosLibres);
        Map<Almacen.Id, Integer> stockLocal = new HashMap<>();
        for (Almacen a : ctx.almacenes) stockLocal.put(a.id, a.stockDisponible());
        Map<Pedido, Integer> restanteLocal = new HashMap<>();
        for (Pedido p : pedidos) restanteLocal.put(p, p.cantidadPendiente);

        Solucion solucion = new Solucion();

        while (true) {
            AperturaRuta apertura = buscarAperturaFactible(vehiculosLocal, stockLocal, restanteLocal);
            if (apertura == null) break;

            int stockDisponibleAlmacen = stockLocal.get(apertura.almacen.id);
            vehiculosLocal.remove(apertura.vehiculo);
            stockLocal.merge(apertura.almacen.id, -apertura.cantidadInicial, Integer::sum);

            Ruta ruta = construirRuta(apertura, restanteLocal, stockDisponibleAlmacen - apertura.cantidadInicial);
            stockLocal.merge(apertura.almacen.id, -(ruta.cargaTotal() - apertura.cantidadInicial), Integer::sum);
            solucion.rutas.add(ruta);
        }
        return solucion;
    }

    private record AperturaRuta(Pedido semilla, Almacen almacen, TipoVehiculo tipo, UnidadTransporte vehiculo, int cantidadInicial) {}

    private AperturaRuta buscarAperturaFactible(List<UnidadTransporte> vehiculosLocal, Map<Almacen.Id, Integer> stockLocal,
                                                 Map<Pedido, Integer> restanteLocal) {
        List<Pedido> ordenados = new ArrayList<>(pedidos);
        ordenados.sort((a, b) -> a.fechaLimite.compareTo(b.fechaLimite));

        for (Pedido semilla : ordenados) {
            if (restanteLocal.get(semilla) <= 0) continue;
            Almacen almacenAprox = almacenMasCercanoConStockLocal(semilla.ubicacion, stockLocal, 1);
            if (almacenAprox == null) continue;

            TipoVehiculo recomendado = AsignadorFlota.tipoRecomendado(semilla, almacenAprox.ubicacion, ctx);
            TipoVehiculo[] tipos = tipoVehiculoAmpliado ? ORDEN_COMPLETO_POR_COSTO : ordenPorCostoDesde(recomendado);
            for (TipoVehiculo tipo : tipos) {
                UnidadTransporte libre = vehiculosLocal.stream().filter(v -> v.tipo == tipo).findFirst().orElse(null);
                if (libre == null) continue;
                int cantidad = Math.min(restanteLocal.get(semilla), tipo.capacidad);
                Almacen almacen = almacenMasCercanoConStockLocal(semilla.ubicacion, stockLocal, cantidad);
                if (almacen == null) continue;
                RutaUtil.Evaluacion ev = RutaUtil.evaluar(List.of(new Entrega(semilla, cantidad)), tipo,
                        almacen.ubicacion, ctx.horaActual, ctx.grafo);
                if (ev.factible()) return new AperturaRuta(semilla, almacen, tipo, libre, cantidad);
            }
        }
        return null;
    }

    private static final TipoVehiculo[] ORDEN_COMPLETO_POR_COSTO =
            {TipoVehiculo.BICICLETA, TipoVehiculo.MOTO, TipoVehiculo.AUTO};

    private static TipoVehiculo[] ordenPorCostoDesde(TipoVehiculo recomendado) {
        int inicio = 0;
        for (int i = 0; i < ORDEN_COMPLETO_POR_COSTO.length; i++) if (ORDEN_COMPLETO_POR_COSTO[i] == recomendado) inicio = i;
        List<TipoVehiculo> resultado = new ArrayList<>();
        for (int i = inicio; i < ORDEN_COMPLETO_POR_COSTO.length; i++) resultado.add(ORDEN_COMPLETO_POR_COSTO[i]);
        return resultado.toArray(new TipoVehiculo[0]);
    }

    private Almacen almacenMasCercanoConStockLocal(Punto punto, Map<Almacen.Id, Integer> stockLocal, int cantidadNecesaria) {
        Almacen mejor = null;
        int mejorDistancia = Integer.MAX_VALUE;
        for (Almacen a : ctx.almacenes) {
            if (stockLocal.getOrDefault(a.id, 0) < cantidadNecesaria) continue;
            int d = ctx.grafo.distanciaKm(punto, a.ubicacion);
            if (d < mejorDistancia) { mejorDistancia = d; mejor = a; }
        }
        return mejor;
    }

    private Ruta construirRuta(AperturaRuta apertura, Map<Pedido, Integer> restanteLocal, int stockAlmacenRestante) {
        Ruta ruta = new Ruta(apertura.vehiculo, apertura.almacen, ctx.horaActual);
        List<Entrega> secuencia = new ArrayList<>();
        List<Pedido> visitados = new ArrayList<>();
        Punto puntoActual = apertura.almacen.ubicacion;
        int nodoActual = 0;
        int capacidadRestante = apertura.tipo.capacidad;

        while (capacidadRestante > 0 && stockAlmacenRestante > 0) {
            List<Pedido> candidatosPedido = new ArrayList<>();
            Map<Pedido, Integer> cantidadPorCandidato = new HashMap<>();
            for (Pedido p : pedidos) {
                if (restanteLocal.get(p) <= 0 || visitados.contains(p)) continue;
                int cantidad = Math.min(Math.min(restanteLocal.get(p), capacidadRestante), stockAlmacenRestante);
                if (cantidad <= 0) continue;
                List<Entrega> tentativa = new ArrayList<>(secuencia);
                tentativa.add(new Entrega(p, cantidad));
                if (RutaUtil.evaluar(tentativa, apertura.tipo, apertura.almacen.ubicacion, ctx.horaActual, ctx.grafo).factible()) {
                    candidatosPedido.add(p);
                    cantidadPorCandidato.put(p, cantidad);
                }
            }
            if (candidatosPedido.isEmpty()) break;

            Pedido siguiente = elegirSiguiente(nodoActual, puntoActual, candidatosPedido);
            int nodoSiguiente = indice.get(siguiente);
            actualizarFeromonaLocal(nodoActual, nodoSiguiente);

            int cantidad = cantidadPorCandidato.get(siguiente);
            secuencia.add(new Entrega(siguiente, cantidad));
            visitados.add(siguiente);
            restanteLocal.put(siguiente, restanteLocal.get(siguiente) - cantidad);
            capacidadRestante -= cantidad;
            stockAlmacenRestante -= cantidad;
            nodoActual = nodoSiguiente;
            puntoActual = siguiente.ubicacion;
        }

        if (secuencia.isEmpty()) {
            secuencia.add(new Entrega(apertura.semilla, apertura.cantidadInicial));
            restanteLocal.put(apertura.semilla, restanteLocal.get(apertura.semilla) - apertura.cantidadInicial);
        }

        RutaUtil.Evaluacion evFinal = RutaUtil.evaluar(secuencia, apertura.tipo, apertura.almacen.ubicacion, ctx.horaActual, ctx.grafo);
        ruta.secuencia.addAll(secuencia);
        ruta.distanciaIdaKm = evFinal.distanciaIdaKm();
        ruta.horaFinUltimaEntrega = evFinal.horaFinUltimaEntrega();

        Punto puntoFinal = secuencia.get(secuencia.size() - 1).pedido.ubicacion;
        Almacen retorno = ctx.almacenMasCercanoConStock(puntoFinal, 1);
        if (retorno == null) retorno = ctx.almacenes.stream().filter(a -> a.id == Almacen.Id.CENTRAL).findFirst().orElseThrow();
        ruta.almacenRetorno = retorno;
        ruta.distanciaRetornoKm = RutaUtil.distanciaRetornoKm(puntoFinal, retorno, ctx.grafo);
        double horasRetorno = ruta.distanciaRetornoKm / apertura.tipo.velocidadKmH;
        ruta.horaLlegadaRetorno = ruta.horaFinUltimaEntrega.plusSeconds(Math.round(horasRetorno * 3600));
        return ruta;
    }

    private Pedido elegirSiguiente(int nodoActual, Punto puntoActual, List<Pedido> factibles) {
        if (factibles.size() == 1) return factibles.get(0);

        double[] pesos = new double[factibles.size()];
        double sumaPesos = 0.0;
        for (int k = 0; k < factibles.size(); k++) {
            Pedido p = factibles.get(k);
            int j = indice.get(p);
            double heuristica = heuristica(puntoActual, p);
            pesos[k] = Math.pow(feromona[nodoActual][j], alfa) * Math.pow(heuristica, beta);
            sumaPesos += pesos[k];
        }

        double q = rnd.nextDouble();
        if (q <= q0) {
            int mejor = 0;
            for (int k = 1; k < pesos.length; k++) if (pesos[k] > pesos[mejor]) mejor = k;
            return factibles.get(mejor);
        }
        double umbral = rnd.nextDouble() * sumaPesos;
        double acumulado = 0.0;
        for (int k = 0; k < pesos.length; k++) {
            acumulado += pesos[k];
            if (acumulado >= umbral) return factibles.get(k);
        }
        return factibles.get(factibles.size() - 1);
    }

    private double heuristica(Punto actual, Pedido p) {
        double distancia = Math.max(ctx.grafo.distanciaKm(actual, p.ubicacion), 0.5);
        double minutosRestantes = Math.max(Duration.between(ctx.horaActual, p.fechaLimite).toMinutes(), 1);
        double urgencia = 60.0 / minutosRestantes;
        return urgencia / distancia;
    }

    private void actualizarFeromonaLocal(int i, int j) {
        feromona[i][j] = (1 - rho) * feromona[i][j] + rho * feromonaInicial;
        feromona[j][i] = feromona[i][j];
    }

    private void actualizarFeromonaGlobal(Solucion mejorGlobal, double costoMejorGlobal) {
        for (double[] fila : feromona) {
            for (int j = 0; j < fila.length; j++) fila[j] *= (1 - rho);
        }
        if (mejorGlobal == null) return;
        double aporte = rho * (1.0 / Math.max(costoMejorGlobal, 1e-6));
        for (Ruta ruta : mejorGlobal.rutas) {
            int nodoActual = 0;
            for (Entrega e : ruta.secuencia) {
                int nodoSiguiente = indice.get(e.pedido);
                feromona[nodoActual][nodoSiguiente] += aporte;
                feromona[nodoSiguiente][nodoActual] += aporte;
                nodoActual = nodoSiguiente;
            }
        }
    }

    @Override
    public List<Ruta> generarRutas(List<Pedido> pedidos, List<UnidadTransporte> flota) {
        AntColonySystemVRP instancia = new AntColonySystemVRP(pedidos, flota, ctx, 1L,
                numHormigas, numIteraciones, alfa, beta, rho, q0, null, 0.0, false, false);
        return instancia.resolverTick().rutas;
    }

    @Override
    public List<Ruta> replanificar(Ruta rutaAfectada, Incidencia incidencia) {
        return generarRutas(rutaAfectada.getPedidosAsignados(), List.of(rutaAfectada.vehiculo));
    }
}