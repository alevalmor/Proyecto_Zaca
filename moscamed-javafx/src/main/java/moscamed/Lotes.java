package moscamed;

import static moscamed.Programa1.avisar;
import static moscamed.Programa1.esPes;
import static moscamed.Programa1.ir;
import static moscamed.Programa1.puede;
import static moscamed.Ui.*;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Lotes: lista, alta, modificación, estado y control de calidad (04, 05 y 06 del diseño). */
final class Lotes {
    private Lotes() {}

    static final int POR_PAGINA = 20;
    static final List<String> ESTADOS = List.of("PRODUCIDO", "APROBADO", "RECHAZADO", "ENVIADO", "LIBERADO");

    /** Siguiente código con el prefijo; el prefijo lo pone el programa, nunca el usuario. */
    static String siguienteCodigo(String prefijo) throws SQLException {
        String tabla = prefijo.startsWith("MET") ? "lote_produccion" : prefijo.startsWith("AER") ? "embarque_aereo" : "envio";
        var fila = Db.q1("SELECT coalesce(max(substr(codigo, ?)::int), 0) + 1 AS n FROM " + tabla + " WHERE codigo LIKE ?",
                prefijo.length() + 1, prefijo + "%");
        return prefijo + String.format("%04d", l(fila.get("n")));
    }

    // ---------- Lista ----------

    static Node lista() {
        TextField q = new TextField();
        q.setPromptText("MET-2026-…");
        q.getStyleClass().add("mono");
        ComboBox<String> estado = new ComboBox<>();
        estado.getItems().add("Todos");
        estado.getItems().addAll(ESTADOS);
        estado.getSelectionModel().select(0);
        DatePicker desde = new DatePicker(), hasta = new DatePicker();
        VBox resultados = new VBox();
        int[] pagina = {1};

        Runnable[] cargar = new Runnable[1];
        cargar[0] = () -> {
            List<String> filtros = new ArrayList<>(List.of("true"));
            List<Object> p = new ArrayList<>();
            if (!q.getText().isBlank()) { filtros.add("l.codigo ILIKE ?"); p.add("%" + q.getText().trim() + "%"); }
            if (estado.getSelectionModel().getSelectedIndex() > 0) { filtros.add("l.estado = ?"); p.add(estado.getValue()); }
            if (desde.getValue() != null) { filtros.add("l.fecha_produccion >= ?"); p.add(desde.getValue()); }
            if (hasta.getValue() != null) { filtros.add("l.fecha_produccion <= ?"); p.add(hasta.getValue()); }
            String donde = String.join(" AND ", filtros);
            try {
                long total = l(Db.q1("SELECT count(*) AS n FROM v_lote l WHERE " + donde, p.toArray()).get("n"));
                List<Object> pp = new ArrayList<>(p);
                pp.add(POR_PAGINA);
                pp.add((pagina[0] - 1) * POR_PAGINA);
                var filas = Db.q("SELECT l.*, c.clave AS destino FROM v_lote l JOIN centro_empaque c ON c.id = l.centro_empaque_id WHERE "
                        + donde + " ORDER BY l.fecha_produccion DESC, l.codigo DESC LIMIT ? OFFSET ?", pp.toArray());
                List<Node[]> nodos = new ArrayList<>();
                for (var lote : filas) {
                    String codigo = s(lote.get("codigo")), est = s(lote.get("estado"));
                    Object cp = lote.get("calidad_planta");
                    Node acciones = puede("lotes", true) && (est.equals("PRODUCIDO") || est.equals("APROBADO"))
                            ? juntos(link("Ver", () -> ir("estado_lote", codigo)), link("Modificar", () -> ir("modificar_lote", codigo)))
                            : juntos(link("Ver", () -> ir("estado_lote", codigo)));
                    nodos.add(new Node[]{mono(codigo), t(fecha(lote.get("fecha_produccion"))), t(miles(lote.get("cantidad_pupas"))),
                            t(lote.get("cepa")), t(lote.get("destino")),
                            t(cp == null ? "Pendiente" : (Boolean) cp ? "Aprobado" : "Rechazado"), pill(est), acciones});
                }
                resultados.getChildren().setAll(tabla(List.of("Código", "Producción", "Pupas", "Cepa", "Destino", "Calidad planta", "Estado", "Acciones"),
                        nodos, Set.of(2, 7), "Sin resultados."), paginacion(pagina, total, cargar[0], null));
            } catch (SQLException e) {
                resultados.getChildren().setAll(aviso(Db.mensaje(e), "error"));
            }
        };
        cargar[0].run();

        Runnable filtrar = () -> { pagina[0] = 1; cargar[0].run(); };
        q.setOnAction(e -> filtrar.run());
        HBox filtros = new HBox(12, campo("Buscar por código", q, null), campo("Estado", estado, null),
                campo("Desde", desde, null), campo("Hasta", hasta, null), btn("Filtrar", "borde", filtrar));
        filtros.setAlignment(Pos.BOTTOM_LEFT);
        HBox.setHgrow(filtros.getChildren().get(0), Priority.ALWAYS);

        var derecha = new HBox();
        if (puede("nuevo_lote", true)) derecha.getChildren().add(btn("+  Nuevo lote", () -> ir("nuevo_lote")));
        return new VBox(18, encabezado(titulo("Inicio / Lotes", "Lista de lotes"), derecha),
                esPes() ? aviso("Como PES puedes ver y modificar los lotes de la planta.")
                        : aviso("Como " + Db.perfil + " solo ves los lotes: los registra y modifica la planta de Metapa.", "azul"),
                caja(filtros), caja(resultados));
    }

