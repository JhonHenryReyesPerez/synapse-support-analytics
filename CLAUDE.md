# Contexto e instrucciones del proyecto — Backend Spring Boot

> Guía de cambios sobre el repositorio ya clonado para desarrollar un sistema inteligente de análisis de incidentes tecnológicos y asistencia mediante IA.
> Fecha: 11 de noviembre de 2026. Responsable de Spring Boot: Jhon Henry Reyes Pérez.
> Alcance de esta guía: backend, persistencia, seguridad, contratos de integración, analítica y pruebas. Describe las responsabilidades externas necesarias, pero no autoriza desarrollar todo el frontend ni los workflows de otro integrante.

## 1. Cómo utilizar este documento

Trabaja sobre el repositorio ya clonado. Lee esta guía e inspecciona el código vigente antes de modificarlo. Aplica los cambios descritos de forma incremental, reutilizando los componentes existentes y conservando su arquitectura. No vuelvas a clonar ni recrees el proyecto.

Distingue tres niveles:

- **OBLIGATORIO:** requisitos del documento `docs/requerimientos/requerimientosSpringBoot.md` y sus seis imágenes complementarias.
- **BASE EXISTENTE:** comportamiento observado en el backend; puede necesitar correcciones.
- **DECISIÓN PROPUESTA:** definición técnica o de negocio adoptada como punto de partida de esta guía. No atribuirla al enunciado académico. Si el equipo acuerda otra, actualizar esta guía, contratos y pruebas antes de implementarla.

Las decisiones propuestas buscan cerrar ambigüedades sin cambiar la arquitectura. Las instrucciones explícitas posteriores del responsable prevalecen. No convertir recomendaciones en requisitos académicos ni añadir funcionalidades fuera del alcance silenciosamente.

### 1.1 Forma de colaboración con IA

El enunciado exige tutoría técnica: orientar, explicar decisiones, presentar pseudocódigo y ejemplos parciales, revisar el código del estudiante y trabajar por incrementos. No generar ni ejecutar autónomamente la totalidad del proyecto.

Para cada incremento:

1. Identificar requisito, comportamiento actual y resultado esperado.
2. Explicar brevemente qué cambiará y por qué.
3. Proponer pseudocódigo cuando ayude al aprendizaje.
4. Ayudar con el cambio acotado solicitado, sin reescrituras masivas.
5. Verificar con pruebas relevantes y comunicar qué no pudo comprobarse.
6. Actualizar Swagger, documentación y `AI_USAGE.md` cuando corresponda.

Crear o mantener `AI_USAGE.md` en la raíz del repositorio durante la implementación: herramienta utilizada, fecha, componentes asistidos, tipo de ayuda, decisiones sugeridas, revisión humana y validación realizada. No inventar contribuciones, pruebas, commits ni resultados.

Este archivo es una especificación; su existencia no significa que las funcionalidades descritas ya estén implementadas.

## 2. Problema y objetivo

La organización recibe incidentes de hardware, software, conectividad, aplicaciones y servicios informáticos. Los problemas se repiten y las soluciones históricas no se aprovechan adecuadamente.

La aplicación debe centralizar incidentes, administrar conocimiento verificado, analizar información histórica y ofrecer orientaciones respaldadas por documentos mediante embeddings, búsqueda semántica y RAG.

El análisis de datos es un módulo central, no un añadido de contadores. La IA no debe inventar soluciones ni cifras del dashboard.

## 3. Arquitectura que debe conservarse

### 3.1 Responsabilidades

| Componente | Tecnología | Responsabilidad |
|---|---|---|
| Frontend | HTML, CSS, JavaScript | Formularios, consultas, reportes y gráficos interactivos |
| Backend | Java y Spring Boot | API REST, autenticación, autorización, validaciones, reglas de negocio, persistencia y contratos con n8n |
| Base relacional | PostgreSQL en la base actual | Fuente de verdad de usuarios, incidentes, artículos, consultas, respuestas y análisis |
| Automatización | n8n | Orquestar indexación y asistencia RAG |
| Base vectorial | Qdrant | Vectores y metadatos para recuperar artículos similares |
| Modelos IA | OpenRouter o proveedor indicado por el trainer | Embeddings y generación fundamentada |
| Analítica | SQL y gráficos JavaScript | Calcular y representar indicadores verificables |
| Infraestructura | Docker Compose | Ejecutar servicios de manera reproducible |

El enunciado admite PostgreSQL o MySQL. **Decisión propuesta:** conservar PostgreSQL para aprovechar la base existente; no mantener dos implementaciones de persistencia sin necesidad.

### 3.2 Límites obligatorios

- Todas las consultas inteligentes entran primero por Spring Boot.
- El frontend nunca consume directamente n8n, Qdrant ni OpenRouter.
- Spring Boot coordina el acceso a datos y a n8n; n8n coordina embeddings, recuperación y generación.
- Qdrant es un índice derivado; la base relacional decide si un artículo existe y puede utilizarse.
- No trasladar reglas de autorización al frontend ni confiar en campos enviados por este para identificar al usuario.
- No convertir el proyecto en microservicios, agregar otro backend o cambiar a Spring AI como sustitución de n8n sin una decisión explícita del equipo.
- No introducir Kafka, Redis, CQRS, event sourcing o frameworks nuevos para resolver problemas que la arquitectura actual puede manejar.

### 3.3 Organización Java

