# Synapse Support Analytics (CampusDesk)

Sistema inteligente de gestión, análisis de incidentes tecnológicos y asistencia técnica fundamentada mediante **Retrieval-Augmented Generation (RAG)**.

El backend está construido con **Spring Boot 3.x (Java 21)**, base de datos relacional **PostgreSQL 16**, base de datos vectorial **Qdrant**, orquestación de flujos de automatización con **n8n**, y modelos de lenguaje/embeddings vía **OpenRouter**.

---

## 1. Arquitectura del Sistema

El sistema implementa una arquitectura desacoplada y segura donde **Spring Boot** actúa como la única fuente de verdad y punto de entrada para todas las operaciones de negocio y consultas inteligentes.

```mermaid
flowchart TD
    subgraph Clientes ["Capa de Presentación"]
        CLI["Cliente / Frontend"]
    end

    subgraph BackendApp ["Spring Boot API (Puerto 8080)"]
        AUTH["Seguridad JWT & RBAC"]
        TICKETS["Módulo Incidentes"]
        KNOWLEDGE["Módulo Conocimiento"]
        ASSIST["Módulo Asistencia RAG"]
        ANALYTICS["Módulo Analítica SQL"]
        INTEG["Controlador Integración"]
    end

    subgraph RelationalDB ["Persistencia Relacional (Puerto 5432)"]
        PG[("PostgreSQL 16\nFuente de Verdad")]
    end

    subgraph Automation ["Orquestación de Flujos (Puerto 5679)"]
        N8N_F1["Flujo 1: Indexación\n(Webhook: campusdesk-index)"]
        N8N_F2["Flujo 2: Asistencia RAG\n(Webhook: campusdesk-assist)"]
    end

    subgraph VectorDB ["Base Vectorial (Puerto 6333)"]
        QDRANT[("Qdrant\nColección: articulos_soporte\n(Dimensión 1536)")]
    end

    subgraph ExternalAI ["Modelos de Inteligencia Artificial"]
        OR_EMB["OpenRouter Embeddings\n(text-embedding-3-small)"]
        OR_LLM["OpenRouter LLM\n(google/gemini-2.5-flash)"]
    end

    %% Relaciones de clientes
    CLI -->|HTTP / JWT Bearer| AUTH

    %% Relaciones internas backend
    AUTH --> TICKETS
    AUTH --> KNOWLEDGE
    AUTH --> ASSIST
    AUTH --> ANALYTICS

    %% Relaciones con PostgreSQL
    TICKETS -->|JPA / Flyway| PG
    KNOWLEDGE -->|JPA / Flyway| PG
    ASSIST -->|JPA / Flyway| PG
    ANALYTICS -->|SQL Nativo / Vistas| PG

    %% Sincronización e Indexación (Flujo 1)
    KNOWLEDGE -->|POST X-Service-Token| N8N_F1
    N8N_F1 -->|Generar Vector| OR_EMB
    N8N_F1 -->|Upsert / Delete Point| QDRANT

    %% Asistencia RAG (Flujo 2)
    ASSIST -->|POST X-Service-Token| N8N_F2
    N8N_F2 -->|Vectorizar Consulta| OR_EMB
    N8N_F2 -->|Búsqueda Coseno >= 0.60| QDRANT
    N8N_F2 -->|Validar Elegibilidad Relacional\nPOST /api/integration/knowledge/eligibility| INTEG
    INTEG -->|Verificar active & verified| PG
    N8N_F2 -->|Generación con Contexto| OR_LLM
    N8N_F2 -->|Respuesta Estructurada JSON| ASSIST
```

