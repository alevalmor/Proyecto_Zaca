package moscamed;

import static moscamed.Programa1.avisar;
import static moscamed.Programa1.esPes;
import static moscamed.Programa1.ir;
import static moscamed.Programa1.puede;
import static moscamed.Ui.*;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Envíos y liberaciones: los registra el centro de empaque (07, 08 y 09 del diseño). */
final class Envios {
    private Envios() {}

    static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    static LocalDateTime leerFechaHora(String texto) {
        return LocalDateTime.parse(texto.trim(), FECHA_HORA);
    }

    // ---------- Lista ----------

    static Node lista() throws SQLException {
        TextField q = new TextField();
        q.setPromptText("TAP-ENV-… o MET-2026-…");
        q.getStyleClass().add("mono");
        ComboBox<String> estado = Lotes.combo(List.of("Todos", "EN CAMINO", "RECIBIDO", "DISPERSADO", "CANCELADO"), null);
        VBox resultados = new VBox();
        int[] pagina = {1};

        Runnable[] cargar = new Runnable[1];
        cargar[0] = () -> {
            List<String> filtros = new ArrayList<>(List.of("true"));
            List<Object> p = new ArrayList<>();
            if (!q.getText().isBlank()) {
                filtros.add("(e.codigo ILIKE ? OR l.codigo ILIKE ?)");
                p.add("%" + q.getText().trim() + "%");
                p.add("%" + q.getText().trim() + "%");
            }
            if (estado.getSelectionModel().getSelectedIndex() > 0) { filtros.add("e.estado = ?"); p.add(estado.getValue()); }
            String desde = " FROM v_envio e JOIN lote_produccion l ON l.id = e.lote_id JOIN vehiculo v ON v.id = e.vehiculo_id WHERE "
                    + String.join(" AND ", filtros);
            try {
                long total = l(Db.q1("SELECT count(*) AS n" + desde, p.toArray()).get("n"));
                List<Object> pp = new ArrayList<>(p);
                pp.add(Lotes.POR_PAGINA);
                pp.add((pagina[0] - 1) * Lotes.POR_PAGINA);
                var filas = Db.q("SELECT e.*, l.codigo AS lote, v.matricula" + desde + " ORDER BY e.fecha_salida DESC LIMIT ? OFFSET ?", pp.toArray());
                List<Node[]> nodos = new ArrayList<>();
                for (var e : filas) {
                    String codigo = s(e.get("codigo")), est = s(e.get("estado"));
                    List<Node> acc = new ArrayList<>(List.of(link("Ver", () -> ir("estado_envio", codigo))));
                    if (est.equals("EN CAMINO") && puede("estado_envio", true)) acc.add(link("Recibir", () -> ir("estado_envio", codigo)));
                    if (est.equals("EN CAMINO") && puede("envios", true)) acc.add(link("Modificar", () -> ir("modificar_envio", codigo)));
                    nodos.add(new Node[]{mono(codigo), mono(e.get("lote")), mono(e.get("matricula")),
                            t(fechaHora(e.get("fecha_salida"))), t(fechaHora(e.get("recibido"))), t(miles(e.get("cantidad"))), t(e.get("temperatura")), pill(est),
                            juntos(acc.toArray(Node[]::new))});
                }
                resultados.getChildren().setAll(tabla(List.of("Envío", "Lote", "Camioneta", "Salida de Metapa", "Llegada a Tapachula", "Moscas", "°C", "Estado", "Acciones"),
                        nodos, Set.of(5, 6, 8), "Sin resultados."), Lotes.paginacion(pagina, total, cargar[0], null));
            } catch (SQLException ex) {
                resultados.getChildren().setAll(aviso(Db.mensaje(ex), "error"));
            }
        };
        cargar[0].run();
        Runnable filtrar = () -> { pagina[0] = 1; cargar[0].run(); };
        q.setOnAction(e -> filtrar.run());

        Map<String, Long> resumen = new HashMap<>();
        for (var r : Db.q("SELECT estado, count(*) AS n FROM v_envio GROUP BY estado")) resumen.put(s(r.get("estado")), l(r.get("n")));
        Node kpis = fila(14, kpi("Total", resumen.values().stream().mapToLong(Long::longValue).sum(), null, null),
                kpi("En camino", resumen.getOrDefault("EN CAMINO", 0L), "falta registrar su llegada", "ambar"),
                kpi("Recibidos en Tapachula", resumen.getOrDefault("RECIBIDO", 0L) + resumen.getOrDefault("DISPERSADO", 0L), null, null),
                kpi("Cancelados", resumen.getOrDefault("CANCELADO", 0L), null, null));

        HBox filtros = new HBox(12, campo("Buscar envío o lote", q, null), campo("Estado", estado, null),
                btn("Filtrar", "borde", filtrar));
        filtros.setAlignment(Pos.BOTTOM_LEFT);
        HBox.setHgrow(filtros.getChildren().get(0), Priority.ALWAYS);
        var derecha = new HBox();
        if (puede("nuevo_envio", true)) derecha.getChildren().add(btn("+  Nuevo envío", () -> ir("nuevo_envio")));
        return new VBox(18, encabezado(titulo("Inicio / Envíos", "Traslados terrestres Metapa → Tapachula"), derecha),
                puede("envios", true) ? aviso("Registras los traslados por tierra a Tapachula y puedes modificarlos o cancelarlos mientras van en camino. "
                        + "La llegada la registra el centro de empaque.", "azul")
                        : puede("estado_envio", true) ? aviso("Consultas los traslados y registras su llegada a Tapachula desde el estado de cada envío.", "azul")
                        : aviso("Solo consultas los traslados: los registra el centro de empaque. La dispersión es solo aérea."),
                kpis, caja(filtros), caja(resultados));
    }

