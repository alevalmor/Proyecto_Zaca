package moscamed.vista.admin;

import static moscamed.vista.Componentes.*;

import java.util.LinkedHashMap;
import java.util.Map;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import moscamed.controlador.UsuarioControlador;
import moscamed.controlador.UsuarioControlador.EntradaUsuario;
import moscamed.interfaces.controlador.Navegador;
import moscamed.interfaces.vista.Vista;
import moscamed.modelo.NuevoUsuario;
import moscamed.modelo.Reglas;

public final class NuevoUsuarioVista implements Vista {
    private final UsuarioControlador control;
    private final Navegador nav;
    private final String perfil;

    public NuevoUsuarioVista(UsuarioControlador control, Navegador nav, String perfil) {
        this.control = control;
        this.nav = nav;
        this.perfil = perfil;
    }

    @Override
    public Node vista() {
        String sede = Reglas.sede(perfil), prefijo = sede.equals("PLANTA") ? "MET-" : "TAP-", rol = Reglas.PERFILES.get(perfil)[3];
        ToggleGroup grupo = new ToggleGroup();
        String[][] opciones = {{"PES", NuevoUsuario.TITULOS.get("PES"), "Planta Metapa · lotes y su calidad"},
                {"PEM", NuevoUsuario.TITULOS.get("PEM"), "Centro de empaque · traslados desde Metapa"}};
        Node[] botones = new Node[opciones.length];
        for (int i = 0; i < opciones.length; i++) {
            ToggleButton b = opcion(opciones[i][1], opciones[i][2], opciones[i][0], grupo);
            b.setSelected(opciones[i][0].equals(perfil));
            botones[i] = b;
        }
        grupo.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) a.setSelected(true);
            else if (!b.getUserData().equals(perfil)) nav.ir("nuevo", (String) b.getUserData());
        });

        TextField nombre = new TextField(), paterno = new TextField(), materno = new TextField(), tel = new TextField(), correo = new TextField();
        TextField empleado = fijo(prefijo + "…");
        TextField usuario = new TextField();
        usuario.setPromptText("i.coutino");
        usuario.getStyleClass().add("mono");
        PasswordField clave = new PasswordField();
        clave.setText(Reglas.claveTemporal());
        var generar = link("Generar otra contraseña", () -> clave.setText(Reglas.claveTemporal()));

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
            Map<String, boolean[]> marcados = new LinkedHashMap<>();
            marcas.forEach((v, c) -> marcados.put(v, new boolean[]{c[0].isSelected(), c[1].isSelected()}));
            mostrarErrores(errores, control.crear(new EntradaUsuario(perfil, nombre.getText(), paterno.getText(), materno.getText(),
                    tel.getText(), correo.getText(), usuario.getText(), clave.getText(), marcados)));
        });

        VBox form = new VBox(18,
                caja(h2("1. Perfil"), campos(2, botones)),
                caja(h2("2. Datos de la persona"), campos(3, campo("Nombre(s)", nombre, null), campo("Apellido paterno", paterno, null),
                        campo("Apellido materno", materno, null), campo("Teléfono", tel, null), campo("Correo", correo, null))),
                caja(h2("3. Acceso"), campos(3, campo("Número de empleado", empleado, "Se asigna el siguiente al guardar"),
                                campo("Usuario", usuario, null), new VBox(4, campoClave("Contraseña", clave, "Mínimo 10 caracteres, con número y símbolo"), generar)),
                        lbl("Al guardar se muestran el usuario y la contraseña para copiarlos y entregarlos. PostgreSQL solo guarda la contraseña cifrada: "
                                + "después ya no se puede ver, solo restablecer.", "gris", "chico")),
                caja(h2("4. Permisos en el Programa 1"),
                        lbl("Se llenan solos con el perfil. Puedes quitar permisos, pero no dar más de los que permite la sede en la base de datos.", "gris", "chico"),
                        permisos),
                errores,
                accionesFin(btn("Cancelar", "gris", () -> nav.ir("usuarios", null)), crear));
        VBox lateral = new VBox(16, caja(h2("Lo que se crea en la base"),
                lbl("• Un registro en responsable con sede " + sede), lbl("• Un usuario de PostgreSQL dentro de " + rol),
                lbl("• El mismo usuario en los dos servidores, porque los roles no se replican"), lbl("• Una entrada en la bitácora de auditoría")), codigo);
        return new VBox(18, titulo("Usuarios / Nuevo", "Nuevo usuario y accesos"), columnas(form, lateral));
    }
}
