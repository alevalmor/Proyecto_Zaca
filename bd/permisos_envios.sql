-- Aplica a una base ya instalada el cambio de permisos de envíos (moscamed.sql ya lo trae para instalaciones nuevas):
-- la planta (PES) crea, modifica y cancela envíos; el centro de empaque (PEM) solo registra su llegada.
-- Los roles no se replican: córrelo en los dos servidores, como postgres.
--   psql -h localhost -p 5452 -U postgres -d moscamed -f bd/permisos_envios.sql
REVOKE INSERT, UPDATE ON envio FROM rol_empaque;
GRANT INSERT (lote_id, vehiculo_id, fecha_salida, cantidad, temperatura, responsable_id) ON envio TO rol_produccion;
GRANT UPDATE (lote_id, vehiculo_id, fecha_salida, cantidad, temperatura, cancelado) ON envio TO rol_produccion;
GRANT UPDATE (recibido) ON envio TO rol_empaque;
