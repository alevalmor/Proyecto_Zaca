# Pgpool-II no tiene versión para Windows: corre en Linux. La imagen de postgres ya trae el repositorio PGDG.
FROM postgres:18
RUN apt-get update && apt-get install -y --no-install-recommends pgpool2 && rm -rf /var/lib/apt/lists/*
USER postgres
# -D: al reiniciar olvida qué nodo estaba caído (en modo raw no regresa solo; ver infra/README.md).
# Borra el pid que queda en /tmp si el contenedor se apagó sin cerrar Pgpool (si no, no vuelve a arrancar).
CMD ["sh", "-c", "rm -f /tmp/pgpool.pid && exec pgpool -n -D -f /etc/pgpool2/pgpool.conf"]
