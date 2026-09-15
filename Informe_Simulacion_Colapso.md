# Actualización: simulación del escenario de colapso con datos reales

Este documento actualiza el pseudocódigo y las estructuras de ACS y GRASP-VNS para que
reflejen los cambios de alcance más recientes, y agrega el motor de simulación (`Simulador`)
que los invoca en cada paso de tiempo hasta detectar el colapso logístico.

## 1. Qué cambió respecto a la versión anterior

- **Flota fija**: ya no se asume disponibilidad ilimitada. Hay exactamente 10 autos (TA01-TA10),
  15 motos (TM01-TM15) y 12 bicicletas (TB01-TB12), cada una con su posición y disponibilidad real.
- **Tres almacenes**: central (27,14, capacidad infinita) y dos intermedios, Nor-Oeste (12,38) y
  Este (57,27), cada uno con 1,000 unidades y recarga instantánea a medianoche. Cualquier ruta
  puede despachar y retornar desde cualquiera de los tres, siempre que tengan stock.
- **Bloqueos de calles**: la distancia entre dos nodos ya no es la Manhattan directa; se calcula
  con un BFS sobre la cuadrícula (`GrafoVial`) excluyendo los tramos bloqueados vigentes en el
  instante de la consulta.
- **Mantenimiento preventivo**: cada vehículo tiene ventanas de fecha en las que no está disponible
  para programación (según su archivo `mant.preventivo.aa.m1-m2.txt`).
- **Entregas parciales**: un mismo pedido puede repartirse en varias `Entrega` (posiblemente en
  vehículos y momentos distintos), cada una con su propia hora de acondicionamiento de 1h.
- **Sin averías**: no se simulan fallas de unidades en camino (quedan fuera de esta corrida).
- **Escenario de colapso**: el simulador avanza el reloj en pasos fijos y se detiene apenas un
  pedido pendiente supera su plazo (`fechaLimite`) sin haber sido completado.

Ambos algoritmos dejan de resolver "todo el histórico de una sola vez" y pasan a resolver un
**tick de planificación**: dado el estado del mundo en un instante (pedidos pendientes, vehículos
libres, stock de los almacenes, bloqueos vigentes), deciden qué rutas despachar ahora mismo. El
simulador los vuelve a invocar en cada tick, con el estado ya actualizado.

## 2. Estructuras de datos nuevas o modificadas

| Estructura | Campos | Descripción |
|---|---|---|
| `Almacen` | id (CENTRAL/NOROESTE/ESTE), ubicacion, capacidadMaxima, stock | Central con capacidad infinita; intermedios con 1,000 y recarga diaria a medianoche. |
| `Vehiculo` | id (TTNN), tipo, posicionActual, disponibleDesde, estado | Unidad concreta de la flota fija (LIBRE / EN_RUTA / MANTENIMIENTO). |
| `Pedido` (actualizado) | ..., momentoLlegada (fecha y hora real), fechaLimite, cantidadPendiente | `cantidadPendiente` baja a medida que se le asignan `Entrega`s; puede llegar a 0 en varios pasos. |
| `Entrega` | pedido, cantidad | Una parada: entrega total o parcial de un pedido. Siempre consume 1h de acondicionamiento. |
| `Bloqueo` | inicio, fin, poligonal (lista de nodos) | Un tramo de calle bloqueado durante una ventana de tiempo (archivo `bloqueo.aamm.txt`). |
| `Mantenimiento` | fecha, vehiculoId, inicio, fin | Ventana de indisponibilidad de un vehículo (archivo `mant.preventivo.aa.m1-m2.txt`); duración según tipo (bici 8h, moto 24h, auto 48h). |
| `GrafoVial` | — | BFS sobre la cuadrícula 70x50 que da la distancia más corta entre dos nodos evitando los tramos bloqueados vigentes. |
| `ContextoPlanificacion` | almacenes, grafo, horaActual | El "estado del mundo" que se le pasa a los algoritmos en cada tick. |
| `Ruta` (actualizado) | vehiculo, almacenDespacho, secuencia: List\<Entrega\>, almacenRetorno | Ahora referencia un vehículo y un almacén concretos, no solo un tipo abstracto. |

