# Respuestas — despliegue del backend PaqRoute en VM Ubuntu 24.04

> Respuestas al documento `preguntas-despliegue-ubuntu-24.md`, alineadas con el backend real construido
> en `backend/` (Java 17 + Spring Boot 4.0.6 + Maven, sin BD, datos desde los `.txt` reales de
> `PaqRoute-6F/data`) y con todo el sistema PaqRoute.
> Donde no hay información confirmada de infraestructura, se **toma una decisión** y se indica si el
> código del backend ya fue adecuado a ella (etiqueta `[código ajustado]`).

---

## 1. Acceso a la máquina virtual

| Pregunta | Respuesta / Decisión |
|---|---|
| ¿IP de la VM? | **DECISIÓN**: por confirmar al aprovisionar. No hay referencia en el código. |
| ¿Usuario SSH? | **DECISIÓN**: usuario `paqroute` en la VM, con `sudo` (grupos `sudo` y `docker`). |
| ¿Autenticación? | **DECISIÓN**: clave SSH **ed25519** (pública entregada por infra). Sin contraseñas. |
| ¿Permisos sudo? | Sí, necesario para manejar `systemd`, `ufw` y `docker compose`. |
| ¿Acceso directo o bastión? | **DECISIÓN**: acceso directo por SSH; si hay VPN/jump host, definir por infra. |
| ¿Recursos VM? | **DECISIÓN**: mínimo **2 vCPU / 2 GB RAM / 10 GB disco**. La app es una JVM ligera; durante la simulación usa ~1 núcleo. No hay base de datos (no se reserva RAM para una BD). |

## 2. Red y firewall

| Pregunta | Respuesta / Decisión |
|---|---|
| Puertos abiertos (Security Group/firewall cloud) | **DECISIÓN**: abrir `22/tcp`, `80/tcp`, `443/tcp`. NO exponer `8080`. |
| ¿Configurar `ufw`? ¿Qué puertos? | Sí. `sudo ufw allow 22/tcp`, `sudo ufw allow 80/tcp`, `sudo ufw allow 443/tcp`, habilitar `ufw`. El 8080 queda solo para `127.0.0.1`. |
| ¿Puerto interno de la app? | **8080** por defecto, configurable con `PAQROUTE_PORT` (`application.properties`). |
| ¿Whitelist de IPs? | **DECISIÓN**: restringir SSH solo a IPs de administración del equipo. |

## 3. Aplicación: stack y versiones

| Pregunta | Respuesta |
|---|---|
| Lenguaje / framework | Java 17 + Spring Boot 4.0.6 (Spring Web MVC) + Maven. |
| Runtime exacto | JDK 17 (Temurin). Ubuntu 24.04 trae OpenJDK 21 por defecto: **no depende del JDK del host** porque la app corre en la imagen `eclipse-temurin:17-jre` (Dockerfile). |
| Librerías nativas / paquetes `apt` | Ninguna. Es Java puro; no se requieren paquetes adicionales. |
| Herramientas de compilación en servidor | No hacen falta con Docker (el build ocurre dentro de `maven:3.9-eclipse-temurin-17`). Para build sin contenedor: JDK 17 + Maven 3.9 (el repo incluye `./mvnw`). |

## 4. Código y build

| Pregunta | Respuesta / Decisión |
|---|---|
| ¿Dónde está el código? | `https://github.com/Morjia/PaqRoute-6F.git`, rama `main`; el backend vive en `backend/`. |
| Repositorio privado | **DECISIÓN**: usar deploy key de solo lectura si el repo se marca privado. |
| ¿Artefacto ya compilado? | Fat JAR `backend/target/paqroute-backend-0.0.1-SNAPSHOT.jar` o imagen Docker construida desde `backend/Dockerfile`. |
| Comandos de build | `./mvnw clean package` (o `mvn clean package`); `docker build -t paqroute-backend .` / `docker compose build`. |
| ¿Dockerfile / compose? | Sí, ambos en `backend/`. Despliegue con contenedores. |
| Comando de arranque | `docker compose up -d`; directo: `java -jar target/paqroute-backend-0.0.1-SNAPSHOT.jar`. |
| Health check | `GET /api/health` → `{"status":"UP","aplicacion":"paqroute-backend",...}`. |

