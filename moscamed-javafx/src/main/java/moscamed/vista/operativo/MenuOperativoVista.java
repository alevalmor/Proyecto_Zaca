package moscamed.vista.operativo;

import static moscamed.vista.Componentes.*;

import java.util.Arrays;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import moscamed.interfaces.controlador.Navegador;
import moscamed.interfaces.vista.Menu;
import moscamed.modelo.Reglas;
import moscamed.modelo.Sesion;

public final class MenuOperativoVista implements Menu {
    private static final Object[][] MENU = {
            {"General", new String[][]{{"inicio", "Inicio"}}},
            {"Lotes", new String[][]{{"nuevo_lote", "Nuevo lote"}, {"lotes", "Lista de lotes"}, {"estado_lote", "Estado de lote"}}},
            {"Metapa → Tapachula", new String[][]{{"nuevo_envio", "Nuevo envío"}, {"envios", "Lista de envíos"}, {"estado_envio", "Estado de envío"}}},
            {"Dispersión aérea", new String[][]{{"nueva_dispersion", "Nuevo informe"}, {"dispersiones", "Informes"}}},
            {"Ajustes", new String[][]{{"perfil", "Mi perfil"}}},
    };

    private final Navegador nav;
    private final Runnable salir;
    private final Sesion sesion = Sesion.actual();

    public MenuOperativoVista(Navegador nav, Runnable salir) {
        this.nav = nav;
        this.salir = salir;
    }

    @Override
    public Node menu(String activo) {
        VBox menu = new VBox();
        menu.getStyleClass().add("menu");
        String[] datos = Reglas.PERFILES.get(sesion.perfil());
        HBox marca = new HBox(10, logo(30, sesion.esPem() ? "#BC955C" : "#9fd3b2"), new VBox(lbl("Moscamed", "marca"), lbl(datos[1], "sub")));
        marca.setAlignment(Pos.CENTER_LEFT);
        VBox perfil = new VBox(lbl("Perfil " + sesion.perfil(), "dato"), lbl(datos[0], "sub"));
        perfil.getStyleClass().add("menu-perfil");
        VBox items = new VBox(2);
        VBox.setVgrow(items, Priority.ALWAYS);
        for (Object[] seccion : MENU) {
            List<String[]> visibles = Arrays.stream((String[][]) seccion[1]).filter(e -> sesion.puede(e[0], false)).toList();
            if (visibles.isEmpty()) continue;
            items.getChildren().add(lbl(((String) seccion[0]).toUpperCase(), "seccion"));
            for (String[] e : visibles) items.getChildren().add(entrada(e[0], e[1], e[0].equals(activo)));
        }
        var cerrar = link("Cerrar sesión", salir);
        VBox pie = new VBox(lbl(sesion.nombre(), "dato"), lbl(sesion.empleado() + " · " + sesion.usuario(), "sub", "mono"), cerrar);
        pie.getStyleClass().add("menu-pie");
        menu.getChildren().addAll(marca, perfil, items, pie);
        return menu;
    }

    private Button entrada(String vista, String etiqueta, boolean activo) {
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox contenido = new HBox(8, new Label(etiqueta), hueco);
        contenido.setAlignment(Pos.CENTER_LEFT);
        if (!vista.equals("inicio") && !vista.equals("perfil")) {
            boolean mod = sesion.puede(vista, true);
            Label tag = lbl(mod ? "Modificar" : "Solo ver", "tag");
            tag.setWrapText(false);
            tag.setMinWidth(Region.USE_PREF_SIZE);
            if (!mod) tag.getStyleClass().add("tag-ver");
            contenido.getChildren().add(tag);
        }
        Button b = new Button();
        b.setGraphic(contenido);
        contenido.prefWidthProperty().bind(b.widthProperty().subtract(20));
        b.getStyleClass().setAll("menu-item");
        if (activo) b.getStyleClass().add("activo");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setOnAction(e -> nav.ir(vista));
        return b;
    }
}
