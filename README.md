# Synapse Support Analytics

Sistema inteligente de análisis de incidentes tecnológicos y asistencia mediante IA. Centraliza los
incidentes de soporte, administra una base de conocimiento verificada, analiza la información
histórica con indicadores calculados en SQL y ofrece un asistente RAG cuyas respuestas citan
artículos verificados.

## Componentes

| Componente | Tecnología | Estado en esta rama (`dev/DiegoLeon`) |
|---|---|---|
| Backend | Java 21, Spring Boot, PostgreSQL | Implementado: ver [`backend/README.md`](backend/README.md) |
| Frontend | HTML, CSS, JavaScript | Implementado: ver [`frontend/README.md`](frontend/README.md) |
| Automatización | n8n, Qdrant, OpenRouter | Flujos en [`n8n/`](n8n) y colección en [`qdrant/`](qdrant); probados con modelos reales de OpenRouter |
| Infraestructura | Docker Compose | [`docker-compose.yml`](docker-compose.yml): PostgreSQL, Qdrant, n8n y backend |

## Estructura del repositorio

```text
backend/     API REST de Spring Boot, migraciones, datos de demostración y pruebas
frontend/    Interfaz de usuario web (HTML5, CSS3, JS ES6) con dashboard analítico y asistente RAG
n8n/         Flujos de indexación y de asistencia RAG, y script de arranque de n8n
qdrant/      Configuración de la colección vectorial
docker-compose.yml   Entorno completo
docs/
  requerimientos/   Enunciado del proyecto
  backend/          Contratos, diagramas, informe de análisis y documento de pruebas
  assets/           Capturas de la interfaz
AI_USAGE.md  Registro de uso de inteligencia artificial
CLAUDE.md    Especificación de trabajo del backend y estado de implementación
```

## Inicio rápido del backend

```bash
cd backend
cp .env.example .env                           # completar base de datos y secretos
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun   # API en http://localhost:8051
```

Swagger queda en `http://localhost:8051/swagger-ui.html`. Instalación, variables, datos de
demostración y pruebas: [`backend/README.md`](backend/README.md).

## Entorno completo con Docker Compose

Levanta PostgreSQL, Qdrant, n8n y el backend. Los puertos se publican solo en `127.0.0.1`.

```bash
cp .env.example .env        # completar todos los secretos; sin ellos Compose no arranca
docker compose up -d --build
```

| Servicio | Dirección |
|---|---|
| API y Swagger | `http://localhost:8051/swagger-ui.html` |
| n8n | `http://localhost:5678` |
| Qdrant | `http://localhost:6333/dashboard` |

Al arrancar, Qdrant crea la colección `articulos_soporte` (1536 dimensiones, distancia coseno) y n8n
importa y publica los dos flujos de `n8n/`. Los flujos del repositorio reemplazan en cada arranque a
los guardados en n8n: los cambios hechos en su editor hay que exportarlos a `n8n/` para conservarlos.
Los flujos no contienen secretos; leen la clave de OpenRouter y los tokens de las variables de entorno.

### Spring Boot en IntelliJ y n8n en Docker Desktop

Si el backend corre en IntelliJ, n8n debe volver al equipo anfitrión para validar las fuentes.
Configura `N8N_BACKEND_URL=http://host.docker.internal:8051` en el `.env` de la raíz.
Sin esta variable, n8n usa el servicio `backend` de Compose, adecuado para el entorno completo.

Si otras instalaciones ocupan los puertos habituales, este es un ejemplo de configuración en el
`.env` de la raíz:

```properties
DB_PORT=5434
N8N_PORT=5680
QDRANT_PORT=6335
N8N_BACKEND_URL=http://host.docker.internal:8051
```

Prepara `backend/.env` con las credenciales de la misma base y los mismos tokens de integración.
Para los puertos del ejemplo, define:

```properties
DB_URL=jdbc:postgresql://localhost:5434/campusdesk
N8N_BASE_URL=http://localhost:5680
```

Arranca los servicios desde la raíz y ejecuta Spring Boot desde IntelliJ con `backend` como
directorio de trabajo:

```bash
docker compose up -d db qdrant-init n8n
```

El editor de n8n queda en `http://localhost:5680`. Si solicita configurar el propietario en el
primer acceso, crea tu cuenta; los usuarios de demostración de la aplicación no son cuentas de n8n.
Reinicia Spring Boot cada vez que cambies `backend/.env`. Publicar los flujos registra los webhooks;
las ejecuciones aparecen cuando el backend solicita indexar o consultar el asistente.

Datos de demostración e indexación de la base de conocimiento:

```bash
# 1. Cargar los datos (13 usuarios, 31 artículos, 180 incidentes)
docker compose exec -T db psql -U campusdesk -d campusdesk -v ON_ERROR_STOP=1 \
  < backend/src/main/resources/db/seed/demo_data.sql

# 2. Iniciar sesión como administrador de demostración y enviar los artículos elegibles a n8n
TOKEN=$(curl -s -X POST http://localhost:8051/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin.demo","password":"Demo.2026"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
curl -X POST http://localhost:8051/api/knowledge/articles/index/reconcile -H "Authorization: Bearer $TOKEN"
```

El modelo de embeddings (`EMBEDDING_MODEL`) debe producir vectores de 1536 dimensiones y ser el mismo
para indexar y para consultar. Si se cambia, hay que recrear la colección y reindexar.

Estado de la verificación: el entorno se probó de extremo a extremo con modelos reales de OpenRouter
(`openai/text-embedding-3-small` y `google/gemini-2.5-flash`) y las diez pruebas obligatorias dieron el
resultado esperado ([`docs/backend/PRUEBAS.md`](docs/backend/PRUEBAS.md) §8). El umbral de similitud por
defecto, 0,55, sale de la calibración del §9 del mismo documento.

## Documentación

| Documento | Contenido |
|---|---|
| [`backend/README.md`](backend/README.md) | Instalación, configuración, ejecución y decisiones del backend |
| [`docs/backend/DIAGRAMAS.md`](docs/backend/DIAGRAMAS.md) | Arquitectura, flujos y modelo entidad-relación |
| [`docs/backend/API_CONOCIMIENTO_ASISTENTE_ANALITICA.md`](docs/backend/API_CONOCIMIENTO_ASISTENTE_ANALITICA.md) | Contrato de la API para el frontend |
| [`docs/backend/CONTRATO_N8N.md`](docs/backend/CONTRATO_N8N.md) | Contrato entre Spring Boot y n8n |
| [`docs/backend/ANALITICA_SQL.md`](docs/backend/ANALITICA_SQL.md) | Definición y SQL de los siete indicadores |
| [`docs/backend/INFORME_ANALISIS.md`](docs/backend/INFORME_ANALISIS.md) | Informe de análisis de datos con conclusiones |
| [`docs/backend/PRUEBAS.md`](docs/backend/PRUEBAS.md) | Resultados de pruebas y limitaciones |
| [`AI_USAGE.md`](AI_USAGE.md) | Herramientas de IA utilizadas, componentes asistidos y validación |

## Reglas de arquitectura

- Toda consulta inteligente entra por Spring Boot. El frontend no consume n8n, Qdrant ni OpenRouter.
- PostgreSQL es la fuente de verdad: decide qué artículos existen y pueden usarse como fuente.
- Cuando no hay artículos suficientemente relevantes, el sistema lo informa en lugar de generar una solución sin respaldo.
- Los indicadores del dashboard se calculan con consultas reales a la base de datos, nunca con un modelo.
- Las claves y credenciales se configuran con variables de entorno; el repositorio no contiene secretos.
