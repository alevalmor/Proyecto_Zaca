package moscamed.datos.postgres;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import moscamed.datos.Conexion;
import moscamed.interfaces.datos.AccesoDao;
import moscamed.modelo.Responsable;

public final class AccesoPostgres implements AccesoDao {
    @Override
    public boolean esAdmin(Connection con) throws SQLException {
        return Filas.si(Conexion.q(con, "SELECT pg_has_role(current_user, 'rol_admin', 'member') AS si").get(0), "si");
    }

    @Override
    public Optional<Responsable> responsableActual(Connection con) throws SQLException {
        return Conexion.q(con, "SELECT * FROM responsable WHERE usuario_bd = current_user").stream().findFirst().map(Filas::responsable);
    }

    @Override
    public Map<String, boolean[]> permisosQuitados(Connection con, int responsableId) throws SQLException {
        Map<String, boolean[]> quitados = new HashMap<>();
        for (var q : Conexion.q(con, "SELECT * FROM permiso_vista WHERE responsable_id = ?", responsableId)) {
            quitados.put(Filas.s(q, "vista"), new boolean[]{Filas.si(q, "ver"), Filas.si(q, "modificar")});
        }
        return quitados;
    }

    @Override
    public Responsable responsable(int id) throws SQLException {
        return Filas.responsable(Conexion.q1("SELECT * FROM responsable WHERE id = ?", id));
    }

    @Override
    public void cambiarMiClave(String nueva) throws SQLException {
        Conexion.enAmbos("ALTER ROLE CURRENT_USER PASSWORD " + Conexion.literal(nueva));
        Conexion.auditar("Cambió su contraseña");
    }
}
