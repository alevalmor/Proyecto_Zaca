#!/bin/bash
# Base Moscamed en el PostgreSQL de Linux (paquetes de Ubuntu), una sede por computadora, sin Docker ni sudo.
#   planta  -> puerto 5452, con la base trampeo y la administración (usr_admin, catálogos, datos de ejemplo)
#   empaque -> puerto 5453
# Orden (la IP es la de la OTRA computadora):
#   1. Planta:   bash bd/instalar_linux.sh planta
#   2. Empaque:  bash bd/instalar_linux.sh empaque <ip_planta>    instala, lee el trampeo de la planta y copia sus datos
#   3. Planta:   bash bd/instalar_linux.sh planta <ip_empaque>    se suscribe al empaque (cierra el master-master)
# Los servidores corren con tu usuario y no tocan los clusters del sistema (5432, 5433).
# Se puede volver a correr: lo ya instalado no se repite y el servidor apagado se enciende.
# Contraseñas de desarrollo: postgres=postgres, lector=lector, replicador=replicador, pgpool=pgpool,
# usr_admin y usuarios de ejemplo=moscamed.
set -euo pipefail
sede=${1:-}; ip_otra=${2:-}
[[ $sede = planta || $sede = empaque ]] || { echo "Uso: bash bd/instalar_linux.sh planta|empaque [ip_de_la_otra_sede]"; exit 1; }
[[ $sede = empaque && -z $ip_otra ]] && { echo "El empaque necesita la IP de la planta (trampeo y datos)"; exit 1; }

BIN=${PG_BIN:-$(ls -d /usr/lib/postgresql/*/bin | sort -V | tail -1)}
DATOS=${MOSCAMED_DATOS:-"$HOME/moscamed-pg"}
SQL=$(cd "$(dirname "$0")/.." && pwd)/moscamed.sql
export PGUSER=postgres PGPASSWORD=postgres PGHOST=localhost PGCLIENTENCODING=UTF8
declare -A PUERTO=([planta]=5452 [empaque]=5453)
p=${PUERTO[$sede]}
dir="$DATOS/$sede"

if [ ! -f "$dir/PG_VERSION" ]; then
    mkdir -p "$dir"
    printf 'postgres' > "$DATOS/clave.tmp"
    "$BIN/initdb" -D "$dir" -U postgres --pwfile="$DATOS/clave.tmp" -A scram-sha-256 -E UTF8 \
        --locale=C.UTF-8 > /dev/null
    rm "$DATOS/clave.tmp"
    # El socket va en la carpeta de datos: /var/run/postgresql es del usuario postgres.
    printf "\nport = %s\nlisten_addresses = '*'\nwal_level = logical\nunix_socket_directories = '%s'\n" \
        "$p" "$dir" >> "$dir/postgresql.conf"
    # La otra sede (replicador), los programas, Pgpool y el FDW del empaque entran desde la red local.
    cat >> "$dir/pg_hba.conf" <<'EOF'
host  moscamed  all       samenet  scram-sha-256
host  trampeo   lector    samenet  scram-sha-256
host  postgres  pgpool    samenet  scram-sha-256
EOF
fi
if ! "$BIN/pg_ctl" status -D "$dir" > /dev/null 2>&1; then
    "$BIN/pg_ctl" start -D "$dir" -l "$DATOS/$sede.log" -w > /dev/null \
        || { echo "No arrancó $sede: revisa $DATOS/$sede.log"; exit 1; }
fi

hay() { [ "$("$BIN/psql" -X -p "$p" -d "$1" -tAc "$2")" = 1 ]; }
sql() { "$BIN/psql" -X -q -v ON_ERROR_STOP=1 -p "$p" -d "$1" -f "$SQL" "${@:2}"; }
claves=(-v clave_lector=lector -v clave_replicador=replicador -v clave_usuarios=moscamed -v clave_pgpool=pgpool)

if [ "$sede" = planta ]; then
    ips=(-v ip_planta=localhost -v ip_empaque="${ip_otra:-localhost}" -v ip_trampeo=localhost -v puerto_trampeo="$p")
    hay postgres "SELECT count(*) FROM pg_database WHERE datname = 'trampeo'" || "$BIN/createdb" -p "$p" trampeo
    hay trampeo "SELECT count(*) FROM pg_tables WHERE tablename = 'revision'" \
        || sql trampeo -v paso=trampeo "${claves[@]}"
else
    ips=(-v ip_planta="$ip_otra" -v ip_empaque=localhost -v ip_trampeo="$ip_otra" -v puerto_trampeo="${PUERTO[planta]}")
fi

hay postgres "SELECT count(*) FROM pg_database WHERE datname = 'moscamed'" || "$BIN/createdb" -p "$p" moscamed
hay moscamed "SELECT count(*) FROM pg_proc WHERE proname = 'sede_local'" \
    || sql moscamed -v paso=instalar -v sede="$sede" "${ips[@]}" "${claves[@]}"

if [ -n "$ip_otra" ]; then
    otra=$([ "$sede" = planta ] && echo empaque || echo planta)
    hay moscamed "SELECT count(*) FROM pg_subscription" \
        || sql moscamed -v paso=suscribir -v sede="$sede" -v puerto_otra="${PUERTO[$otra]}" "${ips[@]}" "${claves[@]}"
    echo "Suscrita a $otra en $ip_otra:${PUERTO[$otra]}."
else
    echo "Falta la replicación: cuando el empaque esté instalado, corre: bash bd/instalar_linux.sh planta <ip_empaque>"
fi
echo "Moscamed $sede listo en el puerto $p de $(hostname -I | cut -d' ' -f1). Datos en $dir"
