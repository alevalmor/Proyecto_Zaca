package moscamed.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record Envio(int id, String codigo, int loteId, int vehiculoId, LocalDateTime fechaSalida, long cantidad,
                    BigDecimal temperatura, LocalDateTime recibido, boolean cancelado, long liberadas, String estado,
                    String loteCodigo, String matricula, String modelo, String registro, String zonas) {
    public static final List<String> ESTADOS = List.of("EN CAMINO", "RECIBIDO", "DISPERSADO", "CANCELADO");

    public boolean enCamino() {
        return "EN CAMINO".equals(estado);
    }
}
