# Guía del Backend de PaqRoute

Guía completa y detallada de cómo funciona el backend y de todo lo que se ha construido.
Fecha: 2026-10-06 · Estado: operativo con datos reales.

---

## 1. ¿Qué es este backend?

El backend de **PaqRoute** expone el **motor de simulación del escenario de colapso logístico** de
PaqRap como una API REST. Integra el dominio y los algoritmos reales del proyecto `PaqRoute-6F`
(un **Simulador** por ticks + **ACS** y **GRASP-VNS** sobre una cuadrícula vial 70×50) dentro de una
arquitectura en capas estilo CODA.

Características clave:

- **Java 17 + Spring Boot 4.0.6 + Maven** (Spring Web MVC, sin base de datos, sin autenticación).
- **Sin base de datos**: los datos viven en memoria, cargados desde los archivos `.txt` reales.
- **Sin login, sin servicios externos** (ni mapas, ni Cloudinary, ni SMTP).
- El motor es el **mismo que el del informe**: los resultados de colapso coinciden con él.

---

## 2. Stack y estructura del proyecto

```
backend/
├── pom.xml                      # Maven (Spring Boot 4.0.6, Java 17, Lombok)
├── mvnw / mvnw.cmd / .mvn/      # Maven wrapper
├── Dockerfile                   # Build multietapa + usuario sin privilegios
├── docker-compose.yml           # Monta ../data (:ro) + volumen de logs
├── nginx.conf                   # Reverse proxy de ejemplo
├── paqroute.service             # Unit de systemd
├── src/main/java/com/paqroute/backend/
│   ├── PaqRouteApplication.java
│   ├── config/                  # Cors, SecurityHeadersFilter, Scheduling, SeedData
│   ├── controller/              # Health, Simulacion, Consulta
│   ├── dto/request/             # SimulacionRequestDTO
│   ├── dto/response/            # ResultadoSimulacionResponseDTO, ResumenDatosResponseDTO
│   ├── enums/                   # TipoVehiculo
│   ├── exception/               # Excepciones + GlobalExceptionHandler
│   ├── loader/                  # Lector*Txt (ventas, bloqueos, mantenimiento)
│   ├── mapper/                  # ResultadoSimulacionMapper
│   ├── model/                   # Entidades de dominio (POJOs)
│   ├── engine/                  # Motor: Simulador, ACS, GRASP-VNS, GrafoVial, ...
│   ├── repository/              # Repositorios en memoria
│   └── service/                 # CargaDatos, Simulacion, Consulta, Notificacion
├── src/main/resources/application.properties
└── docs/
    ├── api/                     # index.md, simulacion.md, datos.md
    └── deploy/                  # preguntas-despliegue-ubuntu-24.md
```

**48 clases** de `main` (sin tests; los tests se eliminaron por decisión).

---

## 3. Fuentes de datos (los `.txt` reales)

El backend NO inventa datos: lee los archivos reales de `PaqRoute-6F/data` (carpeta por defecto
`../data`, configurable con `PAQROUTE_DATA_DIR`).

| Carpeta | Archivos | Líneas | Formato | Semántica |
|---|---|---|---|---|
| `ventas/` | `ventas.aaaamm.txt` (36) | 160 010 | `##d##h##m:posX,posY,cIdCliente,qq,hl` | Pedidos: posición en grid, cantidad `qq`, plazos `hl` (4/8/12/18/36) |
| `bloqueos/` | `bloqueo.aamm.txt` (36) | 21 725 | `##d##h##m-##d##h##m:x1,y1,...` | Polilínea de calles bloqueadas en una ventana |
| `mantenimiento/` | `mant.preventivo.aa.m1-m2.txt` (18) | 666 | `aaaammdd:TTNN` | Mantenimiento preventivo de **vehículos** (bici 8h, moto 24h, auto 48h) |

El cargador (`CargaDatosService` → `Lector*Txt`) los parsea al arrancar y los guarda en repositorios
en memoria. Si una carpeta no existe, registra WARN y arranca sin esa fuente.

---

## 4. Modelo de dominio (`model/`)

POJOs sin JPA (port fiel de `PaqRoute-6F`):

