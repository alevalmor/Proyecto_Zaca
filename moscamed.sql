-- =====================================================================================================
-- Moscamed · instalación completa (PostgreSQL 16 o más reciente) en un solo archivo.
--
-- Se corre como superusuario en este orden. Cada paso pide las contraseñas que necesita
-- (no se escriben en la línea de comandos para que no queden en el historial):
--
--   1. Trampeo (192.168.30.10):   createdb trampeo
--                                  psql -d trampeo  -v paso=trampeo -f moscamed.sql
--   2. Planta  (192.168.10.10):   createdb moscamed
--                                  psql -d moscamed -v paso=instalar -v sede=planta  -f moscamed.sql
--   3. Empaque (192.168.20.10):   createdb moscamed
--                                  psql -d moscamed -v paso=instalar -v sede=empaque -f moscamed.sql
--   4. Empaque:                    psql -d moscamed -v paso=suscribir -v sede=empaque -f moscamed.sql
--      (copia los datos de la planta; esperar a que termine: en empaque,
--       SELECT srsubstate FROM pg_subscription_rel; todas las filas deben estar en 'r')
--   5. Planta:                     psql -d moscamed -v paso=suscribir -v sede=planta  -f moscamed.sql
--   6. Pgpool-II en planta y en empaque (Linux):  sudo bash pgpool/instalar.sh planta   (o empaque)
--      El programa se conecta al Pgpool de su sede (puerto 9999); si cae el PostgreSQL de esa sede,
--      Pgpool lo pasa al de la otra, que tiene los mismos datos por la replicación master-master.
--
-- Variables opcionales (con su valor por omisión):
--   ip_planta=192.168.10.10  ip_empaque=192.168.20.10  ip_trampeo=192.168.30.10  puerto=5432
--   sede_admin=planta   servidor donde el Programa 2 da de alta usuarios y catálogos (ver punto 10 abajo)
--   ejemplo=si          en la planta carga personas, catálogos y movimientos de ejemplo (punto 11);
--                       con ejemplo=no la base queda vacía y los catálogos se cargan a mano con psql
--
-- Antes de empezar, en cada servidor:
--   · postgresql.conf de planta y empaque: wal_level = logical
--   · postgresql.conf de los tres: listen_addresses = '*'
--   · pg_hba.conf de planta y empaque (scram-sha-256), desde 127.0.0.1, 192.168.10.10 y 192.168.20.10:
--       host  moscamed  all  ...   la gente entra por el Pgpool de su sede o, en failover, por el de la otra;
--                                  el Programa 2 crea los usuarios en los dos servidores
--       host  postgres  pgpool ... Pgpool revisa con este usuario que el servidor responda
--     (replicador también entra a la base moscamed: la replicación lógica no usa la línea "replication")
--   · pg_hba.conf de trampeo: permitir al usuario lector desde 192.168.10.0/24 y 192.168.20.0/24
--
-- Para el Programa 2:
--   · No mandar id, codigo, creado ni modificado en los INSERT: los pone la base
--     (hay permisos por columna y el INSERT falla si los manda). responsable_id se acepta,
--     pero la base lo reemplaza por quien está en sesión.
--   · Los usuarios de PostgreSQL no se replican: al dar de alta a alguien hay que crear su usuario
--     en LOS DOS servidores (CREATE ROLE ... IN ROLE rol_xxx), conectándose como usr_admin.
--   · Usuarios, permisos por vista y catálogos (centros, vehículos, zonas) solo se escriben en
--     el servidor de sede_admin; así las dos sedes nunca editan la misma fila.
--   · Dar de baja (activo = false) quita el LOGIN del usuario en los dos servidores automáticamente.
-- =====================================================================================================

\set ON_ERROR_STOP on
\encoding UTF8
SET client_min_messages = warning;

\if :{?paso}
\else
    DO $$ BEGIN RAISE EXCEPTION 'Falta -v paso=trampeo, -v paso=instalar o -v paso=suscribir'; END $$;
\endif
\if :{?sede}
\else
    \set sede ''
\endif
\if :{?ip_planta}
\else
    \set ip_planta 192.168.10.10
\endif
\if :{?ip_empaque}
\else
    \set ip_empaque 192.168.20.10
\endif
\if :{?ip_trampeo}
\else
    \set ip_trampeo 192.168.30.10
\endif
\if :{?puerto}
\else
    \set puerto 5432
\endif
\if :{?puerto_trampeo}
\else
    \set puerto_trampeo :puerto
\endif
\if :{?puerto_otra}
\else
    \set puerto_otra :puerto
\endif
\if :{?sede_admin}
\else
    \set sede_admin planta
\endif
\if :{?ejemplo}
\else
    \set ejemplo si
\endif

SELECT :'paso' = 'trampeo' AS es_trampeo, :'paso' = 'instalar' AS es_instalar, :'paso' = 'suscribir' AS es_suscribir,
       :'sede' = 'planta' AND :'ejemplo' = 'si' AS cargar_ejemplo,
       :'paso' IN ('trampeo', 'instalar', 'suscribir') AS paso_ok,
       :'sede' IN ('planta', 'empaque') AS sede_ok, :'sede_admin' IN ('planta', 'empaque') AS sede_admin_ok,
       CASE :'sede' WHEN 'planta' THEN 1 ELSE 2 END AS inicio \gset

\if :paso_ok
\else
    DO $$ BEGIN RAISE EXCEPTION 'paso debe ser trampeo, instalar o suscribir'; END $$;
\endif
\if :es_trampeo
\elif :sede_ok
\else
    DO $$ BEGIN RAISE EXCEPTION 'Falta -v sede=planta o -v sede=empaque'; END $$;
\endif
\if :sede_admin_ok
\else
    DO $$ BEGIN RAISE EXCEPTION 'sede_admin debe ser planta o empaque'; END $$;
\endif


-- #####################################################################################################
-- PASO 1 · Base de trampeo (192.168.30.10). Las sedes la leen con postgres_fdw.
-- #####################################################################################################
\if :es_trampeo

\if :{?clave_lector}
\else
    \prompt 'Contraseña para el usuario lector (la usarán las dos sedes): ' clave_lector
\endif

REVOKE CREATE ON SCHEMA public FROM PUBLIC;

CREATE TABLE revision (
    trampa      varchar(12) NOT NULL,
    zona_clave  varchar(8)  NOT NULL,
    fecha       date        NOT NULL,
    silvestres  integer     NOT NULL DEFAULT 0 CHECK (silvestres >= 0),
    PRIMARY KEY (trampa, fecha)
);
-- Las sedes piden "revisiones de los últimos N días por zona"; el FDW manda el filtro de fecha hasta aquí.
CREATE INDEX ix_revision_fecha ON revision (fecha, zona_clave);

-- Usuario de solo lectura para el FDW.
CREATE ROLE lector LOGIN PASSWORD :'clave_lector';
GRANT SELECT ON revision TO lector;

-- Datos de ejemplo: una trampa por zona revisada a diario los últimos 30 días; capturas silvestres en algunas.
INSERT INTO revision (trampa, zona_clave, fecha, silvestres)
SELECT 'TR-' || substr(z, 4), z, current_date - g,
       CASE WHEN g = 3 AND z IN ('ZL-037', 'ZL-016', 'ZL-084', 'ZL-087') THEN 3
            WHEN g = 5 AND z = 'ZL-005' THEN 1 ELSE 0 END
FROM unnest(ARRAY['ZL-001', 'ZL-005', 'ZL-016', 'ZL-020', 'ZL-037', 'ZL-046', 'ZL-084', 'ZL-087']) z
CROSS JOIN generate_series(0, 29) g;

\echo 'Trampeo listo.'
\endif


-- #####################################################################################################
-- PASO 2 y 3 · Esquema, permisos, trampeo y publicación de una sede.
--   planta  -> ids impares    empaque -> ids pares    (ids que no chocan entre sedes)
-- #####################################################################################################
\if :es_instalar

\if :{?clave_lector}
\else
    \prompt 'Contraseña del usuario lector de la base de trampeo: ' clave_lector
\endif
\if :{?clave_replicador}
\else
    \prompt 'Contraseña nueva para el usuario replicador de esta sede: ' clave_replicador
\endif
\if :{?clave_usuarios}
\else
    \prompt 'Contraseña inicial de usr_admin y los usuarios de ejemplo: ' clave_usuarios
\endif
\if :{?clave_pgpool}
\else
    \prompt 'Contraseña del usuario pgpool (la misma en las dos sedes y en pgpool/instalar.sh): ' clave_pgpool
\endif

-- ---------- 1. Ajustes de la base ----------

-- Nadie crea objetos en public salvo el dueño; un candado colgado no bloquea para siempre.
-- Las dos sedes guardan fechas sin zona horaria: tienen que usar la misma.
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
DO $$
BEGIN
    EXECUTE format('ALTER DATABASE %I SET lock_timeout = %L', current_database(), '15s');
    EXECUTE format('ALTER DATABASE %I SET idle_in_transaction_session_timeout = %L', current_database(), '5min');
    EXECUTE format('ALTER DATABASE %I SET timezone = %L', current_database(), 'America/Mexico_City');
END $$;
SET timezone = 'America/Mexico_City';

