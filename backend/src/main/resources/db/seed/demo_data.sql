-- =====================================================================================
-- CampusDesk — datos iniciales de demostración (PostgreSQL)
--
-- Contenido: 13 usuarios, 31 artículos de conocimiento y 180 incidentes con su historial
-- de estados, asignaciones y comentarios. Las 7 categorías las crea la migración V3.
--
-- Datos SINTÉTICOS y DETERMINISTAS: no se usa random(); cada valor sale de md5(semilla:n),
-- de modo que cargar el script dos veces en bases distintas produce exactamente los mismos
-- registros y los mismos indicadores. Fecha de corte del conjunto: 2026-11-09 08:00
-- (America/Bogota); ningún evento es posterior a esa fecha.
--
-- NO se ejecuta al arrancar la aplicación ni forma parte de las migraciones. Carga explícita,
-- después de arrancar la aplicación una vez (Flyway crea el esquema y el arranque crea los roles):
--
--   psql -v ON_ERROR_STOP=1 -U <usuario> -d <base> -f backend/src/main/resources/db/seed/demo_data.sql
--   docker exec -i <contenedor> psql -v ON_ERROR_STOP=1 -U <usuario> -d <base> < backend/src/main/resources/db/seed/demo_data.sql
--
-- SOLO PARA DEMOSTRACIÓN: todos los usuarios comparten la contraseña "Demo.2026" y no deben
-- cargarse en un despliegue real. El script aborta si los datos ya fueron cargados.
-- =====================================================================================

BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM public.app_role WHERE name = 'TECHNICIAN') THEN
        RAISE EXCEPTION 'Faltan los roles: arranca la aplicación una vez antes de cargar los datos de demostración';
    END IF;
    IF (SELECT count(*) FROM public.categories) < 7 THEN
        RAISE EXCEPTION 'Faltan las categorías base de la migración V3';
    END IF;
    IF EXISTS (SELECT 1 FROM public."user" WHERE email LIKE '%@demo.campusdesk.local') THEN
        RAISE EXCEPTION 'Los datos de demostración ya están cargados en esta base';
    END IF;
END $$;

-- Pseudoaleatorio determinista en [0, modulo): 28 bits de md5(semilla:n).
CREATE FUNCTION pg_temp.demo_rnd(n integer, salt text, modulo integer) RETURNS integer
LANGUAGE sql IMMUTABLE AS $$
    SELECT (('x' || substr(md5(salt || ':' || n::text), 1, 7))::bit(28)::int % modulo)
$$;

-- -------------------------------------------------------------------------------------
-- 1. Usuarios: 8 solicitantes, 4 técnicos y 1 administrador. Hash BCrypt de "Demo.2026".
-- -------------------------------------------------------------------------------------
INSERT INTO public."user" (username, email, password, locked, disabled, must_change_password, credential_version, full_name, area)
SELECT u.username, u.username || '@demo.campusdesk.local',
       '$2a$10$3TSEYLZ2F8kFlTcw0Wue2e4u.3wYq5MjhkqmUts/x0eVe2FwiUqgm', false, false, false, 0, u.full_name, u.area
FROM (VALUES
    ('laura.gomez',      'Laura Gómez',     'Contabilidad'),
    ('carlos.ruiz',      'Carlos Ruiz',     'Recursos Humanos'),
    ('maria.perez',      'María Pérez',     'Ventas'),
    ('andres.castro',    'Andrés Castro',   'Logística'),
    ('diana.mora',       'Diana Mora',      'Mercadeo'),
    ('felipe.rojas',     'Felipe Rojas',    'Jurídica'),
    ('sofia.vargas',     'Sofía Vargas',    'Gerencia'),
    ('jorge.nino',       'Jorge Niño',      'Producción'),
    ('tec.redes',        'Camilo Herrera',  'Soporte - Redes'),
    ('tec.hardware',     'Paola Díaz',      'Soporte - Hardware'),
    ('tec.software',     'Julián Ortiz',    'Soporte - Software'),
    ('tec.aplicaciones', 'Natalia Suárez',  'Soporte - Aplicaciones'),
    ('admin.demo',       'Marta Salazar',   'Coordinación de soporte')
) AS u(username, full_name, area);

INSERT INTO public.user_role (username, role, granted_date)
SELECT username,
       CASE WHEN username = 'admin.demo' THEN 'ADMIN' WHEN username LIKE 'tec.%' THEN 'TECHNICIAN' ELSE 'USER' END,
       timestamp '2026-04-01 08:00:00'
FROM public."user" WHERE email LIKE '%@demo.campusdesk.local';

INSERT INTO public.technician_profiles (username, availability_status, active_tickets_count, specialty, last_assigned_at)
VALUES ('tec.redes', 'AVAILABLE', 0, 'Redes y conectividad', NULL),
       ('tec.hardware', 'AVAILABLE', 0, 'Hardware y periféricos', NULL),
       ('tec.software', 'AVAILABLE', 0, 'Sistema operativo, ofimática y accesos', NULL),
       ('tec.aplicaciones', 'AVAILABLE', 0, 'Aplicaciones y servicios institucionales', NULL);

-- -------------------------------------------------------------------------------------
-- 2. Base de conocimiento: 28 artículos publicados (verificados y activos), 1 verificado e
--    inactivo (procedimiento retirado) y 2 borradores. Los publicados quedan con el índice
--    PENDING: se envían a n8n con POST /api/knowledge/articles/index/reconcile.
--    content_hash replica el cálculo de la aplicación: SHA-256 de cada campo seguido de 0x1F.
-- -------------------------------------------------------------------------------------
INSERT INTO public.knowledge_articles (title, problem, symptoms, cause, solution, author_username, verified,
        verified_by_username, verified_at, active, content_version, content_hash, index_status, created_at, updated_at)
SELECT a.title, a.problem, a.symptoms, a.cause, a.solution, a.author,
       a.state <> 'DRAFT',
       CASE WHEN a.state <> 'DRAFT' THEN 'admin.demo' END,
       CASE WHEN a.state <> 'DRAFT' THEN timestamp '2026-04-06 09:00:00' + make_interval(days => a.ord) END,
       a.state = 'PUB',
       1,
       encode(sha256(convert_to(a.title || chr(31) || a.problem || chr(31) || a.symptoms || chr(31) || a.cause || chr(31)
               || a.solution || chr(31), 'UTF8')), 'hex'),
       CASE WHEN a.state = 'PUB' THEN 'PENDING' ELSE 'NOT_INDEXED' END,
       timestamp '2026-04-03 10:00:00' + make_interval(days => a.ord),
       timestamp '2026-04-06 09:00:00' + make_interval(days => a.ord)