    /** Pie de tabla con "Mostrando a–b de n" y botones para cambiar de página. */
    static Node paginacion(int[] pagina, long total, Runnable cargar, String nota) {
        long desde = (pagina[0] - 1L) * POR_PAGINA + 1, hasta = Math.min((long) pagina[0] * POR_PAGINA, total);
        Label texto = lbl((total == 0 ? "Sin resultados" : "Mostrando " + desde + "–" + hasta + " de " + total) + (nota == null ? "" : nota), "gris");
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox h = new HBox(8, texto, hueco);
        h.setAlignment(Pos.CENTER_LEFT);
        h.setStyle("-fx-padding: 12 0 0 0;");
        if (pagina[0] > 1) h.getChildren().add(chico(btn("Anterior", "gris", () -> { pagina[0]--; cargar.run(); })));
        if ((long) pagina[0] * POR_PAGINA < total) h.getChildren().add(chico(btn("Siguiente", "gris", () -> { pagina[0]++; cargar.run(); })));
        return h;
    }

    static javafx.scene.control.Button chico(javafx.scene.control.Button b) {
        b.getStyleClass().add("btn-chico");
        return b;
    }

    // ---------- Alta y modificación ----------

    static Node formulario(String codigo) throws SQLException {
        Map<String, Object> lote = null;
        if (codigo != null) {
            lote = Db.q1("SELECT * FROM v_lote WHERE codigo = ?", codigo);
            if (lote == null) throw new SQLException("No existe el lote " + codigo);
            String est = s(lote.get("estado"));
            if (!est.equals("PRODUCIDO") && !est.equals("APROBADO")) {
                avisar("Solo se modifican lotes que aún no salen de la planta.");
                ir("estado_lote", codigo);
                return null;
            }
        }
        LocalDate hoy = LocalDate.now();
        TextField cod = new TextField(lote != null ? codigo : siguienteCodigo("MET-" + hoy.getYear() + "-"));
        cod.setEditable(false);
        cod.getStyleClass().add("mono");
        TextField pupas = new TextField(lote != null ? s(lote.get("cantidad_pupas")) : "");
        DatePicker fp = new DatePicker(lote != null ? (LocalDate) lote.get("fecha_produccion") : hoy);
        DatePicker fi = new DatePicker(lote != null ? (LocalDate) lote.get("fecha_irradiacion") : hoy);
        TextField dosis = new TextField(lote != null ? s(lote.get("dosis_gy")) : "120.0");
        ComboBox<String> cepa = combo(List.of("Tapachula-7 TSL", "VIENNA-8 TSL"), lote != null ? s(lote.get("cepa")) : null);
        ComboBox<String> tinte = combo(List.of("Rosa fluorescente", "Naranja fluorescente", "Verde fluorescente"), lote != null ? s(lote.get("tinte")) : null);
        var centros = Db.q("SELECT * FROM centro_empaque ORDER BY clave");
        ComboBox<String> centro = new ComboBox<>();
        Map<String, Integer> idCentro = new HashMap<>();
        for (var c : centros) {
            String nombre = c.get("clave") + " · " + c.get("nombre");
            centro.getItems().add(nombre);
            idCentro.put(nombre, (Integer) c.get("id"));
            if (lote != null && c.get("id").equals(lote.get("centro_empaque_id"))) centro.setValue(nombre);
        }
        if (centro.getValue() == null && !centro.getItems().isEmpty()) centro.getSelectionModel().select(0);
        TextField resp = new TextField(Db.nombre + " · " + Db.empleado + " (usuario en sesión)");
        resp.setEditable(false);
        TextArea obs = new TextArea(lote != null ? s(lote.get("observaciones")) : "");
        obs.setPrefRowCount(3);
        VBox errores = errores();
        Map<String, Object> original = lote;

        var guardar = btn(lote != null ? "Guardar cambios" : "Guardar lote", () -> {
            List<String> errs = new ArrayList<>();
            long n = 0;
            BigDecimal gy = null;
            try {
                n = Reglas.entero(pupas.getText());
                gy = new BigDecimal(dosis.getText().trim());
            } catch (NumberFormatException e) {
                errs.add("Revisa que la cantidad de pupas y la dosis sean números.");
            }
            if (fp.getValue() == null || fi.getValue() == null) errs.add("Llena las dos fechas.");
            else if (fi.getValue().isBefore(fp.getValue())) errs.add("La irradiación debe ser el mismo día o después de la producción.");
            if (gy != null && (gy.compareTo(BigDecimal.valueOf(90)) < 0 || gy.compareTo(BigDecimal.valueOf(160)) > 0)) errs.add("La dosis debe estar entre 90 y 160 Gy.");
            if (errs.isEmpty() && n <= 0) errs.add("La cantidad de pupas debe ser mayor que cero.");
            if (centro.getValue() == null) errs.add("Elige el centro de empaque destino.");
            mostrarErrores(errores, errs);
            if (!errs.isEmpty()) return;
            String nota = obs.getText().isBlank() ? null : obs.getText().trim();
            long cantidad = n;
            BigDecimal dosisGy = gy;
            String[] guardado = {codigo};
            try {
                Db.tx(() -> {
                    if (original != null) {
                        Db.exec("""
                                UPDATE lote_produccion SET fecha_produccion = ?, fecha_irradiacion = ?, dosis_gy = ?, cantidad_pupas = ?,
                                cepa = ?, tinte = ?, centro_empaque_id = ?, observaciones = ? WHERE id = ?""",
                                fp.getValue(), fi.getValue(), dosisGy, cantidad, cepa.getValue(), tinte.getValue(),
                                idCentro.get(centro.getValue()), nota, original.get("id"));
                        Db.auditar("Modificó el lote " + codigo);
                    } else {
                        guardado[0] = s(Db.q1("""
                                INSERT INTO lote_produccion (fecha_produccion, fecha_irradiacion, dosis_gy, cantidad_pupas, cepa, tinte,
                                centro_empaque_id, responsable_id, observaciones) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING codigo""",
                                fp.getValue(), fi.getValue(), dosisGy, cantidad, cepa.getValue(), tinte.getValue(),
                                idCentro.get(centro.getValue()), Db.responsableId, nota).get("codigo"));  // el código lo pone la base
                        Db.auditar("Registró el lote " + guardado[0]);
                    }
                });
            } catch (SQLException e) {
                mostrarErrores(errores, List.of(Db.mensaje(e)));
                return;
            }
            avisar("Lote " + guardado[0] + " guardado.");
            ir("estado_lote", guardado[0]);
        });

        VBox form = caja(lbl("Datos de producción", "h2", "h2-grande"), errores,
                campos(2, campo("Código del lote", cod, lote != null ? "No cambia al modificar" : "Se genera solo con el prefijo de la planta"),
                        campo("Cantidad de pupas", pupas, null), campo("Fecha de producción", fp, null), campo("Fecha de irradiación", fi, null),
                        campo("Dosis de irradiación (Gy)", dosis, null), campo("Cepa", cepa, null), campo("Color de tinte", tinte, null),
                        campo("Centro de empaque destino", centro, null)),
                campo("Responsable", resp, null), campo("Observaciones (opcional)", obs, null),
                accionesFin(btn("Cancelar", "gris", () -> ir("lotes")), guardar));
        form.setSpacing(18);

        VBox reglas = new VBox(10);
        for (String r : List.of("La irradiación es el mismo día o después de la producción", "La dosis está entre 90 y 160 Gy",
                "La cantidad de pupas es mayor que cero", "El responsable es de la planta y está activo")) {
            reglas.getChildren().add(marcado(true, r, null));
        }
        VBox lateral = new VBox(16, caja(h2("Revisión antes de guardar"), reglas),
                aviso("¿Qué pasa al guardar?\nEl lote recibe un id impar de la planta y aparece en el centro de empaque en segundos por la replicación. "
                        + "Queda en estado PRODUCIDO hasta registrar su control de calidad.", "azul"));
        return new VBox(20, encabezado(titulo("Inicio / Lotes / " + (lote != null ? "Modificar" : "Nuevo"),
                        lote != null ? "Modificar lote " + codigo : "Nuevo lote de producción"), lbl("Solo PES · planta Metapa", "chip")),
                columnas(form, lateral));
    }

