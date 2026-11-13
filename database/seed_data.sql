-- =============================================================================
-- SYNAPSE-SUPPORT-ANALYTICS: DATOS INICIALES OBLIGATORIOS (SEED DATA)
-- 10 Usuarios, 25 Artículos Técnicos RAG, 150 Incidentes Históricos Coherentes
-- =============================================================================

-- 1. Inserción de Usuarios y Roles
INSERT INTO public."user" (username, full_name, email, password, disabled, locked, must_change_password, credential_version, area)
VALUES ('superadmin', 'Super Administrador', 'superadmin@technova.com', '$2a$10$wT8hA417B6lH/W89n83XF.c3m17i1oI8/kEep6bC6mZ7yVvIknQ4i', false, false, false, 1, 'Dirección TI')
ON CONFLICT (username) DO UPDATE SET full_name = EXCLUDED.full_name, area = EXCLUDED.area;
INSERT INTO public.user_role (username, role)
VALUES ('superadmin', 'ADMIN')
ON CONFLICT (username, role) DO NOTHING;
INSERT INTO public."user" (username, full_name, email, password, disabled, locked, must_change_password, credential_version, area)
VALUES ('admin1', 'Jhon Reyes', 'jreyes@technova.com', '$2a$10$wT8hA417B6lH/W89n83XF.c3m17i1oI8/kEep6bC6mZ7yVvIknQ4i', false, false, false, 1, 'Infraestructura')
ON CONFLICT (username) DO UPDATE SET full_name = EXCLUDED.full_name, area = EXCLUDED.area;
INSERT INTO public.user_role (username, role)
VALUES ('admin1', 'ADMIN')
ON CONFLICT (username, role) DO NOTHING;
INSERT INTO public."user" (username, full_name, email, password, disabled, locked, must_change_password, credential_version, area)
VALUES ('admin2', 'Yeison Pallares', 'ypallares@technova.com', '$2a$10$wT8hA417B6lH/W89n83XF.c3m17i1oI8/kEep6bC6mZ7yVvIknQ4i', false, false, false, 1, 'DevOps')
ON CONFLICT (username) DO UPDATE SET full_name = EXCLUDED.full_name, area = EXCLUDED.area;
INSERT INTO public.user_role (username, role)
VALUES ('admin2', 'ADMIN')
ON CONFLICT (username, role) DO NOTHING;
INSERT INTO public."user" (username, full_name, email, password, disabled, locked, must_change_password, credential_version, area)
VALUES ('tech_carlos', 'Carlos Mendoza', 'cmendoza@technova.com', '$2a$10$wT8hA417B6lH/W89n83XF.c3m17i1oI8/kEep6bC6mZ7yVvIknQ4i', false, false, false, 1, 'Soporte Nivel 2')
ON CONFLICT (username) DO UPDATE SET full_name = EXCLUDED.full_name, area = EXCLUDED.area;
INSERT INTO public.user_role (username, role)
VALUES ('tech_carlos', 'TECHNICIAN')
ON CONFLICT (username, role) DO NOTHING;
INSERT INTO public.technician_profiles (username, specialty, availability_status, active_tickets_count, last_assigned_at)
VALUES ('tech_carlos', 'Soporte Nivel 2', 'AVAILABLE', 0, CURRENT_TIMESTAMP)
ON CONFLICT (username) DO NOTHING;
INSERT INTO public."user" (username, full_name, email, password, disabled, locked, must_change_password, credential_version, area)
VALUES ('tech_laura', 'Laura Gómez', 'lgomez@technova.com', '$2a$10$wT8hA417B6lH/W89n83XF.c3m17i1oI8/kEep6bC6mZ7yVvIknQ4i', false, false, false, 1, 'Redes y Conectividad')
ON CONFLICT (username) DO UPDATE SET full_name = EXCLUDED.full_name, area = EXCLUDED.area;
INSERT INTO public.user_role (username, role)
VALUES ('tech_laura', 'TECHNICIAN')
ON CONFLICT (username, role) DO NOTHING;
INSERT INTO public.technician_profiles (username, specialty, availability_status, active_tickets_count, last_assigned_at)
VALUES ('tech_laura', 'Redes y Conectividad', 'AVAILABLE', 0, CURRENT_TIMESTAMP)
ON CONFLICT (username) DO NOTHING;
INSERT INTO public."user" (username, full_name, email, password, disabled, locked, must_change_password, credential_version, area)
VALUES ('tech_david', 'David Salazar', 'dsalazar@technova.com', '$2a$10$wT8hA417B6lH/W89n83XF.c3m17i1oI8/kEep6bC6mZ7yVvIknQ4i', false, false, false, 1, 'Sistemas y Servidores')
ON CONFLICT (username) DO UPDATE SET full_name = EXCLUDED.full_name, area = EXCLUDED.area;
INSERT INTO public.user_role (username, role)
VALUES ('tech_david', 'TECHNICIAN')
ON CONFLICT (username, role) DO NOTHING;
INSERT INTO public.technician_profiles (username, specialty, availability_status, active_tickets_count, last_assigned_at)
VALUES ('tech_david', 'Sistemas y Servidores', 'AVAILABLE', 0, CURRENT_TIMESTAMP)
ON CONFLICT (username) DO NOTHING;
INSERT INTO public."user" (username, full_name, email, password, disabled, locked, must_change_password, credential_version, area)
VALUES ('user_ana', 'Ana Martínez', 'amartinez@technova.com', '$2a$10$wT8hA417B6lH/W89n83XF.c3m17i1oI8/kEep6bC6mZ7yVvIknQ4i', false, false, false, 1, 'Contabilidad')
ON CONFLICT (username) DO UPDATE SET full_name = EXCLUDED.full_name, area = EXCLUDED.area;
INSERT INTO public.user_role (username, role)
VALUES ('user_ana', 'USER')
ON CONFLICT (username, role) DO NOTHING;
INSERT INTO public."user" (username, full_name, email, password, disabled, locked, must_change_password, credential_version, area)
VALUES ('user_felipe', 'Felipe Torres', 'ftorres@technova.com', '$2a$10$wT8hA417B6lH/W89n83XF.c3m17i1oI8/kEep6bC6mZ7yVvIknQ4i', false, false, false, 1, 'Recursos Humanos')
ON CONFLICT (username) DO UPDATE SET full_name = EXCLUDED.full_name, area = EXCLUDED.area;
INSERT INTO public.user_role (username, role)
VALUES ('user_felipe', 'USER')
ON CONFLICT (username, role) DO NOTHING;
INSERT INTO public."user" (username, full_name, email, password, disabled, locked, must_change_password, credential_version, area)
VALUES ('user_marta', 'Marta Rivas', 'mrivas@technova.com', '$2a$10$wT8hA417B6lH/W89n83XF.c3m17i1oI8/kEep6bC6mZ7yVvIknQ4i', false, false, false, 1, 'Ventas')
ON CONFLICT (username) DO UPDATE SET full_name = EXCLUDED.full_name, area = EXCLUDED.area;
INSERT INTO public.user_role (username, role)
VALUES ('user_marta', 'USER')
ON CONFLICT (username, role) DO NOTHING;
INSERT INTO public."user" (username, full_name, email, password, disabled, locked, must_change_password, credential_version, area)
VALUES ('user_sergio', 'Sergio Castro', 'scastro@technova.com', '$2a$10$wT8hA417B6lH/W89n83XF.c3m17i1oI8/kEep6bC6mZ7yVvIknQ4i', false, false, false, 1, 'Operaciones')
ON CONFLICT (username) DO UPDATE SET full_name = EXCLUDED.full_name, area = EXCLUDED.area;
INSERT INTO public.user_role (username, role)
VALUES ('user_sergio', 'USER')
ON CONFLICT (username, role) DO NOTHING;
INSERT INTO public."user" (username, full_name, email, password, disabled, locked, must_change_password, credential_version, area)
VALUES ('user_elena', 'Elena Vargas', 'evargas@technova.com', '$2a$10$wT8hA417B6lH/W89n83XF.c3m17i1oI8/kEep6bC6mZ7yVvIknQ4i', false, false, false, 1, 'Marketing')
ON CONFLICT (username) DO UPDATE SET full_name = EXCLUDED.full_name, area = EXCLUDED.area;
INSERT INTO public.user_role (username, role)
VALUES ('user_elena', 'USER')
ON CONFLICT (username, role) DO NOTHING;


