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

## 21. Estado de implementación (actualizado el 13 de noviembre de 2026)

Registro de lo realizado en la rama `dev/JhonReyes`. Las secciones anteriores siguen siendo la especificación; esta sección indica qué parte ya existe en el código. Actualizarla al cerrar cada incremento. La revisión humana de todo lo listado está pendiente (ver `AI_USAGE.md`).

### 21.1 Listo y verificado

Verificado con `./gradlew test` en H2 y con `backend/scripts/test-postgres.sh` en PostgreSQL 17 (migraciones reales de Flyway y Hibernate en modo `validate`), salvo donde se indica. Última ejecución (13 de noviembre de 2026): 104 pruebas y 0 fallos en ambos (en H2 se omiten las 6 de datos iniciales).

| ID / etapa | Qué quedó implementado | Prueba principal |
|---|---|---|
| B01 | Secretos obligatorios por entorno, perfiles separados, `.env.example` | `JwtPropertiesBindingTests` |
| B02 | `PasswordPolicy` única al establecer claves; el login solo autentica | `PasswordPolicyTests`, `PasswordChangeFlowTests` |
| B03 | Cuenta con cambio de clave pendiente: solo `login`, `change-password` y `me`; el resto responde 403 `PASSWORD_CHANGE_REQUIRED` | `PasswordChangeRequiredTests` |
| B04 | Token de 30 min; claim `cv` comparado con `credential_version`; el cambio o restablecimiento invalida tokens previos; `change-password` devuelve token nuevo; clave temporal aleatoria | `TokenInvalidationTests` |
| B05 | `TicketAssignmentService` es el único caso de uso de asignación; `/transitions` y `/status` delegan en él; listado de técnicos derivado del rol con carga calculada desde tickets | `TicketAssignmentTests` (incluye concurrencia) |
| Migraciones | Flyway: `V1` línea base … `V8`; `ddl-auto=validate`; `baseline-on-migrate` para bases ya existentes | Suite completa en PostgreSQL |
| B06 | `modules/category` (entidad, CRUD de ADMIN, activar/desactivar); `Ticket.category` ManyToOne; prioridad canónica BAJA/MEDIA/ALTA/CRITICA; alias anteriores solo como entrada | `CategoryTests` |
| B07 | `fullName` y `area` opcionales en el usuario; `username` sigue siendo la clave primaria (sin ID técnico) | `UserAccountPolicyTests` |
| B08 | `resolvedAt`, `closedAt`, `resolutionNote`; RESOLVE exige nota; fechas previas derivadas del historial; límites de título (150) y descripción (10.000) tras trim | `TicketResolutionTests` |
| B09 | `GET /api/analytics/summary`: los siete indicadores, filtros de fecha/categoría/prioridad, alcance por rol, serie con ceros, exclusiones reportadas | `AnalyticsTests`; SQL en `docs/backend/ANALITICA_SQL.md` |
| B10 | Reglas de cuentas trasladadas a `UserService` y `UserAccountPolicy`; `UserRepository` conserva validación de datos y persistencia (traslado parcial) | `UserAccountPolicyTests` |
| B11 | `GET /api/tickets` paginado (20 por defecto, máximo 100, orden `createdAt, id`) | `TicketPaginationTests` |
| B12 y §5 | Política efectiva: el rol delimita y el permiso individual solo habilita dentro de ese límite; último administrador activo protegido; técnico con tickets activos no cambia de rol; baja lógica de usuarios; catálogo de roles/permisos solo ADMIN | `UserAccountPolicyTests` |
| §14, intentos de login | `LoginAttemptLimiter`: tras 5 fallos seguidos de un mismo identificador en 15 min, el login responde 429 `TOO_MANY_LOGIN_ATTEMPTS` con `Retry-After` durante 15 min, exista o no la cuenta; un login correcto reinicia la cuenta; valores configurables (`LOGIN_MAX_ATTEMPTS`, `LOGIN_ATTEMPT_WINDOW`, `LOGIN_LOCK_DURATION`); estado en memoria | `LoginAttemptLimitTests` |
| Errores | 401 sin autenticación válida, 403 sin permiso, 400 para parámetros inválidos, 500 controlado; respuestas JSON uniformes | Suite completa |
| Etapa 3 (RF02) | `modules/knowledge`: borrador → verificado → activo; solo `active AND verified` es elegible; un cambio de contenido incrementa versión, revoca verificación y desactiva; sin borrado | `KnowledgeArticleTests` |
| Etapa 4 (RF03) | `integration/n8n` (`HttpN8nClient` con URL fija, secreto en cabecera, tiempos límite y tope de respuesta; contrato v1; `IntegrationTokenFilter`); `KnowledgeIndexService` sincroniza fuera de transacción con estados `NOT_INDEXED`/`PENDING`/`INDEXED`/`ERROR`, reindexar y reconciliar; `/api/integration/knowledge/eligibility` y `/eligible` para n8n con `X-Integration-Token`; `spring.jpa.open-in-view=false` | `KnowledgeIndexTests` (con `FakeN8nClient`; no ejecutado contra un n8n real) |
| Etapa 5 (RF04, RF05) | `modules/assistance`: `POST /api/tickets/{id}/assistance` persiste la consulta en `PENDING`, llama a n8n sin transacción abierta y fija `ANSWERED`, `NO_RESULTS` o `ERROR`; `AssistResponseValidator` rechaza respuestas que no cumplen el contrato v1; las fuentes se contrastan con la base relacional (existencia, elegibilidad, versión, modelo de embeddings); un único reintento por fuentes obsoletas; evidencia conservada por fuente; una sola consulta en proceso por usuario e incidente, `requestId` idempotente y reintento explícito enlazado; `AssistanceRecovery` cierra las `PENDING` abandonadas; historial paginado por incidente; errores 502/503/504 con `queryId`; migración `V7` | `AssistanceTests` (con `FakeN8nClient`; no ejecutado contra un n8n real) |
| Etapa 6 (RF08) | `PUT /api/assistance/queries/{id}/rating`: solo quien hizo una consulta `ANSWERED` la califica (entero 1–5, comentario opcional); una por respuesta y usuario, repetir la actualiza; admitida con el incidente cerrado; rango y unicidad también en la base (`V8`); la calificación aparece en la consulta y en el historial | `AssistanceRatingTests` |
| Etapa 8 (parte del backend) | Datos iniciales deterministas por script explícito (`db/seed/demo_data.sql`: 13 usuarios, 31 artículos, 180 incidentes con historial); README raíz y del backend; contratos para el frontend y para n8n; diagramas de arquitectura y entidad-relación; informe de análisis con cifras reales del conjunto; documento de pruebas; OpenAPI exportado | `DemoDataSeedTests` (solo PostgreSQL), `OpenApiDocumentationTests`, `CommentVisibilityTests` |
| Integración n8n, Qdrant y Compose | Flujos `n8n/Flujo-1-Indexacion-Conocimiento.json` y `n8n/Flujo-2-Asistencia-RAG.json` ajustados al contrato v1 (un punto por artículo, umbral y máximo tomados de cada solicitud, contexto armado con lo que devuelve el endpoint de elegibilidad, códigos de error propios, sin secretos en el JSON); `docker-compose.yml` con secretos obligatorios, puertos en `127.0.0.1` e imágenes con versión fija; `qdrant/setup_qdrant.sh` idempotente; `n8n/start-n8n.sh` importa y publica los flujos; `HttpN8nClient` fijado en HTTP/1.1 | Prueba integrada manual con n8n 2.47.7, Qdrant 1.20.1 y modelos reales de OpenRouter (`docs/backend/PRUEBAS.md` §8) |

