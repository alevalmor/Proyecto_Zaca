#!/bin/bash
# Instala Pgpool-II en el servidor Linux (Debian/Ubuntu) de una sede, después de los pasos 1 a 5 de moscamed.sql.
#   sudo bash pgpool/instalar.sh planta      (en 192.168.10.10)
#   sudo bash pgpool/instalar.sh empaque     (en 192.168.20.10)
# Variables opcionales: IP_PLANTA, IP_EMPAQUE, PUERTO_PG (5432) y CLAVE_PGPOOL (si no, la pregunta).
# Al terminar, el programa se conecta a esta máquina en el puerto 9999 (PG_HOST_xxx / PG_PORT_xxx=9999).
#
# En modo raw, un nodo que se cayó no regresa solo: cuando vuelva su PostgreSQL,
#   sudo systemctl restart pgpool2
# (arranca con -D: olvida qué nodo estaba caído y regresa al de su sede).
set -euo pipefail
sede=${1:-}
[ "$sede" = planta ] || [ "$sede" = empaque ] || { echo "Uso: sudo bash $0 planta|empaque"; exit 1; }
[ "$(id -u)" = 0 ] || { echo "Córrelo con sudo."; exit 1; }
IP_PLANTA=${IP_PLANTA:-192.168.10.10}
IP_EMPAQUE=${IP_EMPAQUE:-192.168.20.10}
PUERTO_PG=${PUERTO_PG:-5432}
if [ -z "${CLAVE_PGPOOL:-}" ]; then
    read -rsp "Contraseña del usuario pgpool (la de moscamed.sql): " CLAVE_PGPOOL; echo
fi
# Nodo 0 = esta sede, nodo 1 = la otra (failover).
if [ "$sede" = planta ]; then nodo0=$IP_PLANTA nodo1=$IP_EMPAQUE; else nodo0=$IP_EMPAQUE nodo1=$IP_PLANTA; fi

command -v pgpool > /dev/null && command -v pg_isready > /dev/null \
    || { apt-get update -qq && DEBIAN_FRONTEND=noninteractive apt-get install -y -qq pgpool2 postgresql-client; }

conf=/etc/pgpool2/pgpool.conf
[ -f "$conf.original" ] || cp "$conf" "$conf.original"
clave=$(printf '%s' "$CLAVE_PGPOOL" | sed -e "s/'/''/g" -e 's/[\\&|]/\\&/g')  # comilla de pgpool.conf y caracteres de sed
sed -e "s|@NODO0@|$nodo0|" -e "s|@NODO1@|$nodo1|" -e "s|@PUERTO@|$PUERTO_PG|" -e "s|@CLAVE@|$clave|" \
    "$(dirname "$0")/pgpool.conf" > "$conf"
chown postgres:postgres "$conf"
chmod 600 "$conf"  # lleva la contraseña de pgpool

# -D en el servicio: al reiniciar Pgpool vuelve a usar los dos nodos.
mkdir -p /etc/systemd/system/pgpool2.service.d
printf '[Service]\nExecStart=\nExecStart=/usr/sbin/pgpool -n -D\n' > /etc/systemd/system/pgpool2.service.d/moscamed.conf
systemctl daemon-reload
systemctl enable pgpool2 > /dev/null 2>&1
systemctl restart pgpool2

for _ in $(seq 20); do pg_isready -q -h 127.0.0.1 -p 9999 && break; sleep 1; done
pg_isready -q -h 127.0.0.1 -p 9999 || { echo "Pgpool no arrancó: journalctl -u pgpool2"; exit 1; }
echo "Pgpool de $sede listo en el puerto 9999: nodo 0 = $nodo0:$PUERTO_PG, nodo 1 (failover) = $nodo1:$PUERTO_PG."
echo "Revisa los nodos con: psql -h 127.0.0.1 -p 9999 -U usr_admin -d moscamed -c 'SHOW pool_nodes'"
