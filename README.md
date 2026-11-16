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
cp .env.example .env
```

Edita el archivo `.env` y coloca tu clave de OpenRouter:
```properties
# Reemplaza con tu API Key real de OpenRouter
OPENROUTER_API_KEY=sk-or-v1-tu-clave-aqui
```

> [!NOTE]
> Las contraseñas de PostgreSQL, los tokens de integración (`N8N_SERVICE_TOKEN`, `INTEGRATION_API_TOKEN`) y el secreto JWT ya vienen preconfigurados con valores seguros para el entorno de desarrollo local.

### Paso 2: Iniciar los Servicios

#### Opción A: Despliegue Estándar (Automático)
Ejecuta el siguiente comando en la raíz del proyecto para compilar y levantar todos los contenedores:

```bash
docker compose up -d
```

#### Opción B: Despliegue Ultrarrápido (Recomendado para Desarrollo Local, < 3 segundos)
Si deseas evitar la descarga y compilación de Gradle dentro de Docker en cada inicio, compila el archivo JAR una sola vez en tu host con Java 21 y levanta con `Dockerfile.fast`:

```bash
# 1. Compilar el JAR localmente (toma ~10 segundos con caché de Gradle)
cd backend && ./gradlew bootJar -x test && cd ..

# 2. Levantar los contenedores al instante
docker compose -f docker-compose.yml -f docker-compose.fast.yml up -d
```

Verifica que todos los contenedores estén levantados y saludables:
```bash
docker compose ps
```

Deberías ver:
- `synapse-db`: Up (healthy)
- `synapse-qdrant`: Up (healthy)
- `synapse-qdrant-init`: Exited (0) (tarea de inicialización completada exitosamente en < 1 segundo)
- `synapse-n8n`: Up (healthy)
- `synapse-backend`: Up (healthy)

> [!TIP]
> **Auditoría de Rendimiento de Inicio:**
> - **Inicialización de Qdrant:** El script `setup_qdrant.sh` valida la disponibilidad mediante HTTP 200 directo (`/readyz`), resolviendo el bucle infinito anterior.
> - **Caché de Capas:** El `Dockerfile` pre-resuelve dependencias antes de copiar `src`, evitando descargas redundantes.
> - **Healthchecks con `start_period`:** Configurados a 2-3s para que Docker marque los servicios como listos en cuanto inicien sin esperas artificiales.
> - **Optimizaciones de n8n:** Telemetría y notificaciones remotas desactivadas para arranque instantáneo sin llamadas bloqueantes a la nube.


---

## 4. Configuración e Importación de Flujos en n8n

Una vez que el contenedor de n8n esté arriba:

1. Abre tu navegador e ingresa a: **`http://localhost:5679`**
2. Si es la primera vez, completa el formulario de registro de usuario inicial de n8n.
3. **Configurar Credencial de OpenRouter:**
   - Ve a **Credentials** $\rightarrow$ **Add Credential**.
   - Selecciona **Header Auth**.
   - Asigna el nombre: `OpenRouter Header Auth`.
   - Name: `Authorization`
   - Value: `Bearer TU_OPENROUTER_API_KEY` (o `Bearer sk-or-v1-...`).
   - Guarda la credencial.
4. **Importar Flujo 1 (Indexación):**
   - Ve a **Workflows** $\rightarrow$ Menú de opciones (tres puntos) $\rightarrow$ **Import from File**.
   - Selecciona el archivo: [`n8n/Flujo-1-Indexacion-Conocimiento.json`](file:///C:/Users/USER26/Desktop/Proyecto-de-IA-dos/n8n/Flujo-1-Indexacion-Conocimiento.json).
   - En el nodo `OpenRouter Embeddings`, asegúrate de vincular tu credencial `OpenRouter Header Auth`.
   - Activa el flujo con el interruptor **Active** (arriba a la derecha) y guarda.
5. **Importar Flujo 2 (Asistencia RAG):**
   - Menú de opciones $\rightarrow$ **Import from File**.
   - Selecciona el archivo: [`n8n/Flujo-2-Asistencia-RAG.json`](file:///C:/Users/USER26/Desktop/Proyecto-de-IA-dos/n8n/Flujo-2-Asistencia-RAG.json).
   - En los nodos `OpenRouter Embeddings` y `OpenRouter Gemini Chat`, vincula tu credencial `OpenRouter Header Auth`.
   - Activa el flujo con el interruptor **Active** y guarda.

---

## 5. Carga de Datos Iniciales (Seed Data)

El proyecto incluye un script SQL de datos iniciales en [`database/seed_data.sql`](file:///C:/Users/USER26/Desktop/Proyecto-de-IA-dos/database/seed_data.sql) que contiene:
- **10 Usuarios del Sistema:**
  - `admin` (Administrador general)
  - `jhon_tech`, `yeison_tech`, `carlos_tech`, `maria_tech`, `david_tech` (Técnicos)
  - `ana_user`, `pedro_user`, `laura_user`, `sergio_user` (Usuarios solicitantes)
  - *Todas las contraseñas iniciales son:* `Password123!`
- **25 Artículos de Base de Conocimiento:**
  - Cubren Hardware, Software, Conectividad, Cuentas, Servidores, etc.
  - Tienen hashes SHA-256 reales, versiones editoriales y estado `verified=true`, `active=true`.
- **150 Incidentes Históricos:**
  - Con ciclo de vida completo (`NEW`, `ASSIGNED`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`), fechas históricas coherentes, resoluciones técnicas y categorías normalizadas.

### Aplicar el Script en PostgreSQL
Ejecuta el siguiente comando para cargar los datos en el contenedor de base de datos:

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