-- Qué servidor es este y dónde se administra. Son funciones, no tablas, para que no se repliquen
-- ni las cambie nadie con un SET.
SELECT format('CREATE FUNCTION sede_local() RETURNS text LANGUAGE sql IMMUTABLE AS %L',
              'SELECT ' || quote_literal(:'sede')) \gexec
SELECT format('CREATE FUNCTION sede_admin() RETURNS text LANGUAGE sql IMMUTABLE AS %L',
              'SELECT ' || quote_literal(:'sede_admin')) \gexec

-- ---------- 2. Tablas ----------
-- creado / modificado: cuándo se escribió y cuándo cambió por última vez (los pone la base, no el programa).

CREATE TABLE responsable (
    id              integer GENERATED BY DEFAULT AS IDENTITY (START WITH :inicio INCREMENT BY 2) PRIMARY KEY,
    num_empleado    varchar(12)  NOT NULL UNIQUE,
    nombre          varchar(60)  NOT NULL,
    ap_paterno      varchar(60)  NOT NULL,
    ap_materno      varchar(60),
    puesto          varchar(80)  NOT NULL,
    telefono        varchar(20),
    correo          varchar(120) CHECK (correo ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
    sede            varchar(8)   NOT NULL CHECK (sede IN ('PLANTA', 'EMPAQUE')),
    -- PES planta Metapa · PEM centro de empaque · PEA aeropuerto de Tapachula · PED encargada de dispersión.
    -- Solo PES trabaja en el servidor de la planta; los otros tres, en el de Tapachula.
    perfil          varchar(3)   NOT NULL CHECK (perfil IN ('PES', 'PEM', 'PEA', 'PED')),
    usuario_bd      varchar(63)  NOT NULL UNIQUE CHECK (usuario_bd ~ '^[a-z][a-z0-9_.]{2,30}$'),
    activo          boolean      NOT NULL DEFAULT true,
    fecha_alta      date         NOT NULL DEFAULT current_date,
    creado          timestamp    NOT NULL DEFAULT now(),
    modificado      timestamp    NOT NULL DEFAULT now(),
    CHECK ((perfil = 'PES') = (sede = 'PLANTA')),
    CHECK (num_empleado ~ CASE WHEN sede = 'PLANTA' THEN '^MET-[0-9]{4}$' ELSE '^TAP-[0-9]{4}$' END)
);

-- Permisos por vista que el administrador quitó; sin fila = lo que permite el perfil.
CREATE TABLE permiso_vista (
    responsable_id  integer     NOT NULL REFERENCES responsable(id) ON DELETE CASCADE,
    vista           varchar(30) NOT NULL,
    ver             boolean     NOT NULL,
    modificar       boolean     NOT NULL,
    PRIMARY KEY (responsable_id, vista),
    CHECK (ver OR NOT modificar)  -- sin ver no se modifica
);

CREATE TABLE centro_empaque (
    id          integer GENERATED BY DEFAULT AS IDENTITY (START WITH :inicio INCREMENT BY 2) PRIMARY KEY,
    clave       varchar(12)  NOT NULL UNIQUE,
    nombre      varchar(100) NOT NULL,
    creado      timestamp    NOT NULL DEFAULT now(),
    modificado  timestamp    NOT NULL DEFAULT now()
);

CREATE TABLE lote_produccion (
    id                  integer GENERATED BY DEFAULT AS IDENTITY (START WITH :inicio INCREMENT BY 2) PRIMARY KEY,
    codigo              varchar(16)  NOT NULL UNIQUE,  -- MET-AAAA-NNNN; lo pone la base
    fecha_produccion    date         NOT NULL,
    fecha_irradiacion   date         NOT NULL,
    dosis_gy            numeric(5,1) NOT NULL CHECK (dosis_gy BETWEEN 90 AND 160),
    cantidad_pupas      bigint       NOT NULL CHECK (cantidad_pupas > 0),
    cepa                varchar(40)  NOT NULL,
    tinte               varchar(40)  NOT NULL,
    centro_empaque_id   integer      NOT NULL REFERENCES centro_empaque(id),
    responsable_id      integer      NOT NULL REFERENCES responsable(id),  -- lo pone la base: quien está en sesión
    observaciones       text,
    creado              timestamp    NOT NULL DEFAULT now(),
    modificado          timestamp    NOT NULL DEFAULT now(),
    CHECK (fecha_irradiacion >= fecha_produccion)
);

-- Criterio de aprobación en un solo lugar: lo usan la columna "aprobado" y las reglas de negocio.
CREATE FUNCTION calidad_aprobada(emergencia numeric, vuelo numeric, esterilidad numeric)
RETURNS boolean LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
    SELECT emergencia >= 75 AND vuelo >= 65 AND esterilidad >= 99.5
$$;

CREATE TABLE control_calidad (
    id              integer GENERATED BY DEFAULT AS IDENTITY (START WITH :inicio INCREMENT BY 2) PRIMARY KEY,
    lote_id         integer      NOT NULL REFERENCES lote_produccion(id),
    etapa           varchar(8)   NOT NULL CHECK (etapa IN ('PLANTA', 'EMPAQUE')),
    emergencia      numeric(5,2) NOT NULL CHECK (emergencia BETWEEN 0 AND 100),
    vuelo           numeric(5,2) NOT NULL CHECK (vuelo BETWEEN 0 AND 100),
    esterilidad     numeric(5,2) NOT NULL CHECK (esterilidad BETWEEN 0 AND 100),
    peso_mg         numeric(5,2) NOT NULL CHECK (peso_mg > 0),
    aprobado        boolean GENERATED ALWAYS AS (calidad_aprobada(emergencia, vuelo, esterilidad)) STORED,
    fecha           date         NOT NULL DEFAULT current_date,
    responsable_id  integer      NOT NULL REFERENCES responsable(id),  -- lo pone la base: quien hizo la última prueba
    observaciones   text,
    creado          timestamp    NOT NULL DEFAULT now(),
    modificado      timestamp    NOT NULL DEFAULT now(),
    UNIQUE (lote_id, etapa)
);

-- Camionetas del traslado terrestre Metapa -> Tapachula. Por tierra no se dispersa: la dispersión es solo aérea.
CREATE TABLE vehiculo (
    id          integer GENERATED BY DEFAULT AS IDENTITY (START WITH :inicio INCREMENT BY 2) PRIMARY KEY,
    matricula   varchar(12) NOT NULL UNIQUE,
    modelo      varchar(40) NOT NULL,
    capacidad   bigint      NOT NULL CHECK (capacidad > 0),
    activo      boolean     NOT NULL DEFAULT true,
    creado      timestamp   NOT NULL DEFAULT now(),
    modificado  timestamp   NOT NULL DEFAULT now()
);

CREATE TABLE zona_liberacion (
    id                  integer GENERATED BY DEFAULT AS IDENTITY (START WITH :inicio INCREMENT BY 2) PRIMARY KEY,
    clave               varchar(8)    NOT NULL UNIQUE,
    nombre              varchar(80)   NOT NULL,
    municipio           varchar(80)   NOT NULL,
    superficie_ha       numeric(10,1) NOT NULL CHECK (superficie_ha > 0),
    densidad_objetivo   integer       NOT NULL CHECK (densidad_objetivo > 0),  -- moscas por ha por semana
    creado              timestamp     NOT NULL DEFAULT now(),
    modificado          timestamp     NOT NULL DEFAULT now()
);

-- Traslado terrestre de un lote de la planta de Metapa al centro de empaque de Tapachula.
CREATE TABLE envio (
    id              integer GENERATED BY DEFAULT AS IDENTITY (START WITH :inicio INCREMENT BY 2) PRIMARY KEY,
    codigo          varchar(16)  NOT NULL UNIQUE,  -- TAP-ENV-NNNN; lo pone la base
    lote_id         integer      NOT NULL REFERENCES lote_produccion(id),
    vehiculo_id     integer      NOT NULL REFERENCES vehiculo(id),
    fecha_salida    timestamp    NOT NULL,
    cantidad        bigint       NOT NULL CHECK (cantidad > 0),
    temperatura     numeric(4,1) NOT NULL CHECK (temperatura BETWEEN -10 AND 40),
    responsable_id  integer      NOT NULL REFERENCES responsable(id),
    recibido        timestamp,   -- llegada al centro de empaque de Tapachula; sin ella no se dispersa
    cancelado       boolean      NOT NULL DEFAULT false,
    creado          timestamp    NOT NULL DEFAULT now(),
    modificado      timestamp    NOT NULL DEFAULT now(),
    CHECK (recibido IS NULL OR recibido >= fecha_salida),
    CHECK (NOT (cancelado AND recibido IS NOT NULL))  -- lo que ya llegó no se cancela
);

-- Embarques aéreos de moscas estériles desde Perú que llegan al aeropuerto de Tapachula.
-- Una parte se queda en el aeropuerto (se dispersa desde ahí) y el resto va al centro de empaque.
CREATE TABLE embarque_aereo (
    id                  integer GENERATED BY DEFAULT AS IDENTITY (START WITH :inicio INCREMENT BY 2) PRIMARY KEY,
    codigo              varchar(16)  NOT NULL UNIQUE,  -- AER-EMB-NNNN; lo pone la base
    guia_aerea          varchar(30)  NOT NULL,
    vuelo               varchar(20),
    origen              varchar(60)  NOT NULL DEFAULT 'Perú',
    fecha_llegada       timestamp    NOT NULL,
    cantidad            bigint       NOT NULL CHECK (cantidad > 0),
    queda_aeropuerto    bigint       NOT NULL,
    temperatura         numeric(4,1) CHECK (temperatura BETWEEN -10 AND 40),
    responsable_id      integer      NOT NULL REFERENCES responsable(id),
    observaciones       text,
    creado              timestamp    NOT NULL DEFAULT now(),
    modificado          timestamp    NOT NULL DEFAULT now(),
    CHECK (queda_aeropuerto BETWEEN 0 AND cantidad)
);

-- Informe de dispersión aérea que la avioneta entrega a la encargada de dispersión (perfil PED).
-- Las moscas salen de un envío ya recibido en el centro de empaque o de un embarque de Perú
-- (de la parte que quedó en el aeropuerto o de la que fue al centro de empaque).
CREATE TABLE liberacion (
    id              integer GENERATED BY DEFAULT AS IDENTITY (START WITH :inicio INCREMENT BY 2) PRIMARY KEY,
    envio_id        integer       REFERENCES envio(id),
    embarque_id     integer       REFERENCES embarque_aereo(id),
    punto           varchar(10)   NOT NULL CHECK (punto IN ('EMPAQUE', 'AEROPUERTO')),
    zona_id         integer       NOT NULL REFERENCES zona_liberacion(id),
    fecha           timestamp     NOT NULL,
    cantidad        bigint        NOT NULL CHECK (cantidad > 0),
    superficie_ha   numeric(10,1) NOT NULL CHECK (superficie_ha > 0),
    altitud_m       integer       NOT NULL CHECK (altitud_m BETWEEN 1 AND 6000),
    avioneta        varchar(12)   NOT NULL,
    piloto          varchar(80)   NOT NULL,
    responsable_id  integer       NOT NULL REFERENCES responsable(id),
    observaciones   text,
    creado          timestamp     NOT NULL DEFAULT now(),
    modificado      timestamp     NOT NULL DEFAULT now(),
    CHECK ((envio_id IS NULL) <> (embarque_id IS NULL)),
    CHECK (envio_id IS NULL OR punto = 'EMPAQUE')
);

-- Bitácora que escribe el programa con frases para la gente ("Registró el envío TAP-ENV-0101").
-- El programa solo manda la frase: fecha y usuario los pone la base.
CREATE TABLE auditoria (
    id      integer GENERATED BY DEFAULT AS IDENTITY (START WITH :inicio INCREMENT BY 2) PRIMARY KEY,
    fecha   timestamp   NOT NULL DEFAULT now(),
    usuario varchar(63) NOT NULL DEFAULT current_user,
    accion  text        NOT NULL
);

-- Historial que escribe la base sola en cada cambio, aunque entren por psql: antes y después de la fila.
CREATE TABLE historial (
    id          integer GENERATED BY DEFAULT AS IDENTITY (START WITH :inicio INCREMENT BY 2) PRIMARY KEY,
    fecha       timestamp   NOT NULL DEFAULT now(),
    usuario     varchar(63) NOT NULL,
    tabla       varchar(63) NOT NULL,
    operacion   varchar(6)  NOT NULL CHECK (operacion IN ('INSERT', 'UPDATE', 'DELETE')),
    registro_id integer,
    antes       jsonb,
    despues     jsonb
);

-- ---------- 3. Trampeo (postgres_fdw) ----------
-- Vive en la base de trampeo (paso 1). No se replica: cada sede la lee en vivo.
-- Todos leen con el mismo usuario de solo lectura: la base de trampeo no conoce a la gente de las sedes.

CREATE SCHEMA trampeo;
CREATE EXTENSION postgres_fdw;
CREATE SERVER srv_trampeo FOREIGN DATA WRAPPER postgres_fdw
    OPTIONS (host :'ip_trampeo', port :'puerto_trampeo', dbname 'trampeo');
CREATE USER MAPPING FOR PUBLIC SERVER srv_trampeo OPTIONS (user 'lector', password :'clave_lector');
IMPORT FOREIGN SCHEMA public LIMIT TO (revision) FROM SERVER srv_trampeo INTO trampeo;

-- ---------- 4. Índices ----------
-- PostgreSQL no indexa solo las llaves foráneas. Cada índice va con la consulta que lo usa.

CREATE INDEX ix_lote_fecha        ON lote_produccion (fecha_produccion DESC);  -- lista e inicio de lotes
CREATE INDEX ix_envio_lote        ON envio (lote_id);                         -- envíos de un lote, reglas de cantidad
CREATE INDEX ix_envio_vehiculo    ON envio (vehiculo_id);
CREATE INDEX ix_envio_salida      ON envio (fecha_salida DESC);               -- lista de envíos
CREATE INDEX ix_envio_en_camino   ON envio (fecha_salida) WHERE recibido IS NULL AND NOT cancelado;  -- inicio del PEM
CREATE INDEX ix_embarque_llegada  ON embarque_aereo (fecha_llegada DESC);     -- lista de embarques
CREATE INDEX ix_liberacion_envio  ON liberacion (envio_id) WHERE envio_id IS NOT NULL;        -- existencias, estado de envío
CREATE INDEX ix_liberacion_emb    ON liberacion (embarque_id, punto) WHERE embarque_id IS NOT NULL;
CREATE INDEX ix_liberacion_zona   ON liberacion (zona_id, fecha);             -- zonas prioritarias
CREATE INDEX ix_liberacion_fecha  ON liberacion (fecha DESC);                 -- informes del mes
CREATE INDEX ix_auditoria_fecha   ON auditoria (fecha DESC);
CREATE INDEX ix_historial_fila    ON historial (tabla, registro_id);
CREATE INDEX ix_historial_fecha   ON historial (fecha DESC);

-- ---------- 5. Funciones comunes ----------
-- Los triggers BEFORE corren en orden alfabético: t_0 sede, t_1 código, t_2 responsable, t_3 reglas, t_4 modificado.
-- Los triggers normales no corren al aplicar la replicación (session_replication_role = replica):
-- cada fila se valida y se registra una sola vez, en la sede donde se escribió.

-- Responsable de la persona en sesión (null para el superusuario o el administrador).
CREATE FUNCTION yo() RETURNS integer LANGUAGE sql STABLE AS $$
    SELECT id FROM responsable WHERE usuario_bd = current_user
$$;

CREATE FUNCTION es_superusuario() RETURNS boolean LANGUAGE sql STABLE AS $$
    SELECT coalesce((SELECT rolsuper FROM pg_roles WHERE rolname = current_user), false)
$$;

-- Siguiente código con el prefijo de la tabla. El candado evita que dos personas reciban el mismo número.
-- Pasando de 9999 el número crece a 5 cifras en vez de cortarse.
CREATE FUNCTION poner_codigo() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    prefijo text := TG_ARGV[0];
    n       integer;
BEGIN
    IF nullif(btrim(NEW.codigo), '') IS NOT NULL THEN
        RETURN NEW;  -- solo el superusuario puede mandar el código (cargas de datos)
    END IF;
    IF prefijo = 'MET-' THEN  -- los lotes llevan el año de producción
        prefijo := prefijo || extract(year FROM (to_jsonb(NEW) ->> 'fecha_produccion')::date) || '-';
    END IF;
    PERFORM pg_advisory_xact_lock(4, hashtext(prefijo));
    EXECUTE format('SELECT coalesce(max(substr(codigo, %s)::int), 0) + 1 FROM %I WHERE codigo ~ %L',
                   length(prefijo) + 1, TG_TABLE_NAME, '^' || prefijo || '[0-9]+$') INTO n;
    NEW.codigo := prefijo || lpad(n::text, greatest(4, length(n::text)), '0');
    RETURN NEW;
END $$;

-- El responsable de lo que se registra es quien está en sesión: no se puede firmar a nombre de otro.
-- En los cambios se conserva quien lo registró, salvo con el argumento 'firma' (control de calidad:
-- quien repite la prueba la firma). Un usuario dado de baja no registra ni cambia nada.
CREATE FUNCTION poner_responsable() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    r responsable%ROWTYPE;
BEGIN
    SELECT * INTO r FROM responsable WHERE usuario_bd = current_user;
    IF FOUND THEN
        IF NOT r.activo THEN
            RAISE EXCEPTION 'Tu usuario está dado de baja.';
        END IF;
        IF TG_OP = 'INSERT' OR TG_ARGV[0] = 'firma' THEN
            NEW.responsable_id := r.id;
        ELSE
            NEW.responsable_id := OLD.responsable_id;
        END IF;
    ELSIF NOT es_superusuario() THEN
        RAISE EXCEPTION 'Tu usuario no tiene perfil en el sistema.';
    END IF;  -- el superusuario (cargas de datos) indica el responsable a mano
    RETURN NEW;
END $$;

CREATE FUNCTION poner_modificado() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.creado := OLD.creado;  -- nadie reescribe cuándo se creó
    NEW.modificado := now();
    RETURN NEW;
END $$;

-- Usuarios, permisos y catálogos se escriben en un solo servidor (sede_admin): si las dos sedes
-- escribieran la misma fila, la replicación se detendría por llave duplicada o perdería un cambio.
CREATE FUNCTION solo_sede_admin() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF sede_local() <> sede_admin() AND NOT es_superusuario() THEN
        RAISE EXCEPTION 'Los usuarios y catálogos se modifican en el servidor de %. Este es el de %.',
                        upper(sede_admin()), upper(sede_local());
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END $$;

-- Corre como dueño de la tabla historial: nadie más tiene permiso de escribirla ni de borrarla.
CREATE FUNCTION registrar_historial() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE
    antes   jsonb := CASE WHEN TG_OP <> 'INSERT' THEN to_jsonb(OLD) END;
    despues jsonb := CASE WHEN TG_OP <> 'DELETE' THEN to_jsonb(NEW) END;
BEGIN
    INSERT INTO historial (usuario, tabla, operacion, registro_id, antes, despues)
    VALUES (session_user, TG_TABLE_NAME, TG_OP, (coalesce(despues, antes) ->> 'id')::int, antes, despues);
    RETURN NULL;
END $$;
REVOKE ALL ON FUNCTION registrar_historial() FROM PUBLIC;

-- ---------- 6. Reglas de negocio ----------
-- Viven en la base para que se cumplan escriba quien escriba y en cualquiera de los dos servidores.
-- Los candados (pg_advisory_xact_lock) son por servidor. Por eso cada regla se revisa con datos que se
-- escriben en la misma sede: la planta escribe lotes y su calidad; Tapachula, envíos, embarques,
-- dispersiones y la calidad de empaque. Donde una regla cruza sedes (cantidad del lote contra envíos),
-- la planta solo puede mover el dato en la dirección que no rompe nada (aumentar pupas).
-- Si Pgpool manda a alguien al servidor de la otra sede mientras el suyo está caído, los códigos que
-- se generen ahí pueden chocar al volver la conexión; en ese caso la replicación se detiene y avisa.
-- Margen de 1 hora en "no puede ser en el futuro" por relojes desfasados entre equipos.

-- El responsable tiene que pertenecer al rol de su perfil (si su usuario ya existe en este servidor).
CREATE FUNCTION validar_responsable() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    rol text := CASE NEW.perfil WHEN 'PES' THEN 'rol_produccion' WHEN 'PEM' THEN 'rol_empaque'
                                WHEN 'PEA' THEN 'rol_aeropuerto' ELSE 'rol_dispersion' END;
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = NEW.usuario_bd)
       AND NOT pg_has_role(NEW.usuario_bd, rol, 'MEMBER') THEN
        RAISE EXCEPTION 'El usuario % no pertenece a %, que es el rol del perfil %.', NEW.usuario_bd, rol, NEW.perfil;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER t_3_validar BEFORE INSERT OR UPDATE ON responsable FOR EACH ROW EXECUTE FUNCTION validar_responsable();