- **`Punto`** — nodo `(x,y)` de la cuadrícula; distancia Manhattan; `idNodo(anchoX)`.
- **`Arista`** — tramo de calle no dirigido entre dos puntos (forma canónica `de(p1,p2)`).
- **`Almacen`** — `CENTRAL(27,14,∞)`, `NOROESTE(12,38,1000)`, `ESTE(57,27,1000)`; stock con
  `retirar()`, `tieneStock()`, `recargar()` diaria.
- **`Vehiculo`** — unidad concreta `TTNN` (TA01..TA10, TM01..TM15, TB01..TB12); tipos
  `AUTO(24 paq, 40 km/h, S/8)`, `MOTO(8, 25, S/6)`, `BICICLETA(4, 12, S/3)`; estados
  `LIBRE / EN_RUTA / MANTENIMIENTO`; `disponibleDesde`, `posicionActual`.
- **`Pedido`** — `idCliente`, `ubicacion`, `cantidadTotal`/`cantidadPendiente`,
  `momentoLlegada`, `horasLimite`, `fechaLimite`; soporta entregas parciales; `copiarEscalada(lambda)`
  (cantidad = `ceil(qq*lambda)`); `completo()`, `incumplido()`, `consumoSlaPct()`.
- **`Entrega`** — una parada: entrega total o parcial de un pedido (1h de servicio por parada).
- **`Bloqueo`** — `inicio`, `fin`, `poligonal` de puntos; `aristasBloqueadas()`; estático
  `aristasVigentesEn(bloqueos, momento)`.
- **`Mantenimiento`** — ventana de indisponibilidad vehicular por fecha+TTNN.
- **`Ruta`** — vehículo real + almacén de despacho + secuencia de entregas + almacén de retorno +
  distancias y horas (ida/retorno, fin de última entrega, hora de retorno).
- **`Solucion`** — conjunto de rutas de un tick; `costoTotal()`, `totalEntregas()`.
- **`ContextoPlanificacion`** — estado del mundo de un tick: almacenes, grafo y hora actual;
  `almacenMasCercanoConStock(punto, cantidad)`.

---

## 5. Motor (`engine/`) — el corazón del sistema

### 5.1 `GrafoVial`

Cuadrícula 70 km × 50 km, nodos cada 1 km, sin diagonales, doble sentido. Calcula la distancia más
corta entre dos nodos con **BFS** (todos los tramos miden 1 km), **excluyendo los tramos bloqueados
vigentes** del instante consultado. Cachea los BFS por origen; `actualizarBloqueosVigentes(Set<Arista>)`
limpia el cache (se llama en cada tick).

### 5.2 `RutaUtil` y `AsignadorFlota`

- `RutaUtil.evaluar(secuencia, tipo, origen, horaInicio, grafo)` → valida **capacidad** y que **cada
  entrega llegue antes de su `fechaLimite`**; devuelve distancia de ida y hora de fin. Es la regla
  compartida por ACS y GRASP-VNS. 1 hora de servicio por parada.
- `AsignadorFlota.tipoRecomendado(pedido, origen, ctx)` → entre los tipos que cumplen la velocidad
  requerida por el plazo restante, elige el de **menor costo total estimado** (viajes ida/vuelta ×
  distancia × costo/km), no el más barato por km.

### 5.3 `Simulador` (Algoritmo 1 y 2 + motor)

Avanza el reloj en pasos fijos (`tick`, default 30 min = **Sc**), va ingresando los pedidos del
histórico a medida que "llegan", y en cada tick:

1. Recarga diaria de almacenes intermedios.
2. Ingresa pedidos con `momentoLlegada <= horaActual`.
3. Determina vehículos libres (no en mantenimiento y disponibles).
4. Si hay pendientes y flota libre → construye `ContextoPlanificacion` con los bloqueos vigentes e
   invoca al **algoritmo** para armar la `Solucion` del tick (mide **Ta** con `System.nanoTime`).
5. Aplica la solución al estado real (descuenta stock, marca vehículos EN_RUTA, con `disponibleDesde`
   = hora de retorno).