### 21.2 En curso, sin terminar

Nada en curso.

### 21.3 Pendiente

Del backend:

- Evaluar la fidelidad de las respuestas con más casos y revisión humana: la prueba con modelos reales del 13 de noviembre cubrió las diez pruebas obligatorias y pocas repeticiones.
- Recalibrar `ASSIST_MIN_SCORE` (0,55) con consultas reales de usuarios o si cambia el modelo de embeddings: se calibró con 25 consultas redactadas para la prueba.
- Migraciones sobre una copia de la base de desarrollo existente (solo se han ejecutado sobre bases vacías).
- Capturas de Swagger con los endpoints nuevos (la especificación ya está exportada en `docs/backend/openapi.json`).
- Hallazgos abiertos de la auditoría (§22.4).
- Revisión humana de todo lo listado en 21.1 y ratificación de las decisiones de 21.4.

De otros integrantes o del equipo (fuera del alcance de esta guía): frontend y presentación final.

### 21.4 Decisiones tomadas durante la implementación

Añadidas a las del §20; requieren ratificación del equipo.

1. **Fechas:** se conservan como hora local sin zona en `America/Bogota` (`APP_TIME_ZONE`, fijada al arrancar) en lugar de migrar a UTC, porque los registros existentes ya están en esa hora. Difiere de la propuesta del §13.1.
2. **Categorías:** nombres de catálogo en español (REDES/NETWORK → Conectividad); el valor original queda en `tickets.category_legacy` como respaldo.
3. **Usuarios:** nombre y área opcionales para no inventar datos de cuentas previas; `DELETE /api/user/delete/{name}` deshabilita la cuenta en todos los casos.
4. **Asignación:** conflicto de estado responde 409 y técnico inválido 400; el rol, no el perfil técnico, decide quién es asignable; `active_tickets_count` deja de usarse.
5. **Índice vectorial:** cuatro estados de sincronización en lugar de los tres propuestos; indexación por envío desde Spring Boot a n8n con un intento automático y reintentos explícitos.
6. **Secretos de integración separados:** `N8N_SERVICE_TOKEN` (Spring Boot → n8n) e `INTEGRATION_API_TOKEN` (n8n → Spring Boot).
7. **Open-Session-In-View desactivado** (`spring.jpa.open-in-view=false`): cada transacción usa su propio contexto de persistencia; las relaciones LAZY se leen dentro del servicio.
8. **Asistente:** la pregunta es opcional (por defecto, la descripción del incidente); una sola consulta en proceso por usuario e incidente; `requestId` idempotente opcional y reintento explícito enlazado (`retryOfQueryId`); el contrato v1 solo admite similitud coseno; los metadatos de recuperación se guardan en la consulta (también en `NO_RESULTS`) y el modelo de generación en la respuesta, a diferencia de lo propuesto en el §7.
9. **Evidencia de las fuentes:** cada fuente guarda una copia del título y del contenido usado, porque los artículos no conservan versiones anteriores.
10. **Errores del asistente:** 502, 503 o 504 según la causa, con `queryId`; una consulta `PENDING` se da por abandonada a los 5 minutos (`ASSIST_PENDING_TIMEOUT`).
11. **Calificaciones:** `ADMIN` no califica consultas ajenas; la puntuación se recibe como número para rechazar decimales en lugar de truncarlos.
12. **Datos iniciales:** sintéticos y deterministas, con un pico de conectividad y un crecimiento gradual diseñados; usuarios de demostración con contraseña pública (`Demo.2026`) y sin cambio obligatorio, cargados solo por script explícito. El conjunto no incluye consultas al asistente ni calificaciones.