FROM (VALUES
    -- Conectividad
    (1, 'PUB', 'tec.redes', 'Equipo sin acceso a internet: dirección IP 169.254.x.x',
     'El equipo no obtiene una dirección IP del servidor DHCP y queda sin acceso a la red interna ni a internet.',
     'El icono de red muestra una advertencia. El comando ipconfig informa una dirección 169.254.x.x. No abre ninguna página ni recurso interno.',
     'El cliente DHCP no recibió una concesión: cable o punto de red defectuoso, puerto del switch sin enlace o concesión vencida que no se renovó.',
     '1. Verificar que el cable esté conectado y que el puerto de red tenga luz de enlace. 2. Abrir una consola como administrador y ejecutar ipconfig /release y luego ipconfig /renew. 3. Si persiste, deshabilitar y habilitar el adaptador de red. 4. Probar con otro cable u otro punto de red. 5. Si ningún equipo del área obtiene dirección, escalar a redes para revisar el servidor DHCP.'),
    (2, 'PUB', 'tec.redes', 'Sin navegación por fallo de resolución DNS',
     'El equipo tiene conexión de red pero no resuelve nombres de dominio, por lo que no abre páginas web ni aplicaciones por nombre.',
     'El navegador muestra DNS_PROBE_FINISHED_NXDOMAIN o «no se puede encontrar el servidor». El ping a una dirección IP responde, pero el ping a un nombre falla.',
     'Servidores DNS mal configurados en el adaptador o caché DNS local dañada.',
     '1. Ejecutar ping 8.8.8.8 para confirmar que hay conectividad. 2. Ejecutar ipconfig /flushdns. 3. Revisar que el adaptador obtenga los DNS automáticamente o tenga los DNS institucionales. 4. Ejecutar nslookup con un dominio conocido para validar. 5. Si falla en varios equipos, escalar a redes para revisar el servidor DNS.'),
    (3, 'PUB', 'tec.redes', 'Wi-Fi corporativo conectado pero sin internet',
     'El equipo se conecta a la red inalámbrica corporativa pero no navega.',
     'El sistema indica «Conectado, sin internet». Otros equipos cercanos sí navegan. La señal aparece con intensidad normal.',
     'Perfil inalámbrico con credenciales antiguas, portal de autenticación sin completar o dirección IP en conflicto.',
     '1. Olvidar la red Wi-Fi y volver a conectarse con el usuario de dominio. 2. Abrir el navegador para completar el portal de autenticación si aparece. 3. Ejecutar ipconfig /release e ipconfig /renew. 4. Reiniciar el adaptador inalámbrico. 5. Si la falla afecta a toda la zona, reportar el punto de acceso a redes.'),
    (4, 'PUB', 'tec.redes', 'La VPN no conecta: error 809 o tiempo de espera agotado',
     'El usuario no logra establecer la conexión VPN desde fuera de la oficina.',
     'El cliente VPN muestra el error 809 o «se agotó el tiempo de espera». Internet funciona con normalidad sin la VPN.',
     'El router o el proveedor bloquean los puertos de IPsec, el cliente VPN está desactualizado o la cuenta no tiene habilitado el acceso remoto.',
     '1. Confirmar que el equipo navega sin la VPN. 2. Actualizar el cliente VPN a la versión publicada por soporte. 3. Probar desde otra red, por ejemplo compartiendo datos del celular, para descartar bloqueo del router. 4. Verificar con el administrador que la cuenta pertenezca al grupo de acceso remoto. 5. Si el error continúa, enviar a redes la hora exacta del intento para revisar el registro del concentrador.'),
    (5, 'PUB', 'tec.redes', 'Conexión lenta o intermitente en la red cableada',
     'La conexión por cable se cae por momentos o funciona con mucha lentitud.',
     'Las videollamadas se cortan, las descargas se interrumpen y el icono de red alterna entre conectado y desconectado.',
     'Cable o conector dañado, negociación incorrecta de velocidad del adaptador o puerto del switch con errores.',
     '1. Reemplazar el cable de red por uno certificado. 2. Conectar el equipo a otro punto de red para comparar. 3. Revisar en el adaptador que la velocidad esté en negociación automática. 4. Actualizar el controlador de la tarjeta de red. 5. Si el problema sigue al punto de red, solicitar a redes la revisión del puerto del switch.'),
    (6, 'PUB', 'tec.redes', 'Sin internet en toda una sede: verificación del enlace principal',
     'Todos los equipos de una sede pierden el acceso a internet al mismo tiempo.',
     'Ningún equipo navega, ni por cable ni por Wi-Fi. Los recursos internos de la misma sede sí responden. Varios usuarios reportan la falla de forma simultánea.',
     'Caída del enlace del proveedor o falla del router de borde de la sede.',
     '1. Confirmar con dos o más usuarios que la falla es general. 2. Revisar las luces del router de borde y del equipo del proveedor. 3. Reiniciar el equipo del proveedor solo si el procedimiento de la sede lo autoriza. 4. Abrir el caso con el proveedor indicando el número del enlace. 5. Activar el enlace de respaldo y comunicar a los usuarios el tiempo estimado de solución.'),
    (7, 'PUB', 'tec.redes', 'No se puede acceder a las carpetas compartidas de red',
     'El usuario no puede abrir las unidades o carpetas compartidas del servidor de archivos.',
     'Las unidades de red aparecen con una X roja. Al abrirlas se muestra «No se encuentra la ruta de acceso de la red».',
     'El equipo está fuera de la red interna o sin VPN, la unidad quedó desconectada o las credenciales guardadas vencieron.',
     '1. Verificar que el equipo esté en la red interna o con la VPN activa. 2. Hacer ping al servidor de archivos. 3. Desconectar la unidad de red y volver a asignarla. 4. Eliminar las credenciales guardadas en el Administrador de credenciales e iniciar sesión de nuevo. 5. Si pide permisos, tramitarlos con el responsable de la carpeta.'),
    -- Aplicaciones
    (8, 'PUB', 'tec.aplicaciones', 'La aplicación no inicia: error 0xc000007b',
     'Una aplicación de escritorio no abre y muestra el error 0xc000007b.',
     'Al ejecutar el programa aparece «La aplicación no se pudo iniciar correctamente (0xc000007b)». El resto de programas funciona.',
     'Bibliotecas de Visual C++ dañadas o de una arquitectura distinta a la de la aplicación (32 y 64 bits mezclados).',
     '1. Desinstalar las versiones dañadas de Microsoft Visual C++ Redistributable. 2. Instalar los paquetes x86 y x64 desde el repositorio de soporte. 3. Reiniciar el equipo. 4. Ejecutar la aplicación como administrador para validar. 5. Si el error persiste, reinstalar la aplicación.'),
    (9, 'PUB', 'tec.aplicaciones', 'Error «Falta MSVCP140.dll» o «VCRUNTIME140.dll» al abrir una aplicación',
     'La aplicación no abre porque no encuentra una biblioteca de ejecución.',
     'Mensaje «No se puede continuar la ejecución del código porque no se encontró MSVCP140.dll» o VCRUNTIME140.dll.',
     'No está instalado Microsoft Visual C++ Redistributable 2015-2022 o su instalación quedó incompleta.',
     '1. Instalar Microsoft Visual C++ Redistributable 2015-2022 en sus versiones x86 y x64. 2. Reiniciar el equipo. 3. Abrir de nuevo la aplicación. 4. No descargar archivos DLL sueltos de internet: representan un riesgo de seguridad.'),
    (10, 'PUB', 'tec.aplicaciones', 'La aplicación de nómina se cierra inesperadamente al iniciar',
     'El sistema de nómina se cierra pocos segundos después de abrirlo.',
     'Aparece la ventana de inicio y el programa se cierra sin mensaje, o muestra «El programa dejó de funcionar».',
     'Archivos temporales del perfil de la aplicación dañados después de una actualización o de un apagado inesperado.',
     '1. Cerrar la aplicación desde el Administrador de tareas. 2. Borrar la carpeta de caché de la aplicación dentro de %LOCALAPPDATA%. 3. Abrir de nuevo el programa para que regenere la configuración. 4. Si continúa, instalar la última actualización publicada por el proveedor. 5. Revisar el Visor de eventos y adjuntar el error al escalar el caso.'),
    (11, 'PUB', 'tec.aplicaciones', 'Error de conexión a la base de datos al abrir el sistema contable',
     'El sistema contable no logra conectarse a su base de datos.',
     'Mensaje «No se pudo establecer conexión con el servidor» o «tiempo de espera agotado» al iniciar sesión. Afecta a uno o varios usuarios.',
     'El equipo no alcanza el servidor (red o VPN), el servicio de base de datos está detenido o el origen de datos ODBC quedó mal configurado.',
     '1. Verificar que el equipo esté en la red interna o con la VPN activa. 2. Hacer ping al servidor del sistema contable. 3. Revisar el origen de datos ODBC y probar la conexión. 4. Si falla para todos los usuarios, pedir a aplicaciones que revise el servicio de base de datos. 5. No reinstalar el cliente hasta descartar los pasos anteriores.'),
    (12, 'PUB', 'tec.aplicaciones', 'La aplicación web institucional muestra una página en blanco o error 500',
     'Una aplicación web interna no carga o muestra un error del servidor.',
     'Página en blanco después de iniciar sesión, error 500 o un formulario que no guarda los cambios.',
     'Caché o cookies del navegador dañadas, extensión que bloquea scripts o sesión vencida.',
     '1. Abrir la aplicación en una ventana privada. 2. Borrar la caché y las cookies del sitio. 3. Deshabilitar temporalmente las extensiones del navegador. 4. Probar con otro navegador compatible. 5. Si falla para varios usuarios, reportar a aplicaciones con la hora exacta y una captura del error.'),
    (13, 'DRAFT', 'tec.aplicaciones', 'La aplicación Java no abre por versión de Java incompatible',
     'Una aplicación que depende de Java no inicia tras una actualización.',
     'Mensaje «Unsupported major.minor version» o la ventana se cierra de inmediato.',
     'La versión de Java instalada es distinta de la que requiere la aplicación.',
     '1. Identificar la versión requerida en el manual de la aplicación. 2. Instalar esa versión desde el repositorio de soporte. 3. Ajustar la variable JAVA_HOME. Pendiente de validar con el proveedor antes de publicar.'),
    -- Software
    (14, 'PUB', 'tec.software', 'Microsoft Office solicita activación o aparece sin licencia',
     'Las aplicaciones de Office muestran un aviso de producto sin licencia y limitan la edición.',
     'Banda roja «Producto sin licencia» o «La mayoría de las características están deshabilitadas».',
     'La sesión de la cuenta institucional venció o la licencia no está asignada al usuario.',
     '1. En Archivo, Cuenta, cerrar la sesión e iniciar de nuevo con la cuenta institucional. 2. Verificar la fecha y la hora del equipo. 3. Confirmar con el administrador que el usuario tenga la licencia asignada. 4. Reiniciar Office. 5. Si continúa, ejecutar la reparación rápida desde Aplicaciones y características.'),
    (15, 'PUB', 'tec.software', 'Windows Update falla con el error 0x80070002',
     'El equipo no logra instalar las actualizaciones de Windows.',
     'La actualización se descarga, falla al instalar y reporta el error 0x80070002 de forma repetida.',
     'Archivos de la carpeta de distribución de actualizaciones dañados o incompletos.',
     '1. Ejecutar el solucionador de problemas de Windows Update. 2. Detener los servicios wuauserv y bits. 3. Renombrar la carpeta C:\Windows\SoftwareDistribution. 4. Iniciar de nuevo los servicios y buscar actualizaciones. 5. Si persiste, ejecutar sfc /scannow y DISM /Online /Cleanup-Image /RestoreHealth.'),
    (16, 'PUB', 'tec.software', 'El equipo tarda demasiado en iniciar sesión',
     'El inicio de sesión de Windows demora varios minutos.',
     'Pantalla «Bienvenido» prolongada, escritorio que tarda en mostrar los iconos y disco al 100 % durante el arranque.',
     'Demasiados programas configurados al inicio, perfil de usuario muy pesado o disco con poco espacio libre.',
     '1. Deshabilitar los programas de inicio que no sean necesarios desde el Administrador de tareas. 2. Liberar espacio hasta dejar al menos un 15 % del disco libre. 3. Mover los archivos grandes del escritorio a la carpeta de red. 4. Instalar las actualizaciones pendientes y reiniciar. 5. Si el disco es mecánico y el problema continúa, solicitar a hardware la evaluación de cambio a disco sólido.'),
    (17, 'PUB', 'tec.software', 'Outlook no abre o se queda en «Cargando perfil»',
     'Outlook no termina de iniciar.',
     'La ventana se queda en «Cargando perfil» o «Procesando», o se cierra sin mostrar la bandeja de entrada.',
     'Complemento defectuoso, perfil de correo dañado o archivo de datos con errores.',
     '1. Iniciar Outlook en modo seguro con outlook.exe /safe. 2. Si abre, deshabilitar los complementos y activarlos uno por uno. 3. Crear un perfil de correo nuevo desde el Panel de control. 4. Reparar el archivo de datos con la herramienta scanpst. 5. Como último recurso, ejecutar la reparación en línea de Office.'),
    (18, 'PUB', 'tec.software', 'El antivirus corporativo aparece desactualizado o deshabilitado',
     'El agente antivirus no se actualiza o figura sin protección.',
     'Icono del antivirus con advertencia, firmas con varios días de antigüedad o protección en tiempo real apagada.',
     'El agente perdió comunicación con la consola central o su servicio está detenido.',
     '1. Verificar que el equipo tenga conexión a la red interna. 2. Reiniciar el servicio del agente antivirus. 3. Forzar la actualización de firmas desde el propio agente. 4. Si no se comunica con la consola, reinstalar el agente con el instalador de soporte. 5. Ejecutar un análisis completo al terminar.'),
    -- Hardware
    (19, 'PUB', 'tec.hardware', 'La impresora no imprime: trabajos detenidos en la cola',
     'Los documentos enviados a la impresora no salen.',
     'Los trabajos quedan en la cola en estado «Imprimiendo» o «Error» y no se pueden cancelar.',
     'El servicio de cola de impresión está bloqueado por un trabajo dañado o la impresora quedó fuera de línea.',
     '1. Verificar que la impresora esté encendida, con papel y sin atascos. 2. Detener el servicio Cola de impresión. 3. Borrar los archivos de C:\Windows\System32\spool\PRINTERS. 4. Iniciar de nuevo el servicio. 5. Enviar una página de prueba y, si falla, reinstalar el controlador.'),
    (20, 'PUB', 'tec.hardware', 'El monitor no muestra imagen al encender el equipo',
     'El equipo enciende pero la pantalla permanece en negro.',
     'El monitor muestra «Sin señal» o queda en reposo. El equipo emite los sonidos normales de arranque.',
     'Cable de video suelto o dañado, entrada equivocada seleccionada en el monitor o salida de video incorrecta.',
     '1. Revisar que el cable de video esté firme en ambos extremos. 2. Seleccionar en el monitor la entrada correcta (HDMI, DisplayPort o VGA). 3. Probar con otro cable. 4. Conectar el monitor a otro equipo para descartar daño. 5. Si el monitor funciona en otro equipo, escalar a hardware para revisar la tarjeta de video.'),
    (21, 'PUB', 'tec.hardware', 'El equipo de escritorio no enciende',
     'El computador no da ninguna señal al presionar el botón de encendido.',
     'Sin luces, sin ventiladores y sin sonido. En algunos casos enciende un segundo y se apaga.',
     'Cable de poder o multitoma sin energía, fuente de poder dañada o descarga estática acumulada.',
     '1. Probar la toma eléctrica con otro dispositivo. 2. Cambiar el cable de poder y conectar directo a la pared, sin multitoma. 3. Desconectar el cable, mantener presionado el botón de encendido 20 segundos y volver a conectar. 4. Revisar el interruptor trasero de la fuente. 5. Si no responde, entregar el equipo a hardware para probar la fuente de poder.'),
    (22, 'PUB', 'tec.hardware', 'El teclado o el mouse USB no responden',
     'Un periférico USB deja de funcionar.',
     'El teclado no escribe o el puntero no se mueve. En los inalámbricos la luz del receptor no enciende.',
     'Puerto USB sin energía, baterías agotadas, receptor mal vinculado o controlador en estado de error.',
     '1. Conectar el dispositivo en otro puerto USB, de preferencia trasero. 2. Cambiar las baterías si es inalámbrico. 3. Volver a vincular el receptor con el botón de conexión. 4. Reiniciar el equipo. 5. Probar el periférico en otro computador y reemplazarlo si tampoco funciona.'),
    (23, 'PUB', 'tec.hardware', 'El portátil no carga la batería',
     'El portátil no carga aunque esté conectado al cargador.',
     'Mensaje «Conectado, no se está cargando» o el porcentaje de batería no aumenta. Se apaga al desconectarlo.',
     'Cargador o cable dañado, conector sucio o batería que agotó su vida útil.',
     '1. Probar con otro cargador original del mismo modelo. 2. Revisar que el conector no tenga suciedad ni holgura. 3. Apagar el equipo, desconectar el cargador y mantener presionado el botón de encendido 30 segundos. 4. Generar el informe con powercfg /batteryreport para ver la capacidad real. 5. Si la capacidad es inferior al 40 % de la original, solicitar el cambio de batería.'),
    -- Accesos
    (24, 'PUB', 'tec.software', 'Cuenta de dominio bloqueada por intentos fallidos',
     'El usuario no puede iniciar sesión porque su cuenta quedó bloqueada.',
     'Mensaje «La cuenta a la que se hace referencia está bloqueada» en el equipo o en el portal.',
     'Se superó el número de intentos permitidos, con frecuencia por una contraseña antigua guardada en el celular o en una unidad de red.',
     '1. Confirmar la identidad del usuario. 2. Desbloquear la cuenta desde la consola de administración de usuarios. 3. Pedir al usuario que actualice la contraseña guardada en el celular, el correo y las unidades de red. 4. Si se vuelve a bloquear, identificar en el registro del controlador de dominio el equipo que origina los intentos.'),
    (25, 'PUB', 'tec.software', 'Contraseña vencida: no permite iniciar sesión',
     'La contraseña del usuario venció y el sistema impide el ingreso.',
     'Mensaje «Su contraseña ha expirado y debe cambiarse» o rechazo de la clave habitual.',
     'La política institucional exige cambiar la contraseña de forma periódica y el plazo se cumplió.',
     '1. En un equipo de la red interna presionar Ctrl+Alt+Supr y elegir Cambiar una contraseña. 2. Definir una clave que cumpla la política: longitud mínima, mayúscula y carácter especial. 3. Fuera de la oficina, conectar primero la VPN o usar el portal de autoservicio. 4. Si no es posible, el administrador restablece la clave y el usuario la cambia en el siguiente ingreso.'),
    (26, 'PUB', 'tec.software', 'Sin permisos para acceder a una carpeta o aplicación',
     'El usuario necesita entrar a un recurso y el sistema se lo niega.',
     'Mensaje «Acceso denegado» o «No tiene permisos para acceder a esta carpeta».',
     'El usuario no pertenece al grupo de seguridad que otorga el acceso, o cambió de área y conserva los permisos anteriores.',
     '1. Identificar el recurso exacto y el tipo de acceso requerido: lectura o escritura. 2. Solicitar la aprobación del responsable del recurso. 3. Agregar al usuario al grupo de seguridad correspondiente. 4. Pedir al usuario que cierre e inicie sesión para aplicar el cambio. 5. Registrar la aprobación en el incidente.'),
    (27, 'DRAFT', 'tec.software', 'No llega el código de verificación en dos pasos',
     'El usuario no recibe el código para completar el inicio de sesión.',
     'La pantalla solicita el código y el mensaje nunca llega al celular.',
     'Número de celular desactualizado o aplicación autenticadora sin sincronizar.',
     '1. Confirmar el número registrado. 2. Sincronizar la hora de la aplicación autenticadora. Borrador: falta definir con seguridad el procedimiento de restablecimiento del segundo factor.'),
    -- Servicios informáticos
    (28, 'PUB', 'tec.aplicaciones', 'El correo no sincroniza en Outlook o en el celular',
     'El buzón deja de recibir o enviar mensajes en un dispositivo.',
     'Outlook muestra «Desconectado» o «Intentando conectar». En el celular aparece un error de sincronización. El correo web sí funciona.',
     'Contraseña cambiada sin actualizar en el dispositivo, modo sin conexión activado o perfil de correo dañado.',
     '1. Comprobar que el correo web funcione para descartar una falla del servicio. 2. En Outlook, desactivar «Trabajar sin conexión». 3. Actualizar la contraseña guardada en el dispositivo. 4. En el celular, eliminar la cuenta y agregarla de nuevo. 5. Si solo falla Outlook, crear un perfil de correo nuevo.'),
    (29, 'PUB', 'tec.aplicaciones', 'Buzón de correo lleno: no se pueden enviar ni recibir mensajes',
     'El buzón alcanzó su cuota de almacenamiento.',
     'Mensaje «Su buzón está lleno» y rebotes a los remitentes con el aviso de cuota excedida.',
     'Acumulación de adjuntos grandes y elementos eliminados que no se han vaciado.',
     '1. Vaciar la carpeta Elementos eliminados y el correo no deseado. 2. Ordenar el buzón por tamaño y borrar o guardar los adjuntos más pesados. 3. Activar el archivo en línea si el plan lo incluye. 4. Mover los correos antiguos al archivo. 5. Si el cargo lo justifica, solicitar la ampliación de cuota con aprobación del jefe inmediato.'),
    (30, 'PUB', 'tec.aplicaciones', 'Restaurar un archivo eliminado desde la copia de seguridad',
     'El usuario borró o sobrescribió un archivo de una carpeta de red y necesita recuperarlo.',
     'El archivo ya no aparece en la carpeta compartida o contiene una versión equivocada.',
     'Eliminación o modificación accidental. Las carpetas de red tienen versiones anteriores y copia de seguridad diaria.',
     '1. En la carpeta de red, abrir Propiedades y la pestaña Versiones anteriores. 2. Elegir la versión de la fecha requerida y copiarla a otra ubicación. 3. Si no hay versiones, pedir la restauración indicando la ruta exacta, el nombre del archivo y la fecha aproximada. 4. La restauración desde la copia diaria cubre los últimos 30 días.'),
    -- Procedimiento retirado: verificado pero inactivo, no debe alimentar al asistente.
    (31, 'VER', 'tec.redes', 'Conexión a la VPN con el cliente PPTP (procedimiento retirado)',
     'Instrucciones del cliente VPN anterior, reemplazado por el cliente actual.',
     'El usuario busca el acceso directo de la conexión PPTP que ya no existe.',
     'El protocolo PPTP fue retirado por razones de seguridad.',
     'Procedimiento retirado: no crear conexiones PPTP. Usar el cliente VPN vigente descrito en el artículo de la VPN con error 809.')
) AS a(ord, state, author, title, problem, symptoms, cause, solution);

