# Contrato Spring Boot ↔ n8n (versión 1)

Este documento define lo que el backend envía a n8n, lo que espera recibir y lo que n8n puede
consultar al backend. Los workflows, Qdrant y el proveedor de modelos pertenecen a otro integrante;
aquí solo está el contrato que ambos lados deben cumplir. Fuente en el código:
`backend/src/main/java/com/roles/usermanagement/integration/n8n/dto`.

Estado: implementado en el backend y en los dos flujos de `n8n/`. Probado con un doble de n8n
(`FakeN8nClient`) y, el 13 de noviembre de 2026, de extremo a extremo con n8n 2.47.7 y Qdrant 1.20.1
reales y el proveedor de modelos simulado (`docs/backend/PRUEBAS.md` §8). **No se ha ejecutado con
modelos reales de OpenRouter.**

## 1. Reglas generales

| Aspecto | Regla |
|---|---|
| Dirección Spring Boot → n8n | `POST {N8N_BASE_URL}{ruta}` con `Content-Type: application/json` y cabecera `X-Service-Token: {N8N_SERVICE_TOKEN}` |
| Dirección n8n → Spring Boot | Cabecera `X-Integration-Token: {INTEGRATION_API_TOKEN}`; solo abre `/api/integration/**` |
| Secretos | Dos secretos distintos, mínimo 32 caracteres, por variable de entorno. Nunca dentro del JSON del workflow |
| Destino | La URL de n8n es fija por configuración; ningún cliente la elige |
| Rutas por defecto | Indexación `/webhook/campusdesk-index`; asistencia `/webhook/campusdesk-assist` (`N8N_INDEX_PATH`, `N8N_ASSIST_PATH`) |
| Tiempos | Conexión 5 s y respuesta 60 s por defecto (`N8N_CONNECT_TIMEOUT`, `N8N_READ_TIMEOUT`) |
| Tamaño | Respuesta máxima de 1 MiB (`N8N_MAX_RESPONSE_BYTES`) |
| Protocolo | HTTP/1.1. El cliente del backend lo fija: n8n no responde a la petición de actualización a HTTP/2 sin TLS |
| Estado HTTP | n8n responde **200** con el JSON del contrato, también cuando informa `status: "ERROR"`. Cualquier otro estado se trata como fallo de n8n |
| Reintentos | El backend no repite una llamada tras un timeout. Solo reintenta la asistencia una vez cuando una fuente quedó obsoleta (`attempt: 2`) |
| Versión | Todo mensaje lleva `schemaVersion: 1`. Una respuesta de asistencia con otra versión se rechaza |
| Campos desconocidos | Se ignoran: el contrato puede crecer sin romper al receptor |

El webhook de n8n debe validar `X-Service-Token` antes de procesar. El backend no envía el JWT del
usuario, su nombre, su correo ni comentarios internos del incidente.

## 2. Indexación de un artículo (RF03)

Spring Boot llama a n8n cuando un artículo se activa, se desactiva, cambia de contenido o cuando un
administrador pide reindexar o reconciliar. Un artículo por llamada.

### Solicitud

```json
{
  "schemaVersion": 1,
  "action": "UPSERT",
  "articleId": 12,
  "contentVersion": 3,
  "title": "La VPN no conecta: error 809",
  "problem": "…",
  "symptoms": "…",
  "cause": "…",
  "solution": "…",
  "contentHash": "9f2c…(64 hexadecimales)",
  "eligible": true,
  "updatedAt": "2026-11-12T10:15:00"
}
```

| `action` | Qué debe hacer n8n |
|---|---|
| `UPSERT` | Generar los embeddings de esa versión y guardarlos en Qdrant **reemplazando** todos los puntos del artículo. Identidad determinista por `articleId` y número de fragmento, de modo que repetir la llamada no duplique puntos. Retirar los fragmentos sobrantes de versiones anteriores |
| `DELETE` | Retirar todos los puntos del artículo. El contenido llega en `null` y `eligible` en `false` |

Cada punto de Qdrant debe guardar en su payload al menos `articleId`, `contentVersion`, `contentHash`
y el índice del fragmento.

### Respuesta

```json
{
  "schemaVersion": 1,
  "articleId": 12,
  "contentVersion": 3,
  "status": "INDEXED",
  "embeddingModel": "nombre-del-modelo",
  "embeddingDimension": 1536,
  "chunkCount": 2,
  "errorCode": null
}
```

| Campo | Regla que valida el backend |
|---|---|
| `articleId`, `contentVersion` | Deben coincidir con la solicitud |
| `status` | `INDEXED` para un `UPSERT` correcto, `REMOVED` para un `DELETE` correcto, `ERROR` si falló |
| `embeddingModel`, `embeddingDimension` | Obligatorios en `INDEXED`. Si otro artículo ya está indexado con un modelo o una dimensión distintos, el backend marca `EMBEDDING_MODEL_MISMATCH`: no se mezclan vectores incompatibles |
| `errorCode` | Código en mayúsculas con formato `[A-Z][A-Z0-9_]{2,59}`, por ejemplo `EMBEDDING_FAILED`. Cualquier otro texto se descarta y se guarda `INDEX_FAILED` |