-- Dar de baja o de alta a una persona le quita o le devuelve el acceso. Corre también al llegar el
-- cambio por replicación (ENABLE ALWAYS), así el acceso se quita en los dos servidores.
CREATE FUNCTION sincronizar_acceso() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.activo IS DISTINCT FROM OLD.activo
       AND EXISTS (SELECT 1 FROM pg_roles WHERE rolname = NEW.usuario_bd AND NOT rolsuper) THEN
        EXECUTE format('ALTER ROLE %I %s', NEW.usuario_bd, CASE WHEN NEW.activo THEN 'LOGIN' ELSE 'NOLOGIN' END);
    END IF;
    RETURN NULL;
END $$;
CREATE TRIGGER t_acceso AFTER UPDATE OF activo ON responsable FOR EACH ROW EXECUTE FUNCTION sincronizar_acceso();
ALTER TABLE responsable ENABLE ALWAYS TRIGGER t_acceso;

CREATE FUNCTION validar_lote() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    enviadas bigint;
BEGIN
    IF NEW.fecha_produccion > current_date THEN
        RAISE EXCEPTION 'La fecha de producción no puede ser futura.';
    END IF;
    IF TG_OP = 'UPDATE' THEN
        PERFORM pg_advisory_xact_lock(1, NEW.id);  -- el mismo candado que toman los envíos del lote
        SELECT COALESCE(sum(cantidad), 0) INTO enviadas FROM envio WHERE lote_id = NEW.id AND NOT cancelado;
        IF NEW.cantidad_pupas < enviadas THEN
            RAISE EXCEPTION 'El lote % ya envió % pupas: no puede quedar con menos.', NEW.codigo, enviadas;
        END IF;
        -- Aprobado el lote, Tapachula ya puede registrar envíos que aquí todavía no llegan:
        -- la cantidad solo puede subir.
        IF NEW.cantidad_pupas < OLD.cantidad_pupas
           AND EXISTS (SELECT 1 FROM control_calidad WHERE lote_id = NEW.id AND etapa = 'PLANTA' AND aprobado) THEN
            RAISE EXCEPTION 'El lote % ya está aprobado: su cantidad de pupas solo puede aumentar.', NEW.codigo;
        END IF;
        IF enviadas > 0 AND (NEW.fecha_produccion, NEW.fecha_irradiacion, NEW.dosis_gy, NEW.cepa)
                            IS DISTINCT FROM (OLD.fecha_produccion, OLD.fecha_irradiacion, OLD.dosis_gy, OLD.cepa) THEN
            RAISE EXCEPTION 'El lote % ya salió de la planta: sus datos de producción ya no cambian.', NEW.codigo;
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER t_3_validar BEFORE INSERT OR UPDATE ON lote_produccion FOR EACH ROW EXECUTE FUNCTION validar_lote();

