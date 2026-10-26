package moscamed.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DatosLote(LocalDate fechaProduccion, LocalDate fechaIrradiacion, BigDecimal dosisGy, long cantidadPupas,
                        String cepa, String tinte, int centroEmpaqueId, String observaciones) {
}
