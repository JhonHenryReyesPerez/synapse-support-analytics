# Documento de pruebas del backend

Resultados reales de la última ejecución y limitaciones conocidas. Alcance: backend Spring Boot.
El frontend, Docker Compose y los workflows de n8n tienen sus propias pruebas a cargo de otros integrantes.

## 1. Resultado de la última ejecución

Fecha: 12 de noviembre de 2026. Rama `dev/JhonReyes`. Java 21 (Temurin 21.0.12), Gradle Wrapper.

| Ejecución | Comando | Base de datos | Pruebas | Fallos | Omitidas |
|---|---|---|---:|---:|---:|
| Rápida | `./gradlew test` | H2 en memoria, esquema generado por Hibernate | 100 | 0 | 6 |
| Completa | `backend/scripts/test-postgres.sh` | PostgreSQL 17.11 desechable, 8 migraciones de Flyway, Hibernate en `validate` | 100 | 0 | 0 |

Las 6 omitidas en H2 son `DemoDataSeedTests`: cargan el script de datos iniciales, que es SQL de
PostgreSQL, y solo se ejecutan en la ejecución completa.

| Clase de prueba | Pruebas | Qué cubre |
|---|---:|---|
| `UsermanagementApplicationTests` | 16 | Flujo base: usuarios, roles, tickets, comentarios, historial |
| `PasswordPolicyTests` | 12 | Política única de contraseñas (B02) |
| `AssistanceTests` | 12 | Asistente RAG: consulta, validación del flujo, fuentes, estados, historial (RF04, RF05, RF08) |
| `DemoDataSeedTests` | 6 | Datos iniciales: mínimos, coherencia, analítica contra SQL (§16) |
| `CategoryTests` | 5 | Categorías relacionales y prioridad canónica (B06) |
| `KnowledgeIndexTests` | 5 | Sincronización del índice y contrato de servicio para n8n (RF03) |
| `TicketAssignmentTests` | 5 | Asignación única, incluida la concurrencia (B05) |
| `UserAccountPolicyTests` | 5 | Política de cuentas, último administrador, baja lógica (B07, B10, B12) |
| `AnalyticsTests` | 4 | Siete indicadores contra cálculo manual y SQL (B09, RF06, RF07) |
| `KnowledgeArticleTests` | 4 | Ciclo editorial de artículos (RF02) |
| `PasswordChangeFlowTests` | 4 | Cambio y restablecimiento de contraseña |
| `PasswordChangeRequiredTests` | 4 | Restricción con cambio de contraseña pendiente (B03) |
| `TokenInvalidationTests` | 4 | Invalidación de tokens por versión de credenciales (B04) |
| `AssistanceRatingTests` | 3 | Calificación de respuestas (RF08) |
| `JwtPropertiesBindingTests` | 3 | Secretos obligatorios por entorno (B01) |
| `TicketResolutionTests` | 3 | Fechas de resolución y máquina de estados (B08) |
| `JwtUtilTests` | 2 | Firma y validación del JWT |
| `CommentVisibilityTests` | 1 | El solicitante no ve ni crea notas internas (§6.3) |
| `OpenApiDocumentationTests` | 1 | El contrato de Swagger incluye los endpoints nuevos |
| `TicketPaginationTests` | 1 | Paginación y orden estable (B11) |
| **Total** | **100** | |

## 2. Cómo reproducirlas

```bash
cd backend
./gradlew test                    # H2, sin dependencias externas
bash scripts/test-postgres.sh     # PostgreSQL 17 en Docker (requiere Docker en ejecución)
```

`test-postgres.sh` levanta un contenedor `postgres:17-alpine`, crea dos bases vacías
(`campusdesk_test` y `campusdesk_seed`), aplica las migraciones reales y ejecuta toda la suite. El
informe HTML queda en `backend/build/reports/tests/test/index.html`.

Cada prueba crea sus propios usuarios, tickets y artículos y recupera sus identificadores reales:
no dependen del orden de ejecución ni de `ticketId=1`.

## 3. Las diez pruebas funcionales obligatorias

En las pruebas del asistente, n8n se sustituye por `FakeN8nClient`, un doble que registra lo que el
backend envía y devuelve la respuesta programada en cada caso. Esto hace las pruebas deterministas,
pero significa que comprueban **lo que hace Spring Boot ante cada respuesta posible del flujo**, no
la calidad de la recuperación semántica ni del texto generado.