Directorio backend: `backend/` (antes `spring-security-user-role-management/`; renombrado al reorganizar el repositorio el 11/11/2026).
Paquete raíz observado: `com.roles.usermanagement`.

Conservar la organización actual y extender los módulos con sus capas:

| Ubicación | Uso |
|---|---|
| `web/config` | Spring Security, JWT, CORS, configuración transversal |
| `web/controller` | Controladores existentes de autenticación y usuarios, excepciones |
| `domain/service`, `domain/dto` | Contratos y servicios existentes de usuarios y seguridad |
| `persistance` | Persistencia existente de usuarios; mantener nombre mientras no haya una refactorización acordada |
| `modules/ticket` | Incidentes |
| `modules/assignment`, `modules/lifecycle`, `modules/statushistory` | Asignación, transiciones e historial |
| `modules/comment`, `modules/technician` | Comentarios y técnicos |
| `modules/dashboard` | Indicadores y consultas analíticas |
| `modules/category` — nuevo | Categorías relacionales |
| `modules/knowledge` — nuevo | Artículos y estado de indexación |
| `modules/assistance` — nuevo | Consultas IA, respuestas, fuentes y calificaciones |
| `integration/n8n` — nuevo | Cliente HTTP, DTO de integración y traducción de errores externos |

En módulos nuevos usar `controller`, `service`, `repository`, `entity`, `dto` y `mapper` según necesidad, sin crear clases vacías por formalidad.

Controlador: HTTP, validación de entrada y delegación. Servicio: reglas y transacciones. Repositorio: acceso a datos. Mapper: transformación sin decisiones de autorización. DTO: contrato público, separado de las entidades JPA.

Mantener Gradle y el wrapper. La configuración base declara Java 21, Spring Boot 4.1.1, Spring Security, JPA, PostgreSQL, Auth0 Java JWT, MapStruct, Lombok y springdoc. Estos son valores observados, no una certificación de compatibilidad. Verificar resolución y compilación antes de actualizar dependencias; no cambiar a Maven ni degradar Java para adaptarse a instalaciones antiguas.

## 4. Inventario y correcciones de la base

### 4.1 Componentes reutilizables

- Login mediante usuario o correo; firma y validación JWT en `JwtUtil` y `JwtFilter`.
- BCrypt, carga de permisos desde base de datos y verificación de cuenta bloqueada/deshabilitada.
- Roles `USER`, `TECHNICIAN`, `ADMIN`; permisos de rol e individuales.
- Tickets con solicitante, técnico, título, descripción, categoría, prioridad, estado y fechas.
- Filtros mediante Specifications, DTO, mappers y comprobaciones de acceso por incidente.
- Historial de estados, asignaciones y comentarios públicos/internos.
- Transacciones y bloqueo de escritura para determinadas operaciones de transición/asignación.
- Swagger, manejo centralizado de varias excepciones y pruebas de JWT e integración.

### 4.2 Cambios identificados y criterio de aceptación

| ID | Hallazgo observado | Cambio requerido o propuesto | Comprobación |
|---|---|---|---|
| B01 | Credenciales y secreto JWT con valores predeterminados; perfil `dev` predeterminado | Exigir secretos por entorno; separar perfiles y no arrancar un despliegue con credenciales de demostración | Arranque rechaza configuración obligatoria ausente; secretos no están en Git |
| B02 | Login exige patrón de contraseña, cambio solo exige longitud | Política uniforme al establecer claves; login autentica sin imponer nuevamente la política de creación | Toda clave aceptada al cambiarla permite autenticarse |
| B03 | `mustChangePassword` se devuelve, pero no restringe la API | Restringir en backend la cuenta con cambio pendiente a las operaciones mínimas de sesión y cambio | Llamada directa a tickets es rechazada hasta cambiar la clave |
| B04 | JWT de 15 días por defecto; cambio de clave no invalida tokens | Access token corto configurable; invalidación por versión de credenciales o mecanismo equivalente | Token anterior deja de servir tras cambio/restablecimiento |
| B05 | Asignación implementada en dos servicios con auditoría diferente | Un caso de uso compartido con validaciones y registro atómicos | Ambos endpoints, si se conservan, producen el mismo resultado e historial |
| B06 | Categoría modelada como enum con alias | Crear entidad Categoria y migración explícita de valores existentes | Todos los tickets conservan categoría válida y no aparecen duplicados por idioma |
| B07 | Usuario identificado por username; sin nombre ni área | Añadir nombre y área; evaluar ID técnico con migración de todas las FK | No se pierden usuarios ni asociaciones históricas |
| B08 | Sin `resolvedAt`; solo creación/actualización e historial | Añadir fecha de resolución y derivarla del evento RESOLVED para datos previos | Duración coincide con el historial y nunca con una edición posterior |
| B09 | Dashboard solo cuenta estados | Implementar los siete indicadores y filtros reales | API y SQL independiente coinciden |
| B10 | Reglas de usuarios concentradas en `UserRepository` | Trasladar gradualmente lógica de negocio a servicios preservando contratos | Pruebas de registro, roles y contraseñas conservan comportamiento esperado |
| B11 | Listados completos sin paginación en tickets | Incorporar paginación, límite máximo y orden estable | No se cargan todos los registros para cada consulta |
| B12 | Reglas de rol y permisos individuales conviven | Definir una política efectiva única y aplicarla en servicio/controlador | Un permiso aislado no elude propiedad ni restricciones del rol |

