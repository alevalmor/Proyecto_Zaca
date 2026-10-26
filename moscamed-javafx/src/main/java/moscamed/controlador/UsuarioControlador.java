package moscamed.controlador;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.scene.Node;
import moscamed.datos.Conexion;
import moscamed.interfaces.controlador.Navegador;
import moscamed.interfaces.datos.UsuarioDao;
import moscamed.interfaces.vista.VistaPaginada;
import moscamed.modelo.NuevoUsuario;
import moscamed.modelo.Reglas;
import moscamed.modelo.Responsable;
import moscamed.vista.Componentes;
import moscamed.vista.admin.NuevoUsuarioVista;
import moscamed.vista.admin.UsuariosVista;

public final class UsuarioControlador {
    public record EntradaUsuario(String perfil, String nombre, String paterno, String materno, String telefono, String correo,
                          String usuario, String clave, Map<String, boolean[]> marcados) {}

    private final UsuarioDao usuarios;
    private final Navegador nav;

    public UsuarioControlador(UsuarioDao usuarios, Navegador nav) {
        this.usuarios = usuarios;
        this.nav = nav;
    }

    public Node usuarios() throws SQLException {
        return new UsuariosVista(this, nav, usuarios.resumen()).vista();
    }

    public void buscar(String texto, String perfil, Boolean activo, int pagina, VistaPaginada<Responsable> vista) {
        try {
            vista.mostrar(usuarios.buscar(texto, perfil, activo, pagina));
        } catch (SQLException e) {
            vista.mostrarError(Conexion.mensaje(e));
        }
    }

    public void accion(int id, String login, String accion) {
        try {
            if (accion.equals("clave")) {
                String clave = Reglas.claveTemporal();
                usuarios.restablecerClave(login, clave);
                nav.avisar("Contraseña temporal de " + login + ": " + clave + " — entrégasela en persona.");
                Componentes.mostrarCredenciales("Nueva contraseña de " + login, login, clave);
            } else {
                boolean activo = accion.equals("reactivar");
                usuarios.cambiarAcceso(id, login, activo);
                nav.avisar(login + (activo ? " reactivado." : " dado de baja."));
            }
        } catch (SQLException e) {
            nav.avisar(Conexion.mensaje(e));
        }
        nav.ir("usuarios", null);
    }

    public Node nuevo(String perfil) {
        return new NuevoUsuarioVista(this, nav, perfil).vista();
    }

    public List<String> crear(EntradaUsuario e) {
        String u = e.usuario().trim().toLowerCase();
        List<String> errs = new ArrayList<>();
        if (!Reglas.usuarioBdValido(u)) errs.add("El usuario va en minúsculas, empieza con letra y solo lleva letras, números, punto o guion bajo.");
        if (e.nombre().isBlank()) errs.add("Falta el nombre.");
        if (e.paterno().isBlank()) errs.add("Falta el apellido paterno.");
        if (!Reglas.contrasenaValida(e.clave())) errs.add("La contraseña lleva mínimo 10 caracteres, con número y símbolo.");
        if (!errs.isEmpty()) return errs;

        Map<String, boolean[]> quitados = new LinkedHashMap<>();
        for (var m : e.marcados().entrySet()) {
            boolean[] max = Reglas.PERMISOS_PERFIL.get(e.perfil()).get(m.getKey());
            boolean ver = m.getValue()[0], mod = m.getValue()[1];
            if ((max[0] && !ver) || (max[1] && !mod)) quitados.put(m.getKey(), new boolean[]{ver, mod});
        }
        var nuevo = new NuevoUsuario(e.perfil(), e.nombre().trim(), e.paterno().trim(), Entradas.opcional(e.materno()),
                Entradas.opcional(e.telefono()), Entradas.opcional(e.correo()), u, e.clave(), quitados);
        try {
            usuarios.crearRol(nuevo);
        } catch (SQLException ex) {
            return List.of("No se pudo crear el usuario en los dos servidores: " + Conexion.mensaje(ex));
        }
        String num;
        try {
            num = usuarios.registrar(nuevo);
        } catch (SQLException ex) {
            return List.of(Conexion.mensaje(ex) + " El usuario de PostgreSQL ya se creó; bórralo con DROP ROLE antes de reintentar.");
        }
        nav.avisar("Usuario " + u + " creado (" + num + "). Contraseña: " + e.clave() + " — entrégasela en persona.");
        Componentes.mostrarCredenciales("Usuario " + u + " creado", u, e.clave());
        nav.ir("usuarios", null);
        return List.of();
    }
}
