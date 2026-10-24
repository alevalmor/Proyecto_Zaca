-- Trae trampeo.revision de la base de trampeo con postgres_fdw. Correr como superusuario en cada sede:
--   psql -d moscamed -v host=192.168.30.10 -v clave=lector -f fdw.sql

CREATE EXTENSION postgres_fdw;
CREATE SERVER srv_trampeo FOREIGN DATA WRAPPER postgres_fdw OPTIONS (host :'host', dbname 'trampeo');
-- Todos leen con el mismo usuario de solo lectura: la base de trampeo no conoce a la gente de las sedes.
CREATE USER MAPPING FOR PUBLIC SERVER srv_trampeo OPTIONS (user 'lector', password :'clave');
IMPORT FOREIGN SCHEMA public LIMIT TO (revision) FROM SERVER srv_trampeo INTO trampeo;
GRANT SELECT ON ALL TABLES IN SCHEMA trampeo TO rol_produccion, rol_empaque, rol_aeropuerto, rol_dispersion, rol_admin;
