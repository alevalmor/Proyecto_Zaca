package moscamed;

import static moscamed.Ui.*;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.InputEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;

/** Programa 2 · Administrador: el único que crea perfiles y accesos al Programa 1. */
public class Programa2 extends Application {

    static final List<String> TABLAS = List.of("responsable", "centro_empaque", "lote_produccion", "control_calidad", "vehiculo",
            "zona_liberacion", "envio", "embarque_aereo", "liberacion", "historial");
    /** Puestos que se ofrecen al crear un usuario de cada perfil. */
    static final Map<String, List<String>> PUESTOS = Map.of(
            "PES", List.of("Técnico de irradiación", "Analista de control de calidad", "Técnico de cría", "Supervisor de turno"),
            "PEM", List.of("Jefe de empaque", "Técnico de empaque", "Supervisor de turno"),
            "PEA", List.of("Encargado de aeropuerto", "Técnico de recepción de embarques"),
            "PED", List.of("Encargada de dispersión", "Auxiliar de dispersión"));
    static final SecureRandom AZAR = new SecureRandom();

    static Programa2 app;
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
        var vigilante = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.seconds(15), e -> {
            if (Db.sede != null && System.currentTimeMillis() - ultimoUso > Programa1.INACTIVIDAD_MS) salir("Tu sesión terminó por inactividad.");
        }));
        vigilante.setCycleCount(javafx.animation.Timeline.INDEFINITE);
        vigilante.play();
        stage.setTitle("Moscamed Admin · Programa 2");
        stage.setScene(scene);
        mostrarLogin(null);
        stage.show();
    }

    @Override
    public void stop() {
        Db.cerrar();
    }

    static String claveTemporal() {
        String letras = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder("Moscamed#");
        for (int i = 0; i < 8; i++) sb.append(letras.charAt(AZAR.nextInt(letras.length())));
        return sb.append(AZAR.nextInt(10)).toString();
    }

    /** Los roles no se replican: cada orden va a los dos servidores, primero a la otra sede para que,
     *  si no responde, no quede cambiado solo uno. */
    static void enAmbos(String orden) throws SQLException {
        try (Connection otra = Db.conEn(Db.otraSede()); Statement st = otra.createStatement()) {
            st.execute(orden);
        }
        try (Statement st = Db.con().createStatement()) {
            st.execute(orden);
        }
    }

    // ---------- Navegación ----------

    void ir(String pantalla, String perfil) {
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
                case "usuarios" -> usuarios();
                case "nuevo" -> nuevoUsuario(perfil == null ? "PES" : perfil);
                case "sistema" -> sistema();
                default -> throw new IllegalArgumentException(pantalla);
            });
        } catch (SQLException e) {
            contenido.getChildren().add(aviso(Db.mensaje(e), "error"));
        }
        marco.setLeft(menu(pantalla));
        marco.setCenter(desplazable(contenido));
        if (scene.getRoot() != marco) scene.setRoot(marco);
    }

    void salir(String mensaje) {
        Db.cerrar();
        mostrarLogin(mensaje);
    }

    static Node escudo(double tam, String color) {
        SVGPath p = new SVGPath();
        p.setContent("M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z M9 12l2 2 4-4");
        p.setFill(Color.TRANSPARENT);
        p.setStroke(Color.web(color));
        p.setStrokeWidth(1.8);
        Group g = new Group(p);
        g.setScaleX(tam / 24);
        g.setScaleY(tam / 24);
        return new Group(g);
    }

    Node menu(String activo) {
        VBox menu = new VBox();
        menu.getStyleClass().add("menu");
        HBox marca = new HBox(10, escudo(30, "#a8b8d8"), new VBox(lbl("Moscamed Admin", "marca"), lbl("Programa 2 · permisos superiores", "sub")));
        marca.setAlignment(Pos.CENTER_LEFT);
        VBox items = new VBox(2, lbl("ACCESOS", "seccion"));
        VBox.setVgrow(items, Priority.ALWAYS);
        for (String[] e : new String[][]{{"usuarios", "Usuarios y perfiles"}, {"nuevo", "Nuevo usuario"}, {"sistema", "Estado del sistema"}}) {
            Button b = new Button(e[1]);
            b.getStyleClass().setAll("menu-item");
            if (e[0].equals(activo)) b.getStyleClass().add("activo");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setAlignment(Pos.CENTER_LEFT);
            b.setStyle("-fx-text-fill: " + (e[0].equals(activo) ? "white; -fx-font-weight: bold;" : "#d5dcea;") + " -fx-font-size: 15px;");
            b.setOnAction(x -> ir(e[0], null));
            items.getChildren().add(b);
        }
        var cerrar = link("Cerrar sesión", () -> salir(null));
        VBox pie = new VBox(lbl("Administrador de la base", "dato"), lbl(Db.usuario + " · " + Db.sede.toLowerCase(), "sub", "mono"), cerrar);
        pie.getStyleClass().add("menu-pie");
        menu.getChildren().addAll(marca, items, pie);
        return menu;
    }

    // ---------- Inicio de sesión ----------

    void mostrarLogin(String mensaje) {
        marco = new BorderPane();
        marco.getStyleClass().add("admin");
        TextField usuario = new TextField();
        usuario.getStyleClass().add("mono");
        PasswordField clave = new PasswordField();
        VBox errores = errores();
        Button entrar = btn("Entrar al panel", () -> {});
        entrar.setDefaultButton(true);
        entrar.setMaxWidth(Double.MAX_VALUE);
        entrar.setOnAction(e -> {
            String u = usuario.getText().trim();
            try {
                var primero = Db.conectarCualquiera(u, clave.getText());
                String s = primero.getKey();
                Connection con = primero.getValue();
                boolean admin = (Boolean) Db.q(con, "SELECT pg_has_role(current_user, 'rol_admin', 'member') AS si").get(0).get("si");
                if (!admin) {
                    con.close();
                    mostrarErrores(errores, List.of("Este usuario no es administrador."));
                    return;
                }
                Db.abrir(s, u, clave.getText(), con);
                ir("usuarios", null);
            } catch (SQLException ex) {
                mostrarErrores(errores, List.of(Db.errorConexion(ex, "ningún servidor")));
            }
        });
        HBox marca = new HBox(12, escudo(40, "#34507f"), new VBox(lbl("Moscamed Admin", "h2", "h2-grande"), lbl("Programa 2 · administración de accesos", "gris")));
        marca.setAlignment(Pos.CENTER_LEFT);
        VBox form = new VBox(marca, lbl("Solo para el administrador. Desde aquí se crean los usuarios y perfiles (PES, PEM, PEA y PED) que entran al Programa 1.", "gris"));
        form.getStyleClass().add("form-login");
        if (mensaje != null) form.getChildren().add(aviso(mensaje, "azul"));
        form.getChildren().addAll(errores, campo("Usuario administrador", usuario, null), campoClave("Contraseña", clave, null),
                entrar,
                lbl("Acceso limitado a 5 conexiones · cada acción queda en la bitácora de auditoría", "gris", "chico"));
        StackPane raiz = new StackPane(form);
        raiz.getStyleClass().add("admin");
        raiz.setStyle("-fx-background-color: #1c2433; -fx-padding: 48 16 48 16;");
        scene.setRoot(raiz);
        usuario.requestFocus();
    }

    // ---------- Usuarios y perfiles ----------

    Node usuarios() throws SQLException {
        TextField q = new TextField();
        q.setPromptText("Nombre, MET-… o TAP-…");
        ComboBox<String> perfil = Lotes.combo(List.of("Todos", "PES · planta", "PEM · empaque", "PEA · aeropuerto", "PED · dispersión"), null);
        ComboBox<String> estado = Lotes.combo(List.of("Activos y de baja", "Activos", "De baja"), null);
        VBox resultados = new VBox();
        int[] pagina = {1};
        Runnable[] cargar = new Runnable[1];
        cargar[0] = () -> {
            List<String> filtros = new ArrayList<>(List.of("true"));
            List<Object> p = new ArrayList<>();
            if (!q.getText().isBlank()) {
                filtros.add("(nombre || ' ' || ap_paterno || ' ' || coalesce(ap_materno, '') ILIKE ? OR num_empleado ILIKE ?)");
                p.add("%" + q.getText().trim() + "%");
                p.add("%" + q.getText().trim() + "%");
            }
            if (perfil.getSelectionModel().getSelectedIndex() > 0) { filtros.add("perfil = ?"); p.add(perfil.getValue().substring(0, 3)); }
            if (estado.getSelectionModel().getSelectedIndex() > 0) { filtros.add("activo = ?"); p.add(estado.getSelectionModel().getSelectedIndex() == 1); }
            String donde = String.join(" AND ", filtros);
            try {
                long total = l(Db.q1("SELECT count(*) AS n FROM responsable WHERE " + donde, p.toArray()).get("n"));
                List<Object> pp = new ArrayList<>(p);
                pp.add(Lotes.POR_PAGINA);
                pp.add((pagina[0] - 1) * Lotes.POR_PAGINA);
                List<Node[]> filas = new ArrayList<>();
                for (var u : Db.q("SELECT * FROM responsable WHERE " + donde + " ORDER BY activo DESC, perfil, num_empleado LIMIT ? OFFSET ?", pp.toArray())) {
                    boolean activo = (Boolean) u.get("activo");
                    String pf = s(u.get("perfil")), login = s(u.get("usuario_bd"));
                    int id = (Integer) u.get("id");
                    filas.add(new Node[]{t((u.get("nombre") + " " + u.get("ap_paterno") + " " + s(u.get("ap_materno"))).trim()), mono(u.get("num_empleado")),
                            t(u.get("puesto")), pill(pf), mono(login), pill(activo ? "Activo" : "De baja", activo ? "Activo" : ""),
                            juntos(link("Restablecer contraseña", () -> accion(id, login, "clave")),
                                    link(activo ? "Dar de baja" : "Reactivar", () -> accion(id, login, activo ? "baja" : "reactivar")))});
                }
                resultados.getChildren().setAll(tabla(List.of("Nombre", "Empleado", "Puesto", "Perfil", "Usuario de acceso", "Estado", "Acciones"),
                        filas, Set.of(6), "Sin resultados."),
                        Lotes.paginacion(pagina, total, cargar[0], " · los usuarios dados de baja no pueden registrar nada"));
            } catch (SQLException e) {
                resultados.getChildren().setAll(aviso(Db.mensaje(e), "error"));
            }
        };
        cargar[0].run();
        Runnable filtrar = () -> { pagina[0] = 1; cargar[0].run(); };
        q.setOnAction(e -> filtrar.run());

        var r = Db.q1("""
                SELECT count(*) FILTER (WHERE perfil = 'PES') AS pes, count(*) FILTER (WHERE perfil = 'PEM') AS pem,
                       count(*) FILTER (WHERE perfil = 'PEA') AS pea, count(*) FILTER (WHERE perfil = 'PED') AS ped,
                       count(*) FILTER (WHERE NOT activo) AS bajas FROM responsable""");
        HBox filtros = new HBox(12, campo("Buscar nombre o número de empleado", q, null), campo("Perfil", perfil, null), campo("Estado", estado, null),
                btn("Filtrar", "borde", filtrar));
        filtros.setAlignment(Pos.BOTTOM_LEFT);
        HBox.setHgrow(filtros.getChildren().get(0), Priority.ALWAYS);
        return new VBox(18, encabezado(titulo("Programa 2 · Administrador", "Usuarios y perfiles"), btn("+  Nuevo usuario", () -> ir("nuevo", null))),
                fila(14, kpi("PES · planta", r.get("pes"), null, null), kpi("PEM · empaque", r.get("pem"), null, null),
                        kpi("PEA · aeropuerto", r.get("pea"), null, null), kpi("PED · dispersión", r.get("ped"), null, null),
                        kpi("Dados de baja", r.get("bajas"), null, null)),
                caja(filtros), caja(resultados));
    }

    void accion(int id, String login, String accion) {
        String pregunta = switch (accion) {
            case "clave" -> "¿Restablecer la contraseña de " + login + "?";
            case "baja" -> "¿Dar de baja a " + login + "? Ya no podrá entrar.";
            default -> "¿Reactivar a " + login + "?";
        };
        if (!confirmar(pregunta)) return;
        try {
            if (accion.equals("clave")) {
                String clave = claveTemporal();
                enAmbos("ALTER ROLE " + Db.ident(login) + " PASSWORD " + Db.literal(clave));
                Db.auditar("Restableció la contraseña de " + login);
                flash = "Contraseña temporal de " + login + ": " + clave + " — entrégasela en persona.";
                mostrarCredenciales("Nueva contraseña de " + login, login, clave);
            } else {
                boolean activo = accion.equals("reactivar");
                enAmbos("ALTER ROLE " + Db.ident(login) + (activo ? " LOGIN" : " NOLOGIN"));
                Db.exec("UPDATE responsable SET activo = ? WHERE id = ?", activo, id);
                Db.auditar((activo ? "Reactivó a " : "Dio de baja a ") + login);
                flash = login + (activo ? " reactivado." : " dado de baja.");
            }
        } catch (SQLException e) {
            flash = Db.mensaje(e);
        }
        ir("usuarios", null);
    }

    // ---------- Nuevo usuario ----------

    Node nuevoUsuario(String perfil) {
        String sede = Reglas.sede(perfil), prefijo = sede.equals("PLANTA") ? "MET-" : "TAP-", rol = Reglas.PERFILES.get(perfil)[3];
        ToggleGroup grupo = new ToggleGroup();
        String[][] opciones = {{"PES", "PES · Esterilizador", "Planta Metapa · lotes y su calidad"},
                {"PEM", "PEM · Empacador", "Centro de empaque · traslados desde Metapa"},
                {"PEA", "PEA · Aeropuerto", "Aeropuerto Tapachula · embarques de Perú"},
                {"PED", "PED · Dispersión", "Encargada de los informes de la avioneta"}};
        Node[] botones = new Node[opciones.length];
        for (int i = 0; i < opciones.length; i++) {
            ToggleButton b = Programa1.opcion(opciones[i][1], opciones[i][2], opciones[i][0], grupo);
            b.setSelected(opciones[i][0].equals(perfil));
            botones[i] = b;
        }
        grupo.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) a.setSelected(true);
            else if (!b.getUserData().equals(perfil)) ir("nuevo", (String) b.getUserData());
        });

        TextField nombre = new TextField(), paterno = new TextField(), materno = new TextField(), tel = new TextField(), correo = new TextField();
        ComboBox<String> puesto = Lotes.combo(PUESTOS.get(perfil), null);
        TextField empleado = new TextField(prefijo + "…");
        empleado.setEditable(false);
        TextField usuario = new TextField();
        usuario.setPromptText("i.coutino");
        usuario.getStyleClass().add("mono");
        PasswordField clave = new PasswordField();
        clave.setText(claveTemporal());
        var generar = link("Generar otra contraseña", () -> clave.setText(claveTemporal()));

        GridPane permisos = new GridPane();
        permisos.setHgap(24);
        permisos.setVgap(10);
        permisos.addRow(0, lbl("Vista", "th"), lbl("Ver", "th"), lbl("Modificar", "th"));
        Map<String, CheckBox[]> marcas = new LinkedHashMap<>();
        int fila = 1;
        for (String[] v : Reglas.VISTAS) {
            boolean[] max = Reglas.PERMISOS_PERFIL.get(perfil).get(v[0]);
            CheckBox ver = new CheckBox(), mod = new CheckBox();
            ver.setSelected(max[0]);
            ver.setDisable(!max[0]);
            mod.setSelected(max[1]);
            mod.setDisable(!max[1]);
            ver.setAccessibleText("Ver " + v[1]);
            mod.setAccessibleText("Modificar " + v[1]);
            marcas.put(v[0], new CheckBox[]{ver, mod});
            permisos.addRow(fila++, lbl(v[1]), ver, mod);
        }

        Label sql = lbl("", "mono");
        Runnable vista = () -> sql.setText("CREATE ROLE " + (usuario.getText().isBlank() ? "usuario" : usuario.getText().trim())
                + " LOGIN\n  PASSWORD '********'\n  CONNECTION LIMIT 30\n  IN ROLE " + rol + ";");
        usuario.textProperty().addListener((o, a, b) -> vista.run());
        vista.run();
        VBox codigo = new VBox(sql);
        codigo.getStyleClass().add("codigo");

        VBox errores = errores();
        var crear = btn("Crear usuario", () -> {
            String u = usuario.getText().trim().toLowerCase();
            List<String> errs = new ArrayList<>();
            if (!Reglas.usuarioBdValido(u)) errs.add("El usuario va en minúsculas, empieza con letra y solo lleva letras, números, punto o guion bajo.");
            if (nombre.getText().isBlank()) errs.add("Falta el nombre.");
            if (paterno.getText().isBlank()) errs.add("Falta el apellido paterno.");
            if (!Reglas.contrasenaValida(clave.getText())) errs.add("La contraseña lleva mínimo 10 caracteres, con número y símbolo.");
            mostrarErrores(errores, errs);
            if (!errs.isEmpty()) return;
            String contrasena = clave.getText();
            try {
                enAmbos("CREATE ROLE " + Db.ident(u) + " LOGIN PASSWORD " + Db.literal(contrasena) + " CONNECTION LIMIT 30 IN ROLE " + Db.ident(rol));
            } catch (SQLException e) {
                mostrarErrores(errores, List.of("No se pudo crear el usuario en los dos servidores: " + Db.mensaje(e)));
                return;
            }
            String[] num = new String[1];
            try {
                Db.tx(() -> {
                    long n = l(Db.q1("SELECT coalesce(max(substr(num_empleado, 5)::int), 0) + 1 AS n FROM responsable WHERE num_empleado LIKE ?",
                            prefijo + "%").get("n"));
                    num[0] = prefijo + String.format("%04d", n);
                    int rid = (Integer) Db.q1("""
                            INSERT INTO responsable (num_empleado, nombre, ap_paterno, ap_materno, puesto, telefono, correo, sede, perfil, usuario_bd)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""",
                            num[0], nombre.getText().trim(), paterno.getText().trim(), vacio(materno), puesto.getValue(), vacio(tel), vacio(correo),
                            sede, perfil, u).get("id");
                    // Solo se guardan las vistas donde el administrador quitó algo.
                    for (var e : marcas.entrySet()) {
                        boolean[] max = Reglas.PERMISOS_PERFIL.get(perfil).get(e.getKey());
                        boolean ver = e.getValue()[0].isSelected(), mod = e.getValue()[1].isSelected();
                        if ((max[0] && !ver) || (max[1] && !mod)) Db.exec("INSERT INTO permiso_vista VALUES (?, ?, ?, ?)", rid, e.getKey(), ver, mod);
                    }
                    Db.auditar("Creó el usuario " + u + " (" + perfil + ")");
                });
            } catch (SQLException e) {
                mostrarErrores(errores, List.of(Db.mensaje(e) + " El usuario de PostgreSQL ya se creó; bórralo con DROP ROLE antes de reintentar."));
                return;
            }
            flash = "Usuario " + u + " creado (" + num[0] + "). Contraseña: " + contrasena + " — entrégasela en persona.";
            mostrarCredenciales("Usuario " + u + " creado", u, contrasena);
            ir("usuarios", null);
        });

        VBox form = new VBox(18,
                caja(h2("1. Perfil"), campos(2, botones)),
                caja(h2("2. Datos de la persona"), campos(3, campo("Nombre(s)", nombre, null), campo("Apellido paterno", paterno, null),
                        campo("Apellido materno", materno, null), campo("Puesto", puesto, null), campo("Teléfono", tel, null), campo("Correo", correo, null))),
                caja(h2("3. Acceso"), campos(3, campo("Número de empleado", empleado, "Se asigna el siguiente al guardar"),
                        campo("Usuario", usuario, null), new VBox(4, campoClave("Contraseña", clave, "Mínimo 10 caracteres, con número y símbolo"), generar)),
                        lbl("Al guardar se muestran el usuario y la contraseña para copiarlos y entregarlos. PostgreSQL solo guarda la contraseña cifrada: "
                                + "después ya no se puede ver, solo restablecer.", "gris", "chico")),
                caja(h2("4. Permisos en el Programa 1"),
                        lbl("Se llenan solos con el perfil. Puedes quitar permisos, pero no dar más de los que permite la sede en la base de datos.", "gris", "chico"),
                        permisos),
                errores,
                accionesFin(btn("Cancelar", "gris", () -> ir("usuarios", null)), crear));
        VBox lateral = new VBox(16, caja(h2("Lo que se crea en la base"),
                lbl("• Un registro en responsable con sede " + sede), lbl("• Un usuario de PostgreSQL dentro de " + rol),
                lbl("• El mismo usuario en los dos servidores, porque los roles no se replican"), lbl("• Una entrada en la bitácora de auditoría")), codigo);
        return new VBox(18, titulo("Usuarios / Nuevo", "Nuevo usuario y accesos"), columnas(form, lateral));
    }

    static String vacio(TextField f) {
        return f.getText().isBlank() ? null : f.getText().trim();
    }

    // ---------- Estado del sistema ----------

    Node sistema() throws SQLException {
        Map<String, Map<String, Object>> sedes = new LinkedHashMap<>();
        for (var e : Db.SERVIDORES.entrySet()) {
            String sede = e.getKey();
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("host", e.getValue()[0] + ":" + e.getValue()[1]);
            boolean local = sede.equals(Db.sede);
            Connection con = null;
            try {
                con = local ? Db.con() : Db.conEn(sede);
                info.put("publica", Db.q(con, "SELECT pubname FROM pg_publication").stream().map(r -> s(r.get("pubname"))).toList());
                info.put("suscripciones", Db.q(con, "SELECT subname, pid IS NOT NULL AS activa FROM pg_stat_subscription WHERE relid IS NULL"));
                info.put("envia", Db.q(con, "SELECT application_name, pg_wal_lsn_diff(sent_lsn, replay_lsn) AS retraso FROM pg_stat_replication"));
                Map<String, Long> conteos = new LinkedHashMap<>();
                for (String t : TABLAS) conteos.put(t, l(Db.q(con, "SELECT count(*) AS n FROM " + t).get(0).get("n")));
                info.put("conteos", conteos);
                try {
                    info.put("pgpool", Db.poolNodes(sede));
                } catch (SQLException sinPgpool) {
                    info.put("pgpool", null); // conectado directo a PostgreSQL, no por Pgpool-II
                }
                try {
                    info.put("trampas", l(Db.q(con, "SELECT count(DISTINCT trampa) AS n FROM trampeo.revision").get(0).get("n")));
                } catch (SQLException sinFdw) {
                    info.put("trampas", null);
                }
                info.put("ok", true);
            } catch (SQLException ex) {
                info.put("ok", false);
                info.put("error", ex.getMessage().lines().findFirst().orElse(""));
            } finally {
                if (!local && con != null) con.close();
            }
            sedes.put(sede, info);
        }

        List<Node> tarjetas = new ArrayList<>();
        for (var e : sedes.entrySet()) {
            var s = e.getValue();
            boolean ok = (Boolean) s.get("ok"), planta = e.getKey().equals("PLANTA");
            HBox cab = new HBox(lbl(planta ? "Planta Metapa" : "Centro de empaque Tapachula", "dato"));
            javafx.scene.layout.Region hueco = new javafx.scene.layout.Region();
            HBox.setHgrow(hueco, Priority.ALWAYS);
            cab.getChildren().addAll(hueco, pill(ok ? "EN LÍNEA" : "SIN CONEXIÓN", ok ? "APROBADO" : "RECHAZADO"));
            VBox t = new VBox(4, cab, lbl(s.get("host") + " · ids " + (planta ? "impares" : "pares"), "gris", "mono", "chico"));
            if (ok) {
                List<?> pubs = (List<?>) s.get("publica");
                t.getChildren().add(lbl("Publica: " + (pubs.isEmpty() ? "ninguna" : String.join(", ", pubs.stream().map(Object::toString).toList()))));
                @SuppressWarnings("unchecked") var subs = (List<Map<String, Object>>) s.get("suscripciones");
                if (subs.isEmpty()) t.getChildren().add(lbl("Sin suscripciones: no recibe datos de la otra sede"));
                for (var sub : subs) t.getChildren().add(lbl("Suscripción " + sub.get("subname") + ": " + ((Boolean) sub.get("activa") ? "activa" : "detenida")));
                @SuppressWarnings("unchecked") var envia = (List<Map<String, Object>>) s.get("envia");
                for (var r : envia) t.getChildren().add(lbl("Envía a " + r.get("application_name") + " · " + miles(r.get("retraso")) + " bytes de retraso"));
            } else {
                t.getChildren().add(lbl(s(s.get("error")), "rojo"));
            }
            t.getStyleClass().addAll("tarjeta", planta ? "tarjeta-verde" : "tarjeta-azul");
            tarjetas.add(t);
        }

        VBox servicios = new VBox(10);
        for (var e : sedes.entrySet()) {
            var s = e.getValue();
            if (!(Boolean) s.get("ok")) continue;
            String sede = e.getKey().toLowerCase();
            @SuppressWarnings("unchecked") var nodos = (List<Map<String, Object>>) s.get("pgpool");
            if (nodos == null) {
                servicios.getChildren().add(Inicio.item(new VBox(2, lbl("Pgpool " + sede, "dato"),
                        lbl("Conexión directa a PostgreSQL, no pasa por Pgpool-II", "gris", "chico")), pill("SIN DATOS", ""), null, null));
            } else {
                for (var n : nodos) {
                    String st = s(n.get("status"));
                    servicios.getChildren().add(Inicio.item(new VBox(2, lbl("Pgpool " + sede + " · nodo " + n.get("node_id"), "dato"),
                                    lbl(n.get("hostname") + ":" + n.get("port") + " · " + n.get("role"), "gris", "chico")),
                            pill(st.toUpperCase(), st.equals("up") || st.equals("waiting") ? "APROBADO" : "RECHAZADO"), null, null));
                }
            }
            Object trampas = s.get("trampas");
            servicios.getChildren().add(Inicio.item(new VBox(2, lbl("FDW base de trampeo · " + sede, "dato"),
                            lbl(trampas != null ? trampas + " trampas leídas" : "No se pudo leer trampeo.revision", "gris", "chico")),
                    pill(trampas != null ? "CONECTADO" : "FALLA", trampas != null ? "APROBADO" : "RECHAZADO"), null, null));
        }

        List<Node[]> conteo = new ArrayList<>();
        var p = sedes.get("PLANTA");
        var em = sedes.get("EMPAQUE");
        for (String t : TABLAS) {
            conteo.add(new Node[]{mono(t), t((Boolean) p.get("ok") ? ((Map<?, ?>) p.get("conteos")).get(t) : "—"),
                    t((Boolean) em.get("ok") ? ((Map<?, ?>) em.get("conteos")).get(t) : "—")});
        }
        boolean iguales = (Boolean) p.get("ok") && (Boolean) em.get("ok") && p.get("conteos").equals(em.get("conteos"));
        Label coinciden = lbl(iguales ? "Las dos sedes tienen los mismos datos" : "Las sedes no coinciden: revisa la replicación", "dato");
        coinciden.setStyle("-fx-text-fill: " + (iguales ? "#145232;" : "#9b1c1c;"));

        List<Node[]> bitacora = new ArrayList<>();
        for (var b : Db.q("SELECT * FROM auditoria ORDER BY fecha DESC LIMIT 10")) {
            bitacora.add(new Node[]{t(fechaHora(b.get("fecha"))), mono(b.get("usuario")), t(b.get("accion"))});
        }
        return new VBox(18,
                encabezado(titulo("Programa 2 · Administrador", "Estado del sistema"), btn("Actualizar", "gris", () -> ir("sistema", null))),
                caja(h2("Replicación master-master"), fila(14, tarjetas.toArray(Node[]::new))),
                fila(18, caja(h2("Pgpool-II y FDW"), servicios),
                        caja(h2("Registros por tabla"), tabla(List.of("Tabla", "Planta", "Empaque"), conteo, Set.of(1, 2), ""), coinciden)),
                caja(h2("Bitácora de auditoría reciente"), tabla(List.of("Fecha", "Usuario", "Acción"), bitacora, Set.of(), "Sin movimientos.")));
    }
}