### Principios y Límites Arquitectónicos
1. **Entrada Única:** Todas las solicitudes pasan primero por Spring Boot. El cliente/frontend **nunca** se comunica directamente con n8n, Qdrant ni OpenRouter.
2. **PostgreSQL es la Fuente de Verdad:** Qdrant es un índice derivado. Un artículo solo se considera elegible para el RAG si en PostgreSQL se encuentra en estado `active = true` y `verified = true`.
3. **Validación en Dos Vías (Two-Way Verification):** Durante la asistencia RAG, n8n recupera los artículos candidatos de Qdrant y consulta a Spring Boot (`POST /api/integration/knowledge/eligibility`) antes de llamar al LLM. Si las fuentes fueron revocadas o desactivadas, el sistema responde `NO_RESULTS` en lugar de generar una respuesta alucinada.
4. **Seguridad de Integración:** La comunicación entre Spring Boot y n8n está protegida mediante cabeceras dedicadas:
   - `X-Service-Token`: Spring Boot $\rightarrow$ n8n.
   - `X-Integration-Token`: n8n $\rightarrow$ Spring Boot.

---

## 2. Flujos de Automatización n8n

Los flujos han sido diseñados utilizando nodos estándar de n8n para máxima portabilidad (sin dependencias de nodos propietarios inestables).

### Flujo 1: Indexación de Base de Conocimiento (`n8n/Flujo-1-Indexacion-Conocimiento.json`)
- **Trigger:** Webhook `POST /webhook/campusdesk-index`
- **Autenticación:** Valida la cabecera `X-Service-Token`.
- **Comportamiento:**
  - Si `operation == "UPSERT"`: Genera el embedding de 1536 dimensiones del texto (título + contenido) mediante OpenRouter (`text-embedding-3-small`), y realiza un `PUT` en Qdrant a `/collections/articulos_soporte/points` guardando metadatos (`articleId`, `title`, `category`, `version`, `estado`, `contentHash`).
  - Si `operation == "DELETE"`: Elimina el punto en Qdrant mediante `POST /collections/articulos_soporte/points/delete`.
- **Respuesta:** JSON `{ "status": "INDEXED" | "DELETED", "articleId": <id>, "vectorId": <id> }`.

### Flujo 2: Asistencia Inteligente RAG (`n8n/Flujo-2-Asistencia-RAG.json`)
- **Trigger:** Webhook `POST /webhook/campusdesk-assist`
- **Autenticación:** Valida la cabecera `X-Service-Token`.
- **Comportamiento:**
  1. Extrae la consulta (`queryText`), categoría y título del incidente.
  2. Genera el embedding de la consulta mediante OpenRouter (`text-embedding-3-small`).
  3. Ejecuta búsqueda semántica en Qdrant con filtro `{ "key": "estado", "match": { "value": "activo" } }`, similitud coseno $\ge 0.60$ y tope de 3 documentos.
  4. Si no hay coincidencias vectoriales $\rightarrow$ responde inmediatamente `status: "NO_RESULTS"`.
  5. Si hay coincidencias $\rightarrow$ invoca a Spring Boot `POST /api/integration/knowledge/eligibility` pasando los IDs y versiones de las fuentes para confirmar que siguen activas en PostgreSQL.
  6. Si ninguna fuente es elegible $\rightarrow$ responde `status: "NO_RESULTS"`.
  7. Si hay fuentes elegibles $\rightarrow$ ensambla el prompt de sistema con el contexto verificado y solicita al LLM (`google/gemini-2.5-flash` vía OpenRouter) una respuesta estructurada en formato JSON estricto (`summary`, `steps`, `recommendations`, `sourcesUsed`).
- **Respuesta:** JSON estructurado consumible directamente por el servicio de asistencia del backend.

---

## 3. Despliegue con Docker Compose

El proyecto incluye una orquestación completa en `docker-compose.yml` que levanta los 5 servicios necesarios en una red compartida `incidentes_net`:

