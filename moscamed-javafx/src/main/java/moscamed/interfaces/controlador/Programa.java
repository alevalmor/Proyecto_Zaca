package moscamed.interfaces.controlador;

import javafx.scene.Scene;
import javafx.stage.Stage;

public interface Programa extends Navegador, Autenticador {
    void preparar(Stage stage);

    Scene escena();

    void salir(String mensaje);

    void detener();
}
