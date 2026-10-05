# Registro de requerimientos trazados — PaqRap 26-2
<!-- Estado: REGISTRO DE TRABAJO, no LE formal. -->
<!-- REGLA DE LECTURA: todo lo que NO tenga "Fuente: <profesor>" es supuesto del equipo y va marcado [SUPUESTO]. -->
<!-- Los requerimientos salen de lo que dijo el profesor en sesiones/documentos, no de invención propia. -->
<!-- Borrador previo `equipo/le/lista_requisitos_inicial.md` (42 RF) es insumo, no fuente. -->

## 1. Alcance del producto
- Sistema de gestión operativa diaria de PaqRap con planificador metaheurístico y dos modos analíticos sobre el mismo motor: operación día a día, simulación 5D y simulación de colapso.
  - Fuente: caso, `fuentes/enunciado/caso_paqrap.md` (necesidades i-iii: registrar, planificar/replanificar, monitoreo gráfico); decisión `requirements/product-positioning`.
- Las simulaciones son el mismo sistema visto en cámara rápida, no un sistema aparte.
  - Fuente: profesor en `fuentes/sesiones/2026-08-25_zoom.txt`: la simulación es "condensación del tiempo de visualización" con la misma cantidad de datos.
- Enfoque de alcance deliberadamente acotado ("simplificaciones académicas para no complicar"); el alcance definido ya es complejo: preguntar sirve para saber qué DEJAR DE LADO, no asumir.
  - Fuente: `fuentes/sesiones/2026-09-15_zoom_qa.vtt` (apertura 00:09:15-00:09:49: "el problema ya con lo definido como alcance es bastante complicado").

## 2. Actores y roles
- Registrador: registra pedidos (destino/cantidad, plazo normal o priorizado, nada más); tiene pestaña aparte solo en operación día a día.
  - Fuente: `fuentes/sesiones/2026-09-01_zoom_qa.txt` L536-539 ("cantidades, cliente/destino y plazo… nada más… tiene su pestaña aparte").
- Operador del sistema: ejecuta las planificaciones del día a día.
  - Fuente: `fuentes/sesiones/2026-09-01_zoom_qa.txt` (tres roles: registrador, operador, usuario de simulación/planificación).
- Usuario de simulación/planificador: corre 5D y colapso, ve comportamiento de mercado/flota.
  - Fuente: misma sesión 01-09.
- Quien INICIA la simulación tiene el control exclusivo de mutación (averías/bloqueos/cambios); los demás conectados son observadores view-only con paneles y zonas independientes.
  - Fuente: `fuentes/sesiones/2026-09-01_zoom_qa.txt` L290-302 (solo el iniciador provoca averías; cada persona en su cuadrante con panel propio).
- Sin login, sin permisos, sin autenticación en el curso (pantalla de login opcional y desaconsejada; seguridad = tema real pero fuera del curso).
  - Fuente: `fuentes/sesiones/2026-09-01_zoom_qa.txt` L536-542 y L614-618 ("No necesitan poner usuario contraseña en ninguna parte").

## 3. Escenarios
- Día a día: operación en tiempo real (no existe "simulación día a día").
  - Fuente: `fuentes/sesiones/2026-08-25_zoom.txt` ("No existe simulación día a día… existen operaciones día a día y existen simulaciones").
- Pestañas separadas por escenario; el registro es pestaña adicional de la operación.
  - Fuente: `fuentes/sesiones/2026-09-01_zoom_qa.txt` L27-47 ("Cada escenario es distinto, deben ir de manera separada… Dos pestañas… El registro sería una pestaña adicional").
- 5D: 5 días simulados visibles en 30-60 minutos, a elección del equipo dentro del rango.
  - Fuente: caso + `fuentes/sesiones/2026-09-01_zoom_qa.txt` L440-458.
