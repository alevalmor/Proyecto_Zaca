package moscamed.interfaces.datos;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import moscamed.modelo.Responsable;

public interface AccesoDao {
    boolean esAdmin(Connection con) throws SQLException;

    Optional<Responsable> responsableActual(Connection con) throws SQLException;

    Map<String, boolean[]> permisosQuitados(Connection con, int responsableId) throws SQLException;

    Responsable responsable(int id) throws SQLException;

    void cambiarMiClave(String nueva) throws SQLException;
}