| # | Prueba del enunciado | Método que la cubre | Qué comprueba | Resultado |
|---:|---|---|---|---|
| 1 | Incidente de conectividad con soluciones relevantes mediante RAG | `AssistanceTests.connectivityIncidentGetsGroundedAnswerWithSourcesAndSimilarities` | Respuesta con solución, pasos, tres fuentes ordenadas por similitud y evidencia; a n8n solo viajan datos mínimos, sin usuario ni comentarios internos; todo queda persistido | Pasa (con doble de n8n) |
| 2 | Error de ejecución de una aplicación y artículos recuperados | `AssistanceTests.applicationErrorQueryReturnsExactlyTheRetrievedArticles` | Las fuentes son exactamente los artículos recuperados; dos fuentes se informan como evidencia limitada; la fuente conserva versión y texto si el artículo cambia | Pasa (con doble de n8n) |
| 3 | Caso sin cobertura: no se inventan soluciones | `AssistanceTests.queryWithoutCoverageEndsInNoResultsWithoutInventedSolution` | Estado `NO_RESULTS`, sin respuesta ni pasos; el texto que acompañe a `NO_RESULTS` se descarta y no se guarda | Pasa (con doble de n8n) |
| 4 | Incidentes de una categoría coinciden con la base relacional | `DemoDataSeedTests.demoUsersCanLogInAndSeeOnlyTheirScope`, `AnalyticsTests.indicatorsMatchManualCalculationAndIndependentSql` | El listado filtrado por categoría y el indicador por categoría coinciden con `SELECT count(*)` directo | Pasa (PostgreSQL) |
| 5 | Evolución semanal comprobada con SQL | `DemoDataSeedTests.analyticsOverTheFullDatasetMatchIndependentSql`, `AnalyticsTests` | Cada semana de la serie coincide con `date_trunc('week')`; la serie suma el total; límites de domingo y lunes | Pasa (PostgreSQL) |
| 6 | Un artículo desactivado no vuelve a ser fuente | `AssistanceTests.deactivatedArticleIsNeverUsedAsSourceEvenIfItsVectorRemains`, `sourceChangedDuringGenerationDiscardsTheResult`, `KnowledgeIndexTests.integrationContractRequiresServiceSecretAndReflectsRelationalTruth` | Aunque el flujo devuelva el artículo retirado, el resultado se descarta, hay un único reintento y nunca se guarda como fuente; el endpoint de elegibilidad lo marca como no utilizable | Pasa (con doble de n8n) |
| 7 | Descripción vacía: no se ejecuta el flujo inteligente | `AssistanceTests.blankDescriptionOrQuestionNeverInvokesTheFlow` | Incidente sin descripción: 400. Pregunta vacía: 400. Cero llamadas a n8n y ninguna consulta guardada | Pasa |
| 8 | Fallo del servicio de embeddings | `AssistanceTests.embeddingFailureEndsInControlledErrorWithQueryId` | Estado `ERROR` con código seguro, respuesta 502/503/504 con `queryId`, sin repetición automática y sin exponer el detalle del proveedor | Pasa (con doble de n8n) |
| 9 | Historial de asistencias de un incidente | `AssistanceTests.historyIsListedPerIncidentWithTicketAuthorization` | Historial paginado y ordenado; acceso del solicitante, técnico asignado y administrador; 403 para terceros; disponible con el incidente cerrado | Pasa |
| 10 | Calificar una respuesta y comprobar la persistencia | `AssistanceRatingTests.requesterRatesAnAnsweredQueryAndTheRatingIsPersistedAndUpdated` | Calificación guardada, actualizada sin duplicar, rango 1 a 5 validado en la API y en la base | Pasa |

## 4. Pruebas adicionales (§17.2)