13. **Control de intentos de login:** por identificador enviado (usuario o correo, sin distinguir mayúsculas) y en memoria, sin tabla ni dependencia nueva. Se reinicia con la aplicación, no se comparte entre instancias, y usuario y correo de una misma cuenta llevan contadores separados. No modifica el campo `locked` de la cuenta. Un tercero puede bloquear temporalmente el acceso de otra persona fallando a propósito con su identificador.
14. **Migración de roles al arrancar eliminada:** `UserRoleSchemaMigration` ejecutaba `db/postgresql/single-role-and-user-permissions.sql` en cada arranque, fuera de Flyway. Sus tablas y su restricción única ya están en `V1`, y a una cuenta sin rol le asignaba `CUSTOMER`, que no es un rol del sistema. Flyway queda como único mecanismo de cambio de esquema.

15. **Flujos de n8n y Compose corregidos por el responsable del backend:** a petición explícita suya (13 de noviembre de 2026) se fusionó `test` en `dev/Json` y se reescribieron los flujos, el Compose y la inicialización de Qdrant de esa rama para que cumplan el contrato. Es trabajo sobre componentes de otro integrante; debe revisarlo su autor. Se eliminó `database/seed_data.sql` porque no carga en el esquema actual (`user_role.granted_date` es obligatorio) y duplicaba `db/seed/demo_data.sql`.
16. **Un punto por artículo en Qdrant:** sin fragmentación; el identificador del punto es el del artículo y el payload no guarda el contenido, solo `articleId`, `contentVersion`, `contentHash`, `chunkIndex`, título y modelo.
17. **HTTP/1.1 hacia n8n:** el cliente de Java pide por defecto actualizar a HTTP/2 sin TLS y n8n no responde a esa petición.

