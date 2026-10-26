package moscamed.interfaces.datos;

import java.sql.SQLException;

@FunctionalInterface
public interface Transaccion {
    void correr() throws SQLException;
}
