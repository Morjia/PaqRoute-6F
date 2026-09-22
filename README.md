# PaqRoute — Componente Planificador (ACS y GRASP-VNS)

Implementación de los dos algoritmos metaheurísticos candidatos del planificador, ejecutados por
un simulador de escenario de colapso logístico: flota fija (10 autos, 15 motos, 12 bicicletas),
3 almacenes con stock y recarga diaria, bloqueos de calles, mantenimiento preventivo, niveles de
servicio priorizados y entregas parciales. No se consideran averías de unidades de transporte.

Ver `Informe_Simulacion_Colapso.md` para las estructuras, variables, pseudocódigo actualizado y
los resultados de la corrida. `Informe_Pseudocodigo_Estructuras.md` y `Guion_Exposicion_v02.md`
quedan como referencia de la version anterior (sin flota fija, sin bloqueos/mantenimiento, sin
simulación) y están desactualizados frente al modelo actual.

## Compilar y ejecutar

Requiere JDK 17+ (probado con JDK 21, usa `record` y `switch` con patrones).

```bash
javac -d out src/paqroute/*.java
java -cp out paqroute.Main
```

Los datos (36 meses de `ventas.aaaamm.txt`, 36 de `bloqueo.aamm.txt` y 18 de
`mant.preventivo.aa.m1-m2.txt`) ya vienen incluidos en `data/ventas/`, `data/bloqueos/` y
`data/mantenimiento/`. `Main` usa esas tres carpetas por **rutas relativas** al directorio desde
el que se ejecuta el programa -- por eso hay que correr `java` parado en la raíz del repositorio
(donde también está `src/`). Si quieres apuntar a otras carpetas (por ejemplo, un subconjunto con
menos meses para probar más rápido), pásalas como argumentos:

```bash
java -cp out paqroute.Main "<carpeta ventas>" "<carpeta bloqueos>" "<carpeta mantenimiento>"
```

`Main` corre el escenario de colapso con ACS y con GRASP-VNS por separado, imprimiendo en qué
momento colapsó cada uno y con qué desempeño acumulado hasta ahí (pedidos completados a tiempo,
costo total, km recorridos, entregas parciales, Ta promedio/máximo del algoritmo).

Advertencia de rendimiento: correr los 36 meses completos (2026-2028, ~160,000 pedidos) toma
del orden de 1 a 1.5 minutos por algoritmo.

## Experimentos del Informe de Diseno de Experimento

`paqroute.Experimentos` ejecuta por lotes los Experimentos 1 (consumo de SLA) y 2 (carga maxima
sostenible U*): bloques de 1 dia (00:00-24:00), 5 semillas por algoritmo, semilla derivada del
numero de tick, factor de carga lambda (cantidad x lambda, redondeo hacia arriba), seleccion de
los 20 dias mas cargados que ambos algoritmos procesan sin colapsar, y busqueda de lambda*
(duplicacion + biseccion hasta 0.05 + verificacion de monotonia con lambda*+0.1 y +0.2).
Usa todos los nucleos disponibles.

```bash
javac -d out src/paqroute/*.java
java -cp out paqroute.Experimentos
```

Opciones: `--salida=resultados --bloques=20 --semillas=5 --hilos=N --desde=2026-09-01
--hasta=2028-12-31 --max-candidatos=N --lambda-max=64 --solo-exp1`. Salidas en `resultados/`:
`resultados_corridas.csv` (Anexo 1, una fila por corrida), `tabla2_bloques.csv` (Tabla 2),
`resultados_bloques_exp1.csv` y `resultados_bloques_exp2.csv` (formato largo fecha_bloque/algoritmo/valor,
listos para el pivot del Anexo 2) y `dias_descartados.csv`. Un lambda "sobrevive" solo si ninguna
semilla colapsa; al primer colapso se dejan de correr las semillas restantes de ese lambda.

### Afinacion de hiperparametros

`paqroute.Afinacion` barre una grilla chica de hiperparametros de ACS (beta x q0) y GRASP-VNS
(alfaGRASP x iteraciones) buscando la combinacion que maximiza U* (Experimento 2), sobre un
conjunto de dias SEPARADO de los 20 bloques de `Experimentos` (por defecto 2026-09-01 a
2027-04-30, mientras que la corrida final de `Experimentos` cae en 2027-05-01 en adelante) para
no afinar sobre la misma muestra que despues se usa para comparar.

