package moscamed;

import javafx.application.Application;

/**
 * Punto de entrada de moscamed.jar en Windows y Linux. JavaFX no arranca desde el classpath si la clase
 * con main extiende Application, por eso se lanza desde aquí. "java -jar moscamed.jar admin" abre el Programa 2.
 */
public final class Iniciar {
    private Iniciar() {}

    public static void main(String[] args) {
        boolean admin = args.length > 0 && args[0].equalsIgnoreCase("admin");
        Application.launch(admin ? Programa2.class : Programa1.class, args);
    }
}
