# Diagramas: arquitectura y modelo entidad-relación

Los diagramas están en Mermaid: GitHub los dibuja al abrir este archivo. Describen el backend tal
como está implementado (migraciones `V1` a `V8`). El frontend, Docker Compose, Qdrant y los
workflows de n8n pertenecen a otros integrantes y aparecen aquí solo como componentes externos.

## 1. Arquitectura

```mermaid
flowchart TB
    FE["Frontend<br/>HTML · CSS · JavaScript"]

    subgraph SB["Backend Spring Boot (Java 21)"]
        direction TB
        SEC["Seguridad<br/>JwtFilter · IntegrationTokenFilter"]
        API["Controladores REST<br/>tickets · conocimiento · asistente · analítica · usuarios"]
        SVC["Servicios<br/>reglas de negocio y transacciones"]
        N8NC["Cliente n8n<br/>URL fija · secreto · tiempos límite"]
        SEC --> API --> SVC --> N8NC
    end

    PG[("PostgreSQL<br/>fuente de verdad · analítica SQL")]

    subgraph IA["Orquestación de IA"]
        direction TB
        N8N["n8n<br/>flujo de indexación · flujo de asistencia"]
        QD[("Qdrant<br/>índice vectorial derivado")]
        OR["OpenRouter<br/>embeddings y generación"]
        N8N --> QD
        N8N --> OR
    end

    FE -- "HTTPS + JWT" --> SEC
    SVC -- "JPA · Flyway" --> PG
    N8NC -- "X-Service-Token" --> N8N
    N8N -- "X-Integration-Token<br/>/api/integration/**" --> SEC
```

Límites que el diseño mantiene:

- El frontend solo habla con Spring Boot. Nunca con n8n, Qdrant ni OpenRouter.
- PostgreSQL decide si un artículo existe y puede usarse. Qdrant es un índice derivado: un vector que sobra no convierte un artículo inactivo en fuente.
- Spring Boot no tiene credenciales de Qdrant ni de OpenRouter; viven en n8n.
- La analítica se calcula con SQL sobre PostgreSQL. Ningún modelo interviene en esas cifras.

## 2. Flujo de una consulta al asistente

```mermaid
sequenceDiagram
    autonumber
    participant F as Frontend
    participant S as Spring Boot
    participant P as PostgreSQL
    participant N as n8n
    participant Q as Qdrant
    participant O as OpenRouter

    F->>S: POST /api/tickets/{id}/assistance
    S->>P: Transacción 1: autoriza el incidente, valida la pregunta, guarda la consulta PENDING
    Note over S,P: La transacción termina antes de llamar a n8n
    S->>N: AssistRequest (requestId, queryId, pregunta, incidente, maxResults, minScore)
    N->>O: Embedding de la consulta
    N->>Q: Búsqueda por similitud
    N->>S: POST /api/integration/knowledge/eligibility
    S-->>N: Artículos utilizables con su contenido vigente
    alt Hay evidencia
        N->>O: Generación con ese contexto
        N-->>S: ANSWERED + fuentes y similitudes
    else Sin evidencia
        N-->>S: NO_RESULTS
    end
    S->>P: Transacción 2: valida contrato y fuentes, guarda respuesta y estado terminal
    alt Una fuente cambió o se desactivó
        S->>N: Mismo requestId con attempt = 2 (único reintento)
    end
    S-->>F: 201 ANSWERED o NO_RESULTS · 502/503/504 con queryId si hubo fallo
```

## 3. Flujo de indexación de un artículo

```mermaid
sequenceDiagram
    autonumber
    participant A as ADMIN
    participant S as Spring Boot
    participant P as PostgreSQL
    participant N as n8n
    participant Q as Qdrant

    A->>S: POST /api/knowledge/articles/{id}/activate
    S->>P: active = true, indexStatus = PENDING
    S->>N: IndexRequest UPSERT (artículo, versión, contenido, hash)
    N->>Q: Reemplaza los puntos del artículo (identidad por artículo y fragmento)
    N-->>S: INDEXED (modelo, dimensión)
    S->>P: indexStatus = INDEXED solo si el artículo no cambió entre tanto
    Note over S,P: Si n8n falla: indexStatus = ERROR; el artículo sigue publicado y se reintenta
```

