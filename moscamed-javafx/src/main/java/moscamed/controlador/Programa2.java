package moscamed.controlador;

import static moscamed.vista.Componentes.aviso;
import static moscamed.vista.Componentes.desplazable;

import java.sql.SQLException;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.InputEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import moscamed.datos.Conexion;
import moscamed.datos.postgres.AccesoPostgres;
import moscamed.datos.postgres.SistemaPostgres;
import moscamed.datos.postgres.UsuarioPostgres;
import moscamed.interfaces.controlador.Programa;
import moscamed.modelo.Sesion;
import moscamed.vista.acceso.LoginAdminVista;
import moscamed.vista.admin.MenuAdminVista;

public class Programa2 extends Application implements Programa {
    static Programa2 app;
    Scene scene;
    BorderPane marco;
    String flash;
    long ultimoUso = System.currentTimeMillis();
    Timeline vigilante;

    private final AccesoControlador acceso = new AccesoControlador(new AccesoPostgres());
    private final UsuarioControlador usuarios = new UsuarioControlador(new UsuarioPostgres(), this);
    private final SistemaControlador sistema = new SistemaControlador(new SistemaPostgres(), this);

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        preparar(stage);
        stage.setScene(scene);
        mostrarLogin(null);
        stage.show();
    }

    @Override
    public void preparar(Stage stage) {
        app = this;
        marco = new BorderPane();
        marco.getStyleClass().add("admin");
        scene = new Scene(new StackPane(), 1280, 860);
        scene.getStylesheets().add(Programa2.class.getResource("/moscamed/estilos.css").toExternalForm());
        scene.addEventFilter(InputEvent.ANY, e -> ultimoUso = System.currentTimeMillis());
        vigilante = new Timeline(new KeyFrame(Duration.seconds(15), e -> {
            if (Sesion.actual() != null && System.currentTimeMillis() - ultimoUso > Programa1.INACTIVIDAD_MS) salir("Tu sesión terminó por inactividad.");
        }));
        vigilante.setCycleCount(Timeline.INDEFINITE);
        vigilante.play();
        stage.setTitle("Moscamed Admin · Programa 2");
    }

    @Override
    public void stop() {
        AccesoControlador.salir();
    }

    @Override
    public void ir(String pantalla, String arg) {
        VBox contenido = new VBox();
        contenido.getStyleClass().add("contenido");
        if (flash != null) {
            Label aviso = aviso(flash);
            aviso.setStyle("-fx-font-size: 15px;");
            contenido.getChildren().add(aviso);
            flash = null;
        }
        try {
            contenido.getChildren().add(switch (pantalla) {
                case "usuarios" -> usuarios.usuarios();
                case "nuevo" -> usuarios.nuevo(arg == null ? "PES" : arg);
                case "sistema" -> sistema.sistema();
                default -> throw new IllegalArgumentException(pantalla);
            });
        } catch (SQLException e) {
            contenido.getChildren().add(aviso(Conexion.mensaje(e), "error"));
        }
        marco.setLeft(new MenuAdminVista(this, () -> salir(null)).menu(pantalla));
        marco.setCenter(desplazable(contenido));
        if (scene.getRoot() != marco) scene.setRoot(marco);
    }

    @Override
    public void avisar(String texto) {
        flash = texto;
    }

    @Override
    public Scene escena() {
        return scene;
    }

    @Override
    public void detener() {
        vigilante.stop();
    }

    @Override
    public void salir(String mensaje) {
        AccesoControlador.salir();
        mostrarLogin(mensaje);
    }

    void mostrarLogin(String mensaje) {
        if (Selector.ventana != null) {
            Selector.volver(mensaje);
            return;
        }
        marco = new BorderPane();
        marco.getStyleClass().add("admin");
        LoginAdminVista login = new LoginAdminVista(this);
        scene.setRoot(login.raiz(mensaje));
        login.enfocar();
    }

    @Override
    public String entrar(String usuario, String clave) {
        var intento = acceso.entrarAdmin(usuario, clave);
        if (!intento.ok()) return intento.mensaje();
        ir("usuarios", null);
        return null;
    }
}
