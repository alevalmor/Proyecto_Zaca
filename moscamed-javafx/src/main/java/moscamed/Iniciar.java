package moscamed;

import javafx.application.Application;

/**
 * Punto de entrada de moscamed.jar en Windows y Linux. JavaFX no arranca desde el classpath si la clase
 * con main extiende Application, por eso se lanza desde aquí. Sin argumento abre el inicio de sesión
 * único (Operativo o Administrador); "admin" abre directo el Programa 2 y "operativo" el Programa 1.
 */
public final class Iniciar {
    private Iniciar() {}

    public static void main(String[] args) {
        String cual = args.length > 0 ? args[0].toLowerCase() : "";
        Application.launch(switch (cual) {
            case "admin" -> Programa2.class;
            case "operativo" -> Programa1.class;
            default -> Selector.class;
        }, args);
    }
}
