package moscamed.vista.acceso;

import static moscamed.vista.Componentes.*;

import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import moscamed.interfaces.controlador.Autenticador;
import moscamed.interfaces.vista.VistaLogin;

public final class LoginAdminVista implements VistaLogin {
    private final Autenticador autenticador;
    private final TextField usuario = new TextField();

    public LoginAdminVista(Autenticador autenticador) {
        this.autenticador = autenticador;
    }

    @Override
    public Parent raiz(String mensaje) {
        usuario.getStyleClass().add("mono");
        PasswordField clave = new PasswordField();
        VBox errores = errores();
        Button entrar = btn("Entrar al panel", () -> {
            String error = autenticador.entrar(usuario.getText().trim(), clave.getText());
            if (error != null) mostrarErrores(errores, List.of(error));
        });
        entrar.setDefaultButton(true);
        entrar.setMaxWidth(Double.MAX_VALUE);
        HBox marca = new HBox(12, escudo(40, "#6A1B29"), new VBox(lbl("Moscamed Admin", "h2", "h2-grande"), lbl("Programa 2 · administración de accesos", "gris")));
        marca.setAlignment(Pos.CENTER_LEFT);
        VBox form = new VBox(marca, lbl("Solo para el administrador. Desde aquí se crean los usuarios y perfiles (PES y PEM) que entran al Programa 1.", "gris"));
        form.getStyleClass().add("form-login");
        if (mensaje != null) form.getChildren().add(aviso(mensaje, "azul"));
        form.getChildren().addAll(errores, campo("Usuario administrador", usuario, null), campoClave("Contraseña", clave, null),
                entrar,
                lbl("Acceso limitado a 5 conexiones · cada acción queda en la bitácora de auditoría", "gris", "chico"));
        StackPane raiz = new StackPane(form);
        raiz.getStyleClass().add("admin");
        raiz.setStyle("-fx-background-color: #123826; -fx-padding: 48 16 48 16;");
        return raiz;
    }

    @Override
    public void enfocar() {
        usuario.requestFocus();
    }
}
