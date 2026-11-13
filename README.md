# Synapse Support Analytics

Sistema inteligente de análisis de incidentes tecnológicos y asistencia mediante IA. Centraliza los
incidentes de soporte, administra una base de conocimiento verificada, analiza la información
histórica con indicadores calculados en SQL y ofrece un asistente RAG cuyas respuestas citan
artículos verificados.

## Componentes

| Componente | Tecnología | Estado en esta rama (`dev/JhonReyes`) |
|---|---|---|
| Backend | Java 21, Spring Boot, PostgreSQL | Implementado: ver [`backend/README.md`](backend/README.md) |
| Frontend | HTML, CSS, JavaScript | A cargo de otro integrante; no está en esta rama |
| Automatización | n8n, Qdrant, OpenRouter | A cargo de otro integrante (rama `dev/Json`); aquí está el contrato que deben cumplir |
| Infraestructura | Docker Compose | A cargo de otro integrante (rama `dev/Json`) |

## Estructura del repositorio

```text
backend/     API REST de Spring Boot, migraciones, datos de demostración y pruebas
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
