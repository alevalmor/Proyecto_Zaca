package moscamed.interfaces.datos;

import java.sql.SQLException;
import moscamed.modelo.NuevoUsuario;
import moscamed.modelo.Pagina;
import moscamed.modelo.Responsable;
import moscamed.modelo.ResumenUsuarios;

public interface UsuarioDao {
    Pagina<Responsable> buscar(String texto, String perfil, Boolean activo, int pagina) throws SQLException;

    ResumenUsuarios resumen() throws SQLException;

    void restablecerClave(String usuario, String clave) throws SQLException;

    void cambiarAcceso(int id, String usuario, boolean activo) throws SQLException;

    void crearRol(NuevoUsuario nuevo) throws SQLException;

    String registrar(NuevoUsuario nuevo) throws SQLException;
}
