package paqroute;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Ant Colony System (ACS) aplicado a un TICK de planificacion del simulador de PaqRoute: dado el
 * estado actual (pedidos pendientes, vehiculos libres, almacenes con su stock y bloqueos vigentes),
 * decide que rutas despachar AHORA, con flota fija (cantidad limitada de vehiculos por tipo),
 * despacho/retorno desde cualquiera de los 3 almacenes con stock, y soporte de entregas parciales
 * (si un pedido no cabe entero en el vehiculo, se entrega solo lo que quepa y el resto queda
 * pendiente para un tick posterior).
 *
 * Al ser invocado en cada tick del simulador (en vez de una sola vez sobre todo el historico), el
 * algoritmo corre con pocas hormigas/iteraciones y arranca con feromona nueva cada vez -- es la
 * adaptacion "en linea" del ACS al ruteo dinamico (ver DVRPTW, Necula et al. 2017, citado en el
 * informe), donde el algoritmo reconstruye la solucion de forma continua a medida que llegan pedidos.
 *
 * Grafo de trabajo interno: nodo 0 = "inicio de una ruta nueva" (nodo virtual, ya que ahora hay 3
 * almacenes posibles en vez de un unico deposito), nodos 1..n = pedidos pendientes de este tick.
 */
public final class AntColonySystemVRP {

    private final List<Pedido> pedidos;
    private final List<Vehiculo> vehiculosLibres;
    private final ContextoPlanificacion ctx;
    private final Random rnd;

    private final int numHormigas;
    private final int numIteraciones;
    private final double alfa, beta, rho, q0;

    private final int n;
    private final Map<Pedido, Integer> indice = new HashMap<>();
    private final double[][] feromona;
    private final double feromonaInicial;

    public AntColonySystemVRP(List<Pedido> pedidosPendientes, List<Vehiculo> vehiculosLibres,
                               ContextoPlanificacion ctx, long semilla,
                               int numHormigas, int numIteraciones,
                               double alfa, double beta, double rho, double q0) {
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
        return mejorGlobal != null ? mejorGlobal : new Solucion();
    }

    /** Construccion de una solucion (varias rutas) por una hormiga, sin mutar el estado real del mundo. */
    private Solucion construirSolucion() {
        List<Vehiculo> vehiculosLocal = new ArrayList<>(vehiculosLibres);
        Map<Almacen.Id, Integer> stockLocal = new HashMap<>();
        for (Almacen a : ctx.almacenes) stockLocal.put(a.id, a.stockDisponible());
        Map<Pedido, Integer> restanteLocal = new HashMap<>();
        for (Pedido p : pedidos) restanteLocal.put(p, p.cantidadPendiente);

        Solucion solucion = new Solucion();

        while (true) {
            AperturaRuta apertura = buscarAperturaFactible(vehiculosLocal, stockLocal, restanteLocal);
            if (apertura == null) break;

            // Todo lo que la ruta cargue (parada inicial + paradas adicionales) sale del mismo almacen;
            // se limita a lo que ese almacen realmente tiene disponible en este momento.
            int stockDisponibleAlmacen = stockLocal.get(apertura.almacen.id);
            vehiculosLocal.remove(apertura.vehiculo);
            stockLocal.merge(apertura.almacen.id, -apertura.cantidadInicial, Integer::sum);

            Ruta ruta = construirRuta(apertura, restanteLocal, stockDisponibleAlmacen - apertura.cantidadInicial);
            stockLocal.merge(apertura.almacen.id, -(ruta.cargaTotal() - apertura.cantidadInicial), Integer::sum);
            solucion.rutas.add(ruta);
        }
        return solucion;
    }

    private record AperturaRuta(Pedido semilla, Almacen almacen, TipoVehiculo tipo, Vehiculo vehiculo, int cantidadInicial) {}

    private AperturaRuta buscarAperturaFactible(List<Vehiculo> vehiculosLocal, Map<Almacen.Id, Integer> stockLocal,
                                                 Map<Pedido, Integer> restanteLocal) {
        List<Pedido> ordenados = new ArrayList<>(pedidos);
        ordenados.sort((a, b) -> a.fechaLimite.compareTo(b.fechaLimite));

        for (Pedido semilla : ordenados) {
            if (restanteLocal.get(semilla) <= 0) continue;
            Almacen almacenAprox = almacenMasCercanoConStockLocal(semilla.ubicacion, stockLocal, 1);
            if (almacenAprox == null) continue;

            TipoVehiculo recomendado = AsignadorFlota.tipoRecomendado(semilla, almacenAprox.ubicacion, ctx);
            for (TipoVehiculo tipo : ordenPorCostoDesde(recomendado)) {
                Vehiculo libre = vehiculosLocal.stream().filter(v -> v.tipo == tipo).findFirst().orElse(null);
                if (libre == null) continue;
                int cantidad = Math.min(restanteLocal.get(semilla), tipo.capacidad);
                // El almacen debe tener stock suficiente para ESTA cantidad, no solo stock > 0
                // (con lambda escalado, la carga puede superar lo que queda en el almacen mas cercano).
                Almacen almacen = almacenMasCercanoConStockLocal(semilla.ubicacion, stockLocal, cantidad);
                if (almacen == null) continue;
                RutaUtil.Evaluacion ev = RutaUtil.evaluar(List.of(new Entrega(semilla, cantidad)), tipo,
                        almacen.ubicacion, ctx.horaActual, ctx.grafo);
                if (ev.factible()) return new AperturaRuta(semilla, almacen, tipo, libre, cantidad);
            }
        }
        return null;
    }

    private static TipoVehiculo[] ordenPorCostoDesde(TipoVehiculo recomendado) {
        TipoVehiculo[] orden = {TipoVehiculo.BICICLETA, TipoVehiculo.MOTO, TipoVehiculo.AUTO};
        int inicio = 0;
        for (int i = 0; i < orden.length; i++) if (orden[i] == recomendado) inicio = i;
        List<TipoVehiculo> resultado = new ArrayList<>();
        for (int i = inicio; i < orden.length; i++) resultado.add(orden[i]);
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

    /** {@code stockAlmacenRestante}: cuanto mas puede cargar esta ruta del mismo almacen ademas de la parada inicial. */
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
        double minutosRestantes = Math.max(java.time.Duration.between(ctx.horaActual, p.fechaLimite).toMinutes(), 1);
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
}
