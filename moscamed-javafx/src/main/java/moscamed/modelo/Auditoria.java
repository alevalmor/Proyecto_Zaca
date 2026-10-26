package moscamed.modelo;

import java.time.LocalDateTime;

public record Auditoria(LocalDateTime fecha, String usuario, String accion) {
}