-- -------------------------------------------------------------------------------------
-- 3. Incidentes: 180, creados entre el 2026-05-25 y el 2026-11-02, en días hábiles y horario de oficina.
--    - 24 corresponden a una caída de conectividad en la semana del 2026-09-14 (pico).
--    - El resto crece de forma gradual: las semanas recientes tienen más incidentes.
--    - Tiempo de resolución según categoría y prioridad (horas transcurridas).
--    - Estado derivado de la línea de tiempo: creación ≤ asignación ≤ inicio ≤ resolución ≤ cierre.
-- -------------------------------------------------------------------------------------
CREATE TEMP TABLE demo_template ON COMMIT DROP AS
SELECT * FROM (VALUES
    ('Conectividad', 0, 'Sin acceso a internet en el equipo', 'El equipo no abre ninguna página web y el icono de red muestra una advertencia.', 'Se renovó la dirección IP con ipconfig /release y /renew; el equipo recuperó la navegación.'),
    ('Conectividad', 1, 'El Wi-Fi conecta pero no navega', 'El portátil se conecta al Wi-Fi corporativo pero indica que no hay internet.', 'Se eliminó el perfil inalámbrico y se conectó de nuevo con el usuario de dominio.'),
    ('Conectividad', 2, 'La VPN no conecta desde casa', 'Al intentar conectar la VPN aparece el error 809 y no es posible entrar a los sistemas internos.', 'Se actualizó el cliente VPN y se verificó la pertenencia al grupo de acceso remoto.'),
    ('Conectividad', 3, 'Internet muy lento e intermitente', 'La conexión por cable se cae varias veces al día y las videollamadas se cortan.', 'Se reemplazó el cable de red y se cambió el equipo a otro punto con enlace estable.'),
    ('Conectividad', 4, 'No abren las páginas por nombre', 'El navegador muestra un error de DNS aunque el equipo aparece conectado a la red.', 'Se limpió la caché DNS y se corrigieron los servidores DNS del adaptador.'),
    ('Conectividad', 5, 'No puedo entrar a las carpetas compartidas', 'Las unidades de red aparecen con una X roja y no abren los archivos del área.', 'Se reasignó la unidad de red y se actualizaron las credenciales guardadas.'),
    ('Software', 0, 'Office pide activar el producto', 'Word y Excel muestran una banda roja de producto sin licencia y no dejan editar.', 'Se cerró la sesión de Office y se inició de nuevo con la cuenta institucional; licencia activa.'),
    ('Software', 1, 'Windows no instala las actualizaciones', 'Las actualizaciones fallan con el error 0x80070002 cada vez que se reinicia el equipo.', 'Se restableció la carpeta SoftwareDistribution y las actualizaciones se instalaron.'),
    ('Software', 2, 'El equipo demora mucho en iniciar sesión', 'Después de escribir la contraseña el escritorio tarda más de cinco minutos en aparecer.', 'Se deshabilitaron programas de inicio y se liberó espacio en disco.'),
    ('Software', 3, 'Outlook no abre', 'Outlook se queda en la pantalla de carga del perfil y no muestra la bandeja de entrada.', 'Se inició en modo seguro y se deshabilitó el complemento que causaba el bloqueo.'),
    ('Software', 4, 'El antivirus aparece desactualizado', 'El icono del antivirus muestra una advertencia de firmas antiguas.', 'Se reinició el servicio del agente y se forzó la actualización de firmas.'),
    ('Software', 5, 'Solicitud de instalación de un programa', 'Se requiere instalar el lector de PDF autorizado para firmar documentos.', 'Se instaló la aplicación desde el repositorio de software autorizado.'),
    ('Hardware', 0, 'La impresora no imprime', 'Los documentos quedan detenidos en la cola de impresión y no salen.', 'Se limpió la cola de impresión y se reinició el servicio; página de prueba correcta.'),
    ('Hardware', 1, 'El monitor no muestra imagen', 'El equipo enciende pero la pantalla dice sin señal.', 'Se ajustó el cable de video y se seleccionó la entrada correcta del monitor.'),
    ('Hardware', 2, 'El computador no enciende', 'Al presionar el botón de encendido no prende ninguna luz ni suena el ventilador.', 'Se reemplazó la fuente de poder; el equipo enciende con normalidad.'),
    ('Hardware', 3, 'El teclado no responde', 'El teclado inalámbrico dejó de escribir desde esta mañana.', 'Se cambiaron las baterías y se vinculó de nuevo el receptor USB.'),
    ('Hardware', 4, 'El portátil no carga', 'El portátil indica conectado pero no cargando y se apaga al desconectarlo.', 'Se reemplazó el cargador por uno original del mismo modelo.'),
    ('Hardware', 5, 'El mouse se mueve de forma errática', 'El puntero salta por la pantalla y es difícil seleccionar.', 'Se limpió el sensor y se reemplazó el periférico por desgaste.'),
    ('Aplicaciones', 0, 'La aplicación de nómina no abre', 'Al abrir la aplicación de nómina aparece el error 0xc000007b y se cierra.', 'Se reinstalaron los paquetes de Visual C++ x86 y x64; la aplicación inicia.'),
    ('Aplicaciones', 1, 'Falta un archivo DLL al abrir el programa', 'El programa de diseño indica que falta el archivo MSVCP140.dll.', 'Se instaló Microsoft Visual C++ Redistributable 2015-2022.'),
    ('Aplicaciones', 2, 'El sistema contable no conecta', 'El sistema contable muestra que no pudo establecer conexión con el servidor.', 'Se corrigió el origen de datos ODBC y se validó la conexión con el servidor.'),
    ('Aplicaciones', 3, 'La intranet muestra una página en blanco', 'Después de iniciar sesión en la intranet la página queda en blanco.', 'Se borraron la caché y las cookies del sitio; la aplicación carga.'),
    ('Aplicaciones', 4, 'La aplicación se cierra sola', 'El sistema de inventario se cierra a los pocos segundos de abrirlo.', 'Se eliminó la caché local de la aplicación y se instaló la última actualización.'),
    ('Aplicaciones', 5, 'Error 500 al guardar un formulario', 'Al guardar una solicitud en la aplicación web aparece un error 500.', 'Se identificó una sesión vencida; tras iniciar sesión de nuevo el formulario guarda.'),
    ('Accesos', 0, 'Cuenta bloqueada', 'El sistema indica que la cuenta está bloqueada y no permite iniciar sesión.', 'Se desbloqueó la cuenta y se actualizó la contraseña guardada en el celular.'),
    ('Accesos', 1, 'La contraseña venció', 'El equipo indica que la contraseña expiró y no deja entrar.', 'Se restableció la contraseña y el usuario definió una nueva en su primer ingreso.'),
    ('Accesos', 2, 'Sin permisos para una carpeta', 'Al abrir la carpeta del proyecto aparece acceso denegado.', 'Con aprobación del responsable se agregó al usuario al grupo de la carpeta.'),
    ('Accesos', 3, 'Acceso a una aplicación para un usuario nuevo', 'Se requiere acceso al sistema de compras para un funcionario que ingresó al área.', 'Se creó el perfil con el rol aprobado por el jefe inmediato.'),
    ('Accesos', 4, 'Olvidé mi contraseña', 'No recuerdo la contraseña del portal institucional.', 'Se restableció la contraseña y se exigió su cambio en el siguiente ingreso.'),
    ('Accesos', 5, 'Retirar accesos de un funcionario', 'Un funcionario cambió de área y conserva permisos que ya no corresponden.', 'Se retiraron los grupos del área anterior y se asignaron los del área nueva.'),
    ('Servicios informáticos', 0, 'El correo no sincroniza', 'Outlook aparece desconectado y no llegan correos nuevos; el correo web sí funciona.', 'Se actualizó la contraseña guardada y se desactivó el modo sin conexión.'),
    ('Servicios informáticos', 1, 'El buzón está lleno', 'Los remitentes reciben un rebote indicando que el buzón superó la cuota.', 'Se vació la carpeta de eliminados y se activó el archivo en línea.'),
    ('Servicios informáticos', 2, 'Recuperar un archivo borrado', 'Se eliminó por error un archivo de la carpeta compartida del área.', 'Se restauró la versión anterior desde las versiones de la carpeta de red.'),
    ('Servicios informáticos', 3, 'El correo no sincroniza en el celular', 'El celular muestra un error de sincronización de la cuenta institucional.', 'Se eliminó la cuenta del celular y se configuró de nuevo.'),
    ('Servicios informáticos', 4, 'Ampliar el espacio de la carpeta del área', 'La carpeta compartida del área no admite más archivos.', 'Se depuraron archivos duplicados y se amplió la cuota aprobada.'),
    ('Servicios informáticos', 5, 'No puedo enviar correos con adjuntos', 'Los correos con adjuntos grandes son rechazados.', 'Se indicó el límite de tamaño y se compartió el archivo mediante un enlace.'),
    ('Otros', 0, 'El televisor de la sala de juntas no proyecta', 'El televisor de la sala no reconoce el portátil para proyectar.', 'Se reemplazó el cable HDMI de la sala y se dejó un adaptador disponible.'),
    ('Otros', 1, 'Solicitud de préstamo de un videoproyector', 'Se requiere un videoproyector para una capacitación del área.', 'Se entregó el equipo en préstamo con acta firmada.'),
    ('Otros', 2, 'El teléfono IP no da tono', 'El teléfono del puesto no da tono ni muestra la extensión.', 'Se reinició el teléfono y se reconectó al puerto con alimentación.'),
    ('Otros', 3, 'El lector de huella no registra', 'El lector de huella de la entrada no reconoce a varios funcionarios.', 'Se limpió el sensor y se volvió a registrar la huella de los usuarios afectados.'),
    ('Otros', 4, 'Asesoría para una videoconferencia', 'Se necesita apoyo para conectar la cámara y el micrófono de la sala.', 'Se configuraron los dispositivos de la sala y se hizo una llamada de prueba.'),
    ('Otros', 5, 'La cámara de seguridad no graba', 'La cámara del pasillo aparece sin imagen en el monitor de vigilancia.', 'Se reemplazó la fuente de alimentación de la cámara.')
) AS t(category, idx, title, description, resolution);

