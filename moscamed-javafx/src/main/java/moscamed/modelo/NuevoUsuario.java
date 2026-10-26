package moscamed.modelo;

import java.util.Map;

public record NuevoUsuario(String perfil, String nombre, String apPaterno, String apMaterno, String telefono, String correo,
                           String usuario, String clave, Map<String, boolean[]> quitados) {
    public static final Map<String, String> TITULOS = Map.of("PES", "Administrador Moscamed", "PEM", "Administrador Empacadora SENASICA");

    public String sede() {
        return Reglas.sede(perfil);
    }

    public String rol() {
        return Reglas.PERFILES.get(perfil)[3];
    }

    public String prefijoEmpleado() {
        return sede().equals("PLANTA") ? "MET-" : "TAP-";
    }

    public String puesto() {
        return TITULOS.get(perfil);
    }
}