CREATE FUNCTION validar_calidad() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(1, NEW.lote_id);  -- no se cruza con un envío del mismo lote
    IF NEW.etapa = 'PLANTA' AND NOT pg_has_role('rol_produccion', 'USAGE') THEN
        RAISE EXCEPTION 'Solo la planta registra la calidad de la etapa PLANTA.';
    END IF;
    IF NEW.etapa = 'EMPAQUE' AND NOT pg_has_role('rol_empaque', 'USAGE') THEN
        RAISE EXCEPTION 'Solo el centro de empaque registra la calidad de la etapa EMPAQUE.';
    END IF;
    IF NEW.fecha > current_date THEN
        RAISE EXCEPTION 'La fecha de la prueba no puede ser futura.';
    END IF;
    IF NEW.etapa = 'EMPAQUE' AND NOT EXISTS (SELECT 1 FROM control_calidad WHERE lote_id = NEW.lote_id AND etapa = 'PLANTA') THEN
        RAISE EXCEPTION 'El lote aún no tiene la calidad de la planta.';
    END IF;
    IF TG_OP = 'UPDATE' AND NEW.etapa = 'PLANTA' AND EXISTS (SELECT 1 FROM envio WHERE lote_id = NEW.lote_id AND NOT cancelado) THEN
        RAISE EXCEPTION 'El lote ya tiene envíos: su calidad de planta ya no se cambia.';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER t_3_validar BEFORE INSERT OR UPDATE ON control_calidad FOR EACH ROW EXECUTE FUNCTION validar_calidad();

CREATE FUNCTION validar_envio() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    v vehiculo%ROWTYPE;
    l lote_produccion%ROWTYPE;
    enviadas bigint;
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF OLD.cancelado THEN
            RAISE EXCEPTION 'El envío % está cancelado y ya no se modifica.', OLD.codigo;
        END IF;
        IF OLD.recibido IS NOT NULL THEN
            RAISE EXCEPTION 'El envío % ya llegó a Tapachula: no se modifica ni se cancela.', OLD.codigo;
        END IF;
        IF NEW.cancelado OR NEW.recibido IS NOT NULL THEN
            IF (NEW.lote_id, NEW.vehiculo_id, NEW.cantidad, NEW.fecha_salida, NEW.temperatura)
               IS DISTINCT FROM (OLD.lote_id, OLD.vehiculo_id, OLD.cantidad, OLD.fecha_salida, OLD.temperatura) THEN
                RAISE EXCEPTION 'Al cancelar o recibir un envío no se cambian sus demás datos.';
            END IF;
            IF NEW.recibido > now() + interval '1 hour' THEN
                RAISE EXCEPTION 'La llegada a Tapachula no puede ser futura.';
            END IF;
            RETURN NEW;  -- cancelar devuelve las pupas al lote; recibir solo registra la hora
        END IF;
    END IF;
    PERFORM pg_advisory_xact_lock(1, NEW.lote_id);  -- dos envíos del mismo lote a la vez no se pasan de lo producido
    IF NOT EXISTS (SELECT 1 FROM control_calidad WHERE lote_id = NEW.lote_id AND etapa = 'PLANTA' AND aprobado)
       OR EXISTS (SELECT 1 FROM control_calidad WHERE lote_id = NEW.lote_id AND NOT aprobado) THEN
        RAISE EXCEPTION 'El lote no está aprobado por la planta o tiene un control de calidad rechazado.';
    END IF;
    SELECT * INTO l FROM lote_produccion WHERE id = NEW.lote_id;
    IF NEW.fecha_salida::date < l.fecha_irradiacion THEN
        RAISE EXCEPTION 'El envío no puede salir antes de irradiar el lote (%).', to_char(l.fecha_irradiacion, 'DD/MM/YYYY');
    END IF;
    IF NEW.fecha_salida > now() + interval '1 day' THEN
        RAISE EXCEPTION 'La salida se registra el día que sale la camioneta, no antes.';
    END IF;
    SELECT * INTO v FROM vehiculo WHERE id = NEW.vehiculo_id;
    IF NOT v.activo THEN
        RAISE EXCEPTION 'El vehículo % está fuera de servicio.', v.matricula;
    END IF;
    IF NEW.cantidad > v.capacidad THEN
        RAISE EXCEPTION 'No cabe en el vehículo %: lleva hasta % moscas.', v.matricula, v.capacidad;
    END IF;
    SELECT COALESCE(sum(cantidad), 0) INTO enviadas FROM envio
    WHERE lote_id = NEW.lote_id AND NOT cancelado AND id IS DISTINCT FROM NEW.id;
    IF enviadas + NEW.cantidad > l.cantidad_pupas THEN
        RAISE EXCEPTION 'Supera lo que queda del lote: quedan % pupas.', l.cantidad_pupas - enviadas;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER t_3_validar BEFORE INSERT OR UPDATE ON envio FOR EACH ROW EXECUTE FUNCTION validar_envio();

