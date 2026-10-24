#!/bin/bash
# Prueba de punta a punta contra "docker compose up": Pgpool, replicación en los dos sentidos, FDW,
# reglas de negocio en la base y failover de Pgpool. Uso (Linux, o Git Bash en Windows): bash infra/probar.sh
set -uo pipefail
cd "$(dirname "$0")"
# psql del PATH (Linux) o el instalador de PostgreSQL 18 (Windows, Git Bash)
PSQL="${PSQL:-$(command -v psql || echo "/c/Program Files/PostgreSQL/18/bin/psql.exe")}"
export PGDATABASE=moscamed PGCLIENTENCODING=UTF8
fallas=0
ok()  { echo "  ok   $1"; }
mal() { echo "  MAL  $1"; fallas=$((fallas + 1)); }
# q <puerto> <usuario> <sql>: una sola respuesta, sin adornos
q() { PGPASSWORD="$([ "$2" = postgres ] && echo postgres || echo contrasena)" "$PSQL" -h 127.0.0.1 -p "$1" -U "$2" -tAX -v ON_ERROR_STOP=1 -c "$3" 2>&1; }
# espera <puerto> <sql>: hasta 15 s a que la consulta (como postgres) dé 1
espera() { for _ in $(seq 30); do [ "$(q "$1" postgres "$2")" = 1 ] && return 0; sleep 0.5; done; return 1; }
# rechaza <puerto> <usuario> <sql> <texto esperado en el error>
rechaza() { local r; r="$(q "$1" "$2" "$3")"; if [[ "$r" == *"$4"* ]]; then ok "rechaza: $4"; else mal "esperaba '$4', salió: $r"; fi; }
P=9991 E=9992 DP=5433 DE=5434   # Pgpool planta / empaque, nodos directos
s=$(date +%H%M%S)

echo "Pgpool-II"
[[ "$(q $P usr_planta 'SHOW pool_nodes')" == *"|planta|"*"|up|"* ]] && ok "planta entra por Pgpool y su nodo 0 está arriba" || mal "SHOW pool_nodes en 9991"
[[ "$(q $E usr_empaque 'SHOW pool_nodes')" == *"|empaque|"*"|up|"* ]] && ok "empaque entra por Pgpool y su nodo 0 está arriba" || mal "SHOW pool_nodes en 9992"

echo "FDW"
[ "$(q $E usr_empaque 'SELECT count(DISTINCT trampa) FROM trampeo.revision')" = 8 ] && ok "empaque lee 8 trampas de la base de trampeo" || mal "FDW en empaque"
[ "$(q $P usr_planta 'SELECT count(DISTINCT trampa) FROM trampeo.revision')" = 8 ] && ok "planta lee 8 trampas de la base de trampeo" || mal "FDW en planta"

echo "Replicación master-master"
for t in responsable lote_produccion control_calidad envio embarque_aereo liberacion; do
    a=$(q $DP postgres "SELECT count(*) FROM $t"); b=$(q $DE postgres "SELECT count(*) FROM $t")
    [ "$a" = "$b" ] && ok "$t igual en las dos sedes ($a)" || mal "$t: planta $a, empaque $b"
done
q $P usr_planta "INSERT INTO lote_produccion (codigo, fecha_produccion, fecha_irradiacion, dosis_gy, cantidad_pupas, cepa, tinte, centro_empaque_id, responsable_id)
  SELECT 'PRB-$s', current_date, current_date, 120, 1000, 'Tapachula-7 TSL', 'Rosa', (SELECT min(id) FROM centro_empaque), (SELECT id FROM responsable WHERE usuario_bd = 'usr_planta')" >/dev/null
q $P usr_planta "INSERT INTO control_calidad (lote_id, etapa, emergencia, vuelo, esterilidad, peso_mg, responsable_id)
  SELECT id, 'PLANTA', 90, 80, 99.9, 7.8, responsable_id FROM lote_produccion WHERE codigo = 'PRB-$s'" >/dev/null
