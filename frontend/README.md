# Synapse Support Analytics — Frontend Web

Interfaz de usuario nativa (HTML5, CSS3, JavaScript ES6) para el sistema de análisis inteligente de incidentes tecnológicos y asistencia mediante IA.

## 1. Características Implementadas

- **Diseño Cómodo, Moderno y Profesional:**
  - Sistema de tokens y variables CSS (`variables.css`) con paleta ejecutiva *Slate / Navy*, estados de tickets y contrastes accesibles.
  - Componentes reutilizables: botones interactivos con estados de carga (`.btn.is-loading`), modales con *backdrop blur*, checklist dinámico de políticas de contraseñas y sistema de notificaciones *Toast*.
- **Autenticación y Seguridad (JWT):**
  - Pantalla de acceso (`index.html`) con navegación entre pestañas para *Iniciar Sesión* y *Crear Cuenta* (`POST /api/auth/register`).
  - Accesos rápidos con credenciales del conjunto de datos de prueba (`admin.demo`, `tec.redes`, `laura.gomez` con contraseña `Demo.2026`).
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
    2. Incidentes por categoría (gráfico dona).
    3. Tendencia temporal de incidentes (gráfico de líneas con series completas).
    4. Tasa de resolución (% de resueltos/cerrados).
    5. Tiempo promedio de resolución en horas (`averageHours`).
    6. Categorías más recurrentes.
    7. Incidentes por prioridad (gráfico de barras).
    8. Tabla resumen de tiempos promedio por categoría.
- **Módulo de Administración:**
  - Gestión de usuarios y roles (`/api/user/all`).
  - Restablecimiento administrativo de contraseñas (`POST /api/user/{username}/reset-password`) con generación de clave temporal.
  - Creación y activación/desactivación de categorías relacionales (`/api/categories`).

## 2. Estructura de Archivos

```text
frontend/
├── index.html                   # Pantalla de Login, Registro y Cambio Obligatorio
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
    ├── auth.js                  # Servicio de login, registro, cambio de contraseña y sesión
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
