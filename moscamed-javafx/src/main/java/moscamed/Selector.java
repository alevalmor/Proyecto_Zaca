package moscamed;

import static moscamed.Ui.*;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Inicio de sesión único: la persona elige si entra como operativo (Programa 1) o como administrador
 * (Programa 2) y escribe su usuario. Solo entra si su usuario corresponde a lo elegido: un perfil operativo
 * no pasa como administrador ni el administrador como operativo. Al cerrar sesión se regresa aquí.
 */
public class Selector extends Application {

    /** Ventana abierta desde aquí; null si el programa se abrió directo (sin inicio de sesión único). */
    static Stage ventana;
    /** Ventanita sin marco ni fondo del inicio de sesión: solo se ve la tarjeta. */
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

    /** Cierra la sesión del programa abierto, detiene su vigilante de inactividad y vuelve al inicio de sesión. */
    static void volver(String mensaje) {
        Db.cerrar();
        if (Programa1.app != null && Programa1.app.vigilante != null) Programa1.app.vigilante.stop();
        if (Programa2.app != null && Programa2.app.vigilante != null) Programa2.app.vigilante.stop();
        mostrar(mensaje);
    }

    static void mostrar(String mensaje) {
        ToggleGroup tipo = new ToggleGroup();
        ToggleButton operativo = Programa1.opcion("Operativo", "Lotes, traslados, embarques y dispersión", "operativo", tipo);
        ToggleButton admin = Programa1.opcion("Administrador", "Usuarios, contraseñas y estado del sistema", "admin", tipo);
        operativo.setSelected(true);
        tipo.selectedToggleProperty().addListener((o, antes, ahora) -> {
            if (ahora == null) antes.setSelected(true); // siempre queda una opción elegida
        });
        HBox opciones = new HBox(10, operativo, admin);
        HBox.setHgrow(operativo, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(admin, javafx.scene.layout.Priority.ALWAYS);

        TextField usuario = new TextField();
        usuario.getStyleClass().add("mono");
        PasswordField clave = new PasswordField();
        VBox errores = errores();
        Button entrar = btn("Entrar", () -> {});
        entrar.setMaxWidth(Double.MAX_VALUE);
        entrar.setDefaultButton(true);
        entrar.setOnAction(e -> entrar(tipo.getSelectedToggle().getUserData().equals("admin"), usuario.getText().trim(), clave.getText(), errores));

        // Sin barra de título: la "x" cierra el programa y la tarjeta se arrastra para moverla.
        Button cerrar = btn("✕", "gris", Platform::exit);
        cerrar.getStyleClass().add("btn-chico");
        Region hueco = new Region();
        HBox.setHgrow(hueco, javafx.scene.layout.Priority.ALWAYS);
        HBox marca = new HBox(12, Programa1.logo(40, "#2f7a4f"), new VBox(lbl("Moscamed", "h2"), lbl("Control de moscas estériles", "gris")), hueco, cerrar);
        marca.setAlignment(Pos.CENTER_LEFT);
        VBox form = new VBox(marca, lbl("Iniciar sesión", "h1"), lbl("Entra con el usuario y la contraseña que te dio el administrador.", "gris"));
        form.getStyleClass().add("form-login");
        form.setMaxHeight(Region.USE_PREF_SIZE);
        if (mensaje != null) form.getChildren().add(aviso(mensaje, "azul"));
        form.getChildren().addAll(errores, lbl("Entrar como", "dato"), opciones, campo("Usuario", usuario, null),
                campoClave("Contraseña", clave, null), entrar, aviso("¿No tienes usuario? Pídeselo al administrador.", "gris"));
        double[] agarre = new double[2];
        form.setOnMousePressed(e -> { agarre[0] = e.getScreenX() - login.getX(); agarre[1] = e.getScreenY() - login.getY(); });
        form.setOnMouseDragged(e -> { login.setX(e.getScreenX() - agarre[0]); login.setY(e.getScreenY() - agarre[1]); });
        StackPane raiz = new StackPane(form);
        raiz.setStyle("-fx-background-color: transparent;");

        Scene scene = new Scene(raiz);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(Selector.class.getResource("estilos.css").toExternalForm());
        login.setScene(scene);
        login.show();
        login.centerOnScreen();
        ventana.hide();
        usuario.requestFocus();
    }

    /** Valida al usuario con el programa elegido; si no le corresponde, los errores se muestran aquí mismo. */
    static void entrar(boolean comoAdmin, String usuario, String clave, VBox errores) {
        if (comoAdmin) {
            Programa2 p = new Programa2();
            p.preparar(ventana);
            if (p.entrar(usuario, clave, errores)) abrir(p.scene);
            else cancelar(p.vigilante);
        } else {
            Programa1 p = new Programa1();
            p.preparar(ventana);
            if (p.entrar(usuario, clave, errores)) abrir(p.scene);
            else cancelar(p.vigilante);
        }
    }

    /** Pasa de la ventanita del inicio de sesión a la ventana normal del programa. */
    private static void abrir(Scene scene) {
        ventana.setScene(scene);
        ventana.setMaximized(true);
        ventana.show();
        login.hide();
    }

    private static void cancelar(javafx.animation.Timeline vigilante) {
        vigilante.stop();
        ventana.setTitle("Moscamed");
        login.sizeToScene(); // los errores agrandan la tarjeta
    }

    @Override
    public void stop() {
        Db.cerrar();
    }
}
