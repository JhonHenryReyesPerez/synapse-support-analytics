# Synapse Support Analytics — Frontend Web

Interfaz de usuario nativa (HTML5, CSS3, JavaScript ES6) para el sistema de análisis inteligente de incidentes tecnológicos y asistencia mediante IA.

## 1. Características Implementadas

- **Diseño Cómodo, Moderno y Profesional:**
  - Identidad **Editorial tecnológico**: papel cálido, tinta ciruela y acento arcilla; modo oscuro con superficies ciruela y texto marfil. Tokens compartidos para controles, estados y gráficos.
  - Componentes reutilizables: botones interactivos con estados de carga (`.btn.is-loading`), modales con gestión de foco, checklist dinámico de políticas de contraseñas y sistema de notificaciones *Toast*.
- **Autenticación y Seguridad (JWT Corporativo):**
  - Pantalla de acceso corporativo (`index.html`): sin formulario de registro; las cuentas se crean desde el módulo de administración. La API conserva `POST /api/auth/register` (crea solo cuentas `USER`).
  - Cuentas de demostración en un desplegable para evaluación rápida (1-click autofill con contraseña `Demo.2026`):
    - 👑 **Admin:** `admin.demo` (Administrador de sistema, control total y aprobación RAG).
    - 🛠️ **Técnicos especialistas:** `tec.redes`, `tec.hardware`, `tec.software`, `tec.aplicaciones`.
    - 👤 **Usuarios solicitantes:** `laura.gomez`, `carlos.ruiz`.
  - Detección y bloqueo con modal obligatorio cuando `mustChangePassword: true` (`POST /api/auth/change-password`).
  - Manejo de bloqueo temporal tras fallos repetidos (`429 Too Many Requests` con lectura de `Retry-After`).
  - Cierre y limpieza de sesión segura ante `401 Unauthorized`.
- **Gestión de Incidentes (RF01):**
  - Registro de incidentes con validación de título ($\le 150$), descripción ($\le 10.000$), categoría y prioridad.
  - Listado con filtros en tiempo real por estado, prioridad y categoría, con paginación estable.
  - Flujo de transiciones de estado: `ASSIGN` / `REASSIGN` (con justificación), `START_PROGRESS`, `RESOLVE` (con nota obligatoria de procedimiento técnico) y `CONFIRM_CLOSE`.
  - Hilo de comentarios con soporte para notas internas técnicas y línea de tiempo de auditoría de estados.
- **Asistente RAG Inteligente (RF04, RF05, RF08):**
  - Consulta interactiva desde el detalle del incidente (`POST /api/tickets/{id}/assistance`) con `requestId` idempotente (UUID v4).
  - Estado de espera visual mientras Spring Boot, n8n y Qdrant procesan embeddings y recuperación.
  - Presentación estructurada de la solución sugerida, pasos recomendados ordenados y citas de fuentes verificadas con similitud coseno (`cos: 0.xxxx`) y visualizador desplegable de evidencia.
  - Indicador preventivo de *Evidencia Limitada* cuando $< 3$ artículos superan el umbral y aviso de *Sin Resultados* cuando no hay información suficiente.
  - Calificación de la respuesta de 1 a 5 estrellas con comentario opcional (`PUT /api/assistance/queries/{id}/rating`).
  - Historial persistente de consultas previas del incidente.
- **Base de Conocimiento (RF02, RF03):**
  - Catálogo de artículos con filtros por alcance (`PUBLISHED`, `MINE`, `ALL`).
  - Ciclo de vida: redacción de borrador (técnicos/admin), verificación (admin) y activación con sincronización vectorial en Qdrant.
  - Visualización del estado del índice vectorial (`NOT_INDEXED`, `PENDING`, `INDEXED`, `ERROR`) y botón de reconciliación masiva para administradores.
- **Dashboard Analítico (RF06, RF07):**
  - Filtros de cohorte por fechas (`from`, `to`), categoría, prioridad y granularidad (`DAY`, `WEEK`).
  - Cálculo estricto mediante consultas SQL relacionales a `GET /api/analytics/summary` (cero cifras inventadas por IA).
  - Visualización interactiva con Chart.js:
    1. Total de incidentes.
    2. Incidentes por categoría (gráfico dona con colores estables por identificador).
    3. Tendencia temporal de incidentes (gráfico de líneas con series completas y tabla accesible).
    4. Tasa de resolución (% de resueltos/cerrados).
    5. Tiempo promedio de resolución en horas (`averageHours`).
    6. Categorías más recurrentes.
    7. Incidentes por prioridad (gráfico de barras).
    8. Tabla resumen de tiempos promedio por categoría.
- **Módulo de Administración (Exclusivo ADMIN):**
  - Creación de nuevas cuentas de colaboradores institucionales (`POST /api/user/add`) con asignación de rol (`USER`, `TECHNICIAN`, `ADMIN`), área, contraseña inicial y opción de cambio forzado en primer inicio.
  - Gestión y listado general de usuarios y roles (`/api/user/all`).
  - Restablecimiento administrativo de contraseñas (`POST /api/user/{username}/reset-password`) con generación de clave temporal.
  - Creación y activación/desactivación de categorías relacionales (`/api/categories`).

## 2. Estructura de Archivos

