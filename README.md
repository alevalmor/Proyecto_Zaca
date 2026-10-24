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

Cada persona entra con **su propio usuario de PostgreSQL** y la base de datos hace cumplir los permisos con sus roles (`rol_produccion`, `rol_empaque`, `rol_aeropuerto`, `rol_dispersion`, `rol_admin`). Las reglas de negocio (cantidades, fechas, calidad aprobada, existencias) están en triggers de `sql/esquema.sql`.

## Carpetas

| Carpeta | Contenido |
|---|---|
| [`moscamed-javafx/`](moscamed-javafx) | La aplicación: Programa 1 (operativo) y Programa 2 (administrador) |
| [`sql/`](sql) | Esquema, roles, replicación, FDW y datos de ejemplo |
| [`infra/`](infra) | Docker: dos PostgreSQL master-master, Pgpool-II, base de trampeo y prueba de punta a punta |
| [`legado-flask/`](legado-flask) | Prototipo web anterior; ya no se mantiene |

## Arranque rápido

```powershell
cd infra
docker compose down -v           # solo si ya tenías la base con el esquema anterior: borra los datos de prueba
docker compose up -d --build
bash probar.sh                   # 49 pruebas: Pgpool, replicación, FDW, reglas, integridad y failover

$env:PG_HOST_PLANTA="127.0.0.1";  $env:PG_PORT_PLANTA="9991"
$env:PG_HOST_EMPAQUE="127.0.0.1"; $env:PG_PORT_EMPAQUE="9992"
cd ..\moscamed-javafx
mvn javafx:run                                  # Programa 1
mvn javafx:run -Dapp.main=moscamed.Programa2    # Programa 2
```

En Linux es igual, cambiando `$env:VAR="valor"` por `export VAR=valor`. Para instalar en las computadoras de las sedes (Windows o Linux) ver [`moscamed-javafx/README.md`](moscamed-javafx/README.md#instalar-en-windows-y-linux). Al entrar, cada persona solo escribe su usuario y contraseña: el sistema sabe su perfil y su sede.

Usuarios de ejemplo, todos con contraseña `contrasena`: `usr_planta` (PES), `usr_empaque` (PEM), `usr_aeropuerto` (PEA), `usr_dispersion` (PED) y `usr_admin`.