6. **Criterio de colapso**: si algún pedido pendiente supera su `fechaLimite`, se detiene ahí.
7. Si se agota el histórico sin colapso, calcula la métrica **SLA promedio** (consumo de plazo).

`ParametrosAlgoritmo.DEFAULT = (5 hormigas, 5 iterACs, alfa=1.0, beta=2.0, rho=0.1, q0=0.9,
alfaGrasp=0.5, maxIterGrasp=10)`, más 3 variantes opcionales de ACS (memoria, VND, tipo ampliado).

### 5.4 `AntColonySystemVRP` (ACS por tick)

Feromona `n+1 × n+1` (nodo 0 = inicio de ruta virtual). Por hormiga: abre rutas (elige tipo vía
`AsignadorFlota`, almacén más cercano con stock, vehículo libre concreto) y extiende parada a parada
con regla pseudoaleatoria `τ^α·η^β` (`η = urgencia/distancia`), actualización local y global
(elitista). Variantes opt-in: `MemoriaFeromonas` (retención entre ticks), `MejoraLocalAcs` (VND),
`tipoVehiculoAmpliado`.

### 5.5 `GraspVnsVRP` (GRASP-VNS por tick)

Construcción golosa aleatorizada (RCL con `alfaGRASP`) + VND con 4 vecindades: **N1 relocate,
N2 swap, N3 2-opt intra-ruta, N4 cambio de tipo de vehículo** (con flota fija real). Lleva control
incremental de stock por almacén.

### 5.6 `ResultadoSimulacion`

Resumen de la corrida: `colapso`, `momentoColapso`, `clientePedidoColapsado`, pedidos ingresados /
completados a tiempo, entregas totales/parciales, costo acumulado, km, ticks, `Ta` promedio/máximo,
SLA promedio (si no colapsó).

---

## 6. Cargas, repositorios y servicios

### 6.1 `loader/` (parsers de los `.txt` reales)

- `LectorPedidosTxt` — `ventas.aaaamm.txt` → `List<Pedido>` ordenado cronológicamente (regex del
  formato real).
- `LectorBloqueosTxt` — `bloqueo.aamm.txt` → `List<Bloqueo>` (año `2000+aa`).
- `LectorMantenimientoTxt` — `mant.preventivo.aa.m1-m2.txt` → `List<Mantenimiento>`.

### 6.2 `repository/` (en memoria)

- `PedidoRepository` — pedidos base + `copiaFresca()` (Pedido es `mutable`: cada corrida trabaja
  sobre una copia nueva con `copiaEscalada(1.0)`).
- `BloqueoRepository` — bloqueos + `vigentesEn(momento)`.
- `MantenimientoRepository` — registros de mantenimiento.

### 6.3 `service/`

- **`CargaDatosService`** — orquesta la lectura de las 3 fuentes al arranque (se llena en
  `SeedDataConfig`, un `CommandLineRunner`).
- **`SimulacionService`** — prepara los parámetros (algoritmo, tick, semilla, `desde`, `lambda`,
  ACS mejorado) y ejecuta `new Simulador(...).ejecutar()`. Si `desde` viene, filtra los pedidos cuyo
  `momentoLlegada >= desde` y usa esa fecha como inicio.
- **`ConsultaService`** — lecturas simples: resumen de totales y bloqueos vigentes.
- **`NotificacionService`** — logs de eventos internos.

---

