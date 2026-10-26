@echo off
rem Moscamed en Windows. Copia este archivo junto a moscamed.jar.
rem   moscamed.bat         -> inicio de sesión (elige Operativo o Administrador)
rem   moscamed.bat admin   -> Programa 2 (administrador)
rem Servidores (Pgpool-II de cada sede). Cambialos si tu red usa otras direcciones.
if "%PG_HOST_PLANTA%"=="" set PG_HOST_PLANTA=192.168.10.10
if "%PG_PORT_PLANTA%"=="" set PG_PORT_PLANTA=9999
if "%PG_HOST_EMPAQUE%"=="" set PG_HOST_EMPAQUE=192.168.20.10
if "%PG_PORT_EMPAQUE%"=="" set PG_PORT_EMPAQUE=9999
start "" javaw -jar "%~dp0moscamed.jar" %*
