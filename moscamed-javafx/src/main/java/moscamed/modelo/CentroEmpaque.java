package moscamed.modelo;

public record CentroEmpaque(int id, String clave, String nombre) {
    public String etiqueta() {
        return clave + " · " + nombre;
    }
}
