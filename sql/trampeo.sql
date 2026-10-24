-- Base de trampeo (192.168.30.10, base "trampeo"). Las sedes la leen con postgres_fdw (fdw.sql).
--   createdb trampeo && psql -d trampeo -f trampeo.sql
SET client_encoding = 'UTF8';

CREATE TABLE revision (
    trampa      varchar(12) NOT NULL,
    zona_clave  varchar(8)  NOT NULL,
    fecha       date        NOT NULL,
    silvestres  integer     NOT NULL DEFAULT 0,
    PRIMARY KEY (trampa, fecha)
);
-- Las sedes piden "revisiones de los últimos N días por zona"; el FDW manda el filtro de fecha hasta aquí.
CREATE INDEX ix_revision_fecha ON revision (fecha, zona_clave);

-- Usuario de solo lectura para el FDW (cámbiale la contraseña en producción).
CREATE ROLE lector LOGIN PASSWORD 'lector';
GRANT SELECT ON revision TO lector;

-- Datos de ejemplo: una trampa por zona revisada a diario los últimos 30 días; capturas silvestres en algunas.
INSERT INTO revision (trampa, zona_clave, fecha, silvestres)
SELECT 'TR-' || substr(z, 4), z, current_date - g,
       CASE WHEN g = 3 AND z IN ('ZL-037', 'ZL-016', 'ZL-084', 'ZL-087') THEN 3
            WHEN g = 5 AND z = 'ZL-005' THEN 1 ELSE 0 END
FROM unnest(ARRAY['ZL-001', 'ZL-005', 'ZL-016', 'ZL-020', 'ZL-037', 'ZL-046', 'ZL-084', 'ZL-087']) z
CROSS JOIN generate_series(0, 29) g;