CREATE FUNCTION validar_embarque() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    primera timestamp;
BEGIN
    IF NEW.fecha_llegada > now() + interval '1 hour' THEN
        RAISE EXCEPTION 'La llegada del embarque no puede ser futura.';
    END IF;
    IF TG_OP = 'UPDATE' THEN
        PERFORM pg_advisory_xact_lock(3, NEW.id);  -- el mismo candado que toman sus dispersiones
        SELECT min(fecha) INTO primera FROM liberacion WHERE embarque_id = NEW.id;
        IF primera IS NOT NULL THEN
            IF (NEW.cantidad, NEW.queda_aeropuerto) IS DISTINCT FROM (OLD.cantidad, OLD.queda_aeropuerto) THEN
                RAISE EXCEPTION 'El embarque % ya tiene dispersiones: sus cantidades ya no cambian.', NEW.codigo;
            END IF;
            IF NEW.fecha_llegada > primera THEN
                RAISE EXCEPTION 'El embarque % tiene dispersiones desde el %: no pudo llegar después.',
                                NEW.codigo, to_char(primera, 'DD/MM/YYYY HH24:MI');
            END IF;
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER t_3_validar BEFORE INSERT OR UPDATE ON embarque_aereo FOR EACH ROW EXECUTE FUNCTION validar_embarque();

CREATE FUNCTION validar_liberacion() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    codigo      text;
    disponibles bigint;
    desde       timestamp;
    usadas      bigint;
    cancelado   boolean;
    lote        integer;
    zona        zona_liberacion%ROWTYPE;
BEGIN
    IF NEW.fecha > now() + interval '1 hour' THEN
        RAISE EXCEPTION 'La dispersión se registra después del vuelo: la fecha no puede ser futura.';
    END IF;
    SELECT * INTO zona FROM zona_liberacion WHERE id = NEW.zona_id;
    IF NEW.superficie_ha > zona.superficie_ha THEN
        RAISE EXCEPTION 'La zona % mide % ha: no se pueden cubrir %.', zona.clave, zona.superficie_ha, NEW.superficie_ha;
    END IF;
    IF NEW.envio_id IS NOT NULL THEN
        PERFORM pg_advisory_xact_lock(2, NEW.envio_id);
        SELECT e.codigo, e.cantidad, e.recibido, e.cancelado, e.lote_id INTO codigo, disponibles, desde, cancelado, lote
        FROM envio e WHERE e.id = NEW.envio_id;
        IF cancelado THEN
            RAISE EXCEPTION 'El envío % está cancelado: no se dispersa.', codigo;
        END IF;
        IF desde IS NULL THEN
            RAISE EXCEPTION 'El envío % aún no llega al centro de empaque de Tapachula.', codigo;
        END IF;
        IF EXISTS (SELECT 1 FROM control_calidad c WHERE c.lote_id = lote AND NOT c.aprobado) THEN
            RAISE EXCEPTION 'El lote del envío % tiene un control de calidad rechazado: no se dispersa.', codigo;
        END IF;
        SELECT COALESCE(sum(l.cantidad), 0) INTO usadas FROM liberacion l
        WHERE l.envio_id = NEW.envio_id AND l.id IS DISTINCT FROM NEW.id;
    ELSE
        PERFORM pg_advisory_xact_lock(3, NEW.embarque_id);
        SELECT b.codigo, CASE WHEN NEW.punto = 'AEROPUERTO' THEN b.queda_aeropuerto ELSE b.cantidad - b.queda_aeropuerto END, b.fecha_llegada
        INTO codigo, disponibles, desde FROM embarque_aereo b WHERE b.id = NEW.embarque_id;
        SELECT COALESCE(sum(l.cantidad), 0) INTO usadas FROM liberacion l
        WHERE l.embarque_id = NEW.embarque_id AND l.punto = NEW.punto AND l.id IS DISTINCT FROM NEW.id;
    END IF;
    IF NEW.fecha < desde THEN
        RAISE EXCEPTION 'La dispersión no puede ser antes de que llegaran las moscas de % (%).', codigo, to_char(desde, 'DD/MM/YYYY HH24:MI');
    END IF;
    IF usadas + NEW.cantidad > disponibles THEN
        RAISE EXCEPTION 'Se dispersarían más moscas de las que hay en %: quedan %.', codigo, disponibles - usadas;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER t_3_validar BEFORE INSERT OR UPDATE ON liberacion FOR EACH ROW EXECUTE FUNCTION validar_liberacion();

-- Triggers comunes de cada tabla.
CREATE TRIGGER t_1_codigo BEFORE INSERT ON lote_produccion FOR EACH ROW EXECUTE FUNCTION poner_codigo('MET-');
CREATE TRIGGER t_1_codigo BEFORE INSERT ON envio           FOR EACH ROW EXECUTE FUNCTION poner_codigo('TAP-ENV-');
CREATE TRIGGER t_1_codigo BEFORE INSERT ON embarque_aereo  FOR EACH ROW EXECUTE FUNCTION poner_codigo('AER-EMB-');

CREATE TRIGGER t_2_responsable BEFORE INSERT OR UPDATE ON lote_produccion FOR EACH ROW EXECUTE FUNCTION poner_responsable();
CREATE TRIGGER t_2_responsable BEFORE INSERT OR UPDATE ON control_calidad FOR EACH ROW EXECUTE FUNCTION poner_responsable('firma');
CREATE TRIGGER t_2_responsable BEFORE INSERT OR UPDATE ON envio           FOR EACH ROW EXECUTE FUNCTION poner_responsable();
CREATE TRIGGER t_2_responsable BEFORE INSERT OR UPDATE ON embarque_aereo  FOR EACH ROW EXECUTE FUNCTION poner_responsable();
CREATE TRIGGER t_2_responsable BEFORE INSERT OR UPDATE ON liberacion      FOR EACH ROW EXECUTE FUNCTION poner_responsable();

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['responsable', 'permiso_vista', 'centro_empaque', 'vehiculo', 'zona_liberacion'] LOOP
        EXECUTE format('CREATE TRIGGER t_0_sede BEFORE INSERT OR UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION solo_sede_admin()', t);
    END LOOP;
    FOREACH t IN ARRAY ARRAY['responsable', 'centro_empaque', 'lote_produccion', 'control_calidad', 'vehiculo',
                             'zona_liberacion', 'envio', 'embarque_aereo', 'liberacion'] LOOP
        EXECUTE format('CREATE TRIGGER t_4_modificado BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION poner_modificado()', t);
    END LOOP;
    FOREACH t IN ARRAY ARRAY['responsable', 'permiso_vista', 'centro_empaque', 'lote_produccion', 'control_calidad',
                             'vehiculo', 'zona_liberacion', 'envio', 'embarque_aereo', 'liberacion'] LOOP
        EXECUTE format('CREATE TRIGGER t_historial AFTER INSERT OR UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION registrar_historial()', t);
    END LOOP;
END $$;

-- ---------- 7. Vistas ----------
-- El estado nunca se escribe: se calcula, así las sedes no lo editan a la vez.
-- security_invoker: la vista respeta los permisos de quien consulta, no los del dueño.

CREATE VIEW v_envio WITH (security_invoker = true) AS
SELECT e.*,
       COALESCE(l.liberadas, 0) AS liberadas,
       CASE WHEN e.cancelado THEN 'CANCELADO'
            WHEN e.recibido IS NULL THEN 'EN CAMINO'
            WHEN COALESCE(l.liberadas, 0) >= e.cantidad THEN 'DISPERSADO'
            ELSE 'RECIBIDO' END AS estado
FROM envio e
LEFT JOIN (SELECT envio_id, sum(cantidad) AS liberadas FROM liberacion WHERE envio_id IS NOT NULL GROUP BY envio_id) l ON l.envio_id = e.id;

