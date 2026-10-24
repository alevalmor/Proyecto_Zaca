package moscamed;

import static moscamed.Programa1.avisar;
import static moscamed.Programa1.ir;
import static moscamed.Programa1.puede;
import static moscamed.Ui.*;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Dispersión aérea: la encargada (perfil PED) captura el informe que le entrega la avioneta. */
final class Dispersion {
    private Dispersion() {}

    /** Clave de una existencia para pasarla entre pantallas: ORIGEN|código|PUNTO. */
    static String clave(Map<String, Object> e) {
        return e.get("origen") + "|" + e.get("codigo") + "|" + e.get("punto");
    }

    static String punto(Object punto) {
        return "AEROPUERTO".equals(punto) ? "Aeropuerto" : "Centro de empaque";
    }

    // ---------- Lista de informes y existencias ----------

    static Node lista() throws SQLException {
        TextField q = new TextField();
        q.setPromptText("Avioneta, zona, envío o embarque");
        ComboBox<String> lugar = Lotes.combo(List.of("Aeropuerto y centro de empaque", "Aeropuerto", "Centro de empaque"), null);
        VBox resultados = new VBox();
        int[] pagina = {1};

        Runnable[] cargar = new Runnable[1];
        cargar[0] = () -> {
            List<String> filtros = new ArrayList<>(List.of("true"));
            List<Object> p = new ArrayList<>();
            if (!q.getText().isBlank()) {
                filtros.add("(li.avioneta ILIKE ? OR z.clave ILIKE ? OR z.nombre ILIKE ? OR coalesce(e.codigo, b.codigo) ILIKE ?)");
                for (int i = 0; i < 4; i++) p.add("%" + q.getText().trim() + "%");
            }
            if (lugar.getSelectionModel().getSelectedIndex() > 0) {
                filtros.add("li.punto = ?");
                p.add(lugar.getSelectionModel().getSelectedIndex() == 1 ? "AEROPUERTO" : "EMPAQUE");
            }
            String desde = " FROM liberacion li JOIN zona_liberacion z ON z.id = li.zona_id"
                    + " LEFT JOIN envio e ON e.id = li.envio_id LEFT JOIN embarque_aereo b ON b.id = li.embarque_id WHERE "
                    + String.join(" AND ", filtros);
            try {
                long total = l(Db.q1("SELECT count(*) AS n" + desde, p.toArray()).get("n"));
                p.add(Lotes.POR_PAGINA);
                p.add((pagina[0] - 1) * Lotes.POR_PAGINA);
                List<Node[]> nodos = new ArrayList<>();
                for (var li : Db.q("SELECT li.*, z.clave, z.nombre, e.codigo AS envio, b.codigo AS embarque" + desde
                        + " ORDER BY li.fecha DESC LIMIT ? OFFSET ?", p.toArray())) {
                    String envio = s(li.get("envio"));
                    Node fuente = li.get("envio") != null ? link(envio, () -> ir("estado_envio", envio)) : mono(li.get("embarque"));
                    nodos.add(new Node[]{t(fechaHora(li.get("fecha"))), t(li.get("clave") + " · " + li.get("nombre")), fuente,
                            t(punto(li.get("punto"))), mono(li.get("avioneta")), t(li.get("piloto")), t(miles(li.get("cantidad"))),
                            t(miles(li.get("superficie_ha")) + " ha"), t(miles(li.get("altitud_m")) + " m")});
                }
                resultados.getChildren().setAll(tabla(List.of("Fecha", "Zona", "Moscas de", "Salieron de", "Avioneta", "Piloto", "Moscas",
                        "Superficie", "Altitud"), nodos, Set.of(6, 7, 8), "Todavía no hay informes de dispersión."),
                        Lotes.paginacion(pagina, total, cargar[0], null));
            } catch (SQLException e) {
                resultados.getChildren().setAll(aviso(Db.mensaje(e), "error"));
            }
        };
        cargar[0].run();
        Runnable filtrar = () -> { pagina[0] = 1; cargar[0].run(); };
        q.setOnAction(e -> filtrar.run());

        var k = Db.q1("""
                SELECT (SELECT coalesce(sum(cantidad), 0) FROM liberacion WHERE fecha >= date_trunc('month', current_date)) AS mes,
                       (SELECT count(*) FROM liberacion WHERE fecha >= date_trunc('month', current_date)) AS informes,
                       (SELECT coalesce(sum(quedan), 0) FROM v_existencia WHERE punto = 'EMPAQUE') AS empaque,
                       (SELECT coalesce(sum(quedan), 0) FROM v_existencia WHERE punto = 'AEROPUERTO') AS aeropuerto""");
        Node kpis = fila(14, kpi("Dispersadas este mes", millones(k.get("mes")), k.get("informes") + " informes de vuelo", null),
                kpi("Por dispersar en el centro", millones(k.get("empaque")), "envíos de Metapa y embarques", "ambar"),
                kpi("Por dispersar en el aeropuerto", millones(k.get("aeropuerto")), "embarques de Perú", "ambar"));

        List<Node[]> filas = new ArrayList<>();
        for (var e : Db.q("SELECT * FROM v_existencia WHERE quedan > 0 ORDER BY desde")) {
            String c = clave(e);
            filas.add(new Node[]{mono(e.get("codigo")), t(("ENVIO".equals(e.get("origen")) ? "Lote " : "Guía ") + e.get("referencia")),
                    t(punto(e.get("punto"))), t(fechaHora(e.get("desde"))), t(miles(e.get("recibidas"))), t(miles(e.get("quedan"))),
                    puede("nueva_dispersion", true) ? link("Dispersar", () -> ir("nueva_dispersion", c)) : t("")});
        }
        Node existencias = caja(h2("Moscas listas para dispersar"),
                tabla(List.of("Viene de", "Referencia", "Están en", "Desde", "Recibidas", "Quedan", ""), filas, Set.of(4, 5, 6),
                        "No hay moscas por dispersar: falta que lleguen envíos o embarques."));

        HBox filtros = new HBox(12, campo("Buscar", q, null), campo("Salieron de", lugar, null), btn("Filtrar", "borde", filtrar));
        filtros.setAlignment(Pos.BOTTOM_LEFT);
        HBox.setHgrow(filtros.getChildren().get(0), Priority.ALWAYS);
        var derecha = new HBox();
        if (puede("nueva_dispersion", true)) derecha.getChildren().add(btn("+  Nuevo informe", () -> ir("nueva_dispersion")));
        return new VBox(18, encabezado(titulo("Inicio / Dispersión aérea", "Informes de dispersión"), derecha),
                aviso("La dispersión es solo aérea. Cada informe es lo que reporta la avioneta: zona, moscas, superficie y altitud.", "azul"),
                kpis, existencias, caja(filtros), caja(resultados));
    }