- Colapso = PRIMER paquete que no puede entregarse dentro de su plazo; en el curso ahí termina la corrida ("colapsa de arranque" si la capacidad física lo hace imposible desde el inicio).
  - Fuente: `fuentes/sesiones/2026-08-25_zoom.txt` L382 ("En el primer pedido que no se puede entregar, llegó el colapso logístico"), L441, L455 ("solo basta que colapse… y ahí queda"); `fuentes/sesiones/2026-09-15_zoom_qa.vtt` 00:51:46 ("1600 paquetes… Colapsa el sistema de arranque").
- Mismas reglas de negocio en los 3 escenarios; solo cambia la escala temporal.
  - Fuente: sesiones 08/09 y video obligatorio (ver §7).
- Arranque de simulación en fecha arbitraria: arranca en VACÍO (nada anterior existe), almacenes al 100%, UTs vacías en central; el profesor admite también suponer datos previos, pero recomienda vacío por simpleza ("es como que instalan el sistema por primera vez").
  - Fuente: `fuentes/sesiones/2026-09-08_zoom_qa.vtt` L549-561, L589-593 ("también funciona" con datos previos), L637 (día a día también vacío), L645-649.

## 4. Dominio operativo (todo dicho por el profesor)
- Ciudad: retícula 70x50 km, nodos cada 1 km, sin diagonales/curvas, doble sentido, origen (0,0) abajo-izquierda; clientes referidos por nodo (x,y).
  - Fuente: hoja oficial PR#4, `fuentes/hojas_oficiales/2026-09-12/PR_Proyecto.md`.
- Almacenes: Central (27,14), Nor-Oeste (12,38), Este (57,27) — CAMBIADOS el 2026-09-08 (antes 25,15 / 55,27).
  - Fuente: `fuentes/hojas_oficiales/2026-09-12/PR_Proyecto.md` PR#5 (bloque nuevo con fecha) + `Mapa.md` (leyenda C/NO/E).
- Central con inventario infinito; intermedios de 1.000 unidades con recarga diaria instantánea a las 23:59:59.
  - Fuente: `fuentes/enunciado/caso_paqrap.md` L7.
- Flota: autos 10 (cap. 24), motos 15 (cap. 8), bicicletas 12 (cap. 4); códigos oficiales TA/TB/TM + correlativo (ej. TA01/TB10/TM03); existen placas pero "nadie usa las placas", se controla por código; flota NO cambia durante una presentación.
  - Fuente: `fuentes/hojas_oficiales/2026-09-12/Flota.md` + PR#17/PR#18; `fuentes/sesiones/2026-09-09_indicaciones.vtt` L61-105 (códigos con semántica vs ID correlativo; "no inventen otra codificación").
- Velocidades: PARÁMETRO, conflicto abierto caso (auto 40/moto 25/bici 12) vs hoja Flota (20/40/14); el profesor admitió que la hoja eran pruebas suyas sin deshacer ("me hiciste dudar, tengo que volver a revisar… sea cual sea la velocidad que se define, es un parámetro"). Rige su corrección en hoja verde. No hardcodear.
  - Fuente: caso; `Flota.md`; `fuentes/sesiones/2026-09-15_zoom_qa.vtt` L857-969.
- Velocidad en caliente: por TIPO de unidad (no por vehículo), aplica desde la siguiente planificación/iteración; todo vehículo en almacén o ruta se replanifica cada iteración.
  - Fuente: PR#15/PR#16 (`PR_Proyecto.md` L181-188); sesión 01-09 L306-318 (el profesor la cambia en vivo en demos: 20→25, 30→35).
- Costos: S/8 auto, S/6 moto, S/3 bici por km; el costo es criterio SECUNDARIO frente a postergar el colapso ("el que haga la menor ruta… no debería ser prioridad… debe estar asociado a que el sistema opere el mayor tiempo posible").
  - Fuente: caso; `fuentes/sesiones/2026-09-15_zoom_qa.vtt` 00:14:56-00:16:12.
