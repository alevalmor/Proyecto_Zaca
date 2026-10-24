package moscamed;

import static moscamed.Programa1.ir;
import static moscamed.Programa1.puede;
import static moscamed.Ui.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/** Zonas prioritarias: cruza el trampeo (postgres_fdw) con las liberaciones (10 del diseño). */
final class Zonas {
    private Zonas() {}

    /** Zonas ordenadas por prioridad; la regla vive en la base (función zonas_prioritarias de esquema.sql). */
    static List<Map<String, Object>> prioridad(int dias, LocalDate corte) throws SQLException {
        return Db.q("SELECT * FROM zonas_prioritarias(?, ?)", corte, dias);
    }

    static Node vista() throws SQLException {
        DatePicker corte = new DatePicker(LocalDate.now());
        ToggleGroup periodo = new ToggleGroup();
        ToggleButton d14 = new ToggleButton("14 días"), d30 = new ToggleButton("30 días");
        d14.setUserData(14);
        d30.setUserData(30);
        d14.setToggleGroup(periodo);
        d30.setToggleGroup(periodo);
        d14.setSelected(true);
        d14.setMinWidth(Region.USE_PREF_SIZE);
        d30.setMinWidth(Region.USE_PREF_SIZE);
        HBox segmento = new HBox(d14, d30);
        segmento.getStyleClass().add("segmento");
        VBox resultados = new VBox(18);

        Runnable recargar = () -> {
            if (periodo.getSelectedToggle() == null) d14.setSelected(true);
            LocalDate fecha = corte.getValue() == null ? LocalDate.now() : corte.getValue();
            try {
                resultados.getChildren().setAll(resultados(fecha, (Integer) periodo.getSelectedToggle().getUserData()));
            } catch (SQLException e) {
                resultados.getChildren().setAll(aviso(Db.mensaje(e), "error"));
            }
        };
        corte.valueProperty().addListener((o, a, b) -> recargar.run());
        periodo.selectedToggleProperty().addListener((o, a, b) -> recargar.run());
        var exportar = btn("Exportar", "borde", () -> exportar(corte.getValue(), (Integer) periodo.getSelectedToggle().getUserData()));
        recargar.run();

        HBox filtros = new HBox(12, campo("Fecha de corte", corte, null), segmento, exportar);
        filtros.setAlignment(Pos.BOTTOM_LEFT);
        filtros.setFillHeight(false);
        VBox titulo = titulo("Inicio / Reportes", "Zonas prioritarias y liberaciones");
        titulo.getChildren().add(lbl("Cruza el trampeo (otra base de datos, leída en vivo con postgres_fdw) con lo que ya se liberó en cada zona", "gris"));
        return new VBox(18, encabezado(titulo, filtros), resultados);
    }