    // ---------- Alta y modificación ----------

    static Node formulario(String codigo) throws SQLException {
        Map<String, Object> envio = null;
        if (codigo != null) {
            envio = Db.q1("SELECT * FROM v_envio WHERE codigo = ?", codigo);
            if (envio == null) throw new SQLException("No existe el envío " + codigo);
            if (!"EN CAMINO".equals(envio.get("estado"))) {
                avisar("Solo se modifican envíos en camino.");
                ir("estado_envio", codigo);
                return null;
            }
        }
        Map<String, Object> original = envio;
        var lotes = Db.q("""
                SELECT l.id, l.codigo, l.cantidad_pupas, l.enviadas, cp.fecha AS fecha_cc FROM v_lote l
                JOIN control_calidad cp ON cp.lote_id = l.id AND cp.etapa = 'PLANTA'
                WHERE l.calidad_planta AND l.estado <> 'RECHAZADO' AND (l.cantidad_pupas > l.enviadas OR l.id = ?)
                ORDER BY l.fecha_produccion""", envio != null ? envio.get("lote_id") : null);
        for (var l : lotes) { // lo que lleva este mismo envío vuelve a estar disponible al modificarlo
            long propio = original != null && original.get("lote_id").equals(l.get("id")) ? l(original.get("cantidad")) : 0;
            l.put("disponibles", l(l.get("cantidad_pupas")) - l(l.get("enviadas")) + propio);
        }
        var vehiculos = Db.q("SELECT * FROM vehiculo ORDER BY activo DESC, matricula");

        ComboBox<Map<String, Object>> lote = new ComboBox<>();
        lote.getItems().addAll(lotes);
        lote.setConverter(convertidor(l -> l.get("codigo") + " · aprobado · " + miles(l.get("disponibles")) + " disponibles"));
        ComboBox<Map<String, Object>> veh = new ComboBox<>();
        veh.getItems().addAll(vehiculos);
        veh.setConverter(convertidor(v -> v.get("matricula") + " · " + v.get("modelo")
                + " · cabe " + miles(v.get("capacidad")) + ((Boolean) v.get("activo") ? "" : " (fuera de servicio)")));
        for (var l : lotes) if (envio != null && l.get("id").equals(envio.get("lote_id"))) lote.setValue(l);
        for (var v : vehiculos) if (envio != null && v.get("id").equals(envio.get("vehiculo_id"))) veh.setValue(v);
        if (lote.getValue() == null && !lotes.isEmpty()) lote.setValue(lotes.get(0));
        if (veh.getValue() == null && !vehiculos.isEmpty()) veh.setValue(vehiculos.get(0));
        TextField cantidad = new TextField(envio != null ? s(envio.get("cantidad")) : "");
        TextField salida = new TextField((envio != null ? (LocalDateTime) envio.get("fecha_salida") : LocalDateTime.now()).format(FECHA_HORA));
        TextField temp = new TextField(envio != null ? s(envio.get("temperatura")) : "3.0");
        TextField resp = new TextField(Db.nombre + " · " + Db.empleado + " (usuario en sesión)");
        resp.setEditable(false);

        Label pupas = lbl("—", "kpi-n"), enviadas = lbl("—", "kpi-n"), cc = lbl("—", "kpi-n");
        pupas.setWrapText(false);
        enviadas.setWrapText(false);
        VBox checks = new VBox(12);
        VBox errores = errores();
        var guardar = btn(envio != null ? "Guardar cambios" : "Guardar envío", () -> {});

        Runnable revisar = () -> {
            var l = lote.getValue();
            var v = veh.getValue();
            long n;
            try {
                n = Reglas.entero(cantidad.getText());
            } catch (NumberFormatException e) {
                n = 0;
            }
            if (l != null) {
                pupas.setText(miles(l.get("cantidad_pupas")));
                enviadas.setText(miles(l.get("enviadas")));
                cc.setText("Aprobado " + fecha(l.get("fecha_cc")));
            }
            boolean cabe = v != null && n > 0 && n <= l(v.get("capacidad"));
            boolean alcanza = l != null && n <= l(l.get("disponibles"));
            boolean activo = v != null && (Boolean) v.get("activo");
            checks.getChildren().setAll(
                    Lotes.marcado(l != null, "Lote aprobado y sin rechazos", "Solo se listan lotes aprobados"),
                    Lotes.marcado(cabe, "Cabe en el vehículo", v == null ? "" : miles(n) + " de " + miles(v.get("capacidad")) + " moscas"),
                    Lotes.marcado(alcanza, "No supera lo que queda del lote", l == null ? "" : "Quedan " + miles(l.get("disponibles")) + " pupas disponibles"),
                    Lotes.marcado(activo, "Vehículo activo", v == null ? "" : v.get("matricula") + (activo ? " en servicio" : " fuera de servicio")));
            guardar.setDisable(!(l != null && cabe && alcanza && activo));
        };
        lote.valueProperty().addListener((o, a, b) -> revisar.run());
        veh.valueProperty().addListener((o, a, b) -> revisar.run());
        cantidad.textProperty().addListener((o, a, b) -> revisar.run());
        revisar.run();

        guardar.setOnAction(e -> {
            var l = lote.getValue();
            var v = veh.getValue();
            List<String> errs = new ArrayList<>();
            long n = 0;
            LocalDateTime fs = null;
            BigDecimal t = null;
            try {
                n = Reglas.entero(cantidad.getText());
                fs = leerFechaHora(salida.getText());
                t = new BigDecimal(temp.getText().trim());
            } catch (NumberFormatException | DateTimeParseException ex) {
                errs.add("Revisa la cantidad, la fecha (dd/mm/aaaa hh:mm) y la temperatura.");
            }
            if (l == null) errs.add("Elige un lote aprobado y con pupas disponibles.");
            if (v == null || !(Boolean) v.get("activo")) errs.add("Elige un vehículo en servicio.");
            if (errs.isEmpty()) errs.addAll(Reglas.erroresEnvio(n, l(v.get("capacidad")), l(l.get("disponibles"))));
            mostrarErrores(errores, errs);
            if (!errs.isEmpty()) return;
            long cant = n;
            LocalDateTime fechaSalida = fs;
            BigDecimal temperatura = t;
            String[] guardado = {codigo};
            try {
                Db.tx(() -> {
                    if (original != null) {
                        Db.exec("UPDATE envio SET lote_id = ?, vehiculo_id = ?, cantidad = ?, fecha_salida = ?, temperatura = ? WHERE id = ?",
                                l.get("id"), v.get("id"), cant, fechaSalida, temperatura, original.get("id"));
                        Db.auditar("Modificó el envío " + codigo);
                    } else {
                        guardado[0] = s(Db.q1("""
                                INSERT INTO envio (lote_id, vehiculo_id, fecha_salida, cantidad, temperatura, responsable_id)
                                VALUES (?, ?, ?, ?, ?, ?) RETURNING codigo""", l.get("id"), v.get("id"), fechaSalida, cant, temperatura,
                                Db.responsableId).get("codigo"));  // el código lo pone la base
                        Db.auditar("Registró el envío " + guardado[0]);
                    }
                });
            } catch (SQLException ex) {
                mostrarErrores(errores, List.of(Db.mensaje(ex)));
                return;
            }
            avisar("Envío " + guardado[0] + " guardado.");
            ir("estado_envio", guardado[0]);
        });

        VBox i1 = new VBox(2, lbl("Pupas del lote", "kpi-t"), pupas), i2 = new VBox(2, lbl("Ya enviadas", "kpi-t"), enviadas),
                i3 = new VBox(2, lbl("Calidad planta", "kpi-t"), cc);
        for (VBox i : List.of(i1, i2, i3)) i.getStyleClass().addAll("kpi", "kpi-gris");
        VBox form = caja(errores);
        if (lotes.isEmpty()) form.getChildren().add(aviso("No hay lotes aprobados con pupas disponibles. La planta debe registrar su control de calidad primero.", "ambar"));
        form.getChildren().addAll(campo("Lote", lote, "Solo aparecen lotes aprobados en la planta y sin rechazos"), fila(14, i1, i2, i3),
                campos(2, campo("Camioneta", veh, "Traslado terrestre Metapa → Tapachula"), campo("Moscas a enviar", cantidad, null),
                        campo("Fecha y hora de salida de Metapa", salida, "dd/mm/aaaa hh:mm"), campo("Temperatura de salida (°C)", temp, null)),
                campo("Responsable", resp, null), accionesFin(btn("Cancelar", "gris", () -> ir("envios")), guardar));
        form.setSpacing(18);
        VBox lateral = new VBox(16, caja(h2("Validación del envío"), checks),
                aviso("Por tierra solo se trasladan las pupas de la planta de Metapa al centro de empaque de Tapachula; no se dispersan. "
                        + "Al llegar, el centro registra la llegada y la encargada de dispersión ya puede usarlas.", "azul"));
        return new VBox(20, encabezado(titulo("Inicio / Envíos / " + (envio != null ? "Modificar" : "Nuevo"),
                envio != null ? "Modificar envío " + codigo : "Nuevo envío"), lbl("Solo PES · planta Metapa", "chip")), columnas(form, lateral));
    }

