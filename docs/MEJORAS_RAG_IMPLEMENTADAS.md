# Funcionalidades añadidas: conocimiento, embeddings y RAG

Fecha: 19 de noviembre de 2026. Rama: `feat/rag-knowledge-evolution`.
Base: `origin/main`, commit `8a81400`.

Se incorporan las 17 propuestas en un primer alcance operativo. Se conserva Spring Boot → n8n → Qdrant/proveedor, PostgreSQL como autoridad, HTML/CSS/JavaScript y la identidad editorial con temas claro y oscuro. Los siete indicadores de incidentes no cambian.

## Funcionalidades implementadas

| # | Funcionalidad | Alcance concreto |
|---|---|---|
| 1 | Evaluación reproducible | ADMIN registra preguntas, artículos esperados o abstención, tema y notas. Separa CALIBRATION/HOLDOUT y compara hasta tres perfiles y 30 búsquedas por ejecución. Conserva corpus, versiones, hash y configuración; calcula Recall@5, MRR@5 y nDCG@5. No mide automáticamente fidelidad de respuestas. |
| 2 | Buscador independiente | En Conocimiento se consulta documentación publicada sin ticket ni generación. La búsqueda textual funciona sin proveedor externo. |
| 3 | Recuperación híbrida | Coincidencia léxica y búsqueda densa combinadas con RRF (constante 60). Conserva códigos literales y separa coseno de ranking. La vía textual usa cobertura de términos normalizados, **no BM25**. |
| 4 | Citas por paso | Pasos con referencias a fragmentos recuperados; rechazo de referencias ajenas, evidencia y versión persistidas, apertura desde cada paso y exportación local a Markdown. Las citas válidas no prueban por sí solas fidelidad semántica. |
| 5 | Salud del índice | Compara puntos reales con documentos vigentes; detecta ausentes, incompatibles, obsoletos y huérfanos. Reparación explícita en la colección del perfil y recuperación posterior de ausentes por lotes. Nunca borra la colección completa. |
| 6 | Operación del RAG | Últimas 1000 búsquedas: estados, errores, duración, p50/p95 y metadatos de etapas/tokens/coste cuando los reporta el proveedor. No inventa consumos ausentes ni cambia el dashboard obligatorio. |
| 7 | Fragmentación estructural | Artículo completo para textos cortos o secciones y párrafos para documentos largos. UUID determinista por perfil/artículo/versión/fragmento/hash. No corta un párrafo largo; el tamaño es orientativo. |
| 8 | Reranking opcional | Estrategia RERANK mediante OpenRouter sobre candidatos previamente validados. Verifica índices, duplicados y scores finitos. No se activa automáticamente. |
| 9 | Aclaración y hechos explícitos | Hasta cuatro hechos y pregunta final de 2000 caracteres. Solicita aplicación, código y versión si resulta vaga. No copia comentarios internos ni construye conversaciones ocultas. |
| 10 | Filtros técnicos | Sistema, aplicación, versión y arquitectura editables por autores autorizados. Coincidencia exacta sin distinguir mayúsculas; restringe IDs antes de Qdrant y revalida después. |
| 11 | Importación PDF | Previsualización para ADMIN/TECHNICIAN: PDF con texto, hasta 5 MB, 100 páginas y 50.000 caracteres extraídos. SHA-256 de bytes originales, nombre y páginas. Selección/revisión de hasta 10.000 caracteres para el procedimiento; no publica ni indexa automáticamente. |
| 12 | Borrador desde resolución | Incidente RESOLVED/CLOSED autorizado → título, problema y nota de solución. Exige completar causa/síntomas y revisar datos sensibles. Conserva origen y detecta borradores propios relacionados; no copia comentarios. |
| 13 | Edición y paginación | Usa PUT existente y sus reglas de versión/verificación. Páginas de 20 artículos, anterior/siguiente y sugerencias de duplicados. Si falla guardar metadatos, conserva el ID para reintentar sin duplicar contenido. |
| 14 | Bandeja editorial | ADMIN revisa NO_RESULTS y valoraciones de 1–2, guarda diagnóstico, acción y artículo relacionado. No convierte utilidad percibida en verificación ni resolución. |
| 15 | Perfiles y cambio reversible | Configuración inmutable de modelo, dimensión, representación, umbral, fragmentación, estrategia y colección. Otro modelo requiere candidato nuevo. Activación y regreso al anterior; solicitudes con configuración física explícita, sin mezclar espacios vectoriales. |
| 16 | Caché exacta y previsión | Reutiliza por texto preparado, modelo, dimensión, representación y versión de preparación. Prevé artículos/fragmentos, caché, embeddings nuevos y tokens estimados por caracteres. Lotes explícitos de hasta diez fragmentos (cinco desde UI). |
| 17 | Incidentes similares | Comparación **textual** dentro del alcance SQL: propios, asignados o todos para ADMIN. Hasta cinco resultados; sin vectorizar incidentes privados ni enviarlos al proveedor. |

## Ubicación y compatibilidad

- **Conocimiento:** buscador, filtros, evidencia, paginación y editor.
- **Laboratorio y salud del conocimiento (ADMIN):** perfiles, previsión/indexación, diagnóstico/reparación, evaluación, brechas y operación.
- **Detalle de incidente:** similares y borrador desde resolución para autores autorizados.
- **Asistente:** Clásica/perfiles, hechos, citas y exportación. Clásica sigue como opción inicial; seleccionar un perfil opta por v2.

