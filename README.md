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
cd PaqRoute-Planificador
javac -d out src/paqroute/*.java
java -cp out paqroute.Main "<carpeta ventas>" "<carpeta bloqueos>" "<carpeta mantenimiento>"
```

Los tres argumentos son opcionales; si se omiten, `Main` usa por defecto las rutas de las
carpetas de datos reales de la corrida de prueba (ver `Main.java`). `Main` lee todos los
`ventas.aaaamm.txt` de la carpeta indicada, todos los `bloqueo.aamm.txt` y todos los
`mant.preventivo.aa.m1-m2.txt`, y corre el escenario de colapso con ACS y con GRASP-VNS por
separado, imprimiendo en qué momento colapsó cada uno y con qué desempeño acumulado hasta ahí
(pedidos completados a tiempo, costo total, km recorridos, entregas parciales).

Advertencia de rendimiento: correr los 36 meses completos (2026-2028, ~160,000 pedidos) toma
del orden de 1 a 1.5 minutos por algoritmo. Para pruebas rápidas, apunta las carpetas a un
subconjunto de archivos (p.ej. un solo mes) copiándolos a una carpeta aparte.

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
```