## 7. API REST

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/health` | Health check → `{"status":"UP",...}` |
| GET | `/api/datos/resumen` | `{totalPedidosVentas, totalVehiculos, totalAlmacenes, totalBloqueos, totalMantenimientos}` |
| GET | `/api/datos/bloqueos/vigentes?momento=` | Cantidad de bloqueos vigentes (momento ISO opcional) |
| GET | `/api/simulacion/algoritmos` | `["ACS","GRASP_VNS"]` |
| POST | `/api/simulacion/colapso` | Ejecuta la simulación de colapso |

### 7.0 Componentes (Diseño de Componentes v1.0 → backend)

Los 6 componentes del diseño se reflejan así en el backend:

1. **Registro de Pedidos** — `POST/GET /api/pedidos`, `GET /api/pedidos/clientes` + carga en lote `.txt`.
2. **Planificador de Rutas** — motor ACS/GRASP embebido + `POST /api/rutas/planificar` (one-shot).
3. **Gestión de Incidencias** — `POST/GET /api/incidencias`, `GET /api/incidencias/historial`;
   las incidencias registradas se mezclan en las corridas y disparan replanificación.
4. **Visualizador de Operaciones** (backend alimenta datos) — `GET /api/operacion/estado` + ticks por STOMP.
5. **Desempeño de las Operaciones** — `GET /api/desempeno/kpis|comparativa|reportes` (CSV/JSON).
6. **Configuración y Ejecución de Escenarios** — `POST /api/escenarios/ejecutar` (3 tipos) +
   `/streaming` (STOMP) + `GET /api/escenarios/historial`.

### POST /api/simulacion/colapso

```json
{
  "algoritmo": "ACS",
  "tickMinutos": 30,
  "semilla": 1,
  "desde": "2027-01-01T00:00:00",
  "lambda": 1.0,
  "acsMejorado": false
}
```

Respuesta `200` (ejemplo real ACS desde 2027-01-01):

```json
{
  "colapso": true,
  "momentoColapso": "2027-04-03T10:30:00",
  "clientePedidoColapsado": "c6612 (0,1) (pendiente 6/6, limite 2027-04-03T10:27)",
  "pedidosIngresados": 15849,
  "pedidosCompletadosATiempo": 15771,
  "entregasTotales": 22318,
  "entregasParciales": 12071,
  "costoAcumuladoSoles": 3094262.0,
  "kilometrosRecorridos": 526640.0,
  "ticksSimulados": 4437,
  "consumoSlaPromedioPct": null,
  "invocacionesAlgoritmo": 2687,
  "tiempoAlgoritmoPromedioMs": 27.18,
  "tiempoAlgoritmoMaximoMs": 174
}
```

**Nota de rendimiento**: la simulación completa (36 meses) toma del orden de 60–90 s por algoritmo;
por eso el smoke test usa `desde` para acotar el período.

### Formato de error (`ApiErrorResponse`)

```json
{
  "fechaHora": "2026-10-06T10:15:30",
  "estado": 400,
  "error": "Bad Request",
  "mensaje": "Algoritmo inválido: X (esperado ACS o GRASP_VNS)",
  "ruta": "/api/simulacion/colapso"
}
```

| Situación | HTTP |
|---|---|
| Recurso no encontrado (`ResourceNotFoundException`) | 404 |
| Regla de negocio (`BusinessException`, algoritmo inválido) | 400 |
| Archivo `.txt` inválido (`ArchivoInvalidoException`) | 400 |
| Validación de DTO | 400 |
| JSON mal formado | 400 |
| Content-Type no soportado | 415 |
| Error inesperado | 500 |

El handler (`GlobalExceptionHandler`) usa `error = estado.getReasonPhrase()` y solo el **primer**
error de validación (estilo CODA).

---

## 8. Configuración

`application.properties` (todo por variables de entorno con defaults):

| Propiedad | Variable de entorno | Default |
|---|---|---|
| `server.port` | `PAQROUTE_PORT` | `8080` |
| `paqroute.data.directorio` | `PAQROUTE_DATA_DIR` | `../data` |
| `paqroute.data.carpeta-ventas` | `PAQROUTE_DATA_VENTAS` | `ventas` |
| `paqroute.data.carpeta-bloqueos` | `PAQROUTE_DATA_BLOQUEOS` | `bloqueos` |
| `paqroute.data.carpeta-mantenimiento` | `PAQROUTE_DATA_MANTENIMIENTO` | `mantenimiento` |
| `paqroute.frontend.url` | `PAQROUTE_FRONTEND_URL` | `http://localhost:5173` |
| `logging.file.name` | `PAQROUTE_LOG_FILE` | `logs/paqroute.log` |
| `logging.logback.rollingpolicy.max-file-size` | `PAQROUTE_LOG_MAX_SIZE` | `10MB` |
| `logging.logback.rollingpolicy.max-history` | `PAQROUTE_LOG_MAX_HISTORY` | `14` |
| niveles de log | `PAQROUTE_LOG_LEVEL_*` | `INFO`/`DEBUG` |

