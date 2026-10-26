package moscamed.controlador;

import java.sql.SQLException;
import javafx.scene.Node;
import moscamed.interfaces.controlador.Navegador;
import moscamed.interfaces.datos.SistemaDao;
import moscamed.vista.admin.SistemaVista;

public final class SistemaControlador {
    private final SistemaDao sistema;
    private final Navegador nav;

    public SistemaControlador(SistemaDao sistema, Navegador nav) {
        this.sistema = sistema;
        this.nav = nav;
    }

    public Node sistema() throws SQLException {
        return new SistemaVista(nav, sistema.estadoSedes(), sistema.bitacora(10), SistemaDao.TABLAS).vista();
    }
}
