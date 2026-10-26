package moscamed.controlador;

import java.sql.Connection;
import java.sql.SQLException;
import moscamed.datos.Conexion;
import moscamed.interfaces.datos.AccesoDao;
import moscamed.modelo.Reglas;
import moscamed.modelo.Responsable;
import moscamed.modelo.Sesion;

public final class AccesoControlador {
    public record Intento(boolean ok, String mensaje) {
        public static Intento error(String mensaje) { return new Intento(false, mensaje); }
    }

    public static void salir() {
        Conexion.cerrar();
        Sesion.terminar();
    }

    private final AccesoDao acceso;

    public AccesoControlador(AccesoDao acceso) {
        this.acceso = acceso;
    }

    public Intento entrarOperativo(String usuario, String clave) {
        try {
            var primero = Conexion.conectarCualquiera(usuario, clave);
            String sede = primero.getKey();
            Connection con = primero.getValue();
            var yo = acceso.responsableActual(con);
            String error = null;
            if (acceso.esAdmin(con)) error = "Este usuario es de administrador: no puede entrar como operativo.";
            else if (yo.isEmpty()) error = "Tu usuario no tiene perfil en el sistema. Pídeselo al administrador.";
            else if (!yo.get().activo()) error = "Tu usuario está dado de baja.";
            if (error != null) {
                con.close();
                return Intento.error(error);
            }
            Responsable r = yo.get();
            String aviso = null;
            if (!sede.equals(r.sede())) {
                try {
                    Connection propia = Conexion.conectar(r.sede(), usuario, clave);
                    con.close();
                    con = propia;
                    sede = r.sede();
                } catch (SQLException caido) {
                    if (!Conexion.esDesconexion(caido)) throw caido;
                    aviso = "El servidor de tu sede no responde: estás trabajando en el de la otra sede. Tus datos se copian solos cuando regrese.";
                }
            }
            var quitados = acceso.permisosQuitados(con, r.id());
            Conexion.abrir(sede, usuario, clave, con);
            Sesion.iniciar(Sesion.operativo(sede, usuario, r, Reglas.permisos(r.perfil(), quitados)));
            return new Intento(true, aviso);
        } catch (SQLException e) {
            return Intento.error(Conexion.errorConexion(e, "ningún servidor"));
        }
    }

    public Intento entrarAdmin(String usuario, String clave) {
        try {
            var primero = Conexion.conectarCualquiera(usuario, clave);
            Connection con = primero.getValue();
            if (!acceso.esAdmin(con)) {
                con.close();
                return Intento.error("Este usuario no es administrador.");
            }
            Conexion.abrir(primero.getKey(), usuario, clave, con);
            Sesion.iniciar(Sesion.administrador(primero.getKey(), usuario));
            return new Intento(true, null);
        } catch (SQLException e) {
            return Intento.error(Conexion.errorConexion(e, "ningún servidor"));
        }
    }

}