CREATE TEMP TABLE demo_plan ON COMMIT DROP AS
WITH raw AS (
    SELECT n, n <= 24 AS spike,
           pg_temp.demo_rnd(n, 'cat', 100) AS rc,
           pg_temp.demo_rnd(n, 'pri', 100) AS rp,
           date '2026-05-04' + floor(182 * sqrt(pg_temp.demo_rnd(n, 'day', 10000) / 10000.0))::int AS base_day
    FROM generate_series(1, 180) AS n
), classified AS (
    SELECT n, spike,
           CASE WHEN spike OR rc < 22 THEN 'Conectividad' WHEN rc < 42 THEN 'Software' WHEN rc < 60 THEN 'Hardware'
                WHEN rc < 76 THEN 'Aplicaciones' WHEN rc < 90 THEN 'Accesos' WHEN rc < 97 THEN 'Servicios informáticos'
                ELSE 'Otros' END AS category,
           CASE WHEN spike THEN (CASE WHEN rp < 35 THEN 'CRITICA' WHEN rp < 85 THEN 'ALTA' ELSE 'MEDIA' END)
                WHEN rp < 30 THEN 'BAJA' WHEN rp < 70 THEN 'MEDIA' WHEN rp < 92 THEN 'ALTA' ELSE 'CRITICA' END AS priority,
           CASE WHEN spike
                THEN timestamp '2026-09-14 07:30:00'
                     + make_interval(days => pg_temp.demo_rnd(n, 'sday', 5), mins => pg_temp.demo_rnd(n, 'smin', 600))
                -- Los fines de semana se trasladan al lunes; horario de 07:00 a 18:00.
                ELSE (base_day + CASE extract(isodow FROM base_day)::int WHEN 6 THEN 2 WHEN 7 THEN 1 ELSE 0 END)::timestamp
                     + make_interval(mins => 420 + pg_temp.demo_rnd(n, 'min', 660))
           END AS created_at
    FROM raw
), timed AS (
    SELECT c.*,
           CASE c.priority WHEN 'CRITICA' THEN 5 + pg_temp.demo_rnd(n, 'asg', 40)
                           WHEN 'ALTA' THEN 15 + pg_temp.demo_rnd(n, 'asg', 120)
                           WHEN 'MEDIA' THEN 30 + pg_temp.demo_rnd(n, 'asg', 300)
                           ELSE 60 + pg_temp.demo_rnd(n, 'asg', 600) END AS assign_min,
           10 + pg_temp.demo_rnd(n, 'sta', 240) AS start_min,
           -- Horas base por categoría × factor de prioridad × variación entre 0,5 y 1,5.
           greatest(15, round(60
               * CASE c.category WHEN 'Hardware' THEN 30 WHEN 'Aplicaciones' THEN 14 WHEN 'Otros' THEN 12 WHEN 'Software' THEN 10
                                 WHEN 'Servicios informáticos' THEN 8 WHEN 'Conectividad' THEN 6 ELSE 3 END
               * CASE c.priority WHEN 'CRITICA' THEN 0.4 WHEN 'ALTA' THEN 0.7 WHEN 'MEDIA' THEN 1.0 ELSE 1.6 END
               * (0.5 + pg_temp.demo_rnd(n, 'res', 1001) / 1000.0)))::int AS resolve_min,
           30 + pg_temp.demo_rnd(n, 'clo', 4320) AS close_min,
           -- Hasta dónde llegaría el incidente: algunos se quedan sin asignar, en curso o sin confirmar el cierre.
           CASE WHEN pg_temp.demo_rnd(n, 'dep', 100) < 4 THEN 0 WHEN pg_temp.demo_rnd(n, 'dep', 100) < 7 THEN 1
                WHEN pg_temp.demo_rnd(n, 'dep', 100) < 11 THEN 2 WHEN pg_temp.demo_rnd(n, 'dep', 100) < 22 THEN 3
                ELSE 4 END AS intended,
           (ARRAY['laura.gomez', 'carlos.ruiz', 'maria.perez', 'andres.castro', 'diana.mora', 'felipe.rojas',
                  'sofia.vargas', 'jorge.nino'])[1 + pg_temp.demo_rnd(n, 'app', 8)] AS applicant,
           CASE WHEN pg_temp.demo_rnd(n, 'alt', 100) < 15
                THEN (ARRAY['tec.redes', 'tec.hardware', 'tec.software', 'tec.aplicaciones'])[1 + pg_temp.demo_rnd(n, 'tec', 4)]
                ELSE CASE c.category WHEN 'Conectividad' THEN 'tec.redes' WHEN 'Hardware' THEN 'tec.hardware'
                                     WHEN 'Software' THEN 'tec.software' WHEN 'Accesos' THEN 'tec.software'
                                     ELSE 'tec.aplicaciones' END
           END AS technician,
           CASE WHEN c.spike THEN (ARRAY['Sede Centro, piso 1', 'Sede Centro, piso 4'])[1 + pg_temp.demo_rnd(n, 'loc', 2)]
                ELSE (ARRAY['Sede Norte, piso 2', 'Sede Norte, piso 3', 'Sede Centro, piso 1', 'Sede Centro, piso 4',
                            'Bodega principal', 'Sede Sur, recepción'])[1 + pg_temp.demo_rnd(n, 'loc', 6)] END AS location,
           -- En la caída de conectividad casi todos reportan falta de internet.
           CASE WHEN c.spike THEN (ARRAY[0, 0, 0, 1, 3, 4])[1 + pg_temp.demo_rnd(n, 'tpl', 6)]
                ELSE pg_temp.demo_rnd(n, 'tpl', 6) END AS tpl
    FROM classified c
), lined AS (
    SELECT t.*,
           t.created_at + make_interval(mins => t.assign_min) AS t_assigned,
           t.created_at + make_interval(mins => t.assign_min + t.start_min / 2) AS t_reassigned,
           t.created_at + make_interval(mins => t.assign_min + t.start_min) AS t_started,
           t.created_at + make_interval(mins => t.assign_min + t.start_min + t.resolve_min) AS t_resolved,
           t.created_at + make_interval(mins => t.assign_min + t.start_min + t.resolve_min + t.close_min) AS t_closed
    FROM timed t
), staged AS (
    SELECT l.*,
           -- El estado es el más avanzado que se alcanzó antes de la fecha de corte.
           least(l.intended,
                 (l.t_assigned <= timestamp '2026-11-09 08:00:00')::int + (l.t_started <= timestamp '2026-11-09 08:00:00')::int
                 + (l.t_resolved <= timestamp '2026-11-09 08:00:00')::int + (l.t_closed <= timestamp '2026-11-09 08:00:00')::int) AS depth
    FROM lined l
)
SELECT s.*,
       (SELECT coalesce(max(id), 0) FROM public.tickets) + row_number() OVER (ORDER BY s.created_at, s.n) AS ticket_id,
       (ARRAY['OPEN', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'])[s.depth + 1] AS status,
       -- Reasignación (8 %): el incidente se asignó primero a otro técnico y se corrigió antes de iniciar la atención.
       (s.depth >= 1 AND pg_temp.demo_rnd(s.n, 'rea', 100) < 8 AND s.t_reassigned <= timestamp '2026-11-09 08:00:00'
            AND (s.depth >= 2 OR pg_temp.demo_rnd(s.n, 'rea2', 2) = 0)) AS reassigned,
       CASE s.technician WHEN 'tec.redes' THEN 'tec.hardware' WHEN 'tec.hardware' THEN 'tec.software'
                         WHEN 'tec.software' THEN 'tec.aplicaciones' ELSE 'tec.redes' END AS first_technician
FROM staged s;

INSERT INTO public.tickets (id, title, description, category_id, priority, status, applicant_username, technician_username,
        created_at, updated_at, resolved_at, closed_at, resolution_note)
SELECT p.ticket_id, t.title, t.description || ' Ubicación: ' || p.location || '.', c.id, p.priority, p.status, p.applicant,
       CASE WHEN p.depth >= 1 THEN p.technician END,
       p.created_at,
       CASE p.depth WHEN 0 THEN p.created_at WHEN 1 THEN (CASE WHEN p.reassigned THEN p.t_reassigned ELSE p.t_assigned END)
                    WHEN 2 THEN p.t_started WHEN 3 THEN p.t_resolved ELSE p.t_closed END,
       CASE WHEN p.depth >= 3 THEN p.t_resolved END,
       CASE WHEN p.depth >= 4 THEN p.t_closed END,
       CASE WHEN p.depth >= 3 THEN t.resolution END
FROM demo_plan p
JOIN public.categories c ON c.name = p.category
JOIN demo_template t ON t.category = p.category AND t.idx = p.tpl;

SELECT setval(pg_get_serial_sequence('public.tickets', 'id'), (SELECT max(id) FROM public.tickets));

-- Historial de estados: un evento por transición, con su actor y su instante.
INSERT INTO public.ticket_status_history (ticket_id, previous_status, new_status, changed_by_username, action_type, changed_at)
SELECT ticket_id, NULL, 'OPEN', applicant, 'CREATION', created_at FROM demo_plan
UNION ALL
SELECT ticket_id, 'OPEN', 'ASSIGNED', 'admin.demo', 'ASSIGNMENT', t_assigned FROM demo_plan WHERE depth >= 1
UNION ALL
SELECT ticket_id, 'ASSIGNED', 'ASSIGNED', 'admin.demo', 'ASSIGNMENT', t_reassigned FROM demo_plan WHERE reassigned
UNION ALL
SELECT ticket_id, 'ASSIGNED', 'IN_PROGRESS', technician, 'STATUS_TRANSITION', t_started FROM demo_plan WHERE depth >= 2
UNION ALL
SELECT ticket_id, 'IN_PROGRESS', 'RESOLVED', technician, 'STATUS_TRANSITION', t_resolved FROM demo_plan WHERE depth >= 3
UNION ALL
SELECT ticket_id, 'RESOLVED', 'CLOSED', applicant, 'CLOSURE', t_closed FROM demo_plan WHERE depth >= 4
ORDER BY 6, 1;

-- Auditoría de asignaciones: la inicial y, cuando aplica, la reasignación con su justificación.
INSERT INTO public.ticket_assignment_logs (ticket_id, previous_technician_username, new_technician_username,
        assigned_by_username, assignment_type, justification, assigned_at)
SELECT ticket_id, NULL, CASE WHEN reassigned THEN first_technician ELSE technician END, 'admin.demo', 'INITIAL', NULL, t_assigned
FROM demo_plan WHERE depth >= 1
UNION ALL
SELECT ticket_id, first_technician, technician, 'admin.demo', 'REASSIGNMENT',
       'El incidente corresponde a la especialidad de otro técnico', t_reassigned
FROM demo_plan WHERE reassigned
ORDER BY 7, 1;

-- Comentarios: uno público al iniciar la atención en parte de los incidentes y algunas notas internas.
INSERT INTO public.ticket_comments (ticket_id, author_username, content, visibility, created_at)
SELECT ticket_id, technician, 'Se inicia la revisión del caso. Si se requiere acceso al equipo se contactará al usuario.',
       'PUBLIC', t_started + interval '2 minutes'
FROM demo_plan WHERE depth >= 2 AND n % 3 = 0
UNION ALL
SELECT ticket_id, technician, 'Nota interna: se revisó el historial del equipo antes de intervenir.',
       'INTERNAL', t_started + interval '5 minutes'
FROM demo_plan WHERE depth >= 2 AND n % 8 = 0
ORDER BY 5, 1;

UPDATE public.technician_profiles tp
SET last_assigned_at = a.last_assigned
FROM (SELECT new_technician_username, max(assigned_at) AS last_assigned
      FROM public.ticket_assignment_logs GROUP BY new_technician_username) a
WHERE a.new_technician_username = tp.username;

-- -------------------------------------------------------------------------------------
-- 4. Comprobación: mínimos del enunciado y coherencia de fechas. Si algo no se cumple, la
--    transacción se revierte y no queda ningún dato a medias.
-- -------------------------------------------------------------------------------------
DO $$
DECLARE incoherent integer;
BEGIN
    IF (SELECT count(*) FROM public.tickets t JOIN demo_plan p ON p.ticket_id = t.id) < 150 THEN
        RAISE EXCEPTION 'Se esperaban al menos 150 incidentes';
    END IF;
    IF (SELECT count(*) FROM public.knowledge_articles WHERE active AND verified) < 25 THEN
        RAISE EXCEPTION 'Se esperaban al menos 25 artículos publicados';
    END IF;
    IF (SELECT count(*) FROM public."user" WHERE email LIKE '%@demo.campusdesk.local') < 10 THEN
        RAISE EXCEPTION 'Se esperaban al menos 10 usuarios';
    END IF;
    IF (SELECT count(DISTINCT status) FROM public.tickets) < 4 OR (SELECT count(DISTINCT priority) FROM public.tickets) < 4
            OR (SELECT count(DISTINCT category_id) FROM public.tickets) < 5 THEN
        RAISE EXCEPTION 'Los incidentes deben cubrir al menos 4 estados, 4 prioridades y 5 categorías';
    END IF;
    SELECT count(*) INTO incoherent
    FROM public.tickets t JOIN demo_plan p ON p.ticket_id = t.id
    WHERE (t.status IN ('RESOLVED', 'CLOSED')) <> (t.resolved_at IS NOT NULL)
       OR (t.status = 'CLOSED') <> (t.closed_at IS NOT NULL)
       OR (t.status = 'OPEN') <> (t.technician_username IS NULL)
       OR t.resolved_at < t.created_at OR t.closed_at < t.resolved_at
       OR t.created_at > timestamp '2026-11-09 08:00:00' OR t.updated_at > timestamp '2026-11-09 08:00:00'
       OR extract(isodow FROM t.created_at) > 5;
    IF incoherent > 0 THEN
        RAISE EXCEPTION '% incidentes tienen fechas o estados incoherentes', incoherent;
    END IF;
    -- El último evento del historial de cada incidente coincide con su estado actual.
    SELECT count(*) INTO incoherent
    FROM public.tickets t JOIN demo_plan p ON p.ticket_id = t.id
    WHERE t.status <> (SELECT h.new_status FROM public.ticket_status_history h WHERE h.ticket_id = t.id
                       ORDER BY h.changed_at DESC, h.id DESC LIMIT 1);
    IF incoherent > 0 THEN
        RAISE EXCEPTION '% incidentes no coinciden con su historial de estados', incoherent;
    END IF;
END $$;

COMMIT;

-- Resumen de lo cargado.
SELECT (SELECT count(*) FROM public."user" WHERE email LIKE '%@demo.campusdesk.local') AS usuarios,
       (SELECT count(*) FROM public.categories) AS categorias,
       (SELECT count(*) FROM public.knowledge_articles) AS articulos,
       (SELECT count(*) FROM public.knowledge_articles WHERE active AND verified) AS articulos_publicados,
       (SELECT count(*) FROM public.tickets) AS incidentes,
       (SELECT count(*) FROM public.ticket_status_history) AS eventos_de_historial;