18. **Umbral de similitud 0,55:** sustituye al 0,70 inicial. Sale de medir 25 consultas con `openai/text-embedding-3-small` (`docs/backend/PRUEBAS.md` §9): el artículo correcto más bajo obtuvo 0,594 y la consulta sin cobertura más alta 0,506. Con ese umbral la media es de 1,3 fuentes por consulta; no se baja para alcanzar tres porque entrarían artículos de temas cercanos. Las pruebas automáticas fijan 0,70 en su perfil para no depender del valor de producción.
19. **Generación acotada:** el flujo envía `max_tokens` (1500 por defecto) y temperatura 0, y el prompt prohíbe añadir causas o explicaciones que no estén en los artículos y exige responder «sin información suficiente» cuando solo hay artículos de temas cercanos.

### 21.5 Cambios de contrato para el frontend y n8n

- `GET /api/tickets` devuelve `{content, page, size, totalElements, totalPages}`.
- En tickets, `category` es el nombre y `categoryId` el identificador; la creación acepta `categoryId` o `category`.
- Resolver exige `resolutionNote`; reasignar por `/transitions` exige `justification`.
- Sin token: 401. Cambio de clave pendiente: 403 con `code=PASSWORD_CHANGE_REQUIRED`.
- `POST /api/auth/change-password` devuelve un `token` nuevo; el anterior deja de servir.
- Compose, Qdrant y workflows de n8n pertenecen a otro integrante (rama `dev/Json`); el backend solo define el contrato.
- Asistente: `POST` y `GET /api/tickets/{id}/assistance`, `GET /api/assistance/queries/{id}`, `PUT /api/assistance/queries/{id}/rating`. Contrato en `docs/backend/API_CONOCIMIENTO_ASISTENTE_ANALITICA.md`.
- n8n: contrato v1 de indexación y asistencia, y endpoints `/api/integration/knowledge/**`, en `docs/backend/CONTRATO_N8N.md`.
- `POST /api/auth/login` puede responder 429 con `code=TOO_MANY_LOGIN_ATTEMPTS`, `retryAfterSeconds` y la cabecera `Retry-After`; el frontend debe mostrar la espera en lugar de tratarlo como credenciales incorrectas.

## 22. Auditoría del backend (13 de noviembre de 2026)