Si la respuesta no cumple, el artículo queda con `indexStatus = ERROR` y sigue existiendo en la base
relacional. Se reintenta con `POST /api/knowledge/articles/{id}/reindex` o con
`POST /api/knowledge/articles/index/reconcile` (hasta 50 pendientes por llamada).

## 3. Consulta de asistencia (RF04 y RF05)

### Solicitud

```json
{
  "schemaVersion": 1,
  "requestId": "3f2b8c1e-6a4d-4b7e-9c0a-5d1e2f3a4b5c",
  "queryId": 87,
  "incidentId": 140,
  "attempt": 1,
  "question": "¿Cómo recupero la conexión?",
  "incidentTitle": "Sin conexión a internet",
  "incidentDescription": "El equipo no navega y muestra el aviso Sin acceso a internet",
  "incidentCategory": "Conectividad",
  "maxResults": 5,
  "minScore": 0.7
}
```

| Campo | Significado |
|---|---|
| `requestId`, `queryId` | Identifican la consulta ya guardada en PostgreSQL. La respuesta debe devolver los mismos valores |
| `attempt` | `1` normalmente. `2` cuando el backend descartó el primer resultado porque citaba un artículo inactivo o de otra versión; mismo `requestId` |
| `question` | Pregunta del usuario. Si no escribió ninguna, es la descripción del incidente (igual a `incidentDescription`) |
| `maxResults` | Máximo de artículos a recuperar (entre 3 y 5) |
| `minScore` | Umbral de similitud coseno. **Ningún artículo por debajo puede devolverse como fuente**; no se completa el número con artículos menos parecidos |

### Lo que debe hacer el workflow

1. Generar el embedding de la consulta con **el mismo modelo** usado para indexar.
2. Buscar en Qdrant y agrupar los fragmentos por artículo; la similitud del artículo es la máxima de sus fragmentos.
3. Descartar los que queden por debajo de `minScore` y quedarse con `maxResults` como máximo.
4. Validar los candidatos con `POST /api/integration/knowledge/eligibility` (sección 4) y construir el
   contexto **solo con el contenido que devuelve ese endpoint**, no con el texto guardado en Qdrant.
5. Si no queda ningún artículo utilizable, responder `NO_RESULTS` sin llamar al modelo de generación.
6. Si queda al menos uno, generar la respuesta usando exclusivamente ese contexto.

El prompt debe indicar que los artículos y la pregunta son **datos**, no instrucciones: el modelo no
cambia reglas, no revela secretos y no ejecuta acciones. El workflow no debe tener herramientas con
permisos operativos (no cambia estados de incidentes, no asigna técnicos, no verifica artículos).

### Respuesta

```json
{
  "schemaVersion": 1,
  "requestId": "3f2b8c1e-6a4d-4b7e-9c0a-5d1e2f3a4b5c",
  "queryId": 87,
  "status": "ANSWERED",
  "answer": "Renueve la dirección IP del equipo…",
  "recommendedSteps": ["Abrir una consola como administrador", "Ejecutar ipconfig /release y luego ipconfig /renew"],
  "sources": [
    { "articleId": 1, "contentVersion": 1, "score": 0.93, "chunkIds": ["1-0"] },
    { "articleId": 2, "contentVersion": 1, "score": 0.81, "chunkIds": ["2-0", "2-1"] }
  ],
  "embeddingModel": "nombre-del-modelo-de-embeddings",
  "generationModel": "nombre-del-modelo-de-generación",
  "threshold": 0.7,
  "similarityMetric": "cosine",
  "workflowVersion": "assist-1",
  "errorCode": null
}
```

| `status` | Cuándo | Qué exige el backend |
|---|---|---|
| `ANSWERED` | Hay evidencia y se generó una respuesta | `answer` no vacío (máximo 20 000 caracteres); `sources` con entre 1 y `maxResults` artículos |
| `NO_RESULTS` | Ningún artículo utilizable superó el umbral | `sources` vacío o ausente. Cualquier `answer` se ignora y no se guarda |
| `ERROR` | Fallo técnico (embeddings, Qdrant, generación) | `errorCode` con formato `[A-Z][A-Z0-9_]{2,59}`; si no lo cumple se guarda `ASSIST_FAILED` |

Validaciones sobre la respuesta. Si alguna falla, la respuesta se rechaza **completa** y la consulta
termina en `ERROR` con `N8N_INVALID_RESPONSE`:

- `schemaVersion` igual a 1; `requestId` y `queryId` iguales a los enviados; `status` conocido.
- `recommendedSteps`: opcional, máximo 20 pasos, ninguno vacío, 2000 caracteres cada uno.
- Cada fuente: `articleId` positivo, `contentVersion` ≥ 1, `score` numérico finito entre −1 y 1 y
  **mayor o igual que `minScore`**; sin artículos repetidos; `chunkIds` opcional (máximo 50).
