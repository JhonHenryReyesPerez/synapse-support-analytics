# Contrato de herramientas de conocimiento v2

Extensión aditiva. Spring Boot autentica, autoriza y persiste; n8n orquesta proveedor/Qdrant; PostgreSQL decide elegibilidad y conserva evidencia. El frontend no consume servicios internos.

## API del navegador

Base `/api/rag`, JWT válido y restricciones de sesión existentes. 400 entrada inválida; 403 rol/recurso; 404 inexistente; 409 conflicto o manifiesto sin indexar. Fallos externos: 502 contrato/proveedor, 503 indisponible/no configurado, 504 timeout. Códigos seguros sin mensajes arbitrarios del proveedor.

| Método / ruta | Actor | Contrato |
|---|---|---|
| GET `/profiles` | Autenticado | Perfiles inmutables y activo |
| POST `/profiles` | ADMIN | `{id,name,model,dimension,strategy,minCosine,chunkSize,representation,rerankModel?}` |
| GET `/profiles/{id}/plan` | ADMIN | Fragmentos, artículos, caché y estimación identificada |
| POST `/profiles/{id}/index?limit=5` | ADMIN | Lote 1–10; `{indexed,remaining,done,total}` para perfiles vectoriales |
| GET `/profiles/{id}/health` | ADMIN | Puntos ausentes/incompatibles, obsoletos y completitud |
| POST `/profiles/{id}/repair` | ADMIN | Retira obsoletos y libera manifiesto de ausentes; completar con `/index` |
| POST `/profiles/{id}/activate` | ADMIN | Exige manifiesto vigente para perfiles vectoriales; conserva anteriores |
| POST `/search` | Autenticado | `{question,profileId?,limit?,platform?,application?,productVersion?,architecture?}` → `{requestId,profileId,strategy,hits,telemetry}` |
| GET / PUT `/articles/{id}/metadata` | Acceso al artículo / ADMIN o TECH autor de borrador | Etiquetas técnicas y procedencia opcional; valida artículo/origen |
| POST `/import/preview` | ADMIN / TECHNICIAN | Multipart `file`; `{filename,sha256,pages,text,notice}`, sin crear artículo |
| GET `/tickets/{id}/draft` | ADMIN / TECHNICIAN autorizado | Plantilla desde resolución, origen y borradores propios relacionados |
| GET `/tickets/{id}/similar` | Acceso al ticket | Hasta cinco coincidencias textuales dentro del alcance |
| POST `/clarify` | Autenticado | `{question?,facts?}` → pregunta preparada, `needsClarification,prompts` |
| GET / POST `/evaluation/cases` | ADMIN | `{question,expectedArticleIds,expectNoResults,split?,category?,notes?}`; booleano obligatorio, lista vacía para abstención |
| GET / POST `/evaluation/runs` | ADMIN | POST `{profileIds,caseIds}`, hasta 3 perfiles y 30 búsquedas. GET últimas 50 ejecuciones |
| GET `/gaps?page=0` | ADMIN | Página de 50 NO_RESULTS o utilidad ≤2 |
| PUT `/gaps/{queryId}` | ADMIN | `{diagnosis,notes?,articleId?}`; diagnóstico enumerado en `RagContracts` |
| GET `/operations` | ADMIN | Últimas 1000 búsquedas, p50/p95, estado/duración/error/telemetría |

Perfil: id `[a-z0-9_-]` de 1–40 caracteres; nombre ≤100; modelo ≤120; dimensión 1–8192. Estrategias TEXT/DENSE/HYBRID/RERANK. `minCosine` obligatorio [0,1]. `chunkSize`: 0 o 300–8000 caracteres orientativos. Representación FULL/SYMPTOMS; RERANK exige modelo.

Pregunta directa: 1–2000 caracteres; límite 1–20 artículos, predeterminado 5. Filtros exactos sin distinguir mayúsculas. Hit: `pointId,articleId,version,title,section,evidence,cosine,rankingScore,metric,rank`. Coseno puede ser null; ranking RRF/reranker/cobertura no es coseno ni certeza.

