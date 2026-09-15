# Pseudocódigo y estructuras requeridas — Componente Planificador PaqRoute

Alcance cubierto en este entregable (según lo solicitado): **rutas de reparto**, **gestión de
niveles de servicio priorizados** y **asignación de capacidades y flota heterogénea**. Quedan
fuera de este entregable: gestión de almacenes intermedios, restricciones operativas de turno,
bloqueos de calles, replanificación ante incidencias y multiescenario.

**Supuestos de simplificación de esta fase** (documentados también en el código, clase `RutaUtil`):

- Origen único de despacho: el **almacén central** (25,15). La decisión de despachar desde un
  almacén intermedio pertenece a la variable "gestión de almacenes intermedios", fuera de alcance.
- Todos los pedidos de una corrida de planificación se consideran liberados en el mismo instante
  de despacho (t = 0); su holgura disponible es directamente `horasLimite (hl)`. El día/hora/minuto
  de llegada del pedido se conserva en el objeto `Pedido` para las fases del curso que sí lo requieren
  (tiempo real, multiescenario).
- Tiempo de acondicionamiento/entrega del producto P: 1 hora por parada (dato de la situación auténtica).
- Distancia entre dos nodos = distancia Manhattan (cuadrícula sin diagonales ni curvas).
- No se fija un tamaño de flota: se asume disponibilidad suficiente de autos, motos y bicicletas;
  el algoritmo decide cuántas unidades de cada tipo usar y cómo repartir la carga entre ellas.

---

## 1. Ant Colony System (ACS)

### 1.1 Estructuras de datos requeridas

| Estructura | Campos | Descripción |
|---|---|---|
| `Punto` | `x, y` (enteros, km) | Nodo/esquina de la cuadrícula vial. |
| `Pedido` | `idCliente, ubicacion, cantidad(qq), dia, hora, minuto, horasLimite(hl)` | Registro leído del archivo mensual de envíos. |
| `TipoVehiculo` (enum) | `capacidad, velocidadKmH, costoPorKm` | AUTO(24, 40, S/8), MOTO(8, 25, S/6), BICICLETA(4, 12, S/3). |
| `Ruta` | `tipo, secuencia: List<Pedido>` | Secuencia ordenada de paradas asignada a una unidad. |
| `Solucion` | `rutas: List<Ruta>` | Conjunto de rutas que cubre los pedidos de la corrida. |
| `τ` (feromona) | matriz `double[n+1][n+1]` | Nodo 0 = depósito, nodos 1..n = pedidos. `τ[i][j]` = deseabilidad aprendida del arco i→j. |
| `η` (heurística) | función `η(i,j)` | `(1 / horasLimite_j) / distancia(i,j)`: favorece nodos cercanos **y** urgentes. |

### 1.2 Variables del algoritmo

| Variable | Símbolo | Descripción |
|---|---|---|
| Número de hormigas | `m` | Cantidad de soluciones completas construidas por iteración. |
| Número de iteraciones | `I` | Criterio de parada (o tiempo límite de cómputo). |
| Peso de la feromona | `α` | Exponente que pondera `τ[i][j]` en la regla de transición. |
| Peso heurístico | `β` | Exponente que pondera `η(i,j)` (cercanía + urgencia). |
| Tasa de evaporación/refuerzo | `ρ ∈ (0,1]` | Fracción de feromona renovada en cada actualización local y global. |
| Probabilidad de explotación | `q0 ∈ [0,1]` | Probabilidad de tomar el mejor arco conocido en vez de explorar (regla pseudoaleatoria proporcional). |
| Feromona inicial | `τ0` | `1 / (n · C_nn)`, con `C_nn` = costo de una solución de vecino más cercano. |
| `n` | — | Cantidad de pedidos pendientes en la corrida. |

### 1.3 Pseudocódigo