```bash
java -cp out paqroute.Afinacion
```

Opciones: `--salida=afinacion --desde=2026-09-01 --hasta=2027-04-30 --dias=5 --semillas=2
--hilos=N --lambda-max=64`. Salidas en `afinacion/`: `afinacion_corridas.csv` (una fila por
dia+combinacion) y `afinacion_resumen.csv` (promedio de U* por combinacion, de mejor a peor).

**Resultado de la grilla (5 dias de afinacion, 2 semillas):**

- **ACS**: ninguna combinacion alcanzo a GRASP-VNS en U* (la mejor de ACS quedo por debajo de la
  peor de GRASP-VNS) -- la brecha del Experimento 2 es estructural (GRASP-VNS tiene una fase de
  busqueda local que ACS no tiene), no un efecto de afinacion. `beta=3.0` si mejoraba U* de ACS en
  ~4-5% *dentro de la muestra de afinacion*.
- **GRASP-VNS**: `alfaGRASP=0.5, maxIteracionesGRASP=10` (en vez de 0.3 y 5) mejoraba U* en ~5.8%
  *dentro de la muestra de afinacion*.

**Verificacion de generalizacion** (paso obligatorio antes de adoptar cualquier hallazgo de la
afinacion: correr los 20 bloques finales de `Experimentos` con el parametro nuevo y comparar contra
la corrida con los valores originales, sobre la MISMA seleccion de bloques):

| | Muestra de afinacion (5 dias) | 20 bloques finales |
|---|---|---|
| ACS `beta=3.0` vs `2.0` | +4-5% en U* | **+0.1%** (no generalizo) |
| GRASP-VNS `alfa=0.5,iter=10` vs `0.3,5` | +5.8% en U* | **SLA -6.6%, U* +6.1%** (si generalizo, mismos 20 dias aceptados) |

**Conclusion**: `beta=3.0` de ACS se descarto (quedo en 2.0, el valor original del informe, sin
ajuste). `alfaGRASP=0.5, maxIteracionesGRASP=10` si se adoptaron como nuevo default de GRASP-VNS en
`Simulador.ParametrosAlgoritmo.DEFAULT` -- son los valores con los que se corrieron los resultados
finales en `resultados/`. El resto de los parametros de ambos algoritmos (numHormigas,
numIteracionesAcs, alfaAcs, rho, q0) sigue en sus valores originales del informe, sin ajuste.

## Estructura

```
src/paqroute/
  Punto.java, Arista.java        nodo y tramo de la cuadricula vial
  GrafoVial.java                  distancia mas corta (BFS) respetando bloqueos vigentes
  TipoVehiculo.java               enum AUTO/MOTO/BICICLETA (codigo TTNN, capacidad, velocidad, costo/km)
  Vehiculo.java                   unidad concreta de la flota fija (id, posicion, disponibilidad, estado)
  Almacen.java                    CENTRAL/NOROESTE/ESTE, stock y recarga diaria
  Pedido.java, Entrega.java       pedido (con cantidadPendiente) y cada parada/entrega parcial
  Bloqueo.java, Mantenimiento.java  ventanas de bloqueo de calles y de mantenimiento preventivo
  ContextoPlanificacion.java       estado del mundo pasado a los algoritmos en cada tick
  Ruta.java, Solucion.java         una ruta concreta y el conjunto de rutas de un tick
  RutaUtil.java                    distancia, factibilidad de plazo y costo de una secuencia de entregas
  AsignadorFlota.java              recomienda el tipo de vehiculo de menor costo TOTAL estimado
  LectorPedidos.java, LectorBloqueos.java, LectorMantenimiento.java   parsers de los 3 tipos de archivo
  AntColonySystemVRP.java          Algoritmo 1: ACS, resuelve un tick de planificacion
  GraspVnsVRP.java                 Algoritmo 2: GRASP-VNS, resuelve un tick de planificacion
  Simulador.java                   motor de la simulacion: avanza el reloj hasta el colapso
  ResultadoSimulacion.java         resumen de una corrida (momento de colapso, metricas acumuladas)
  Main.java                        corre el escenario de colapso con ambos algoritmos y compara

data/
  ventas/         ventas.aaaamm.txt (36 meses, 2026-01 a 2028-12)
  bloqueos/       bloqueo.aamm.txt (36 meses)
  mantenimiento/  mant.preventivo.aa.m1-m2.txt (18 archivos bimensuales)
```
