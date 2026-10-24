# Infraestructura Moscamed

Levanta en una sola máquina lo que en producción son tres servidores:

| Servicio | Qué es | Puerto en tu máquina | Producción |
|---|---|---|---|
| `pgpool-planta` | Pgpool-II de la planta | `9991` | `192.168.10.10:9999` |
| `pgpool-empaque` | Pgpool-II del centro de empaque | `9992` | `192.168.20.10:9999` |
| `planta` / `empaque` | PostgreSQL 18, master-master | `5433` / `5434` | detrás de cada Pgpool |
| `trampeo` | Base de trampeo, leída con `postgres_fdw` | `5435` | `192.168.30.10` |

Requiere Docker Desktop. Pgpool-II no tiene versión para Windows, por eso corre en contenedores Linux.

```powershell
cd infra
docker compose up -d --build     # la primera vez crea las bases, el FDW y las suscripciones
bash probar.sh                   # prueba Pgpool, replicación, FDW, reglas y failover (Git Bash)
```

Luego abre la app de escritorio apuntando a los Pgpool:

```powershell
$env:PG_HOST_PLANTA="127.0.0.1";  $env:PG_PORT_PLANTA="9991"
$env:PG_HOST_EMPAQUE="127.0.0.1"; $env:PG_PORT_EMPAQUE="9992"
cd ..\moscamed-javafx; mvn javafx:run
```

`docker compose down -v` borra todo y la siguiente subida empieza de cero.

## Cómo funciona

- **Replicación master-master.** Cada sede publica sus tablas (`replicacion.sql`) y se suscribe a la otra con `origin = none` (`suscripcion.sql`), así lo que llega no se reenvía. Los ids no chocan: la planta usa impares y el empaque pares. Cada tabla la escribe una sola sede (lotes la planta; envíos, embarques y dispersiones Tapachula) y los estados se calculan en vistas, así no hay ediciones cruzadas.
- **Reglas de negocio en la base.** Los triggers de `esquema.sql` revisan calidad aprobada, capacidad de la camioneta, pupas disponibles, llegada a Tapachula antes de dispersar, moscas que quedan de cada envío o embarque, fechas y envíos cancelados o ya recibidos. No corren al aplicar la replicación: cada fila se valida en la sede donde se escribió.
- **Lo que pone la base, no el programa.** Los códigos (`MET-AAAA-NNNN`, `TAP-ENV-NNNN`, `AER-EMB-NNNN`) con candado para que dos personas no saquen el mismo; el responsable de cada registro (quien está en sesión, no se puede firmar por otro); `creado` y `modificado`; y la tabla `historial`, con la fila antes y después de cada cambio, aunque entren por psql. Solo el administrador lee el historial.
- **Permisos por columna.** Al modificar, cada rol solo toca las columnas que le corresponden (nadie cambia el código ni quién registró). Nadie tiene `DELETE` sobre datos de operación. El administrador puede dar los roles de PES, PEM, PEA y PED, pero no hereda sus permisos.
- **Procedimientos y funciones.** `CALL recibir_envio(código, llegada)` y `CALL cancelar_envio(código)` hacen el cambio y su bitácora en una transacción. `zonas_prioritarias(fecha, días)` calcula la prioridad de cada zona con el trampeo. Las vistas `v_lote`, `v_envio`, `v_embarque` y `v_existencia` calculan los estados y respetan los permisos de quien consulta (`security_invoker`).
- **Índices** en cada llave foránea y en las fechas por las que se filtra. `lock_timeout` de 15 s, para que un candado colgado no congele el programa.
- **Pgpool-II en modo raw.** Cada Pgpool manda todo a su nodo (nodo 0). Si se cae, pasa al servidor de la otra sede (nodo 1), que tiene los mismos datos. Cuando el nodo regresa, Pgpool no vuelve a él solo: `docker compose restart pgpool-planta` (en producción `pcp_attach_node`).
- **FDW.** `fdw.sql` importa `trampeo.revision` desde la base de trampeo con un usuario de solo lectura. No se replica: cada sede la lee en vivo.

## En los servidores reales

En cada sede, como superusuario y con `wal_level = logical`:

```bash
createdb moscamed
psql -d moscamed -v inicio=1 -f sql/esquema.sql            # empaque: inicio=2
psql -d moscamed -f sql/roles.sql
psql -d moscamed -v pub=pub_planta -v clave=... -f sql/replicacion.sql    # empaque: pub_empaque
psql -d moscamed -v host=192.168.30.10 -v clave=... -f sql/fdw.sql
```

En la base de trampeo: `psql -d trampeo -f sql/trampeo.sql`. Carga `datos_ejemplo.sql` en una sola sede (opcional) y después crea las suscripciones como indica `suscripcion.sql`: primero el empaque con `copiar=true`, y la planta con `copiar=false` hasta que termine la copia. Usa los mismos `infra/pgpool/*.conf` cambiando los nombres de host por las IP y activando `ssl`.
