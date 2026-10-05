# DISEÑO DE ARQUITECTURA — PaqRoute
**Versión 2.0** · Curso 1INF54 · Grupo 6F (horario 0981) · PUCP

> **Este archivo es la fuente de verdad arquitectónica del equipo.** Todo change SDD
> que toque stack, comunicación o despliegue DEBE citar este documento (no re-decidir).
> Diagramas C4 (contenedores/componentes) versionados en el documento original del curso;
> pendiente exportar las imágenes a este directorio.

| Ítem | Fecha | Versión | Descripción |
|---|---|---|---|
| 1 | 08/09/2026 | 1.0 | Versión Preliminar |
| 2 | 22/09/2026 | 2.0 | Mejora de diagramas de componentes y contenedores |

Integrantes: VALVERDE VARILLAS, JUAN DIEGO (20185976) · SANCHEZ ESCOBAR, DIEGO ALONSO (20202623) · ESPINOZA DAMIAN, FAVIO WALDO (20216549) · PORTALATINO MUNGUIA, JEYSON DAVID (20221973)

## 1. Introducción

PaqRoute es la solución del curso para apoyar la gestión de operaciones de delivery de PaqRap: planificación de rutas y visualización de la operación logística. Contempla la operación diaria y la ejecución de simulaciones sobre distintos escenarios. Este documento establece la organización general, componentes, relaciones y entorno de despliegue.

## 2. Consideraciones Arquitectónicas

### 2.1 Atributos de Calidad
- **Rendimiento**: la simulación de 5 días debe ejecutarse entre 30 y 60 minutos, representando gráficamente el periodo durante la ejecución; procesamiento eficiente considerando volumen de datos y operaciones del planificador.
- **Configurabilidad**: parámetros de escenarios y visualización modificables sin tocar código.
- **Confiabilidad**: el planificador prioriza el cumplimiento de plazos y permite planificar/replanificar ante bloqueos e incidencias (averías), de forma consistente en todos los escenarios.

### 2.2 Restricciones Técnicas y de Entorno
- Planificador en **Java** (exigencia del curso, NF-a del caso).
- Dos alternativas **metaheurísticas** evaluadas por experimentación numérica (NF-b).
- Debe ejecutarse en los equipos del laboratorio de Ingeniería Informática (NF-e): VM Ubuntu 24, 2 vCPU / 2 GB RAM (Corrado 01/09).

## 3. Modelo Arquitectónico (C4)

### 3.1 Contexto
PaqRoute es un único sistema. Los usuarios acceden para visualizar la operación y participar de una simulación. **Quien inicia la simulación asume el control** y puede modificar los parámetros permitidos durante la ejecución (los cambios se aplican a la simulación en curso); **los demás se conectan como observadores**, viendo estado y evolución en tiempo real sin poder intervenir.

### 3.2 Contenedores / 3.3 Componentes
Ver diagramas del documento original (imágenes por exportar a este directorio).

## 4. Arquitectura de Despliegue

Despliegue en la **VM del laboratorio**. Frontend React servido por **Nginx**, que actúa además de **proxy inverso** hacia el backend **Spring Boot** (app Java administrada por **systemd**) — el backend concentra lógica de negocio, simulación y planificador. Comunicación: **REST** para operaciones convencionales, **WebSocket** para tiempo real de la simulación.

## 5. Stack Tecnológico

### 5.1 Frontend *(foco actual del repo paqroute)*
| Tecnología | Uso |
|---|---|
| React | Aplicación web y componentes de interfaz |
| TypeScript | **Lenguaje del frontend** (addendum interno 24/09, decisión del coordinador). Los tipos del contrato se GENERAN desde `contract/plan-iteration.v1.schema.json` (json-schema-to-typescript) y los consumen MSW (fixtures validados al compilar), Zustand y los componentes |
| Vite | Desarrollo, ejecución local y build del frontend |
| MUI | Componentes de UI reutilizables (formularios, botones, tablas, diálogos) |
| Zustand | Estado centralizado de la app, incl. información recibida durante la simulación |
| Leaflet + L.CRS.Simple | Visualización/interacción con la ciudad simulada (calles, rutas, vehículos, almacenes) |
| MSW | Simulación de las comunicaciones con el backend durante desarrollo y pruebas del frontend |

### 5.2 Backend *(repo aparte, diferido)*
| Tecnología | Uso |
|---|---|
| Java | Backend, simulación y planificador |
| Spring Boot | Backend monolítico, expone las funcionalidades |

### 5.3 Comunicación
| Tecnología | Uso |
|---|---|
| REST | Operaciones convencionales (envío/consulta de información) |
| WebSocket + STOMP | Comunicación persistente y suscripciones de clientes durante la simulación |
| JSON | Formato de intercambio entre componentes |

### 5.4 Despliegue
| Tecnología | Uso |
|---|---|
| Nginx | Servidor web del frontend + proxy inverso al backend |
| systemd | Servicio del backend (inicio/ejecución controlada) |
| Docker | *Pendiente de validación* para la VM del laboratorio; no es requisito del despliegue base |

## 6. Referencias
- Martin, R. C. (2017). *Clean Architecture*. Prentice Hall.
- OMG (2017). *UML 2.5.1 Specification*.
- Toth & Vigo (2014). *Vehicle Routing: Problems, Methods, and Applications* (SIAM).

---
*Notas de gobernanza (equipo, no parte del doc del curso):*
- *Base-first: la parte de **comunicación + frontend** se construye primero; backend y BD después* (decisión del coordinador, 23/09).
- *Idioma del frontend: **TypeScript** (24/09, coordinador) — los tipos generados desde la schema del contrato son la puerta de conformidad en todo el frontend.*
- *La BD no aparece aún en el stack: definir en el doc del backend cuando toque (guía 26-1: "RDBMS, no-SQL, csv/txt" — decisión nuestra).*