    // ---------- Nuevo informe ----------

    static Node formulario(String elegida) throws SQLException {
        var existencias = Db.q("SELECT * FROM v_existencia WHERE quedan > 0 ORDER BY desde");
        var zonas = Zonas.prioridad(14, LocalDate.now());
        ComboBox<Map<String, Object>> fuente = new ComboBox<>();
        fuente.getItems().addAll(existencias);
        fuente.setConverter(Envios.convertidor(e -> e.get("codigo") + " · " + punto(e.get("punto")) + " · quedan " + miles(e.get("quedan"))));
        for (var e : existencias) if (clave(e).equals(elegida)) fuente.setValue(e);
        if (fuente.getValue() == null && !existencias.isEmpty()) fuente.setValue(existencias.get(0));
        ComboBox<Map<String, Object>> zona = new ComboBox<>();
        zona.getItems().addAll(zonas);
        zona.setConverter(Envios.convertidor(z -> z.get("clave") + " · " + z.get("nombre") + " (" + s(z.get("prioridad")).toLowerCase() + ")"));
        if (!zonas.isEmpty()) zona.setValue(zonas.get(0));

        TextField fecha = new TextField(LocalDateTime.now().format(Envios.FECHA_HORA));
        TextField cantidad = new TextField(fuente.getValue() == null ? "" : s(fuente.getValue().get("quedan")));
        fuente.valueProperty().addListener((o, a, b) -> cantidad.setText(b == null ? "" : s(b.get("quedan"))));
        TextField sup = new TextField(), alt = new TextField(), avioneta = new TextField(), piloto = new TextField();
        avioneta.setPromptText("XB-TWE");
        avioneta.getStyleClass().add("mono");
        TextArea obs = new TextArea();
        obs.setPrefRowCount(2);
        TextField resp = new TextField(Db.nombre + " · " + Db.empleado + " (usuario en sesión)");
        resp.setEditable(false);
        VBox errores = errores();

        var guardar = btn("Guardar informe", () -> {
            var f = fuente.getValue();
            List<String> errs = new ArrayList<>();
            LocalDateTime fl = null;
            long n = 0, altitud = 0;
            BigDecimal superficie = null;
            try {
                fl = Envios.leerFechaHora(fecha.getText());
                n = Reglas.entero(cantidad.getText());
                altitud = Reglas.entero(alt.getText());
                superficie = new BigDecimal(sup.getText().replace(",", "").trim());
            } catch (NumberFormatException | DateTimeParseException ex) {
                errs.add("Revisa la fecha (dd/mm/aaaa hh:mm) y que la cantidad, la superficie y la altitud sean números.");
            }
            if (f == null) errs.add("Elige de dónde salieron las moscas.");
            if (zona.getValue() == null) errs.add("Elige la zona.");
            if (avioneta.getText().isBlank()) errs.add("Falta la matrícula de la avioneta.");
            if (piloto.getText().isBlank()) errs.add("Falta el piloto que reporta.");
            if (errs.isEmpty()) errs.addAll(Reglas.erroresDispersion(n, l(f.get("quedan")), altitud));
            if (errs.isEmpty() && superficie.signum() <= 0) errs.add("La superficie debe ser mayor que cero.");
            mostrarErrores(errores, errs);
            if (!errs.isEmpty()) return;
            boolean deEnvio = "ENVIO".equals(f.get("origen"));
            LocalDateTime cuando = fl;
            long cant = n, altM = altitud;
            BigDecimal supHa = superficie;
            try {
                Db.tx(() -> {
                    Db.exec("""
                            INSERT INTO liberacion (envio_id, embarque_id, punto, zona_id, fecha, cantidad, superficie_ha, altitud_m,
                            avioneta, piloto, responsable_id, observaciones) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                            deEnvio ? f.get("id") : null, deEnvio ? null : f.get("id"), f.get("punto"), zona.getValue().get("id"), cuando,
                            cant, supHa, (int) altM, avioneta.getText().trim().toUpperCase(), piloto.getText().trim(), Db.responsableId,
                            obs.getText().isBlank() ? null : obs.getText().trim());
                    Db.auditar("Registró dispersión de " + f.get("codigo") + " en " + zona.getValue().get("clave"));
                });
            } catch (SQLException ex) {
                mostrarErrores(errores, List.of(Db.mensaje(ex)));
                return;
            }
            avisar("Informe de dispersión guardado.");
            ir("dispersiones");
        });

        VBox form = caja(errores);
        if (existencias.isEmpty()) form.getChildren().add(aviso("No hay moscas por dispersar: falta que el centro reciba un envío de Metapa o que llegue un embarque de Perú.", "ambar"));
        form.getChildren().addAll(campo("Moscas de", fuente, "Envío recibido de Metapa o embarque de Perú, y dónde están"),
                campos(2, campo("Zona", zona, "O elígela en las zonas sugeridas"), campo("Fecha y hora del vuelo", fecha, "dd/mm/aaaa hh:mm"),
                        campo("Moscas dispersadas", cantidad, null), campo("Superficie cubierta (ha)", sup, null),
                        campo("Altitud de vuelo (m s. n. m.)", alt, "Obligatoria: la dispersión es aérea"), campo("Avioneta (matrícula)", avioneta, null),
                        campo("Piloto", piloto, "Como viene en el informe de vuelo"), campo("Capturó", resp, null)),
                campo("Observaciones (opcional)", obs, null),
                accionesFin(btn("Cancelar", "gris", () -> ir("dispersiones")), guardar));
        form.setSpacing(18);

        VBox sugeridas = new VBox(10);
        for (var z : zonas.subList(0, Math.min(5, zonas.size()))) {
            sugeridas.getChildren().add(Inicio.item(new VBox(2, lbl(z.get("clave") + " · " + z.get("nombre"), "dato"),
                            lbl("MTD " + dec(z.get("mtd"), 2) + " · sugeridas " + millones(z.get("sugeridas")), "gris", "chico")),
                    pill(s(z.get("prioridad"))), null, () -> zona.setValue(z)));
        }
        VBox lateral = caja(h2("Zonas sugeridas por el trampeo"),
                lbl("Ordenadas por prioridad (base de trampeo vía FDW, últimos 14 días). Pulsa una para elegirla.", "gris", "chico"), sugeridas);
        return new VBox(20, encabezado(titulo("Inicio / Dispersión / Nuevo", "Nuevo informe de dispersión aérea"), lbl("Solo PED · dispersión", "chip")),
                columnas(form, lateral));
    }
}
