#!/bin/bash
# FDW al trampeo y suscripciones cruzadas. Se corre en cada "docker compose up"; si ya está hecho, no toca nada.
set -euo pipefail
export PGUSER=postgres PGPASSWORD=postgres PGDATABASE=moscamed
sql() { psql -v ON_ERROR_STOP=1 -h "$1" "${@:2}"; }
hay() { [ "$(psql -h "$1" -tAc "$2")" = 1 ]; }

for nodo in planta empaque; do
    hay "$nodo" "SELECT count(*) FROM pg_foreign_server WHERE srvname = 'srv_trampeo'" \
        || sql "$nodo" -v host=trampeo -v clave=lector -f /sql/fdw.sql
done

if ! hay empaque "SELECT count(*) FROM pg_subscription"; then
    sql empaque -v nombre=sub_desde_planta -v pub=pub_planta -v copiar=true \
        -v conexion='host=planta dbname=moscamed user=replicador password=replicador' -f /sql/suscripcion.sql
fi
# La planta se suscribe hasta que empaque terminó la copia inicial; si no, la copia volvería a la planta.
until [ "$(psql -h empaque -tAc "SELECT count(*) FROM pg_subscription_rel WHERE srsubstate <> 'r'")" = 0 ]; do
    sleep 1
done
if ! hay planta "SELECT count(*) FROM pg_subscription"; then
    sql planta -v nombre=sub_desde_empaque -v pub=pub_empaque -v copiar=false \
        -v conexion='host=empaque dbname=moscamed user=replicador password=replicador' -f /sql/suscripcion.sql
fi
echo "Replicación master-master y FDW listos."
