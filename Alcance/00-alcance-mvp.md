# Alcance del MVP — Frontend PaqRoute

> Documento de entrada rápida. Para detalle, ver `proposal.md` §1.

## Qué hace

El frontend demuestra el ciclo end-to-end de la simulación contra el backend:

```
POST /api/runs → STOMP subscribe → 12 planes con scenarioNormal
→ render de UTs en mapa Leaflet → finished(completed) en el plan 12
```

Una sola página (`/`), un solo tab (`normal`). Sin autenticación, sin playback, sin multi-user.

## Stack

Vite + React 18 + TypeScript + MUI + Zustand + Leaflet + MSW v2 + @stomp/stompjs.
Backend mockeado con MSW hasta que el equipo de Java entregue.

## Lo que SÍ va

- 1 tab: escenario `normal`
- Mapa de retícula abstracta 70×50 km, `L.CRS.Simple`
- 3 warehouses visibles: C en (27,14), NO en (12,38), E en (57,27)
- UTs TA01..TA10 / TB01..TB15 / TM01..TM12 con su posición del último plan
- Header con `runId` + `seq` + connection state
- Sidebar con `simTimeStart/End` + contador de envelopes
- Zoom (rueda) y pan (drag) funcionales
- `Cmd+R` crea un run nuevo
- Mock engine que emite 12 planes deterministas a 100ms; plan 12 lleva `terminal.completed`

## Lo que NO va (anti-scope-creep)

- Tabs `bloqueo` / `preventivo`
- Panel de averías en UI (las averías las acepta el backend pero no las disparamos desde UI)
- Panel `DATOS PEDIDOS` por turno
- Reporte final con las 3 cifras (pedidos / tiempo sim / tiempo real)
- Multi-usuario (controller/observer con claim rotation)
- Login / auth / permisos
- Controles de reproducción (pause/play/velocidad) — prohibidos por el profesor
- Animaciones, transiciones, temas oscuros, accesibilidad WCAG completa
- Persistencia entre runs
- Tests E2E automatizados
- Conexión al backend Java real (cambio aparte)
- Cancelaciones — NO existen en el modelo
- Gasolina — infinito en el modelo
- Mapas geográficos — solo retícula abstracta

## 12 criterios de aceptación (cuándo el demo está "done")

1. `npm run dev` arranca sin errores
2. MSW inicializa (mensaje "Mocking enabled" en consola dev)
3. Layout (HeaderBar + SimMap + StatusSidebar) renderiza sin warnings
4. `POST /api/runs` retorna 201 con `runId` + `controllerToken`
5. Conexión STOMP abre (`connectionState: "open"`)
6. Llegan ≥5 envelopes `kind:"plan"` antes del primer paint
7. Mapa muestra ≥1 UT en coordenada del contrato (no `(0,0)` salvo dato real)
8. Warehouses C/NO/E visibles en sus coordenadas exactas
9. Header muestra `runId` (UUID) + `seq` + `connectionState: "open"`
10. Sidebar muestra `simTimeStart/End` + counter de envelopes ≥5
11. Zoom (rueda) y pan (drag) funcionan sin errores en consola
12. `Cmd+R` crea un run nuevo (no reutiliza viejos)

## Dónde leer más

- **Por qué este MVP, no otro**: `proposal.md` §1 "Por qué este MVP, no otro"
- **Cómo se descompone el trabajo**: `tasks.md` (40 tasks, 8 PRs)
- **Cómo se construye**: `design.md` (9 decisiones)
- **Qué debe cumplir cada componente**: `specs/` (6 archivos)