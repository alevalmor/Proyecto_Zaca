package moscamed.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ControlCalidad(String etapa, BigDecimal emergencia, BigDecimal vuelo, BigDecimal esterilidad, BigDecimal pesoMg,
                             boolean aprobado, LocalDate fecha, String observaciones, String numEmpleado) {
    public String resultado() {
        return aprobado ? "Aprobado" : "Rechazado";
    }
}
