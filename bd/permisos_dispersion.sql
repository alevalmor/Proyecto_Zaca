-- Aplica a una base ya instalada el cambio de permisos de dispersión (moscamed.sql ya lo trae para instalaciones nuevas):
-- el centro de empaque (PEM), igual que la encargada de dispersión (PED), registra, modifica y borra los informes.
-- Los roles no se replican: córrelo en los dos servidores, como postgres.
--   psql -h localhost -p 5452 -U postgres -d moscamed -f bd/permisos_dispersion.sql
GRANT INSERT (envio_id, embarque_id, punto, zona_id, fecha, cantidad, superficie_ha, altitud_m, avioneta, piloto, responsable_id, observaciones)
    ON liberacion TO rol_empaque;
GRANT UPDATE (envio_id, embarque_id, punto, zona_id, fecha, cantidad, superficie_ha, altitud_m, avioneta, piloto, observaciones)
    ON liberacion TO rol_empaque;
GRANT DELETE ON liberacion TO rol_dispersion, rol_empaque;
COMMENT ON TABLE liberacion IS 'Informes de dispersión aérea capturados por la encargada de dispersión (PED) o el centro de empaque (PEM).';
