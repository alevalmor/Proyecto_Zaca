package moscamed;

import static moscamed.Programa1.avisar;
import static moscamed.Programa1.esPes;
import static moscamed.Programa1.ir;
import static moscamed.Ui.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Ajustes · Mi perfil (11 del diseño). */
final class Perfil {
    private Perfil() {}

    static Node vista() throws SQLException {
        var yo = Db.q1("SELECT * FROM responsable WHERE id = ?", Db.responsableId);
        String sede = Reglas.PERFILES.get(Db.perfil)[1];
        var avatar = lbl("" + s(yo.get("nombre")).charAt(0) + s(yo.get("ap_paterno")).charAt(0), "avatar");
        HBox chips = new HBox(8, pill("Perfil " + Db.perfil, Db.perfil), pill(s(yo.get("num_empleado")), ""), pill(s(yo.get("usuario_bd")), ""));
        HBox cabecera = new HBox(18, avatar, new VBox(4, lbl(Db.nombre, "h2", "h2-grande"), lbl(yo.get("puesto") + " · " + sede, "gris"), chips));
        cabecera.setAlignment(Pos.CENTER_LEFT);

        List<Node[]> filas = new ArrayList<>();
        for (String[] v : Reglas.VISTAS) {
            boolean[] p = Db.permisos.get(v[0]);
            filas.add(new Node[]{t(v[1]), pill(p[0] ? "Sí" : "No", p[0] ? "si" : ""), pill(p[1] ? "Sí" : "No", p[1] ? "si" : "")});
        }

        VBox principal = new VBox(18, caja(cabecera),
                caja(h2("Datos personales"), datos(2, "Correo", yo.get("correo") == null ? "—" : s(yo.get("correo")),
                                "Teléfono", yo.get("telefono") == null ? "—" : s(yo.get("telefono")),
                                "Fecha de alta", fecha(yo.get("fecha_alta")), "Sede", sede),
                        lbl("Para cambiar estos datos o tu perfil, pídeselo al administrador (Programa 2).", "gris")),
                caja(h2("Mis permisos"), lbl("Los asigna el administrador según tu perfil", "gris", "chico"),
                        tabla(List.of("Vista", "Ver", "Modificar"), filas, Set.of(), "")));

        String[] servidor = Db.SERVIDORES.get(Db.sede);
        VBox lateral = new VBox(16, formularioClave(),
                caja(h2("Sesión"), renglon("Servidor", servidor[0] + ":" + servidor[1]), renglon("Inicio de sesión", Db.entrada),
                        renglon("Cierre por inactividad", "5 min")));
        return new VBox(18, titulo("Inicio / Ajustes / Perfil", "Mi perfil"), columnas(principal, lateral));
    }

    static Node renglon(String clave, String valor) {
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox h = new HBox(lbl(clave), hueco, lbl(valor, "mono"));
        h.getStyleClass().add("renglon");
        return h;
    }

    static Node formularioClave() {
        PasswordField actual = new PasswordField(), nueva = new PasswordField(), conf = new PasswordField();
        VBox errores = errores();
        var guardar = btn("Guardar contraseña", () -> {
            String error = null;
            if (!Db.claveCorrecta(actual.getText())) error = "La contraseña actual no coincide.";
            else if (!nueva.getText().equals(conf.getText())) error = "La nueva contraseña y su confirmación no coinciden.";
            else if (!Reglas.contrasenaValida(nueva.getText())) error = "Mínimo 10 caracteres, con número y símbolo.";
            if (error != null) {
                mostrarErrores(errores, List.of(error));
                return;
            }
            String orden = "ALTER ROLE CURRENT_USER PASSWORD " + Db.literal(nueva.getText());
            // Primero la otra sede: si no responde, no cambia nada (los roles no se replican).
            try (Connection otra = Db.conEn(Db.otraSede()); Statement st = otra.createStatement()) {
                st.execute(orden);
                try (Statement st2 = Db.con().createStatement()) {
                    st2.execute(orden);
                }
                Db.auditar("Cambió su contraseña");
            } catch (SQLException e) {
                mostrarErrores(errores, List.of("No se pudo cambiar en los dos servidores: " + Db.mensaje(e)));
                return;
            }
            Db.cambiarClaveGuardada(nueva.getText());
            avisar("Contraseña cambiada en los dos servidores.");
            ir("perfil");
        });
        guardar.setMaxWidth(Double.MAX_VALUE);
        return caja(h2("Cambiar contraseña"), errores, campoClave("Contraseña actual", actual, null),
                campoClave("Nueva contraseña", nueva, "Mínimo 10 caracteres, con número y símbolo"), campoClave("Confirmar contraseña", conf, null), guardar);
    }
}
