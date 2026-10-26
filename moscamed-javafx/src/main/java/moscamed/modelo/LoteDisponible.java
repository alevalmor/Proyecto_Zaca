package moscamed.modelo;

import java.time.LocalDate;

public record LoteDisponible(int id, String codigo, long cantidadPupas, long enviadas, LocalDate fechaCalidad, long disponibles) {
}