Desactivar o cambiar el contenido envía `DELETE` para retirar los vectores. La exclusión del
artículo no depende de esa limpieza: la validación relacional lo descarta desde el primer momento.

## 4. Estados

```mermaid
stateDiagram-v2
    direction LR
    state "Incidente" as T {
        [*] --> OPEN
        OPEN --> ASSIGNED: ASSIGN (ADMIN)
        ASSIGNED --> ASSIGNED: REASSIGN (ADMIN, con justificación)
        ASSIGNED --> IN_PROGRESS: START_PROGRESS (técnico asignado)
        IN_PROGRESS --> RESOLVED: RESOLVE (técnico, con nota)
        RESOLVED --> CLOSED: CONFIRM_CLOSE (solicitante)
        CLOSED --> [*]
    }
```

```mermaid
stateDiagram-v2
    direction LR
    state "Consulta al asistente" as Q {
        [*] --> PENDING
        PENDING --> ANSWERED: respuesta con fuentes válidas
        PENDING --> NO_RESULTS: sin evidencia relevante
        PENDING --> ERROR: fallo técnico o tiempo límite
    }
    state "Artículo" as K {
        [*] --> Borrador
        Borrador --> Verificado: verify (ADMIN)
        Verificado --> Publicado: activate (ADMIN)
        Publicado --> Verificado: deactivate (ADMIN)
        Publicado --> Borrador: cambio de contenido (versión + 1)
        Verificado --> Borrador: cambio de contenido (versión + 1)
    }
```

## 5. Modelo entidad-relación

Equivalencia con el enunciado: Usuario = `user`, Categoria = `categories`, Incidente = `tickets`,
ArticuloConocimiento = `knowledge_articles`, ConsultaIA = `assistance_queries`, RespuestaIA =
`assistance_responses`, FuenteRespuesta = `assistance_response_sources`, Calificacion = `assistance_ratings`.

