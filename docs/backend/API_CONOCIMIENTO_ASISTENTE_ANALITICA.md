# API para el frontend: conocimiento, asistente, calificaciones y analítica

Endpoints añadidos sobre la base de CampusDesk. Todos requieren `Authorization: Bearer <token>`.
La especificación completa y navegable está en Swagger (`/swagger-ui.html`) y exportada en
[`openapi.json`](openapi.json). El frontend **nunca** llama a n8n, Qdrant ni al proveedor de modelos.

## Convenciones

| Aspecto | Regla |
|---|---|
| Fechas | ISO 8601 sin zona, en hora de `America/Bogota`: `2026-11-12T10:15:00` |
| Paginación | `page` desde 0; `size` 20 por defecto y máximo 100. Respuesta `{content, page, size, totalElements, totalPages}` |
| Errores | `{timestamp, status, error, message}`; la validación de campos añade `errors: {campo: mensaje}` |
| 401 | Sin token o token inválido, vencido o invalidado por un cambio de contraseña |
| 403 | Sin permiso sobre el recurso. Con `code: "PASSWORD_CHANGE_REQUIRED"`: debe cambiar la contraseña primero |
| 404 / 409 | El recurso no existe / el estado actual no admite la operación |

## 1. Base de conocimiento — `/api/knowledge/articles`

Ciclo de un artículo: **borrador** → **verificado** (ADMIN) → **activo** (ADMIN). Solo un artículo
verificado y activo (`eligible: true`) alimenta al asistente. Cambiar su contenido crea una versión
nueva, le quita la verificación y lo desactiva.

| Método y ruta | Quién | Qué hace |
|---|---|---|
| `GET /api/knowledge/articles?scope=&page=&size=` | Todos | `scope=PUBLISHED` (por defecto): publicados. `MINE`: los del usuario (TECHNICIAN, ADMIN). `ALL`: todos (ADMIN) |
| `GET /api/knowledge/articles/{id}` | Todos | Un publicado; o un borrador para su autor y para ADMIN |
| `POST /api/knowledge/articles` | TECHNICIAN, ADMIN | Crea un borrador. 201 |
| `PUT /api/knowledge/articles/{id}` | Autor técnico (solo borradores), ADMIN | Edita el contenido |
| `POST /api/knowledge/articles/{id}/verify` | ADMIN | Verifica la versión vigente |
| `POST /api/knowledge/articles/{id}/activate` | ADMIN | Publica (409 si no está verificado) y lo envía a indexar |
| `POST /api/knowledge/articles/{id}/deactivate` | ADMIN | Deja de ser fuente de inmediato; no se elimina |
| `POST /api/knowledge/articles/{id}/reindex` | ADMIN | Reintenta la sincronización con el índice |
| `POST /api/knowledge/articles/index/reconcile` | ADMIN | Sincroniza los pendientes o con error (hasta 50) |

Cuerpo de creación y edición (todos obligatorios):

```json
{ "title": "…", "problem": "…", "symptoms": "…", "cause": "…", "solution": "…" }
```

Respuesta (`ArticleResponse`): `id`, los cinco campos de contenido, `authorUsername`, `verified`,
`verifiedByUsername`, `verifiedAt`, `active`, `eligible`, `contentVersion`, `indexStatus`
(`NOT_INDEXED`, `PENDING`, `INDEXED`, `ERROR`), `indexedVersion`, `indexedAt`, `indexErrorCode`,
`createdAt`, `updatedAt`.

`indexStatus = ERROR` no revierte la publicación: indica que el índice vectorial no se pudo
actualizar y que un administrador debe reintentar.

## 2. Asistente — consultar sobre un incidente

### `POST /api/tickets/{ticketId}/assistance`

USER en sus incidentes, TECHNICIAN en los asignados, ADMIN en cualquiera. No se admite en incidentes cerrados.

Cuerpo opcional:

```json
{ "question": "¿Cómo recupero la conexión?", "requestId": "3f2b8c1e-6a4d-4b7e-9c0a-5d1e2f3a4b5c", "retryOfQueryId": 85 }
```

| Campo | Regla |
|---|---|
| `question` | Opcional, máximo 2000 caracteres. Si se omite, se consulta con la descripción del incidente. Si se envía vacío o solo con espacios: 400 |
| `requestId` | Opcional. UUID generado por el frontend. Repetir la llamada con el mismo valor devuelve la consulta ya creada (200) en lugar de crear otra: úsalo para reintentos de red |
| `retryOfQueryId` | Opcional. Consulta anterior del mismo incidente que se repite. Crea una consulta nueva y deja intacta la anterior |

La llamada es síncrona: puede tardar hasta unos dos minutos. Deshabilita el botón mientras espera.

| Estado HTTP | Significado | Qué mostrar |
|---|---|---|
| 201 con `status: "ANSWERED"` | Respuesta con fuentes | `answer`, `recommendedSteps` y `sources` con su `similarity` |
| 201 con `status: "NO_RESULTS"` | No hay artículos suficientemente relevantes | `message`. No hay solución que mostrar |
| 200 | `requestId` repetido | La consulta existente, en el estado en que esté |
| 400 | Pregunta vacía o demasiado larga, o `requestId` que no es UUID | Error de validación |
| 409 | Incidente cerrado, o ya hay una consulta propia en proceso | `message` |
| 502, 503, 504 | Fallo técnico. El cuerpo trae `code`, `queryId` y `requestId` | «No fue posible completar la consulta; intenta de nuevo» y ofrecer reintento con `retryOfQueryId` |

