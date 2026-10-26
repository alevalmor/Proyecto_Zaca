# Sistema Moscamed · Java y JavaFX

Aplicación de escritorio con las vistas del diseño "Mockups Sistema Moscamed". Usa Java 21 o más reciente, JavaFX 21 y PostgreSQL por JDBC.

- **Programa 1** (`moscamed.controlador.Programa1`): operativo para cuatro perfiles. PES (planta Metapa): lotes y control de calidad. PEM (centro de empaque Tapachula): traslados terrestres Metapa → Tapachula y su llegada. PEA (aeropuerto de Tapachula): embarques aéreos de Perú. PED (encargada de dispersión): informes de dispersión aérea. Todos ven su perfil.
- **Programa 2** (`moscamed.controlador.Programa2`): administrador. Crea usuarios de los cuatro perfiles, da de baja, restablece contraseñas y muestra el estado del sistema.

Cada persona entra con **su propio usuario de PostgreSQL**. La base de datos aplica los permisos con `rol_produccion`, `rol_empaque`, `rol_aeropuerto`, `rol_dispersion` y `rol_admin`.

### Contraseñas

Al crear un usuario, el administrador ve la contraseña (generada o escrita por él) con "Ver contraseña". Al guardar aparece una ventana con el usuario y la contraseña y un botón para copiarlos. Lo mismo pasa al restablecer. PostgreSQL guarda las contraseñas cifradas, así que después ya no se pueden consultar: si alguien la pierde, se restablece. Todos los campos de contraseña tienen "Ver contraseña".

## Base de datos

Dos servidores PostgreSQL master-master y la base de trampeo leída con `postgres_fdw`, todo en [`../moscamed.sql`](../moscamed.sql) (su encabezado trae los pasos para los servidores reales). En esta computadora se levanta sin Docker, en los puertos 5452 (planta) y 5453 (empaque):

```bash
bash ../bd/instalar.sh
```

Usuarios de ejemplo (contraseña `moscamed`): `usr_planta` (PES), `usr_empaque` (PEM), `usr_aeropuerto` (PEA), `usr_dispersion` (PED) y `usr_admin`.

## Ejecutar

Las variables de entorno apuntan a Pgpool-II de cada sede. Sin ellas se usan los valores del diseño:

| Variable | Por defecto |
|---|---|
| `PG_HOST_PLANTA` / `PG_PORT_PLANTA` | `192.168.10.10` / `9999` |
| `PG_HOST_EMPAQUE` / `PG_PORT_EMPAQUE` | `192.168.20.10` / `9999` |
| `PG_DB` | `moscamed` |

```bash
mvn javafx:run                                  # inicio de sesión: Operativo o Administrador
mvn javafx:run -Dapp.main=moscamed.controlador.Programa2    # Programa 2
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
mvn test        # reglas de negocio, sin base de datos
```

Con los dos servidores encendidos también se prueban la base y las pantallas (necesita escritorio para JavaFX):

```bash
export MOSCAMED_BD=si PG_HOST_PLANTA=127.0.0.1 PG_PORT_PLANTA=5452 PG_HOST_EMPAQUE=127.0.0.1 PG_PORT_EMPAQUE=5453
mvn test
```

- `FlujoBdTest`: recorrido completo con los usuarios de ejemplo (lote, calidad, envío, llegada, dispersión, alta y baja de una persona) y que la base rechace lo que no le toca a cada perfil. Al terminar borra lo que escribió (entra como `postgres`; su contraseña se cambia con `MOSCAMED_PG_CLAVE`).
- `PantallasTest`: entra con cada perfil y abre todas sus pantallas; ninguna debe fallar ni mostrar un error de la base. Solo lee.

## Estructura (modelo-vista-controlador)

Todo está en `src/main/java/moscamed/`. Cada capa solo usa a la de abajo: las vistas no tocan la base y la base no sabe de pantallas.

| Paquete | Contenido |
|---|---|
| `modelo` | Objetos del sistema (`Lote`, `Envio`, `InformeDispersion`, `Existencia`, `Zona`, `Responsable`…), `Sesion` (quién entró y sus permisos) y `Reglas` (perfiles, permisos, calidad y validaciones, sin base de datos) |
| `interfaces.datos` | Acceso a datos: `LoteDao`, `EnvioDao`, `DispersionDao`, `AccesoDao`, `UsuarioDao`, `SistemaDao` y `Transaccion` (lo que corre `Conexion.tx` en una sola transacción) |
| `interfaces.controlador` | `Navegador` (las vistas piden cambiar de pantalla), `Autenticador` (lo que hace "Entrar") y `Programa` (lo común del Programa 1 y el Programa 2; `Selector` abre cualquiera de los dos con ella) |
| `interfaces.vista` | `Vista` (toda pantalla), `VistaPaginada` (listas con filtros y páginas), `Menu` (menús laterales) y `VistaLogin` (inicios de sesión) |
| `datos` | `Conexion`: conexión con el usuario de la sesión, transacciones y mensajes de error |
| `datos.postgres` | Las consultas SQL que implementan las interfaces de datos (`LotePostgres`, `EnvioPostgres`…) |
| `vista` | `Componentes` + `estilos.css` (tarjetas, píldoras, tablas, formularios) y `Formato` (números y fechas) |
| `vista.operativo` | Pantallas del Programa 1: inicio, lotes, envíos, dispersión, perfil y menú |
| `vista.admin` | Pantallas del Programa 2: usuarios, nuevo usuario, estado del sistema y menú |
| `vista.acceso` | Inicios de sesión (único, Programa 1 y Programa 2) |
| `controlador` | `Programa1`, `Programa2` y `Selector` (ventanas, navegación e inactividad), y un controlador por tema (`LoteControlador`, `EnvioControlador`, `DispersionControlador`, `InicioControlador`, `PerfilControlador`, `UsuarioControlador`, `SistemaControlador`, `AccesoControlador`): leen con los DAO, validan lo que se escribe y eligen la vista |
| `Iniciar.java` | Punto de entrada de `moscamed.jar` |
