-- B07: nombre y área del usuario. Se agregan como opcionales: las cuentas existentes no tienen
-- ese dato y no se inventa; se completa desde la administración de usuarios.
-- La clave primaria sigue siendo username: no se altera ninguna FK ni asociación histórica.
ALTER TABLE public."user" ADD COLUMN full_name character varying(150);
ALTER TABLE public."user" ADD COLUMN area character varying(100);