Revisión del código de la rama `dev/JhonReyes` frente al enunciado (`docs/requerimientos/requerimientosSpringBoot.md`) y a esta guía. La hizo la misma herramienta de IA que asistió la implementación, de modo que no sustituye la revisión del responsable ni la de un tercero. Método: lectura del código de seguridad, configuración y migraciones; búsqueda de secretos y referencias obsoletas en el repositorio; ejecución de las dos suites; arranque real con el perfil `dev` sobre una base vacía con los datos de demostración (`docs/backend/PRUEBAS.md` §7).

### 22.1 Cobertura de los requerimientos funcionales

| Requisito | Estado en el backend | Qué falta para darlo por cumplido en el proyecto |
|---|---|---|
| RF01 Incidentes | Implementado: registro, consulta, filtros, paginación y máquina de estados | Frontend |
| RF02 Base de conocimiento | Implementado: ciclo borrador → verificado → activo; solo `active AND verified` es fuente | Frontend; revisión humana del contenido de los 31 artículos |
| RF03 Embeddings | Implementado: flujo de n8n con OpenRouter y Qdrant, estados de sincronización y endpoints para n8n; 28 artículos indexados con el modelo real | Nada pendiente |
| RF04 Búsqueda semántica | Implementado: hasta 5 resultados, umbral configurable de 0,55 justificado con la calibración de `PRUEBAS.md` §9 | Recalibrar con consultas reales de usuarios |
| RF05 Asistente RAG | Implementado y probado con el modelo real: respuesta con fuentes y similitudes, `NO_RESULTS` sin texto inventado | Revisión humana de la fidelidad con más casos; frontend |
| RF06 Análisis de datos | Implementado: siete indicadores en SQL, contrastados con cálculo manual y con el conjunto de 180 incidentes | Nada del backend |
| RF07 Dashboard | Implementada la API con filtros de fecha, categoría y prioridad | Los gráficos interactivos son del frontend |
| RF08 Historial y evaluación | Implementado: consulta, respuesta, fuentes, estado, fecha y calificación 1–5 | Frontend |

Reglas de negocio del enunciado (§11): las consultas inteligentes solo entran por Spring Boot; las fuentes se validan contra la base relacional; los cuatro estados existen; los secretos se leen del entorno. La búsqueda en el repositorio no encontró claves ni credenciales reales versionadas: `backend/.env.example` solo contiene marcadores, y la contraseña `Demo.2026` de los usuarios de demostración es pública a propósito (decisión 12 de §21.4).

### 22.2 Entregables

| Entregable | Estado |
|---|---|
| Repositorio con historial progresivo | La rama `dev/JhonReyes` está publicada, con más de 20 commits por delante de `main`. No hay merge ni pull request hacia `main` todavía, y el enunciado los evalúa |
| Backend Spring Boot | Hecho |
| Script SQL de datos iniciales y migraciones | Hecho: `db/migration/V1`–`V8` y `db/seed/demo_data.sql` |
| Diagramas entidad-relación y arquitectura | Hechos en Mermaid (`docs/backend/DIAGRAMAS.md`); no se ha comprobado cómo se muestran |
| `.env.example` | Hecho para el backend; el del proyecto completo depende del Compose |
| Evidencias de Swagger o Postman | Parcial: `docs/backend/openapi.json` exportado; faltan capturas de los endpoints nuevos |
| Documento de pruebas | Hecho: `docs/backend/PRUEBAS.md` con resultados reales y limitaciones |
| README | Hecho: raíz y `backend/` |
| Informe de análisis de datos | Hecho sobre datos sintéticos: `docs/backend/INFORME_ANALISIS.md` |
| `AI_USAGE.md` | Al día; toda la columna de revisión humana sigue en «Pendiente» |
| Workflows JSON de n8n y Docker Compose | Hechos en `n8n/` y `docker-compose.yml`; probados con modelos reales de OpenRouter |
| Frontend y presentación | De otros integrantes o del equipo; no están en el repositorio |

### 22.3 Hallazgos corregidos en esta auditoría

