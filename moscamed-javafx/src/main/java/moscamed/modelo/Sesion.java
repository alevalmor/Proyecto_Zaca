package moscamed.modelo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public final class Sesion {
    private static Sesion actual;

    private final String sede, usuario, entrada;
    private final Responsable yo;
    private final Map<String, boolean[]> permisos;

    private Sesion(String sede, String usuario, Responsable yo, Map<String, boolean[]> permisos) {
        this.sede = sede;
        this.usuario = usuario;
        this.yo = yo;
        this.permisos = permisos;
        this.entrada = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    public static Sesion operativo(String sede, String usuario, Responsable yo, Map<String, boolean[]> permisos) {
        return new Sesion(sede, usuario, yo, permisos);
    }

    public static Sesion administrador(String sede, String usuario) {
        return new Sesion(sede, usuario, null, Map.of());
    }

    public static Sesion actual() {
        return actual;
    }

    public static void iniciar(Sesion sesion) {
        actual = sesion;
    }

    public static void terminar() {
        actual = null;
    }

    public String sede() { return sede; }
    public String usuario() { return usuario; }
    public String entrada() { return entrada; }
    public Responsable yo() { return yo; }
    public Map<String, boolean[]> permisos() { return permisos; }
    public String perfil() { return yo == null ? null : yo.perfil(); }
    public int responsableId() { return yo.id(); }
    public String nombre() { return yo.nombreCompleto(); }
    public String nombreCorto() { return yo.nombreCorto(); }
    public String empleado() { return yo.numEmpleado(); }

    public boolean esPes() {
        return "PES".equals(perfil());
    }

    public boolean esPem() {
        return "PEM".equals(perfil());
    }

    public String firmaEnSesion() {
        return nombre() + " · " + empleado() + " (usuario en sesión)";
    }

    public boolean puede(String vista, boolean modificar) {
        if (vista == null || vista.equals("inicio") || vista.equals("perfil")) return true;
        boolean[] p = permisos.getOrDefault(vista, new boolean[]{false, false});
        return modificar ? p[1] : p[0];
    }
}