Primera línea: `spring.config.import=optional:file:.env[.properties]` (soporta `.env` local opcional).

---

## 9. Compilar, ejecutar y probar

```bash
cd backend
mvn clean package                     # compila y empaqueta (BUILD SUCCESS)
java -jar target/paqroute-backend-0.0.1-SNAPSHOT.jar   # arranca en :8080
```

Smoke test (PowerShell):

```powershell
curl.exe http://localhost:8080/api/health
curl.exe http://localhost:8080/api/datos/resumen
curl.exe -X POST http://localhost:8080/api/simulacion/colapso `
  -H "Content-Type: application/json" `
  -d '{"algoritmo":"ACS","tickMinutos":30,"semilla":1,"desde":"2027-01-01T00:00:00","lambda":1.0}'
```

> Nota: en PowerShell, el JSON largo debe enviarse con un archivo (`--data-binary @body.json`), no con
> `-d` inline, para evitar errores de parseo (400).

### Despliegue

```bash
docker compose up -d --build   # monta ../data (:ro) y crea volumen paqroute-logs
docker compose logs -f
sudo systemctl enable --now paqroute   # si se usa el unit de systemd
```

---

## 10. Lo que se ha hecho (historial de este trabajo)

1. **Primera versión (guía CODA)**: backend Spring Boot con dominio de la guía (almacenes/vehículos/
   pedidos/tramos/incidencias, `pesoKg`/nodos string, planificador simple). Se alineó el estilo con
   el backend real de CODA (exception handler, filter, `@EnableAsync`, `.env` import, `SeedDataConfig`,
   tests con AssertJ). Verificado con `mvn clean verify` + smoke test.
2. **Eliminación de tests** (decisión): se borró `src/test` y se limpió `pom.xml` (deps de test y
   JaCoCo). Queda solo el backend de clases.
3. **Port del dominio real de PaqRoute**: los `.txt` reales (`ventas/bloqueos/mantenimiento`) pasaron
   a ser la fuente de datos (default `../data`). Se portaron 11 clases de `model`, 9 de `engine`
   (Simulador + ACS + GRASP-VNS + GrafoVial, etc.), 3 `loader`, 3 repos y 4 servicios, y se eliminaron
   58 clases del dominio anterior.
4. **API final**: Health + `datos/*` (resumen, bloqueos vigentes) + `simulacion/*` (algoritmos,
   colapso).
5. **Despliegue coherente**: `Dockerfile` con usuario sin privilegios, `docker-compose.yml` con
   `../data` `:ro` y volumen `paqroute-logs`, rotación de logs en `application.properties`, y el
   documento `docs/deploy/preguntas-despliegue-ubuntu-24.md`.
6. **Verificación real**: `mvn clean package` → BUILD SUCCESS; `docker compose config` → OK; resumen
   real `160010/21725/666/37/3`; colapso **ACS** desde 2027-01-01 → **2027-04-03 10:30 (c6612)** y
   **GRASP-VNS** → **2027-02-09 08:30 (c0844)**, coherentes con el informe.

---

## 11. Restricciones y decisiones

- **Sin BD, sin login, sin servicios externos** (reglas del proyecto).
- El motor es **determinista** por semilla (`semilla * 1_000_003 + tick`).
- Los datos son **inputs del negocio** (`PaqRoute-6F/data`), no se modifican por el backend.
- **Turnos de operarios**: fuera de alcance (divergencia documentada; el informe lo excluye).
- **Ritmo de visualización (5 días en 30–60 min)**: lo controla el **frontend**; el backend publica
  los ticks por STOMP. En "pacinado" opcional, `VELOCIDAD` ajusta los ms entre ticks.
- **Despliegue base**: `systemd` ejecutando el JAR directamente; **Docker opcional/pendiente**.
- Cualquier cambio debe mantener la estructura de paquetes y verificar con `mvn clean package`.

Documentos relacionados: `docs/api/index.md`, `docs/api/simulacion.md`, `docs/api/datos.md`,
`docs/deploy/preguntas-despliegue-ubuntu-24.md`, `AGENTS.md`, `README.md`.