## 5. Configuración y secretos

| Pregunta | Respuesta |
|---|---|
| Variables de entorno | `PAQROUTE_PORT`, `PAQROUTE_DATA_DIR`, `PAQROUTE_DATA_VENTAS`, `PAQROUTE_DATA_BLOQUEOS`, `PAQROUTE_DATA_MANTENIMIENTO`, `PAQROUTE_FRONTEND_URL`, `PAQROUTE_LOG_FILE`, `PAQROUTE_LOG_MAX_SIZE`, `PAQROUTE_LOG_MAX_HISTORY`, `PAQROUTE_LOG_FILE_PATTERN`, `PAQROUTE_LOG_LEVEL_ROOT/APP/WEB` (todas con default no productivo en `application.properties`). |
| Qué cambia dev → prod | `PAQROUTE_DATA_DIR` (apunta a `PaqRoute-6F/data`), `PAQROUTE_FRONTEND_URL` (dominio real), niveles de log. |
| Secretos | **No hay secretos**: sin BD, sin login, sin servicios externos. No aplica quién los entrega. |
| ¿Dónde guardarlos? | No aplica. Si a futuro se agrega una API key, usar `.env` con permisos `600` (`spring.config.import=optional:file:.env[.properties]` ya lo soporte). |
| Otros archivos de configuración | Los `.txt` reales (`ventas/`, `bloqueos/`, `mantenimiento/`) montados como volumen de solo lectura; logs en volumen con nombre. |

## 6. Base de datos y servicios externos

| Pregunta | Respuesta |
|---|---|
| Base de datos | **Ninguna** (in-memory). |
| ¿Misma VM o externa? | No aplica. |
| Usuario/contraseña/BD | No aplica. |
| Migraciones / seeds | No hay migraciones. Seed automático al arranque (`SeedDataConfig` → `CargaDatosService`) leyendo los `.txt` reales. |
| Redis / colas / Kafka | Ninguno. |
| Almacenamiento de archivos | Solo lectura de `PaqRoute-6F/data` (`:ro` en compose); los logs se persisten en volumen con nombre `paqroute-logs`. No hay uploads. |
| Correos / SMTP | No. |
| APIs de terceros | No (sin egress requerido). |

## 7. Acceso público: dominio y HTTPS

| Pregunta | Respuesta / Decisión (y ajuste de código) |
|---|---|
| Dominio | **DECISIÓN**: `paqroute.paqrap.com` (placeholder usado en `nginx.conf` y `docker-compose.yml`). |
| Quién administra el DNS | **DECISIÓN**: por confirmar con el equipo; se necesita un registro A `paqroute.paqrap.com` → IP de la VM. |
| Certificado HTTPS | **DECISIÓN**: Let's Encrypt con Certbot en el host, contra nginx (no hay cert en el repo). |
| Reverse proxy | Nginx (se incluye `backend/nginx.conf`). Proxy `/api/` hacia `127.0.0.1:8080`, **WebSocket/STOMP** en `/ws/` con headers de upgrade, y servicio del build de React. CORS ya manejado en la app (`CorsConfig`). |

## 8. Operación y mantenimiento