Nuevo módulo `backend/.../modules/rag/`, migración aditiva `V10__rag_knowledge_tools.sql`, workflow `Flujo-3-Herramientas-Conocimiento.json` (ID `synapseKnowledgeV2`), `rag.js` y `rag.css`. PDFBox 3.0.8 es la única dependencia nueva. Contrato: [CONTRATO_RAG_V2.md](CONTRATO_RAG_V2.md).

Las colecciones `rag_synapse_*` están separadas del índice v1. El estado editorial INDEXED sigue describiendo el flujo v1; no certifica sincronización de todos los perfiles. El historial conserva v1 y añade detalles/coseno nullable para evidencia léxica; null no significa cero.

## Activación en pruebas

1. Revisar y respaldar la base de destino antes del despliegue. El arranque ordinario con Flyway aplicará V10; esta sesión **no** ejecutó migraciones sobre datos reales.
2. Importar/publicar el tercer workflow con el mecanismo existente. El script de inicio lo incorpora junto con los otros dos. Mantener secretos `N8N_SERVICE_TOKEN`, `INTEGRATION_API_TOKEN` y `OPENROUTER_API_KEY`; configurar `BACKEND_URL`, `QDRANT_URL` y, si corresponde, `QDRANT_API_KEY`.
3. Comprobar conectividad entre servicios. El perfil `text` queda activo; `dense`, `hybrid` y `rerank` son candidatos. Sus umbrales iniciales **no están calibrados** para producción.
4. ADMIN prevé consumo, indexa por lotes, comprueba salud y ejecuta casos revisados. Activar tras revisar resultados; se puede regresar a `text`.
5. Tras publicar, editar o retirar artículos, repetir diagnóstico/reparación/indexación de perfiles utilizados. La sincronización nueva es explícita; la validación relacional excluye versiones retiradas aunque queden puntos viejos.

Activar comprueba el manifiesto relacional vigente y no sustituye el diagnóstico físico. Comprobar salud inmediatamente antes. El timeout pendiente debe superar cuatro veces conexión+lectura n8n para asistencia v2; los valores predeterminados (5 min y 5+60 s) cumplen esta condición.

## Validación realizada

- Suite completa Gradle, H2 aislado: **132 pruebas, 6 omitidas, 0 fallos**. Las omitidas no se cuentan como aprobadas. Casos nuevos: permisos, filtros, IDs desconocidos, códigos literales, salud, caché, perfiles, citas, idempotencia, retirada concurrente de fuentes, evaluación reservada y PDF.
- **73 pruebas frontend** con DOM simulado y workflow en Node, sin fallos (7 de workflow). Compilan nodos Code, revisan conexiones y prueban autenticación, dimensiones, RRF, reranking, citas, errores y retiro. No ejecutan el motor n8n.
- Transporte v2 con HTTP real a un servidor local controlado: ruta fija y secreto de servicio. Las pruebas de reglas usan `FakeN8nClient`, sin modelos pagados.
- Orca con backend H2 temporal: login, búsqueda textual, laboratorio, edición PUT y metadatos. La edición retira el artículo hasta nueva revisión. Inspección a **1440, 768 y 390 px**, temas claro/oscuro; sin desbordamiento horizontal observado ni errores en la consola consultada.
- Tras los ajustes finales se repitieron los 13 escenarios RAG y el caso de asistencia citada; los códigos literales no aceptan prefijos de otros códigos/versiones. Swagger conserva los operationId anteriores y documenta JWT y coseno nullable.
- Revisión de Git y `git diff --check`. No se alteraron datos ni infraestructura existentes.

## Límites y comprobaciones pendientes

- Validación de integración posterior: PostgreSQL 17, n8n 2.47.7 y Qdrant 1.20.1 aislados; Flyway V1–V10, Hibernate `validate`, workflow publicado y 11 recorridos end-to-end aprobados. Se detectó y corrigió la falta de `webhookId` (el webhook devolvía 404) y una expresión inválida al crear la colección. Suite PostgreSQL: 132 pruebas, 0 omitidas/fallos; H2: 132 pruebas, 6 omitidas, 0 fallos.
- Embeddings, reranking y generación se probaron contra un mock HTTP local determinista; no se usó proveedor/modelo real ni se midió calidad semántica, latencia o coste real. Requiere evaluación humana y proveedor configurado.
- Importación solo PDF con texto: sin OCR/DOCX/HTML ni tablas/imágenes automáticas. No almacena archivo original. La procedencia reenviada al guardar es declarada, no una firma del servidor.
- Corpus/candidatos textuales en memoria. Límites del workflow: 10.000 fragmentos elegibles, 200 candidatos de evidencia, 100 densos y 40 para reranking. Diagnóstico hasta 25.600 puntos; si es incompleto bloquea reparar. Evaluar paginación/FTS al superarlos.
- Caché con exclusión local a una instancia; varias réplicas requieren upsert PostgreSQL en esa escritura. No se añadieron colas ni microservicios.
- Trazabilidad no equivale a verdad automática: fidelidad, calibración y selección final de modelo requieren revisión humana.
