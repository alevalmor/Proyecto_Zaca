package moscamed.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InformeDispersion(long id, Integer envioId, Integer embarqueId, String punto, int zonaId, LocalDateTime fecha,
                                long cantidad, BigDecimal superficieHa, int altitudM, String avioneta, String piloto,
                                String observaciones, String zonaClave, String zonaNombre, String envioCodigo,
                                String embarqueCodigo) {
    public String fuenteCodigo() {
        return envioCodigo != null ? envioCodigo : embarqueCodigo;
    }
}
