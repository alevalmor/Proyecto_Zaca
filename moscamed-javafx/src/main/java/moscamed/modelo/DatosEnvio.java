package moscamed.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DatosEnvio(int loteId, int vehiculoId, LocalDateTime fechaSalida, long cantidad, BigDecimal temperatura) {
}
