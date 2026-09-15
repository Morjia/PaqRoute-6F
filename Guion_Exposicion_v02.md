# Guion de exposición — Informe de Selección de Algoritmos v2.0

Duración estimada total: ~14-16 minutos. Dividido en 4 bloques, uno por integrante,
siguiendo el mismo orden del índice del documento. Los corchetes `[...]` son
indicaciones de qué mostrar en pantalla mientras se habla, no se leen en voz alta.

---

## Bloque 1 — Introducción y alcance (≈2 min)
**Expositor sugerido: Valverde Varillas, Juan Diego**

[Mostrar portada y luego la sección 1. Introducción]

"Buenos días/tardes. Vamos a presentar el Informe de Selección de Algoritmos,
versión 2.0, del componente planificador de PaqRoute, el sistema de ruteo de
entregas de la empresa PaqRap.

Este planificador tiene que generar y replanificar en tiempo real las rutas de
reparto de una flota de autos, motos y bicicletas, sobre una red vial modelada
como una cuadrícula de 70 kilómetros de largo por 50 de ancho, con calles de
doble sentido y sin diagonales ni curvas.

[Mostrar 1.1 Alcance del planificador]

Para esta primera etapa del proyecto nos enfocamos en tres de las variables
que debe resolver el planificador: primero, las **rutas de reparto**, es decir
la secuencia óptima de tramos que recorre cada unidad; segundo, la **gestión de
niveles de servicio priorizados**, porque PaqRap se compromete a que el 100%
de los pedidos llegue dentro de su plazo —36 horas en la modalidad regular, o
18, 12, 8 o 4 horas en las priorizadas—; y tercero, la **asignación de
capacidades y flota heterogénea**, repartiendo la carga entre autos, motos y
bicicletas para minimizar el costo por kilómetro recorrido.

Las demás variables del alcance completo —gestión de almacenes intermedios,
restricciones de turno, bloqueos de calles, replanificación ante incidencias y
multiescenario— se documentan en el enunciado, pero quedarán para una siguiente
iteración del planificador; por eso el análisis y la implementación que van a
ver a continuación se concentran en esas tres primeras."

---

## Bloque 2 — Ant Colony System: funcionamiento y aplicación (≈3 min)
**Expositor sugerido: Sánchez Escobar, Diego Alonso**

[Mostrar 2.1 Ant Colony System (ACS) → 2.1.1 Funcionamiento]

"El primer algoritmo candidato es el Ant Colony System, o ACS. Es una
metaheurística de inteligencia de enjambre: un grupo de hormigas construye
rutas de manera distribuida, guiadas por dos señales: el rastro de feromona
que van dejando en los arcos del grafo, y una heurística asociada al costo de
moverse de un nodo a otro.

A diferencia del Ant Colony clásico, el ACS agrega dos ingredientes: una regla
de decisión que privilegia explotar los mejores arcos ya conocidos, y una
actualización de feromona que ocurre apenas una hormiga cruza un arco —esto
evita que todas las hormigas de una misma ronda terminen siguiendo
exactamente el mismo camino.

[Señalar los 5 pasos numerados del funcionamiento]

El algoritmo sigue 5 pasos: se inicializa la feromona de manera uniforme en
todos los arcos; cada hormiga construye su ruta eligiendo el siguiente nodo
con esa regla de transición; después de cada arco recorrido se hace una
actualización local que reduce un poco la feromona para fomentar la
exploración; al cerrar cada ronda solo la mejor solución global refuerza su
feromona —esto es lo que se llama una regla elitista—; y el ciclo se repite
hasta un número máximo de iteraciones o un tiempo límite.

[Mostrar 2.1.2 Aplicación en el caso y justificación]

En el caso de PaqRoute, el grafo donde trabajan las hormigas es exactamente la
cuadrícula vial: cada esquina es un nodo, cada tramo de calle un arco, y una
ruta construida por una hormiga es la secuencia que va a recorrer un auto, una
moto o una bicicleta para llevar los paquetes desde su almacén de despacho
hasta el cliente. Además, los cinco niveles de servicio de PaqRoute se pueden
incorporar directamente en la heurística del algoritmo, para que se priorice
primero a los paquetes con menos margen de tiempo."

---

## Bloque 3 — ACS: estructuras, variables y pseudocódigo (≈4 min)
**Expositor sugerido: Espinoza Damián, Favio Waldo**

[Mostrar 3.1.1 Estructuras de datos requeridas — tabla]

"Ahora vamos a la parte de implementación de ACS: qué estructuras de datos y
qué variables necesita el algoritmo para funcionar.

Primero las estructuras. Un `Punto` es simplemente una esquina de la
cuadrícula, con sus coordenadas x, y en kilómetros. Un `Pedido` es cada
registro que llega del archivo mensual de envíos: quién es el cliente, dónde
está, cuántas unidades pide y cuál es su plazo límite. `TipoVehiculo` es un
enumerado con los tres tipos de unidad —auto, moto, bicicleta— cada uno con su
capacidad, su velocidad y su costo por kilómetro. Una `Ruta` es la secuencia
ordenada de pedidos que va a atender una unidad, y una `Solucion` es el
conjunto completo de rutas que cubre todos los pedidos de una corrida.