Priorizar B01–B05 y estabilizar las pruebas antes de añadir IA. B07 no obliga a cambiar inmediatamente la clave primaria: si se introduce `Long id`, hacerlo con migración y compatibilidad, no mediante recreación destructiva de tablas.

En la implementación base, solo `USER` crea tickets; el técnico lista sus asignados. La matriz siguiente conserva estas reglas como punto de partida. No ampliar la creación de tickets a otros roles sin una decisión explícita del equipo.

## 5. Roles y reglas de autorización

**Decisión propuesta:** cada usuario tiene exactamente un rol. Conservar el esquema existente de permisos, evitando ampliarlo con combinaciones que no se necesitan.

| Acción | USER | TECHNICIAN | ADMIN |
|---|---|---|---|
| Registrar incidente | Sí, a su nombre | No por defecto | No por defecto |
| Consultar incidente e historial | Propios | Asignados | Todos |
| Editar título, descripción, categoría y prioridad | Propio, OPEN y sin técnico | No | No por defecto |
| Asignar/reasignar | No | No | Sí, según estado |
| Iniciar atención y resolver | No | Solo asignados | No por defecto |
| Confirmar cierre | Solo propio resuelto | No | No por defecto |
| Solicitar asistencia IA | Propios no cerrados | Asignados no cerrados | Cualquier incidente no cerrado |
| Consultar respuestas | Según acceso al incidente | Según acceso al incidente | Todas |
| Calificar | Respuestas a consultas propias | Respuestas a consultas propias | Respuestas a consultas propias |
| Consultar artículos publicados | Sí | Sí | Sí |
| Crear/editar borradores técnicos | No | Propios | Todos |
| Verificar, activar/desactivar artículos | No | No | Sí |
| Consultar dashboard personal | Propios | Asignados | Global |
| Consultar analítica global | No | No por defecto | Sí |
| Gestionar usuarios y roles | No | No | Sí |

Las restricciones de edición/creación conservan el flujo actual. Si se habilitan excepciones administrativas, documentar motivo, auditoría y pruebas: ADMIN no debe ser un bypass implícito en todos los servicios.

Reglas transversales:

- Identificar al actor desde Spring Security, nunca desde un `username` del cuerpo.
- Verificar autenticación, permiso y alcance del recurso en cada operación, incluidos historial, comentarios, IA y calificaciones.
- Autorregistro crea únicamente USER; no aceptar rol, permisos, bloqueo o verificación de artículos desde ese contrato.
- Un usuario deshabilitado o bloqueado no puede operar aun teniendo un token previamente emitido.
- No borrar físicamente usuarios vinculados a incidentes o auditorías: deshabilitarlos.
- Impedir dejar al sistema sin un administrador activo mediante cambios de rol o deshabilitación.
- Cambio de rol técnico exige resolver o reasignar sus tickets activos; mantener consistente el perfil técnico.
- Un perfil técnico existente no prueba por sí solo que el usuario siga teniendo rol TECHNICIAN.
- Permisos adicionales no deben habilitar ascenso de privilegios, restablecer claves administrativas ni saltar la matriz sin una regla explícita.
- Para recursos inexistentes usar 404; para existentes fuera del alcance, conservar 403 de la base sin revelar sus datos.

## 6. Gestión de incidentes — RF01

### 6.1 Campos y validaciones

Obligatorios: título, descripción, categoría, prioridad, estado, fecha de creación y usuario responsable. Conservar solicitante y técnico como relaciones distintas.

Propuesta de límites: título 1–150 caracteres tras trim; descripción 1–10.000; categoría existente y activa; prioridad entre BAJA, MEDIA, ALTA y CRITICA. No admitir títulos/descripciones compuestos solo por espacios. Ajustar DTO, esquema y Swagger en conjunto.

El servidor establece solicitante, OPEN y fecha de creación. El técnico es nullable mientras está OPEN. No aceptar estado, asignado, fechas o autor como atajos en el DTO de creación.

### 6.2 Máquina de estados conservada

| Estado actual | Acción | Estado siguiente | Actor |
|---|---|---|---|
| OPEN | ASSIGN | ASSIGNED | ADMIN |
| ASSIGNED | REASSIGN | ASSIGNED | ADMIN, a otro técnico |
| ASSIGNED | START_PROGRESS | IN_PROGRESS | Técnico asignado |
| IN_PROGRESS | RESOLVE | RESOLVED | Técnico asignado |
| RESOLVED | CONFIRM_CLOSE | CLOSED | Solicitante |

- Se conservan cinco estados: el enunciado exige al menos cuatro, no exactamente cuatro.
- No permitir saltos, reapertura, cancelación ni reasignación en otros estados sin ampliar explícitamente el modelo.
- En una asignación verificar usuario activo, rol TECHNICIAN, perfil consistente y disponibilidad permitida. Si se usa disponibilidad, definir AVAILABLE como condición operativa y mantenerla actualizada.
- La carga del técnico debe provenir de tickets ASSIGNED e IN_PROGRESS; evitar contadores persistidos sin sincronización.
- Guardar transición, actor, instante y asignación en una misma transacción. La reasignación exige justificación no vacía.
- Resolver requiere una nota de solución no vacía y fija `resolvedAt`; cerrar fija `closedAt` sin reemplazar `resolvedAt`.
- `updatedAt` es auditoría de edición, no fecha de resolución.
- Conservar bloqueo de escritura y una única implementación del caso de uso para impedir asignaciones concurrentes contradictorias.
- No eliminar incidentes históricos. No crear artículos verificados automáticamente al resolverlos.

