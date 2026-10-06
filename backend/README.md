# PaqRoute Backend

Backend del motor de simulación de colapso logístico de PaqRoute (PaqRap). Integra el dominio real de
`PaqRoute-6F` (simulador por ticks con ACS y GRASP-VNS sobre la cuadrícula vial 70×50) dentro de una
arquitectura en capas estilo CODA: **sin base de datos**, **sin login**, datos en memoria cargados
desde los archivos `.txt` reales.

## Stack

| Aspecto | Valor |
|---|---|
| Lenguaje | Java 17 |
| Framework | Spring Boot 4.0.6 (Spring Web MVC) |
| Build | Maven |
| Persistencia | Ninguna (repositorios en memoria + `loader/` de `.txt`) |
| Paquete base | `com.paqroute.backend` |

## Datos

Lee por defecto de `../data` (el `data/` de `PaqRoute-6F`), configurable con `PAQROUTE_DATA_DIR`:

- `ventas.aaaamm.txt` (36) → pedidos con posición, cantidad `qq` y plazos `hl`.
- `bloqueo.aamm.txt` (36) → bloqueos de calles por ventana de tiempo.
- `mant.preventivo.aa.m1-m2.txt` (18) → mantenimiento preventivo de vehículos (TTNN).

## Compilar y ejecutar

```bash
mvn clean package
java -jar target/paqroute-backend-0.0.1-SNAPSHOT.jar
```

Health check: `GET http://localhost:8080/api/health`.
Resumen de lo cargado: `GET http://localhost:8080/api/datos/resumen`.
Simulación de colapso: `POST /api/simulacion/colapso` (ver `docs/api/`).

## Estructura

```
src/main/java/com/paqroute/backend/
  PaqRouteApplication.java
  config/       CORS, headers de seguridad, scheduling, carga inicial
  controller/   Health, SimulacionController, ConsultaController
  dto/request/  SimulacionRequestDTO
  dto/response/ ResultadoSimulacionResponseDTO, ResumenDatosResponseDTO
  enums/        TipoVehiculo (AUTO/MOTO/BICICLETA)
  exception/    excepciones + GlobalExceptionHandler + ApiErrorResponse
  loader/       LectorPedidosTxt, LectorBloqueosTxt, LectorMantenimientoTxt
  mapper/       ResultadoSimulacionMapper
  model/        Punto, Arista, Almacen, Vehiculo, Pedido, Entrega, Bloqueo, Mantenimiento, Ruta, Solucion, ContextoPlanificacion
  engine/       GrafoVial, RutaUtil, AsignadorFlota, AntColonySystemVRP, GraspVnsVRP,
                MejoraLocalAcs, MemoriaFeromonas, Simulador, ResultadoSimulacion
  repository/   PedidoRepository, BloqueoRepository, MantenimientoRepository
  service/      CargaDatosService, SimulacionService, ConsultaService, NotificacionService
docs/api/       documentación de la API
```

## Despliegue

- `Dockerfile` multietapa (Maven + JRE 17).
- `docker-compose.yml` monta `../data` (los `.txt` reales) como volumen de solo lectura.
- `nginx.conf` como reverse proxy hacia `127.0.0.1:8080`.
- `paqroute.service` como unit de `systemd`.

```bash
docker compose up --build
```

## Documentación de la API

Ver `docs/api/index.md`.