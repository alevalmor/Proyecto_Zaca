package moscamed.controlador;

import static moscamed.vista.Componentes.aviso;
import static moscamed.vista.Componentes.desplazable;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.InputEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import moscamed.datos.Conexion;
import moscamed.datos.postgres.AccesoPostgres;
import moscamed.datos.postgres.DispersionPostgres;
import moscamed.datos.postgres.EnvioPostgres;
import moscamed.datos.postgres.LotePostgres;
import moscamed.interfaces.controlador.Programa;
import moscamed.interfaces.datos.AccesoDao;
import moscamed.interfaces.datos.DispersionDao;
import moscamed.interfaces.datos.EnvioDao;
import moscamed.interfaces.datos.LoteDao;
import moscamed.modelo.Sesion;
import moscamed.vista.acceso.LoginOperativoVista;
import moscamed.vista.operativo.MenuOperativoVista;

public class Programa1 extends Application implements Programa {
    static final long INACTIVIDAD_MS = 5 * 60 * 1000;

    static final Map<String, String[]> PANTALLAS = new HashMap<>(Map.of(
            "inicio", new String[]{null, "", "inicio"},
            "perfil", new String[]{null, "", "perfil"},
            "lotes", new String[]{"lotes", "", "lotes"},
            "nuevo_lote", new String[]{"nuevo_lote", "mod", "nuevo_lote"},
            "modificar_lote", new String[]{"lotes", "mod", "lotes"},
            "estado_lote", new String[]{"estado_lote", "", "estado_lote"},
            "envios", new String[]{"envios", "", "envios"},
            "nuevo_envio", new String[]{"nuevo_envio", "mod", "nuevo_envio"},
            "modificar_envio", new String[]{"envios", "mod", "envios"},
            "estado_envio", new String[]{"estado_envio", "", "estado_envio"}));
    static {
        PANTALLAS.put("dispersiones", new String[]{"dispersiones", "", "dispersiones"});
        PANTALLAS.put("nueva_dispersion", new String[]{"nueva_dispersion", "mod", "nueva_dispersion"});
        PANTALLAS.put("modificar_dispersion", new String[]{"dispersiones", "mod", "dispersiones"});
    }

    static Programa1 app;
    Scene scene;
    BorderPane marco;
    String flash;
    long ultimoUso = System.currentTimeMillis();
    Timeline vigilante;

    private final AccesoDao accesoDao = new AccesoPostgres();
    private final LoteDao loteDao = new LotePostgres();
    private final EnvioDao envioDao = new EnvioPostgres();
    private final DispersionDao dispersionDao = new DispersionPostgres();
    private final AccesoControlador acceso = new AccesoControlador(accesoDao);
    private final LoteControlador lotes = new LoteControlador(loteDao, this);
    private final EnvioControlador envios = new EnvioControlador(envioDao, this);
    private final DispersionControlador dispersion = new DispersionControlador(dispersionDao, this);
    private final InicioControlador inicio = new InicioControlador(loteDao, envioDao, dispersionDao, this);
    private final PerfilControlador perfil = new PerfilControlador(accesoDao, this);

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
        scene = new Scene(new StackPane(), 1280, 860);
        scene.getStylesheets().add(Programa1.class.getResource("/moscamed/estilos.css").toExternalForm());
        scene.addEventFilter(InputEvent.ANY, e -> ultimoUso = System.currentTimeMillis());
        vigilante = new Timeline(new KeyFrame(Duration.seconds(15), e -> {
            if (Sesion.actual() != null && System.currentTimeMillis() - ultimoUso > INACTIVIDAD_MS) salir("Tu sesión terminó por inactividad. Vuelve a iniciar sesión.");
        }));
        vigilante.setCycleCount(Timeline.INDEFINITE);
        vigilante.play();
        stage.setTitle("Moscamed · Programa 1");
    }

    @Override
    public void stop() {
        AccesoControlador.salir();
    }

    @Override
    public void ir(String pantalla, String arg) {
        Sesion sesion = Sesion.actual();
        if (sesion == null) {
            mostrarLogin(null);
            return;
        }
        String[] p = PANTALLAS.get(pantalla);
        if (!sesion.puede(p[0], p[1].equals("mod"))) {
            flash = "Tu perfil no tiene permiso para esa acción.";
            pantalla = "inicio";
            p = PANTALLAS.get(pantalla);
        }
        VBox contenido = new VBox();
        contenido.getStyleClass().add("contenido");
        if (flash != null) {
            contenido.getChildren().add(aviso(flash));
            flash = null;
        }
        try {
            Node vista = switch (pantalla) {
                case "inicio" -> switch (sesion.perfil()) {
                    case "PES" -> inicio.planta();
                    case "PEM" -> inicio.empaque();
                    default -> dispersion.lista();
                };
                case "lotes" -> lotes.lista();
                case "nuevo_lote" -> lotes.formulario(null);
                case "modificar_lote" -> lotes.formulario(arg);
                case "estado_lote" -> lotes.estado(arg);
                case "envios" -> envios.lista();
                case "nuevo_envio" -> envios.formulario(null);
                case "modificar_envio" -> envios.formulario(arg);
                case "estado_envio" -> envios.estado(arg);
                case "dispersiones" -> dispersion.lista();
                case "nueva_dispersion" -> dispersion.formulario(arg, null);
                case "modificar_dispersion" -> dispersion.formulario(null, arg);
                case "perfil" -> perfil.vista();
                default -> throw new IllegalArgumentException(pantalla);
            };
            if (vista == null) return;
            contenido.getChildren().add(vista);
        } catch (SQLException e) {
            if (Conexion.esDesconexion(e)) {
                salir("Se perdió la conexión con el servidor. Vuelve a iniciar sesión.");
                return;
            }
            contenido.getChildren().add(aviso(Conexion.mensaje(e), "error"));
        }
        marco.getStyleClass().remove("pem");
        if (sesion.esPem()) marco.getStyleClass().add("pem");
        marco.setLeft(new MenuOperativoVista(this, () -> salir(null)).menu(p[2]));
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
        LoginOperativoVista login = new LoginOperativoVista(this);
        scene.setRoot(login.raiz(mensaje));
        login.enfocar();
    }

    @Override
    public String entrar(String usuario, String clave) {
        var intento = acceso.entrarOperativo(usuario, clave);
        if (!intento.ok()) return intento.mensaje();
        if (intento.mensaje() != null) flash = intento.mensaje();
        ir("inicio");
        return null;
    }
}