Las dos estructuras más particulares de ACS son la matriz de feromona, que
llamamos tau, y la función heurística, que llamamos eta. La matriz de
feromona tiene una fila y una columna por cada pedido, más una para el
depósito, y cada celda tau-i-j guarda qué tan bueno ha resultado, en la
experiencia acumulada del algoritmo, ir del nodo i al nodo j. La heurística
eta combina cercanía y urgencia: se calcula como uno entre el plazo límite del
pedido destino, multiplicado por uno entre la distancia —así, mientras más
cerca y más urgente sea un pedido, mayor es su valor de eta, y más atractivo
resulta para la hormiga.

[Mostrar 3.1.2 Variables del algoritmo — tabla]

En cuanto a variables de control: `m` es el número de hormigas por iteración,
`I` el número de iteraciones. `alfa` y `beta` son los pesos que controlan
cuánto pesa la feromona frente a la heurística en la regla de decisión. `rho`
es la tasa de evaporación y refuerzo de feromona, y `q0` es la probabilidad de
que la hormiga explote directamente el mejor arco conocido en vez de explorar
otras opciones. Por último, `tau0` es el valor inicial de feromona, calculado
a partir del costo de una solución simple de vecino más cercano.

[Mostrar 3.1.3 Pseudocódigo — recorrer el diagrama en pantalla]

Con esto ya podemos leer el pseudocódigo. El algoritmo arranca calculando esa
solución de vecino más cercano para fijar `tau0`, y llenando la matriz de
feromona con ese valor.

Luego entra al ciclo de iteraciones. En cada iteración, cada una de las `m`
hormigas construye su propia solución completa: ordena los pedidos pendientes
por urgencia, y va abriendo rutas una por una. Para cada ruta nueva, primero
elige el tipo de vehículo más conveniente según la urgencia del pedido más
crítico que le toca atender —eso lo hace una función que vamos a explicar en
un momento—, y después va agregando pedidos a esa ruta, nodo por nodo,
siempre y cuando quepan en la capacidad del vehículo y no hagan que ningún
pedido de la ruta incumpla su plazo. Para decidir cuál es el siguiente pedido,
usa la regla de transición del ACS: con probabilidad `q0` va directo al mejor
arco conocido, y si no, elige por sorteo ponderado entre todos los que son
factibles. Cada vez que se mueve de un nodo a otro, aplica esa actualización
local de feromona que mencionamos antes.

Cuando ya no caben más pedidos en la ruta, se cierra y se abre una ruta nueva,
hasta que no queda ningún pedido pendiente. Y si en algún caso un pedido no
cabe en ninguna ruta ni siquiera solo, se le asigna una ruta individual en
auto, para garantizar que ese pedido sí llegue a tiempo.

Al terminar cada iteración se compara la mejor solución de esa ronda contra la
mejor de todo el proceso, y se aplica la actualización global: se evapora un
poco toda la matriz de feromona, y solo se refuerzan los arcos que forman
parte de la mejor solución encontrada hasta ese momento. Esto se repite hasta
completar las iteraciones configuradas, y al final el algoritmo devuelve la
mejor solución global.

[Mostrar 3.1.4 Regla de asignación de flota]

Por último, esta pequeña función que mencioné, `tipoRecomendado`, es la que
decide qué vehículo usar: calcula qué tan rápido se necesitaría viajar para
llegar a tiempo al pedido más urgente, y elige el vehículo más barato —empieza
probando bicicleta, luego moto, luego auto— que aún así alcance esa velocidad
requerida. Esta misma función la reutiliza también el segundo algoritmo, que
va a presentar mi compañero a continuación."

---

## Bloque 4 — GRASP-VNS: funcionamiento, aplicación, estructuras y pseudocódigo (≈5 min)
**Expositor sugerido: Portalatino Munguía, Jeyson David**

[Mostrar 2.2 GRASP con Búsqueda por Vecindad Variable → 2.2.1 Funcionamiento]

"El segundo algoritmo candidato es GRASP con Búsqueda por Vecindad Variable,
GRASP-VNS. Es una metaheurística de dos fases: primero se construye una
solución inicial de forma golosa y aleatorizada —insertando cada cliente en la
posición factible más barata, elegida al azar entre las mejores opciones de
una lista restringida—, y después esa solución se mejora explorando distintas
estructuras de vecindad, de menor a mayor complejidad, hasta que ningún
movimiento adicional logra mejorarla.

[Señalar los 6 pasos numerados]

Sigue 6 pasos: se inicializan el parámetro de aleatoriedad alfa y las
estructuras de vecindad a explorar; se ordenan los pedidos pendientes por
urgencia; se construye una solución golosa aleatorizada; se aplica la
búsqueda por vecindad variable; se actualiza la mejor solución encontrada; y
se repite hasta un número máximo de iteraciones o un tiempo límite.