    private static List<Node> resultados(LocalDate corte, int dias) throws SQLException {
        var filas = prioridad(dias, corte);
        long altas = filas.stream().filter(z -> "ALTA".equals(z.get("prioridad"))).count();
        long medias = filas.stream().filter(z -> "MEDIA".equals(z.get("prioridad"))).count();
        Node kpis = fila(14,
                kpiPrioridad("Prioridad alta", "ALTA", altas, "Cayeron moscas silvestres: reforzar al doble"),
                kpiPrioridad("Prioridad media", "MEDIA", medias, "Sin capturas, pero sin liberar en el periodo"),
                kpiPrioridad("Protegidas", "NORMAL", filas.size() - altas - medias, "Sin capturas y con liberaciones al día"));

        List<Node[]> tabla = new ArrayList<>();
        for (var z : filas) {
            tabla.add(new Node[]{mono(z.get("clave")), t(z.get("municipio")), t(z.get("trampas")), t(z.get("silvestres")),
                    t(dec(z.get("mtd"), 4)), t(miles(z.get("liberadas"))), pill(s(z.get("prioridad"))), t(miles(z.get("sugeridas"))),
                    puede("nueva_dispersion", true) ? link("Dispersar", () -> ir("nueva_dispersion")) : t("")});
        }
        VBox zonas = caja(lbl("Zonas ordenadas por prioridad", "h2"),
                Ui.tabla(List.of("Zona", "Municipio", "Trampas", "Silvestres", "MTD", "Liberadas en el periodo", "Prioridad", "Sugeridas por semana", ""),
                        tabla, Set.of(2, 3, 4, 5, 7, 8), "No hay zonas registradas."),
                lbl("MTD = moscas silvestres por trampa por día. Sugeridas = superficie × densidad objetivo, al doble si la prioridad es alta.", "gris", "chico"));

        var barras = Db.q("""
                SELECT z.clave, z.municipio, sum(li.cantidad) AS total FROM liberacion li JOIN zona_liberacion z ON z.id = li.zona_id
                WHERE li.fecha::date <= ? GROUP BY z.id ORDER BY total DESC LIMIT 6""", corte);
        GridPane grafica = new GridPane();
        grafica.setHgap(10);
        grafica.setVgap(10);
        double tope = barras.isEmpty() ? 1 : l(barras.get(0).get("total"));
        for (int i = 0; i < barras.size(); i++) {
            var b = barras.get(i);
            Region barra = new Region();
            barra.getStyleClass().addAll("barra", "barra-hecho");
            barra.setMinHeight(18);
            barra.setPrefHeight(18);
            StackPane fondo = new StackPane(barra);
            fondo.getStyleClass().add("barra");
            fondo.setMinHeight(18);
            fondo.setAlignment(Pos.CENTER_LEFT);
            barra.maxWidthProperty().bind(fondo.widthProperty().multiply(l(b.get("total")) / tope));
            GridPane.setHgrow(fondo, Priority.ALWAYS);
            grafica.addRow(i, lbl(b.get("clave") + " · " + b.get("municipio")), fondo, lbl(millones(b.get("total"))));
        }
        VBox liberaciones = caja(lbl("Liberaciones por zona", "h2"),
                lbl("Millones de moscas liberadas hasta el " + fecha(corte) + ", las 6 zonas con más moscas", "gris", "chico"),
                barras.isEmpty() ? lbl("Aún no hay liberaciones.", "gris") : grafica);
        return List.of(kpis, zonas, liberaciones);
    }

    private static Node kpiPrioridad(String titulo, String prioridad, long n, String detalle) {
        VBox k = kpi(titulo, n + " zonas", detalle, null);
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        k.getChildren().set(0, new HBox(lbl(titulo, "kpi-t"), hueco, pill(prioridad)));
        return k;
    }

    private static void exportar(LocalDate corte, int dias) {
        LocalDate fecha = corte == null ? LocalDate.now() : corte;
        FileChooser fc = new FileChooser();
        fc.setInitialFileName("zonas_" + fecha + "_" + dias + "d.csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        var archivo = fc.showSaveDialog(null);
        if (archivo == null) return;
        try (PrintWriter w = new PrintWriter(archivo, StandardCharsets.UTF_8)) {
            w.print('﻿'); // para que Excel lea los acentos
            w.println("zona,municipio,trampas,silvestres,mtd,liberadas,prioridad,sugeridas_semana");
            for (var z : prioridad(dias, fecha)) {
                w.println(String.join(",", s(z.get("clave")), "\"" + s(z.get("municipio")).replace("\"", "\"\"") + "\"",
                        s(z.get("trampas")), s(z.get("silvestres")), dec(z.get("mtd"), 4), s(z.get("liberadas")),
                        s(z.get("prioridad")), s(z.get("sugeridas"))));
            }
            Programa1.avisar("Reporte exportado a " + archivo.getName() + ".");
        } catch (IOException | SQLException e) {
            Programa1.avisar("No se pudo exportar: " + e.getMessage());
        }
        ir("zonas");
    }
}