-- LIBERADO: todos sus envíos ya se dispersaron (por_enviar dice si aún quedan pupas en la planta).
CREATE VIEW v_lote WITH (security_invoker = true) AS
SELECT lp.*,
       cp.aprobado AS calidad_planta,
       COALESCE(en.enviadas, 0) AS enviadas,
       lp.cantidad_pupas - COALESCE(en.enviadas, 0) AS por_enviar,
       COALESCE(en.liberadas, 0) AS liberadas,
       COALESCE(en.envios, 0) AS envios,
       COALESCE(en.dispersados, 0) AS dispersados,
       CASE WHEN EXISTS (SELECT 1 FROM control_calidad c WHERE c.lote_id = lp.id AND NOT c.aprobado) THEN 'RECHAZADO'
            WHEN cp.aprobado IS NULL THEN 'PRODUCIDO'
            WHEN COALESCE(en.envios, 0) = 0 THEN 'APROBADO'
            WHEN en.dispersados = en.envios THEN 'LIBERADO'
            ELSE 'ENVIADO' END AS estado
FROM lote_produccion lp
LEFT JOIN control_calidad cp ON cp.lote_id = lp.id AND cp.etapa = 'PLANTA'
LEFT JOIN (SELECT lote_id, sum(cantidad) AS enviadas, sum(liberadas) AS liberadas,
                  count(*) AS envios, count(*) FILTER (WHERE estado = 'DISPERSADO') AS dispersados
           FROM v_envio WHERE NOT cancelado GROUP BY lote_id) en ON en.lote_id = lp.id;

CREATE VIEW v_embarque WITH (security_invoker = true) AS
SELECT b.*,
       b.cantidad - b.queda_aeropuerto AS va_empaque,
       COALESCE(sum(li.cantidad) FILTER (WHERE li.punto = 'AEROPUERTO'), 0) AS dispersadas_aeropuerto,
       COALESCE(sum(li.cantidad) FILTER (WHERE li.punto = 'EMPAQUE'), 0) AS dispersadas_empaque
FROM embarque_aereo b LEFT JOIN liberacion li ON li.embarque_id = b.id
GROUP BY b.id;

-- Moscas listas para dispersar: envíos recibidos en Tapachula y las dos partes de cada embarque de Perú.
CREATE VIEW v_existencia WITH (security_invoker = true) AS
SELECT 'ENVIO' AS origen, e.id, e.codigo, 'EMPAQUE' AS punto, e.recibido AS desde, l.codigo AS referencia,
       e.cantidad AS recibidas, e.liberadas AS dispersadas, e.cantidad - e.liberadas AS quedan
FROM v_envio e JOIN lote_produccion l ON l.id = e.lote_id WHERE e.estado IN ('RECIBIDO', 'DISPERSADO')
UNION ALL
SELECT 'EMBARQUE', b.id, b.codigo, 'AEROPUERTO', b.fecha_llegada, b.guia_aerea,
       b.queda_aeropuerto, b.dispersadas_aeropuerto, b.queda_aeropuerto - b.dispersadas_aeropuerto
FROM v_embarque b WHERE b.queda_aeropuerto > 0
UNION ALL
SELECT 'EMBARQUE', b.id, b.codigo, 'EMPAQUE', b.fecha_llegada, b.guia_aerea,
       b.va_empaque, b.dispersadas_empaque, b.va_empaque - b.dispersadas_empaque
FROM v_embarque b WHERE b.va_empaque > 0;

-- ---------- 8. Procedimientos y consultas ----------
-- Corren con los permisos de quien los llama (SECURITY INVOKER): los roles y triggers siguen mandando.

-- Llegada de un envío al centro de empaque de Tapachula, con su bitácora, en una sola transacción.
CREATE PROCEDURE recibir_envio(p_codigo text, p_llegada timestamp DEFAULT localtimestamp) LANGUAGE plpgsql AS $$
BEGIN
    UPDATE envio SET recibido = p_llegada WHERE codigo = p_codigo;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'No existe el envío %.', p_codigo;
    END IF;
    INSERT INTO auditoria (accion) VALUES ('Recibió en Tapachula el envío ' || p_codigo);
END $$;

CREATE PROCEDURE cancelar_envio(p_codigo text) LANGUAGE plpgsql AS $$
BEGIN
    UPDATE envio SET cancelado = true WHERE codigo = p_codigo;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'No existe el envío %.', p_codigo;
    END IF;
    INSERT INTO auditoria (accion) VALUES ('Canceló el envío ' || p_codigo);
END $$;

-- Prioridad de cada zona: cruza el trampeo (FDW) con lo dispersado en los últimos "dias" hasta "corte".
--   ALTA: hubo moscas silvestres -> se sugiere el doble.  MEDIA: sin capturas pero sin dispersar.  NORMAL: al día.
--   mtd: moscas por trampa por día (capturas / (trampas × días del periodo)).
CREATE FUNCTION zonas_prioritarias(corte date, dias integer)
RETURNS TABLE (id integer, clave varchar, nombre varchar, municipio varchar, superficie_ha numeric, densidad_objetivo integer,
               trampas bigint, silvestres bigint, mtd numeric, liberadas numeric, prioridad text, sugeridas bigint)
LANGUAGE sql STABLE AS $$
    WITH t AS (SELECT zona_clave, count(DISTINCT trampa) AS trampas, sum(silvestres) AS silvestres
               FROM trampeo.revision WHERE fecha > corte - dias AND fecha <= corte GROUP BY zona_clave),
         l AS (SELECT zona_id, sum(cantidad) AS liberadas FROM liberacion
               WHERE fecha >= corte - dias + 1 AND fecha < corte + 1 GROUP BY zona_id),
         z AS (SELECT z.id, z.clave, z.nombre, z.municipio, z.superficie_ha, z.densidad_objetivo,
                      coalesce(t.trampas, 0) AS trampas, coalesce(t.silvestres, 0) AS silvestres,
                      round(coalesce(t.silvestres::numeric / nullif(t.trampas * dias, 0), 0), 4) AS mtd,
                      coalesce(l.liberadas, 0) AS liberadas
               FROM zona_liberacion z LEFT JOIN t ON t.zona_clave = z.clave LEFT JOIN l ON l.zona_id = z.id),
         p AS (SELECT z.*, CASE WHEN z.silvestres > 0 THEN 'ALTA' WHEN z.liberadas = 0 THEN 'MEDIA' ELSE 'NORMAL' END AS prioridad FROM z)
    SELECT p.*, trunc(p.superficie_ha * p.densidad_objetivo * CASE WHEN p.prioridad = 'ALTA' THEN 2 ELSE 1 END)::bigint
    FROM p
    ORDER BY CASE p.prioridad WHEN 'ALTA' THEN 0 WHEN 'MEDIA' THEN 1 ELSE 2 END, p.mtd DESC, p.clave
$$;

-- ---------- 9. Documentación (se ve en psql con \d+ y en pgAdmin) ----------
COMMENT ON TABLE responsable     IS 'Personas que usan el Programa 1; usuario_bd es su usuario de PostgreSQL.';
COMMENT ON TABLE permiso_vista   IS 'Permisos que el administrador quitó a una persona; solo restan a los del perfil.';
COMMENT ON TABLE lote_produccion IS 'Lotes de pupas producidos e irradiados en la planta de Metapa (PES).';
COMMENT ON TABLE control_calidad IS 'Pruebas de calidad por lote: etapa PLANTA (obligatoria) y EMPAQUE (opcional).';
COMMENT ON TABLE vehiculo        IS 'Camionetas del traslado terrestre Metapa -> Tapachula.';
COMMENT ON TABLE zona_liberacion IS 'Zonas donde se dispersan moscas; la clave enlaza con el trampeo.';
COMMENT ON TABLE envio           IS 'Traslado terrestre de un lote a Tapachula (PEM). recibido = llegada al centro de empaque.';
COMMENT ON TABLE embarque_aereo  IS 'Embarques de moscas estériles de Perú en el aeropuerto de Tapachula (PEA).';
COMMENT ON TABLE liberacion      IS 'Informes de dispersión aérea capturados por la encargada de dispersión (PED) o el centro de empaque (PEM).';
COMMENT ON TABLE auditoria       IS 'Bitácora con frases que escribe el programa.';
COMMENT ON TABLE historial       IS 'Cada alta, cambio o baja con la fila antes y después; la escribe la base sola.';
COMMENT ON VIEW v_existencia     IS 'Moscas por dispersar por envío o embarque y lugar (aeropuerto o centro de empaque).';
COMMENT ON FUNCTION zonas_prioritarias(date, integer) IS 'Zonas ordenadas por prioridad según trampeo y dispersiones.';
COMMENT ON FUNCTION sede_local() IS 'Servidor donde está esta base (planta o empaque). No se replica.';
COMMENT ON FUNCTION sede_admin() IS 'Servidor donde se escriben usuarios, permisos y catálogos.';
COMMENT ON PROCEDURE recibir_envio(text, timestamp) IS 'Registra la llegada de un envío a Tapachula.';
COMMENT ON PROCEDURE cancelar_envio(text) IS 'Cancela un envío que aún no llega.';