Se amplía `POST /api/tickets/{id}/assistance` con `retrievalProfileId` opcional. Sin él conserva v1; con él reutiliza autorización, PENDING, requestId, reintento por fuentes obsoletas, historial y calificación. No hay retry HTTP ciego. Historial añade `advancedJson` opcional con perfil, estrategia, fuentes, citas `{stepIndex,pointIds}` y telemetría. `sources[].similarity` admite null. En v2 prima orden de recuperación; v1 conserva orden por coseno. `retrieval.minScore` representa el umbral de la vía vectorial; la evidencia literal híbrida tiene otro criterio.

## Spring Boot → n8n

POST fijo `/webhook/synapse-knowledge-v2`, JSON, `X-Service-Token` de entorno ≥32 caracteres. Se conserva límite de respuesta/plazo total; sin redirects. No envía JWT, identidades humanas, comentarios internos ni entidad completa del incidente.

Sobre común: `{schemaVersion:2,requestId:UUID,operation,profile}`. `profile` es la configuración física inmutable procedente del backend: `id,model,dimension,collection_name,strategy,min_cosine,chunk_size,representation,rerank_model`. Cada colección `rag_synapse_*` pertenece a un perfil; modelos o dimensiones incompatibles no comparten colección.

| Operación | Entrada adicional | Resultado OK |
|---|---|---|
| INDEX | `document` con pointId/artículo/versión/hash/sección/texto preparado, `cachedVector?` | `vector`, dimensión/valores finitos comprobados antes de guardar caché/manifiesto |
| SEARCH | `question,limit,lexicalCandidates,allowedPointIds` | `hits` con pointId/cosine/rankingScore y telemetría |
| GENERATE | `question,sources` con evidencia autorizada | `answer,steps[{text,citations}],generationModel?,telemetry?` o `insufficient:true` |
| SCROLL | `cursor?` | `points[{id,payload}],nextCursor,collectionExists` |
| DELETE | `pointIds` UUID, máximo 100 | Confirmación en colección aislada |

Respuesta: `schemaVersion:2`, requestId exacto, `status:OK|ERROR`; ERROR con código cerrado de etapa. Errores técnicos se devuelven estructurados; autenticación/solicitud inválida usan HTTP 4xx. Spring Boot vuelve a validar el contrato.

SEARCH: embedding → Qdrant con filtro `has_id` → revalidación relacional → RRF → reranker opcional → deduplicación por artículo. DENSE exige coseno mínimo; HYBRID/RERANK aceptan también evidencia literal admisible. Umbral léxico inicial 0,6 de cobertura o todos los identificadores detectados; requiere calibración. RRF suma `1/(60+posición)` por lista. No aplica umbral coseno al score RRF.

GENERATE: pregunta/evidencia como datos, sin herramientas operativas. 1–20 pasos, 1–5 citas por paso, solo referencias del contexto. Al persistir revalida versión/publicación. Si cambia una fuente, descarta toda la respuesta y permite como máximo otro intento; no conserva texto quitando solo la cita. Fidelidad semántica requiere revisión humana.

## n8n → Spring Boot

POST `/api/integration/rag/evidence`, solo `X-Integration-Token` de entorno; un JWT humano no habilita la ruta. Entrada `{profileId,pointIds}` (hasta 200). Devuelve documentos del perfil con artículo activo, verificado y versión vigente; sin autores ni datos de incidentes.

## Límites y operación

Qdrant guarda metadatos derivados; PostgreSQL decide elegibilidad. URLs/secretos son configuración del servicio. Las vistas escapan texto y el PDF nunca se interpreta como HTML. Ver sincronización explícita, límites de corpus/caché, pruebas pendientes y activación en [MEJORAS_RAG_IMPLEMENTADAS.md](MEJORAS_RAG_IMPLEMENTADAS.md). Activar perfil afecta al buscador, no cambia silenciosamente el asistente clásico ni certifica calidad del modelo.
