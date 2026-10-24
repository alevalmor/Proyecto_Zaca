-- Roles de grupo y permisos. Correr como superusuario en LOS DOS servidores (los roles no se replican).
-- La base de datos hace cumplir los perfiles: aunque el programa fallara, nadie escribe lo que no le toca.
-- Nadie tiene DELETE sobre datos de operación: lo registrado se corrige o se cancela, no se borra.

CREATE ROLE rol_produccion NOLOGIN;  -- PES · planta Metapa
CREATE ROLE rol_empaque    NOLOGIN;  -- PEM · centro de empaque Tapachula
CREATE ROLE rol_aeropuerto NOLOGIN;  -- PEA · aeropuerto de Tapachula (embarques de Perú)
CREATE ROLE rol_dispersion NOLOGIN;  -- PED · encargada de dispersión aérea
CREATE ROLE rol_admin      NOLOGIN;  -- Programa 2

GRANT USAGE ON SCHEMA trampeo TO rol_produccion, rol_empaque, rol_aeropuerto, rol_dispersion, rol_admin;
GRANT SELECT ON ALL TABLES IN SCHEMA public, trampeo TO rol_produccion, rol_empaque, rol_aeropuerto, rol_dispersion, rol_admin;
REVOKE SELECT ON historial FROM rol_produccion, rol_empaque, rol_aeropuerto, rol_dispersion;  -- solo el administrador lo consulta
GRANT INSERT ON auditoria TO rol_produccion, rol_empaque, rol_aeropuerto, rol_dispersion, rol_admin;

-- Altas completas; en cambios, solo las columnas que el programa permite tocar (ni el código ni quién lo registró).
GRANT INSERT ON lote_produccion, control_calidad TO rol_produccion;
GRANT UPDATE (fecha_produccion, fecha_irradiacion, dosis_gy, cantidad_pupas, cepa, tinte, centro_empaque_id, observaciones)
    ON lote_produccion TO rol_produccion;
GRANT UPDATE (emergencia, vuelo, esterilidad, peso_mg, fecha, responsable_id, observaciones)
    ON control_calidad TO rol_produccion, rol_empaque;  -- responsable_id: quien repite la prueba la firma
GRANT INSERT ON envio, control_calidad TO rol_empaque;
GRANT UPDATE (lote_id, vehiculo_id, fecha_salida, cantidad, temperatura, recibido, cancelado) ON envio TO rol_empaque;
GRANT INSERT ON embarque_aereo TO rol_aeropuerto;
GRANT INSERT ON liberacion TO rol_dispersion;
GRANT INSERT, UPDATE, DELETE ON responsable, permiso_vista TO rol_admin;
GRANT pg_monitor TO rol_admin;  -- para ver el estado de la replicación en "Estado del sistema"

-- Usuarios de ejemplo (cámbiales la contraseña). Los demás se crean desde el Programa 2.
CREATE ROLE usr_admin LOGIN CREATEROLE PASSWORD 'contrasena' CONNECTION LIMIT 5 IN ROLE rol_admin;
-- El administrador puede dar estos roles, pero no hereda sus permisos: no registra lotes, envíos ni dispersiones.
GRANT rol_produccion, rol_empaque, rol_aeropuerto, rol_dispersion TO usr_admin WITH ADMIN TRUE, INHERIT FALSE, SET FALSE;
CREATE ROLE usr_planta     LOGIN PASSWORD 'contrasena' CONNECTION LIMIT 30 IN ROLE rol_produccion;
CREATE ROLE usr_empaque    LOGIN PASSWORD 'contrasena' CONNECTION LIMIT 30 IN ROLE rol_empaque;
CREATE ROLE usr_aeropuerto LOGIN PASSWORD 'contrasena' CONNECTION LIMIT 30 IN ROLE rol_aeropuerto;
CREATE ROLE usr_dispersion LOGIN PASSWORD 'contrasena' CONNECTION LIMIT 30 IN ROLE rol_dispersion;