| Caso | Dónde |
|---|---|
| JWT inválido, vencido o con otro emisor; usuario bloqueado o deshabilitado; token invalidado por cambio de clave | `JwtUtilTests`, `UsermanagementApplicationTests.cp16_rejectsInvalidOrExpiredJwt`, `UserAccountPolicyTests`, `TokenInvalidationTests` |
| Cambio de contraseña obligatorio no eludible con llamadas directas | `PasswordChangeRequiredTests` |
| USER no consulta tickets ajenos, notas internas ni métricas globales | `UsermanagementApplicationTests.cp06_rejectsViewingOtherUsersTicket`, `CommentVisibilityTests`, `AnalyticsTests.scopeIsAppliedBeforeAggregatingAndTiesAreReported` |
| Técnico no resuelve tickets no asignados ni omite `IN_PROGRESS` | `TicketResolutionTests` |
| Asignación concurrente y equivalencia de rutas de asignación | `TicketAssignmentTests` |
| Cambio de contenido invalida verificación e índice | `KnowledgeArticleTests`, `KnowledgeIndexTests` |
| Fuente desactivada o modificada durante la generación invalida la respuesta | `AssistanceTests.sourceChangedDuringGenerationDiscardsTheResult` |
| Artículo desactivado mientras n8n lo indexa | `KnowledgeIndexTests.articleChangedDuringIndexingIsNotMarkedAsIndexed` |
| Doble solicitud, `requestId` repetido y respuesta única por consulta | `AssistanceTests.doubleSubmissionDoesNotDuplicateQueriesOrResponses`, `AssistanceRatingTests.databaseRejectsOutOfRangeOrDuplicateRatings` |
| Recuperación de `PENDING` tras una interrupción y resultado tardío | `AssistanceTests.abandonedPendingQueriesAreRecoveredAndLateResultsDoNotOverwriteThem` |
| Indexación repetida sin cambiar la identidad; cambio de versión retira la anterior | `KnowledgeIndexTests` |
| Una o dos fuentes válidas, y cero; sin rellenar con evidencia irrelevante | `AssistanceTests` (pruebas 2 y 3; «similitud bajo el umbral» en `malformedWorkflowResponsesNeverBecomeAnswers`) |
| Scores, identificadores o cuerpo malformados de n8n no crean respuestas | `AssistanceTests.malformedWorkflowResponsesNeverBecomeAnswers` (25 variantes) |
| Calificación ajena, duplicada, fuera de rango y de una consulta no respondida | `AssistanceRatingTests` |
| Indicadores con cohorte vacía, empates, límites de día y semana, `CLOSED` y resolución sin fecha | `AnalyticsTests` |
| Las migraciones se aplican sobre una base vacía y los datos iniciales cumplen los mínimos | Ejecución completa en PostgreSQL, `DemoDataSeedTests` |

## 5. Verificación de los datos iniciales

`DemoDataSeedTests` carga `db/seed/demo_data.sql` sobre una base recién migrada y comprueba:

| Mínimo del enunciado | Exigido | Cargado |
|---|---:|---:|
| Incidentes históricos | 150 | 180 |
| Artículos técnicos | 25 | 31 (28 publicados, 1 verificado inactivo, 2 borradores) |
| Categorías | 5 | 7 |
| Usuarios | 10 | 13 (8 solicitantes, 4 técnicos, 1 administrador) |
| Estados de incidente | 4 | 5 |
| Niveles de prioridad | 4 | 4 |

Además: creación ≤ asignación ≤ inicio ≤ resolución ≤ cierre; `resolved_at` y `closed_at` coinciden
con el evento del historial; el último evento coincide con el estado actual; el técnico actual es el
de la última asignación; cargar el script dos veces se rechaza sin duplicar; los usuarios de
demostración inician sesión; el hash de los artículos es el que calcula la aplicación; y los siete
indicadores de la API coinciden con SQL independiente sobre los 180 incidentes.

## 6. Evidencia de Swagger

- Especificación OpenAPI exportada: [`openapi.json`](openapi.json), 48 rutas. La genera
  `OpenApiDocumentationTests` en `backend/build/openapi/openapi.json` a partir de la aplicación en ejecución.
- Interfaz: `http://localhost:8051/swagger-ui.html` con la aplicación arrancada.
- Las capturas de pantalla de Swagger con los endpoints nuevos **están pendientes**: las de `docs/assets` son anteriores a estos módulos.

## 7. Limitaciones y lo que no se ha comprobado

| Limitación | Consecuencia |
|---|---|
| El asistente y la indexación solo se han probado con un doble de n8n | Las pruebas 1, 2, 3, 6 y 8 no demuestran que la recuperación en Qdrant sea relevante ni que el modelo se limite al contexto. Falta la prueba integrada contra los workflows reales |
| El umbral de similitud (0,70) no está calibrado | Es un valor inicial; debe ajustarse con el modelo de embeddings real y casos con y sin cobertura |
| La fidelidad del texto generado no se evalúa automáticamente | Una lista de fuentes válida no prueba que el texto esté fundamentado: requiere revisión humana con respuestas reales |
| Las migraciones solo se han ejecutado sobre bases vacías | No se han probado sobre una copia de la base de desarrollo existente (creada antes de adoptar Flyway) |
| H2 no ejecuta las migraciones | Las restricciones definidas solo en SQL (índice único parcial de consultas pendientes, varios `CHECK`) solo se validan en la ejecución con PostgreSQL |
| Sin pruebas de carga | No se midió el comportamiento con muchas consultas simultáneas al asistente |
| Control de intentos de inicio de sesión | Endurecimiento propuesto, no implementado |
| Revisión humana | El código de estas etapas fue generado con asistencia de IA y su revisión por el responsable está pendiente (ver `AI_USAGE.md`) |