### 6.3 Comentarios e historial

Conservar comentarios PUBLIC e INTERNAL. USER solo ve PUBLIC; ADMIN y técnico autorizado pueden ver INTERNAL. No incluir comentarios internos en respuestas, prompts o DTO accesibles al solicitante.

Un ticket CLOSED queda de solo lectura para comentarios y nuevas asistencias; permite consultar historial y calificar respuestas anteriores. Los eventos históricos no se editan ni eliminan desde endpoints ordinarios.

## 7. Modelo relacional objetivo

| Entidad | Campos principales y relaciones |
|---|---|
| Usuario / UserEntity | Identificador, username único, nombre, email único normalizado, área, hash, estado de cuenta, rol, cambio pendiente, versión de credenciales propuesta |
| Categoria | id, nombre único, descripción, activa |
| Incidente / Ticket | id, título, descripción, categoría ManyToOne, prioridad, estado, solicitante ManyToOne, técnico ManyToOne nullable, createdAt, updatedAt, resolvedAt, closedAt, nota de resolución |
| ArticuloConocimiento | id, título, problema, síntomas, causa, solución, autor, verificador, activo, verificado, versión de contenido y fechas |
| ConsultaIA | id, incidente ManyToOne, solicitante ManyToOne, pregunta, estado, createdAt, completedAt, requestId único, errorCode opcional |
| RespuestaIA | id, consulta OneToOne única, texto, fecha; metadatos de modelo y versión del flujo cuando estén disponibles |
| FuenteRespuesta | id, respuesta ManyToOne, artículo ManyToOne, versión del artículo, similitud, orden y evidencia conservada |
| Calificacion | id, respuesta ManyToOne, usuario ManyToOne, puntuación, comentario opcional y fechas |

Conservar las entidades actuales de comentarios, historial, asignaciones, roles, permisos y perfil técnico. No renombrar `Ticket` a `Incidente` por estética: documentar equivalencia y mantener compatibilidad.

Integridad:

- FK no nulas donde la relación sea obligatoria; restricción única de respuesta por consulta.
- Puntuación entera entre 1 y 5 tanto en DTO como en base de datos.
- Una calificación por usuario y respuesta; actualizarla en lugar de duplicarla.
- Evitar cascadas REMOVE desde usuarios, tickets o artículos hacia evidencia histórica.
- Índices útiles: tickets por fecha, categoría, prioridad, estado, solicitante y técnico; consultas por incidente/fecha y requestId; artículos por elegibilidad; fuentes por respuesta; calificaciones por respuesta/usuario.
- Elegir índices compuestos según consultas reales; no indexar indiscriminadamente todos los campos.
- Normalizar alias de categorías y prioridades en la migración; persistir una sola representación por valor.
- Para datos previos, recuperar resolución desde historial; si falta, marcarla como dato incompleto, nunca inventarla.
- Al sembrar fechas históricas, no usar un callback que las sustituya por la hora actual. Conservar la protección del API y usar scripts de carga controlados.

## 8. Base de conocimiento — RF02

Cada artículo contiene título, problema, síntomas, causa y procedimiento de solución, con fechas y estado. Usar dos condiciones explícitas: `active` y `verified`.

Reglas propuestas:

1. Crear artículo como borrador no verificado e inactivo.
2. TECHNICIAN edita sus borradores; ADMIN puede editar cualquier artículo.
3. Verificar solo cuando los campos obligatorios estén completos y el administrador haya revisado la solución. Registrar quién verificó y cuándo.
4. Activar solo artículos verificados. Solo `active=true AND verified=true` puede alimentar el RAG.
5. Cambiar contenido técnico incrementa versión, revoca verificación, desactiva el artículo e invalida su índice anterior.
6. La reactivación requiere revisión y sincronización de la versión vigente.
7. No borrar artículos usados por respuestas; desactivarlos preservando fuentes históricas.
8. Las fuentes históricas conservan versión y evidencia usada, y se muestran como históricas si el artículo cambió o quedó inactivo. No reutilizarlas automáticamente en nuevas consultas.

## 9. Indexación — RF03

Flujo obligatorio: Spring Boot → n8n → embeddings → Qdrant.

El backend expone a n8n únicamente los artículos elegibles necesarios y sus versiones, por un contrato de servicio autenticado. No abrir estos endpoints al público ni entregar credenciales de administrador humano a n8n.

Contrato propuesto por artículo: `articleId`, `contentVersion`, título, problema, síntomas, causa, solución, `contentHash`, elegibilidad y `updatedAt`.

- n8n genera embeddings y realiza upsert en Qdrant con identidad determinista por artículo y fragmento.
- Si el artículo requiere fragmentación, preservar relación de cada fragmento con artículo/versión; retirar fragmentos sobrantes de versiones anteriores.
- Guardar modelo de embedding, dimensión, versión y hash. No mezclar vectores incompatibles en una colección.
- Repetir una indexación sin cambios no debe duplicar puntos.
- Actualizaciones de contenido reemplazan o invalidan vectores anteriores; desactivaciones los eliminan o excluyen de recuperación.
- Mantener estado de sincronización separado del estado editorial: PENDING, INDEXED, ERROR como propuesta.
- Un fallo de indexación no elimina el artículo relacional ni lo marca exitosamente indexado.
- Mantener reintento acotado y una operación de reconciliación para resolver desincronización con Qdrant.
- La exclusión de artículos inactivos debe funcionar incluso antes de que termine la limpieza del índice, mediante validación relacional durante la consulta.

