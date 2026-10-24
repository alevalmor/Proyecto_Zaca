package moscamed;

import static moscamed.Ui.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.InputEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import javafx.util.Duration;

/** Programa 1 · Operativo: planta esterilizadora (PES) y centro de empaque (PEM). */
public class Programa1 extends Application {

    static final long INACTIVIDAD_MS = 5 * 60 * 1000; // el diseño pide cierre a los 5 min sin uso

    /** Secciones del menú: {vista, etiqueta}. */
    static final Object[][] MENU = {
            {"General", new String[][]{{"inicio", "Inicio"}}},
            {"Lotes", new String[][]{{"nuevo_lote", "Nuevo lote"}, {"lotes", "Lista de lotes"}, {"estado_lote", "Estado de lote"}}},
            {"Metapa → Tapachula", new String[][]{{"nuevo_envio", "Nuevo envío"}, {"envios", "Lista de envíos"}, {"estado_envio", "Estado de envío"}}},
            {"Aeropuerto Tapachula", new String[][]{{"nuevo_embarque", "Nuevo embarque"}, {"embarques", "Embarques"}}},
            {"Dispersión aérea", new String[][]{{"nueva_dispersion", "Nuevo informe"}, {"dispersiones", "Informes"}}},
            {"Reportes", new String[][]{{"zonas", "Zonas prioritarias"}}},
            {"Ajustes", new String[][]{{"perfil", "Mi perfil"}}},
    };
    /** Pantalla -> {permiso que pide, "mod" si pide modificar, entrada del menú que queda activa}. */
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
        PANTALLAS.put("zonas", new String[]{"zonas", "", "zonas"});
        PANTALLAS.put("embarques", new String[]{"embarques", "", "embarques"});
        PANTALLAS.put("nuevo_embarque", new String[]{"nuevo_embarque", "mod", "nuevo_embarque"});
        PANTALLAS.put("dispersiones", new String[]{"dispersiones", "", "dispersiones"});
        PANTALLAS.put("nueva_dispersion", new String[]{"nueva_dispersion", "mod", "nueva_dispersion"});
    }

    static Programa1 app;
    Scene scene;
    BorderPane marco;
    String flash;
    long ultimoUso = System.currentTimeMillis();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        app = this;
        scene = new Scene(new StackPane(), 1280, 860);
        scene.getStylesheets().add(getClass().getResource("estilos.css").toExternalForm());
        scene.addEventFilter(InputEvent.ANY, e -> ultimoUso = System.currentTimeMillis());
        Timeline vigilante = new Timeline(new KeyFrame(Duration.seconds(15), e -> {
            if (Db.sede != null && System.currentTimeMillis() - ultimoUso > INACTIVIDAD_MS) salir("Tu sesión terminó por inactividad. Vuelve a iniciar sesión.");
        }));
        vigilante.setCycleCount(Timeline.INDEFINITE);
        vigilante.play();
        stage.setTitle("Moscamed · Programa 1");
        stage.setScene(scene);
        mostrarLogin(null);
        stage.show();
    }

    @Override
    public void stop() {
        Db.cerrar();
    }

    // ---------- Permisos y navegación ----------

    public static boolean puede(String vista, boolean modificar) {
        if (vista == null || vista.equals("inicio") || vista.equals("perfil")) return true;
        boolean[] p = Db.permisos.getOrDefault(vista, new boolean[]{false, false});
        return modificar ? p[1] : p[0];
    }

    public static boolean esPes() {
        return "PES".equals(Db.perfil);
    }

    /** Abre una pantalla; arg es el código del lote o envío cuando aplica. */
    public static void ir(String pantalla, String arg) {
        app.navegar(pantalla, arg);
    }

    public static void ir(String pantalla) {
        ir(pantalla, null);
    }

    /** Muestra un aviso arriba de la siguiente pantalla. */
    public static void avisar(String texto) {
        app.flash = texto;
    }

    void navegar(String pantalla, String arg) {
        if (Db.sede == null) {
            mostrarLogin(null);
            return;
        }
        String[] p = PANTALLAS.get(pantalla);
        if (!puede(p[0], p[1].equals("mod"))) {
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
                case "inicio" -> switch (Db.perfil) {
                    case "PES" -> Inicio.pes();
                    case "PEM" -> Inicio.pem();
                    case "PEA" -> Embarques.lista();
                    default -> Dispersion.lista();
                };
                case "lotes" -> Lotes.lista();
                case "nuevo_lote" -> Lotes.formulario(null);
                case "modificar_lote" -> Lotes.formulario(arg);
                case "estado_lote" -> Lotes.estado(arg);
                case "envios" -> Envios.lista();
                case "nuevo_envio" -> Envios.formulario(null);
                case "modificar_envio" -> Envios.formulario(arg);
                case "estado_envio" -> Envios.estado(arg);
                case "embarques" -> Embarques.lista();
                case "nuevo_embarque" -> Embarques.formulario();
                case "dispersiones" -> Dispersion.lista();
                case "nueva_dispersion" -> Dispersion.formulario(arg);
                case "zonas" -> Zonas.vista();
                case "perfil" -> Perfil.vista();
                default -> throw new IllegalArgumentException(pantalla);
            };
            if (vista == null) return; // la pantalla mandó a otra (por ejemplo, si aún no hay lotes)
            contenido.getChildren().add(vista);
        } catch (SQLException e) {
            if (e.getSQLState() != null && e.getSQLState().startsWith("08")) {
                salir("Se perdió la conexión con el servidor. Vuelve a iniciar sesión.");
                return;
            }
            contenido.getChildren().add(aviso(Db.mensaje(e), "error"));
        }
        marco.setLeft(menu(p[2]));
        marco.setCenter(desplazable(contenido));
        if (scene.getRoot() != marco) scene.setRoot(marco);
    }

    void salir(String mensaje) {
        Db.cerrar();
        mostrarLogin(mensaje);
    }

    // ---------- Menú lateral ----------

    static Node logo(double tam, String color) {
        Circle c = new Circle(20, 20, 17, Color.TRANSPARENT);
        c.setStroke(Color.web(color));
        c.setStrokeWidth(2.4);
        SVGPath alas = new SVGPath();
        alas.setContent("M12 22c4-8 12-8 16 0 M20 10v20");
        alas.setFill(Color.TRANSPARENT);
        alas.setStroke(Color.web(color));
        alas.setStrokeWidth(2.4);
        Group g = new Group(c, alas);
        g.setScaleX(tam / 40);
        g.setScaleY(tam / 40);
        return new Group(g);
    }

    Node menu(String activo) {
        VBox menu = new VBox();
        menu.getStyleClass().add("menu");
        String[] datos = Reglas.PERFILES.get(Db.perfil);
        HBox marca = new HBox(10, logo(30, "#9fd3b2"), new VBox(lbl("Moscamed", "marca"), lbl(datos[1], "sub")));
        marca.setAlignment(Pos.CENTER_LEFT);
        VBox perfil = new VBox(lbl("Perfil " + Db.perfil, "dato"), lbl(datos[0], "sub"));
        perfil.getStyleClass().add("menu-perfil");
        VBox items = new VBox(2);
        VBox.setVgrow(items, Priority.ALWAYS);
        for (Object[] seccion : MENU) {
            List<String[]> visibles = java.util.Arrays.stream((String[][]) seccion[1]).filter(e -> puede(e[0], false)).toList();
            if (visibles.isEmpty()) continue;
            items.getChildren().add(lbl(((String) seccion[0]).toUpperCase(), "seccion"));
            for (String[] e : visibles) items.getChildren().add(entradaMenu(e[0], e[1], e[0].equals(activo)));
        }
        var cerrar = link("Cerrar sesión", () -> salir(null));
        VBox pie = new VBox(lbl(Db.nombre, "dato"), lbl(Db.empleado + " · " + Db.usuario, "sub", "mono"), cerrar);
        pie.getStyleClass().add("menu-pie");
        menu.getChildren().addAll(marca, perfil, items, pie);
        return menu;
    }

    static Button entradaMenu(String vista, String etiqueta, boolean activo) {
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox contenido = new HBox(8, new Label(etiqueta), hueco);
        contenido.setAlignment(Pos.CENTER_LEFT);
        if (!vista.equals("inicio") && !vista.equals("perfil")) {
            boolean mod = puede(vista, true);
            Label tag = lbl(mod ? "Modificar" : "Solo ver", "tag");
            tag.setWrapText(false);
            tag.setMinWidth(Region.USE_PREF_SIZE); // con fuentes más anchas (Linux) se recorta el nombre, no la etiqueta
            if (!mod) tag.getStyleClass().add("tag-ver");
            contenido.getChildren().add(tag);
        }
        Button b = new Button();
        b.setGraphic(contenido);
        contenido.prefWidthProperty().bind(b.widthProperty().subtract(20));
        b.getStyleClass().setAll("menu-item");
        if (activo) b.getStyleClass().add("activo");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setOnAction(e -> ir(vista));
        return b;
    }

    // ---------- Inicio de sesión ----------

    void mostrarLogin(String mensaje) {
        marco = new BorderPane();
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

        TextField usuario = new TextField();
        usuario.getStyleClass().add("mono");
        PasswordField clave = new PasswordField();
        VBox errores = errores();
        Button entrar = btn("Entrar", () -> {
        });
        entrar.setMaxWidth(Double.MAX_VALUE);
        entrar.setDefaultButton(true);
        entrar.setOnAction(e -> entrar(usuario.getText().trim(), clave.getText(), errores));

        VBox form = new VBox(lbl("Iniciar sesión", "h1"), lbl("Entra con el usuario y la contraseña que te dio el administrador.", "gris"));
        form.getStyleClass().add("form-login");
        if (mensaje != null) form.getChildren().add(aviso(mensaje, "azul"));
        form.getChildren().addAll(errores, campo("Usuario", usuario, null), campoClave("Contraseña", clave, null), entrar,
                aviso("¿No tienes usuario? Pídeselo al administrador.", "gris"));
        StackPane lado = new StackPane(form);
        lado.setStyle("-fx-padding: 48 24 48 24;");
        HBox raiz = new HBox(portada, lado);
        HBox.setHgrow(lado, Priority.ALWAYS);
        scene.setRoot(raiz);
        usuario.requestFocus();
    }

    static ToggleButton opcion(String titulo, String detalle, Object dato, ToggleGroup grupo) {
        ToggleButton t = new ToggleButton();
        Label a = lbl(titulo, "titulo"), b = lbl(detalle, "gris", "chico");
        a.setWrapText(false); // con ajuste de línea el botón calcula su alto como si midiera 0 px de ancho
        b.setWrapText(false);
        t.setGraphic(new VBox(2, a, b));
        t.getStyleClass().setAll("opcion");
        t.setUserData(dato);
        t.setToggleGroup(grupo);
        t.setMaxWidth(Double.MAX_VALUE);
        return t;
    }

    /** El usuario no elige sede ni perfil: entra al servidor que responda, lee su perfil y se cambia al servidor de su sede. */
    void entrar(String usuario, String clave, VBox errores) {
        try {
            var primero = Db.conectarCualquiera(usuario, clave);
            String sede = primero.getKey();
            Connection con = primero.getValue();
            var yo = Db.q(con, "SELECT * FROM responsable WHERE usuario_bd = current_user");
            String error = null;
            if (yo.isEmpty()) error = "Tu usuario no tiene perfil en el sistema. Pídeselo al administrador.";
            else if (!(Boolean) yo.get(0).get("activo")) error = "Tu usuario está dado de baja.";
            if (error != null) {
                con.close();
                mostrarErrores(errores, List.of(error));
                return;
            }
            var r = yo.get(0);
            if (!sede.equals(r.get("sede"))) { // cada sede escribe en su propio servidor
                try {
                    Connection propia = Db.conectar(s(r.get("sede")), usuario, clave);
                    con.close();
                    con = propia;
                    sede = s(r.get("sede"));
                } catch (SQLException caido) {
                    if (!String.valueOf(caido.getSQLState()).startsWith("08")) throw caido;
                    // Como el failover de Pgpool: se trabaja en el otro servidor y la replicación lo lleva de vuelta.
                    flash = "El servidor de tu sede no responde: estás trabajando en el de la otra sede. Tus datos se copian solos cuando regrese.";
                }
            }
            Map<String, boolean[]> quitados = new HashMap<>();
            for (var q : Db.q(con, "SELECT * FROM permiso_vista WHERE responsable_id = ?", r.get("id"))) {
                quitados.put((String) q.get("vista"), new boolean[]{(Boolean) q.get("ver"), (Boolean) q.get("modificar")});
            }
            Db.abrir(sede, usuario, clave, con);
            Db.perfil = s(r.get("perfil"));
            Db.responsableId = (Integer) r.get("id");
            Db.nombre = (r.get("nombre") + " " + r.get("ap_paterno") + " " + s(r.get("ap_materno"))).trim();
            Db.nombreCorto = s(r.get("nombre")).split(" ")[0];
            Db.empleado = s(r.get("num_empleado"));
            Db.permisos = Reglas.permisos(Db.perfil, quitados);
            ir("inicio");
        } catch (SQLException e) {
            mostrarErrores(errores, List.of(Db.errorConexion(e, "ningún servidor")));
        }
    }
}
