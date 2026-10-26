package moscamed.controlador;

import java.sql.SQLException;
import java.util.List;
import javafx.scene.Node;
import moscamed.datos.Conexion;
import moscamed.interfaces.controlador.Navegador;
import moscamed.interfaces.datos.AccesoDao;
import moscamed.modelo.Reglas;
import moscamed.modelo.Sesion;
import moscamed.vista.operativo.PerfilVista;

public final class PerfilControlador {
    private final AccesoDao acceso;
    private final Navegador nav;

    public PerfilControlador(AccesoDao acceso, Navegador nav) {
        this.acceso = acceso;
        this.nav = nav;
    }

    public Node vista() throws SQLException {
        Sesion sesion = Sesion.actual();
        return new PerfilVista(this, acceso.responsable(sesion.responsableId()), Conexion.direccion(sesion.sede())).vista();
    }

    public List<String> cambiarClave(String actual, String nueva, String confirmacion) {
        String error = null;
        if (!Conexion.claveCorrecta(actual)) error = "La contraseña actual no coincide.";
        else if (!nueva.equals(confirmacion)) error = "La nueva contraseña y su confirmación no coinciden.";
        else if (!Reglas.contrasenaValida(nueva)) error = "Mínimo 10 caracteres, con número y símbolo.";
        if (error != null) return List.of(error);
        try {
            acceso.cambiarMiClave(nueva);
        } catch (SQLException e) {
            return List.of("No se pudo cambiar en los dos servidores: " + Conexion.mensaje(e));
        }
        Conexion.cambiarClaveGuardada(nueva);
        nav.avisar("Contraseña cambiada en los dos servidores.");
        nav.ir("perfil");
        return List.of();
    }
}
