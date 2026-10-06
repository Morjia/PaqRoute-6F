# AGENTS.md — PaqRoute Backend

Reglas del proyecto para agentes y desarrolladores.

## Objetivo

Backend del motor de simulación de colapso logístico de PaqRoute (PaqRap). Porta el dominio real de
`PaqRoute-6F` (grid 70×50, ventas/bloqueos/mantenimiento) dentro de una arquitectura estilo CODA.
Java 17 + Spring Boot 4.0.6 + Maven. Paquete base `com.paqroute.backend`.

## Reglas obligatorias

- **Sin base de datos.** Los datos viven en memoria y se cargan desde archivos `.txt` reales
  (`ventas.aaaamm.txt`, `bloqueo.aamm.txt`, `mant.preventivo.aa.m1-m2.txt`). No agregar JPA/JDBC.
- **Sin login.** No agregar JWT, RBAC ni Spring Security. Conservar solo `CorsConfig` y
  `SecurityHeadersFilter`.
- **Sin servicios externos** (ni mapas, ni Cloudinary, ni SMTP).
- **El motor es el de PaqRoute-6F:** `engine/` contiene `GrafoVial`, `RutaUtil`, `AsignadorFlota`,
  `AntColonySystemVRP`, `GraspVnsVRP`, `MejoraLocalAcs`, `MemoriaFeromonas`, `Simulador` y
  `ResultadoSimulacion`. No modificar su lógica sin validar contra el informe.
- **No hardcodear rutas de datos.** Usar `paqroute.data.*` de `application.properties`
  (default `../data`, es decir `PaqRoute-6F/data`).
- **No incluir secretos** en el repositorio.
- **Nunca exponer entidades de dominio en la API.** Responder siempre con DTOs de `response`.
- **Mantener la estructura de paquetes:**
  `config → controller → dto(request,response) → enums → exception → loader → mapper → model → engine → repository → service`.
- Cada capa depende solo de la capa inferior:
  `Controller → Service → Repository/Loader → Model` y `Service/Controller → Mapper → DTO`.
- Javadoc obligatorio en toda clase con `Autor`, `Fecha de creación` y `Descripción`.
- Lombok permitido (`@Getter`, `@Setter`, `@NoArgsConstructor`, `@Builder`).

## Verificación

```bash
mvn clean package
```

Debe terminar en `BUILD SUCCESS`. Actualmente no hay tests (solo backend de clases).

## Endpoints

REST (prefijo `/api`):
- `GET /health`
- `GET /datos/resumen`, `GET /datos/bloqueos/vigentes?momento=`
- `GET /pedidos?limite=`, `POST /pedidos`, `GET /pedidos/clientes`
- `POST /rutas/planificar` (one-shot)
- `GET /operacion/estado?momento=`
- `POST /incidencias`, `GET /incidencias`, `GET /incidencias/historial`
- `POST /escenarios/ejecutar`, `POST /escenarios/ejecutar/streaming`, `GET /escenarios/historial|tipos|algoritmos`
- `POST /simulacion/colapso` (alias), `GET /simulacion/algoritmos`
- `GET /desempeno/kpis?algoritmo=`, `GET /desempeno/comparativa`, `GET /desempeno/reportes?formato=csv|json`

WebSocket/STOMP: endpoint `/ws`; suscripción `/topic/simulacion/{id}` (ticks), `/topic/simulacion/{id}/resultado`;
control por `/app/simulacion/{id}/control` (`PAUSAR`, `REANUDAR`, `VELOCIDAD`).

## Arquitectura de despliegue

- Base: Spring Boot administrado por `systemd` (`paqroute.service`) ejecutando el JAR directamente
  con usuario sin privilegios. Nginx sirve el frontend y hace proxy de `/api` y `/ws`.
- Docker (`Dockerfile`/`docker-compose.yml`) queda como **opcional / pendiente de validación**.

## Formatos de datos `.txt` (fuente real `../data`)

- `ventas/ventas.aaaamm.txt`: `##d##h##m:posX,posY,cIdCliente,qq,hl`
- `bloqueos/bloqueo.aamm.txt`: `##d##h##m-##d##h##m:x1,y1,x2,y2,...,xn,yn`
- `mantenimiento/mant.preventivo.aa.m1-m2.txt`: `aaaammdd:TTNN`

Flota fija: 10 autos (TA01-TA10), 15 motos (TM01-TM15), 12 bicicletas (TB01-TB12).
Almacenes: CENTRAL(27,14,∞), NOROESTE(12,38,1000), ESTE(57,27,1000).