- `similarityMetric`: ausente o `cosine`. El contrato v1 solo admite similitud coseno.
- `embeddingModel`, `generationModel` (120 caracteres) y `workflowVersion` (60) dentro de su longitud.

Validaciones contra PostgreSQL al guardar una respuesta `ANSWERED`:

| Situación | Resultado |
|---|---|
| Un `articleId` no existe | `ERROR` con `N8N_INVALID_RESPONSE` |
| Un artículo está inactivo, sin verificar o en otra versión | El resultado se descarta entero y el backend llama de nuevo con `attempt: 2`. Si vuelve a ocurrir, `ERROR` con `SOURCES_CHANGED` |
| `embeddingModel` distinto del modelo con que se indexó alguna fuente | `ERROR` con `EMBEDDING_MODEL_MISMATCH` |

Quitar la cita de un artículo obsoleto y conservar el texto no es válido: el texto se generó con esa
fuente. Por eso el backend descarta el resultado y pide otro.

## 4. Lo que n8n puede consultar al backend

Ambos endpoints requieren `X-Integration-Token`. Un JWT de usuario, incluso de administrador, no los abre.

### `POST /api/integration/knowledge/eligibility`

Valida los artículos recuperados de Qdrant antes de construir el contexto. Máximo 50 por llamada.

```json
{ "schemaVersion": 1, "articles": [ { "articleId": 1, "contentVersion": 1 }, { "articleId": 31, "contentVersion": 1 } ] }
```

```json
{
  "schemaVersion": 1,
  "articles": [
    { "articleId": 1, "requestedVersion": 1, "usable": true, "currentVersion": 1,
      "title": "…", "problem": "…", "symptoms": "…", "cause": "…", "solution": "…" },
    { "articleId": 31, "requestedVersion": 1, "usable": false, "currentVersion": null,
      "title": null, "problem": null, "symptoms": null, "cause": null, "solution": null }
  ]
}
```

`usable` es `true` solo si el artículo existe, está activo y verificado y la versión pedida es la
vigente. En cualquier otro caso no se devuelve contenido. Esta comprobación es la que impide usar
un artículo desactivado aunque su vector siga en Qdrant.

### `GET /api/integration/knowledge/eligible`

Lista de artículos elegibles con su versión y su hash, para reconciliar el índice: todo punto de
Qdrant cuyo artículo o versión no figure en la lista debe retirarse.

```json
[ { "articleId": 1, "contentVersion": 1, "contentHash": "9f2c…" } ]
```

## 5. Códigos de error

| Código | Origen | Estado HTTP que recibe el frontend |
|---|---|---|
| `N8N_NOT_CONFIGURED` | Falta `N8N_BASE_URL` | 503 |
| `N8N_UNAVAILABLE` | No se pudo conectar | 503 |
| `N8N_TIMEOUT` | n8n no respondió a tiempo | 504 |
| `N8N_UPSTREAM_ERROR` | n8n respondió con un estado HTTP distinto de 2xx | 502 |
| `N8N_INVALID_RESPONSE` | Respuesta ilegible, demasiado grande o que no cumple el contrato | 502 |
| `SOURCES_CHANGED` | Las fuentes siguieron obsoletas tras el reintento | 503 |
| `EMBEDDING_MODEL_MISMATCH` | Modelo de embeddings distinto al de la indexación | 502 |
| `ASSIST_ABANDONED` | La consulta quedó sin resultado más allá de `ASSIST_PENDING_TIMEOUT` | 504 |
| `ASSIST_UNEXPECTED_ERROR` | Fallo interno no previsto al procesar la consulta | 502 |
| Código propio del workflow (`EMBEDDING_FAILED`, `VECTOR_SEARCH_FAILED`, `GENERATION_FAILED`…) | n8n con `status: "ERROR"` | 502 |

## 6. Pendiente de acordar con quien construye los workflows

- **Umbral:** `ASSIST_MIN_SCORE=0.70` es un valor inicial sin calibrar. Debe ajustarse con el modelo
  de embeddings real y un conjunto de consultas de prueba (con cobertura y sin ella), y documentar la justificación.
- **Fragmentación:** los flujos actuales indexan cada artículo como un único punto (fragmento 0, `chunkIds` con la forma `"{articleId}-0"`). Si los artículos crecen habrá que fragmentar.
- **Códigos de error** que emiten los flujos: `INVALID_REQUEST`, `EMBEDDING_FAILED`, `VECTOR_UPSERT_FAILED`, `VECTOR_DELETE_FAILED`, `VECTOR_SEARCH_FAILED`, `ELIGIBILITY_CHECK_FAILED`, `GENERATION_FAILED` y `GENERATION_INVALID_OUTPUT`.
- **Prueba con modelos reales:** la prueba integrada usó un proveedor simulado; falta repetirla con una clave de OpenRouter.