[Mostrar 2.2.2 Aplicación en el caso]

En PaqRoute, la fase constructiva procesa primero los pedidos con plazos más
ajustados —4, 8, 12 y 18 horas— antes que los del plazo regular de 36 horas, e
inserta cada uno considerando la capacidad y la velocidad del vehículo
disponible. Esto incorpora de forma explícita la prioridad del cliente
directamente en la función de costo del algoritmo.

[Mostrar 3.2.1 Estructuras de datos requeridas]

Para la implementación, GRASP-VNS reutiliza las mismas estructuras que ya
presentó mi compañero —Punto, Pedido, TipoVehiculo, Ruta y Solución— y agrega
dos nuevas: una `Candidata`, que es cada opción de inserción que se evalúa
durante la construcción golosa —con su costo marginal, es decir, cuánto
subiría el costo total si se aplica—, y la `RCL`, la Lista Restringida de
Candidatos, que es simplemente el subconjunto de las mejores candidatas entre
las que se elige al azar.

[Mostrar 3.2.2 Variables del algoritmo]

Las variables de control son: `alfa_GRASP`, que define qué porcentaje de las
candidatas entra a la RCL; `maxIter`, el número de iteraciones de GRASP; y
`K`, la cantidad de estructuras de vecindad que se exploran en la fase de
mejora, que en nuestro caso son 4.

[Mostrar 3.2.3 Pseudocódigo]

El algoritmo, en cada iteración, primero construye una solución desde cero:
ordena los pedidos por urgencia y, para cada uno, evalúa todas las formas
posibles de incorporarlo —insertarlo en alguna ruta ya abierta, o crear una
ruta nueva con cualquiera de los tres tipos de vehículo—, calcula el costo
marginal de cada opción, arma la lista restringida con las mejores, y elige
una al azar de esa lista. Si ningún pedido cabe en ninguna parte, se le
asigna, igual que en ACS, una ruta individual en auto para no incumplir la
política de entrega.

Después de construir la solución, entra a la fase de mejora, que es la
Búsqueda por Vecindad Variable.

[Mostrar 3.2.4 Estructuras de vecindad]

Aquí exploramos cuatro tipos de movimiento: `relocate`, que prueba mover un
pedido de una ruta a otra posición; `swap`, que prueba intercambiar un pedido
de una ruta con uno de otra ruta; `2-opt`, que prueba invertir un tramo dentro
de una misma ruta para acortar la distancia recorrida; y el cuarto, que es el
que conecta directamente con la asignación de flota heterogénea: probar si una
ruta completa puede pasar a un vehículo más barato —de auto a moto, o de moto
a bicicleta— sin dejar de cumplir la capacidad ni los plazos.

La lógica de esta búsqueda es: se empieza probando el primer tipo de
movimiento; si se encuentra una mejora, se aplica y se vuelve a empezar desde
el primer tipo, porque ese cambio pudo haber abierto nuevas oportunidades; si
no se encuentra ninguna mejora, se pasa al siguiente tipo de movimiento. Esto
continúa hasta agotar los cuatro tipos sin encontrar más mejoras.

Al final de cada iteración, si la solución resultante es factible —cumple el
100% de los plazos— y es más barata que la mejor encontrada hasta el momento,
se guarda como la nueva mejor solución. Y esto se repite durante todas las
iteraciones configuradas, devolviendo al final la mejor solución de todo el
proceso."

---

## Cierre (≈1 min)
**Cualquier integrante, o entre todos**

"En resumen: ambos algoritmos resuelven el mismo problema —construir rutas
factibles que cumplan el 100% de los plazos comprometidos, al menor costo
posible, repartiendo la carga entre autos, motos y bicicletas— pero con
enfoques distintos. ACS aprende de forma incremental a través del rastro de
feromona a lo largo de muchas iteraciones, mientras que GRASP-VNS combina una
construcción rápida y aleatorizada con una búsqueda local sistemática que
refina cada solución antes de pasar a la siguiente iteración. En la siguiente
etapa del proyecto vamos a someter ambos algoritmos a experimentación
numérica para comparar su desempeño. Gracias."

---

## Notas para la grabación
- Si el curso pide que se vea el rostro o que todos participen, se puede
  repartir cada bloque en dos micro-turnos (una persona presenta la mitad de
  las diapositivas de su bloque y su pareja la otra mitad), manteniendo el
  mismo contenido.
- Para las secciones de pseudocódigo, conviene ir señalando con el mouse o un
  puntero cada bloque (`Inicialización`, `Construcción`, etc.) mientras se
  habla, en vez de leer literalmente el pseudocódigo línea por línea.
- Practicar el Bloque 3 y el Bloque 4 (pseudocódigo) en voz alta al menos una
  vez, son los más densos y los que más fácil se pueden quedar cortos de
  tiempo o sonar leídos.
