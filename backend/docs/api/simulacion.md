# Escenarios y simulación

Configuración y ejecución de los tres tipos de escenario definidos en el diseño de componentes.

## Tipos

`GET /api/escenarios/tipos`

```json
["OPERACION_DIARIA", "CINCO_DIAS", "COLAPSO"]
```

| Tipo | Ventana | Notas |
|---|---|---|
| `OPERACION_DIARIA` | `desde` + 24 h (configurable en `duracionHoras`) | Operación de un día |
| `CINCO_DIAS` | `desde` + 120 h (configurable) | Simulación de 5 días (el ritmo visual de 30–60 min lo controla el frontend; el backend publica por tick) |
| `COLAPSO` | todo el horizonte disponible | Estrés hasta el colapso logístico |

## POST /api/escenarios/ejecutar

Ver detalle en [index.md](index.md). Parámetros:

- `tipo` (obligatorio), `algoritmo` (default `ACS`), `desde`, `duracionHoras`,
  `lambda` (0.25–128), `replicas` (1–20), `acsMejorado`.

## POST /api/escenarios/ejecutar/streaming

Publica un `TickSnapshotDTO` por tick en `/topic/simulacion/{id}` y el resultado final en
`/topic/simulacion/{id}/resultado`. Devuelve el `id` para suscribirse:

```json
{
  "id": "4301a1ba-de68-4d58-9478-fe65d0b67dc0",
  "estado": "EN_PROCESO",
  "topic": "/topic/simulacion/4301a1ba-de68-4d58-9478-fe65d0b67dc0"
}
```

Control en `/app/simulacion/{id}/control` (`PAUSAR`, `REANUDAR`, `VELOCIDAD`).

## POST /api/simulacion/colapso (alias)

Equivalente a ejecutar `tipo: COLAPSO` con una réplica; devuelve el `ResultadoSimulacionResponseDTO`
directamente. Conserva compatibilidad con versiones anteriores.

## GET /api/escenarios/historial

Lista las ejecuciones guardadas (id, tipo, algoritmo, semilla, lambda, resultado resumido).

## GET /api/escenarios/algoritmos

```json
["ACS", "GRASP_VNS"]
```