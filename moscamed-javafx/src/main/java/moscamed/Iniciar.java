package moscamed;

import javafx.application.Application;
import moscamed.controlador.Programa1;
import moscamed.controlador.Programa2;
import moscamed.controlador.Selector;

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