-- ---------- 10. Roles y permisos ----------
-- Los roles no se replican: este paso crea los mismos en los dos servidores.
-- La base de datos hace cumplir los perfiles: aunque el programa fallara, nadie escribe lo que no le toca.
-- Nadie tiene DELETE sobre datos de operación: lo registrado se corrige o se cancela, no se borra.
-- Las altas son por columna: ni el id, ni el código, ni las fechas de control los manda el programa
-- (un id con la paridad de la otra sede detendría la replicación). responsable_id sí se puede mandar,
-- pero el trigger t_2_responsable lo reemplaza por quien está en sesión.

CREATE ROLE rol_produccion NOLOGIN;  -- PES · planta Metapa
CREATE ROLE rol_empaque    NOLOGIN;  -- PEM · centro de empaque Tapachula
CREATE ROLE rol_aeropuerto NOLOGIN;  -- PEA · aeropuerto de Tapachula (embarques de Perú)
CREATE ROLE rol_dispersion NOLOGIN;  -- PED · encargada de dispersión aérea
CREATE ROLE rol_admin      NOLOGIN;  -- Programa 2

GRANT USAGE ON SCHEMA trampeo TO rol_produccion, rol_empaque, rol_aeropuerto, rol_dispersion, rol_admin;
GRANT SELECT ON ALL TABLES IN SCHEMA public, trampeo TO rol_produccion, rol_empaque, rol_aeropuerto, rol_dispersion, rol_admin;
REVOKE SELECT ON historial FROM rol_produccion, rol_empaque, rol_aeropuerto, rol_dispersion;  -- solo el administrador lo consulta
GRANT INSERT (accion) ON auditoria TO rol_produccion, rol_empaque, rol_aeropuerto, rol_dispersion, rol_admin;

-- Planta: lotes y su calidad.
GRANT INSERT (fecha_produccion, fecha_irradiacion, dosis_gy, cantidad_pupas, cepa, tinte, centro_empaque_id, responsable_id, observaciones)
    ON lote_produccion TO rol_produccion;
GRANT UPDATE (fecha_produccion, fecha_irradiacion, dosis_gy, cantidad_pupas, cepa, tinte, centro_empaque_id, observaciones)
    ON lote_produccion TO rol_produccion;
GRANT INSERT (lote_id, etapa, emergencia, vuelo, esterilidad, peso_mg, fecha, responsable_id, observaciones)
    ON control_calidad TO rol_produccion, rol_empaque;
GRANT UPDATE (emergencia, vuelo, esterilidad, peso_mg, fecha, responsable_id, observaciones)
    ON control_calidad TO rol_produccion, rol_empaque;  -- quien repite la prueba la firma (lo pone la base)

-- Envíos: la planta los da de alta en camino, los modifica o los cancela; el centro de empaque solo registra la llegada.
GRANT INSERT (lote_id, vehiculo_id, fecha_salida, cantidad, temperatura, responsable_id) ON envio TO rol_produccion;
GRANT UPDATE (lote_id, vehiculo_id, fecha_salida, cantidad, temperatura, cancelado) ON envio TO rol_produccion;
GRANT UPDATE (recibido) ON envio TO rol_empaque;

-- Aeropuerto: embarques de Perú.
GRANT INSERT (guia_aerea, vuelo, origen, fecha_llegada, cantidad, queda_aeropuerto, temperatura, responsable_id, observaciones)
    ON embarque_aereo TO rol_aeropuerto;
GRANT UPDATE (guia_aerea, vuelo, origen, fecha_llegada, cantidad, queda_aeropuerto, temperatura, observaciones)
    ON embarque_aereo TO rol_aeropuerto;

-- Dispersión: informes de vuelo. La encargada y el centro de empaque los registran, modifican y borran.
GRANT INSERT (envio_id, embarque_id, punto, zona_id, fecha, cantidad, superficie_ha, altitud_m, avioneta, piloto, responsable_id, observaciones)
    ON liberacion TO rol_dispersion, rol_empaque;
GRANT UPDATE (envio_id, embarque_id, punto, zona_id, fecha, cantidad, superficie_ha, altitud_m, avioneta, piloto, observaciones)
    ON liberacion TO rol_dispersion, rol_empaque;
GRANT DELETE ON liberacion TO rol_dispersion, rol_empaque;

-- Administrador (Programa 2): personas, permisos por vista y catálogos.
GRANT INSERT (num_empleado, nombre, ap_paterno, ap_materno, puesto, telefono, correo, sede, perfil, usuario_bd, activo, fecha_alta),
      UPDATE (num_empleado, nombre, ap_paterno, ap_materno, puesto, telefono, correo, sede, perfil, usuario_bd, activo, fecha_alta),
      DELETE ON responsable TO rol_admin;
GRANT INSERT, UPDATE, DELETE ON permiso_vista TO rol_admin;
GRANT INSERT (clave, nombre), UPDATE (clave, nombre) ON centro_empaque TO rol_admin;
GRANT INSERT (matricula, modelo, capacidad, activo), UPDATE (matricula, modelo, capacidad, activo) ON vehiculo TO rol_admin;
GRANT INSERT (clave, nombre, municipio, superficie_ha, densidad_objetivo),
      UPDATE (clave, nombre, municipio, superficie_ha, densidad_objetivo) ON zona_liberacion TO rol_admin;
GRANT pg_monitor TO rol_admin;  -- para ver el estado de la replicación en "Estado del sistema"

-- Administrador y usuarios de ejemplo (cámbiales la contraseña después). Los demás se crean desde el Programa 2.
CREATE ROLE usr_admin LOGIN CREATEROLE PASSWORD :'clave_usuarios' CONNECTION LIMIT 5 IN ROLE rol_admin;
-- El administrador puede dar estos roles, pero no hereda sus permisos: no registra lotes, envíos ni dispersiones.
GRANT rol_produccion, rol_empaque, rol_aeropuerto, rol_dispersion TO usr_admin WITH ADMIN TRUE, INHERIT FALSE, SET FALSE;
-- Los crea usr_admin para que después pueda cambiarles la contraseña o darlos de baja (PostgreSQL 16).
SET ROLE usr_admin;
CREATE ROLE usr_planta     LOGIN PASSWORD :'clave_usuarios' CONNECTION LIMIT 30 IN ROLE rol_produccion;
CREATE ROLE usr_empaque    LOGIN PASSWORD :'clave_usuarios' CONNECTION LIMIT 30 IN ROLE rol_empaque;
CREATE ROLE usr_aeropuerto LOGIN PASSWORD :'clave_usuarios' CONNECTION LIMIT 30 IN ROLE rol_aeropuerto;
CREATE ROLE usr_dispersion LOGIN PASSWORD :'clave_usuarios' CONNECTION LIMIT 30 IN ROLE rol_dispersion;
RESET ROLE;

-- Pgpool-II (health_check_user) entra con este usuario a la base postgres para ver si el servidor responde.
-- No tiene permisos sobre las tablas de Moscamed.
CREATE ROLE pgpool LOGIN PASSWORD :'clave_pgpool' CONNECTION LIMIT 10;

-- ---------- 11. Datos de ejemplo (solo en la planta; la suscripción los copia a empaque) ----------
-- Sin estas filas el Programa 1 no deja entrar a los usuarios de ejemplo y no hay centros, vehículos
-- ni zonas para registrar lotes, envíos y dispersiones (el Programa 2 no edita catálogos).
-- Corre como superusuario: por eso puede mandar código y responsable a mano. Fechas relativas a hoy.
\if :cargar_ejemplo

INSERT INTO responsable (num_empleado, nombre, ap_paterno, ap_materno, puesto, telefono, correo, sede, perfil, usuario_bd, fecha_alta) VALUES
 ('MET-0003', 'Diana', 'Rodríguez', 'Roblero', 'Técnico de irradiación', '9628472671', 'diana.rodriguez@moscamed.ejemplo.mx', 'PLANTA', 'PES', 'usr_planta', '2017-12-22'),
 ('MET-0001', 'Pedro', 'Mendoza', 'Ruiz', 'Analista de control de calidad', '9621110001', 'pedro.mendoza@moscamed.ejemplo.mx', 'PLANTA', 'PES', 'p.mendoza', '2015-03-02'),
 ('TAP-0008', 'Luis', 'Pineda', 'Matías', 'Jefe de empaque', '9621110008', 'luis.pineda@moscamed.ejemplo.mx', 'EMPAQUE', 'PEM', 'usr_empaque', '2016-06-15'),
 ('TAP-0021', 'Karla', 'Velasco', 'Toledo', 'Encargada de dispersión', '9621110021', 'karla.velasco@moscamed.ejemplo.mx', 'EMPAQUE', 'PED', 'usr_dispersion', '2019-01-10'),
 ('TAP-0030', 'Óscar', 'Cifuentes', 'Roblero', 'Encargado de aeropuerto', '9621110030', 'oscar.cifuentes@moscamed.ejemplo.mx', 'EMPAQUE', 'PEA', 'usr_aeropuerto', '2020-08-01');

INSERT INTO centro_empaque (clave, nombre) VALUES
 ('CEMM-TAP', 'Centro de Empaque Tapachula');