```text
frontend/
├── index.html                   # Pantalla de Login y Cambio Obligatorio
├── app.html                     # Plataforma principal SPA multi-módulo
├── README.md                    # Documentación del Frontend
├── css/
│   ├── variables.css            # Tokens de diseño, paleta Slate/Navy y estados
│   ├── base.css                 # Reset tipográfico y base accesible
│   ├── components.css           # Botones, inputs, badges, modales, toasts
│   └── modules/
│       ├── auth.css             # Estilos de la pantalla de bienvenida y acceso
│       ├── app.css              # Layout del panel (sidebar, header, breadcrumbs)
│       ├── tickets.css          # Tablas, línea de tiempo e hilos de comentarios
│       ├── assistant.css        # Tarjeta RAG, fuentes, similitud y estrellas
│       ├── knowledge.css        # Cuadrícula de artículos y badges de indexación
│       └── analytics.css        # Tarjetas de KPIs y grilla de gráficos
└── js/
    ├── config.js                # URL base de API (http://localhost:8051/api) y rutas
    ├── ui.js                    # Toasts, modales, spinners, fechas y validador de claves
    ├── api.js                   # Fetch client centralizado con interceptores y Bearer JWT
    ├── auth.js                  # Servicio de login, cambio de contraseña y sesión
    ├── tickets.js               # Lógica de incidentes, filtros, transiciones y notas
    ├── assistant.js             # Integración del Asistente RAG y calificaciones
    ├── knowledge.js             # Base de conocimiento y ciclo editorial/vectorial
    ├── analytics.js             # Dashboard de analítica y gráficos con Chart.js
    ├── admin.js                 # Gestión de usuarios, reset password y categorías
    └── app.js                   # Controlador general y enrutamiento interno
```

## 3. Cómo Ejecutar Localmente

### Opción A: Extensión Live Server (Recomendada en VS Code)
1. Abrir la carpeta `frontend` en VS Code.
2. Hacer clic derecho sobre `index.html` y seleccionar **"Open with Live Server"**.
3. El frontend se abrirá en `http://localhost:5500` o `http://127.0.0.1:5500` (orígenes ya autorizados en el CORS de desarrollo del backend).

### Opción B: Servidor HTTP de Python
```bash
cd frontend
python -m http.server 5500
```
Abrir `http://localhost:5500/index.html` en el navegador.

## 4. Conexión con el Backend

El frontend consume la API REST de Spring Boot en `http://localhost:8051/api`. Si se requiere modificar la URL (por ejemplo, en un contenedor Docker), se puede configurar ejecutando en la consola del navegador:
```javascript
localStorage.setItem('SYNAPSE_API_URL', 'http://127.0.0.1:8051/api');
location.reload();
```

## 5. Pruebas del frontend

Desde la raíz del repositorio, con Node.js instalado y sin dependencias adicionales:

```bash
node --test frontend/tests/*.test.*
```

Las pruebas usan respuestas simuladas y cubren cambios de incidente durante peticiones,
historial paginado, calificaciones, errores de resolución, sesión y teclado en modales.
No requieren el backend ni llaman servicios de IA. El diseño adaptable se comprueba
en navegador; los dobles de DOM de estas pruebas no verifican el layout.

El botón «Modo oscuro» está disponible en el acceso y en la cabecera de la aplicación.
Usa el tema del sistema al entrar por primera vez y guarda la elección manual en el navegador.

Los iconos vectoriales se sirven desde `assets/icons.svg`, sin bibliotecas ni servicios externos.

## 6. Identidad editorial y temas

- Navegación horizontal en escritorio; menú desplegable con Escape y estado accesible en móvil.
- Source Serif 4 para títulos y cifras; IBM Plex Sans para controles y lectura operativa. Las fuentes WOFF2 se sirven desde `assets/fonts/`, con sus licencias SIL OFL.
- Chart.js 4.4.1 se sirve desde `assets/vendor/`, con su licencia MIT. No depende del CDN en ejecución y conserva los tres tipos de gráficos.
- El botón de tema indica la acción disponible («Modo claro» o «Modo oscuro»), conserva la elección y adapta colores, leyendas, ejes y tooltips sin nuevas solicitudes. La primera visita utiliza la preferencia del sistema.
- El dashboard muestra alcance, período, fecha de cálculo, muestra y exclusiones. Los filtros están en un desplegable; las respuestas fuera de orden se descartan. Un fallo identifica los datos anteriores y ofrece reintentar.
- Los siete indicadores mantienen sus contratos y reglas. Un promedio `null` se muestra como «—». Las tablas de datos siguen disponibles si no carga la biblioteca de gráficos.
- Se respeta `prefers-reduced-motion` en CSS y Chart.js. Los tests comprueban contraste de texto, badges, botones y límites de controles en ambos temas; no sustituyen una auditoría con lector de pantalla.

Validación de este incremento: 37 pruebas de Node aprobadas (54 tras las correcciones de la auditoría del 16 de noviembre, `tests/audit-fixes.test.cjs`). Revisión visual con Orca en escritorio (1440 px), tablet (768 px) y móvil (390 px), incluidos gráficos, navegación, detalle de incidente y formularios. Las operaciones de escritura del backend y la generación RAG no se ejecutaron como parte de esta revisión visual.
