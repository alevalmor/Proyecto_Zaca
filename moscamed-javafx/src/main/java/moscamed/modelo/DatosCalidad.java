package moscamed.modelo;

import java.math.BigDecimal;

public record DatosCalidad(BigDecimal emergencia, BigDecimal vuelo, BigDecimal esterilidad, BigDecimal pesoMg, String observaciones) {
}