| Pregunta | Respuesta / Decisión (y ajuste de código) |
|---|---|
| ¿Cómo se mantiene activo? | Unit `systemd` `backend/paqroute.service` que ejecuta **el JAR directamente** (`/usr/bin/java -jar /opt/paqroute/paqroute-backend.jar`) como usuario sin privilegios. Docker queda como **opcional/pendiente de validación** (la arquitectura no lo requiere para el despliegue base). |
| ¿Inicia al reiniciar la VM? | Sí: `systemctl enable paqroute`. |
| Logs y rotación | Archivo `PAQROUTE_LOG_FILE` (default `logs/paqroute.log`); **rotación por tamaño y retención ajustada en código** `[código ajustado]`: `logging.logback.rollingpolicy.max-file-size=`10MB` y `max-history=14` (env `PAQROUTE_LOG_MAX_SIZE/HISTORY`); volumen con nombre `paqroute-logs` `[código ajustado]`. Ver también `docker compose logs`. |
| Monitoreo / alertas | **DECISIÓN**: usar `GET /api/health` como sonda del orchestrator/uptime; sin herramienta de alertas por ahora (pendiente de decisión del equipo). |
| Backups | No hay BD. Respaldar el repositorio (los datos `PaqRoute-6F/data` están versionados en git) y los logs. |
| Deploy de nuevas versiones | Manual: `git pull && docker compose up -d --build` en la VM. |
| Rollback | Volver a un commit/tag anterior: `git checkout <tag> && docker compose up -d --build`. |

## 9. Seguridad básica

| Pregunta | Respuesta / Decisión (y ajuste de código) |
|---|---|
| ¿Deshabilitar password y root SSH? | **DECISIÓN**: sí (claves ed25519, `PermitRootLogin no`, `PasswordAuthentication no`). |
| ¿fail2ban? | **DECISIÓN**: instalar y habilitar para SSH. |
| ¿unattended-upgrades? | **DECISIÓN**: activar actualizaciones automáticas de seguridad. |
| ¿App sin privilegios? | **Sí, ajustado en código** `[código ajustado]`: el `Dockerfile` crea `USER paqroute` (no root) y, para el **despliegue base**, `paqroute.service` arranca el JAR con `User=paqroute`/`Group=paqroute`. |

## 10. Coordinación

| Pregunta | Respuesta / Decisión |
|---|---|
| Contacto del equipo backend | **DECISIÓN**: por definir con el equipo. |
| Fecha/ventana de despliegue | **DECISIÓN**: por definir. |
| Criterios de aceptación | Propuestos: (1) `GET /api/health` → UP; (2) `GET /api/datos/resumen` muestra los totales reales (160010 pedidos / 21725 bloqueos / 666 mantenimientos / 37 vehículos / 3 almacenes); (3) `POST /api/simulacion/colapso` con ACS responde un colapso coherente con el informe (p. ej. desde 2027-01-01 → 2027-04-03 10:30, cliente c6612). |

---

## Resumen mínimo imprescindible (decisión tomada)

- **IP + usuario + clave SSH**: por asignar por infra; usuario `paqroute` con sudo, clave ed25519.
- **App y versión**: Java 17, Spring Boot 4.0.6, Maven; contenedor `eclipse-temurin:17-jre`.
- **Origen/artefacto**: repo `github.com/Morjia/PaqRoute-6F` (rama `main`, carpeta `backend/`); fat JAR o imagen Docker.
- **Arranque y puerto**: `docker compose up -d`; puerto interno **8080** (`PAQROUTE_PORT`).
- **Variables**: `PAQROUTE_DATA_DIR`, `PAQROUTE_FRONTEND_URL`, `PAQROUTE_PORT`, `PAQROUTE_LOG_*`.
- **Base de datos**: ninguna.
- **Dominio**: `paqroute.paqrap.com` (placeholder) tras nginx + Certbot.

## Ajustes de código aplicados (solo dentro de `backend/`)

1. `Dockerfile`: usuario sin privilegios (`USER paqroute`) y carpeta `logs` con propietario correcto (opcional).
2. `docker-compose.yml`: volumen con nombre `paqroute-logs` y `../data` montado `:ro` (opcional).
3. `application.properties`: rotación de logs por tamaño y retención (`PAQROUTE_LOG_MAX_SIZE`/`PAQROUTE_LOG_MAX_HISTORY`), y `paqroute.planificador.max-pedidos`.
4. `paqroute.service`: **despliegue base por systemd ejecutando el JAR directamente** con `User=paqroute`.
5. `nginx.conf`: proxy de `/api/` y de `/ws/` (WebSocket/STOMP con upgrade).

Verificación: `mvn clean package` → BUILD SUCCESS; `docker compose config` → OK.