package moscamed;

import static moscamed.Programa1.avisar;
import static moscamed.Programa1.ir;
import static moscamed.Programa1.puede;
import static moscamed.Ui.*;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Aeropuerto de Tapachula: embarques aéreos de moscas estériles que llegan de Perú (perfil PEA). */
final class Embarques {
    private Embarques() {}

    // ---------- Lista ----------

    static Node lista() throws SQLException {
        TextField q = new TextField();
        q.setPromptText("AER-EMB-… o guía aérea");
        q.getStyleClass().add("mono");
        VBox resultados = new VBox();
        int[] pagina = {1};

        Runnable[] cargar = new Runnable[1];
        cargar[0] = () -> {
            String donde = q.getText().isBlank() ? "true" : "(codigo ILIKE ? OR guia_aerea ILIKE ?)";
            List<Object> p = new ArrayList<>();
            if (!q.getText().isBlank()) {
                p.add("%" + q.getText().trim() + "%");
                p.add("%" + q.getText().trim() + "%");
            }
            try {
                long total = l(Db.q1("SELECT count(*) AS n FROM v_embarque WHERE " + donde, p.toArray()).get("n"));
                p.add(Lotes.POR_PAGINA);
                p.add((pagina[0] - 1) * Lotes.POR_PAGINA);
                List<Node[]> nodos = new ArrayList<>();
                for (var b : Db.q("SELECT * FROM v_embarque WHERE " + donde + " ORDER BY fecha_llegada DESC LIMIT ? OFFSET ?", p.toArray())) {
                    long quedanAer = l(b.get("queda_aeropuerto")) - l(b.get("dispersadas_aeropuerto"));
                    long quedanEmp = l(b.get("va_empaque")) - l(b.get("dispersadas_empaque"));
                    nodos.add(new Node[]{mono(b.get("codigo")), mono(b.get("guia_aerea")), t(s(b.get("vuelo")) + " · " + b.get("origen")),
                            t(fechaHora(b.get("fecha_llegada"))), t(millones(b.get("cantidad"))),
                            t(millones(b.get("queda_aeropuerto")) + " · quedan " + millones(quedanAer)),
                            t(millones(b.get("va_empaque")) + " · quedan " + millones(quedanEmp))});
                }
                resultados.getChildren().setAll(tabla(List.of("Embarque", "Guía aérea", "Vuelo · origen", "Llegada", "Moscas",
                                "En aeropuerto", "Al centro"), nodos, Set.of(4, 5, 6), "Todavía no hay embarques."),
                        Lotes.paginacion(pagina, total, cargar[0], null));
            } catch (SQLException e) {
                resultados.getChildren().setAll(aviso(Db.mensaje(e), "error"));
            }
        };
        cargar[0].run();
        Runnable filtrar = () -> { pagina[0] = 1; cargar[0].run(); };
        q.setOnAction(e -> filtrar.run());

        var k = Db.q1("""
                SELECT count(*) FILTER (WHERE fecha_llegada >= date_trunc('month', current_date)) AS mes,
                       coalesce(sum(cantidad) FILTER (WHERE fecha_llegada >= date_trunc('month', current_date)), 0) AS moscas_mes,
                       coalesce(sum(queda_aeropuerto - dispersadas_aeropuerto), 0) AS en_aeropuerto,
                       coalesce(sum(va_empaque - dispersadas_empaque), 0) AS en_empaque
                FROM v_embarque""");
        Node kpis = fila(14, kpi("Embarques este mes", k.get("mes"), "llegados de Perú", null),
                kpi("Moscas recibidas este mes", millones(k.get("moscas_mes")), null, null),
                kpi("Por dispersar en el aeropuerto", millones(k.get("en_aeropuerto")), null, "ambar"),
                kpi("Por dispersar desde el centro", millones(k.get("en_empaque")), null, null));

        HBox filtros = new HBox(12, campo("Buscar embarque o guía", q, null), btn("Filtrar", "borde", filtrar));
        filtros.setAlignment(Pos.BOTTOM_LEFT);
        HBox.setHgrow(filtros.getChildren().get(0), Priority.ALWAYS);
        var derecha = new HBox();
        if (puede("nuevo_embarque", true)) derecha.getChildren().add(btn("+  Nuevo embarque", () -> ir("nuevo_embarque")));
        return new VBox(18, encabezado(titulo("Inicio / Aeropuerto de Tapachula", "Embarques aéreos de Perú"), derecha),
                aviso("Los embarques de moscas estériles de Perú llegan al aeropuerto de Tapachula: una parte se queda ahí para dispersarse "
                        + "desde el aeropuerto y el resto va al centro de empaque.", "azul"),
                kpis, caja(filtros), caja(resultados));
    }

    // ---------- Alta ----------

