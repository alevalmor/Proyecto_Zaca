package moscamed.controlador;

import java.time.LocalDateTime;
import moscamed.vista.Formato;

final class Entradas {
    private Entradas() {}

    static LocalDateTime fechaHora(String texto) {
        return LocalDateTime.parse(texto.trim(), Formato.FECHA_HORA);
    }

    static String opcional(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
