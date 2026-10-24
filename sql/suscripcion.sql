-- Replicación master-master, parte 2: cada sede se suscribe a la publicación de la otra.
-- origin = none evita el eco: lo que llega de la otra sede no se le vuelve a mandar.
-- Primero la sede vacía copia los datos (copiar=true); cuando termine, la otra se suscribe sin copiar:
--   empaque: psql -d moscamed -v nombre=sub_desde_planta  -v pub=pub_planta  -v copiar=true \
--                 -v conexion='host=192.168.10.10 dbname=moscamed user=replicador password=...' -f suscripcion.sql
--   planta:  psql -d moscamed -v nombre=sub_desde_empaque -v pub=pub_empaque -v copiar=false \
--                 -v conexion='host=192.168.20.10 dbname=moscamed user=replicador password=...' -f suscripcion.sql

CREATE SUBSCRIPTION :nombre CONNECTION :'conexion' PUBLICATION :pub WITH (origin = none, copy_data = :copiar);