    static Node formulario() throws SQLException {
        TextField cod = new TextField(Lotes.siguienteCodigo("AER-EMB-"));
        cod.setEditable(false);
        cod.getStyleClass().add("mono");
        TextField guia = new TextField(), vuelo = new TextField(), origen = new TextField("Perú");
        guia.getStyleClass().add("mono");
        TextField llegada = new TextField(LocalDateTime.now().format(Envios.FECHA_HORA));
        TextField cantidad = new TextField(), queda = new TextField(), temp = new TextField("4.0");
        TextArea obs = new TextArea();
        obs.setPrefRowCount(2);
        TextField resp = new TextField(Db.nombre + " · " + Db.empleado + " (usuario en sesión)");
        resp.setEditable(false);
        Label vaCentro = lbl("—", "kpi-n");
        vaCentro.setWrapText(false);
        VBox errores = errores();

        Runnable calcular = () -> {
            try {
                long n = Reglas.entero(cantidad.getText()), a = Reglas.entero(queda.getText());
                vaCentro.setText(Reglas.erroresEmbarque(n, a).isEmpty() ? miles(n - a) : "—");
            } catch (NumberFormatException e) {
                vaCentro.setText("—");
            }
        };
        cantidad.textProperty().addListener((o, a, b) -> calcular.run());
        queda.textProperty().addListener((o, a, b) -> calcular.run());

        var guardar = btn("Guardar embarque", () -> {
            List<String> errs = new ArrayList<>();
            long n = 0, a = 0;
            LocalDateTime f = null;
            BigDecimal t = null;
            try {
                n = Reglas.entero(cantidad.getText());
                a = Reglas.entero(queda.getText());
                f = Envios.leerFechaHora(llegada.getText());
                if (!temp.getText().isBlank()) t = new BigDecimal(temp.getText().trim());
            } catch (NumberFormatException | DateTimeParseException e) {
                errs.add("Revisa las cantidades, la fecha (dd/mm/aaaa hh:mm) y la temperatura.");
            }
            if (guia.getText().isBlank()) errs.add("Falta la guía aérea.");
            if (origen.getText().isBlank()) errs.add("Falta el origen.");
            if (errs.isEmpty()) errs.addAll(Reglas.erroresEmbarque(n, a));
            mostrarErrores(errores, errs);
            if (!errs.isEmpty()) return;
            long cant = n, quedan = a;
            LocalDateTime fecha = f;
            BigDecimal temperatura = t;
            String[] guardado = new String[1];
            try {
                Db.tx(() -> {
                    guardado[0] = s(Db.q1("""
                            INSERT INTO embarque_aereo (guia_aerea, vuelo, origen, fecha_llegada, cantidad, queda_aeropuerto,
                            temperatura, responsable_id, observaciones) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING codigo""",
                            guia.getText().trim(), Programa2.vacio(vuelo), origen.getText().trim(), fecha, cant, quedan,
                            temperatura, Db.responsableId, obs.getText().isBlank() ? null : obs.getText().trim()).get("codigo"));
                    Db.auditar("Registró el embarque " + guardado[0]);
                });
            } catch (SQLException e) {
                mostrarErrores(errores, List.of(Db.mensaje(e)));
                return;
            }
            avisar("Embarque " + guardado[0] + " guardado.");
            ir("embarques");
        });

        VBox va = new VBox(2, lbl("Van al centro de empaque", "kpi-t"), vaCentro);
        va.getStyleClass().addAll("kpi", "kpi-gris");
        VBox form = caja(errores,
                campos(2, campo("Código del embarque", cod, "Se genera solo"), campo("Guía aérea", guia, null),
                        campo("Vuelo", vuelo, "Opcional"), campo("Origen", origen, null),
                        campo("Fecha y hora de llegada", llegada, "dd/mm/aaaa hh:mm"), campo("Temperatura al llegar (°C)", temp, "Opcional"),
                        campo("Moscas que trae el embarque", cantidad, null), campo("Se quedan en el aeropuerto", queda, "Se dispersan desde el aeropuerto")),
                va, campo("Responsable", resp, null), campo("Observaciones (opcional)", obs, null),
                accionesFin(btn("Cancelar", "gris", () -> ir("embarques")), guardar));
        form.setSpacing(18);
        VBox lateral = new VBox(16, aviso("Lo que no se queda en el aeropuerto va al centro de empaque de Tapachula. "
                + "Las dos partes quedan disponibles para que la encargada de dispersión registre los vuelos de la avioneta.", "azul"));
        return new VBox(20, encabezado(titulo("Inicio / Aeropuerto / Nuevo", "Nuevo embarque aéreo de Perú"), lbl("Solo PEA · aeropuerto", "chip")),
                columnas(form, lateral));
    }
}
