package moscamed.vista.operativo;

import static moscamed.vista.Componentes.*;
import static moscamed.vista.Formato.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import moscamed.controlador.PerfilControlador;
import moscamed.interfaces.vista.Vista;
import moscamed.modelo.Reglas;
import moscamed.modelo.Responsable;
import moscamed.modelo.Sesion;

public final class PerfilVista implements Vista {
    private final PerfilControlador control;
    private final Sesion sesion = Sesion.actual();
    private final Responsable yo;
    private final String servidor;

    public PerfilVista(PerfilControlador control, Responsable yo, String servidor) {
        this.control = control;
        this.yo = yo;
        this.servidor = servidor;
    }

    @Override
    public Node vista() {
        String sede = Reglas.PERFILES.get(sesion.perfil())[1];
        var avatar = lbl(yo.iniciales(), "avatar");
        HBox chips = new HBox(8, pill("Perfil " + sesion.perfil(), sesion.perfil()), pill(yo.numEmpleado(), ""), pill(yo.usuarioBd(), ""));
        HBox cabecera = new HBox(18, avatar, new VBox(4, lbl(sesion.nombre(), "h2", "h2-grande"), lbl(yo.puesto() + " · " + sede, "gris"), chips));
        cabecera.setAlignment(Pos.CENTER_LEFT);

        List<Node[]> filas = new ArrayList<>();
        for (String[] v : Reglas.VISTAS) {
            boolean[] p = sesion.permisos().get(v[0]);
            filas.add(new Node[]{t(v[1]), pill(p[0] ? "Sí" : "No", p[0] ? "si" : ""), pill(p[1] ? "Sí" : "No", p[1] ? "si" : "")});
        }

        VBox principal = new VBox(18, caja(cabecera),
                caja(h2("Datos personales"), datos(2, "Correo", oGuion(yo.correo()), "Teléfono", oGuion(yo.telefono()),
                                "Fecha de alta", fecha(yo.fechaAlta()), "Sede", sede),
                        lbl("Para cambiar estos datos o tu perfil, pídeselo al administrador (Programa 2).", "gris")),
                caja(h2("Mis permisos"), lbl("Los asigna el administrador según tu perfil", "gris", "chico"),
                        tabla(List.of("Vista", "Ver", "Modificar"), filas, Set.of(), "")));

        VBox lateral = new VBox(16, formularioClave(),
                caja(h2("Sesión"), renglon("Servidor", servidor), renglon("Inicio de sesión", sesion.entrada()),
                        renglon("Cierre por inactividad", "5 min")));
        return new VBox(18, titulo("Inicio / Ajustes / Perfil", "Mi perfil"), columnas(principal, lateral));
    }

    private Node formularioClave() {
        PasswordField actual = new PasswordField(), nueva = new PasswordField(), conf = new PasswordField();
        VBox errores = errores();
        var guardar = btn("Guardar contraseña", () -> mostrarErrores(errores, control.cambiarClave(actual.getText(), nueva.getText(), conf.getText())));
        guardar.setMaxWidth(Double.MAX_VALUE);
        return caja(h2("Cambiar contraseña"), errores, campoClave("Contraseña actual", actual, null),
                campoClave("Nueva contraseña", nueva, "Mínimo 10 caracteres, con número y símbolo"), campoClave("Confirmar contraseña", conf, null), guardar);
    }
}
