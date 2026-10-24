package moscamed;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Control;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Piezas visuales del diseño "Mockups Sistema Moscamed"; los estilos están en estilos.css. */
public final class Ui {
    private Ui() {}

    // ---------- Formatos ----------

    public static String miles(Object n) {
        return String.format(Locale.US, "%,d", n == null ? 0L : ((Number) n).longValue());
    }

    public static String millones(Object n) {
        double v = n == null ? 0 : ((Number) n).doubleValue();
        return String.format(Locale.US, "%,.1f M", v / 1e6);
    }

    public static String fecha(Object d) {
        if (d instanceof LocalDate f) return f.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        if (d instanceof LocalDateTime f) return f.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        return "—";
    }

    public static String fechaHora(Object d) {
        return d instanceof LocalDateTime f ? f.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "—";
    }

    public static String dec(Object n, int decimales) {
        return n == null ? "—" : String.format(Locale.US, "%." + decimales + "f", ((Number) n).doubleValue());
    }

    public static String s(Object o) {
        return o == null ? "" : o.toString();
    }

    public static long l(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }

    public static BigDecimal bd(Object o) {
        return o instanceof BigDecimal b ? b : new BigDecimal(s(o));
    }

    // ---------- Texto y etiquetas ----------

    public static Label lbl(String texto, String... clases) {
        Label l = new Label(texto);
        l.getStyleClass().addAll(clases);
        l.setWrapText(true);
        return l;
    }

    public static Label h1(String texto) {
        return lbl(texto, "h1");
    }

    public static Label h2(String texto) {
        return lbl(texto, "h2");
    }

    /** Píldora de estado; la clase se deriva del texto (EN CAMINO -> EN-CAMINO). */
    public static Label pill(String texto) {
        return pill(texto, texto.replace(' ', '-'));
    }

    public static Label pill(String texto, String clase) {
        Label l = lbl(texto, "pill");
        if (clase != null && !clase.isEmpty()) l.getStyleClass().add("pill-" + clase);
        l.setWrapText(false);
        l.setMinWidth(Region.USE_PREF_SIZE);
        return l;
    }