## 3. Regla de asignación de flota (actualizada)

Antes se elegía siempre el tipo más barato por km que alcanzara la velocidad requerida. Con
pedidos más grandes que la capacidad de una bicicleta o una moto, eso llevaba a fragmentar en
demasiadas entregas parciales innecesarias. Ahora se compara el **costo total estimado**,
considerando cuántos viajes de ida y vuelta harían falta con cada tipo:

```
FUNCION tipoRecomendado(pedido, origen, contexto)
  distancia <- grafo.distancia(origen, pedido.ubicacion)          // BFS, respeta bloqueos vigentes
  horasRestantes <- (pedido.fechaLimite - contexto.horaActual) en horas
  horasViajeDisponibles <- max(horasRestantes - 1, 0.1)           // resta 1h de acondicionamiento
  velocidadRequerida <- distancia / horasViajeDisponibles

  mejor <- nulo ; mejorCosto <- +infinito
  PARA tipo EN [BICICLETA, MOTO, AUTO]
    SI tipo.velocidad < velocidadRequerida ENTONCES continuar
    viajesNecesarios <- techo(pedido.cantidadPendiente / tipo.capacidad)
    costoEstimado <- viajesNecesarios * 2 * distancia * tipo.costoPorKm
    SI costoEstimado < mejorCosto ENTONCES mejor <- tipo ; mejorCosto <- costoEstimado
  DEVOLVER mejor (o AUTO si ninguno alcanza la velocidad requerida)
```

## 4. Pseudocódigo actualizado — Ant Colony System (por tick)

```
ALGORITMO AntColonySystemVRP_Tick(pendientes, vehiculosLibres, contexto, m, I, α, β, ρ, q0)

  // 1. Inicializacion (igual que antes, pero sobre los pendientes de ESTE tick)
  n <- |pendientes|
  τ0 <- 1 / (n * costo(vecino_mas_cercano(pendientes)))
  τ[i][j] <- τ0 para todo i,j en {0..n}      // 0 = "inicio de una ruta nueva" (nodo virtual)
  mejorGlobal <- nulo ; costoMejorGlobal <- +infinito

  PARA it <- 1 HASTA I HACER
    PARA cada hormiga h <- 1 HASTA m HACER
      // Copias LOCALES: no se toca el estado real hasta aplicar la mejor solucion del tick
      vehiculosLocal <- copia(vehiculosLibres)
      stockLocal <- stock actual de cada almacen (copia)
      restanteLocal <- cantidadPendiente actual de cada pedido (copia)
      solucion.rutas <- {}

      MIENTRAS existan vehiculos libres y pedidos con restante > 0 HACER
        // Elegir el pedido mas urgente que SI se pueda atender con algun vehiculo libre ahora
        (semilla, almacen, tipo, vehiculo) <- primera combinacion factible, recorriendo
             pendientes ordenados por fechaLimite ascendente, probando en cada uno:
               almacen <- almacen mas cercano con stock >= 1
               tipo <- tipoRecomendado(semilla, almacen, contexto)
               vehiculo <- vehiculo libre de ese tipo (o del siguiente mas caro si no hay)
        SI no se encontro ninguna combinacion factible ENTONCES romper (fin de esta hormiga)

        retirar vehiculo de vehiculosLocal ; descontar stock del almacen elegido
        ruta <- Ruta(vehiculo, almacen)
        nodoActual <- 0 ; puntoActual <- almacen.ubicacion ; capacidadRestante <- tipo.capacidad

        MIENTRAS capacidadRestante > 0 HACER
          factibles <- { p : restanteLocal[p] > 0, p no visitado en esta ruta,
                          agregar Entrega(p, min(restanteLocal[p], capacidadRestante)) sigue
                          siendo factible en plazo con GrafoVial y la hora real de la ruta }
          SI factibles = vacio ENTONCES romper

          // Regla de transicion pseudoaleatoria proporcional (igual que antes)
          q <- aleatorio(0,1)
          siguiente <- (q<=q0) ? argmax(τ^α · η^β) : sorteo_ponderado(τ^α · η^β)
          τ[nodoActual][siguiente] <- (1-ρ)*τ[nodoActual][siguiente] + ρ*τ0      // actualizacion local

          cantidad <- min(restanteLocal[siguiente], capacidadRestante)
          ruta.secuencia <- ruta.secuencia + Entrega(siguiente, cantidad)
          restanteLocal[siguiente] -= cantidad ; capacidadRestante -= cantidad
          nodoActual <- indice(siguiente) ; puntoActual <- siguiente.ubicacion
        FIN MIENTRAS

        cerrar ruta: almacenRetorno <- almacen mas cercano con stock a puntoActual
        solucion.rutas <- solucion.rutas + ruta
      FIN MIENTRAS

      SI costo(solucion) < costoMejorIteracion ENTONCES mejorIteracion <- solucion
    FIN PARA (hormigas)

    SI costoMejorIteracion < costoMejorGlobal ENTONCES mejorGlobal <- mejorIteracion
    // 4. Actualizacion global elitista (igual que antes)
    τ[i][j] <- (1-ρ)*τ[i][j] para todo i,j
    PARA cada arco (i,j) en mejorGlobal: τ[i][j] += ρ * (1/costoMejorGlobal)
  FIN PARA (iteraciones)

  DEVOLVER mejorGlobal   // el simulador recien aqui aplica la solucion al estado real
```