## 10. Búsqueda semántica y asistente — RF04 y RF05

### 10.1 Flujo

1. Spring Boot autentica, autoriza el incidente y valida pregunta/descripción.
2. Persiste ConsultaIA con estado PENDING y requestId único.
3. Envía a n8n datos mínimos y la referencia de la consulta.
4. n8n genera embedding y recupera candidatos en Qdrant.
5. Filtra por umbral y deduplica artículos; valida existencia, elegibilidad y versión vigente con Spring Boot antes de construir el contexto.
6. Si hay evidencia, genera respuesta usando exclusivamente ese contexto.
7. Devuelve resultado estructurado, fuentes y similitudes.
8. Spring Boot vuelve a validar el contrato y las fuentes, persiste el resultado y responde al frontend.

No mantener una transacción de base de datos abierta durante una llamada HTTP a n8n. Persistir PENDING primero y completar el resultado en otra transacción. **Propuesta inicial:** ejecución HTTP síncrona con tiempos límite; conservar consulta persistida para poder consultar el resultado. No introducir procesamiento asíncrono completo hasta necesitarlo.

### 10.2 Recuperación y ausencia de evidencia

- Objetivo obligatorio: recuperar entre tres y cinco soluciones relevantes.
- Configurar objetivo máximo 5 y evaluar un umbral explícito y justificado mediante casos de prueba.
- Nunca completar el número con artículos que no superen el umbral. Si solo existen 1–2 válidos, devolverlos como evidencia limitada; documentar esta excepción operativa con el trainer.
- Cero artículos elegibles implica NO_RESULTS y un mensaje claro de ausencia de información, sin soluciones inventadas.
- Registrar métrica de similitud, umbral aplicado y versión del modelo; un score no es probabilidad de éxito ni porcentaje de certeza.
- Usar la misma familia/modelo/dimensión de embeddings para indexar y consultar.
- Si varios fragmentos pertenecen a un artículo, agruparlos y registrar una regla consistente para su score, por ejemplo máximo de sus fragmentos.

### 10.3 Respuesta fundamentada

La respuesta incluye solución sugerida, pasos recomendados, fuentes identificables y similitudes. Cada referencia debe pertenecer a los documentos recuperados y autorizados.

El prompt indica que los artículos y preguntas son datos, no instrucciones para cambiar reglas, revelar secretos o ejecutar acciones. No ejecutar comandos del contenido recuperado ni usar herramientas con permisos operativos desde el RAG.

La IA no cambia estados, no asigna técnicos y no verifica artículos. Solo orienta. No entregar credenciales, comentarios internos ni datos personales innecesarios al proveedor.

Si una fuente cambia o se desactiva durante la generación, descartar el resultado que la usó y reintentar como máximo una vez con contexto vigente. No basta con quitar la cita y conservar texto generado a partir de ella. Si ya no hay evidencia, terminar NO_RESULTS; si no puede completarse por fallo técnico, ERROR.

Una lista de fuentes devuelta por n8n no demuestra por sí sola que el texto esté fundamentado: validar referencias y evaluar fidelidad con pruebas y revisión humana.

## 11. Contrato Spring Boot ↔ n8n

Definir DTO separados de entidades JPA. Versionar el contrato, por ejemplo `schemaVersion: 1`.

Solicitud propuesta:

- `requestId`, `queryId`, `incidentId`.
- `question`, título y descripción pertinente del incidente.
- `maxResults`, referencia a configuración de umbral y versión de contrato.
- No enviar JWT de usuario, hashes de contraseña ni la entidad Usuario completa.

Respuesta propuesta:

- `requestId`, `queryId`, `status`.
- `answer` y `recommendedSteps` cuando corresponda.
- `sources`: `articleId`, `contentVersion`, `score`, identificadores de fragmentos si aplica.
- `embeddingModel`, `generationModel`, `threshold`, `similarityMetric`, `workflowVersion` cuando estén disponibles.
- `errorCode` seguro en fallos, sin credenciales ni detalles internos del proveedor.

Spring Boot comprueba coincidencia de IDs, estado permitido, tamaño del texto, número de fuentes, scores finitos válidos para la métrica y versiones existentes. Rechazar respuestas malformadas o de una consulta distinta.

Autenticar webhooks/llamadas de servicio con secreto por entorno y permisos mínimos. Configurar URL fija: el cliente no puede elegir el destino HTTP. Definir tiempos de conexión/respuesta, límite de payload y reintentos solo cuando sean seguros.

No repetir ciegamente una generación tras timeout. Usar requestId/idempotencia para evitar consultas y respuestas duplicadas. Si se añade callback, debe estar autenticado, correlacionado y respetar los estados terminales.

## 12. Historial y calificación — RF08

Estados obligatorios, con códigos internos propuestos:

