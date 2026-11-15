-- El técnico ya no lista cuentas ni consulta permisos ajenos: gestionar usuarios es solo de ADMIN.
-- El arranque solo añade permisos a los roles, así que el que ya estaba asignado se retira aquí.
DELETE FROM public.role_permission
WHERE role_name = 'TECHNICIAN' AND permission_name = 'USER_READ';
