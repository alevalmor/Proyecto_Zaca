package moscamed.vista.acceso;

import static moscamed.vista.Componentes.*;

import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import moscamed.interfaces.controlador.Autenticador;
import moscamed.interfaces.vista.VistaLogin;

public final class LoginOperativoVista implements VistaLogin {
    private final Autenticador autenticador;
    private final TextField usuario = new TextField();

    public LoginOperativoVista(Autenticador autenticador) {
        this.autenticador = autenticador;
    }

    @Override
    public Parent raiz(String mensaje) {
        VBox portada = new VBox();
        portada.getStyleClass().add("portada");
        portada.setPrefWidth(560);
        HBox marca = new HBox(12, logo(40, "#9fd3b2"), new VBox(lbl("Moscamed", "h2"), lbl("Programa 1 · Administración de moscas", "texto-portada")));
        marca.setAlignment(Pos.CENTER_LEFT);
        VBox texto = new VBox(16, lbl("Control de producción y liberación de moscas estériles", "titulo-portada"),
                lbl("La planta de Metapa produce y manda las pupas por tierra a Tapachula. Ahí llegan también los embarques aéreos de Perú, "
                        + "y la dispersión se hace solo por avioneta. Cada sede trabaja en su servidor y los datos se replican entre las dos.", "texto-portada"));
        VBox.setVgrow(texto, Priority.ALWAYS);
        texto.setAlignment(Pos.CENTER_LEFT);
        portada.getChildren().addAll(marca, texto);

        usuario.getStyleClass().add("mono");
        PasswordField clave = new PasswordField();
        VBox errores = errores();
        Button entrar = btn("Entrar", () -> {
            String error = autenticador.entrar(usuario.getText().trim(), clave.getText());
            if (error != null) mostrarErrores(errores, List.of(error));
        });
        entrar.setMaxWidth(Double.MAX_VALUE);
        entrar.setDefaultButton(true);

        VBox form = new VBox(lbl("Iniciar sesión", "h1"), lbl("Entra con el usuario y la contraseña que te dio el administrador.", "gris"));
        form.getStyleClass().add("form-login");
        if (mensaje != null) form.getChildren().add(aviso(mensaje, "azul"));
        form.getChildren().addAll(errores, campo("Usuario", usuario, null), campoClave("Contraseña", clave, null), entrar,
                aviso("¿No tienes usuario? Pídeselo al administrador.", "gris"));
        StackPane lado = new StackPane(form);
        lado.setStyle("-fx-padding: 48 24 48 24;");
        HBox raiz = new HBox(portada, lado);
        HBox.setHgrow(lado, Priority.ALWAYS);
        return raiz;
    }

    @Override
    public void enfocar() {
        usuario.requestFocus();
    }
}