| Código | Etiqueta | Significado |
|---|---|---|
| PENDING | Pendiente | Consulta aceptada, aún sin resultado terminal |
| ANSWERED | Respondida | Respuesta persistida con fuentes válidas |
| NO_RESULTS | Sin resultados | Consulta válida sin evidencia relevante |
| ERROR | Error | Fallo técnico de procesamiento o integración |

Solo permitir PENDING → uno de los tres estados terminales. No confundir indisponibilidad del proveedor con ausencia de conocimiento. Un reintento posterior explícito crea otra consulta relacionada, sin sobrescribir evidencia anterior.

- Persistir pregunta, solicitante, incidente, fecha, estado y resultado o mensaje correspondiente.
- ANSWERED requiere respuesta y al menos una fuente válida; NO_RESULTS no tiene orientación técnica sin respaldo; ERROR conserva código seguro.
- Una consulta terminal no puede recibir una segunda respuesta por reintento o callback tardío.
- Definir timeout total y recuperación de consultas PENDING abandonadas tras reinicio.
- Listar historial por incidente con orden y paginación, sujeto a la misma autorización del ticket.
- Calificación propuesta: solo el solicitante de una consulta ANSWERED puede evaluar su respuesta; puntuación 1–5, comentario opcional, una por respuesta/usuario.
- La calificación mide utilidad percibida; no convierte automáticamente el artículo en verificado ni modifica su similitud.

## 13. Analítica — RF06 y RF07

Todos los valores se calculan desde PostgreSQL mediante consultas agregadas verificables. Nunca pedir al modelo que invente, estime o sustituya los cálculos.

### 13.1 Convenciones propuestas

- Almacenar instantes con criterio UTC y usar America/Bogota para agrupar días/semanas y mostrar filtros de negocio.
- En una migración desde LocalDateTime, determinar la zona real de los registros; no asumir que ya están en UTC.
- Filtro temporal sobre `createdAt`: inicio inclusivo y fin exclusivo. Un fin de fecha de calendario incluye ese día mediante el inicio del día siguiente.
- Todos los indicadores comparten filtros de fecha, categoría y prioridad. Definir esta cohorte en el contrato.
- Considerar resuelto tanto RESOLVED como CLOSED.
- Semana comienza el lunes; grupos diarios/semanales incluyen períodos vacíos con conteo cero.
- Los valores reflejan estado actual de la cohorte, no una reconstrucción histórica del estado al final de cada semana. No presentar esa reconstrucción sin implementarla.

### 13.2 Los siete indicadores obligatorios

| Indicador | Definición reproducible |
|---|---|
| Total de incidentes | COUNT de tickets de la cohorte filtrada |
| Incidentes por categoría | Conteo por categoría y 100 × conteo / total filtrado |
| Tendencia temporal | Conteo de incidentes creados por día o semana |
| Tasa de resolución | 100 × tickets RESOLVED o CLOSED / total filtrado |
| Tiempo promedio de resolución | AVG(resolvedAt − createdAt) entre resueltos con fecha válida; total y por categoría |
| Categoría más recurrente | Categoría o categorías empatadas con mayor conteo |
| Incidentes por prioridad | Conteo y porcentaje por prioridad canónica |

- Total cero: conteos cero y porcentajes cero por convención documentada.
- Sin resoluciones válidas: promedio null y tamaño de muestra cero, no promedio cero.
- Tiempo en horas transcurridas, no horas laborables; no hay calendario de SLA en este alcance.
- Excluir de la duración datos incoherentes y reportar número de registros excluidos para no ocultar mala calidad.
- Redondear únicamente la presentación; documentar que los porcentajes redondeados pueden no sumar exactamente 100.
- Diferenciar claramente resuelto de cerrado: el tiempo de confirmación del usuario no aumenta el tiempo técnico de resolución.
- Si se añade tiempo de primera atención, definirlo aparte como START_PROGRESS − createdAt. No renombrar un promedio como si representara otra métrica.

### 13.3 Contrato y permisos

Extender `modules/dashboard`, conservando los endpoints existentes mientras sus consumidores dependan de ellos. Agregar endpoints analíticos con filtros documentados y DTO con conteos, unidades, fechas, zona, tamaño de muestra y exclusiones.

ADMIN consulta análisis global; paneles personales de USER y TECHNICIAN aplican alcance antes de agregar. Una ampliación a analítica global para técnicos debe ser explícita.

Mantener consultas SQL equivalentes en el informe de análisis. Verificar cada indicador con un conjunto pequeño calculado manualmente y después con el dataset completo. No usar caché de métricas sin invalidación o caducidad y sin comunicar frescura.

## 14. Seguridad y configuración

