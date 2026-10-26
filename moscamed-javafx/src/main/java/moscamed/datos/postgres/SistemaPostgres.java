package moscamed.datos.postgres;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import moscamed.datos.Conexion;
import moscamed.interfaces.datos.SistemaDao;
import moscamed.modelo.Auditoria;
import moscamed.modelo.EstadoSede;
import moscamed.modelo.EstadoSede.NodoPgpool;
import moscamed.modelo.EstadoSede.Replica;
import moscamed.modelo.EstadoSede.Suscripcion;

public final class SistemaPostgres implements SistemaDao {
    @Override
    public List<EstadoSede> estadoSedes() {
        List<EstadoSede> sedes = new ArrayList<>();
        for (String sede : Conexion.SERVIDORES.keySet()) {
            String host = Conexion.direccion(sede);
            boolean local = sede.equals(Conexion.sede());
            Connection con = null;
            try {
                con = local ? Conexion.con() : Conexion.conEn(sede);
                var publica = Conexion.q(con, "SELECT pubname FROM pg_publication").stream().map(r -> Filas.s(r, "pubname")).toList();
                var subs = Conexion.q(con, "SELECT subname, pid IS NOT NULL AS activa FROM pg_stat_subscription WHERE relid IS NULL").stream()
                        .map(r -> new Suscripcion(Filas.s(r, "subname"), Filas.si(r, "activa"))).toList();
                var envia = Conexion.q(con, "SELECT application_name, pg_wal_lsn_diff(sent_lsn, replay_lsn) AS retraso FROM pg_stat_replication").stream()
                        .map(r -> new Replica(Filas.s(r, "application_name"), Filas.l(r, "retraso"))).toList();
                Map<String, Long> conteos = new LinkedHashMap<>();
                for (String t : TABLAS) conteos.put(t, Filas.l(Conexion.q(con, "SELECT count(*) AS n FROM " + t).get(0), "n"));
                List<NodoPgpool> pgpool;
                try {
                    pgpool = Conexion.qSimple(sede, "SHOW pool_nodes").stream().map(n -> new NodoPgpool(Filas.s(n, "node_id"),
                            Filas.s(n, "hostname"), Filas.s(n, "port"), Filas.s(n, "role"), Filas.s(n, "status"))).toList();
                } catch (SQLException sinPgpool) {
                    pgpool = null;
                }
                Long trampas;
                try {
                    trampas = Filas.l(Conexion.q(con, "SELECT count(DISTINCT trampa) AS n FROM trampeo.revision").get(0), "n");
                } catch (SQLException sinFdw) {
                    trampas = null;
                }
                sedes.add(new EstadoSede(sede, host, true, null, publica, subs, envia, conteos, pgpool, trampas));
            } catch (SQLException ex) {
                sedes.add(EstadoSede.caida(sede, host, ex.getMessage().lines().findFirst().orElse("")));
            } finally {
                if (!local && con != null) {
                    try {
                        con.close();
                    } catch (SQLException ignorada) {
                    }
                }
            }
        }
        return sedes;
    }

    @Override
    public List<Auditoria> bitacora(int cuantos) throws SQLException {
        return Conexion.q("SELECT * FROM auditoria ORDER BY fecha DESC LIMIT ?", cuantos).stream()
                .map(b -> new Auditoria(Filas.fechaHora(b, "fecha"), Filas.s(b, "usuario"), Filas.s(b, "accion"))).toList();
    }
}