| Hallazgo | Corrección |
|---|---|
| `UserRoleSchemaMigration` ejecutaba un script SQL en cada arranque sobre PostgreSQL, fuera de Flyway y con un bloqueo de tabla. A una cuenta sin rol le asignaba `CUSTOMER`, que no es uno de los roles del sistema (`USER`, `TECHNICIAN`, `ADMIN`) | Eliminados la clase y el script; las tablas y la restricción única que creaba ya están en `V1` (decisión 14 de §21.4) |
| El control de intentos de login estaba pendiente (§14) | `LoginAttemptLimiter` (decisión 13 de §21.4) |
| El arranque con el perfil `dev` nunca se había ejecutado | Comprobado manualmente; resultados en `PRUEBAS.md` §7 |
| `docs/backend/openapi.json` no reflejaba la descripción actual del login | Regenerado desde la aplicación |

### 22.4 Hallazgos abiertos

Ordenados por importancia para la entrega.

1. **Modo de uso de la IA.** El enunciado y el §1.1 piden tutoría técnica y ejemplos parciales, no generación integral. La mayor parte del código del backend de esta rama fue generada por la IA a petición explícita del responsable; `AI_USAGE.md` lo registra así y ninguna entrada tiene revisión humana. Es el principal riesgo frente a los criterios de evaluación: el responsable debe revisar el código, poder explicarlo y completar la columna de revisión con lo que realmente haya revisado.
2. **Sin integración a `main`.** El 13 de noviembre se creó la rama `test` desde `main` y se abrió hacia ella el pull request n.º 1 con `dev/JhonReyes`, aún sin fusionar; falta también el paso de `test` a `main`. `dev/Json` no tiene pull request: GitHub no lo admite entre historias sin relación (§22.5).
3. **Fidelidad del asistente evaluada con pocos casos.** El 13 de noviembre las diez pruebas obligatorias se ejecutaron con modelos reales de OpenRouter y dieron el resultado esperado (`docs/backend/PRUEBAS.md` §8). Con el primer prompt el modelo añadió una vez información que no estaba en los artículos; se corrigió, pero la comprobación posterior son pocas repeticiones.
4. **Umbral calibrado con un conjunto pequeño.** 0,55 está justificado con 25 consultas (`docs/backend/PRUEBAS.md` §9), todas redactadas para la prueba y sobre artículos sintéticos.
5. **Código sin uso en `modules/dashboard`.** `DashboardCacheMetric`, `MetricFilterRequest` y `MetricPeriod` no tienen referencias; la tabla `dashboard_metrics_cache` existe en `V1` y queda vacía. No se eliminaron porque retirar la entidad exige decidir qué hacer con la tabla.
6. **Validación manual en `AuthController`.** Registro, login y cambio de clave validan con `if` y responden `{"error": ...}`, distinto del formato de `ApiExceptionHandler` (`timestamp`, `status`, `error`, `message`). Funciona y está probado, pero se aparta del §15 (Bean Validation, errores con formato consistente). Cambiarlo altera el contrato que ya consume el frontend.
7. **Swagger público en todos los perfiles.** `/swagger-ui.html` y `/v3/api-docs` no piden autenticación tampoco fuera de `dev`. Útil para la evidencia del proyecto; en un despliegue real convendría desactivarlos con las propiedades de springdoc (`springdoc.api-docs.enabled`, `springdoc.swagger-ui.enabled`), algo que no se ha probado aquí.
8. **B07 y B10 parciales.** `username` sigue siendo la clave primaria (el enunciado lista `id` para Usuario) y parte de la validación de usuarios sigue en `UserRepository`.
9. **Documentos anteriores sin revisar.** `AUDITORIA_CAMPUSDESK_TECHNOVA.md`, `CAMPUSDESK_JPA_ENTIDADES_ENUMS.md`, `ENDPOINTS_AUTH_LOGIN.md`, `ENDPOINTS_FRONTEND_PENDIENTES.md` y `PLANTILLA_MODULOS.md` de `docs/backend` son del 11 de noviembre y describen la base anterior a las migraciones, las categorías relacionales y los módulos nuevos; pueden contradecir el estado actual (por ejemplo, `PLANTILLA_MODULOS.md` dice que Hibernate crea las tablas y tiene caracteres mal codificados).
10. **Migraciones sin probar sobre datos previos.** Solo se han ejecutado sobre bases vacías. Tras eliminar el script de arranque, una base de desarrollo anterior a Flyway debe tener ya la restricción única `uk_user_role_username`; lo esperable es que la tenga, porque ese script corría en cada arranque, pero no se ha comprobado sobre una copia real.
11. **Límites del control de intentos.** Los descritos en la decisión 13 de §21.4.
12. **`AGENTS.md` sin versionar en la raíz.** Era una copia idéntica de este archivo antes de esta actualización; no se modificó ni se añadió al repositorio, así que ya no coincide con `CLAUDE.md`.

