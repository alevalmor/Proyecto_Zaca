package moscamed.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record Lote(int id, String codigo, LocalDate fechaProduccion, LocalDate fechaIrradiacion, BigDecimal dosisGy,
                   long cantidadPupas, String cepa, String tinte, int centroEmpaqueId, String observaciones,
                   String estado, Boolean calidadPlanta, long enviadas, long liberadas,
                   String centroClave, String centroNombre, String registro) {
    public static final List<String> ESTADOS = List.of("PRODUCIDO", "APROBADO", "RECHAZADO", "ENVIADO", "LIBERADO");
    public static final List<String> CEPAS = List.of("Tapachula-7 TSL", "VIENNA-8 TSL");
    public static final List<String> TINTES = List.of("Rosa fluorescente", "Naranja fluorescente", "Verde fluorescente");

    public boolean modificable() {
        return estado.equals("PRODUCIDO") || estado.equals("APROBADO");
    }

    public String textoCalidadPlanta() {
        return calidadPlanta == null ? "Pendiente" : calidadPlanta ? "Aprobado" : "Rechazado";
    }
}
