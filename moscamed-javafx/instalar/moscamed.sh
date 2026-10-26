#!/bin/sh
# Moscamed en Linux. Copia este archivo junto a moscamed.jar y dale permiso: chmod +x moscamed.sh
#   ./moscamed.sh         -> inicio de sesión (elige Operativo o Administrador)
#   ./moscamed.sh admin   -> Programa 2 (administrador)
# Servidores (Pgpool-II de cada sede). Cámbialos si tu red usa otras direcciones.
export PG_HOST_PLANTA="${PG_HOST_PLANTA:-192.168.10.10}"
export PG_PORT_PLANTA="${PG_PORT_PLANTA:-9999}"
export PG_HOST_EMPAQUE="${PG_HOST_EMPAQUE:-192.168.20.10}"
export PG_PORT_EMPAQUE="${PG_PORT_EMPAQUE:-9999}"
exec java -jar "$(dirname "$0")/moscamed.jar" "$@"