```
ALGORITMO AntColonySystemVRP(pedidos, deposito, m, I, α, β, ρ, q0)

  // 1. Inicialización
  n <- |pedidos|
  C_nn <- costo(vecino_mas_cercano(pedidos, deposito))
  τ0 <- 1 / (n * C_nn)
  τ[i][j] <- τ0   para todo i,j en {0..n}     // 0 = deposito
  mejorGlobal <- nulo ; costoMejorGlobal <- +infinito

  PARA it <- 1 HASTA I HACER
    mejorIteracion <- nulo ; costoMejorIteracion <- +infinito

    PARA cada hormiga h <- 1 HASTA m HACER
      pendientes <- copia(pedidos) ordenados por horasLimite ascendente   // más urgente primero
      solucion.rutas <- {}

      MIENTRAS pendientes != vacio HACER
        semilla <- pendientes[0]                         // pedido mas urgente restante
        tipo <- AsignadorFlota.tipoRecomendado(semilla, deposito)   // ver 1.4
        ruta <- []; nodoActual <- 0 (deposito); carga <- 0

        REPETIR
          factibles <- { p en pendientes : carga+p.qq <= tipo.capacidad
                                            Y ruta+[p] cumple horasLimite de todos sus pedidos }
          SI factibles = vacio ENTONCES romper

          // Regla de transición pseudoaleatoria proporcional (ACS)
          q <- aleatorio(0,1)
          SI q <= q0 ENTONCES
             siguiente <- argmax_{j en factibles} ( τ[nodoActual][j]^α * η(nodoActual,j)^β )
          SINO
             siguiente <- seleccion_por_ruleta(factibles, pesos = τ^α * η^β)

          // 3. Actualización local de feromona
          τ[nodoActual][siguiente] <- (1-ρ)*τ[nodoActual][siguiente] + ρ*τ0

          ruta <- ruta + [siguiente]; pendientes <- pendientes - {siguiente}
          carga <- carga + siguiente.qq ; nodoActual <- indice(siguiente)
        HASTA factibles = vacio

        SI ruta = vacio ENTONCES  // ningun tipo cubre el plazo de "semilla": forzar AUTO
           ruta <- [semilla]; tipo <- AUTO; pendientes <- pendientes - {semilla}

        solucion.rutas <- solucion.rutas + Ruta(tipo, ruta)
      FIN MIENTRAS

      SI costo(solucion) < costoMejorIteracion ENTONCES
         costoMejorIteracion <- costo(solucion); mejorIteracion <- solucion
    FIN PARA (hormigas)

    SI costoMejorIteracion < costoMejorGlobal ENTONCES
       costoMejorGlobal <- costoMejorIteracion; mejorGlobal <- mejorIteracion

    // 4. Actualización global de feromona (elitista: solo el mejor global)
    τ[i][j] <- (1-ρ) * τ[i][j]   para todo i,j
    PARA cada arco (i,j) usado en mejorGlobal HACER
       τ[i][j] <- τ[i][j] + ρ * (1 / costoMejorGlobal)

  FIN PARA (iteraciones)                         // 5. Criterio de parada

  DEVOLVER mejorGlobal
```

### 1.4 Regla de asignación de flota (compartida con GRASP-VNS)

```
FUNCION tipoRecomendado(pedido, deposito)
  distancia <- manhattan(deposito, pedido.ubicacion)
  horasViajeDisponibles <- max(pedido.horasLimite - 1, 0.1)   // resta 1h de acondicionamiento
  velocidadRequerida <- distancia / horasViajeDisponibles
  PARA tipo EN [BICICLETA, MOTO, AUTO]   // de mas barato a mas caro
     SI tipo.velocidadKmH >= velocidadRequerida ENTONCES DEVOLVER tipo
  DEVOLVER AUTO
```

---

## 2. GRASP con Búsqueda por Vecindad Variable (GRASP-VNS)

### 2.1 Estructuras de datos requeridas

Reutiliza `Punto`, `Pedido`, `TipoVehiculo`, `Ruta`, `Solucion` de la sección 1.1, y agrega:

| Estructura | Campos | Descripción |
|---|---|---|
| `Candidata` | `rutaExistente (o null), posicionInsercion, tipoNuevaRuta, costoMarginal` | Una opción de inserción evaluada durante la construcción golosa. |
| `RCL` | `List<Candidata>` (subconjunto ordenado) | Lista restringida de candidatas: las de menor costo marginal. |

### 2.2 Variables del algoritmo

| Variable | Símbolo | Descripción |
|---|---|---|
| Parámetro de aleatoriedad | `α_GRASP ∈ (0,1]` | Fracción de candidatas que entran a la RCL (tamaño = `round(α_GRASP · |candidatas|)`, mínimo 1). |
| Iteraciones GRASP | `maxIter` | Número de veces que se repite construcción + mejora (criterio de parada, o tiempo límite). |
| Estructuras de vecindad | `k = 1..K` | N1 Relocate, N2 Swap, N3 2-opt intra-ruta, N4 cambio de tipo de vehículo (`K=4`). |
| Solución candidata | — | Solución construida en la iteración actual, antes/después de VND. |
| Mejor solución global | — | Mejor solución factible encontrada en todas las iteraciones (*best-found*). |

### 2.3 Pseudocódigo

