package moscamed.modelo;

import java.time.LocalDate;

public record Responsable(int id, String numEmpleado, String nombre, String apPaterno, String apMaterno, String puesto,
                          String telefono, String correo, String sede, String perfil, String usuarioBd, boolean activo,
                          LocalDate fechaAlta) {
    public String nombreCompleto() {
        return (nombre + " " + apPaterno + " " + (apMaterno == null ? "" : apMaterno)).trim();
    }

    public String nombreCorto() {
        return nombre.split(" ")[0];
    }

    public String iniciales() {
        return "" + nombre.charAt(0) + apPaterno.charAt(0);
    }

    public static String firma(String numEmpleado, String nombre, String apPaterno) {
        return numEmpleado + " · " + nombre.charAt(0) + ". " + apPaterno;
    }
}
