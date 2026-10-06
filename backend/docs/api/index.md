# API PaqRoute Backend

Base URL (desarrollo): `http://localhost:8080/api`

El backend consume los `.txt` reales de `PaqRoute-6F/data` (`ventas`, `bloqueos`, `mantenimiento`)
al arrancar, los mantiene en memoria y expone el motor de simulación de escenarios (operación diaria,
5 días y estrés hasta el colapso) con ACS/GRASP-VNS, incidencias dinámicas, KPIs/reportes y el estado
de la operación. Comunicación: **REST + WebSocket (STOMP)** en `/ws`. Sin BD, sin autenticación.

## Formato de error

```json
{
  "fechaHora": "2026-10-06T10:15:30",
  "estado": 400,
  "error": "Bad Request",
  "mensaje": "Algoritmo inválido: X (esperado ACS o GRASP_VNS)",
  "ruta": "/api/escenarios/ejecutar"
}
```

| Situación | HTTP |
|---|---|
| Recurso no encontrado | 404 |
| Regla de negocio violada | 400 |
| Validación de DTO / JSON mal formado | 400 |
| Content-Type no soportado | 415 |
| Error inesperado | 500 |

## Matriz de endpoints

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/health` | Health check |
| GET | `/api/datos/resumen` | Totales de lo cargado |
| GET | `/api/datos/bloqueos/vigentes?momento=` | N° de bloqueos vigentes |
| GET | `/api/pedidos?limite=` | Lista pedidos (límite, default 100) |
| POST | `/api/pedidos` | Registra un pedido individual |
| GET | `/api/pedidos/clientes?limite=` | Clientes distintos y nº de pedidos |
| POST | `/api/rutas/planificar` | Planificación one-shot de rutas |
| GET | `/api/operacion/estado?momento=` | Snapshot de la operación (visualizador) |
| POST | `/api/incidencias` | Registra incidencia (bloqueo/falla) |
| GET | `/api/incidencias` | Lista incidencias |
| GET | `/api/incidencias/historial` | Historial de replanificación |
| POST | `/api/escenarios/ejecutar` | Ejecuta escenario (síncrono, réplicas) |
| POST | `/api/escenarios/ejecutar/streaming` | Ejecuta escenario y publica ticks por STOMP |
| GET | `/api/escenarios/historial` | Historial de simulaciones |
| GET | `/api/escenarios/tipos` | `OPERACION_DIARIA, CINCO_DIAS, COLAPSO` |
| GET | `/api/escenarios/algoritmos` | `ACS, GRASP_VNS` |
| POST | `/api/simulacion/colapso` | Alias del escenario COLAPSO |
| GET | `/api/simulacion/algoritmos` | Algoritmos disponibles |
| GET | `/api/desempeno/kpis?algoritmo=` | KPI de la última corrida del algoritmo |
| GET | `/api/desempeno/comparativa` | Comparativa ACS vs GRASP-VNS |
| GET | `/api/desempeno/reportes?formato=csv\|json` | Reporte exportable |

## POST /api/escenarios/ejecutar

Request:

```json
{
  "tipo": "CINCO_DIAS",
  "algoritmo": "ACS",
  "desde": "2026-01-01T00:00:00",
  "duracionHoras": 120,
  "lambda": 1.0,
  "replicas": 1,
  "acsMejorado": false
}
```

Respuesta `200`:

```json
{
  "idHistorial": 1,
  "tipo": "CINCO_DIAS",
  "algoritmo": "ACS",
  "replicas": 1,
  "estado": "EJECUTADO",
  "resultados": [
    {
      "colapso": false,
      "consumoSlaPromedioPct": 13.37,
      "costoAcumuladoSoles": 14768.0,
      "entregasTotales": 107,
      "entregasParciales": 60,
      "momentoColapso": null,
      "pedidosCompletadosATiempo": 76,
      "pedidosIngresados": 76,
      "ticksSimulados": 239
    }
  ]
}
```

## WebSocket / STOMP (tiempo real)

- Endpoint: `STOMP /ws` (soporta SockJS).
- Suscripción: `/topic/simulacion/{id}` recibe un `TickSnapshotDTO` por tick.
- Resultado final: `/topic/simulacion/{id}/resultado`.
- Errores: `/topic/simulacion/{id}/error`.
- Control (pausa/reanudar/velocidad): enviar por STOMP a `/app/simulacion/{id}/control`
  con `{ "comando": "PAUSAR" | "REANUDAR" | "VELOCIDAD", "valor": <ms> }`; ack en
  `/topic/simulacion/{id}/control`.

## Flujo principal

1. `SeedDataConfig` → `CargaDatosService` lee `ventas/bloqueos/mantenimiento`.
2. `Registro de Pedidos`: alta individual (`POST /api/pedidos`) o carga en lote por `.txt`.
3. `Incidencias`: `POST /api/incidencias` registra bloqueos/fallas; se mezclan en las corridas.
4. `Escenarios`: `POST /api/escenarios/ejecutar` ejecuta el simulador (una réplica = una semilla) y
   guarda en `HistorialSimulacion`; `.../streaming` publica cada tick por STOMP.
5. `Desempeño`: `GET /api/desempeno/*` produce KPIs y reportes CSV/JSON desde el historial.
6. `Operación`: `GET /api/operacion/estado` alimenta al visualizador.