espera $DE "SELECT count(*) FROM control_calidad c JOIN lote_produccion l ON l.id = c.lote_id WHERE l.codigo = 'PRB-$s'" \
    && ok "lote y calidad escritos en planta llegan a empaque" || mal "planta -> empaque"
q $E usr_empaque "INSERT INTO envio (codigo, lote_id, vehiculo_id, fecha_salida, cantidad, temperatura, responsable_id)
  SELECT 'PRB-ENV-$s', l.id, (SELECT id FROM vehiculo WHERE matricula = 'DA-583-E'), now() - interval '1 hour', 600, 3.0,
         (SELECT id FROM responsable WHERE usuario_bd = 'usr_empaque') FROM lote_produccion l WHERE codigo = 'PRB-$s'" >/dev/null
espera $DP "SELECT count(*) FROM envio WHERE codigo = 'PRB-ENV-$s'" && ok "envío escrito en empaque llega a planta" || mal "empaque -> planta"
[ "$(q $DP postgres "SELECT id % 2 FROM envio WHERE codigo = 'PRB-ENV-$s'")" = 0 ] && ok "el envío trae id par (empaque)" || mal "id del envío"

echo "Reglas de negocio en la base"
lote="(SELECT id FROM lote_produccion WHERE codigo = 'PRB-$s')"
emp="(SELECT id FROM responsable WHERE usuario_bd = 'usr_empaque')"
envio_sql() { echo "INSERT INTO envio (codigo, lote_id, vehiculo_id, fecha_salida, cantidad, temperatura, responsable_id) VALUES ('X-$s', $1, (SELECT id FROM vehiculo WHERE matricula = '$2'), now(), $3, 3.0, $emp)"; }
rechaza $E usr_empaque "$(envio_sql "$lote" DA-583-E 500)" "Supera lo que queda del lote"
rechaza $E usr_empaque "$(envio_sql "(SELECT id FROM lote_produccion WHERE codigo = 'MET-2026-0077')" DA-583-E 10)" "no está aprobado"
rechaza $E usr_empaque "$(envio_sql "$lote" DA-583-E 40000000)" "No cabe en el vehículo"
rechaza $P usr_planta "$(envio_sql "$lote" DA-583-E 10)" "permission denied"
rechaza $P usr_planta "INSERT INTO control_calidad (lote_id, etapa, emergencia, vuelo, esterilidad, peso_mg, responsable_id) SELECT $lote, 'EMPAQUE', 90, 80, 99.9, 7.8, 1" "Solo el centro de empaque"
rechaza $P usr_planta "UPDATE lote_produccion SET cantidad_pupas = 100 WHERE codigo = 'PRB-$s'" "no puede quedar con menos"
dis="(SELECT id FROM responsable WHERE usuario_bd = 'usr_dispersion')"
# lib <fecha> <cantidad> <envío> [altitud]: informe de dispersión aérea con moscas de un envío
lib() { echo "INSERT INTO liberacion (envio_id, punto, zona_id, fecha, cantidad, superficie_ha, altitud_m, avioneta, piloto, responsable_id) SELECT id, 'EMPAQUE', (SELECT min(id) FROM zona_liberacion), $1, $2, 10, ${4:-2300}, 'XB-TWE', 'Prueba', $dis FROM envio WHERE codigo = '$3'"; }
rechaza $E usr_dispersion "$(lib "now()" 10 "PRB-ENV-$s")" "aún no llega al centro de empaque"
rechaza $E usr_empaque "$(lib "now()" 10 "PRB-ENV-$s")" "permission denied"
q $E usr_empaque "UPDATE envio SET recibido = now() - interval '30 minutes' WHERE codigo = 'PRB-ENV-$s'" >/dev/null
rechaza $E usr_empaque "UPDATE envio SET cantidad = 1 WHERE codigo = 'PRB-ENV-$s'" "ya llegó a Tapachula"
rechaza $E usr_dispersion "$(lib "now()" 700 "PRB-ENV-$s")" "más moscas de las que hay"
rechaza $E usr_dispersion "$(lib "now() - interval '1 day'" 10 "PRB-ENV-$s")" "antes de que llegaran"
rechaza $E usr_dispersion "$(lib "now()" 10 "PRB-ENV-$s" NULL)" "altitud_m"
q $E usr_dispersion "$(lib "now()" 600 "PRB-ENV-$s")" >/dev/null
[ "$(q $E usr_dispersion "SELECT estado FROM v_envio WHERE codigo = 'PRB-ENV-$s'")" = DISPERSADO ] && ok "el envío queda DISPERSADO" || mal "estado del envío"
rechaza $E usr_empaque "UPDATE envio SET cancelado = true WHERE codigo = 'PRB-ENV-$s'" "ya llegó a Tapachula"