- Plazos: normal 36 h; priorizados 4/8/12/18 h; el plazo corre desde el timestamp `##d##h##m` del registro.
  - Fuente: caso; PR#8 (`PR_Proyecto.md`: "##d##h##m es el día, hora y minuto en que el pedido llegó").
- Entrega: 1 hora de acondicionamiento por entrega parcial, FUERA del plazo (la última entrega debe caer dentro del plazo sin contar su hora); entregas parciales permitidas.
  - Fuente: caso; PR#11/PR#14; `fuentes/sesiones/2026-09-02_zoom_qa.vtt` (regla "solo a la primera o a cada una" → a cada una).
- Turnos: 07:00/15:00/23:00; conductores NO son variable (flota ≠ choferes; cantidad irrelevante, "hay suficientes"); 1 h de alimentación en cualquier punto del rango de la jornada ("se detiene donde le toque"); cambio de conductor instantáneo y despreciable (el entrante espera en el punto de paso); en experimentación se asume el mismo chofer.
  - Fuente: `fuentes/sesiones/2026-09-15_zoom_qa.vtt` L85-97, L283-313, L437-443; `fuentes/sesiones/2026-09-02_zoom_qa.vtt` L761-805 (posta en primer punto de parada); PR#12.
- Carga de salida: regla interina = cantidad exacta asignada ("asuman que sale la cantidad exacta de que va a entregar"); camión lleno permitido pero inútil tras los primeros meses; la penalidad por peso/consumo fue descartada a propósito con JC; cierre definitivo pendiente (PR#20).
  - Fuente: `fuentes/sesiones/2026-09-09_zoom_qa.vtt` L153; `fuentes/sesiones/2026-09-15_zoom_qa.vtt` 00:10:31, 00:16:28-00:17:07.
- Combustible/gasolina: infinito, sin recarga ("Yo lo he quitado… con lo que tienen tienen para rato").
  - Fuente: `fuentes/sesiones/2026-09-02_zoom_qa.vtt` (pregunta de Gabriel).

## 5. Datos de entrada publicados
- Ventas: `fuentes/datos/ventas.v20260909/` (36 archivos `ventas.YYYYMM.txt`, ~161k registros, rampa 641→5.000/mes con techo desde 2026-09); formato `##d##h##m:posX,posY,cIdCliente,qq,hl` (ej. `11d13h31m:45,43,c9167,12,36`); `qq` 01-10 con cero; `hl` en {04,08,12,18,36}; coordenadas cubren 0-70 x 0-50.
  - Fuente: `fuentes/datos/` (verificación directa) + PR#8.
- Bloqueos: `fuentes/datos/bloqueos.v20260909/` (36 archivos `bloqueo.YYMM.txt`, 516-672 líneas); formato `##d##h##m-##d##h##m:x1,y1,...` (ej. `01d00h00m-01d03h49m:67,37,67,22` = tramo M); tramos rectos ortogonales del catálogo A-O (15 poligonales en `Mapa.md`); existen intervalos solapados del mismo tramo (unir); duraciones 15 min-4 h, cruzan medianoche; solo polígonos abiertos; el profesor garantiza no encerrar zonas; las averías NO generan bloqueos.
  - Fuente: `fuentes/datos/` + PR#7 + `Mapa.md` + `fuentes/sesiones/2026-08-25_zoom.txt` (polígono abierto/cerrado, vuelta en U en nodo bloqueado).
- Preventivo: `fuentes/datos/mant.preventivo.09.10.txt` (37 registros `aaaammdd:TTNN`, 1 UT/día, cada UT una vez por bimestre sep-oct); indisponibilidad total 00:00-23:59 del día marcado; ocurre en almacén central; duraciones orales bici=1 turno/moto=1 día/auto=2 días pendientes de formalizar (PR#19 lo declara: "FALTA poner duración"); mecánica = registro de inicio + duración por tipo; archivo bimensual, el equipo genera ene-2026→dic-2028 (destinatario del "Debe generar" sin explicitar: monitorear).
  - Fuente: `fuentes/datos/` + PR#19 + `fuentes/sesiones/2026-09-09_zoom_qa.vtt` L277-301 (publicado "por cortesía", se formalizará por correo).
- Nombres reales de disco difieren del spec antiguo (`ventas2026mm`, `aaaamm.bloqueadas`); adoptar los reales.
- Colapso esperado por el profesor: marzo-abril 2028 ("mi expectativa, pero no [verificado]… lo he hecho muy rápido"); pidió reportar errores en sus datos.
  - Fuente: `fuentes/sesiones/2026-09-09_indicaciones.vtt` L133-141.

## 6. Averías (todo dicho por el profesor)
- Solo manuales, provocadas por el controlador desde el visualizador: clic en la UT del mapa o panel por código + tipo. "Lo más importante… es que esas averías se generen manualmente… por la interfaz gráfica".
  - Fuente: `fuentes/sesiones/2026-09-08_zoom_qa.vtt` L33-53.
- Tres tipos, permanencia en el lugar: T1 2 h y reanuda donde quedó (o se reasigna); T2/T3 4 h y luego traslado instantáneo "mágico" al central con paquetes no trasvasados; T2 disponible al turno siguiente; T3 +2 días y retorno al turno 15:00-23:00.
  - Fuente: `fuentes/sesiones/2026-09-08_zoom_qa.vtt` L149-245; PR#3 (`PR_Proyecto.md` L27-42).
- Trasvase entre UTs (pasar paquetes de una unidad a otra): 30 minutos en sim-time; K afecta solo la percepción de pared (no la duración del dominio).
  - Fuente: `fuentes/sesiones/2026-09-08_zoom_qa.vtt` L101-133 ("si demora 30 min para el trasvase es 30 min… todo lo que yo voy a precisar… es en tiempo real"). CORRECCIÓN 2026-10-01 (user decision #1, PR 3a): la redacción anterior "en TIEMPO REAL, escalado por K" era una mala lectura — la cita del profesor fija 30 min de TIEMPO DE SIMULACIÓN; K acelera la percepción de reloj de pared, nunca la duración del dominio.
- Si un pedido vence durante la avería, el planificador puede mandar otra UT y luego hacer el trasvase ("Hay varias alternativas. Estoy mencionando una de ellas").
  - Fuente: `fuentes/sesiones/2026-09-08_zoom_qa.vtt` L205-233.
- Jerarquía: avería en calle prevalece sobre preventivo coincidente; luego sigue el mantenimiento.
  - Fuente: `fuentes/sesiones/2026-09-09_zoom_qa.vtt` L429-445.
- Reglas de generación automática: pendientes con JC ("Voy a ver… si es necesario hacer un esquema aleatorio… Por ahora, no"); hoy no existen. El PR#3 quedó truncado en "Se tendrán reglas de generación…".
  - Fuente: `fuentes/sesiones/2026-09-08_zoom_qa.vtt` L37-53; PR#3.
- En el producto: obligatorias. En la comparación de algoritmos del IEN: excluidas por aleatorias ("pueden omitir ese concepto… es un fenómeno aleatorio… durante las pruebas no va a haber averías"), salvo resultados muy cercanos, con justificación.
  - Fuente: `fuentes/sesiones/2026-09-08_zoom_qa.vtt` + `fuentes/sesiones/2026-09-15_zoom_qa.vtt` (Jeyson: experimento sin averías; preventivo fuera con justificación; bloqueos dentro).

## 7. Motor de simulación (video obligatorio + sesiones)
- Esquema de referencia del profesor: planificación programada fija (el algoritmo corre cada intervalo configurable, ej. 5/10/15 min); hay taxonomía de alternativas (permanente/por pedido/a demanda) pero el video desarrolla la fija y cada equipo adapta.
  - Fuente: `fuentes/sesiones/video_obligatorio/como_idear_simulacion_consumo_datos.txt` L28 ("cada cierto tiempo fijo"), L35, L137 (TA=1min, SA=5, K=14 sugeridos).
- Ta = tiempo de ejecución del algoritmo (varía con el volumen; estimarlo cerca del colapso); Sa = salto entre ejecuciones; Sc = salto de consumo; **Sc = Sa × K** (ej. 5×14=70 min de datos por ejecución).
  - Fuente: video L40-50 (TA), L112-114 (K=14 tres días, K=75 colapso; K=1 día a día), L142 (Sc=70 min).
- Restricciones: Sa > Ta obligatorio (Sa<Ta = colisiones/caída del software); Sa enorme incumple pedidos cortos (4-8 h); no cargar todo en memoria (consumo por bloques).
  - Fuente: video L100 (Sa pequeño → caída), ejemplo 8 h en sesiones.
- Cada iteración consume: bloque nuevo (hasta simTime+Sc) + planificados no despachados + en camino (reasignables); nunca lo entregado.
  - Fuente: `fuentes/sesiones/2026-09-08_zoom_qa.vtt` (3 grupos de consumo) + video (consumir datos, no tiempo).
- Delay de visualización: milisegundos/segundos, notorio pero pequeño; la aceleración se logra consumiendo más datos por vez, no optimizando el algoritmo.
  - Fuente: video L127-132 ("va a haber siempre un retardo… debe ser muy pequeño… no se espera que se demore una hora o dos horas").
- Doctrina: no existe replanificación, siempre es planificación completa; UTs en ruta = almacenes móviles.
  - Fuente: `fuentes/sesiones/2026-09-02_zoom_qa.vtt` L1685-1689 (corrección de atribución: es de esta sesión, no de 0809).
- Criterio oficial: mejor solución = colapso más tardío (proveedor A 3 meses vs B 4 meses: gana B, mismo juego de datos).
  - Fuente: video (sección de comparación) + `fuentes/sesiones/2026-09-15_zoom_qa.vtt` (receta IEN).
- IEN (receta regalada 15/09): sin averías; preventivo fuera con justificación escrita; bloqueos como corte congelado de 4-6 simultáneos (NO variables en el tiempo); estratos de carga 100→1536 paquetes/día (techo físico al punto más distante; 2000 al extremo = colapsa de arranque); N corridas por nivel con comparación estadística; equipos de 3 podrían omitir bloqueos (nosotros somos 4: aplican).
  - Fuente: `fuentes/sesiones/2026-09-15_zoom_qa.vtt` 00:35-00:52 (líneas ~1637-1700).

## 8. Contrato planificador↔frontend (congelado v1.0.0) e interacción en tiempo real
- Cada iteración emite planificación COMPLETA (nunca deltas): rutas por UT con paradas y ETA, eventos (entrega/recarga/teleport/trasvase/preventivo/avería), unidades indisponibles con fase y retorno, terminal collapse/completed con última planificación estable.
- Doble eje temporal por mensaje (`simTimeStart/simTimeEnd` + reloj real); cadencia 5D en 30-60 min.
- Límite transporte/dominio: el transporte identifica runs, ordena, autoriza y lleva payloads; NO interpreta ni duplica datos de dominio; el envelope nunca repite campos del payload y viceversa.
- Runs: `POST /api/runs` con `{config}` opaco crea e inicia (201 con `runId`, `controllerToken`, estado); `runId` opaco, rutas anidadas `/api/runs/...`, sin segmentos de versión en URL.
- Orden global: un `seq` entero estrictamente creciente por run para planes y eventos; el servidor es la única autoridad; todos los suscriptores reciben el mismo orden.
- Roles: un solo controlador por run (el creador, header `X-Controller-Token`); observadores sin credencial reciben el mismo stream; `claim` rota el token (el viejo da 401) y emite `controller-changed`; es control de demo, no autenticación.
- Comandos solo por REST (`POST /api/runs/{runId}/commands` con `commandId`); STOMP `SEND` prohibido para comandos. Invariante: cada `202` produce exactamente un evento de asiento, salvo que el run termine antes; cada `4xx` no produce ningún evento.
- Control de ejecución (`pause/resume/terminate`): surte efecto en el siguiente límite de iteración; `terminate` no genera terminal de dominio.
- Cambios de parámetro en caliente: se aplican sin esperar el límite; el asiento `parameter-changed` trae `effectiveFromSimTime`; cambios concurrentes asientan en orden de aceptación (último vale).
- Inyección de incidentes: solo `averia` en v1 (los bloqueos nacen de archivos); el efecto en el mundo viaja solo en los `IterationPlan` siguientes.
- Vocabulario cerrado de eventos: `parameter-changed`, `incident-applied`, `command-failed`, `run-paused`, `run-resumed`, `run-terminated`, `controller-changed`; un tipo nuevo implica cambio de `transportVersion`.
- Stream STOMP 1.2 en `/ws`, destino `/topic/runs/{runId}/stream`, solo suscripción (sin replay; suscribirse a un run terminado es válido y silencioso).
- Snapshot `GET /api/runs/{runId}` para pintar sin haber visto el stream y para reconexión (suscribir → snapshot → descartar `seq ≤ lastSeq`; ante hueco, re-snapshot; el run no se pausa por oyentes ausentes).
- Errores REST con `{code,message,details}`; fallos de aplicación van por `command-failed` en el stream.
- Tipos TS generados desde el schema; deuda asignada: spec `simulation-config` (change 3).
- Fuentes: `contract/plan-iteration.v1.schema.json`; `contract/CHANGELOG.md`; `openspec/specs/planner-frontend-contract/spec.md`; `openspec/changes/mock-simulador/specs/mock-transport-surface/spec.md`; decisiones de usuario 2026-09-23 en `openspec/changes/mock-simulador/continue-state.md`. NOTA: §8 es diseño del equipo (contrato congelado), no palabras del profesor; cada campo con semántica de dominio cita arriba (§4-7).

## 9. Visualizador e interfaz (casi todo del demo aceptado + sesiones)
- Mapa de retícula abstracta (no geográfico), pantalla completa inicial con paneles colapsados, leyenda visible, zoom/pan.
  - Fuente: demo 23-1 (`fuentes/evidencia_ciclos_previos/demo_23-1/demo_23-1_transcripcion.txt`) + capturas `fuentes/evidencia_visual/capturas/`.
- Barra superior: fecha/hora simulada y duración, contadores de flota por tipo, paquetes usados/capacidad (ej. `171/772` en el demo).
  - Fuente: demo 23-1 (narración de la barra) + Guía 26-1 (criterios 35-39) como referencia.
- Hover en UT: posición, destino, ETA, paquetes a bordo; hover/clic en avería: tipo; averiada con fondo rojo y detenida.
  - Fuente: demo 23-1 L11-18 ("posición… destino… tiempo de estima… paquetes"; "vehículo con fondo rojo… se puede ver cuál es el tipo").
- Panel DATOS PEDIDOS por turno; registro masivo por archivo; registro de avería por placa+tipo desde el panel.
  - Fuente: demo 23-1 L21-22 + captura `demo.png` (panel REGISTRO/DATOS PEDIDOS).
- Reporte final de 3 cifras: pedidos realizados, tiempo simulado, tiempo real de ejecución.
  - Fuente: demo 23-1 L27-34 (1077 pedidos, 170.21 h, 27 min 20 s).
- Semáforo = convención de colores de llenado (verde/ámbar/rojo) + categoría "vacío" en matriz previa, NO widget físico; rangos definidos por el equipo y parametrizables (caso NF-d: "en lo que se requiera"); categoría "vacío" y filtro por color pendientes de confirmación.
  - Fuente: `fuentes/enunciado/caso_paqrap.md` L23 (NF-d) + matriz 26-1 C21/C27/E05/E20/F15/F16/G02 (guía, no requisito).
- Tres pestañas separadas por escenario; visor multi-dispositivo (web responsiva PC/tablet/celular) con control solo del iniciador.
  - Fuente: sesiones 01-09 L27-47 y L264-347 (pestañas; "cualquier dispositivo… en distintas zonas y maneras").
- Prohibido (cero en presentación): botones de reproducción/pausa/velocidad/avance/retroceso; login; mapas realistas; diagonales/curvas.
  - Fuente: sesiones 01-09 L380-416 ("aparece y la presentación se pone 0").

## 10. Gestión operativa
- CRUD de tablas: qué va por pantalla vs script está pendiente con JC (PR#21); candidatas: flota/UTs, parámetros, almacenes, planes de mantenimiento.
  - Fuente: PR#21 + `fuentes/sesiones/2026-09-09_zoom_qa.vtt` (pregunta de Ariana: "¿debería tener… registrar… o modificar?").
- Ciclo de pedido: registrado → planificado → en camino → entregado (total/parcial); un incumplido ES el colapso, no un estado persistente.
  - Fuente: 3 grupos de consumo 0809 + colapso §3.
- Entrega registrada automáticamente al cumplirse la hora de acondicionamiento. [SUPUESTO del equipo: ningún documento describe confirmación manual; el modelo es determinista.]
- Cancelaciones de pedido: NO existen (simplificación académica deliberada "para no complicar").
  - Fuente: `fuentes/sesiones/2026-09-15_zoom_qa.vtt` L329-333 ("No hay… cancelaciones") + 00:09:15-00:09:49.
- Persistencia entre días: arrancar en vacío (opción recomendada por el profesor; admitió que suponer datos previos "también funciona").
  - Fuente: `fuentes/sesiones/2026-09-08_zoom_qa.vtt` L589-649.
- Fuentes generales: sesiones 01/09, 08/09, 15/09; PR#21.

## 11. Lo que NO es requisito (dicho explícito del profesor)
- Cancelaciones (15/09: "No"). Login/auth/permisos (01-09: "no necesitan… en ninguna parte"). Controles de reproducción (01-09: "presentación en 0"). Gasolina (0209: "Es infinito… Yo lo he quitado"). Conductores como variable (15/09 L85: "no debería entrar como variable relevante"). Mapas geográficos (retícula abstracta, PR#4). Cuarto tipo de avería (taxonomía oficial = 3, PR#3). Semáforos físicos (convención de color, NF-d). Diagonales/curvas y polígonos cerrados (PR#4/PR#7).

## 12. Parámetros (config, nunca hardcode)
- Velocidades, capacidades, costos/km, rangos de semáforo, K/Sa/Sc, duración de corrida 30-60, dimensiones y almacenes, duraciones de preventivo (cuando se formalicen), techo de colapso esperado.
- Regla madre del profesor: "Todo debe ser posible de ser modificado sin necesidad de modificar el código… que lo lea de algún lado y se pueda parametrizar".
  - Fuente: `fuentes/sesiones/2026-09-09_zoom_qa.vtt` L53.

## 13. Abiertos y monitoreo (con su fuente)
- Hoja verde del profesor: velocidades coherentes ("tengo que volver a revisar… Yo corrijo", 15/09 L957-969), PR#20 (carga llena, con JC), PR#21 (CRUD, con JC), duración y colores de PR#19 ("FALTA poner duración", 15/09 00:55:00).
- Destinatario de "generar el resto" de preventivos 2026-2028 (PR#19 sin explicitar; en datos/ solo existe 09.10; si no aparecen publicados, revivir patrón 1/día 1/UT/bimestre).
- Validación propia B5: ¿colapsa antes de abr-2028 con meseta 5.000 y capacidad 1.536/día? (capacidad calculada por el profesor en 15/09 00:47-00:52: "hasta 1536 paquetes en un día… si me piden 2000… no lo voy a poder atender").
- Leftovers de ciclos previos (cancelaciones/vuelos/15-min-cliente en Guía/matriz) solo como guía, no requisitos.