    static ComboBox<String> combo(List<String> opciones, String valor) {
        ComboBox<String> c = new ComboBox<>();
        c.getItems().addAll(opciones);
        if (valor != null && !c.getItems().contains(valor)) c.getItems().add(valor);
        c.setValue(valor != null ? valor : opciones.get(0));
        return c;
    }

    /** Renglón con marca ✓ o ! y texto (validaciones). */
    static HBox marcado(boolean ok, String titulo, String detalle) {
        Label marca = lbl(ok ? "✓" : "!", "marca-ok");
        if (!ok) marca.getStyleClass().add("marca-no");
        marca.setWrapText(false);
        VBox texto = new VBox(lbl(titulo));
        if (detalle != null) texto.getChildren().add(lbl(detalle, "gris", "chico"));
        HBox h = new HBox(10, marca, texto);
        h.setAlignment(Pos.TOP_LEFT);
        return h;
    }

    // ---------- Estado y control de calidad ----------

    static Node estado(String codigo) throws SQLException {
        if (codigo == null) {
            var ultimo = Db.q1("SELECT codigo FROM v_lote ORDER BY (estado = 'PRODUCIDO') DESC, fecha_produccion DESC LIMIT 1");
            if (ultimo == null) {
                avisar("Todavía no hay lotes registrados.");
                ir("inicio");
                return null;
            }
            codigo = s(ultimo.get("codigo"));
        }
        var lote = Db.q1("""
                SELECT l.*, c.clave AS centro_clave, c.nombre AS centro_nombre, r.num_empleado, r.nombre AS r_nombre, r.ap_paterno
                FROM v_lote l JOIN centro_empaque c ON c.id = l.centro_empaque_id JOIN responsable r ON r.id = l.responsable_id
                WHERE l.codigo = ?""", codigo);
        if (lote == null) throw new SQLException("No existe el lote " + codigo);
        Map<String, Map<String, Object>> calidades = new HashMap<>();
        for (var c : Db.q("SELECT c.*, r.num_empleado FROM control_calidad c JOIN responsable r ON r.id = c.responsable_id WHERE c.lote_id = ?", lote.get("id"))) {
            calidades.put(s(c.get("etapa")), c);
        }
        var envios = Db.q("""
                SELECT e.codigo, e.cantidad, e.liberadas, e.estado, v.matricula,
                       (SELECT string_agg(DISTINCT z.clave || ' · ' || z.municipio, ', ') FROM liberacion li
                        JOIN zona_liberacion z ON z.id = li.zona_id WHERE li.envio_id = e.id) AS zonas
                FROM v_envio e JOIN vehiculo v ON v.id = e.vehiculo_id WHERE e.lote_id = ? ORDER BY e.fecha_salida""", lote.get("id"));
        String etapa = esPes() ? "PLANTA" : "EMPAQUE";
        var cp = calidades.get("PLANTA");
        var ce = calidades.get("EMPAQUE");
        String est = s(lote.get("estado"));

        HBox titulo = new HBox(12, lbl(codigo, "h1", "mono"), pill(est));
        titulo.setAlignment(Pos.CENTER_LEFT);
        var derecha = new HBox(12, selectorLote(codigo));
        derecha.setAlignment(Pos.BOTTOM_RIGHT);
        String cod = codigo;
        if (puede("lotes", true) && (est.equals("PRODUCIDO") || est.equals("APROBADO"))) {
            derecha.getChildren().add(btn("Modificar lote", "gris", () -> ir("modificar_lote", cod)));
        }

        VBox datos = caja(h2("Datos del lote"), Ui.datos(3,
                "Fecha de producción", fecha(lote.get("fecha_produccion")), "Fecha de irradiación", fecha(lote.get("fecha_irradiacion")),
                "Dosis", lote.get("dosis_gy") + " Gy", "Pupas", miles(lote.get("cantidad_pupas")),
                "Cepa", s(lote.get("cepa")), "Tinte", s(lote.get("tinte")),
                "Centro destino", lote.get("centro_clave") + " · " + lote.get("centro_nombre"),
                "Registró", lote.get("num_empleado") + " · " + s(lote.get("r_nombre")).charAt(0) + ". " + lote.get("ap_paterno"),
                "Enviadas / dispersadas", millones(lote.get("enviadas")) + " / " + millones(lote.get("liberadas"))));
        if (lote.get("observaciones") != null) datos.getChildren().add(lbl(s(lote.get("observaciones")), "gris"));

        Node calidad = caja(h2("Control de calidad"), fila(14, tarjetaCalidad("planta", cp, "tarjeta-verde"), tarjetaCalidad("empaque", ce, "tarjeta-azul")));

        List<Node[]> filas = new ArrayList<>();
        for (var e : envios) {
            String ce2 = s(e.get("codigo"));
            filas.add(new Node[]{link(ce2, () -> ir("estado_envio", ce2)), mono(e.get("matricula")),
                    t(miles(e.get("cantidad"))), t(e.get("zonas")), t(l(e.get("liberadas")) > 0 ? miles(e.get("liberadas")) : "—"), pill(s(e.get("estado")))});
        }
        Node tablaEnvios = caja(h2("Envíos y dispersiones de este lote"),
                tabla(List.of("Envío", "Camioneta", "Enviadas", "Zonas", "Dispersadas", "Estado"), filas, Set.of(2, 4), "Este lote aún no tiene envíos."));

        VBox lateral = new VBox(16);
        if (puede("estado_lote", true)) lateral.getChildren().add(formularioCalidad(lote, calidades.get(etapa), etapa));
        lateral.getChildren().add(aviso("El estado del lote no se escribe a mano: el sistema lo calcula con sus pruebas, envíos y liberaciones. "
                + "Así las dos sedes nunca lo editan al mismo tiempo.", "ambar"));

        return new VBox(18, encabezado(new VBox(4, lbl("Lotes / Estado", "ruta"), titulo), derecha),
                columnas(new VBox(18, datos, calidad, tablaEnvios), lateral));
    }

