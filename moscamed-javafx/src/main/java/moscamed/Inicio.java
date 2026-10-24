package moscamed;

import static moscamed.Programa1.ir;
import static moscamed.Programa1.puede;
import static moscamed.Ui.*;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Pantallas de inicio según el perfil (02 y 03 del diseño). */
final class Inicio {
    private Inicio() {}

    static Node saludo(String sede, String sincronizado) {
        Label chip = lbl("●  " + sincronizado, "chip");
        Label perfil = lbl("Perfil " + Db.perfil, "chip", "chip-blanco");
        return Ui.encabezado(titulo(sede, "Buen día, " + Db.nombreCorto), new HBox(8, chip, perfil));
    }

    static Node pes() throws SQLException {
        var k = Db.q1("""
                SELECT count(*) FILTER (WHERE fecha_produccion >= date_trunc('week', current_date)) AS semana,
                       coalesce(sum(cantidad_pupas) FILTER (WHERE fecha_produccion >= date_trunc('week', current_date)), 0) AS pupas,
                       count(*) FILTER (WHERE estado = 'PRODUCIDO') AS sin_cc,
                       count(*) FILTER (WHERE estado = 'RECHAZADO' AND fecha_produccion >= date_trunc('month', current_date)) AS rechazados
                FROM v_lote""");
        var rechazo = Db.q1("""
                SELECT l.codigo, c.vuelo < 65 AS vuelo, c.emergencia < 75 AS emergencia FROM v_lote l
                JOIN control_calidad c ON c.lote_id = l.id AND NOT c.aprobado
                WHERE l.estado = 'RECHAZADO' AND l.fecha_produccion >= date_trunc('month', current_date)
                ORDER BY l.fecha_produccion DESC LIMIT 1""");
        var lotes = Db.q("SELECT codigo, fecha_produccion, cantidad_pupas, estado FROM v_lote ORDER BY fecha_produccion DESC, codigo DESC LIMIT 6");
        var pendientes = Db.q("SELECT codigo, fecha_irradiacion FROM v_lote WHERE estado = 'PRODUCIDO' ORDER BY fecha_produccion DESC LIMIT 3");

        String motivo = rechazo == null ? "ninguno" : rechazo.get("codigo") + " · "
                + ((Boolean) rechazo.get("vuelo") ? "vuelo bajo" : (Boolean) rechazo.get("emergencia") ? "emergencia baja" : "esterilidad baja");
        Node kpis = fila(14,
                kpi("Lotes esta semana", k.get("semana"), "producidos desde el lunes", null),
                kpi("Sin control de calidad", k.get("sin_cc"), "no se pueden enviar aún", "ambar"),
                kpi("Rechazados del mes", k.get("rechazados"), motivo, "rojo"),
                kpi("Pupas esta semana", millones(k.get("pupas")), "millones de pupas", null));

        var acciones = acciones();
        if (puede("nuevo_lote", true)) acciones.getChildren().add(btn("+  Nuevo lote", () -> ir("nuevo_lote")));
        if (!pendientes.isEmpty()) {
            String codigo = s(pendientes.get(0).get("codigo"));
            acciones.getChildren().add(btn("Registrar control de calidad", "borde", () -> ir("estado_lote", codigo)));
        }
        acciones.getChildren().add(btn("Ver todos los lotes", "gris", () -> ir("lotes")));

        List<Node[]> filas = new ArrayList<>();
        for (var l : lotes) {
            String codigo = s(l.get("codigo"));
            filas.add(new Node[]{mono(codigo), t(fecha(l.get("fecha_produccion"))), t(miles(l.get("cantidad_pupas"))),
                    pill(s(l.get("estado"))), link("Ver", () -> ir("estado_lote", codigo))});
        }
        VBox ultimos = caja(encabezado(lbl("Últimos lotes", "h2", "h2-grande"), link("Ver lista completa", () -> ir("lotes"))),
                tabla(List.of("Código", "Producción", "Pupas", "Estado", ""), filas, Set.of(2, 4), "Todavía no hay lotes."));

        VBox lista = new VBox(10);
        for (var p : pendientes) {
            String codigo = s(p.get("codigo"));
            lista.getChildren().add(item(new VBox(2, mono(codigo), lbl("Irradiado " + fecha(p.get("fecha_irradiacion")), "gris", "chico")),
                    lbl("Registrar", "ambar", "dato"), "item-pendiente", () -> ir("estado_lote", codigo)));
        }
        if (pendientes.isEmpty()) lista.getChildren().add(lbl("Todos los lotes tienen su control de calidad.", "gris"));
        VBox lateral = new VBox(18,
                caja(lbl("Pendientes de control de calidad", "h2", "h2-grande"), lista),
                caja(lbl("Lo que puedes hacer como PES", "h2", "h2-grande"),
                        lbl("Registrar y modificar lotes, y su control de calidad en la planta.", "gris"),
                        lbl("Consultar envíos, embarques, dispersiones y zonas prioritarias sin modificarlos: esos datos son de Tapachula.", "gris")));
        return new VBox(22, saludo("Planta esterilizadora · Metapa", "Sincronizado con el centro de empaque"), kpis, acciones,
                columnas(ultimos, lateral));
    }

