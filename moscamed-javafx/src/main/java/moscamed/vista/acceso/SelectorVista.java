package moscamed.vista.acceso;

import static moscamed.vista.Componentes.*;

import java.util.List;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import moscamed.interfaces.vista.VistaLogin;

public final class SelectorVista implements VistaLogin {
    @FunctionalInterface
    public interface Entrada {
        String entrar(boolean comoAdmin, String usuario, String clave);
    }

    private final Stage ventana;
    private final Entrada entrada;
    private final TextField usuario = new TextField();

    public SelectorVista(Stage ventana, Entrada entrada) {
        this.ventana = ventana;
        this.entrada = entrada;
    }

    @Override
    public Parent raiz(String mensaje) {
        ToggleGroup tipo = new ToggleGroup();
        ToggleButton operativo = opcion("Operativo", "Lotes, traslados, embarques y dispersión", "operativo", tipo);
        ToggleButton admin = opcion("Administrador", "Usuarios, contraseñas y estado del sistema", "admin", tipo);
        operativo.setSelected(true);
        tipo.selectedToggleProperty().addListener((o, antes, ahora) -> {
            if (ahora == null) antes.setSelected(true);
        });
        HBox opciones = new HBox(10, operativo, admin);
        HBox.setHgrow(operativo, Priority.ALWAYS);
        HBox.setHgrow(admin, Priority.ALWAYS);

        usuario.getStyleClass().add("mono");
        PasswordField clave = new PasswordField();
        VBox errores = errores();
        Button entrar = btn("Entrar", () -> {
            String error = entrada.entrar(tipo.getSelectedToggle().getUserData().equals("admin"), usuario.getText().trim(), clave.getText());
            if (error != null) {
                mostrarErrores(errores, List.of(error));
                ventana.sizeToScene();
            }
        });
        entrar.setMaxWidth(Double.MAX_VALUE);
        entrar.setDefaultButton(true);

        Button cerrar = chico(btn("✕", "gris", Platform::exit));
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox marca = new HBox(12, logo(40, "#2f7a4f"), new VBox(lbl("Moscamed", "h2"), lbl("Control de moscas estériles", "gris")), hueco, cerrar);
        marca.setAlignment(Pos.CENTER_LEFT);
        VBox form = new VBox(marca, lbl("Iniciar sesión", "h1"), lbl("Entra con el usuario y la contraseña que te dio el administrador.", "gris"));
        form.getStyleClass().add("form-login");
        form.setMaxHeight(Region.USE_PREF_SIZE);
        if (mensaje != null) form.getChildren().add(aviso(mensaje, "azul"));
        form.getChildren().addAll(errores, lbl("Entrar como", "dato"), opciones, campo("Usuario", usuario, null),
                campoClave("Contraseña", clave, null), entrar, aviso("¿No tienes usuario? Pídeselo al administrador.", "gris"));
        double[] agarre = new double[2];
        form.setOnMousePressed(e -> { agarre[0] = e.getScreenX() - ventana.getX(); agarre[1] = e.getScreenY() - ventana.getY(); });
        form.setOnMouseDragged(e -> { ventana.setX(e.getScreenX() - agarre[0]); ventana.setY(e.getScreenY() - agarre[1]); });
        StackPane raiz = new StackPane(form);
        raiz.setStyle("-fx-background-color: transparent;");
        return raiz;
    }

    @Override
    public void enfocar() {
        usuario.requestFocus();
    }
}