echo "Aeropuerto de Tapachula (embarques de Perú)"
emb() { echo "INSERT INTO embarque_aereo (codigo, guia_aerea, fecha_llegada, cantidad, queda_aeropuerto, responsable_id) VALUES ('$1', 'G-$s', now() - interval '1 hour', 1000, $2, (SELECT id FROM responsable WHERE usuario_bd = 'usr_aeropuerto'))"; }
rechaza $E usr_aeropuerto "$(emb "PRB-E2-$s" 1001)" "embarque_aereo_check"
rechaza $E usr_empaque "$(emb "PRB-E3-$s" 0)" "permission denied"
q $E usr_aeropuerto "$(emb "PRB-EMB-$s" 300)" >/dev/null
libe() { echo "INSERT INTO liberacion (embarque_id, punto, zona_id, fecha, cantidad, superficie_ha, altitud_m, avioneta, piloto, responsable_id) SELECT id, '$1', (SELECT min(id) FROM zona_liberacion), now(), $2, 10, 2300, 'XB-ZKA', 'Prueba', $dis FROM embarque_aereo WHERE codigo = 'PRB-EMB-$s'"; }
rechaza $E usr_dispersion "$(libe AEROPUERTO 301)" "quedan 300"
rechaza $E usr_dispersion "$(libe EMPAQUE 701)" "quedan 700"
q $E usr_dispersion "$(libe AEROPUERTO 300)" >/dev/null
[ "$(q $E usr_dispersion "SELECT sum(quedan) FROM v_existencia WHERE codigo = 'PRB-EMB-$s'")" = 700 ] && ok "existencias del embarque: 0 en aeropuerto, 700 en el centro" || mal "v_existencia del embarque"
espera $DP "SELECT count(*) FROM liberacion l JOIN embarque_aereo b ON b.id = l.embarque_id WHERE b.codigo = 'PRB-EMB-$s'" \
    && ok "embarque y dispersión escritos en Tapachula llegan a la planta" || mal "empaque -> planta (embarques)"

echo "Integridad: códigos, responsable, permisos por columna, historial, procedimientos"
rechaza $P usr_admin "INSERT INTO lote_produccion (fecha_produccion, fecha_irradiacion, dosis_gy, cantidad_pupas, cepa, tinte, centro_empaque_id, responsable_id) SELECT current_date, current_date, 120, 10, 'x', 'x', min(id), 1 FROM centro_empaque" "permission denied"
pla="(SELECT id FROM responsable WHERE usuario_bd = 'usr_planta')"
nuevo=$(q $E usr_empaque "INSERT INTO envio (lote_id, vehiculo_id, fecha_salida, cantidad, temperatura, responsable_id) SELECT $lote, (SELECT id FROM vehiculo WHERE matricula = 'DA-583-E'), now(), 10, 3.0, $pla RETURNING codigo" | head -1)
[[ "$nuevo" =~ ^TAP-ENV-[0-9]{4}$ ]] && ok "la base pone el código del envío ($nuevo)" || mal "código del envío: $nuevo"
[ "$(q $E postgres "SELECT r.usuario_bd FROM envio e JOIN responsable r ON r.id = e.responsable_id WHERE e.codigo = '$nuevo'")" = usr_empaque ] \
    && ok "el responsable es quien está en sesión aunque mande otro" || mal "responsable falsificable"