Respuesta (`AssistanceQueryResponse`):

```json
{
  "id": 87, "requestId": "3f2b8c1e-…", "ticketId": 140, "requesterUsername": "laura.gomez",
  "question": "¿Cómo recupero la conexión?",
  "status": "ANSWERED", "statusLabel": "Respondida",
  "message": "Respuesta basada en 3 artículos verificados de la base de conocimiento.",
  "errorCode": null,
  "answer": "Renueve la dirección IP del equipo…",
  "recommendedSteps": ["…", "…"],
  "sources": [
    { "articleId": 1, "title": "Equipo sin acceso a internet: dirección IP 169.254.x.x", "articleVersion": 1,
      "similarity": 0.93, "order": 1, "evidence": "Problema: …\n\nSíntomas: …\n\nCausa: …\n\nSolución: …", "current": true }
  ],
  "limitedEvidence": false,
  "retrieval": { "maxResults": 5, "minScore": 0.55, "similarityMetric": "cosine", "embeddingModel": "…" },
  "generationModel": "…", "workflowVersion": "…",
  "attempts": 1, "retryOfQueryId": null, "rating": null,
  "createdAt": "2026-11-12T10:15:00", "completedAt": "2026-11-12T10:15:21"
}
```

Notas para la interfaz:

- `similarity` es similitud coseno, **no** una probabilidad de acierto ni un porcentaje de certeza. No la presentes como «92 % de confianza».
- `limitedEvidence: true` significa que menos de tres artículos superaron el umbral. Muéstralo como aviso.
- `sources[].current: false` indica una fuente histórica: el artículo cambió o dejó de estar publicado después. `evidence` conserva el texto que se usó.
- `answer` y `evidence` son texto plano: escápalos al insertarlos en el HTML.
- `status` posibles: `PENDING` (Pendiente), `ANSWERED` (Respondida), `NO_RESULTS` (Sin resultados), `ERROR` (Error).

### `GET /api/tickets/{ticketId}/assistance?page=&size=`

Historial de consultas del incidente, de la más reciente a la más antigua. Mismo acceso que el
incidente; disponible también cuando está cerrado. Cada elemento es un `AssistanceQueryResponse` completo.

### `GET /api/assistance/queries/{queryId}`

Una consulta con su resultado. Sirve para recuperar el resultado tras un error de red.

## 3. Calificación — `PUT /api/assistance/queries/{queryId}/rating`

```json
{ "score": 4, "comment": "Resolvió el problema" }
```

| Regla | Detalle |
|---|---|
| Quién | Solo quien hizo la consulta. Otro usuario, el técnico o ADMIN reciben 403 |
| Cuándo | Solo consultas `ANSWERED` (409 en otro estado). Se admite con el incidente cerrado |
| `score` | Entero de 1 a 5. Decimales, valores fuera de rango o ausente: 400 |
| `comment` | Opcional, máximo 1000 caracteres |
| Repetir | Actualiza la calificación existente; no crea otra |

Respuesta: `{queryId, score, comment, ratedBy, createdAt, updatedAt}`. La calificación también
aparece en el campo `rating` de la consulta y del historial.

## 4. Analítica — `GET /api/analytics/summary`

Parámetros opcionales: `from` y `to` (`YYYY-MM-DD`, sobre la fecha de creación; `to` incluye el día
completo), `categoryId`, `priority` (`BAJA`, `MEDIA`, `ALTA`, `CRITICA`) y `granularity` (`DAY` o `WEEK`).

Alcance según el rol: ADMIN ve todos los incidentes, USER los suyos y TECHNICIAN los asignados
(`cohort.scope`: `GLOBAL`, `OWN` o `ASSIGNED`).

| Campo | Indicador |
|---|---|
| `totalIncidents` | 1. Total de incidentes |
| `byCategory[]` `{categoryId, category, count, percentage}` | 2. Incidentes por categoría |
| `trend` `{granularity, points[{periodStart, count}]}` | 3. Tendencia por día o semana (lunes), con períodos vacíos en cero |
| `resolution` `{resolvedCount, ratePercentage}` | 4. Tasa de resolución (RESOLVED o CLOSED) |
| `resolutionTime` `{unit, averageHours, sampleSize, excludedCount, byCategory[]}` | 5. Tiempo promedio de resolución en horas |
| `mostRecurrentCategories[]` | 6. Categoría más recurrente (varias si hay empate) |
| `byPriority[]` `{priority, count, percentage}` | 7. Incidentes por prioridad (siempre las cuatro) |

Los valores llegan sin redondear: redondea al presentar. `averageHours` es `null` cuando no hay
resoluciones en la cohorte; no lo muestres como cero. Definiciones y SQL equivalente en
[`ANALITICA_SQL.md`](ANALITICA_SQL.md).

## 5. Cambios de contrato anteriores que afectan al frontend

- `GET /api/tickets` devuelve una página, no una lista.
- En tickets, `category` es el nombre y `categoryId` el identificador (`GET /api/categories`).
- Resolver exige `resolutionNote`; reasignar por `/transitions` exige `justification`.
- `POST /api/auth/change-password` devuelve un `token` nuevo; el anterior deja de servir.
