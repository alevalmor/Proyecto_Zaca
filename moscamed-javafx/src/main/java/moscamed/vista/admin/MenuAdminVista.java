package moscamed.vista.admin;

import static moscamed.vista.Componentes.*;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import moscamed.interfaces.controlador.Navegador;
import moscamed.interfaces.vista.Menu;
import moscamed.modelo.Sesion;

public final class MenuAdminVista implements Menu {
    private static final String[][] ENTRADAS = {{"usuarios", "Usuarios y perfiles"}, {"nuevo", "Nuevo usuario"}, {"sistema", "Estado del sistema"}};

    private final Navegador nav;
    private final Runnable salir;

    public MenuAdminVista(Navegador nav, Runnable salir) {
        this.nav = nav;
        this.salir = salir;
    }

    @Override
    public Node menu(String activo) {
        Sesion sesion = Sesion.actual();
        VBox menu = new VBox();
        menu.getStyleClass().add("menu");
        HBox marca = new HBox(10, escudo(30, "#BC955C"), new VBox(lbl("Moscamed Admin", "marca"), lbl("Programa 2 · permisos superiores", "sub")));
        marca.setAlignment(Pos.CENTER_LEFT);
        VBox items = new VBox(2, lbl("ACCESOS", "seccion"));
        VBox.setVgrow(items, Priority.ALWAYS);
        for (String[] e : ENTRADAS) {
            Button b = new Button(e[1]);
            b.getStyleClass().setAll("menu-item");
            if (e[0].equals(activo)) b.getStyleClass().add("activo");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setAlignment(Pos.CENTER_LEFT);
            b.setStyle("-fx-text-fill: " + (e[0].equals(activo) ? "white; -fx-font-weight: bold;" : "#dbe8df;") + " -fx-font-size: 15px;");
            b.setOnAction(x -> nav.ir(e[0], null));
            items.getChildren().add(b);
        }
        var cerrar = link("Cerrar sesión", salir);
        VBox pie = new VBox(lbl("Administrador de la base", "dato"), lbl(sesion.usuario() + " · " + sesion.sede().toLowerCase(), "sub", "mono"), cerrar);
        pie.getStyleClass().add("menu-pie");
        menu.getChildren().addAll(marca, items, pie);
        return menu;
    }
}