    static Node pem() throws SQLException {
        var k = Db.q1("""
                SELECT (SELECT count(*) FROM v_lote WHERE estado = 'APROBADO') AS por_despachar,
                       (SELECT count(*) FROM v_envio WHERE estado = 'EN CAMINO') AS en_camino,
                       (SELECT coalesce(sum(quedan), 0) FROM v_existencia WHERE punto = 'EMPAQUE') AS en_centro""");
        var envios = Db.q("""
                SELECT e.codigo, l.codigo AS lote, v.matricula, e.cantidad FROM v_envio e
                JOIN lote_produccion l ON l.id = e.lote_id JOIN vehiculo v ON v.id = e.vehiculo_id
                WHERE e.estado = 'EN CAMINO' ORDER BY e.fecha_salida DESC LIMIT 5""");
        var altas = Zonas.prioridad(14, LocalDate.now()).stream().filter(z -> "ALTA".equals(z.get("prioridad"))).toList();

        Node kpis = fila(14,
                kpi("Lotes aprobados por despachar", k.get("por_despachar"), "listos en la planta de Metapa", null),
                kpi("Envíos en camino", k.get("en_camino"), "falta registrar su llegada", "ambar"),
                kpi("Moscas en el centro", millones(k.get("en_centro")), "por dispersar (Metapa y Perú)", null),
                kpi("Zonas con prioridad alta", altas.size(), "hubo capturas silvestres", "rojo"));

        var acciones = acciones();
        if (puede("nuevo_envio", true)) acciones.getChildren().add(btn("+  Nuevo envío", () -> ir("nuevo_envio")));
        if (!envios.isEmpty()) {
            String codigo = s(envios.get(0).get("codigo"));
            acciones.getChildren().add(btn("Registrar llegada", "borde", () -> ir("estado_envio", codigo)));
        }
        acciones.getChildren().add(btn("Calidad al recibir un lote", "gris", () -> ir("estado_lote")));

        List<Node[]> filas = new ArrayList<>();
        for (var e : envios) {
            String codigo = s(e.get("codigo"));
            filas.add(new Node[]{mono(codigo), mono(e.get("lote")), mono(e.get("matricula")),
                    t(miles(e.get("cantidad"))), link("Recibir", () -> ir("estado_envio", codigo))});
        }
        VBox camino = caja(encabezado(lbl("Envíos en camino desde Metapa", "h2", "h2-grande"), link("Ver todos los envíos", () -> ir("envios"))),
                tabla(List.of("Envío", "Lote", "Camioneta", "Moscas", ""), filas, Set.of(3, 4), "No hay envíos en camino."));

        VBox lista = new VBox(10);
        for (var z : altas.subList(0, Math.min(4, altas.size()))) {
            lista.getChildren().add(item(new VBox(2, lbl(z.get("clave") + " · " + z.get("nombre"), "dato"),
                    lbl("MTD " + dec(z.get("mtd"), 2) + " · " + z.get("silvestres") + " silvestres", "gris", "chico")), pill("ALTA"), null, null));
        }
        if (altas.isEmpty()) lista.getChildren().add(lbl("Sin capturas silvestres en el periodo.", "gris"));
        VBox zonas = caja(encabezado(lbl("Dónde dispersar más", "h2", "h2-grande"), link("Ver reporte", () -> ir("zonas"))),
                lbl("Según el trampeo de los últimos 14 días (base de trampeo vía FDW)", "gris", "chico"), lista);
        return new VBox(22, saludo("Centro de empaque · Tapachula", "Sincronizado con la planta"), kpis, acciones, columnas(camino, zonas));
    }

    /** Renglón de lista con texto a la izquierda y algo a la derecha; si hay acción, se puede pulsar. */
    static HBox item(Node izquierda, Node derecha, String clase, Runnable accion) {
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox h = new HBox(10, izquierda, hueco, derecha);
        h.setAlignment(Pos.CENTER_LEFT);
        h.getStyleClass().add("item");
        if (clase != null) h.getStyleClass().add(clase);
        if (accion != null) {
            h.setOnMouseClicked(e -> accion.run());
            h.setFocusTraversable(true);
            h.setOnKeyPressed(e -> {
                if (e.getCode() == javafx.scene.input.KeyCode.ENTER || e.getCode() == javafx.scene.input.KeyCode.SPACE) accion.run();
            });
        } else {
            h.setStyle("-fx-cursor: default;");
        }
        return h;
    }
}