-- 2. Inserción de Artículos Técnicos Verificados (Base de Conocimiento RAG)
INSERT INTO public.knowledge_articles (id, title, problem, symptoms, cause, solution, author_username, verified, verified_by_username, verified_at, active, content_version, content_hash, index_status, created_at, updated_at)
OVERRIDING SYSTEM VALUE
VALUES (1, 'Falla de conexión Wi-Fi corporativa', 'Los equipos portátiles no logran autenticarse en la red Wi-Fi de la sede principal.', 'Ícono de red muestra advertencia amarilla, mensaje de red sin acceso a internet.', 'Perfil 802.1x desactualizado o certificado de red expirado en el cliente.', '1. Abrir Administrador de redes. 2. Olvidar la red ''TechNova-Corp''. 3. Volver a conectar ingresando credenciales corporativas con certificado vigente. 4. Ejecutar ipconfig /flushdns.', 'admin1', true, 'admin1', CURRENT_TIMESTAMP - INTERVAL '29 days', true, 1, '89b3eda12d7063766b0a60b925521ea5d3dd70abcee3a99374a2de08bcb7ba15', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '39 days', CURRENT_TIMESTAMP - INTERVAL '29 days')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, problem = EXCLUDED.problem, solution = EXCLUDED.solution, content_hash = EXCLUDED.content_hash;
INSERT INTO public.knowledge_articles (id, title, problem, symptoms, cause, solution, author_username, verified, verified_by_username, verified_at, active, content_version, content_hash, index_status, created_at, updated_at)
OVERRIDING SYSTEM VALUE
VALUES (2, 'Conflicto de dirección IP en red cableada', 'La estación de trabajo pierde conectividad intermitentemente por IP duplicada.', 'Mensaje de Windows indicando conflicto de dirección IP con otro equipo en el segmento.', 'Asignación estática de IP sin reserva en el servidor DHCP corporativo.', '1. Configurar la tarjeta de red en modo DHCP automático. 2. En el servidor DHCP, crear una reserva estática por dirección MAC si se requiere IP fija. 3. Reiniciar el adaptador.', 'admin1', true, 'admin1', CURRENT_TIMESTAMP - INTERVAL '28 days', true, 1, '63c1d1f5f4be9a7f7bfb910fdeebbee5230294063bad88ee86f5e39501d7d5e1', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '38 days', CURRENT_TIMESTAMP - INTERVAL '28 days')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, problem = EXCLUDED.problem, solution = EXCLUDED.solution, content_hash = EXCLUDED.content_hash;
INSERT INTO public.knowledge_articles (id, title, problem, symptoms, cause, solution, author_username, verified, verified_by_username, verified_at, active, content_version, content_hash, index_status, created_at, updated_at)
OVERRIDING SYSTEM VALUE
VALUES (3, 'Falla en resolución de nombres DNS', 'Imposibilidad de acceder a los dominios internos de la empresa mediante navegador.', 'Navegadores devuelven ERR_NAME_NOT_RESOLVED para portales internos.', 'Servidor DNS secundario fuera de servicio y cliente sin timeout configurado.', '1. Abrir PowerShell como administrador. 2. Ejecutar ''Clear-DnsClientCache''. 3. Configurar DNS primario 10.0.0.10 y secundario 10.0.0.11 en el adaptador.', 'admin1', true, 'admin1', CURRENT_TIMESTAMP - INTERVAL '27 days', true, 1, 'b6490c778d01d0d5f92e5f98ae52cc19bfb15fb8cbd76ba4203663d951d09f32', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '37 days', CURRENT_TIMESTAMP - INTERVAL '27 days')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, problem = EXCLUDED.problem, solution = EXCLUDED.solution, content_hash = EXCLUDED.content_hash;
INSERT INTO public.knowledge_articles (id, title, problem, symptoms, cause, solution, author_username, verified, verified_by_username, verified_at, active, content_version, content_hash, index_status, created_at, updated_at)
OVERRIDING SYSTEM VALUE
VALUES (4, 'Desconexión recurrente de VPN corporativa', 'Usuarios en trabajo remoto sufren caídas de la VPN cada 15 minutos.', 'Cliente OpenVPN o FortiClient muestra desconexión intempestiva y error de handshake.', 'Incompatibilidad con el tamaño MTU del proveedor de internet del usuario residencial.', '1. Ajustar el MTU de la conexión VPN a 1350 en el archivo de configuración. 2. Desactivar ahorro de energía en la tarjeta de red.', 'admin1', true, 'admin1', CURRENT_TIMESTAMP - INTERVAL '26 days', true, 1, 'd9ad85a4e87e47d904c9a335e1002ff7b348d90b1b9209a8bca1c6625ce96dc1', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '36 days', CURRENT_TIMESTAMP - INTERVAL '26 days')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, problem = EXCLUDED.problem, solution = EXCLUDED.solution, content_hash = EXCLUDED.content_hash;
INSERT INTO public.knowledge_articles (id, title, problem, symptoms, cause, solution, author_username, verified, verified_by_username, verified_at, active, content_version, content_hash, index_status, created_at, updated_at)
OVERRIDING SYSTEM VALUE
VALUES (5, 'Proxy corporativo no responde', 'El navegador no puede cargar ninguna página externa a través del proxy empresarial.', 'Error 504 Gateway Timeout o Proxy Server is not responding.', 'Servicio Squid o proxy inverso saturado o reglas de firewall desincronizadas.', '1. Verificar proxy en Configuración de Red: proxy.technova.local puerto 8080. 2. Reiniciar servicio squid con systemctl restart squid.', 'admin1', true, 'admin1', CURRENT_TIMESTAMP - INTERVAL '25 days', true, 1, 'ffa2414e20035a73118f4ef5f0f403dfc028b636af47828e13c0133e108ba4f0', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '35 days', CURRENT_TIMESTAMP - INTERVAL '25 days')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, problem = EXCLUDED.problem, solution = EXCLUDED.solution, content_hash = EXCLUDED.content_hash;
INSERT INTO public.knowledge_articles (id, title, problem, symptoms, cause, solution, author_username, verified, verified_by_username, verified_at, active, content_version, content_hash, index_status, created_at, updated_at)
OVERRIDING SYSTEM VALUE
VALUES (6, 'Error de compilación Gradle major version 69', 'Fallo al compilar proyectos Java con Gradle indicando Unsupported class file major version.', 'BUG! exception in phase semantic analysis: Unsupported class file major version 69.', 'Gradle wrapper incompatible con JDK 25 configurado como Java predeterminado en el sistema.', '1. Instalar JDK 21 (LTS). 2. Configurar JAVA_HOME=''C:\Program Files\Java\jdk-21.0.12''. 3. Actualizar PATH priorizando la versión 21.', 'admin1', true, 'admin1', CURRENT_TIMESTAMP - INTERVAL '24 days', true, 1, '492ee866750f572044fa672f6128c0b9d6c2b1de1e8f642571354000f75f77d4', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '34 days', CURRENT_TIMESTAMP - INTERVAL '24 days')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, problem = EXCLUDED.problem, solution = EXCLUDED.solution, content_hash = EXCLUDED.content_hash;
INSERT INTO public.knowledge_articles (id, title, problem, symptoms, cause, solution, author_username, verified, verified_by_username, verified_at, active, content_version, content_hash, index_status, created_at, updated_at)
OVERRIDING SYSTEM VALUE
VALUES (7, 'Falla de sincronización en Microsoft Outlook', 'El cliente de correo Outlook no actualiza la bandeja de entrada ni envía correos.', 'Barra de estado muestra ''Desconectado'' o ''Intentando conectar con Exchange''.', 'Archivo de datos OST corrupto o token de autenticación OAuth caducado.', '1. Cerrar Outlook. 2. Ir a %localappdata%\Microsoft\Outlook y renombrar el archivo .ost. 3. Reabrir Outlook para regenerar el caché local.', 'admin1', true, 'admin1', CURRENT_TIMESTAMP - INTERVAL '23 days', true, 1, '211c9b193fef9f407e5f884538b06ff15c0ce540609890bfcd1e4acb6e66ee51', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '33 days', CURRENT_TIMESTAMP - INTERVAL '23 days')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, problem = EXCLUDED.problem, solution = EXCLUDED.solution, content_hash = EXCLUDED.content_hash;
INSERT INTO public.knowledge_articles (id, title, problem, symptoms, cause, solution, author_username, verified, verified_by_username, verified_at, active, content_version, content_hash, index_status, created_at, updated_at)
OVERRIDING SYSTEM VALUE
VALUES (8, 'Bloqueo de aplicación por antivirus corporativo', 'Software interno de facturación bloqueado inmediatamente después de ejecutarse.', 'Notificación de Windows Defender / EDR indicando amenaza potencial detectada (falso positivo).', 'Firma digital del ejecutable interno no reconocida en las reglas heurísticas.', '1. Obtener el hash SHA256 del ejecutable. 2. Solicitar al equipo de seguridad la lista blanca en la consola central. 3. Restaurar archivo de cuarentena.', 'admin1', true, 'admin1', CURRENT_TIMESTAMP - INTERVAL '22 days', true, 1, '6d2572c5caf2e3d9901c256262a189b894701755a678a01e1fd1472b19d5a5dd', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '32 days', CURRENT_TIMESTAMP - INTERVAL '22 days')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, problem = EXCLUDED.problem, solution = EXCLUDED.solution, content_hash = EXCLUDED.content_hash;
INSERT INTO public.knowledge_articles (id, title, problem, symptoms, cause, solution, author_username, verified, verified_by_username, verified_at, active, content_version, content_hash, index_status, created_at, updated_at)
OVERRIDING SYSTEM VALUE
VALUES (9, 'Error de licencia vencida en suite ofimática', 'Aplicaciones de oficina muestran advertencia de producto sin licencia y modo lectura.', 'Aviso en barra superior: ''La mayoría de las características están deshabilitadas''.', 'Caché de licencias de Microsoft 365 desfasado con la cuenta del usuario.', '1. Abrir símbolo del sistema como administrador. 2. Ejecutar cscript ospp.vbs /dstatus para ver claves. 3. Reautenticar con la cuenta corporativa activa.', 'admin1', true, 'admin1', CURRENT_TIMESTAMP - INTERVAL '21 days', true, 1, '1ae2891c998bdea6851b7fd07e0e4f4804d593c7afebd5a15b7515b42da48470', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '31 days', CURRENT_TIMESTAMP - INTERVAL '21 days')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, problem = EXCLUDED.problem, solution = EXCLUDED.solution, content_hash = EXCLUDED.content_hash;
INSERT INTO public.knowledge_articles (id, title, problem, symptoms, cause, solution, author_username, verified, verified_by_username, verified_at, active, content_version, content_hash, index_status, created_at, updated_at)
OVERRIDING SYSTEM VALUE
VALUES (10, 'Error 500 en portal interno de empleados', 'Al acceder al portal de nómina y vacaciones, se presenta pantalla en blanco o error 500.', 'HTTP 500 Internal Server Error en el navegador.', 'Pool de conexiones HikariCP saturado en la base de datos del backend.', '1. Reiniciar el contenedor de la aplicación. 2. Aumentar maximumPoolSize a 30 en application.properties. 3. Validar cierre de conexiones en transacciones.', 'admin1', true, 'admin1', CURRENT_TIMESTAMP - INTERVAL '20 days', true, 1, 'd7af899f7009a58854503484637bcb9abfb8a2f43c355972a3a52812bcf29db4', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '20 days')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, problem = EXCLUDED.problem, solution = EXCLUDED.solution, content_hash = EXCLUDED.content_hash;
INSERT INTO public.knowledge_articles (id, title, problem, symptoms, cause, solution, author_username, verified, verified_by_username, verified_at, active, content_version, content_hash, index_status, created_at, updated_at)
OVERRIDING SYSTEM VALUE
VALUES (11, 'Monitor secundario no detectado', 'Equipo portátil conectado por HDMI o DisplayPort no muestra imagen en segunda pantalla.', 'El monitor muestra ''No Signal'' y entra en suspensión.', 'Fallo en el controlador de gráficos integrados o cable de conexión defectuoso.', '1. Presionar Windows + P y seleccionar ''Extender''. 2. Probar con otro cable HDMI certificado. 3. Reinstalar driver de video desde Device Manager.', 'admin1', true, 'admin1', CURRENT_TIMESTAMP - INTERVAL '19 days', true, 1, '763a13ca275ea2e92e6ced4a450df75561191e0e68f70bef462dfc577c593c2c', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '29 days', CURRENT_TIMESTAMP - INTERVAL '19 days')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, problem = EXCLUDED.problem, solution = EXCLUDED.solution, content_hash = EXCLUDED.content_hash;
INSERT INTO public.knowledge_articles (id, title, problem, symptoms, cause, solution, author_username, verified, verified_by_username, verified_at, active, content_version, content_hash, index_status, created_at, updated_at)
OVERRIDING SYSTEM VALUE
VALUES (12, 'Impresora multifuncional sin respuesta', 'Documentos enviados a la impresora de red de piso quedan en cola indefinidamente.', 'Cola de impresión muestra ''Error - Imprimiendo'' y cola atascada.', 'Spooler de impresión de Windows bloqueado con un trabajo corrupto.', '1. Abrir services.msc y detener ''Cola de impresión'' (Spooler). 2. Borrar archivos en C:\Windows\System32\spool\PRINTERS. 3. Iniciar el servicio.', 'admin1', true, 'admin1', CURRENT_TIMESTAMP - INTERVAL '18 days', true, 1, 'a30d6d497e7952d0dcb1824ea0c2f9bb5ca884fcf8ed33997da828dccc0787b5', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '18 days')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, problem = EXCLUDED.problem, solution = EXCLUDED.solution, content_hash = EXCLUDED.content_hash;
INSERT INTO public.knowledge_articles (id, title, problem, symptoms, cause, solution, author_username, verified, verified_by_username, verified_at, active, content_version, content_hash, index_status, created_at, updated_at)
OVERRIDING SYSTEM VALUE
VALUES (13, 'Teclado o periféricos USB dejan de funcionar', 'Dispositivos USB se desconectan aleatoriamente tras periodos de inactividad.', 'Sonido constante de conexión y desconexión de hardware USB en Windows.', 'Suspensión selectiva de USB activada en las opciones de energía avanzadas.', '1. Ir a Opciones de energía > Cambiar configuración del plan. 2. Desactivar ''Configuración de suspensión selectiva de USB''. 3. Guardar cambios.', 'admin1', true, 'admin1', CURRENT_TIMESTAMP - INTERVAL '17 days', true, 1, 'b8be92003f542c063829d9f2559166ddd44795b3b90fbe146581ff217d69e32e', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '27 days', CURRENT_TIMESTAMP - INTERVAL '17 days')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, problem = EXCLUDED.problem, solution = EXCLUDED.solution, content_hash = EXCLUDED.content_hash;
INSERT INTO public.knowledge_articles (id, title, problem, symptoms, cause, solution, author_username, verified, verified_by_username, verified_at, active, content_version, content_hash, index_status, created_at, updated_at)
OVERRIDING SYSTEM VALUE
VALUES (14, 'Sobrecalentamiento y apagado repentino de laptop', 'Portátiles se apagan intempestivamente durante videollamadas o alta carga.', 'Ventilador al máximo de revoluciones, equipo caliente al tacto y apagado abrupto.', 'Obstrucción de disipador por polvo o degradación de pasta térmica.', '1. Limpieza de ranuras de ventilación con aire comprimido. 2. Actualizar BIOS para perfiles térmicos optimizados. 3. Mantenimiento preventivo en laboratorio.', 'admin1', true, 'admin1', CURRENT_TIMESTAMP - INTERVAL '16 days', true, 1, 'd9110dfa17a167b8ac0acde58a35ead0124efa3624170e90d4805ec489fb3197', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '26 days', CURRENT_TIMESTAMP - INTERVAL '16 days')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, problem = EXCLUDED.problem, solution = EXCLUDED.solution, content_hash = EXCLUDED.content_hash;
INSERT INTO public.knowledge_articles (id, title, problem, symptoms, cause, solution, author_username, verified, verified_by_username, verified_at, active, content_version, content_hash, index_status, created_at, updated_at)
OVERRIDING SYSTEM VALUE
VALUES (15, 'Disco duro o SSD con advertencia de fallo', 'Sistema operativo lento, congelamiento intermitente y advertencia SMART.', 'Aviso de BIOS: ''SMART Failure Predicted on Hard Disk'' al encender.', 'Sectores reasignados excedidos en unidad de almacenamiento.', '1. Realizar respaldo inmediato de la información del usuario a OneDrive/NAS. 2. Programar clonación y sustitución por nueva unidad SSD NVMe.', 'admin1', true, 'admin1', CURRENT_TIMESTAMP - INTERVAL '15 days', true, 1, '8d658c00f5f39a0bbfbcb3e29501a609d96a3667a12593542e4577f3543cdd39', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '25 days', CURRENT_TIMESTAMP - INTERVAL '15 days')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, problem = EXCLUDED.problem, solution = EXCLUDED.solution, content_hash = EXCLUDED.content_hash;
INSERT INTO public.knowledge_articles (id, title, problem, symptoms, cause, solution, author_username, verified, verified_by_username, verified_at, active, content_version, content_hash, index_status, created_at, updated_at)
OVERRIDING SYSTEM VALUE
VALUES (16, 'Cuenta de usuario bloqueada por intentos fallidos', 'El usuario no puede iniciar sesión en su estación de trabajo tras ingresar clave errónea.', 'Mensaje: ''La cuenta de usuario está bloqueada y no se puede usar para iniciar sesión''.', 'Política de seguridad corporativa: bloqueo automático tras 5 intentos fallidos.', '1. Verificar identidad del usuario en Active Directory / Portal Admin. 2. Desbloquear la cuenta en la consola de usuarios. 3. Solicitar cambio de contraseña.', 'admin1', true, 'admin1', CURRENT_TIMESTAMP - INTERVAL '14 days', true, 1, '0484c96f72976fec3b206ad7c5d102b244fc9f9b74ba0da3ca84d21beabfc40d', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '24 days', CURRENT_TIMESTAMP - INTERVAL '14 days')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, problem = EXCLUDED.problem, solution = EXCLUDED.solution, content_hash = EXCLUDED.content_hash;
INSERT INTO public.knowledge_articles (id, title, problem, symptoms, cause, solution, author_username, verified, verified_by_username, verified_at, active, content_version, content_hash, index_status, created_at, updated_at)
OVERRIDING SYSTEM VALUE
VALUES (17, 'Certificado SSL expirado en servicio web interno', 'Al navegar a herramientas internas aparece advertencia de seguridad no segura.', 'NET::ERR_CERT_DATE_INVALID en Google Chrome o Edge.', 'Certificado TLS autofirmado o Let''s Encrypt superó su periodo de 90 días de validez.', '1. Renovar certificado con certbot o emitir nueva clave CSR en la CA interna. 2. Actualizar archivos .crt y .key en Nginx. 3. Recargar servidor web.', 'admin1', true, 'admin1', CURRENT_TIMESTAMP - INTERVAL '13 days', true, 1, '36f66921b4bdb01c5c1da16afadc5c014762bc37f29b5232fd0658393694f7e6', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '23 days', CURRENT_TIMESTAMP - INTERVAL '13 days')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, problem = EXCLUDED.problem, solution = EXCLUDED.solution, content_hash = EXCLUDED.content_hash;
INSERT INTO public.knowledge_articles (id, title, problem, symptoms, cause, solution, author_username, verified, verified_by_username, verified_at, active, content_version, content_hash, index_status, created_at, updated_at)
OVERRIDING SYSTEM VALUE
VALUES (18, 'Detección de correo sospechoso de phishing', 'Usuario reporta correo que solicita actualización urgente de contraseñas de banco o TI.', 'Remitente externo suplantando dominio corporativo con enlaces a dominios desconocidos.', 'Campaña de ingeniería social activa con bypass de filtros SPF/DKIM.', '1. Indicar al usuario no hacer clic ni descargar adjuntos. 2. Extraer cabeceras del correo. 3. Bloquear remitente y dominio en el Gateway de correo.', 'admin1', true, 'admin1', CURRENT_TIMESTAMP - INTERVAL '12 days', true, 1, '0fe198206ec76e74fe6a780934fffb7a12efd04b8edd5915ddb6a43485c74600', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '22 days', CURRENT_TIMESTAMP - INTERVAL '12 days')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, problem = EXCLUDED.problem, solution = EXCLUDED.solution, content_hash = EXCLUDED.content_hash;
INSERT INTO public.knowledge_articles (id, title, problem, symptoms, cause, solution, author_username, verified, verified_by_username, verified_at, active, content_version, content_hash, index_status, created_at, updated_at)
OVERRIDING SYSTEM VALUE
VALUES (19, 'Fallo de autenticación con doble factor 2FA', 'El código TOTP generado en la aplicación del móvil es rechazado por el sistema.', 'Mensaje de código de verificación incorrecto en portal de acceso.', 'Desincronización horaria entre el reloj del smartphone del usuario y el servidor NTP.', '1. En la app de autenticación (Google Authenticator / Microsoft Authenticator), ir a Configuración > Corrección de hora para códigos. 2. Probar nuevamente.', 'admin1', true, 'admin1', CURRENT_TIMESTAMP - INTERVAL '11 days', true, 1, 'feb418631c616b06188a06ce6863f372b13b83836a8712929e419a631c21802e', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '21 days', CURRENT_TIMESTAMP - INTERVAL '11 days')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, problem = EXCLUDED.problem, solution = EXCLUDED.solution, content_hash = EXCLUDED.content_hash;
INSERT INTO public.knowledge_articles (id, title, problem, symptoms, cause, solution, author_username, verified, verified_by_username, verified_at, active, content_version, content_hash, index_status, created_at, updated_at)
OVERRIDING SYSTEM VALUE
VALUES (20, 'Límite de conexiones superado en PostgreSQL', 'Servicios backend fallan con error fatal ''too many clients already''.', 'FATAL: remaining connection slots are reserved for non-replica superuser connections.', 'Microservicios abriendo conexiones sin liberar pool o max_connections insuficiente.', '1. Revisar conexiones activas con ''SELECT count(*) FROM pg_stat_activity''. 2. Configurar PgBouncer como gestor de conexiones. 3. Ajustar max_connections en postgresql.conf.', 'admin1', true, 'admin1', CURRENT_TIMESTAMP - INTERVAL '10 days', true, 1, 'f14e14a96d50874cf8e860f581fe144a0c96b6aa3341d9d0cb63c123fdf4eab3', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '10 days')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, problem = EXCLUDED.problem, solution = EXCLUDED.solution, content_hash = EXCLUDED.content_hash;
INSERT INTO public.knowledge_articles (id, title, problem, symptoms, cause, solution, author_username, verified, verified_by_username, verified_at, active, content_version, content_hash, index_status, created_at, updated_at)
OVERRIDING SYSTEM VALUE
VALUES (21, 'Memoria swap alta y OOM Killer en servidor Linux', 'Procesos críticos del servidor se cierran de forma inesperada.', 'dmesg muestra ''Out of memory: Kill process (java)'' en registros del sistema.', 'Fuga de memoria en aplicación Java o falta de asignación de swap y límites de contenedor.', '1. Inspeccionar uso de memoria con ''free -m'' y ''htop''. 2. Ajustar límite -Xmx en JVM. 3. Configurar restart: unless-stopped en Docker.', 'admin1', true, 'admin1', CURRENT_TIMESTAMP - INTERVAL '9 days', true, 1, 'd214c991c3c06cd9532d9e6b03e5f568b936f2d1c69eb3a59d8c16252ec6b83c', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '19 days', CURRENT_TIMESTAMP - INTERVAL '9 days')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, problem = EXCLUDED.problem, solution = EXCLUDED.solution, content_hash = EXCLUDED.content_hash;
INSERT INTO public.knowledge_articles (id, title, problem, symptoms, cause, solution, author_username, verified, verified_by_username, verified_at, active, content_version, content_hash, index_status, created_at, updated_at)
OVERRIDING SYSTEM VALUE
VALUES (22, 'Error 502 Bad Gateway en Nginx hacia backend', 'Peticiones al dominio institucional devuelven 502 Bad Gateway.', 'Nginx error.log muestra: ''connect() failed (111: Connection refused) while connecting to upstream''.', 'El contenedor o servicio del backend Spring Boot se detuvo o el puerto no coincide.', '1. Verificar estado del contenedor con ''docker ps''. 2. Revisar logs con ''docker logs campusdesk_backend''. 3. Asegurar que el puerto 8051 esté activo y escuchando.', 'admin1', true, 'admin1', CURRENT_TIMESTAMP - INTERVAL '8 days', true, 1, 'aa62f7fa184cbd5485dff4708a89695d4b62957ee056114a045186c7ab518778', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '8 days')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, problem = EXCLUDED.problem, solution = EXCLUDED.solution, content_hash = EXCLUDED.content_hash;
INSERT INTO public.knowledge_articles (id, title, problem, symptoms, cause, solution, author_username, verified, verified_by_username, verified_at, active, content_version, content_hash, index_status, created_at, updated_at)
OVERRIDING SYSTEM VALUE
VALUES (23, 'Fallo en tareas programadas de backup nocturno', 'Las copias de seguridad de las bases de datos no se generaron la noche anterior.', 'Registro cron muestra código de salida 1 y dump incompleto.', 'Espacio en disco insuficiente en la partición /var/backups.', '1. Ejecutar ''df -h'' para verificar espacio. 2. Purgar backups de más de 30 días de antigüedad. 3. Reejecutar el script de respaldo manual.', 'admin1', true, 'admin1', CURRENT_TIMESTAMP - INTERVAL '7 days', true, 1, 'e5e8a3ca75b4e1311a9860cbda50bd29d273e9ead9e116a2773db78acb21e75e', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '17 days', CURRENT_TIMESTAMP - INTERVAL '7 days')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, problem = EXCLUDED.problem, solution = EXCLUDED.solution, content_hash = EXCLUDED.content_hash;
INSERT INTO public.knowledge_articles (id, title, problem, symptoms, cause, solution, author_username, verified, verified_by_username, verified_at, active, content_version, content_hash, index_status, created_at, updated_at)
OVERRIDING SYSTEM VALUE
VALUES (24, 'Acceso denegado SSH por cambio de clave pública', 'Administradores no pueden conectar vía terminal remota SSH al servidor de pruebas.', 'Permission denied (publickey).', 'Permisos incorrectos en ~/.ssh/authorized_keys (deben ser 600) o llave no registrada.', '1. Entrar por consola VNC/KVM del proveedor cloud. 2. Restaurar permisos chmod 700 ~/.ssh y chmod 600 ~/.ssh/authorized_keys. 3. Validar archivo.', 'admin1', true, 'admin1', CURRENT_TIMESTAMP - INTERVAL '6 days', true, 1, '0672a5972d0d4b5eca0bcc4b90ffa828be9b7809309dd3dc0ff3a61e944e297d', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '16 days', CURRENT_TIMESTAMP - INTERVAL '6 days')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, problem = EXCLUDED.problem, solution = EXCLUDED.solution, content_hash = EXCLUDED.content_hash;
INSERT INTO public.knowledge_articles (id, title, problem, symptoms, cause, solution, author_username, verified, verified_by_username, verified_at, active, content_version, content_hash, index_status, created_at, updated_at)
OVERRIDING SYSTEM VALUE
VALUES (25, 'Lentitud en consultas SQL de reportes históricos', 'Generación de reportes analíticos tarda más de 2 minutos o genera timeout.', 'Consultas complejas realizan Sequential Scan sobre millones de registros.', 'Ausencia de índices compuestos sobre campos de filtrado temporal y categórico.', '1. Ejecutar EXPLAIN ANALYZE sobre la consulta lenta. 2. Crear índice compuesto CREATE INDEX idx_compuesto ON tabla(categoria_id, estado, fecha_creacion).', 'admin1', true, 'admin1', CURRENT_TIMESTAMP - INTERVAL '5 days', true, 1, '338715bc0eef08e6ea567a6454a5381b5888e832c797fbf42fba0623424cc8f6', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '15 days', CURRENT_TIMESTAMP - INTERVAL '5 days')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, problem = EXCLUDED.problem, solution = EXCLUDED.solution, content_hash = EXCLUDED.content_hash;
SELECT setval('public.knowledge_articles_id_seq', (SELECT MAX(id) FROM public.knowledge_articles));

