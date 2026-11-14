# Synapse Support Analytics

Sistema inteligente de análisis de incidentes tecnológicos y asistencia mediante IA. Centraliza los
incidentes de soporte, administra una base de conocimiento verificada, analiza la información
histórica con indicadores calculados en SQL y ofrece un asistente RAG cuyas respuestas citan
artículos verificados.

## Componentes

| Componente | Tecnología | Estado en esta rama (`dev/JhonReyes`) |
|---|---|---|
| Backend | Java 21, Spring Boot, PostgreSQL | Implementado: ver [`backend/README.md`](backend/README.md) |
| Frontend | HTML, CSS, JavaScript | A cargo de otro integrante; no está en el repositorio |
| Automatización | n8n, Qdrant, OpenRouter | Flujos en [`n8n/`](n8n) y colección en [`qdrant/`](qdrant); probados con un proveedor de modelos simulado |
| Infraestructura | Docker Compose | [`docker-compose.yml`](docker-compose.yml): PostgreSQL, Qdrant, n8n y backend |

## Estructura del repositorio

```text
backend/     API REST de Spring Boot, migraciones, datos de demostración y pruebas
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

Estado de la verificación: el entorno se probó de extremo a extremo con un proveedor de modelos
simulado ([`docs/backend/PRUEBAS.md`](docs/backend/PRUEBAS.md) §8). **No se ha ejecutado con una
clave real de OpenRouter**, así que los identificadores de modelo por defecto y el umbral de
similitud están sin confirmar.

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