| Servicio | Imagen / Build | Puerto Host | Descripción |
|---|---|---|---|
| `db` | `postgres:16-alpine` | `5432` | Base de datos relacional PostgreSQL con migraciones Flyway automáticas. |
| `qdrant` | `qdrant/qdrant:latest` | `6333` | Base de datos vectorial para recuperación semántica. |
| `qdrant-init` | `curlimages/curl:latest` | — | Inicializa automáticamente la colección `articulos_soporte` (1536 dim, Coseno) e índices de payload. |
| `n8n` | `n8nio/n8n:latest` | `5679` | Motor de flujos de automatización e integración con OpenRouter. |
| `backend` | `./backend/Dockerfile` | `8080` | API REST Spring Boot (Java 21 Temurin). |

### Paso 1: Configurar Variables de Entorno
En la raíz del proyecto, copia el archivo de plantilla `.env.example` a `.env`:

```bash
cp .env.example .env
```

Edita el archivo `.env` y coloca tu clave de OpenRouter:
```properties
# Reemplaza con tu API Key real de OpenRouter
OPENROUTER_API_KEY=sk-or-v1-tu-clave-aqui
```

> [!NOTE]
> Las contraseñas de PostgreSQL, los tokens de integración (`N8N_SERVICE_TOKEN`, `INTEGRATION_API_TOKEN`) y el secreto JWT ya vienen preconfigurados con valores seguros para el entorno de desarrollo local.

### Paso 2: Iniciar los Servicios
Ejecuta el siguiente comando en la raíz del proyecto:

```bash
docker compose up -d
```

Verifica que todos los contenedores estén levantados y saludables:
```bash
docker compose ps
```

Deberías ver:
- `synapse-db`: Up (healthy)
- `synapse-qdrant`: Up (healthy)
- `synapse-qdrant-init`: Exited (0) (tarea de inicialización completada exitosamente)
- `synapse-n8n`: Up (healthy)
- `synapse-backend`: Up (healthy)

---

## 4. Configuración e Importación de Flujos en n8n

Una vez que el contenedor de n8n esté arriba:

1. Abre tu navegador e ingresa a: **`http://localhost:5679`**
2. Si es la primera vez, completa el formulario de registro de usuario inicial de n8n.
3. **Configurar Credencial de OpenRouter:**
   - Ve a **Credentials** $\rightarrow$ **Add Credential**.
   - Selecciona **Header Auth**.
   - Asigna el nombre: `OpenRouter Header Auth`.
   - Name: `Authorization`
   - Value: `Bearer TU_OPENROUTER_API_KEY` (o `Bearer sk-or-v1-...`).
   - Guarda la credencial.
