# Sistema Moscamed · Java y JavaFX

Aplicación de escritorio con las vistas del diseño "Mockups Sistema Moscamed". Usa Java 21 o más reciente, JavaFX 21 y PostgreSQL por JDBC.

- **Programa 1** (`moscamed.Programa1`): operativo para cuatro perfiles. PES (planta Metapa): lotes y control de calidad. PEM (centro de empaque Tapachula): traslados terrestres Metapa → Tapachula y su llegada. PEA (aeropuerto de Tapachula): embarques aéreos de Perú. PED (encargada de dispersión): informes de dispersión aérea. Todos ven zonas prioritarias y su perfil.
- **Programa 2** (`moscamed.Programa2`): administrador. Crea usuarios de los cuatro perfiles, da de baja, restablece contraseñas y muestra el estado del sistema.

Cada persona entra con **su propio usuario de PostgreSQL**. La base de datos aplica los permisos con `rol_produccion`, `rol_empaque`, `rol_aeropuerto`, `rol_dispersion` y `rol_admin`.

### Contraseñas

Al crear un usuario, el administrador ve la contraseña (generada o escrita por él) con "Ver contraseña". Al guardar aparece una ventana con el usuario y la contraseña y un botón para copiarlos. Lo mismo pasa al restablecer. PostgreSQL guarda las contraseñas cifradas, así que después ya no se pueden consultar: si alguien la pierde, se restablece. Todos los campos de contraseña tienen "Ver contraseña".

## Base de datos

Dos servidores PostgreSQL master-master, Pgpool-II en cada sede y la base de trampeo leída con `postgres_fdw`. Todo se levanta con Docker: ver [`../infra/README.md`](../infra/README.md), que también trae los pasos para los servidores reales.

```bash
cd ../infra && docker compose up -d --build && bash probar.sh
```

Usuarios de ejemplo (contraseña `contrasena`): `usr_planta` (PES), `usr_empaque` (PEM), `usr_aeropuerto` (PEA), `usr_dispersion` (PED) y `usr_admin`.

## Ejecutar

Las variables de entorno apuntan a Pgpool-II de cada sede. Sin ellas se usan los valores del diseño:

| Variable | Por defecto |
|---|---|
| `PG_HOST_PLANTA` / `PG_PORT_PLANTA` | `192.168.10.10` / `9999` |
| `PG_HOST_EMPAQUE` / `PG_PORT_EMPAQUE` | `192.168.20.10` / `9999` |
| `PG_DB` | `moscamed` |

```bash
mvn javafx:run                                  # Programa 1
mvn javafx:run -Dapp.main=moscamed.Programa2    # Programa 2
```

En PowerShell, define las variables antes de ejecutar, por ejemplo: `$env:PG_HOST_PLANTA="127.0.0.1"`. En Linux: `export PG_HOST_PLANTA=127.0.0.1`.

## Iniciar sesión

La persona solo escribe su usuario y contraseña: no elige sede ni perfil. El programa entra al primer servidor que responda, lee su perfil (PES, PEM, PEA o PED) y se cambia al servidor de su sede. Si ese servidor no responde, trabaja en el de la otra sede (igual que el failover de Pgpool) y la replicación lleva los datos de vuelta.

## Instalar en Windows y Linux

```bash
mvn -P paquete package     # genera target/moscamed.jar con JavaFX para Windows y Linux
```

En cada computadora hace falta Java 21 o más reciente (por ejemplo Temurin). Copia `target/moscamed.jar` junto con el lanzador de `instalar/` y ajusta en él las direcciones de los servidores:

| Sistema | Programa 1 | Programa 2 |
|---|---|---|
| Windows | `moscamed.bat` | `moscamed.bat admin` |
| Linux | `./moscamed.sh` (antes `chmod +x moscamed.sh`) | `./moscamed.sh admin` |

En Linux se necesita GTK 3 (`libgtk-3-0`), que ya viene en cualquier escritorio. Se probó en Ubuntu 24.04.

Si Maven no puede descargar por un error de certificado (antivirus o proxy), en Windows usa el almacén de certificados del sistema: `$env:MAVEN_OPTS="-Djavax.net.ssl.trustStoreType=Windows-ROOT"`.

## Pruebas

```bash
mvn test
```

## Estructura

| Archivo | Contenido |
|---|---|
| `Db.java` | Conexión con el usuario de la sesión, consultas y mensajes de error |
| `Reglas.java` | Reglas sin base de datos: perfiles, permisos, calidad, prioridad de zonas y validaciones |
| `Ui.java` + `estilos.css` | Piezas visuales del diseño: tarjetas, píldoras, tablas, formularios y campo de contraseña |
| `Programa1.java` | Ventana, inicio de sesión, menú lateral y navegación del Programa 1 |
| `Inicio`, `Lotes`, `Zonas`, `Perfil` | Inicio por perfil, lotes y calidad, zonas prioritarias y perfil |
| `Envios.java` | Traslado terrestre Metapa → Tapachula y registro de llegada (PEM) |
| `Embarques.java` | Embarques aéreos de Perú en el aeropuerto de Tapachula (PEA) |
| `Dispersion.java` | Informes de dispersión aérea y existencias por dispersar (PED) |
| `Programa2.java` | Pantallas del administrador |