- JWT validado con firma, algoritmo esperado, issuer, subject y expiración; no confiar solo en decode.
- Duración propuesta del access token: 30 minutos, configurable; no añadir refresh tokens sin diseñar su almacenamiento y revocación.
- Para invalidación, propuesta `credentialVersion`: incluirla al emitir y compararla con la cuenta en cada solicitud; incrementarla atómicamente al cambiar/restablecer contraseña.
- Restringir cuentas con `mustChangePassword=true` en backend. Generar clave temporal aleatoria o exigirla explícitamente; eliminar la contraseña temporal fija.
- BCrypt para contraseñas; nunca devolver hash en DTO ni escribir claves/tokens en logs.
- Unificar validación de claves y límites compatibles con el encoder utilizado; probar registro, edición administrativa, cambio y restablecimiento.
- CORS con orígenes concretos configurables. Mantener stateless con Bearer; si cambia a cookies, reevaluar CSRF antes de reutilizar la configuración.
- Endpoints públicos explícitos: login/registro según política y documentación solo donde corresponda. No ampliar indiscriminadamente rutas públicas.
- Errores de autenticación uniformes y sin distinguir si existe el usuario; control de intentos como endurecimiento propuesto.
- Consultas SQL parametrizadas; nunca concatenar filtros del usuario.
- Variables sugeridas: DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET, JWT_ISSUER, JWT_EXPIRATION, CORS_ALLOWED_ORIGINS, N8N_BASE_URL, N8N_SERVICE_TOKEN y tiempos límite.
- Credenciales de Qdrant/OpenRouter en el servicio que las utiliza, normalmente n8n; no duplicarlas en Spring Boot sin necesidad.
- `.env.example` contiene nombres y placeholders, nunca valores reales. Excluir `.env` y secretos del repositorio.
- PostgreSQL, Qdrant y n8n no deben quedar expuestos públicamente por defecto en Compose.

## 15. Buenas prácticas de implementación y API

- Inyección por constructor; servicios pequeños con una responsabilidad identificable.
- Bean Validation y `@Valid` en DTO; invariantes de dominio en servicios y restricciones en base de datos.
- Separar DTO de creación, actualización y respuesta; impedir asignación masiva de campos sensibles.
- Mantener nombres de código consistentes en inglés y mensajes al usuario en español. Corregir referencias obsoletas a CUSTOMER/pizzas en comentarios y Swagger.
- Transacciones acotadas; lectura readOnly cuando corresponda; no esperar proveedores dentro de transacciones.
- Evitar N+1 con consultas/proyecciones adecuadas y comprobarlo en listados relevantes.
- Paginación propuesta: 20 por defecto, máximo 100, orden por fecha e ID para estabilidad.
- Fechas ISO 8601, unidades explícitas y errores con formato consistente.
- HTTP: 201 creación, 200 consulta/actualización; 400 validación, 401 sin autenticación válida, 403 sin permiso, 404 ausente, 409 conflicto; 502/503/504 para fallos externos según causa.
- NO_RESULTS es un resultado válido de consulta, no un error 500. En error técnico devolver queryId cuando ya se haya persistido la consulta.
- Manejar fallos de seguridad del filtro además de las excepciones de controlador.
- Configurar logs con requestId/queryId y tiempos; no registrar contenido sensible completo por defecto.
- No capturar excepciones para devolver éxito ficticio ni transformar todo fallo en una lista vacía.
- Incorporar migraciones versionadas —propuesta Flyway— para reproducir cambios. Revisar compatibilidad antes de añadir la dependencia. No depender de `ddl-auto=update` como estrategia final.
- Mantener datos existentes; una migración de categorías, usuarios o fechas debe tener prueba de conservación y plan de recuperación.

## 16. Datos iniciales obligatorios

| Conjunto | Mínimo |
|---|---:|
| Incidentes históricos | 150 |
| Artículos técnicos documentados | 25 |
| Categorías | 5 |
| Usuarios | 10 |
| Estados de incidente | 4; la base conserva 5 |
| Niveles de prioridad | 4 |

Preparar datos sintéticos consistentes y documentados, con diferentes fechas, categorías, prioridades, estados y responsables. Incluir casos de conectividad y fallos de ejecución que tengan fuentes relevantes, y consultas fuera de cobertura.

Fechas: creación ≤ asignación ≤ inicio de atención ≤ resolución ≤ cierre, cuando esos eventos existan. Sembrar historial coherente con el estado actual. No crear usuarios o técnicos inexistentes en las FK.

Distribuir los datos para que permitan interpretar tendencias, no solo cumplir el conteo. Incluir artículos elegibles suficientes para probar recuperación de 3–5 soluciones y al menos un artículo para probar desactivación. Cargar datos de demostración por perfil/procedimiento explícito, no al arrancar cualquier despliegue.

## 17. Pruebas y criterios de aceptación

### 17.1 Diez pruebas obligatorias del enunciado

1. Registrar un incidente de conectividad y obtener soluciones RAG relevantes.
2. Consultar error de ejecución de aplicación y comprobar los artículos recuperados.
3. Consultar un caso sin cobertura y verificar ausencia de soluciones inventadas.
4. Filtrar una categoría y contrastar resultados con PostgreSQL.
5. Mostrar evolución semanal y verificar valores mediante SQL.
6. Desactivar un artículo y comprobar que no sea fuente de nuevas respuestas, incluso si su vector aún existe.
7. Enviar descripción vacía y comprobar que no se invoque el flujo inteligente.
8. Simular fallo de embeddings y verificar estado ERROR y respuesta controlada.
9. Consultar historial de asistencias de un incidente con autorización correcta.
10. Calificar una respuesta y comprobar persistencia y rango 1–5.

### 17.2 Pruebas adicionales necesarias