4. **Importar Flujo 1 (Indexación):**
   - Ve a **Workflows** $\rightarrow$ Menú de opciones (tres puntos) $\rightarrow$ **Import from File**.
   - Selecciona el archivo: [`n8n/Flujo-1-Indexacion-Conocimiento.json`](file:///C:/Users/USER26/Desktop/Proyecto-AI#2/n8n/Flujo-1-Indexacion-Conocimiento.json).
   - En el nodo `OpenRouter Embeddings`, asegúrate de vincular tu credencial `OpenRouter Header Auth`.
   - Activa el flujo con el interruptor **Active** (arriba a la derecha) y guarda.
5. **Importar Flujo 2 (Asistencia RAG):**
   - Menú de opciones $\rightarrow$ **Import from File**.
   - Selecciona el archivo: [`n8n/Flujo-2-Asistencia-RAG.json`](file:///C:/Users/USER26/Desktop/Proyecto-AI#2/n8n/Flujo-2-Asistencia-RAG.json).
   - En los nodos `OpenRouter Embeddings` y `OpenRouter Gemini Chat`, vincula tu credencial `OpenRouter Header Auth`.
   - Activa el flujo con el interruptor **Active** y guarda.

---

## 5. Carga de Datos Iniciales (Seed Data)

El proyecto incluye un script SQL de datos iniciales en [`database/seed_data.sql`](file:///C:/Users/USER26/Desktop/Proyecto-AI#2/database/seed_data.sql) que contiene:
- **10 Usuarios del Sistema:**
  - `admin` (Administrador general)
  - `jhon_tech`, `yeison_tech`, `carlos_tech`, `maria_tech`, `david_tech` (Técnicos)
  - `ana_user`, `pedro_user`, `laura_user`, `sergio_user` (Usuarios solicitantes)
  - *Todas las contraseñas iniciales son:* `Password123!`
- **25 Artículos de Base de Conocimiento:**
  - Cubren Hardware, Software, Conectividad, Cuentas, Servidores, etc.
  - Tienen hashes SHA-256 reales, versiones editoriales y estado `verified=true`, `active=true`.
- **150 Incidentes Históricos:**
  - Con ciclo de vida completo (`NEW`, `ASSIGNED`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`), fechas históricas coherentes, resoluciones técnicas y categorías normalizadas.

### Aplicar el Script en PostgreSQL
Ejecuta el siguiente comando para cargar los datos en el contenedor de base de datos:

```bash
docker compose exec -T db psql -U postgres -d incidentes_db -f /seed_data.sql
```
*(El archivo `database/seed_data.sql` se monta automáticamente dentro del contenedor `db` en `/seed_data.sql`).*

Alternativamente, desde tu máquina local usando `psql`:
```bash
psql -h localhost -p 5432 -U postgres -d incidentes_db -f database/seed_data.sql
```

### Sincronización Inicial de la Base Vectorial
Para indexar los 25 artículos iniciales en Qdrant a través de n8n, ejecuta el siguiente comando contra el endpoint administrativo de Spring Boot:

```bash
# 1. Obtener Token de Administrador
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Password123!"}' | grep -o '"token":"[^"]*' | cut -d'"' -f4)

# 2. Disparar sincronización masiva de artículos verificados
curl -s -X POST http://localhost:8080/api/knowledge/articles/reindex \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

---

## 6. Referencia de Endpoints de la API REST

Swagger UI disponible en: **`http://localhost:8080/swagger-ui/index.html`**

### Autenticación y Cuentas
- `POST /api/auth/login`: Autenticación de usuario. Devuelve token JWT (duración 30 min).
- `POST /api/auth/change-password`: Cambio de contraseña obligatorio o voluntario.
- `GET /api/user/profile`: Datos del usuario autenticado actual.
- `GET /api/user/users`: Lista de usuarios (exclusivo `ADMIN`).

### Incidentes (Tickets)
- `POST /api/tickets`: Creación de incidentes (exclusivo rol `USER`). Requiere `title`, `description`, `category` o `categoryId`, y `priority` (`BAJA`, `MEDIA`, `ALTA`, `CRITICA`).
- `GET /api/tickets?page=0&size=20`: Listado paginado con control de acceso por recurso (usuarios ven sus tickets, técnicos ven sus asignados, administradores ven todos).
- `GET /api/tickets/{id}`: Detalle del ticket y su historial de estados.
- `POST /api/tickets/{id}/assign`: Asignación de técnico (exclusivo `ADMIN`).
- `PATCH /api/tickets/{id}/status`: Transición de estado (`ASSIGNED`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`). `RESOLVED` exige obligatoriamente `resolutionNote`.

### Base de Conocimiento
- `POST /api/knowledge/articles`: Creación de borrador de artículo.
- `PUT /api/knowledge/articles/{id}`: Edición de artículo (incrementa versión y revoca verificación).
- `PATCH /api/knowledge/articles/{id}/verify`: Verificación técnica del artículo (exclusivo `ADMIN`).
- `PATCH /api/knowledge/articles/{id}/activate`: Activación y disparo automático de indexación hacia n8n.
- `PATCH /api/knowledge/articles/{id}/deactivate`: Desactivación y disparo de eliminación vectorial en Qdrant.
- `GET /api/knowledge/articles`: Listado de artículos con filtros por estado editorial e índice.

### Asistencia RAG y Calificaciones
- `POST /api/tickets/{ticketId}/assistance`: Solicitar orientación técnica asistida por IA para un ticket específico.
  - **Autorización:** Solo el solicitante del ticket, el técnico asignado o un administrador pueden consultarlo.
  - **Regla:** El ticket no debe estar en estado `CLOSED`.
  - **Respuesta:** Devuelve la consulta persistida con la respuesta generada, fuentes fundamentadas y tiempo de respuesta.
- `GET /api/assistance/queries/{queryId}`: Consultar el estado y detalle de una consulta previa.
- `POST /api/assistance/responses/{responseId}/rate`: Calificar la utilidad de la respuesta de IA (escala de 1 a 5 estrellas, con comentario opcional). Exclusivo para el usuario que solicitó la consulta.
- `GET /api/assistance/responses/{responseId}/rating`: Obtener la calificación actual de una respuesta.

### Analítica de Incidentes
- `GET /api/analytics/summary`: Indicadores consolidados calculados directamente con SQL verificable:
  - Tiempo promedio de resolución (horas).
  - Tasa de resolución (%).
  - Tasa de escalamiento (%).
  - Total de incidentes, resueltos y pendientes.
  - Distribución por categoría y severidad.
  - Tendencia semanal.
  - Filtros opcionales por rango de fechas (`startDate`, `endDate`), categoría y severidad.

---

## 7. Ejecución de Pruebas Unitarias e Integrales

El backend cuenta con una suite completa de pruebas automatizadas que validan todas las reglas de negocio, seguridad, concurrencia, integración con n8n y ciclo RAG:

```bash
cd backend
./gradlew test
```

### Resultados de la Suite de Pruebas:
- **Total de pruebas:** 82
- **Fallos:** 0
- **Errores:** 0
- **Tasa de éxito:** 100%

### Cobertura de las Pruebas:
- `JwtPropertiesBindingTests`, `PasswordPolicyTests`, `PasswordChangeFlowTests`, `PasswordChangeRequiredTests`: Seguridad, tokens e invalidación por versión de credenciales.
- `TicketAssignmentTests`: Asignación única por servicio, control de concurrencia y roles válidos.
- `TicketResolutionTests`, `TicketPaginationTests`: Reglas de transición de estados, notas obligatorias y paginación `PageResponse`.
- `CategoryTests`, `UserAccountPolicyTests`: Catálogo de categorías, restricciones de roles y baja lógica de usuarios.
- `KnowledgeArticleTests`, `KnowledgeIndexTests`: Ciclo de vida editorial de artículos, sincronización fuera de transacción con n8n, manejo de fallos y aislamiento de sesiones Hibernate.
- `AssistanceTests`: Ciclo completo de consultas RAG (`PENDING` $\rightarrow$ `ANSWERED` / `NO_RESULTS`), validación relacional de fuentes y calificaciones 1-5 estrellas por el solicitante.
- `AnalyticsTests`: Verificación de cálculo de los siete indicadores analíticos con datos de cohorte en PostgreSQL / H2.

---

## 8. Consideraciones de Seguridad y Buenas Prácticas

- **Cero Secretos en el Código:** Todos los secretos (JWT, credenciales de base de datos, API Keys de OpenRouter, tokens de integración) se inyectan estrictamente por variables de entorno.
- **Tokens Invalidados Inmediatamente:** El sistema utiliza un claim de versión de credenciales (`cv`). Cuando un usuario cambia o restablece su contraseña, todos los JWT emitidos previamente quedan invalidados de inmediato sin necesidad de consultar una lista negra en memoria o Redis.
- **RAG Fundamentado y Anti-Alucinación:** El flujo de n8n opera con instrucciones estrictas de abstención: si la similitud semántica es inferior a 0.60 o las fuentes son revocadas por Spring Boot, el modelo no genera conjeturas y responde `NO_RESULTS`.
- **Prevención de Ataques en Base de Datos:** Todas las consultas analíticas y de negocio utilizan JPA estructurado o sentencias preparadas nativas con parámetros tipados para evitar inyección SQL.

---
*Desarrollado para el proyecto académico Synapse Support Analytics (CampusDesk) — Noviembre 2026.*