```mermaid
erDiagram
    USER ||--|| USER_ROLE : "tiene un rol"
    APP_ROLE ||--o{ USER_ROLE : "asignado en"
    APP_ROLE ||--o{ ROLE_PERMISSION : "concede"
    APP_PERMISSION ||--o{ ROLE_PERMISSION : "incluido en"
    USER ||--o{ USER_PERMISSION : "permisos individuales"
    APP_PERMISSION ||--o{ USER_PERMISSION : "otorgado en"
    USER ||--o| TECHNICIAN_PROFILES : "perfil técnico"

    USER ||--o{ TICKETS : "solicita"
    USER |o--o{ TICKETS : "atiende"
    CATEGORIES ||--o{ TICKETS : "clasifica"
    TICKETS ||--o{ TICKET_STATUS_HISTORY : "historial"
    TICKETS ||--o{ TICKET_ASSIGNMENT_LOGS : "asignaciones"
    TICKETS ||--o{ TICKET_COMMENTS : "comentarios"

    USER ||--o{ KNOWLEDGE_ARTICLES : "redacta"
    USER |o--o{ KNOWLEDGE_ARTICLES : "verifica"

    TICKETS ||--o{ ASSISTANCE_QUERIES : "consultas"
    USER ||--o{ ASSISTANCE_QUERIES : "pregunta"
    ASSISTANCE_QUERIES |o--o{ ASSISTANCE_QUERIES : "reintento de"
    ASSISTANCE_QUERIES ||--o| ASSISTANCE_RESPONSES : "respuesta"
    ASSISTANCE_RESPONSES ||--o{ ASSISTANCE_RESPONSE_STEPS : "pasos"
    ASSISTANCE_RESPONSES ||--|{ ASSISTANCE_RESPONSE_SOURCES : "fuentes"
    KNOWLEDGE_ARTICLES ||--o{ ASSISTANCE_RESPONSE_SOURCES : "citado en"
    ASSISTANCE_RESPONSES ||--o{ ASSISTANCE_RATINGS : "calificaciones"
    USER ||--o{ ASSISTANCE_RATINGS : "califica"

    USER {
        varchar username PK
        varchar email UK
        varchar full_name
        varchar area
        varchar password "hash BCrypt"
        boolean locked
        boolean disabled
        boolean must_change_password
        int credential_version
    }
    CATEGORIES {
        bigint id PK
        varchar name UK
        varchar description
        boolean active
    }
    TICKETS {
        bigint id PK
        varchar title
        text description
        bigint category_id FK
        varchar priority "BAJA MEDIA ALTA CRITICA"
        varchar status "OPEN ASSIGNED IN_PROGRESS RESOLVED CLOSED"
        varchar applicant_username FK
        varchar technician_username FK "nulo en OPEN"
        timestamp created_at
        timestamp updated_at
        timestamp resolved_at
        timestamp closed_at
        text resolution_note
    }
    TICKET_STATUS_HISTORY {
        bigint id PK
        bigint ticket_id FK
        varchar previous_status
        varchar new_status
        varchar changed_by_username FK
        varchar action_type
        timestamp changed_at
    }
    TICKET_ASSIGNMENT_LOGS {
        bigint id PK
        bigint ticket_id FK
        varchar previous_technician_username FK
        varchar new_technician_username FK
        varchar assigned_by_username FK
        varchar assignment_type "INITIAL REASSIGNMENT"
        varchar justification
        timestamp assigned_at
    }
    TICKET_COMMENTS {
        bigint id PK
        bigint ticket_id FK
        varchar author_username FK
        text content
        varchar visibility "PUBLIC INTERNAL"
        timestamp created_at
    }
    KNOWLEDGE_ARTICLES {
        bigint id PK
        varchar title
        text problem
        text symptoms
        text cause
        text solution
        varchar author_username FK
        boolean verified
        varchar verified_by_username FK
        boolean active
        int content_version
        varchar content_hash "SHA-256"
        varchar index_status "NOT_INDEXED PENDING INDEXED ERROR"
        int indexed_version
        varchar embedding_model
        int embedding_dimension
    }
    ASSISTANCE_QUERIES {
        bigint id PK
        bigint ticket_id FK
        varchar requester_username FK
        text question
        varchar status "PENDING ANSWERED NO_RESULTS ERROR"
        varchar request_id UK "UUID idempotente"
        varchar error_code
        int max_results
        float min_score
        int attempts
        bigint retry_of_id FK
        varchar embedding_model
        timestamp created_at
        timestamp completed_at
    }
    ASSISTANCE_RESPONSES {
        bigint id PK
        bigint query_id FK "único"
        text answer
        varchar generation_model
        timestamp created_at
    }
    ASSISTANCE_RESPONSE_STEPS {
        bigint response_id PK
        int step_order PK
        text step
    }
    ASSISTANCE_RESPONSE_SOURCES {
        bigint id PK
        bigint response_id FK
        bigint article_id FK
        int article_version
        float score "similitud coseno"
        int source_order
        varchar article_title
        text evidence
        varchar content_hash
    }
    ASSISTANCE_RATINGS {
        bigint id PK
        bigint response_id FK
        varchar username FK
        int score "1 a 5"
        varchar comment_text
        timestamp created_at
        timestamp updated_at
    }
```

Restricciones de integridad destacadas (definidas en las migraciones, no solo en el código):

| Regla | Dónde |
|---|---|
| Un artículo solo puede estar activo si está verificado | `ck_article_active_requires_verified` (`V6`) |
| Una respuesta por consulta | `uk_assist_response_query` (`V7`) |
| Una sola consulta en proceso por usuario e incidente | Índice único parcial `uk_assist_query_one_pending` (`V7`) |
| Una consulta pendiente no tiene fecha de cierre; una terminal sí | `ck_assist_query_completed` (`V7`) |
| Código de error solo en `ERROR` | `ck_assist_query_error` (`V7`) |
| Similitud entre −1 y 1 | `ck_assist_source_score` (`V7`) |
| Puntuación entre 1 y 5; una calificación por usuario y respuesta | `ck_assist_rating_score`, `uk_assist_rating_response_user` (`V8`) |
| Prioridad canónica | `tickets_priority_check` (`V3`) |
| Sin `ON DELETE CASCADE` | Usuarios, incidentes y artículos no se eliminan: se deshabilitan o desactivan |

Tablas que existen y no se muestran por brevedad: `ticket_lifecycle_rules` (reglas de la máquina de
estados), `user_role_history` y `dashboard_metrics_cache` (heredada; la analítica no la usa).
