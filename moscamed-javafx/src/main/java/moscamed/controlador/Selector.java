package moscamed.controlador;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import moscamed.interfaces.controlador.Programa;
import moscamed.vista.acceso.SelectorVista;

public class Selector extends Application {
    static Stage ventana;
    static Stage login;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        ventana = stage;
        login = new Stage(StageStyle.TRANSPARENT);
        login.setTitle("Moscamed");
        mostrar(null);
    }

    static void volver(String mensaje) {
        AccesoControlador.salir();
        if (Programa1.app != null) Programa1.app.detener();
        if (Programa2.app != null) Programa2.app.detener();
        mostrar(mensaje);
    }

    static void mostrar(String mensaje) {
        SelectorVista vista = new SelectorVista(login, Selector::entrar);
        Scene scene = new Scene(vista.raiz(mensaje));
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(Selector.class.getResource("/moscamed/estilos.css").toExternalForm());
        login.setScene(scene);
        login.show();
        login.centerOnScreen();
        ventana.hide();
        vista.enfocar();
    }

    static String entrar(boolean comoAdmin, String usuario, String clave) {
        Programa p = comoAdmin ? new Programa2() : new Programa1();
        p.preparar(ventana);
        String error = p.entrar(usuario, clave);
        if (error == null) {
            abrir(p.escena());
        } else {
            p.detener();
            ventana.setTitle("Moscamed");
        }
        return error;
    }

    private static void abrir(Scene scene) {
        ventana.setScene(scene);
        ventana.setMaximized(true);
        ventana.show();
        login.hide();
    }

    @Override
    public void stop() {
        AccesoControlador.salir();
    }
}