### 22.5 Revisión de la rama `dev/Json` (13 de noviembre de 2026)

`dev/Json` es un único commit sin historia común con `main`. Contiene una copia del backend en el estado del commit `a8d88fa`, un segundo módulo de asistencia con su propia migración `V7`, los dos flujos de n8n, la configuración de Qdrant, `docker-compose.yml`, un `Dockerfile` y otro conjunto de datos iniciales. Por no compartir historia, GitHub no permite abrir un pull request de `dev/Json` hacia `test` ni hacia `main`. Se probó una rama puente (`integration/n8n-json`, pull request n.º 2) y se eliminó el mismo día por decisión del responsable; para integrar este trabajo, su autor debe rehacer la rama partiendo de `test`. El módulo de asistencia y la migración `V7` de `dev/Json` duplican los existentes y no deben entrar.

Resultado de la revisión por lectura frente a `docs/backend/CONTRATO_N8N.md` (detalle completo en la descripción del pull request n.º 2, ya cerrado): tal como están, no funcionan con el backend.

- Los dos flujos leen campos que el backend no envía (`operation`, `version`, `content`, `prompt`, `title`) y devuelven respuestas sin `schemaVersion`, `requestId` ni los nombres del contrato, de modo que el backend las rechaza.
- El flujo de asistencia usa `limit: 3` y umbral 0,60 fijos en lugar de `maxResults` y `minScore`, llama al endpoint de elegibilidad con un cuerpo distinto del esperado y arma el cuerpo de la llamada al modelo con una concatenación que n8n envía como texto literal.
- El Compose pasa al backend `DB_HOST`, `DB_NAME` y `DB_USER` en lugar de `DB_URL` y `DB_USERNAME`, y asume el puerto 8080 cuando la API escucha en 8051.
- `setup_qdrant.sh` espera un texto de `/readyz` que Qdrant 1.20.1 no devuelve (comprobado en ejecución), así que la colección no llega a crearse.
- Hay secretos con valor por defecto en el Compose y en los flujos, y servicios publicados en todas las interfaces.

Esa revisión se hizo por lectura. Después, a petición del responsable, se fusionó `test` en `dev/Json` y se corrigieron en esa rama todos los puntos anteriores (decisiones 15 a 17 de §21.4); el resultado se probó de extremo a extremo con el proveedor de modelos simulado.

### 22.6 No auditado

- Frontend: no está en ninguna de las ramas revisadas.
- Rendimiento y carga: no se midieron tiempos de las consultas analíticas ni concurrencia del asistente más allá de las pruebas de asignación concurrente.
- Dependencias: no se analizaron vulnerabilidades conocidas de las bibliotecas.
- Lectura línea a línea de los módulos anteriores a esta rama (`ticket`, `comment`, `statushistory`, `technician`): se revisaron mediante sus pruebas y por búsquedas dirigidas, no completos.