    static javafx.util.StringConverter<Map<String, Object>> convertidor(java.util.function.Function<Map<String, Object>, String> texto) {
        return new javafx.util.StringConverter<>() {
            @Override public String toString(Map<String, Object> m) { return m == null ? "" : texto.apply(m); }
            @Override public Map<String, Object> fromString(String s) { return null; }
        };
    }

    // ---------- Estado y llegada a Tapachula ----------

    static Node estado(String codigo) throws SQLException {
        if (codigo == null) {
            var ultimo = Db.q1("SELECT codigo FROM v_envio ORDER BY (estado = 'EN CAMINO') DESC, fecha_salida DESC LIMIT 1");
            if (ultimo == null) {
                avisar("Todavía no hay envíos registrados.");
                ir("inicio");
                return null;
            }
            codigo = s(ultimo.get("codigo"));
        }
        var envio = Db.q1("""
                SELECT e.*, l.codigo AS lote, v.matricula, v.modelo, r.num_empleado, r.nombre AS r_nombre, r.ap_paterno
                FROM v_envio e JOIN lote_produccion l ON l.id = e.lote_id JOIN vehiculo v ON v.id = e.vehiculo_id
                JOIN responsable r ON r.id = e.responsable_id WHERE e.codigo = ?""", codigo);
        if (envio == null) throw new SQLException("No existe el envío " + codigo);
        long cantidad = l(envio.get("cantidad")), liberadas = l(envio.get("liberadas"));
        String est = s(envio.get("estado"));
        var dispersiones = Db.q("""
                SELECT li.*, z.clave, z.nombre FROM liberacion li JOIN zona_liberacion z ON z.id = li.zona_id
                WHERE li.envio_id = ? ORDER BY li.fecha""", envio.get("id"));

        HBox titulo = new HBox(12, lbl(codigo, "h1", "mono"), pill(est));
        titulo.setAlignment(Pos.CENTER_LEFT);
        var derecha = new HBox(12);
        String cod = codigo;
        if (est.equals("EN CAMINO") && puede("envios", true)) {
            derecha.getChildren().addAll(btn("Cancelar envío", "gris", () -> cancelar(cod)), btn("Modificar envío", "gris", () -> ir("modificar_envio", cod)));
        }
        if (est.equals("RECIBIDO") && puede("nueva_dispersion", true)) {
            derecha.getChildren().add(btn("Registrar dispersión", () -> ir("nueva_dispersion", "ENVIO|" + cod + "|EMPAQUE")));
        }

        ProgressBar progreso = new ProgressBar(cantidad == 0 ? 0 : (double) liberadas / cantidad);
        progreso.getStyleClass().add("progreso");
        progreso.setMaxWidth(Double.MAX_VALUE);
        String loteCod = s(envio.get("lote"));
        var datos = Ui.datos(4, "Lote", loteCod, "Camioneta", envio.get("matricula") + " · " + envio.get("modelo"),
                "Salida de Metapa", fechaHora(envio.get("fecha_salida")), "Llegada a Tapachula", fechaHora(envio.get("recibido")),
                "Moscas enviadas", miles(cantidad), "Dispersadas", miles(liberadas), "Temperatura", envio.get("temperatura") + " °C",
                "Despachó", envio.get("num_empleado") + " · " + s(envio.get("r_nombre")).charAt(0) + ". " + envio.get("ap_paterno"));
        Node resumen = caja(datos, link("Ver lote " + loteCod, () -> ir("estado_lote", loteCod)), progreso);

        List<Node[]> filas = new ArrayList<>();
        for (var li : dispersiones) {
            filas.add(new Node[]{t(li.get("clave") + " · " + li.get("nombre")), t(fechaHora(li.get("fecha"))), mono(li.get("avioneta")),
                    t(miles(li.get("cantidad"))), t(miles(li.get("superficie_ha")) + " ha"), t(miles(li.get("altitud_m")) + " m")});
        }
        VBox principal = new VBox(18, caja(h2("Dispersiones aéreas con moscas de este envío"),
                tabla(List.of("Zona", "Fecha", "Avioneta", "Moscas", "Superficie", "Altitud"), filas, Set.of(3, 4, 5),
                        (Boolean) envio.get("cancelado") ? "Este envío fue cancelado." : "Todavía no hay dispersiones con este envío.")));

        VBox lateral = new VBox(16);
        if (est.equals("EN CAMINO") && puede("estado_envio", true)) lateral.getChildren().add(formularioLlegada(envio));
        lateral.getChildren().add(aviso("Recorrido: la planta de Metapa produce el lote, la camioneta lo lleva a Tapachula y, al llegar, "
                + "la encargada de dispersión registra los informes de la avioneta. Por tierra no se dispersa.", "azul"));
        return new VBox(18, encabezado(new VBox(4, lbl("Envíos / Estado", "ruta"), titulo), derecha), resumen, columnas(principal, lateral));
    }

