package moscamed.interfaces.controlador;

public interface Navegador {
    void ir(String pantalla, String arg);

    default void ir(String pantalla) {
        ir(pantalla, null);
    }

    void avisar(String texto);
}
