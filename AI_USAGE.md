# Registro de uso de IA

Registro exigido por el enunciado (ver `CLAUDE.md` §1.1). Cada entrada documenta la ayuda recibida y la revisión humana; no se registran contribuciones no realizadas.

| Fecha | Herramienta | Componente | Tipo de ayuda | Decisiones sugeridas | Revisión humana | Validación |
|---|---|---|---|---|---|---|
| 2026-11-11 | Claude Code (Claude Opus 5.5) | Repositorio | Análisis de `CLAUDE.md` frente al código; reorganización en `backend/` y `docs/`; `.gitignore` raíz | Renombrar `spring-security-user-role-management/` a `backend/`; dejar de versionar `build/`, `bin/`, `.gradle/`, `.idea/` | Pendiente — Jhon Reyes | `./gradlew test`: 18 pruebas, 0 fallos |
| 2026-11-11 | Claude Code (Claude Opus 5.5) | Backend — configuración (B01) | Eliminación de secretos por defecto, separación de perfiles, `.env.example`, validación de longitud de `JWT_SECRET`, prueba `JwtPropertiesBindingTests` | Perfil `dev` explícito; `.env` importado opcionalmente; bootstrap admin desactivado fuera de `dev`; secreto JWT ≥ 32 caracteres | Pendiente — Jhon Reyes | `./gradlew test`: 21 pruebas, 0 fallos; `bootRun` sin `JWT_SECRET` falla al arrancar |
| 2026-11-11 | Claude Code (Claude Opus 5.5) | Backend — contraseñas (B02) | Clase `PasswordPolicy` única aplicada en registro, creación/edición administrativa, cambio, restablecimiento y administrador inicial; login sin validación de política; pruebas `PasswordPolicyTests` y `PasswordChangeFlowTests` | Conservar la regla vigente (≥ 6, mayúscula, carácter especial) y añadir máximo de 72 bytes por el límite de BCrypt | Pendiente — Jhon Reyes | `./gradlew test`: 37 pruebas, 0 fallos |