    static Node formularioLlegada(Map<String, Object> envio) {
        TextField llegada = new TextField(LocalDateTime.now().format(FECHA_HORA));
        VBox errores = errores();
        String codigo = s(envio.get("codigo"));
        var recibir = btn("Registrar llegada", () -> {
            LocalDateTime f;
            try {
                f = leerFechaHora(llegada.getText());
            } catch (DateTimeParseException ex) {
                mostrarErrores(errores, List.of("Revisa la fecha (dd/mm/aaaa hh:mm)."));
                return;
            }
            if (f.isBefore((LocalDateTime) envio.get("fecha_salida"))) {
                mostrarErrores(errores, List.of("La llegada no puede ser antes de la salida de Metapa (" + fechaHora(envio.get("fecha_salida")) + ")."));
                return;
            }
            try {
                Db.exec("CALL recibir_envio(?, ?)", codigo, f);  // el procedimiento también deja la bitácora
            } catch (SQLException ex) {
                mostrarErrores(errores, List.of(Db.mensaje(ex)));
                return;
            }
            avisar("Envío " + codigo + " recibido en Tapachula.");
            ir("estado_envio", codigo);
        });
        recibir.setMaxWidth(Double.MAX_VALUE);
        return caja(h2("Llegada al centro de empaque"), errores, campo("Fecha y hora de llegada", llegada, "dd/mm/aaaa hh:mm"), recibir);
    }

    /** Cancela un envío en camino; las pupas regresan al lote. */
    static void cancelar(String codigo) {
        if (!confirmar("¿Cancelar el envío " + codigo + "? No se puede deshacer.")) return;
        try {
            Db.exec("CALL cancelar_envio(?)", codigo);
            avisar("Envío " + codigo + " cancelado.");
        } catch (SQLException ex) {
            avisar(Db.mensaje(ex));
        }
        ir("estado_envio", codigo);
    }
}