Diferencias clave respecto a la versión anterior: (a) cada ruta nueva elige también el **almacén**
de despacho, no solo el tipo de vehículo; (b) el vehículo asignado es una **instancia concreta**
de la flota fija, que se retira del pool de libres para el resto del tick; (c) cada parada puede
ser una **entrega parcial**, dejando el resto del pedido pendiente para un tick futuro; (d) la
factibilidad en el tiempo se evalúa con `GrafoVial` (distancia real considerando bloqueos
vigentes) y con la hora real de inicio de la ruta, no con un presupuesto de horas relativo a `t=0`.

## 5. Pseudocódigo actualizado — GRASP-VNS (por tick)

```
ALGORITMO GraspVnsVRP_Tick(pendientes, vehiculosLibres, contexto, α_GRASP, maxIter)

  mejorGlobal <- nulo ; costoMejorGlobal <- +infinito

  PARA it <- 1 HASTA maxIter HACER
    // Copias locales (igual razon que en ACS: no tocar el estado real hasta el final del tick)
    vehiculosLocal <- copia(vehiculosLibres) ; stockLocal <- copia(stock) ; restanteLocal <- copia(pendientes)
    solucion.rutas <- {}

    // ---- FASE 1: Construccion golosa aleatorizada ----
    PARA cada pedido p, en orden de fechaLimite ascendente HACER
      MIENTRAS restanteLocal[p] > 0 HACER
        candidatas <- {}
        PARA cada ruta abierta r en solucion.rutas HACER
          espacio <- r.vehiculo.capacidad - r.cargaActual
          SI espacio > 0 Y agregar Entrega(p, min(restante,espacio)) es factible ENTONCES
             candidatas <- candidatas + (insertar en r, costoMarginal)
        PARA cada vehiculo libre v en vehiculosLocal HACER
          almacen <- almacen mas cercano con stock a p.ubicacion
          SI abrir ruta nueva con v desde almacen es factible ENTONCES
             candidatas <- candidatas + (nueva ruta con v, costoMarginal)

        SI candidatas = vacio ENTONCES romper (el resto de este pedido queda pendiente)
        ordenar candidatas por costoMarginal ascendente
        RCL <- primeras round(α_GRASP * |candidatas|) candidatas (minimo 1)
        aplicar una candidata elegida al azar de la RCL
        // si abrio ruta nueva: retirar v de vehiculosLocal, descontar stock del almacen
      FIN MIENTRAS
    FIN PARA

    // ---- FASE 2: Busqueda por Vecindad Variable (igual estructura que antes) ----
    k <- 1
    MIENTRAS k <= 4 HACER
      mejora <- explorar_vecindad(k, solucion)   // N1 relocate, N2 swap, N3 2-opt, N4 cambio de vehiculo
      SI mejora ENTONCES k <- 1 SINO k <- k+1
    FIN MIENTRAS

    SI costo(solucion) < costoMejorGlobal ENTONCES mejorGlobal <- solucion
  FIN PARA (iteraciones GRASP)

  DEVOLVER mejorGlobal
```

