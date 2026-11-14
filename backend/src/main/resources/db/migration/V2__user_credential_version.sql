-- B04: versión de credenciales para invalidar tokens tras cambiar o restablecer la contraseña.
-- IF NOT EXISTS: la columna pudo haberla creado ddl-auto=update antes de adoptar Flyway.
ALTER TABLE public."user" ADD COLUMN IF NOT EXISTS credential_version integer DEFAULT 0 NOT NULL;