-- 3. Inserción de 150 Incidentes Históricos
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (1, 'user_elena', 'tech_carlos', 'Reporte #1: Incidencia técnica en Base de Datos', 'El usuario user_elena reporta fallo de prioridad ALTA en el subsistema de Base de Datos. Se requiere revisión técnica.', 6, 'ALTA', 'IN_PROGRESS', '2026-05-02 16:58:00', '2026-05-02 17:58:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (2, 'user_elena', 'tech_david', 'Reporte #2: Incidencia técnica en Accesos', 'El usuario user_elena reporta fallo de prioridad ALTA en el subsistema de Accesos. Se requiere revisión técnica.', 7, 'ALTA', 'CLOSED', '2026-05-03 11:09:00', '2026-05-04 20:09:00', '2026-05-03 23:09:00', '2026-05-04 20:09:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Accesos.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (3, 'user_elena', 'tech_laura', 'Reporte #3: Incidencia técnica en Accesos', 'El usuario user_elena reporta fallo de prioridad BAJA en el subsistema de Accesos. Se requiere revisión técnica.', 7, 'BAJA', 'RESOLVED', '2026-05-04 15:01:00', '2026-05-05 16:01:00', '2026-05-05 16:01:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Accesos.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (4, 'user_felipe', 'tech_carlos', 'Reporte #4: Incidencia técnica en Accesos', 'El usuario user_felipe reporta fallo de prioridad ALTA en el subsistema de Accesos. Se requiere revisión técnica.', 7, 'ALTA', 'CLOSED', '2026-05-05 11:56:00', '2026-05-06 13:56:00', '2026-05-05 18:56:00', '2026-05-06 13:56:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Accesos.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (5, 'user_elena', 'tech_carlos', 'Reporte #5: Incidencia técnica en Accesos', 'El usuario user_elena reporta fallo de prioridad BAJA en el subsistema de Accesos. Se requiere revisión técnica.', 7, 'BAJA', 'RESOLVED', '2026-05-07 10:42:00', '2026-05-08 23:42:00', '2026-05-08 23:42:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Accesos.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (6, 'user_elena', 'tech_david', 'Reporte #6: Incidencia técnica en Software', 'El usuario user_elena reporta fallo de prioridad ALTA en el subsistema de Software. Se requiere revisión técnica.', 2, 'ALTA', 'IN_PROGRESS', '2026-05-08 14:13:00', '2026-05-08 16:13:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (7, 'user_sergio', 'tech_david', 'Reporte #7: Incidencia técnica en Base de Datos', 'El usuario user_sergio reporta fallo de prioridad ALTA en el subsistema de Base de Datos. Se requiere revisión técnica.', 6, 'ALTA', 'CLOSED', '2026-05-09 18:23:00', '2026-05-11 23:23:00', '2026-05-11 05:23:00', '2026-05-11 23:23:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Base de Datos.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (8, 'user_felipe', 'tech_carlos', 'Reporte #8: Incidencia técnica en Conectividad', 'El usuario user_felipe reporta fallo de prioridad MEDIA en el subsistema de Conectividad. Se requiere revisión técnica.', 1, 'MEDIA', 'ASSIGNED', '2026-05-10 13:01:00', '2026-05-10 15:01:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (9, 'user_ana', 'tech_carlos', 'Reporte #9: Incidencia técnica en Servidores', 'El usuario user_ana reporta fallo de prioridad MEDIA en el subsistema de Servidores. Se requiere revisión técnica.', 5, 'MEDIA', 'ASSIGNED', '2026-05-11 18:03:00', '2026-05-11 20:03:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (10, 'user_marta', 'tech_carlos', 'Reporte #10: Incidencia técnica en Conectividad', 'El usuario user_marta reporta fallo de prioridad BAJA en el subsistema de Conectividad. Se requiere revisión técnica.', 1, 'BAJA', 'ASSIGNED', '2026-05-13 16:15:00', '2026-05-13 19:15:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (11, 'user_felipe', 'tech_david', 'Reporte #11: Incidencia técnica en Base de Datos', 'El usuario user_felipe reporta fallo de prioridad CRITICA en el subsistema de Base de Datos. Se requiere revisión técnica.', 6, 'CRITICA', 'IN_PROGRESS', '2026-05-14 10:46:00', '2026-05-14 14:46:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (12, 'user_sergio', 'tech_carlos', 'Reporte #12: Incidencia técnica en Software', 'El usuario user_sergio reporta fallo de prioridad CRITICA en el subsistema de Software. Se requiere revisión técnica.', 2, 'CRITICA', 'RESOLVED', '2026-05-15 09:06:00', '2026-05-16 09:06:00', '2026-05-16 09:06:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Software.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (13, 'user_sergio', NULL, 'Reporte #13: Incidencia técnica en Seguridad', 'El usuario user_sergio reporta fallo de prioridad CRITICA en el subsistema de Seguridad. Se requiere revisión técnica.', 4, 'CRITICA', 'OPEN', '2026-05-16 08:43:00', '2026-05-16 09:43:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (14, 'user_marta', 'tech_carlos', 'Reporte #14: Incidencia técnica en Conectividad', 'El usuario user_marta reporta fallo de prioridad CRITICA en el subsistema de Conectividad. Se requiere revisión técnica.', 1, 'CRITICA', 'CLOSED', '2026-05-17 11:12:00', '2026-05-19 17:12:00', '2026-05-18 23:12:00', '2026-05-19 17:12:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Conectividad.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (15, 'user_felipe', 'tech_laura', 'Reporte #15: Incidencia técnica en Software', 'El usuario user_felipe reporta fallo de prioridad CRITICA en el subsistema de Software. Se requiere revisión técnica.', 2, 'CRITICA', 'RESOLVED', '2026-05-19 15:15:00', '2026-05-20 21:15:00', '2026-05-20 21:15:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Software.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (16, 'user_ana', 'tech_david', 'Reporte #16: Incidencia técnica en Accesos', 'El usuario user_ana reporta fallo de prioridad BAJA en el subsistema de Accesos. Se requiere revisión técnica.', 7, 'BAJA', 'ASSIGNED', '2026-05-20 16:53:00', '2026-05-20 17:53:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (17, 'user_felipe', 'tech_laura', 'Reporte #17: Incidencia técnica en Conectividad', 'El usuario user_felipe reporta fallo de prioridad MEDIA en el subsistema de Conectividad. Se requiere revisión técnica.', 1, 'MEDIA', 'CLOSED', '2026-05-21 15:30:00', '2026-05-22 23:30:00', '2026-05-22 18:30:00', '2026-05-22 23:30:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Conectividad.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (18, 'user_ana', NULL, 'Reporte #18: Incidencia técnica en Software', 'El usuario user_ana reporta fallo de prioridad CRITICA en el subsistema de Software. Se requiere revisión técnica.', 2, 'CRITICA', 'OPEN', '2026-05-22 14:16:00', '2026-05-22 18:16:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (19, 'user_elena', 'tech_david', 'Reporte #19: Incidencia técnica en Hardware', 'El usuario user_elena reporta fallo de prioridad CRITICA en el subsistema de Hardware. Se requiere revisión técnica.', 3, 'CRITICA', 'RESOLVED', '2026-05-23 15:09:00', '2026-05-24 11:09:00', '2026-05-24 11:09:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Hardware.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (20, 'user_elena', 'tech_david', 'Reporte #20: Incidencia técnica en Software', 'El usuario user_elena reporta fallo de prioridad BAJA en el subsistema de Software. Se requiere revisión técnica.', 2, 'BAJA', 'ASSIGNED', '2026-05-25 16:03:00', '2026-05-25 19:03:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (21, 'user_elena', 'tech_laura', 'Reporte #21: Incidencia técnica en Conectividad', 'El usuario user_elena reporta fallo de prioridad BAJA en el subsistema de Conectividad. Se requiere revisión técnica.', 1, 'BAJA', 'CLOSED', '2026-05-26 16:58:00', '2026-05-27 17:58:00', '2026-05-26 21:58:00', '2026-05-27 17:58:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Conectividad.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (22, 'user_ana', 'tech_david', 'Reporte #22: Incidencia técnica en Conectividad', 'El usuario user_ana reporta fallo de prioridad MEDIA en el subsistema de Conectividad. Se requiere revisión técnica.', 1, 'MEDIA', 'CLOSED', '2026-05-27 09:43:00', '2026-05-28 19:43:00', '2026-05-28 12:43:00', '2026-05-28 19:43:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Conectividad.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (23, 'user_elena', 'tech_david', 'Reporte #23: Incidencia técnica en Servidores', 'El usuario user_elena reporta fallo de prioridad MEDIA en el subsistema de Servidores. Se requiere revisión técnica.', 5, 'MEDIA', 'IN_PROGRESS', '2026-05-28 08:39:00', '2026-05-28 09:39:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (24, 'user_marta', 'tech_carlos', 'Reporte #24: Incidencia técnica en Seguridad', 'El usuario user_marta reporta fallo de prioridad ALTA en el subsistema de Seguridad. Se requiere revisión técnica.', 4, 'ALTA', 'CLOSED', '2026-05-29 18:45:00', '2026-05-30 23:45:00', '2026-05-30 11:45:00', '2026-05-30 23:45:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Seguridad.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (25, 'user_marta', 'tech_laura', 'Reporte #25: Incidencia técnica en Seguridad', 'El usuario user_marta reporta fallo de prioridad MEDIA en el subsistema de Seguridad. Se requiere revisión técnica.', 4, 'MEDIA', 'CLOSED', '2026-05-31 13:59:00', '2026-06-01 09:59:00', '2026-05-31 15:59:00', '2026-06-01 09:59:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Seguridad.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (26, 'user_ana', 'tech_david', 'Reporte #26: Incidencia técnica en Servidores', 'El usuario user_ana reporta fallo de prioridad BAJA en el subsistema de Servidores. Se requiere revisión técnica.', 5, 'BAJA', 'CLOSED', '2026-06-01 11:32:00', '2026-06-02 12:32:00', '2026-06-01 21:32:00', '2026-06-02 12:32:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Servidores.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (27, 'user_marta', NULL, 'Reporte #27: Incidencia técnica en Conectividad', 'El usuario user_marta reporta fallo de prioridad MEDIA en el subsistema de Conectividad. Se requiere revisión técnica.', 1, 'MEDIA', 'OPEN', '2026-06-02 12:10:00', '2026-06-02 16:10:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (28, 'user_elena', 'tech_david', 'Reporte #28: Incidencia técnica en Accesos', 'El usuario user_elena reporta fallo de prioridad ALTA en el subsistema de Accesos. Se requiere revisión técnica.', 7, 'ALTA', 'IN_PROGRESS', '2026-06-03 16:00:00', '2026-06-03 19:00:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (29, 'user_felipe', 'tech_laura', 'Reporte #29: Incidencia técnica en Base de Datos', 'El usuario user_felipe reporta fallo de prioridad BAJA en el subsistema de Base de Datos. Se requiere revisión técnica.', 6, 'BAJA', 'CLOSED', '2026-06-04 09:56:00', '2026-06-06 06:56:00', '2026-06-05 22:56:00', '2026-06-06 06:56:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Base de Datos.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (30, 'user_elena', 'tech_carlos', 'Reporte #30: Incidencia técnica en Hardware', 'El usuario user_elena reporta fallo de prioridad ALTA en el subsistema de Hardware. Se requiere revisión técnica.', 3, 'ALTA', 'RESOLVED', '2026-06-06 13:13:00', '2026-06-07 23:13:00', '2026-06-07 23:13:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Hardware.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (31, 'user_ana', 'tech_carlos', 'Reporte #31: Incidencia técnica en Seguridad', 'El usuario user_ana reporta fallo de prioridad ALTA en el subsistema de Seguridad. Se requiere revisión técnica.', 4, 'ALTA', 'IN_PROGRESS', '2026-06-07 18:27:00', '2026-06-07 21:27:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (32, 'user_marta', 'tech_carlos', 'Reporte #32: Incidencia técnica en Conectividad', 'El usuario user_marta reporta fallo de prioridad BAJA en el subsistema de Conectividad. Se requiere revisión técnica.', 1, 'BAJA', 'IN_PROGRESS', '2026-06-08 18:16:00', '2026-06-08 20:16:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (33, 'user_elena', 'tech_david', 'Reporte #33: Incidencia técnica en Base de Datos', 'El usuario user_elena reporta fallo de prioridad CRITICA en el subsistema de Base de Datos. Se requiere revisión técnica.', 6, 'CRITICA', 'ASSIGNED', '2026-06-09 14:35:00', '2026-06-09 15:35:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (34, 'user_felipe', 'tech_david', 'Reporte #34: Incidencia técnica en Conectividad', 'El usuario user_felipe reporta fallo de prioridad BAJA en el subsistema de Conectividad. Se requiere revisión técnica.', 1, 'BAJA', 'CLOSED', '2026-06-10 08:53:00', '2026-06-12 20:53:00', '2026-06-11 23:53:00', '2026-06-12 20:53:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Conectividad.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (35, 'user_felipe', 'tech_carlos', 'Reporte #35: Incidencia técnica en Software', 'El usuario user_felipe reporta fallo de prioridad CRITICA en el subsistema de Software. Se requiere revisión técnica.', 2, 'CRITICA', 'CLOSED', '2026-06-12 12:23:00', '2026-06-13 22:23:00', '2026-06-13 12:23:00', '2026-06-13 22:23:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Software.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (36, 'user_ana', 'tech_laura', 'Reporte #36: Incidencia técnica en Base de Datos', 'El usuario user_ana reporta fallo de prioridad MEDIA en el subsistema de Base de Datos. Se requiere revisión técnica.', 6, 'MEDIA', 'RESOLVED', '2026-06-13 16:56:00', '2026-06-15 09:56:00', '2026-06-15 09:56:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Base de Datos.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (37, 'user_felipe', 'tech_carlos', 'Reporte #37: Incidencia técnica en Base de Datos', 'El usuario user_felipe reporta fallo de prioridad MEDIA en el subsistema de Base de Datos. Se requiere revisión técnica.', 6, 'MEDIA', 'CLOSED', '2026-06-14 10:56:00', '2026-06-14 22:56:00', '2026-06-14 13:56:00', '2026-06-14 22:56:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Base de Datos.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (38, 'user_sergio', 'tech_david', 'Reporte #38: Incidencia técnica en Base de Datos', 'El usuario user_sergio reporta fallo de prioridad ALTA en el subsistema de Base de Datos. Se requiere revisión técnica.', 6, 'ALTA', 'RESOLVED', '2026-06-15 11:17:00', '2026-06-17 09:17:00', '2026-06-17 09:17:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Base de Datos.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (39, 'user_ana', 'tech_laura', 'Reporte #39: Incidencia técnica en Conectividad', 'El usuario user_ana reporta fallo de prioridad CRITICA en el subsistema de Conectividad. Se requiere revisión técnica.', 1, 'CRITICA', 'CLOSED', '2026-06-16 11:12:00', '2026-06-18 00:12:00', '2026-06-17 11:12:00', '2026-06-18 00:12:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Conectividad.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (40, 'user_felipe', 'tech_carlos', 'Reporte #40: Incidencia técnica en Accesos', 'El usuario user_felipe reporta fallo de prioridad MEDIA en el subsistema de Accesos. Se requiere revisión técnica.', 7, 'MEDIA', 'IN_PROGRESS', '2026-06-18 18:12:00', '2026-06-18 22:12:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (41, 'user_ana', 'tech_laura', 'Reporte #41: Incidencia técnica en Hardware', 'El usuario user_ana reporta fallo de prioridad ALTA en el subsistema de Hardware. Se requiere revisión técnica.', 3, 'ALTA', 'RESOLVED', '2026-06-19 13:41:00', '2026-06-21 10:41:00', '2026-06-21 10:41:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Hardware.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (42, 'user_ana', 'tech_carlos', 'Reporte #42: Incidencia técnica en Accesos', 'El usuario user_ana reporta fallo de prioridad ALTA en el subsistema de Accesos. Se requiere revisión técnica.', 7, 'ALTA', 'RESOLVED', '2026-06-20 12:11:00', '2026-06-20 16:11:00', '2026-06-20 16:11:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Accesos.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (43, 'user_marta', 'tech_david', 'Reporte #43: Incidencia técnica en Conectividad', 'El usuario user_marta reporta fallo de prioridad CRITICA en el subsistema de Conectividad. Se requiere revisión técnica.', 1, 'CRITICA', 'RESOLVED', '2026-06-21 13:27:00', '2026-06-22 15:27:00', '2026-06-22 15:27:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Conectividad.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (44, 'user_marta', NULL, 'Reporte #44: Incidencia técnica en Servidores', 'El usuario user_marta reporta fallo de prioridad MEDIA en el subsistema de Servidores. Se requiere revisión técnica.', 5, 'MEDIA', 'OPEN', '2026-06-22 08:45:00', '2026-06-22 12:45:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (45, 'user_marta', 'tech_laura', 'Reporte #45: Incidencia técnica en Conectividad', 'El usuario user_marta reporta fallo de prioridad MEDIA en el subsistema de Conectividad. Se requiere revisión técnica.', 1, 'MEDIA', 'RESOLVED', '2026-06-24 09:42:00', '2026-06-26 02:42:00', '2026-06-26 02:42:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Conectividad.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (46, 'user_marta', 'tech_david', 'Reporte #46: Incidencia técnica en Hardware', 'El usuario user_marta reporta fallo de prioridad BAJA en el subsistema de Hardware. Se requiere revisión técnica.', 3, 'BAJA', 'RESOLVED', '2026-06-25 12:42:00', '2026-06-26 10:42:00', '2026-06-26 10:42:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Hardware.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (47, 'user_elena', 'tech_carlos', 'Reporte #47: Incidencia técnica en Seguridad', 'El usuario user_elena reporta fallo de prioridad ALTA en el subsistema de Seguridad. Se requiere revisión técnica.', 4, 'ALTA', 'ASSIGNED', '2026-06-26 11:26:00', '2026-06-26 15:26:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (48, 'user_elena', 'tech_david', 'Reporte #48: Incidencia técnica en Base de Datos', 'El usuario user_elena reporta fallo de prioridad MEDIA en el subsistema de Base de Datos. Se requiere revisión técnica.', 6, 'MEDIA', 'CLOSED', '2026-06-27 12:25:00', '2026-06-28 22:25:00', '2026-06-28 09:25:00', '2026-06-28 22:25:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Base de Datos.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (49, 'user_elena', 'tech_david', 'Reporte #49: Incidencia técnica en Software', 'El usuario user_elena reporta fallo de prioridad CRITICA en el subsistema de Software. Se requiere revisión técnica.', 2, 'CRITICA', 'RESOLVED', '2026-06-28 18:20:00', '2026-06-30 00:20:00', '2026-06-30 00:20:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Software.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (50, 'user_elena', 'tech_laura', 'Reporte #50: Incidencia técnica en Seguridad', 'El usuario user_elena reporta fallo de prioridad MEDIA en el subsistema de Seguridad. Se requiere revisión técnica.', 4, 'MEDIA', 'IN_PROGRESS', '2026-06-30 10:42:00', '2026-06-30 11:42:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (51, 'user_ana', 'tech_carlos', 'Reporte #51: Incidencia técnica en Hardware', 'El usuario user_ana reporta fallo de prioridad ALTA en el subsistema de Hardware. Se requiere revisión técnica.', 3, 'ALTA', 'CLOSED', '2026-07-01 18:19:00', '2026-07-02 16:19:00', '2026-07-02 08:19:00', '2026-07-02 16:19:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Hardware.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (52, 'user_felipe', 'tech_laura', 'Reporte #52: Incidencia técnica en Conectividad', 'El usuario user_felipe reporta fallo de prioridad BAJA en el subsistema de Conectividad. Se requiere revisión técnica.', 1, 'BAJA', 'RESOLVED', '2026-07-02 17:54:00', '2026-07-04 00:54:00', '2026-07-04 00:54:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Conectividad.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (53, 'user_sergio', 'tech_laura', 'Reporte #53: Incidencia técnica en Seguridad', 'El usuario user_sergio reporta fallo de prioridad MEDIA en el subsistema de Seguridad. Se requiere revisión técnica.', 4, 'MEDIA', 'RESOLVED', '2026-07-03 14:15:00', '2026-07-05 09:15:00', '2026-07-05 09:15:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Seguridad.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (54, 'user_ana', 'tech_laura', 'Reporte #54: Incidencia técnica en Base de Datos', 'El usuario user_ana reporta fallo de prioridad BAJA en el subsistema de Base de Datos. Se requiere revisión técnica.', 6, 'BAJA', 'ASSIGNED', '2026-07-04 11:11:00', '2026-07-04 15:11:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (55, 'user_ana', 'tech_laura', 'Reporte #55: Incidencia técnica en Conectividad', 'El usuario user_ana reporta fallo de prioridad MEDIA en el subsistema de Conectividad. Se requiere revisión técnica.', 1, 'MEDIA', 'CLOSED', '2026-07-06 10:51:00', '2026-07-09 02:51:00', '2026-07-08 06:51:00', '2026-07-09 02:51:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Conectividad.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (56, 'user_sergio', NULL, 'Reporte #56: Incidencia técnica en Servidores', 'El usuario user_sergio reporta fallo de prioridad ALTA en el subsistema de Servidores. Se requiere revisión técnica.', 5, 'ALTA', 'OPEN', '2026-07-07 17:52:00', '2026-07-07 21:52:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (57, 'user_felipe', NULL, 'Reporte #57: Incidencia técnica en Accesos', 'El usuario user_felipe reporta fallo de prioridad CRITICA en el subsistema de Accesos. Se requiere revisión técnica.', 7, 'CRITICA', 'OPEN', '2026-07-08 15:28:00', '2026-07-08 18:28:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (58, 'user_marta', 'tech_david', 'Reporte #58: Incidencia técnica en Accesos', 'El usuario user_marta reporta fallo de prioridad MEDIA en el subsistema de Accesos. Se requiere revisión técnica.', 7, 'MEDIA', 'ASSIGNED', '2026-07-09 15:40:00', '2026-07-09 17:40:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (59, 'user_ana', 'tech_david', 'Reporte #59: Incidencia técnica en Hardware', 'El usuario user_ana reporta fallo de prioridad CRITICA en el subsistema de Hardware. Se requiere revisión técnica.', 3, 'CRITICA', 'IN_PROGRESS', '2026-07-10 12:15:00', '2026-07-10 15:15:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (60, 'user_elena', 'tech_carlos', 'Reporte #60: Incidencia técnica en Hardware', 'El usuario user_elena reporta fallo de prioridad ALTA en el subsistema de Hardware. Se requiere revisión técnica.', 3, 'ALTA', 'IN_PROGRESS', '2026-07-12 10:09:00', '2026-07-12 12:09:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (61, 'user_felipe', NULL, 'Reporte #61: Incidencia técnica en Seguridad', 'El usuario user_felipe reporta fallo de prioridad MEDIA en el subsistema de Seguridad. Se requiere revisión técnica.', 4, 'MEDIA', 'OPEN', '2026-07-13 09:26:00', '2026-07-13 13:26:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (62, 'user_sergio', 'tech_carlos', 'Reporte #62: Incidencia técnica en Hardware', 'El usuario user_sergio reporta fallo de prioridad CRITICA en el subsistema de Hardware. Se requiere revisión técnica.', 3, 'CRITICA', 'IN_PROGRESS', '2026-07-14 11:53:00', '2026-07-14 15:53:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (63, 'user_elena', NULL, 'Reporte #63: Incidencia técnica en Seguridad', 'El usuario user_elena reporta fallo de prioridad BAJA en el subsistema de Seguridad. Se requiere revisión técnica.', 4, 'BAJA', 'OPEN', '2026-07-15 14:30:00', '2026-07-15 15:30:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (64, 'user_sergio', 'tech_laura', 'Reporte #64: Incidencia técnica en Hardware', 'El usuario user_sergio reporta fallo de prioridad ALTA en el subsistema de Hardware. Se requiere revisión técnica.', 3, 'ALTA', 'CLOSED', '2026-07-16 16:47:00', '2026-07-18 12:47:00', '2026-07-18 01:47:00', '2026-07-18 12:47:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Hardware.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (65, 'user_sergio', 'tech_carlos', 'Reporte #65: Incidencia técnica en Hardware', 'El usuario user_sergio reporta fallo de prioridad CRITICA en el subsistema de Hardware. Se requiere revisión técnica.', 3, 'CRITICA', 'ASSIGNED', '2026-07-18 14:21:00', '2026-07-18 18:21:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (66, 'user_sergio', 'tech_carlos', 'Reporte #66: Incidencia técnica en Base de Datos', 'El usuario user_sergio reporta fallo de prioridad MEDIA en el subsistema de Base de Datos. Se requiere revisión técnica.', 6, 'MEDIA', 'CLOSED', '2026-07-19 17:34:00', '2026-07-21 18:34:00', '2026-07-20 20:34:00', '2026-07-21 18:34:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Base de Datos.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (67, 'user_ana', 'tech_david', 'Reporte #67: Incidencia técnica en Servidores', 'El usuario user_ana reporta fallo de prioridad BAJA en el subsistema de Servidores. Se requiere revisión técnica.', 5, 'BAJA', 'IN_PROGRESS', '2026-07-20 14:08:00', '2026-07-20 18:08:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (68, 'user_marta', 'tech_laura', 'Reporte #68: Incidencia técnica en Software', 'El usuario user_marta reporta fallo de prioridad BAJA en el subsistema de Software. Se requiere revisión técnica.', 2, 'BAJA', 'RESOLVED', '2026-07-21 13:13:00', '2026-07-22 11:13:00', '2026-07-22 11:13:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Software.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (69, 'user_marta', 'tech_laura', 'Reporte #69: Incidencia técnica en Hardware', 'El usuario user_marta reporta fallo de prioridad CRITICA en el subsistema de Hardware. Se requiere revisión técnica.', 3, 'CRITICA', 'CLOSED', '2026-07-22 12:53:00', '2026-07-24 00:53:00', '2026-07-23 20:53:00', '2026-07-24 00:53:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Hardware.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (70, 'user_marta', 'tech_carlos', 'Reporte #70: Incidencia técnica en Base de Datos', 'El usuario user_marta reporta fallo de prioridad BAJA en el subsistema de Base de Datos. Se requiere revisión técnica.', 6, 'BAJA', 'RESOLVED', '2026-07-24 18:04:00', '2026-07-24 21:04:00', '2026-07-24 21:04:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Base de Datos.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (71, 'user_ana', 'tech_david', 'Reporte #71: Incidencia técnica en Software', 'El usuario user_ana reporta fallo de prioridad MEDIA en el subsistema de Software. Se requiere revisión técnica.', 2, 'MEDIA', 'ASSIGNED', '2026-07-25 10:15:00', '2026-07-25 12:15:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (72, 'user_elena', 'tech_carlos', 'Reporte #72: Incidencia técnica en Seguridad', 'El usuario user_elena reporta fallo de prioridad BAJA en el subsistema de Seguridad. Se requiere revisión técnica.', 4, 'BAJA', 'RESOLVED', '2026-07-26 15:44:00', '2026-07-27 16:44:00', '2026-07-27 16:44:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Seguridad.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (73, 'user_felipe', NULL, 'Reporte #73: Incidencia técnica en Software', 'El usuario user_felipe reporta fallo de prioridad BAJA en el subsistema de Software. Se requiere revisión técnica.', 2, 'BAJA', 'OPEN', '2026-07-27 12:06:00', '2026-07-27 13:06:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (74, 'user_sergio', NULL, 'Reporte #74: Incidencia técnica en Hardware', 'El usuario user_sergio reporta fallo de prioridad CRITICA en el subsistema de Hardware. Se requiere revisión técnica.', 3, 'CRITICA', 'OPEN', '2026-07-28 11:04:00', '2026-07-28 13:04:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (75, 'user_elena', 'tech_carlos', 'Reporte #75: Incidencia técnica en Conectividad', 'El usuario user_elena reporta fallo de prioridad ALTA en el subsistema de Conectividad. Se requiere revisión técnica.', 1, 'ALTA', 'ASSIGNED', '2026-07-30 17:50:00', '2026-07-30 18:50:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (76, 'user_marta', 'tech_carlos', 'Reporte #76: Incidencia técnica en Hardware', 'El usuario user_marta reporta fallo de prioridad CRITICA en el subsistema de Hardware. Se requiere revisión técnica.', 3, 'CRITICA', 'CLOSED', '2026-07-31 16:41:00', '2026-08-01 11:41:00', '2026-07-31 18:41:00', '2026-08-01 11:41:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Hardware.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (77, 'user_ana', 'tech_laura', 'Reporte #77: Incidencia técnica en Accesos', 'El usuario user_ana reporta fallo de prioridad CRITICA en el subsistema de Accesos. Se requiere revisión técnica.', 7, 'CRITICA', 'RESOLVED', '2026-08-01 13:40:00', '2026-08-03 12:40:00', '2026-08-03 12:40:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Accesos.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (78, 'user_felipe', 'tech_david', 'Reporte #78: Incidencia técnica en Software', 'El usuario user_felipe reporta fallo de prioridad CRITICA en el subsistema de Software. Se requiere revisión técnica.', 2, 'CRITICA', 'ASSIGNED', '2026-08-02 16:41:00', '2026-08-02 19:41:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (79, 'user_sergio', 'tech_laura', 'Reporte #79: Incidencia técnica en Servidores', 'El usuario user_sergio reporta fallo de prioridad CRITICA en el subsistema de Servidores. Se requiere revisión técnica.', 5, 'CRITICA', 'CLOSED', '2026-08-03 17:17:00', '2026-08-04 16:17:00', '2026-08-04 10:17:00', '2026-08-04 16:17:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Servidores.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (80, 'user_felipe', 'tech_laura', 'Reporte #80: Incidencia técnica en Hardware', 'El usuario user_felipe reporta fallo de prioridad CRITICA en el subsistema de Hardware. Se requiere revisión técnica.', 3, 'CRITICA', 'RESOLVED', '2026-08-05 17:39:00', '2026-08-06 16:39:00', '2026-08-06 16:39:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Hardware.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (81, 'user_marta', 'tech_carlos', 'Reporte #81: Incidencia técnica en Conectividad', 'El usuario user_marta reporta fallo de prioridad CRITICA en el subsistema de Conectividad. Se requiere revisión técnica.', 1, 'CRITICA', 'RESOLVED', '2026-08-06 15:13:00', '2026-08-07 09:13:00', '2026-08-07 09:13:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Conectividad.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (82, 'user_elena', 'tech_david', 'Reporte #82: Incidencia técnica en Hardware', 'El usuario user_elena reporta fallo de prioridad ALTA en el subsistema de Hardware. Se requiere revisión técnica.', 3, 'ALTA', 'CLOSED', '2026-08-07 12:35:00', '2026-08-09 09:35:00', '2026-08-08 23:35:00', '2026-08-09 09:35:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Hardware.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (83, 'user_sergio', 'tech_laura', 'Reporte #83: Incidencia técnica en Conectividad', 'El usuario user_sergio reporta fallo de prioridad MEDIA en el subsistema de Conectividad. Se requiere revisión técnica.', 1, 'MEDIA', 'RESOLVED', '2026-08-08 16:48:00', '2026-08-10 14:48:00', '2026-08-10 14:48:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Conectividad.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (84, 'user_sergio', 'tech_carlos', 'Reporte #84: Incidencia técnica en Seguridad', 'El usuario user_sergio reporta fallo de prioridad CRITICA en el subsistema de Seguridad. Se requiere revisión técnica.', 4, 'CRITICA', 'RESOLVED', '2026-08-09 09:18:00', '2026-08-10 12:18:00', '2026-08-10 12:18:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Seguridad.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (85, 'user_marta', 'tech_david', 'Reporte #85: Incidencia técnica en Base de Datos', 'El usuario user_marta reporta fallo de prioridad MEDIA en el subsistema de Base de Datos. Se requiere revisión técnica.', 6, 'MEDIA', 'RESOLVED', '2026-08-11 17:23:00', '2026-08-13 06:23:00', '2026-08-13 06:23:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Base de Datos.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (86, 'user_sergio', 'tech_david', 'Reporte #86: Incidencia técnica en Servidores', 'El usuario user_sergio reporta fallo de prioridad ALTA en el subsistema de Servidores. Se requiere revisión técnica.', 5, 'ALTA', 'RESOLVED', '2026-08-12 16:21:00', '2026-08-14 14:21:00', '2026-08-14 14:21:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Servidores.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (87, 'user_marta', 'tech_laura', 'Reporte #87: Incidencia técnica en Seguridad', 'El usuario user_marta reporta fallo de prioridad ALTA en el subsistema de Seguridad. Se requiere revisión técnica.', 4, 'ALTA', 'CLOSED', '2026-08-13 11:07:00', '2026-08-14 16:07:00', '2026-08-14 09:07:00', '2026-08-14 16:07:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Seguridad.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (88, 'user_felipe', 'tech_carlos', 'Reporte #88: Incidencia técnica en Base de Datos', 'El usuario user_felipe reporta fallo de prioridad MEDIA en el subsistema de Base de Datos. Se requiere revisión técnica.', 6, 'MEDIA', 'ASSIGNED', '2026-08-14 15:17:00', '2026-08-14 18:17:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (89, 'user_marta', 'tech_carlos', 'Reporte #89: Incidencia técnica en Conectividad', 'El usuario user_marta reporta fallo de prioridad MEDIA en el subsistema de Conectividad. Se requiere revisión técnica.', 1, 'MEDIA', 'RESOLVED', '2026-08-15 13:11:00', '2026-08-15 15:11:00', '2026-08-15 15:11:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Conectividad.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (90, 'user_marta', 'tech_carlos', 'Reporte #90: Incidencia técnica en Base de Datos', 'El usuario user_marta reporta fallo de prioridad MEDIA en el subsistema de Base de Datos. Se requiere revisión técnica.', 6, 'MEDIA', 'RESOLVED', '2026-08-17 08:35:00', '2026-08-19 06:35:00', '2026-08-19 06:35:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Base de Datos.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (91, 'user_ana', 'tech_carlos', 'Reporte #91: Incidencia técnica en Software', 'El usuario user_ana reporta fallo de prioridad CRITICA en el subsistema de Software. Se requiere revisión técnica.', 2, 'CRITICA', 'CLOSED', '2026-08-18 17:18:00', '2026-08-20 19:18:00', '2026-08-20 01:18:00', '2026-08-20 19:18:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Software.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (92, 'user_ana', 'tech_laura', 'Reporte #92: Incidencia técnica en Hardware', 'El usuario user_ana reporta fallo de prioridad MEDIA en el subsistema de Hardware. Se requiere revisión técnica.', 3, 'MEDIA', 'RESOLVED', '2026-08-19 15:07:00', '2026-08-20 18:07:00', '2026-08-20 18:07:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Hardware.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (93, 'user_elena', NULL, 'Reporte #93: Incidencia técnica en Seguridad', 'El usuario user_elena reporta fallo de prioridad BAJA en el subsistema de Seguridad. Se requiere revisión técnica.', 4, 'BAJA', 'OPEN', '2026-08-20 18:43:00', '2026-08-20 19:43:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (94, 'user_elena', 'tech_laura', 'Reporte #94: Incidencia técnica en Software', 'El usuario user_elena reporta fallo de prioridad MEDIA en el subsistema de Software. Se requiere revisión técnica.', 2, 'MEDIA', 'IN_PROGRESS', '2026-08-21 09:15:00', '2026-08-21 10:15:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (95, 'user_elena', 'tech_david', 'Reporte #95: Incidencia técnica en Servidores', 'El usuario user_elena reporta fallo de prioridad CRITICA en el subsistema de Servidores. Se requiere revisión técnica.', 5, 'CRITICA', 'ASSIGNED', '2026-08-23 17:14:00', '2026-08-23 21:14:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (96, 'user_marta', 'tech_david', 'Reporte #96: Incidencia técnica en Seguridad', 'El usuario user_marta reporta fallo de prioridad CRITICA en el subsistema de Seguridad. Se requiere revisión técnica.', 4, 'CRITICA', 'RESOLVED', '2026-08-24 14:19:00', '2026-08-26 07:19:00', '2026-08-26 07:19:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Seguridad.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (97, 'user_felipe', 'tech_david', 'Reporte #97: Incidencia técnica en Base de Datos', 'El usuario user_felipe reporta fallo de prioridad BAJA en el subsistema de Base de Datos. Se requiere revisión técnica.', 6, 'BAJA', 'RESOLVED', '2026-08-25 11:16:00', '2026-08-25 23:16:00', '2026-08-25 23:16:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Base de Datos.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (98, 'user_elena', 'tech_carlos', 'Reporte #98: Incidencia técnica en Software', 'El usuario user_elena reporta fallo de prioridad MEDIA en el subsistema de Software. Se requiere revisión técnica.', 2, 'MEDIA', 'ASSIGNED', '2026-08-26 10:00:00', '2026-08-26 14:00:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (99, 'user_marta', 'tech_carlos', 'Reporte #99: Incidencia técnica en Seguridad', 'El usuario user_marta reporta fallo de prioridad CRITICA en el subsistema de Seguridad. Se requiere revisión técnica.', 4, 'CRITICA', 'CLOSED', '2026-08-27 11:18:00', '2026-08-30 03:18:00', '2026-08-29 09:18:00', '2026-08-30 03:18:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Seguridad.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (100, 'user_marta', NULL, 'Reporte #100: Incidencia técnica en Conectividad', 'El usuario user_marta reporta fallo de prioridad MEDIA en el subsistema de Conectividad. Se requiere revisión técnica.', 1, 'MEDIA', 'OPEN', '2026-08-29 18:37:00', '2026-08-29 20:37:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (101, 'user_elena', 'tech_carlos', 'Reporte #101: Incidencia técnica en Seguridad', 'El usuario user_elena reporta fallo de prioridad BAJA en el subsistema de Seguridad. Se requiere revisión técnica.', 4, 'BAJA', 'RESOLVED', '2026-08-30 18:09:00', '2026-08-31 05:09:00', '2026-08-31 05:09:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Seguridad.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (102, 'user_felipe', NULL, 'Reporte #102: Incidencia técnica en Conectividad', 'El usuario user_felipe reporta fallo de prioridad BAJA en el subsistema de Conectividad. Se requiere revisión técnica.', 1, 'BAJA', 'OPEN', '2026-08-31 12:38:00', '2026-08-31 15:38:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (103, 'user_sergio', 'tech_david', 'Reporte #103: Incidencia técnica en Seguridad', 'El usuario user_sergio reporta fallo de prioridad BAJA en el subsistema de Seguridad. Se requiere revisión técnica.', 4, 'BAJA', 'IN_PROGRESS', '2026-09-01 12:44:00', '2026-09-01 16:44:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (104, 'user_sergio', 'tech_carlos', 'Reporte #104: Incidencia técnica en Hardware', 'El usuario user_sergio reporta fallo de prioridad CRITICA en el subsistema de Hardware. Se requiere revisión técnica.', 3, 'CRITICA', 'IN_PROGRESS', '2026-09-02 17:02:00', '2026-09-02 21:02:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (105, 'user_elena', 'tech_laura', 'Reporte #105: Incidencia técnica en Base de Datos', 'El usuario user_elena reporta fallo de prioridad ALTA en el subsistema de Base de Datos. Se requiere revisión técnica.', 6, 'ALTA', 'RESOLVED', '2026-09-03 08:05:00', '2026-09-05 05:05:00', '2026-09-05 05:05:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Base de Datos.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (106, 'user_marta', NULL, 'Reporte #106: Incidencia técnica en Accesos', 'El usuario user_marta reporta fallo de prioridad BAJA en el subsistema de Accesos. Se requiere revisión técnica.', 7, 'BAJA', 'OPEN', '2026-09-05 17:02:00', '2026-09-05 19:02:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (107, 'user_marta', 'tech_carlos', 'Reporte #107: Incidencia técnica en Seguridad', 'El usuario user_marta reporta fallo de prioridad CRITICA en el subsistema de Seguridad. Se requiere revisión técnica.', 4, 'CRITICA', 'CLOSED', '2026-09-06 17:27:00', '2026-09-07 19:27:00', '2026-09-07 00:27:00', '2026-09-07 19:27:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Seguridad.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (108, 'user_marta', 'tech_laura', 'Reporte #108: Incidencia técnica en Hardware', 'El usuario user_marta reporta fallo de prioridad CRITICA en el subsistema de Hardware. Se requiere revisión técnica.', 3, 'CRITICA', 'ASSIGNED', '2026-09-07 18:06:00', '2026-09-07 20:06:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (109, 'user_sergio', 'tech_laura', 'Reporte #109: Incidencia técnica en Hardware', 'El usuario user_sergio reporta fallo de prioridad CRITICA en el subsistema de Hardware. Se requiere revisión técnica.', 3, 'CRITICA', 'RESOLVED', '2026-09-08 18:25:00', '2026-09-10 01:25:00', '2026-09-10 01:25:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Hardware.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (110, 'user_marta', 'tech_laura', 'Reporte #110: Incidencia técnica en Conectividad', 'El usuario user_marta reporta fallo de prioridad ALTA en el subsistema de Conectividad. Se requiere revisión técnica.', 1, 'ALTA', 'ASSIGNED', '2026-09-10 09:49:00', '2026-09-10 13:49:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (111, 'user_elena', 'tech_laura', 'Reporte #111: Incidencia técnica en Accesos', 'El usuario user_elena reporta fallo de prioridad BAJA en el subsistema de Accesos. Se requiere revisión técnica.', 7, 'BAJA', 'CLOSED', '2026-09-11 14:03:00', '2026-09-13 16:03:00', '2026-09-13 01:03:00', '2026-09-13 16:03:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Accesos.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (112, 'user_sergio', 'tech_carlos', 'Reporte #112: Incidencia técnica en Servidores', 'El usuario user_sergio reporta fallo de prioridad CRITICA en el subsistema de Servidores. Se requiere revisión técnica.', 5, 'CRITICA', 'ASSIGNED', '2026-09-12 11:17:00', '2026-09-12 13:17:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (113, 'user_sergio', NULL, 'Reporte #113: Incidencia técnica en Hardware', 'El usuario user_sergio reporta fallo de prioridad CRITICA en el subsistema de Hardware. Se requiere revisión técnica.', 3, 'CRITICA', 'OPEN', '2026-09-13 09:01:00', '2026-09-13 11:01:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (114, 'user_marta', 'tech_david', 'Reporte #114: Incidencia técnica en Base de Datos', 'El usuario user_marta reporta fallo de prioridad MEDIA en el subsistema de Base de Datos. Se requiere revisión técnica.', 6, 'MEDIA', 'ASSIGNED', '2026-09-14 08:35:00', '2026-09-14 12:35:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (115, 'user_ana', NULL, 'Reporte #115: Incidencia técnica en Conectividad', 'El usuario user_ana reporta fallo de prioridad MEDIA en el subsistema de Conectividad. Se requiere revisión técnica.', 1, 'MEDIA', 'OPEN', '2026-09-16 15:07:00', '2026-09-16 17:07:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (116, 'user_elena', NULL, 'Reporte #116: Incidencia técnica en Seguridad', 'El usuario user_elena reporta fallo de prioridad ALTA en el subsistema de Seguridad. Se requiere revisión técnica.', 4, 'ALTA', 'OPEN', '2026-09-17 12:26:00', '2026-09-17 16:26:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (117, 'user_sergio', 'tech_david', 'Reporte #117: Incidencia técnica en Seguridad', 'El usuario user_sergio reporta fallo de prioridad MEDIA en el subsistema de Seguridad. Se requiere revisión técnica.', 4, 'MEDIA', 'CLOSED', '2026-09-18 10:24:00', '2026-09-20 22:24:00', '2026-09-20 02:24:00', '2026-09-20 22:24:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Seguridad.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (118, 'user_ana', 'tech_laura', 'Reporte #118: Incidencia técnica en Base de Datos', 'El usuario user_ana reporta fallo de prioridad MEDIA en el subsistema de Base de Datos. Se requiere revisión técnica.', 6, 'MEDIA', 'CLOSED', '2026-09-19 14:21:00', '2026-09-20 05:21:00', '2026-09-19 16:21:00', '2026-09-20 05:21:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Base de Datos.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (119, 'user_elena', 'tech_david', 'Reporte #119: Incidencia técnica en Base de Datos', 'El usuario user_elena reporta fallo de prioridad ALTA en el subsistema de Base de Datos. Se requiere revisión técnica.', 6, 'ALTA', 'ASSIGNED', '2026-09-20 18:31:00', '2026-09-20 20:31:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (120, 'user_marta', NULL, 'Reporte #120: Incidencia técnica en Seguridad', 'El usuario user_marta reporta fallo de prioridad CRITICA en el subsistema de Seguridad. Se requiere revisión técnica.', 4, 'CRITICA', 'OPEN', '2026-09-22 13:35:00', '2026-09-22 17:35:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (121, 'user_felipe', 'tech_david', 'Reporte #121: Incidencia técnica en Seguridad', 'El usuario user_felipe reporta fallo de prioridad ALTA en el subsistema de Seguridad. Se requiere revisión técnica.', 4, 'ALTA', 'CLOSED', '2026-09-23 11:36:00', '2026-09-24 20:36:00', '2026-09-24 03:36:00', '2026-09-24 20:36:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Seguridad.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (122, 'user_sergio', 'tech_david', 'Reporte #122: Incidencia técnica en Conectividad', 'El usuario user_sergio reporta fallo de prioridad ALTA en el subsistema de Conectividad. Se requiere revisión técnica.', 1, 'ALTA', 'ASSIGNED', '2026-09-24 14:24:00', '2026-09-24 16:24:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (123, 'user_felipe', 'tech_david', 'Reporte #123: Incidencia técnica en Seguridad', 'El usuario user_felipe reporta fallo de prioridad BAJA en el subsistema de Seguridad. Se requiere revisión técnica.', 4, 'BAJA', 'IN_PROGRESS', '2026-09-25 17:21:00', '2026-09-25 18:21:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (124, 'user_ana', 'tech_david', 'Reporte #124: Incidencia técnica en Accesos', 'El usuario user_ana reporta fallo de prioridad CRITICA en el subsistema de Accesos. Se requiere revisión técnica.', 7, 'CRITICA', 'RESOLVED', '2026-09-26 15:00:00', '2026-09-27 19:00:00', '2026-09-27 19:00:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Accesos.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (125, 'user_ana', NULL, 'Reporte #125: Incidencia técnica en Accesos', 'El usuario user_ana reporta fallo de prioridad MEDIA en el subsistema de Accesos. Se requiere revisión técnica.', 7, 'MEDIA', 'OPEN', '2026-09-28 15:50:00', '2026-09-28 18:50:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (126, 'user_ana', 'tech_laura', 'Reporte #126: Incidencia técnica en Hardware', 'El usuario user_ana reporta fallo de prioridad CRITICA en el subsistema de Hardware. Se requiere revisión técnica.', 3, 'CRITICA', 'RESOLVED', '2026-09-29 18:54:00', '2026-09-30 16:54:00', '2026-09-30 16:54:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Hardware.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (127, 'user_elena', 'tech_carlos', 'Reporte #127: Incidencia técnica en Base de Datos', 'El usuario user_elena reporta fallo de prioridad CRITICA en el subsistema de Base de Datos. Se requiere revisión técnica.', 6, 'CRITICA', 'RESOLVED', '2026-09-30 17:04:00', '2026-10-02 11:04:00', '2026-10-02 11:04:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Base de Datos.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (128, 'user_felipe', NULL, 'Reporte #128: Incidencia técnica en Base de Datos', 'El usuario user_felipe reporta fallo de prioridad ALTA en el subsistema de Base de Datos. Se requiere revisión técnica.', 6, 'ALTA', 'OPEN', '2026-10-01 09:27:00', '2026-10-01 10:27:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (129, 'user_sergio', 'tech_carlos', 'Reporte #129: Incidencia técnica en Accesos', 'El usuario user_sergio reporta fallo de prioridad BAJA en el subsistema de Accesos. Se requiere revisión técnica.', 7, 'BAJA', 'IN_PROGRESS', '2026-10-02 12:57:00', '2026-10-02 13:57:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (130, 'user_ana', 'tech_laura', 'Reporte #130: Incidencia técnica en Conectividad', 'El usuario user_ana reporta fallo de prioridad ALTA en el subsistema de Conectividad. Se requiere revisión técnica.', 1, 'ALTA', 'RESOLVED', '2026-10-04 13:23:00', '2026-10-05 00:23:00', '2026-10-05 00:23:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Conectividad.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (131, 'user_elena', 'tech_david', 'Reporte #131: Incidencia técnica en Software', 'El usuario user_elena reporta fallo de prioridad CRITICA en el subsistema de Software. Se requiere revisión técnica.', 2, 'CRITICA', 'IN_PROGRESS', '2026-10-05 10:10:00', '2026-10-05 12:10:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (132, 'user_elena', 'tech_david', 'Reporte #132: Incidencia técnica en Conectividad', 'El usuario user_elena reporta fallo de prioridad CRITICA en el subsistema de Conectividad. Se requiere revisión técnica.', 1, 'CRITICA', 'RESOLVED', '2026-10-06 11:31:00', '2026-10-07 03:31:00', '2026-10-07 03:31:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Conectividad.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (133, 'user_sergio', 'tech_laura', 'Reporte #133: Incidencia técnica en Seguridad', 'El usuario user_sergio reporta fallo de prioridad ALTA en el subsistema de Seguridad. Se requiere revisión técnica.', 4, 'ALTA', 'IN_PROGRESS', '2026-10-07 18:00:00', '2026-10-07 22:00:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (134, 'user_ana', 'tech_laura', 'Reporte #134: Incidencia técnica en Hardware', 'El usuario user_ana reporta fallo de prioridad MEDIA en el subsistema de Hardware. Se requiere revisión técnica.', 3, 'MEDIA', 'CLOSED', '2026-10-08 13:37:00', '2026-10-11 00:37:00', '2026-10-10 07:37:00', '2026-10-11 00:37:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Hardware.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (135, 'user_sergio', 'tech_laura', 'Reporte #135: Incidencia técnica en Base de Datos', 'El usuario user_sergio reporta fallo de prioridad ALTA en el subsistema de Base de Datos. Se requiere revisión técnica.', 6, 'ALTA', 'CLOSED', '2026-10-10 11:24:00', '2026-10-11 06:24:00', '2026-10-10 19:24:00', '2026-10-11 06:24:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Base de Datos.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (136, 'user_elena', 'tech_laura', 'Reporte #136: Incidencia técnica en Seguridad', 'El usuario user_elena reporta fallo de prioridad ALTA en el subsistema de Seguridad. Se requiere revisión técnica.', 4, 'ALTA', 'IN_PROGRESS', '2026-10-11 12:01:00', '2026-10-11 16:01:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (137, 'user_elena', 'tech_david', 'Reporte #137: Incidencia técnica en Hardware', 'El usuario user_elena reporta fallo de prioridad BAJA en el subsistema de Hardware. Se requiere revisión técnica.', 3, 'BAJA', 'IN_PROGRESS', '2026-10-12 08:58:00', '2026-10-12 12:58:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (138, 'user_felipe', NULL, 'Reporte #138: Incidencia técnica en Accesos', 'El usuario user_felipe reporta fallo de prioridad ALTA en el subsistema de Accesos. Se requiere revisión técnica.', 7, 'ALTA', 'OPEN', '2026-10-13 17:51:00', '2026-10-13 20:51:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (139, 'user_elena', NULL, 'Reporte #139: Incidencia técnica en Software', 'El usuario user_elena reporta fallo de prioridad MEDIA en el subsistema de Software. Se requiere revisión técnica.', 2, 'MEDIA', 'OPEN', '2026-10-14 12:43:00', '2026-10-14 14:43:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (140, 'user_ana', NULL, 'Reporte #140: Incidencia técnica en Base de Datos', 'El usuario user_ana reporta fallo de prioridad BAJA en el subsistema de Base de Datos. Se requiere revisión técnica.', 6, 'BAJA', 'OPEN', '2026-10-16 12:50:00', '2026-10-16 16:50:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (141, 'user_felipe', 'tech_carlos', 'Reporte #141: Incidencia técnica en Conectividad', 'El usuario user_felipe reporta fallo de prioridad ALTA en el subsistema de Conectividad. Se requiere revisión técnica.', 1, 'ALTA', 'RESOLVED', '2026-10-17 12:20:00', '2026-10-18 01:20:00', '2026-10-18 01:20:00', NULL, 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Conectividad.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (142, 'user_elena', NULL, 'Reporte #142: Incidencia técnica en Software', 'El usuario user_elena reporta fallo de prioridad MEDIA en el subsistema de Software. Se requiere revisión técnica.', 2, 'MEDIA', 'OPEN', '2026-10-18 13:33:00', '2026-10-18 16:33:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (143, 'user_marta', 'tech_laura', 'Reporte #143: Incidencia técnica en Accesos', 'El usuario user_marta reporta fallo de prioridad MEDIA en el subsistema de Accesos. Se requiere revisión técnica.', 7, 'MEDIA', 'CLOSED', '2026-10-19 12:47:00', '2026-10-20 15:47:00', '2026-10-19 21:47:00', '2026-10-20 15:47:00', 'Incidente diagnosticado y resuelto exitosamente. Se aplicó procedimiento técnico de soporte en Accesos.')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (144, 'user_felipe', 'tech_david', 'Reporte #144: Incidencia técnica en Conectividad', 'El usuario user_felipe reporta fallo de prioridad MEDIA en el subsistema de Conectividad. Se requiere revisión técnica.', 1, 'MEDIA', 'ASSIGNED', '2026-10-20 18:25:00', '2026-10-20 21:25:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (145, 'user_ana', 'tech_laura', 'Reporte #145: Incidencia técnica en Conectividad', 'El usuario user_ana reporta fallo de prioridad CRITICA en el subsistema de Conectividad. Se requiere revisión técnica.', 1, 'CRITICA', 'ASSIGNED', '2026-10-22 16:07:00', '2026-10-22 20:07:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (146, 'user_elena', 'tech_laura', 'Reporte #146: Incidencia técnica en Hardware', 'El usuario user_elena reporta fallo de prioridad ALTA en el subsistema de Hardware. Se requiere revisión técnica.', 3, 'ALTA', 'IN_PROGRESS', '2026-10-23 18:23:00', '2026-10-23 19:23:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (147, 'user_sergio', 'tech_carlos', 'Reporte #147: Incidencia técnica en Base de Datos', 'El usuario user_sergio reporta fallo de prioridad MEDIA en el subsistema de Base de Datos. Se requiere revisión técnica.', 6, 'MEDIA', 'IN_PROGRESS', '2026-10-24 17:56:00', '2026-10-24 20:56:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (148, 'user_ana', 'tech_david', 'Reporte #148: Incidencia técnica en Servidores', 'El usuario user_ana reporta fallo de prioridad MEDIA en el subsistema de Servidores. Se requiere revisión técnica.', 5, 'MEDIA', 'IN_PROGRESS', '2026-10-25 15:58:00', '2026-10-25 18:58:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (149, 'user_ana', NULL, 'Reporte #149: Incidencia técnica en Base de Datos', 'El usuario user_ana reporta fallo de prioridad CRITICA en el subsistema de Base de Datos. Se requiere revisión técnica.', 6, 'CRITICA', 'OPEN', '2026-10-26 10:02:00', '2026-10-26 11:02:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
INSERT INTO public.tickets (id, applicant_username, technician_username, title, description, category_id, priority, status, created_at, updated_at, resolved_at, closed_at, resolution_note)
OVERRIDING SYSTEM VALUE
VALUES (150, 'user_ana', 'tech_carlos', 'Reporte #150: Incidencia técnica en Hardware', 'El usuario user_ana reporta fallo de prioridad CRITICA en el subsistema de Hardware. Se requiere revisión técnica.', 3, 'CRITICA', 'ASSIGNED', '2026-10-28 11:56:00', '2026-10-28 13:56:00', NULL, NULL, NULL)
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status, resolved_at = EXCLUDED.resolved_at;
SELECT setval('public.tickets_id_seq', (SELECT MAX(id) FROM public.tickets));