La vecindad **N4 (cambio de tipo de vehículo)** ahora, además de comprobar que la ruta siga siendo
factible con un tipo más barato, verifica que **exista realmente un vehículo libre de ese tipo**
en este tick (con flota fija ya no se puede "invocar" un vehículo más barato si no hay uno
disponible) -- y hace el intercambio real: el vehículo viejo vuelve al pool de libres de este tick.

## 6. Pseudocódigo nuevo — Simulador (motor del escenario de colapso)

```
ALGORITMO Simulador(pedidosOrdenados, bloqueos, mantenimientos, tick, algoritmo)

  almacenes <- {CENTRAL(27,14,inf), NOROESTE(12,38,1000), ESTE(57,27,1000)}
  flota <- {10 autos TA01..TA10, 15 motos TM01..TM15, 12 bicicletas TB01..TB12}, todos
           LIBRES en CENTRAL desde el inicio

  horaActual <- momento de llegada del primer pedido
  pendientes <- {} ; indice <- 0

  REPETIR
    SI cambio de dia (medianoche) ENTONCES recargar almacenes intermedios a su capacidad maxima

    MIENTRAS pedidosOrdenados[indice].momentoLlegada <= horaActual HACER
      pendientes <- pendientes + pedidosOrdenados[indice] ; indice <- indice + 1
    quitar de pendientes los pedidos ya completos (cantidadPendiente = 0)

    vehiculosLibres <- { v en flota : v no esta en ventana de mantenimiento en horaActual
                          Y (v.estado = LIBRE, o v.estado = EN_RUTA con disponibleDesde <= horaActual) }

    SI pendientes != vacio Y vehiculosLibres != vacio ENTONCES
      bloqueosVigentes <- aristas bloqueadas cuyo periodo cubre horaActual
      grafo.actualizar(bloqueosVigentes)
      contexto <- (almacenes, grafo, horaActual)
      solucion <- algoritmo.resolverTick(pendientes, vehiculosLibres, contexto)

      PARA cada ruta en solucion HACER
        almacenDespacho.retirar(cargaTotal(ruta))
        PARA cada entrega en ruta HACER entrega.pedido.cantidadPendiente -= entrega.cantidad
        vehiculo.estado <- EN_RUTA ; vehiculo.disponibleDesde <- horaLlegadaRetorno
        vehiculo.posicionActual <- almacenRetorno.ubicacion
      FIN PARA

    // Criterio de colapso: algun pedido pendiente ya supero su plazo sin completarse
    PARA cada pedido p en pendientes HACER
      SI p.cantidadPendiente > 0 Y horaActual > p.fechaLimite ENTONCES
        DEVOLVER "COLAPSO en" horaActual, "cliente" p.idCliente

    horaActual <- horaActual + tick
  HASTA que no queden mas pedidos por ingresar y todos los pendientes esten completos

  DEVOLVER "Sin colapso: se agoto el historico disponible"
```

Nota de diseño: el simulador avanza en **pasos fijos** (`tick`, 30 minutos en la corrida de
prueba) en vez de un reloj de eventos continuo. Es una simplificación deliberada: con pedidos
llegando cada pocos minutos y plazos de hasta 4 horas, un paso de 30 minutos da tiempo de
reaccion suficiente sin tener que modelar un calendario de eventos completo. El algoritmo (ACS o
GRASP-VNS) se reconstruye desde cero en cada tick, con pocas hormigas/iteraciones (por defecto,
5), ya que se invoca miles de veces a lo largo de la simulacion en vez de una sola vez sobre todo
el historico.

## 7. Resultado de la corrida con los datos reales

Corrida sobre los 36 meses de `ventas.aaaamm.txt` (2026-01 a 2028-12, 160,010 pedidos en total),
36 meses de `bloqueo.aamm.txt` (21,725 bloqueos) y 18 archivos bimensuales de mantenimiento (666
registros), con tick de 30 minutos:

