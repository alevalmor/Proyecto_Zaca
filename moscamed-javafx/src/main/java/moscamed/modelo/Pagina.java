package moscamed.modelo;

import java.util.List;

public record Pagina<T>(List<T> filas, long total) {
    public static final int TAMANO = 20;

    public static int desplazamiento(int pagina) {
        return (pagina - 1) * TAMANO;
    }
}
