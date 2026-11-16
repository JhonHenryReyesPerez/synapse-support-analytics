# CampusDesk — Backend (API REST)

Backend en Java 21 y Spring Boot del sistema de análisis de incidentes tecnológicos y asistencia
mediante IA. Centraliza los incidentes, administra una base de conocimiento verificada, calcula
indicadores con SQL y expone un asistente RAG cuyas respuestas siempre citan artículos verificados.

> Este README cubre el backend. El frontend, Docker Compose, Qdrant y los workflows de n8n son
> responsabilidad de otros integrantes; aquí se documenta el contrato que deben cumplir.

## Integrantes del equipo

Sergio Aparicio · Jhon Reyes (responsable del backend) · Sebastian Ayala · Juan Diego Leon

## Contenido

- [Qué hace](#qué-hace)
- [Arquitectura](#arquitectura)
- [Requisitos](#requisitos)
- [Configuración](#configuración)
- [Ejecución](#ejecución)
- [Base de datos y migraciones](#base-de-datos-y-migraciones)
- [Datos de demostración](#datos-de-demostración)
- [Endpoints](#endpoints)
- [Pruebas](#pruebas)
- [Decisiones técnicas](#decisiones-técnicas)
- [Estado y pendientes](#estado-y-pendientes)
- [Documentación](#documentación)
- [Créditos](#créditos)

## Qué hace

| Requisito | Implementación |
|---|---|
| RF01 Gestión de incidentes | Tickets con categoría, prioridad, cinco estados, asignación, historial y comentarios públicos e internos |
| RF02 Base de conocimiento | Artículos con ciclo borrador → verificado → activo; solo los verificados y activos alimentan al asistente |
| RF03 Indexación | Spring Boot envía cada artículo a n8n y registra el estado de sincronización del índice vectorial |
| RF04 y RF05 Búsqueda y asistente RAG | La consulta se guarda, se envía a n8n y la respuesta se valida contra el contrato y contra PostgreSQL antes de persistirla |
| RF06 y RF07 Analítica | Siete indicadores con filtros de fecha, categoría y prioridad, calculados con SQL |
| RF08 Historial y evaluación | Historial de consultas por incidente y calificación de 1 a 5 |

Roles: `USER` registra y cierra sus incidentes; `TECHNICIAN` atiende los asignados y redacta
borradores de artículos; `ADMIN` asigna, administra usuarios y categorías, y verifica y publica artículos.

## Arquitectura

```text
Frontend (HTML, CSS, JS)
        │  HTTPS + JWT
        ▼
Spring Boot ── JPA / Flyway ──► PostgreSQL   (fuente de verdad y analítica SQL)
        │  X-Service-Token
        ▼
       n8n ──► Qdrant (índice vectorial)  ·  OpenRouter (embeddings y generación)
        │  X-Integration-Token
        └────► Spring Boot /api/integration/**   (validar artículos antes de usarlos)
```

- El frontend solo habla con Spring Boot; nunca con n8n, Qdrant ni OpenRouter.
- PostgreSQL decide si un artículo existe y puede usarse. Qdrant es un índice derivado.
- Ninguna transacción de base de datos queda abierta durante una llamada a n8n.

Paquete raíz `com.roles.usermanagement`:

```text
web/              Seguridad (JWT, CORS), controladores de autenticación y usuarios, errores
domain/           Servicios y DTO de usuarios y seguridad
persistance/      Entidades y repositorios de usuarios, roles y permisos
integration/n8n/  Cliente HTTP de n8n, contrato v1 y filtro del secreto de integración
modules/
  ticket/ assignment/ lifecycle/ statushistory/ comment/ technician/   Incidentes
  category/       Categorías
  knowledge/      Artículos y sincronización del índice
  assistance/     Consultas al asistente, respuestas, fuentes y calificaciones
  dashboard/      Analítica
```

Diagramas de arquitectura, secuencia y entidad-relación: [`docs/backend/DIAGRAMAS.md`](../docs/backend/DIAGRAMAS.md).

## Requisitos

- JDK 21.
- PostgreSQL 14 o superior (probado con 17).
- Docker, solo para ejecutar las pruebas contra PostgreSQL con `scripts/test-postgres.sh`.
- No hace falta instalar Gradle: se usa el wrapper (8.14.3).

## Configuración

`src/main/resources/application.properties` **no contiene secretos**. Si falta una variable
obligatoria, la aplicación no arranca. Para desarrollo local:

```bash
cd backend
cp .env.example .env      # editar los valores; .env está excluido de Git
```

| Variable | Descripción | Obligatoria / valor por defecto |
|---|---|---|
| `DB_URL` | URL JDBC de PostgreSQL | Obligatoria |
| `DB_USERNAME`, `DB_PASSWORD` | Credenciales de la base | Obligatorias |
| `JWT_SECRET` | Clave HMAC256, mínimo 32 caracteres (`openssl rand -hex 32`) | Obligatoria |
| `JWT_ISSUER` | Emisor del token | `campusdesk-api` |
| `JWT_EXPIRATION` | Vida del token (`30m`, `8h`) | `30m` |
| `LOGIN_MAX_ATTEMPTS` | Logins fallidos seguidos de un mismo identificador antes del bloqueo temporal (responde 429) | `5` |
| `LOGIN_ATTEMPT_WINDOW`, `LOGIN_LOCK_DURATION` | Ventana en la que se cuentan los fallos y duración del bloqueo | `15m`, `15m` |
| `SERVER_PORT` | Puerto HTTP | `8051` |
| `CORS_ALLOWED_ORIGINS` | Orígenes del frontend, separados por comas | Ninguno (en `dev`: `localhost:5500` y `5501`) |
| `APP_TIME_ZONE` | Zona de negocio para fechas y agrupaciones | `America/Bogota` |
| `BOOTSTRAP_ADMIN_ENABLED` | Crear el administrador inicial si no existe | `false` (`true` en `dev`) |
| `ADMIN_USERNAME`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Datos del administrador inicial | Obligatorios si el bootstrap está activo |
| `N8N_BASE_URL` | URL de n8n. Vacía: integración deshabilitada y el asistente responde 503 | Vacía |
| `N8N_SERVICE_TOKEN` | Secreto que Spring Boot envía a n8n (`X-Service-Token`), mínimo 32 caracteres | Obligatoria si hay `N8N_BASE_URL` |
| `INTEGRATION_API_TOKEN` | Secreto que n8n envía a `/api/integration/**` (`X-Integration-Token`), mínimo 32 caracteres | Vacía: esos endpoints quedan cerrados |
| `N8N_INDEX_PATH`, `N8N_ASSIST_PATH` | Rutas de los webhooks | `/webhook/campusdesk-index`, `/webhook/campusdesk-assist` |
| `N8N_CONNECT_TIMEOUT`, `N8N_READ_TIMEOUT` | Tiempos límite hacia n8n | `5s`, `60s` |
| `N8N_MAX_RESPONSE_BYTES` | Tamaño máximo de una respuesta de n8n | `1048576` |
| `ASSIST_MAX_RESULTS` | Artículos a recuperar (3 a 5) | `5` |
| `ASSIST_MIN_SCORE` | Umbral mínimo de similitud coseno (0 a 1) | `0.55` (calibrado con `openai/text-embedding-3-small`; ver `docs/backend/PRUEBAS.md` §9) |
| `ASSIST_PENDING_TIMEOUT` | Edad a partir de la cual una consulta sin resultado se cierra como error | `5m` |

Las credenciales de Qdrant y de OpenRouter no se configuran aquí: pertenecen a n8n.

## Ejecución

Crear la base de datos una vez:

```sql
CREATE DATABASE "campusDESK";
```

Arrancar con el perfil de desarrollo (lee `backend/.env`):

```bash
cd backend
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE = "dev"
.\gradlew.bat bootRun
```

Al arrancar, Flyway crea o actualiza el esquema, se crean los roles y permisos, y en el perfil `dev`
el administrador inicial. La API queda en `http://localhost:8051` y Swagger en
`http://localhost:8051/swagger-ui.html`.

Empaquetar:

```bash
./gradlew clean test bootJar
java -jar build/libs/usermanagement-0.0.1-SNAPSHOT.jar
```

Para autenticarse en Swagger: ejecutar `POST /api/auth/login`, copiar el campo `token` y pegarlo en **Authorize**.

## Base de datos y migraciones

El esquema lo gestionan las migraciones versionadas de `src/main/resources/db/migration`. Hibernate
solo lo valida (`ddl-auto=validate`).

| Versión | Contenido |
|---|---|
| `V1` | Línea base del esquema anterior a Flyway |
| `V2` | Versión de credenciales del usuario (invalidación de tokens) |
| `V3` | Categorías como entidad y prioridad canónica |
| `V4` | Fechas de resolución y cierre, nota de solución |
| `V5` | Nombre y área del usuario |
| `V6` | Artículos de conocimiento |
| `V7` | Consultas al asistente, respuestas, pasos y fuentes |
| `V8` | Calificaciones |
| `V9` | Retira del técnico el permiso de lectura de cuentas |

Una base creada antes de adoptar Flyway se toma como versión 1 (`baseline-on-migrate`) y recibe las
migraciones posteriores. **Esto solo se ha probado sobre bases vacías**: antes de aplicarlo a una
base con datos, hacer una copia de seguridad y probar sobre la copia.

## Datos de demostración

`src/main/resources/db/seed/demo_data.sql` carga 13 usuarios, 31 artículos y 180 incidentes con su
historial. Es determinista (cada carga produce los mismos registros) y **no se ejecuta al arrancar**:
se carga de forma explícita después de arrancar la aplicación una vez.

```bash
psql -v ON_ERROR_STOP=1 -U <usuario> -d <base> -f backend/src/main/resources/db/seed/demo_data.sql
# o, con PostgreSQL en Docker:
docker exec -i <contenedor> psql -v ON_ERROR_STOP=1 -U <usuario> -d <base> < backend/src/main/resources/db/seed/demo_data.sql
```

| Usuario | Rol |
|---|---|
| `laura.gomez`, `carlos.ruiz`, `maria.perez`, `andres.castro`, `diana.mora`, `felipe.rojas`, `sofia.vargas`, `jorge.nino` | USER |
| `tec.redes`, `tec.hardware`, `tec.software`, `tec.aplicaciones` | TECHNICIAN |
| `admin.demo` | ADMIN |

Todos usan la contraseña `Demo.2026`. **Son cuentas de demostración con una contraseña pública: no
cargar este script en un despliegue real.** El script se niega a ejecutarse dos veces sobre la misma base.

Los 28 artículos publicados quedan pendientes de indexar. Con n8n disponible, un administrador los
envía con `POST /api/knowledge/articles/index/reconcile`.

## Endpoints

La referencia completa es Swagger; la especificación está exportada en
[`docs/backend/openapi.json`](../docs/backend/openapi.json). Resumen:

| Área | Rutas | Acceso |
|---|---|---|
| Autenticación | `POST /api/auth/login`, `POST /api/auth/register`, `POST /api/auth/change-password`, `GET /api/auth/me` | Login y registro públicos |
| Usuarios | `/api/user/**` | Según el permiso de cada operación (`USER_READ`, `USER_CREATE`, `USER_UPDATE`, `USER_DELETE`, `ROLE_ASSIGN`, `PERMISSION_ASSIGN`) |
| Roles y permisos | `/api/roles`, `/api/permissions` | ADMIN |
| Categorías | `GET /api/categories`; `POST`, `PUT /{id}`, `PATCH /{id}/activate`, `PATCH /{id}/deactivate` | Lectura: todos. Cambios: ADMIN |
| Incidentes | `POST /api/tickets`, `GET /api/tickets`, `GET` y `PUT /api/tickets/{id}` | Crear y editar: USER. Ver: según alcance |
| Asignación | `PATCH /api/tickets/{id}/assign`, `PUT /api/tickets/{id}/reassign` | ADMIN |
| Estados | `POST /api/tickets/{id}/transitions` | Según la máquina de estados |
| Historial y comentarios | `GET /api/tickets/{id}/history`, `GET` y `POST /api/tickets/{id}/comments` | Quien puede ver el incidente |
| Técnicos | `GET /api/technicians`, `GET /api/technicians/available` | ADMIN |
| Conocimiento | `/api/knowledge/articles/**` | Lectura de publicados: todos. Redacción: TECHNICIAN y ADMIN. Verificar y publicar: ADMIN |
| Asistente | `POST` y `GET /api/tickets/{id}/assistance`, `GET /api/assistance/queries/{id}` | Quien puede ver el incidente |
| Calificación | `PUT /api/assistance/queries/{id}/rating` | Quien hizo la consulta |
| Analítica | `GET /api/analytics/summary` | ADMIN global; USER y TECHNICIAN, su alcance |
| Integración n8n | `POST /api/integration/knowledge/eligibility`, `GET /api/integration/knowledge/eligible` | Solo `X-Integration-Token` |

Códigos de respuesta: 201 creación, 200 consulta o actualización, 400 validación, 401 sin
autenticación válida, 403 sin permiso, 404 inexistente, 409 conflicto de estado, y 502, 503 o 504
cuando falla n8n (con `queryId` en el cuerpo).

## Pruebas

```bash
./gradlew test                    # H2 en memoria: 107 pruebas, 6 omitidas
bash scripts/test-postgres.sh     # PostgreSQL 17 en Docker con las migraciones reales: 107 pruebas
```

Resultados, correspondencia con las diez pruebas obligatorias y limitaciones:
[`docs/backend/PRUEBAS.md`](../docs/backend/PRUEBAS.md).

## Decisiones técnicas

| Decisión | Motivo |
|---|---|
| PostgreSQL, Gradle y la estructura de módulos existente | Aprovechar la base ya construida sin mantener dos persistencias |
| Flyway con `ddl-auto=validate` | Cambios de esquema reproducibles y revisables |
| `spring.jpa.open-in-view=false` | Cada transacción usa su propio contexto; con el valor por defecto se perdía una actualización concurrente durante la indexación |
| Token de 30 minutos invalidado por versión de credenciales | Un cambio o restablecimiento de contraseña deja sin efecto los tokens anteriores, sin lista negra |
| Cinco estados de incidente, sin reapertura ni cancelación | El enunciado exige al menos cuatro; se conserva la máquina de estados existente |
| Solo `ADMIN` verifica y publica artículos; cambiar el contenido revoca la verificación | Solo conocimiento revisado puede ser fuente |
| Consulta al asistente síncrona con persistencia previa | Sin infraestructura asíncrona adicional; la consulta siempre queda registrada y es consultable |
| Fuentes validadas contra PostgreSQL al guardar | Un vector que sobra en Qdrant no convierte un artículo inactivo en fuente |
| Una o dos fuentes válidas se aceptan como evidencia limitada | No se completa el mínimo con artículos por debajo del umbral |
| Contrato v1 con similitud coseno | Una sola métrica con un rango y un umbral bien definidos |
| Fechas locales en `America/Bogota` | Los registros existentes ya estaban en esa hora |
| Analítica por fecha de creación; resuelto incluye `CLOSED`; horas transcurridas | Definiciones reproducibles con SQL |
| Datos de demostración por script explícito | Ningún despliegue arranca con cuentas de demostración |

Varias de estas decisiones son propuestas técnicas, no exigencias del enunciado, y están pendientes
de ratificación por el equipo (`CLAUDE.md`, secciones 20 y 21.4).

## Estado y pendientes

Implementado y probado: seguridad, incidentes, categorías, base de conocimiento, sincronización del
índice, asistente, historial, calificaciones, analítica y datos iniciales.

Pendiente o sin comprobar:

- Revisión humana de la fidelidad de las respuestas generadas: la prueba con modelos reales cubrió pocos casos (`docs/backend/PRUEBAS.md` §8).
- Recalibrar el umbral con consultas reales de los usuarios: se calibró con 25 consultas redactadas para la prueba.
- Migraciones sobre una copia de la base de desarrollo existente.
- Capturas de Swagger con los endpoints nuevos.
- Revisión humana del código generado con asistencia de IA (`AI_USAGE.md`).

## Documentación

| Documento | Contenido |
|---|---|
| [`docs/backend/API_CONOCIMIENTO_ASISTENTE_ANALITICA.md`](../docs/backend/API_CONOCIMIENTO_ASISTENTE_ANALITICA.md) | Contrato para el frontend |
| [`docs/backend/CONTRATO_N8N.md`](../docs/backend/CONTRATO_N8N.md) | Contrato Spring Boot ↔ n8n |
| [`docs/backend/DIAGRAMAS.md`](../docs/backend/DIAGRAMAS.md) | Arquitectura, flujos y entidad-relación |
| [`docs/backend/ANALITICA_SQL.md`](../docs/backend/ANALITICA_SQL.md) | Definición y SQL de los indicadores |
| [`docs/backend/INFORME_ANALISIS.md`](../docs/backend/INFORME_ANALISIS.md) | Informe de análisis de datos |
| [`docs/backend/PRUEBAS.md`](../docs/backend/PRUEBAS.md) | Documento de pruebas |
| [`docs/backend/openapi.json`](../docs/backend/openapi.json) | Especificación OpenAPI exportada |
| [`AI_USAGE.md`](../AI_USAGE.md) | Registro de uso de IA |

## Créditos

El proyecto partió de la plantilla de código abierto **spring-security-user-role-management**, de la
que se reutilizan la configuración base de Spring Security con JWT, el modelo de rol único con
permisos individuales y el servicio de inicialización de catálogos. Sobre esa base el equipo
desarrolló CampusDesk.
