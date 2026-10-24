#!/bin/bash
# Lo corre la imagen de postgres la primera vez que arranca cada nodo (base vacía).
set -euo pipefail
sql() { psql -v ON_ERROR_STOP=1 -U postgres -d moscamed "$@"; }

sql -v inicio="$INICIO" -f /sql/esquema.sql
sql -f /sql/roles.sql
sql -v pub="pub_$SEDE" -v clave=replicador -f /sql/replicacion.sql
# Usuario sin permisos con el que Pgpool-II revisa que el nodo responda.
sql -c "CREATE ROLE pgpool LOGIN PASSWORD 'pgpool'"
# Los datos de ejemplo van en una sola sede; la suscripción los copia a la otra.
if [ "$SEDE" = planta ]; then sql -f /sql/datos_ejemplo.sql; fi
