-- Replicación master-master, parte 1: cada sede publica sus tablas. Correr como superusuario en cada sede
-- (con wal_level = logical en postgresql.conf):
--   planta:  psql -d moscamed -v pub=pub_planta  -v clave=... -f replicacion.sql
--   empaque: psql -d moscamed -v pub=pub_empaque -v clave=... -f replicacion.sql
-- Parte 2: suscripcion.sql. El trampeo no se publica: es una tabla foránea.

CREATE ROLE replicador LOGIN REPLICATION PASSWORD :'clave';
GRANT SELECT ON ALL TABLES IN SCHEMA public TO replicador;  -- para la copia inicial
CREATE PUBLICATION :pub FOR TABLES IN SCHEMA public;