| Métrica | ACS | GRASP-VNS |
|---|---|---|
| Momento del colapso | 2027-04-03 10:30 | 2027-02-07 18:30 |
| Pedido que colapsó | c6612 (0,1): 0/6 unidades entregadas, límite 10:27 (incumplido por 3 min) | c3714 (26,18): 4/5 unidades entregadas, límite 18:25 (incumplido por 5 min) |
| Pedidos ingresados hasta el colapso | 55,859 | 46,897 |
| Completados a tiempo | 55,776 (99.85%) | 46,657 (99.49%) |
| Entregas totales (parciales) | 77,463 (40,959 = 52.9%) | 63,376 (31,560 = 49.8%) |
| Costo acumulado | S/ 13,073,312 | S/ 10,005,723 |
| Km recorridos | 2,380,368 | 1,966,744 |
| Costo por pedido completado | S/ 234.4 | S/ 214.5 |
| Costo por km | S/ 5.49 | S/ 5.09 |
| Invocaciones al algoritmo (Sc=30 min c/u) | 15,933 de 21,954 ticks | 14,481 de 19,330 ticks |
| Ta promedio / Ta máximo | 3.1 ms / 72 ms | 4.7 ms / 116 ms |
| Tiempo de cómputo de la corrida completa | 64.5 s | 82.8 s |

**Lectura de los resultados:** con la misma flota fija (10 autos, 15 motos, 12 bicicletas), ACS
sostuvo la operación casi 2 meses más que GRASP-VNS antes de colapsar (abril 2027 vs. febrero
2027) y alcanzó a atender ~19% más pedidos en total. Sin embargo, GRASP-VNS operó de forma más
barata durante el tiempo que estuvo activo: costó S/ 20 menos por pedido completado y S/ 0.40
menos por kilómetro recorrido, gracias a que su vecindad N4 reasigna agresivamente rutas a
vehículos más baratos cuando la capacidad y el plazo lo permiten. Es un trade-off razonable de
reportar: **ACS prioriza la resiliencia/cobertura en el tiempo, GRASP-VNS prioriza el costo
operativo**, dentro de la misma flota y las mismas reglas de negocio.

**Advertencia sobre el punto exacto de colapso:** en ambos casos, el pedido que dispara el
colapso lo incumple por muy pocos minutos (3 y 5 minutos respectivamente) respecto de su plazo.
Esto sugiere que el momento exacto del colapso es sensible al tamaño del tick de planificación
(30 minutos): un pedido con muy poca holgura puede quedar "atrapado" entre dos ticks aunque en
teoría hubiera flota disponible para atenderlo con un paso de simulación más fino. El día/mes
aproximado del colapso (~13-15 meses de operación) es un resultado robusto; el minuto exacto no
lo es. Si se quiere precisión al minuto, se puede volver a correr con un tick más chico (p.ej. 10
o 5 minutos) a costa de un tiempo de cómputo proporcionalmente mayor.

**Sa, Ta y Sc de esta corrida:** con `Sc = 30 minutos` (cuánto tiempo de pedidos se avanza por
invocación), el algoritmo tardó en promedio `Ta ≈ 3-5 ms` por invocación (máximo 72-116 ms en el
peor caso), y como no hay espera artificial entre ticks, `Sa = Ta`. Esto deja un margen enorme
frente al requisito de la simulación 5D (debe correr en 30-60 minutos reales): procesar 30 minutos
de pedidos en 3-5 ms reales implica que los 3 años completos (2026-2028) solo necesitaron ~50-80
segundos de cómputo efectivo. Si se quisiera *forzar* que la simulación tome exactamente ese rango
de 30-60 minutos reales (por ejemplo, para que sea visualmente seguible en el componente
visualizador), habría que introducir una espera artificial entre ticks para que `Sa` deje de
coincidir con `Ta` y en cambio iguale al `Sc` real transcurrido (es decir, correr la simulación al
mismo ritmo que el reloj, o a un múltiplo de él) -- algo que este simulador no hace porque el
escenario de colapso busca terminar cuanto antes.