INSERT INTO vehiculo (matricula, modelo, capacidad) VALUES
 ('DA-583-E', 'Isuzu ELF refrigerado', 30000000),
 ('DA-214-K', 'Isuzu ELF refrigerado', 30000000),
 ('DB-102-A', 'NP300 refrigerada', 12000000);

-- Las mismas claves que las trampas de ejemplo del paso 1.
INSERT INTO zona_liberacion (clave, nombre, municipio, superficie_ha, densidad_objetivo) VALUES
 ('ZL-001', 'Metapa sector 1', 'Metapa', 8920, 3000),
 ('ZL-005', 'La Trinitaria sector 1', 'La Trinitaria', 8394, 3000),
 ('ZL-016', 'Chicomuselo sector 1', 'Chicomuselo', 5558, 3000),
 ('ZL-020', 'Tapachula sector 5', 'Tapachula', 8173, 3000),
 ('ZL-037', 'Amatenango sector 2', 'Amatenango de la Frontera', 2234, 3000),
 ('ZL-046', 'Villa Comaltitlán sector 1', 'Villa Comaltitlán', 6110, 3000),
 ('ZL-084', 'Tapachula sector 3', 'Tapachula', 6269, 3000),
 ('ZL-087', 'Motozintla sector 4', 'Motozintla', 4523, 3000);

INSERT INTO lote_produccion (codigo, fecha_produccion, fecha_irradiacion, dosis_gy, cantidad_pupas, cepa, tinte, centro_empaque_id, responsable_id)
SELECT v.codigo, current_date - v.d, current_date - v.d + 1, 120.0, v.pupas, v.cepa, 'Rosa fluorescente',
       (SELECT id FROM centro_empaque WHERE clave = 'CEMM-TAP'),
       (SELECT id FROM responsable WHERE num_empleado = 'MET-0003')
FROM (VALUES
 ('MET-' || extract(year FROM current_date - 26) || '-0075', 26, 41095000, 'Tapachula-7 TSL'),
 ('MET-' || extract(year FROM current_date - 24) || '-0077', 24, 44226000, 'Tapachula-7 TSL'),
 ('MET-' || extract(year FROM current_date - 19) || '-0084', 19, 58640000, 'Tapachula-7 TSL'),
 ('MET-' || extract(year FROM current_date - 15) || '-0088', 15, 49107000, 'VIENNA-8 TSL'),
 ('MET-' || extract(year FROM current_date - 5)  || '-0098', 5, 55312000, 'Tapachula-7 TSL'),
 ('MET-' || extract(year FROM current_date - 4)  || '-0099', 4, 47880000, 'Tapachula-7 TSL'),
 ('MET-' || extract(year FROM current_date - 3)  || '-0100', 3, 51204000, 'Tapachula-7 TSL')
) AS v(codigo, d, pupas, cepa);

-- El lote 0077 queda rechazado (vuelo 58.2 < 65); 0098 a 0100 siguen sin calidad (PRODUCIDO).
INSERT INTO control_calidad (lote_id, etapa, emergencia, vuelo, esterilidad, peso_mg, fecha, responsable_id)
SELECT l.id, 'PLANTA', v.em, v.vu, 99.9, 7.85, l.fecha_irradiacion + 1, (SELECT id FROM responsable WHERE num_empleado = 'MET-0001')
FROM (VALUES ('0075', 86.0, 77.0), ('0077', 81.0, 58.2), ('0084', 86.4, 78.1), ('0088', 85.1, 74.0)) AS v(n, em, vu)
JOIN lote_produccion l ON l.codigo LIKE 'MET-%-' || v.n;

-- Traslados terrestres Metapa -> Tapachula; el último sigue en camino.
INSERT INTO envio (codigo, lote_id, vehiculo_id, fecha_salida, cantidad, temperatura, responsable_id, recibido)
SELECT v.codigo, (SELECT id FROM lote_produccion WHERE codigo LIKE 'MET-%-' || v.lote), (SELECT id FROM vehiculo WHERE matricula = v.veh),
       current_date - v.d + time '07:30', v.cant, v.temp, (SELECT id FROM responsable WHERE num_empleado = 'TAP-0008'),
       CASE WHEN v.llego THEN current_date - v.d + time '09:00' END
FROM (VALUES
 ('TAP-ENV-0090', '0075', 'DA-583-E', 22, 25000000, 3.1, true),
 ('TAP-ENV-0091', '0084', 'DA-583-E', 15, 25426000, 3.3, true),
 ('TAP-ENV-0094', '0084', 'DA-214-K', 14, 23511000, 3.0, true),
 ('TAP-ENV-0100', '0088', 'DA-583-E', 0, 24800000, 3.1, false)
) AS v(codigo, lote, veh, d, cant, temp, llego);

-- Embarques aéreos de Perú en el aeropuerto de Tapachula: una parte se queda y el resto va al centro de empaque.
INSERT INTO embarque_aereo (codigo, guia_aerea, vuelo, fecha_llegada, cantidad, queda_aeropuerto, temperatura, responsable_id)
SELECT v.codigo, v.guia, v.vuelo, current_date - v.d + time '05:40', v.cant, v.queda, 4.0,
       (SELECT id FROM responsable WHERE num_empleado = 'TAP-0030')
FROM (VALUES
 ('AER-EMB-0001', '145-22310455', 'LA2464', 6, 40000000, 15000000),
 ('AER-EMB-0002', '145-22904417', 'LA2464', 1, 38000000, 20000000)
) AS v(codigo, guia, vuelo, d, cant, queda);

-- Informes de dispersión aérea capturados por la encargada de dispersión.
INSERT INTO liberacion (envio_id, embarque_id, punto, zona_id, fecha, cantidad, superficie_ha, altitud_m, avioneta, piloto, responsable_id)
SELECT (SELECT id FROM envio WHERE codigo = v.fuente), (SELECT id FROM embarque_aereo WHERE codigo = v.fuente), v.punto,
       (SELECT id FROM zona_liberacion WHERE clave = v.zona),
       coalesce((SELECT recibido FROM envio WHERE codigo = v.fuente), (SELECT fecha_llegada FROM embarque_aereo WHERE codigo = v.fuente)) + interval '90 minutes',
       v.cant, v.sup, 2300, v.avioneta, v.piloto, (SELECT id FROM responsable WHERE num_empleado = 'TAP-0021')
FROM (VALUES
 ('TAP-ENV-0090', 'EMPAQUE', 'ZL-046', 25000000, 6110, 'XB-TWE', 'Cap. Ramiro Gálvez'),
 ('TAP-ENV-0091', 'EMPAQUE', 'ZL-020', 24401000, 8173, 'XB-TWE', 'Cap. Ramiro Gálvez'),
 ('AER-EMB-0001', 'AEROPUERTO', 'ZL-037', 15000000, 2234, 'XB-ZKA', 'Cap. Elena Ochoa'),
 ('AER-EMB-0001', 'EMPAQUE', 'ZL-087', 10000000, 4523, 'XB-ZKA', 'Cap. Elena Ochoa')
) AS v(fuente, punto, zona, cant, sup, avioneta, piloto);

\echo 'Datos de ejemplo cargados.'
\endif

-- ---------- 12. Publicación (replicación master-master, parte 1) ----------
-- Cada sede publica sus tablas; el trampeo no entra porque vive en el esquema trampeo.
SELECT 'pub_' || :'sede' AS pub \gset
CREATE ROLE replicador LOGIN REPLICATION PASSWORD :'clave_replicador';
GRANT SELECT ON ALL TABLES IN SCHEMA public TO replicador;  -- para la copia inicial
CREATE PUBLICATION :"pub" FOR TABLES IN SCHEMA public;

\echo 'Sede instalada. Sigue el paso de suscripción (ver encabezado).'
\endif


-- #####################################################################################################
-- PASO 4 y 5 · Suscripción (replicación master-master, parte 2): cada sede se suscribe a la otra.
--   origin = none evita el eco: lo que llega de la otra sede no se le vuelve a mandar.
--   Empaque copia los datos de la planta; la planta se suscribe después sin copiar.
-- #####################################################################################################
\if :es_suscribir

\if :{?clave_replicador}
\else
    \prompt 'Contraseña del usuario replicador de la OTRA sede: ' clave_replicador
\endif

SELECT CASE :'sede' WHEN 'empaque' THEN 'sub_desde_planta' ELSE 'sub_desde_empaque' END AS nombre_sub,
       CASE :'sede' WHEN 'empaque' THEN 'pub_planta' ELSE 'pub_empaque' END AS pub_otra,
       CASE :'sede' WHEN 'empaque' THEN 'true' ELSE 'false' END AS copiar,
       format('host=%s port=%s dbname=%s user=replicador password=%s',
              CASE :'sede' WHEN 'empaque' THEN :'ip_planta' ELSE :'ip_empaque' END, :'puerto_otra',
              current_database(), :'clave_replicador') AS conexion \gset

CREATE SUBSCRIPTION :"nombre_sub" CONNECTION :'conexion' PUBLICATION :"pub_otra"
    WITH (origin = none, copy_data = :copiar);

\echo 'Suscripción creada. Revisa su estado con: SELECT * FROM pg_stat_subscription;'
\endif
