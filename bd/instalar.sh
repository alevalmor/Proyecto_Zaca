#!/bin/bash
# Base Moscamed en el PostgreSQL 18 instalado en Windows, sin Docker (correr con Git Bash: bash bd/instalar.sh).
# Levanta dos servidores locales con ../moscamed.sql y sus 5 pasos:
#   planta  -> localhost:5452 (también tiene la base trampeo)   empaque -> localhost:5453
# Son dos servidores porque los roles no se replican y el Programa 2 crea cada usuario en los dos.
# Se puede volver a correr: lo ya instalado no se repite y los servidores apagados se encienden.
# Contraseñas de desarrollo: postgres=postgres, lector=lector, replicador=replicador, pgpool=pgpool,
# usr_admin y usuarios de ejemplo=moscamed. Pgpool-II no tiene versión para Windows: en Linux, pgpool/instalar.sh.
set -euo pipefail
BIN=${PG_BIN:-"/c/Program Files/PostgreSQL/18/bin"}
DATOS=${MOSCAMED_DATOS:-"$HOME/moscamed-pg"}  # fuera de OneDrive: la sincronización corrompe los archivos de la base
SQL=$(cd "$(dirname "$0")/.." && pwd)/moscamed.sql
export PGUSER=postgres PGPASSWORD=postgres PGHOST=localhost PGCLIENTENCODING=UTF8
declare -A PUERTO=([planta]=5452 [empaque]=5453)

for sede in planta empaque; do
    dir="$DATOS/$sede"
    if [ ! -f "$dir/PG_VERSION" ]; then
        mkdir -p "$dir"
        printf 'postgres' > "$DATOS/clave.tmp"
        "$BIN/initdb" -D "$dir" -U postgres --pwfile="$DATOS/clave.tmp" -A scram-sha-256 -E UTF8 \
            --locale=C --locale-provider=builtin --builtin-locale=C.UTF-8 > /dev/null
        rm "$DATOS/clave.tmp"
        printf "\nport = %s\nlisten_addresses = 'localhost'\nwal_level = logical\n" "${PUERTO[$sede]}" >> "$dir/postgresql.conf"
    fi
    if ! "$BIN/pg_ctl" status -D "$dir" > /dev/null 2>&1; then
        # Proceso de Windows aparte: arrancado desde esta consola, el servidor se cae al cerrarla.
        powershell.exe -NoProfile -Command "Start-Process -WindowStyle Hidden -FilePath '$(cygpath -w "$BIN/pg_ctl.exe")' \
            -ArgumentList 'start','-D','\"$(cygpath -w "$dir")\"','-l','\"$(cygpath -w "$DATOS/$sede.log")\"'"
        for _ in $(seq 60); do "$BIN/pg_isready" -q -h localhost -p "${PUERTO[$sede]}" && break; sleep 1; done
        "$BIN/pg_isready" -q -h localhost -p "${PUERTO[$sede]}" || { echo "No arrancó $sede: revisa $DATOS/$sede.log"; exit 1; }
    fi
done

P=${PUERTO[planta]}; E=${PUERTO[empaque]}
hay() { [ "$("$BIN/psql" -X -p "$1" -d "$2" -tAc "$3")" = 1 ]; }
sql() { "$BIN/psql" -X -q -v ON_ERROR_STOP=1 -p "$1" -d "$2" -f "$SQL" "${@:3}"; }
claves=(-v clave_lector=lector -v clave_replicador=replicador -v clave_usuarios=moscamed -v clave_pgpool=pgpool)
ips=(-v ip_planta=localhost -v ip_empaque=localhost -v ip_trampeo=localhost -v puerto_trampeo="$P")

hay "$P" postgres "SELECT count(*) FROM pg_database WHERE datname = 'trampeo'" || "$BIN/createdb" -p "$P" trampeo
hay "$P" trampeo "SELECT count(*) FROM pg_tables WHERE tablename = 'revision'" \
    || sql "$P" trampeo -v paso=trampeo "${claves[@]}"

for sede in planta empaque; do
    p=${PUERTO[$sede]}
    hay "$p" postgres "SELECT count(*) FROM pg_database WHERE datname = 'moscamed'" || "$BIN/createdb" -p "$p" moscamed
    hay "$p" moscamed "SELECT count(*) FROM pg_proc WHERE proname = 'sede_local'" \
        || sql "$p" moscamed -v paso=instalar -v sede="$sede" "${ips[@]}" "${claves[@]}"
done

hay "$E" moscamed "SELECT count(*) FROM pg_subscription" \
    || sql "$E" moscamed -v paso=suscribir -v sede=empaque -v puerto_otra="$P" "${ips[@]}" "${claves[@]}"
# La planta se suscribe hasta que empaque terminó la copia inicial; si no, la copia volvería a la planta.
until [ "$("$BIN/psql" -X -p "$E" -d moscamed -tAc "SELECT count(*) FROM pg_subscription_rel WHERE srsubstate <> 'r'")" = 0 ]; do
    sleep 1
done
hay "$P" moscamed "SELECT count(*) FROM pg_subscription" \
    || sql "$P" moscamed -v paso=suscribir -v sede=planta -v puerto_otra="$E" "${ips[@]}" "${claves[@]}"

echo "Moscamed listo: planta en localhost:$P (con trampeo), empaque en localhost:$E. Datos en $DATOS"