    /** Lista de todos los lotes, del más reciente al más antiguo; al elegir uno se abre su estado. */
    static Node selectorLote(String actual) throws SQLException {
        ComboBox<Map<String, Object>> lotes = new ComboBox<>();
        lotes.getItems().addAll(Db.q("SELECT codigo, estado, fecha_produccion FROM v_lote ORDER BY fecha_produccion DESC, codigo DESC"));
        lotes.setConverter(Envios.convertidor(l -> l.get("codigo") + " · " + l.get("estado") + " · " + fecha(l.get("fecha_produccion"))));
        lotes.setVisibleRowCount(12);
        for (var l : lotes.getItems()) if (actual.equals(l.get("codigo"))) lotes.setValue(l);
        lotes.setOnAction(e -> {
            var elegido = lotes.getValue();
            if (elegido != null && !actual.equals(elegido.get("codigo"))) ir("estado_lote", s(elegido.get("codigo")));
        });
        return campo("Ver otro lote", lotes, null);
    }

    static String resultado(Map<String, Object> c) {
        return (Boolean) c.get("aprobado") ? "Aprobado" : "Rechazado";
    }

    static Node tarjetaCalidad(String etapa, Map<String, Object> c, String clase) {
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox cab = new HBox(lbl("Etapa " + etapa, "dato"), hueco,
                c == null ? pill("SIN PRUEBA", "") : pill(resultado(c).toUpperCase()));
        String texto = c != null
                ? "Emergencia " + c.get("emergencia") + " % · Vuelo " + c.get("vuelo") + " %\nEsterilidad " + c.get("esterilidad")
                  + " % · Peso " + c.get("peso_mg") + " mg\n" + fecha(c.get("fecha")) + " · " + c.get("num_empleado")
                : etapa.equals("empaque") ? "Es opcional. La registra el centro de empaque al recibir el lote."
                : "Pendiente. Sin ella el lote no se puede enviar.";
        VBox v = new VBox(8, cab, lbl(texto, "gris"));
        v.getStyleClass().add("tarjeta");
        if (c != null) v.getStyleClass().add(clase);
        v.setMaxWidth(Double.MAX_VALUE);
        return v;
    }