rechaza $E usr_empaque "UPDATE envio SET codigo = 'X' WHERE codigo = '$nuevo'" "permission denied"
rechaza $E usr_empaque "CALL recibir_envio('NO-EXISTE')" "No existe el envío"
q $E usr_empaque "CALL cancelar_envio('$nuevo')" >/dev/null
[ "$(q $E postgres "SELECT cancelado AND modificado > creado FROM envio WHERE codigo = '$nuevo'")" = t ] && ok "CALL cancelar_envio cancela y marca modificado" || mal "cancelar_envio"
[ "$(q $E postgres "SELECT count(*) FROM historial h JOIN envio e ON e.id = h.registro_id WHERE h.tabla = 'envio' AND e.codigo = '$nuevo' AND h.usuario = 'usr_empaque'")" = 2 ] \
    && ok "historial: alta y cancelación con el usuario real" || mal "historial del envío"
[ "$(q $E postgres "SELECT count(*) FROM auditoria WHERE accion LIKE 'Cancel% $nuevo'")" = 1 ] && ok "el procedimiento deja la bitácora" || mal "bitácora del procedimiento"
rechaza $P usr_planta "SELECT count(*) FROM historial" "permission denied"
rechaza $E usr_empaque "INSERT INTO envio (lote_id, vehiculo_id, fecha_salida, cantidad, temperatura, responsable_id) SELECT $lote, (SELECT id FROM vehiculo WHERE matricula = 'DA-583-E'), now() - interval '3 days', 10, 3.0, 1" "antes de irradiar"
rechaza $E usr_dispersion "$(libe EMPAQUE 1 | sed "s/now()/now() + interval '1 day'/")" "no puede ser futura"
rechaza $E usr_dispersion "$(libe EMPAQUE 1 | sed 's/, 10, 2300/, 999999, 2300/')" "no se pueden cubrir"
[ "$(q $E usr_dispersion "SELECT count(*) || ' ' || (array_agg(prioridad))[1] FROM zonas_prioritarias(current_date, 14)")" = "8 ALTA" ] \
    && ok "zonas_prioritarias: 8 zonas, primero las de prioridad alta" || mal "zonas_prioritarias"

echo "Failover de Pgpool (se apaga el nodo de la planta)"
docker compose stop -t 5 planta >/dev/null 2>&1
r=""; for _ in $(seq 20); do r="$(q $P usr_planta "SELECT inet_server_addr() IS NOT NULL AND (SELECT count(*) FROM pg_subscription WHERE subname = 'sub_desde_planta') = 1")"; [ "$r" = t ] && break; sleep 1; done
[ "$r" = t ] && ok "con la planta caída, el Pgpool de la planta atiende con el nodo de empaque" || mal "failover: $r"
docker compose start planta >/dev/null 2>&1
docker compose restart pgpool-planta >/dev/null 2>&1
espera $DP "SELECT count(*) FROM pg_stat_subscription WHERE subname = 'sub_desde_empaque' AND pid IS NOT NULL" \
    && ok "la planta regresa y su suscripción vuelve a correr" || mal "la suscripción de la planta no volvió"
for _ in $(seq 20); do [[ "$(q $P usr_planta 'SHOW pool_nodes')" == *"|planta|"*"|up|"* ]] && break; sleep 1; done
[[ "$(q $P usr_planta 'SHOW pool_nodes')" == *"|planta|"*"|up|"* ]] && ok "Pgpool de la planta vuelve a su nodo" || mal "Pgpool no regresó al nodo de la planta"

echo
[ $fallas = 0 ] && echo "Todo bien." || echo "$fallas pruebas fallaron."
exit $fallas