```
ALGORITMO GraspVnsVRP(pedidos, deposito, α_GRASP, maxIter, K=4)

  mejorGlobal <- nulo ; costoMejorGlobal <- +infinito

  PARA it <- 1 HASTA maxIter HACER

    // ---- FASE 1: Construccion golosa aleatorizada (GRASP) ----
    ordenPrioridad <- pedidos ordenados por horasLimite ascendente   // 2. Ordenamiento por prioridad
    solucion.rutas <- {}

    PARA cada pedido p EN ordenPrioridad HACER          // 3. Construccion golosa aleatorizada
      candidatas <- {}
      PARA cada ruta r EN solucion.rutas HACER
        PARA cada posicion pos EN 0..|r.secuencia| HACER
          SI insertar(p, r, pos) es factible (capacidad y horasLimite) ENTONCES
             candidatas <- candidatas + Candidata(r, pos, costoMarginal)
      PARA cada tipo EN TipoVehiculo.values() HACER
        SI ruta_nueva([p], tipo) es factible ENTONCES
           candidatas <- candidatas + Candidata(nuevaRuta=tipo, costoMarginal)

      SI candidatas = vacio ENTONCES
         solucion.rutas <- solucion.rutas + Ruta(AUTO, [p])   // garantiza 100% de cobertura
      SINO
         ordenar candidatas por costoMarginal ascendente
         RCL <- primeras round(α_GRASP * |candidatas|) candidatas  (minimo 1)
         elegida <- elemento aleatorio de RCL
         aplicar elegida sobre solucion (insertar p, o abrir ruta nueva)
    FIN PARA (pedidos)

    // ---- FASE 2: Busqueda por Vecindad Variable (VND) ----
    k <- 1
    MIENTRAS k <= K HACER
      mejora <- explorar_vecindad(k, solucion)     // ver 2.4
      SI mejora ENTONCES k <- 1
      SINO k <- k + 1
    FIN MIENTRAS

    // ---- Actualizacion de la mejor solucion ----
    SI solucion es factible Y costo(solucion) < costoMejorGlobal ENTONCES
       costoMejorGlobal <- costo(solucion); mejorGlobal <- solucion

  FIN PARA (iteraciones GRASP)                      // 6. Criterio de parada

  DEVOLVER mejorGlobal
```

### 2.4 Estructuras de vecindad (fase VND)

```
N1 - RELOCATE
  PARA cada pedido p en cada ruta origen HACER
    PARA cada (ruta destino, posicion) posible HACER
      SI mover p a esa posicion mantiene factibles origen y destino
         Y reduce el costo total ENTONCES
           aplicar movimiento; DEVOLVER mejora=true
  DEVOLVER mejora=false

N2 - SWAP
  PARA cada par de rutas (A,B), pedido i en A, pedido j en B HACER
    SI intercambiar i y j mantiene factibles A y B
       Y reduce el costo total ENTONCES
         aplicar intercambio; DEVOLVER mejora=true
  DEVOLVER mejora=false

N3 - 2-OPT INTRA-RUTA
  PARA cada ruta, cada segmento [i..j] de su secuencia HACER
    SI invertir el segmento mantiene la ruta factible
       Y reduce su distancia/costo ENTONCES
         aplicar inversion; DEVOLVER mejora=true
  DEVOLVER mejora=false

N4 - CAMBIO DE TIPO DE VEHICULO (flota heterogenea)
  PARA cada ruta HACER
    PARA cada tipo mas barato que el tipo actual (bicicleta < moto < auto) HACER
      SI la ruta completa sigue siendo factible con ese tipo ENTONCES
         cambiar tipo de la ruta; DEVOLVER mejora=true
  DEVOLVER mejora=false
```

---

## 3. Notas de diseño relevantes para la evaluación numérica

- **Factibilidad = cumplimiento de plazo**: ambos algoritmos verifican, en cada inserción o
  movimiento, que el tiempo acumulado de viaje (distancia/velocidad del tipo de vehículo) más el
  tiempo de servicio (1h/parada) no supere `horasLimite` de ningún pedido de la ruta. Un pedido que
  no cabe en ninguna ruta se fuerza a una ruta individual en AUTO (el vehículo más rápido), de modo
  que el 100% de cobertura de la política de servicio nunca se sacrifica por optimizar costo.
- **Asignación de flota heterogénea**: en ACS se decide vía `AsignadorFlota.tipoRecomendado` al abrir
  cada ruta (regla determinística basada en la velocidad mínima requerida por el pedido más urgente);
  en GRASP-VNS se decide tanto en la construcción (se evalúan los 3 tipos al abrir ruta) como en la
  mejora (vecindad N4, que migra rutas a un vehículo más barato cuando la capacidad y el plazo lo permiten).
- **Priorización por nivel de servicio**: en ambos algoritmos los pedidos con menor `horasLimite`
  (4h, 8h, 12h, 18h) se procesan/visitan antes que los de 36h, tanto en el orden de construcción
  (GRASP) como en la función heurística de la regla de transición (ACS).