- JWT inválido, expirado, issuer incorrecto; usuario bloqueado; invalidación por cambio de clave.
- Cambio obligatorio no eludible mediante llamadas directas a API.
- USER no consulta tickets ajenos, notas internas ni métricas globales.
- Técnico no resuelve tickets no asignados ni omite IN_PROGRESS.
- Asignación concurrente y equivalencia de rutas de asignación.
- Cambio de contenido invalida verificación e índice; fuente desactivada durante generación invalida respuesta.
- Doble solicitud, respuesta repetida y recuperación de PENDING tras interrupción.
- Indexación repetida sin duplicados y cambio de versión sin fragmentos huérfanos utilizables.
- Un caso con 1–2 fuentes válidas, otro con cero; no rellenar con evidencia irrelevante.
- Scores, IDs o payload malformados de n8n no crean respuestas válidas.
- Calificación ajena, duplicada, fuera de rango y de una consulta no respondida.
- Indicadores con cohorte vacía, empates, límites de día/semana, CLOSED y fechas de resolución incompletas.
- Migraciones conservan relaciones y los datos iniciales cumplen mínimos.

Conservar las pruebas existentes y mejorarlas gradualmente: actualmente algunas dependen de orden y de `ticketId=1`; crear fixtures y recuperar IDs reales para aislar casos.

Usar dobles del cliente n8n para pruebas deterministas y una prueba integrada separada contra workflows reales. Para consultas SQL específicas validar con PostgreSQL; H2 no demuestra equivalencia completa.

Comando de referencia desde el backend: `./gradlew test` o `bash gradlew test`. Ejecutar compilación y pruebas en el entorno del repositorio. No afirmar éxito sin haberlas ejecutado; documentar cualquier bloqueo de dependencias o configuración.

## 18. Etapas de trabajo

| Etapa | Alcance | Condición de salida |
|---|---|---|
| 0. Estabilizar base | Compilar, ejecutar pruebas, inventariar contratos, registrar decisiones | Línea base reproducible y limitaciones documentadas |
| 1. Seguridad | B01–B04, permisos, política de claves | Pruebas de acceso y cambio de claves pasan |
| 2. Dominio | Asignación única, categorías, usuario, fechas, migraciones | Tickets e historial coherentes; migración verificada |
| 3. Conocimiento | CRUD, revisión, elegibilidad y versiones | Solo artículos verificados/activos son publicables |
| 4. Indexación | Contrato n8n, estado de sincronización y reconciliación | Sin duplicados ni versiones inhabilitadas recuperables |
| 5. Asistencia | Consulta, cliente n8n, fuentes, errores e historial | RAG completo con evidencia y estados persistidos |
| 6. Evaluación | Calificaciones y restricciones | Persistencia correcta y sin evaluaciones ajenas |
| 7. Analítica | Siete indicadores, filtros y SQL verificable | Coincidencia con cálculos independientes |
| 8. Integración y entrega | Datos, Compose, pruebas, documentación | Diez pruebas obligatorias y entregables completos |

Acordar los contratos de conocimiento, IA y analítica con el equipo antes de conectar frontend y workflows. La secuencia no impide que otros integrantes trabajen con contratos o mocks, pero no presupone que lo harán.

No concentrar cambios en un commit final. Usar ramas por funcionalidad/corrección, commits progresivos y merges o PR revisables. No fabricar historial ni atribuir trabajo a otros integrantes. Conservar el historial y la licencia del repositorio existente; documentar los cambios realizados en cada etapa.

## 19. Entregables y definición de terminado

Entregables obligatorios del proyecto completo:

- Repositorio GitHub con historial progresivo, ramas e integración verificables.
- Frontend y backend Spring Boot.
- Script SQL con datos iniciales y esquema/migraciones reproducibles.
- Diagramas entidad-relación y arquitectura actualizados.
- Workflows JSON de n8n, sin secretos embebidos.
- Docker Compose y `.env.example`.
- Evidencias de Swagger o Postman.
- Documento de pruebas con resultados reales y limitaciones.
- README de instalación, configuración, ejecución y decisiones.
- Presentación final.
- Informe de análisis con indicadores, SQL utilizado, interpretación y al menos tres conclusiones respaldadas por evidencia.
- `AI_USAGE.md` actualizado.

Un incremento está terminado cuando tiene validaciones, autorización por recurso, persistencia coherente, errores controlados, prueba relevante, contrato documentado y trazabilidad. No confundir un endpoint que responde 200 con una regla de negocio correctamente implementada.

## 20. Decisiones propuestas que el equipo debe ratificar

Esta guía ofrece valores por defecto para trabajar sin ambigüedad, pero estos puntos no provienen literalmente del enunciado:

1. Conservar PostgreSQL, Java/Gradle y módulos actuales.
2. Mantener creación de tickets solo para USER y analítica global solo para ADMIN.
3. Conservar cinco estados y no incorporar reapertura/cancelación por ahora.
4. ADMIN verifica y activa artículos; cambios técnicos revocan su elegibilidad.
5. JWT de 30 minutos e invalidación por versión de credenciales.
6. Consulta RAG síncrona con persistencia previa y tiempos límite, sin arquitectura asíncrona adicional inicialmente.
7. Admitir 1–2 fuentes válidas sin rellenar artificialmente el mínimo; confirmar interpretación con el trainer.
8. Analítica por fecha de creación, resolución incluyendo CLOSED, horas transcurridas y semana de lunes en America/Bogota.
9. Nota obligatoria al resolver, una calificación editable por solicitante/respuesta y ninguna asistencia nueva en CLOSED.
10. Introducción de migraciones y eventual ID técnico del usuario con preservación de contratos.

Antes de cambiar cualquiera, explicar impacto en entidades, endpoints, pruebas y consumidores. No presentar supuestos como decisiones confirmadas por el equipo.
