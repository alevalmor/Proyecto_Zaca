package moscamed.modelo;

import java.math.BigDecimal;

public record Zona(int id, String clave, String nombre, String prioridad, BigDecimal mtd, long silvestres, long sugeridas) {
    public boolean alta() {
        return "ALTA".equals(prioridad);
    }
}