    static Node formularioCalidad(Map<String, Object> lote, Map<String, Object> actual, String etapa) {
        TextField em = new TextField(actual != null ? s(actual.get("emergencia")) : "");
        TextField vu = new TextField(actual != null ? s(actual.get("vuelo")) : "");
        TextField es = new TextField(actual != null ? s(actual.get("esterilidad")) : "");
        TextField pe = new TextField(actual != null ? s(actual.get("peso_mg")) : "");
        TextArea obs = new TextArea(actual != null ? s(actual.get("observaciones")) : "");
        obs.setPrefRowCount(2);
        Label resultado = lbl("—", "resultado-n");
        VBox caja = new VBox(2, lbl("Resultado calculado", "chico"), resultado,
                lbl("Aprueba con emergencia ≥ 75 %, vuelo ≥ 65 % y esterilidad ≥ 99.5 %", "chico"));
        caja.getStyleClass().add("resultado");
        Runnable calcular = () -> {
            try {
                boolean ok = Reglas.calidadAprobada(Double.parseDouble(em.getText()), Double.parseDouble(vu.getText()), Double.parseDouble(es.getText()));
                resultado.setText(ok ? "APROBADO" : "RECHAZADO");
                caja.getStyleClass().remove("resultado-no");
                if (!ok) caja.getStyleClass().add("resultado-no");
            } catch (NumberFormatException e) {
                resultado.setText("—");
            }
        };
        for (TextField f : List.of(em, vu, es)) f.textProperty().addListener((o, a, b) -> calcular.run());
        calcular.run();
        VBox errores = errores();
        String codigo = s(lote.get("codigo"));

        var guardar = btn(actual != null ? "Guardar cambios de la prueba" : "Registrar control de calidad", () -> {
            BigDecimal[] v = new BigDecimal[4];
            List<String> errs = new ArrayList<>();
            try {
                v[0] = new BigDecimal(em.getText().trim());
                v[1] = new BigDecimal(vu.getText().trim());
                v[2] = new BigDecimal(es.getText().trim());
                v[3] = new BigDecimal(pe.getText().trim());
                for (int i = 0; i < 3; i++) {
                    if (v[i].signum() < 0 || v[i].compareTo(BigDecimal.valueOf(100)) > 0) { errs.add("Los porcentajes van de 0 a 100."); break; }
                }
                if (v[3].signum() <= 0) errs.add("El peso debe ser mayor que cero.");
            } catch (NumberFormatException e) {
                errs.add("Llena las cuatro mediciones con números.");
            }
            mostrarErrores(errores, errs);
            if (!errs.isEmpty()) return;
            try {
                Db.tx(() -> {
                    Db.exec("""
                            INSERT INTO control_calidad (lote_id, etapa, emergencia, vuelo, esterilidad, peso_mg, responsable_id, observaciones)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            ON CONFLICT (lote_id, etapa) DO UPDATE SET emergencia = EXCLUDED.emergencia, vuelo = EXCLUDED.vuelo,
                              esterilidad = EXCLUDED.esterilidad, peso_mg = EXCLUDED.peso_mg, fecha = current_date,
                              responsable_id = EXCLUDED.responsable_id, observaciones = EXCLUDED.observaciones""",
                            lote.get("id"), etapa, v[0], v[1], v[2], v[3], Db.responsableId, obs.getText().isBlank() ? null : obs.getText().trim());
                    Db.auditar("Registró calidad " + etapa.toLowerCase() + " de " + codigo);
                });
            } catch (SQLException e) {
                mostrarErrores(errores, List.of(Db.mensaje(e)));
                return;
            }
            avisar("Control de calidad guardado.");
            ir("estado_lote", codigo);
        });
        guardar.setMaxWidth(Double.MAX_VALUE);
        String tituloForm = etapa.equals("PLANTA") ? (actual != null ? "Modificar prueba de la planta" : "Registrar prueba de la planta") : "Registrar prueba al recibir";
        return caja(new VBox(4, h2(tituloForm), lbl(etapa.equals("PLANTA") ? "Etapa PLANTA · solo la planta puede cambiarla"
                        : "Etapa EMPAQUE · se guarda con id par del centro de empaque", "gris", "chico")), errores,
                campos(2, campo("Emergencia (%)", em, null), campo("Vuelo (%)", vu, null), campo("Esterilidad (%)", es, null), campo("Peso pupa (mg)", pe, null)),
                caja, campo("Observaciones", obs, null), guardar);
    }
}
