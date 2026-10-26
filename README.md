# Sistema Moscamed

Aplicación de escritorio (Java + JavaFX + PostgreSQL) para el control de producción, traslado, recepción y dispersión de moscas estériles.

## Cómo se mueven las moscas

```
Planta Metapa (PES)                    Aeropuerto de Tapachula (PEA)
  lote → control de calidad              embarque aéreo de Perú
        │                                  │                 │
        │ traslado terrestre               │ se queda en     │ va al centro
        │ (solo Metapa → Tapachula)        │ el aeropuerto   │ de empaque
        ▼                                  ▼                 ▼
Centro de empaque Tapachula (PEM)  ──►  Dispersión AÉREA (PED)
  registra la llegada del envío          la encargada captura el informe
                                         que entrega la avioneta
```

- Por tierra **no se dispersa**: la camioneta solo lleva las pupas de Metapa al centro de empaque de Tapachula.
- La dispersión es **solo aérea**. La encargada de dispersión registra cada informe de vuelo: zona, moscas, superficie, altitud, avioneta y piloto.
- Pilotos y avionetas no son usuarios ni vehículos del sistema: vienen como datos del informe.

## Perfiles

| Perfil | Quién | Servidor | Escribe |
|---|---|---|---|
| PES | Esterilizador · planta Metapa | planta | lotes y su control de calidad |
| PEM | Empacador · centro de empaque Tapachula | empaque | traslados Metapa → Tapachula, su llegada y la calidad al recibir |
| PEA | Aeropuerto de Tapachula | empaque | embarques aéreos de Perú (cuánto se queda y cuánto va al centro) |
| PED | Encargada de dispersión | empaque | informes de dispersión aérea |
| Admin | Programa 2 | cualquiera | usuarios, perfiles y contraseñas |

Cada persona entra con **su propio usuario de PostgreSQL** y la base de datos hace cumplir los permisos con sus roles (`rol_produccion`, `rol_empaque`, `rol_aeropuerto`, `rol_dispersion`, `rol_admin`). Las reglas de negocio (cantidades, fechas, calidad aprobada, existencias) están en triggers de `moscamed.sql`.

## Carpetas

| Carpeta | Contenido |
|---|---|
| [`moscamed-javafx/`](moscamed-javafx) | La aplicación: Programa 1 (operativo) y Programa 2 (administrador) |
| [`moscamed.sql`](moscamed.sql) | Toda la base en un archivo: esquema, roles, trampeo (FDW), datos de ejemplo y replicación; los pasos van en su encabezado |
| [`bd/`](bd) | `instalar.sh`: crea la base con `moscamed.sql` en el PostgreSQL 18 de Windows (dos servidores locales, sin Docker) |
| [`pgpool/`](pgpool) | Pgpool-II de cada sede para los servidores Linux: `sudo bash pgpool/instalar.sh planta` (o `empaque`) |

## Servidores Linux (producción)

En cada servidor, en este orden (los comandos exactos y lo que va en `postgresql.conf` y `pg_hba.conf` están en el encabezado de `moscamed.sql`):

1. Trampeo: base `trampeo` con `-v paso=trampeo`.
2. y 3. Planta y empaque: base `moscamed` con `-v paso=instalar -v sede=planta` / `sede=empaque` (esquema, roles, FDW al trampeo y publicación).
4. y 5. Replicación master-master: `-v paso=suscribir`, primero en empaque y, cuando termine la copia, en la planta.
6. Pgpool-II: `sudo bash pgpool/instalar.sh planta` en la planta y `... empaque` en empaque. El programa se conecta al puerto 9999 de su sede; si se cae ese PostgreSQL, Pgpool atiende con el de la otra sede. Cuando regrese: `sudo systemctl restart pgpool2`.
| [`legado-flask/`](legado-flask) | Prototipo web anterior; ya no se mantiene |

## Arranque rápido

```powershell
bash bd/instalar.sh              # la primera vez instala; después solo enciende los servidores (también tras reiniciar)

$env:PG_HOST_PLANTA="127.0.0.1";  $env:PG_PORT_PLANTA="5452"    # planta (con la base trampeo)
$env:PG_HOST_EMPAQUE="127.0.0.1"; $env:PG_PORT_EMPAQUE="5453"   # empaque
cd moscamed-javafx
mvn javafx:run                                  # inicio de sesión: Operativo o Administrador
mvn javafx:run -Dapp.main=moscamed.Programa2    # Programa 2
```

En Linux es igual, cambiando `$env:VAR="valor"` por `export VAR=valor`. Para instalar en las computadoras de las sedes (Windows o Linux) ver [`moscamed-javafx/README.md`](moscamed-javafx/README.md#instalar-en-windows-y-linux). Al entrar, cada persona solo escribe su usuario y contraseña: el sistema sabe su perfil y su sede.

Los datos de la base quedan en `C:\Users\victus\moscamed-pg` (fuera de OneDrive). Usuarios de ejemplo, todos con contraseña `moscamed`: `usr_planta` (PES), `usr_empaque` (PEM), `usr_aeropuerto` (PEA), `usr_dispersion` (PED) y `usr_admin`.