    public static Label aviso(String texto, String... clases) {
        Label l = lbl(texto, "aviso");
        for (String c : clases) l.getStyleClass().add("aviso-" + c);
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    /** Caja de errores que se llena al validar un formulario. */
    public static VBox errores() {
        VBox v = new VBox(4);
        v.getStyleClass().addAll("aviso", "aviso-error");
        v.setVisible(false);
        v.setManaged(false);
        return v;
    }

    public static void mostrarErrores(VBox caja, List<String> errores) {
        caja.getChildren().setAll(errores.stream().map(e -> (Node) lbl(e, "texto-error")).toList());
        caja.setVisible(!errores.isEmpty());
        caja.setManaged(!errores.isEmpty());
    }

    // ---------- Contenedores ----------

    public static VBox caja(Node... hijos) {
        VBox v = new VBox(12, hijos);
        v.getStyleClass().add("caja");
        return v;
    }

    public static VBox kpi(String titulo, Object valor, String detalle, String claseValor) {
        Label n = lbl(String.valueOf(valor), "kpi-n");
        n.setWrapText(false);
        if (claseValor != null) n.getStyleClass().add(claseValor);
        VBox v = new VBox(2, lbl(titulo, "kpi-t"), n);
        if (detalle != null) v.getChildren().add(lbl(detalle, "gris", "chico"));
        v.getStyleClass().add("kpi");
        HBox.setHgrow(v, Priority.ALWAYS);
        v.setMaxWidth(Double.MAX_VALUE);
        return v;
    }

    /** Fila de tarjetas con columnas del mismo ancho. */
    public static GridPane fila(double espacio, Node... hijos) {
        for (Node n : hijos) if (n instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
        GridPane g = campos(hijos.length, hijos);
        g.setHgap(espacio);
        return g;
    }

    /** Dos columnas: principal (3) y lateral (2), como en el diseño. */
    public static HBox columnas(Node principal, Node lateral) {
        HBox h = new HBox(18, principal, lateral);
        h.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(principal, Priority.ALWAYS);
        if (principal instanceof Region r) r.setPrefWidth(600);
        if (lateral instanceof Region r) {
            r.setPrefWidth(380);
            r.setMinWidth(320);
            r.setMaxWidth(420);
        }
        return h;
    }

    public static HBox encabezado(Node izquierda, Node... derecha) {
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox h = new HBox(12, izquierda, hueco);
        h.getChildren().addAll(derecha);
        h.setAlignment(Pos.BOTTOM_LEFT);
        return h;
    }

    public static VBox titulo(String ruta, String titulo) {
        return new VBox(2, lbl(ruta, "ruta"), h1(titulo));
    }

    public static FlowPane acciones(Node... hijos) {
        FlowPane f = new FlowPane(12, 12, hijos);
        f.setAlignment(Pos.CENTER_LEFT);
        return f;
    }

    public static HBox accionesFin(Node... hijos) {
        HBox h = new HBox(12, hijos);
        h.setAlignment(Pos.CENTER_RIGHT);
        h.getStyleClass().add("acciones-fin");
        return h;
    }

    public static ScrollPane desplazable(Node contenido) {
        ScrollPane sp = new ScrollPane(contenido);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("desplazable");
        return sp;
    }

    // ---------- Botones ----------

    public static Button btn(String texto, Runnable accion) {
        return btn(texto, "", accion);
    }

    /** clase: "" (relleno), "borde" o "gris". */
    public static Button btn(String texto, String clase, Runnable accion) {
        Button b = new Button(texto);
        b.getStyleClass().add("btn");
        if (!clase.isEmpty()) b.getStyleClass().add("btn-" + clase);
        b.setOnAction(e -> accion.run());
        b.setMinWidth(Region.USE_PREF_SIZE);
        return b;
    }

    public static Hyperlink link(String texto, Runnable accion) {
        Hyperlink h = new Hyperlink(texto);
        h.setOnAction(e -> accion.run());
        h.setMinWidth(Region.USE_PREF_SIZE);
        return h;
    }

    public static boolean confirmar(String pregunta) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, pregunta, ButtonType.YES, ButtonType.NO);
        a.setHeaderText(null);
        a.setTitle("Confirmar");
        return a.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    // ---------- Formularios ----------

    public static VBox campo(String etiqueta, Control control, String ayuda) {
        Label l = lbl(etiqueta, "campo-lbl");
        l.setLabelFor(control);
        control.setMaxWidth(Double.MAX_VALUE);
        VBox v = new VBox(6, l, control);
        if (ayuda != null) v.getChildren().add(lbl(ayuda, "gris", "chico"));
        return v;
    }

    /** Campo de contraseña con enlace "Ver contraseña"; el texto se lee siempre de clave. */
    public static VBox campoClave(String etiqueta, PasswordField clave, String ayuda) {
        TextField visible = new TextField();
        visible.textProperty().bindBidirectional(clave.textProperty());
        BooleanProperty mostrar = new SimpleBooleanProperty(false);
        Hyperlink ver = link("Ver contraseña", () -> mostrar.set(!mostrar.get()));
        ver.textProperty().bind(Bindings.when(mostrar).then("Ocultar contraseña").otherwise("Ver contraseña"));
        visible.visibleProperty().bind(mostrar);
        visible.managedProperty().bind(mostrar);
        clave.visibleProperty().bind(mostrar.not());
        clave.managedProperty().bind(mostrar.not());
        visible.getStyleClass().add("mono");
        visible.setAccessibleText(etiqueta);
        clave.setMaxWidth(Double.MAX_VALUE);
        visible.setMaxWidth(Double.MAX_VALUE);
        Label l = lbl(etiqueta, "campo-lbl");
        l.setLabelFor(clave);
        VBox v = new VBox(6, l, clave, visible, ver);
        if (ayuda != null) v.getChildren().add(lbl(ayuda, "gris", "chico"));
        return v;
    }

    /** Muestra el usuario y la contraseña recién creados para copiarlos; PostgreSQL no la guarda en claro. */
    public static void mostrarCredenciales(String titulo, String usuario, String clave) {
        TextField u = new TextField(usuario), c = new TextField(clave);
        for (TextField f : List.of(u, c)) {
            f.setEditable(false);
            f.getStyleClass().add("mono");
        }
        Button copiar = new Button("Copiar usuario y contraseña");
        copiar.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString("Usuario: " + usuario + "\nContraseña: " + clave);
            Clipboard.getSystemClipboard().setContent(cc);
            copiar.setText("Copiado");
        });
        VBox contenido = new VBox(10, lbl("Usuario"), u, lbl("Contraseña"), c, copiar,
                lbl("Anótala o cópiala ahora y entrégala en persona. Por seguridad no se guarda: si se pierde, usa \"Restablecer contraseña\".", "gris", "chico"));
        contenido.setPrefWidth(420);
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(titulo);
        a.setHeaderText(titulo);
        a.getDialogPane().setContent(contenido);
        a.showAndWait();
    }

    /** Rejilla de campos en n columnas del mismo ancho. */
    public static GridPane campos(int columnas, Node... hijos) {
        GridPane g = new GridPane();
        g.setHgap(16);
        g.setVgap(16);
        for (int i = 0; i < columnas; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(100.0 / columnas);
            c.setHgrow(Priority.ALWAYS);
            g.getColumnConstraints().add(c);
        }
        for (int i = 0; i < hijos.length; i++) g.add(hijos[i], i % columnas, i / columnas);
        return g;
    }

    // ---------- Tablas ----------

    /** Tabla ligera: celdas de texto o nodos (enlaces, píldoras). derecha = índices de columnas numéricas. */
    public static Node tabla(List<String> cabeceras, List<Node[]> filas, Set<Integer> derecha, String vacio) {
        GridPane g = new GridPane();
        g.getStyleClass().add("tabla");
        for (int c = 0; c < cabeceras.size(); c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setHgrow(Priority.SOMETIMES);
            if (derecha.contains(c)) cc.setHalignment(HPos.RIGHT);
            g.getColumnConstraints().add(cc);
            g.add(celda(lbl(cabeceras.get(c), "th"), "th-celda", derecha.contains(c)), c, 0);
        }
        if (filas.isEmpty()) {
            Label l = lbl(vacio, "gris");
            g.add(celda(l, "td-celda", false), 0, 1, cabeceras.size(), 1);
        }
        for (int f = 0; f < filas.size(); f++) {
            Node[] fila = filas.get(f);
            for (int c = 0; c < fila.length; c++) g.add(celda(fila[c], "td-celda", derecha.contains(c)), c, f + 1);
        }
        ScrollPane sp = new ScrollPane(g);
        sp.setFitToWidth(true);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.getStyleClass().add("desplazable");
        g.setMinWidth(Region.USE_PREF_SIZE);
        return sp;
    }

    private static HBox celda(Node contenido, String clase, boolean derecha) {
        HBox h = new HBox(contenido);
        h.getStyleClass().add(clase);
        h.setAlignment(derecha ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        h.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(h, Priority.ALWAYS);
        return h;
    }

    public static Node t(Object texto) {
        Label l = lbl(texto == null ? "—" : texto.toString());
        l.setWrapText(false);
        return l;
    }

    public static Node mono(Object texto) {
        Label l = lbl(s(texto), "mono");
        l.setWrapText(false);
        return l;
    }

    /** Varios nodos en una celda (por ejemplo, varios enlaces de acción). */
    public static Node juntos(Node... hijos) {
        HBox h = new HBox(12, hijos);
        h.setAlignment(Pos.CENTER_RIGHT);
        return h;
    }

    /** Lista de definiciones (dato: valor) en una rejilla. */
    public static GridPane datos(int columnas, String... paresClaveValor) {
        Node[] nodos = new Node[paresClaveValor.length / 2];
        for (int i = 0; i < nodos.length; i++) {
            nodos[i] = new VBox(2, lbl(paresClaveValor[2 * i], "gris"), lbl(paresClaveValor[2 * i + 1], "dato"));
        }
        GridPane g = campos(columnas, nodos);
        g.setVgap(14);
        return g;
    }
}
