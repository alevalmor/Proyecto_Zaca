package moscamed.vista.operativo;

import static moscamed.vista.Componentes.*;
import static moscamed.vista.Formato.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import moscamed.controlador.LoteControlador;
import moscamed.controlador.LoteControlador.EntradaCalidad;
import moscamed.interfaces.controlador.Navegador;
import moscamed.interfaces.vista.Vista;
import moscamed.modelo.ControlCalidad;
import moscamed.modelo.Envio;
import moscamed.modelo.Lote;
import moscamed.modelo.Reglas;
import moscamed.modelo.Sesion;

public final class LoteEstadoVista implements Vista {
    private final LoteControlador control;
    private final Navegador nav;
    private final Sesion sesion = Sesion.actual();
    private final Lote lote;
    private final Map<String, ControlCalidad> calidades;
    private final String etapa;
    private final List<Envio> envios;
    private final List<Lote> todos;

    public LoteEstadoVista(LoteControlador control, Navegador nav, Lote lote, Map<String, ControlCalidad> calidades, String etapa,
                           List<Envio> envios, List<Lote> todos) {
        this.control = control;
        this.nav = nav;
        this.lote = lote;
        this.calidades = calidades;
        this.etapa = etapa;
        this.envios = envios;
        this.todos = todos;
    }

    @Override
    public Node vista() {
        String codigo = lote.codigo();
        HBox titulo = new HBox(12, lbl(codigo, "h1", "mono"), pill(lote.estado()));
        titulo.setAlignment(Pos.CENTER_LEFT);
        var derecha = new HBox(12, selectorLote());
        derecha.setAlignment(Pos.BOTTOM_RIGHT);
        if (sesion.puede("lotes", true) && lote.modificable()) {
            derecha.getChildren().add(btn("Modificar lote", "gris", () -> nav.ir("modificar_lote", codigo)));
        }

        VBox datos = caja(h2("Datos del lote"), datos(3,
                "Fecha de producción", fecha(lote.fechaProduccion()), "Fecha de irradiación", fecha(lote.fechaIrradiacion()),
                "Dosis", lote.dosisGy() + " Gy", "Pupas", miles(lote.cantidadPupas()),
                "Cepa", lote.cepa(), "Tinte", lote.tinte(),
                "Centro destino", lote.centroClave() + " · " + lote.centroNombre(),
                "Registró", lote.registro(),
                "Enviadas / dispersadas", millones(lote.enviadas()) + " / " + millones(lote.liberadas())));
        if (lote.observaciones() != null) datos.getChildren().add(lbl(lote.observaciones(), "gris"));

        Node calidad = caja(h2("Control de calidad"), fila(14, tarjetaCalidad("planta", calidades.get("PLANTA"), "tarjeta-verde"),
                tarjetaCalidad("empaque", calidades.get("EMPAQUE"), "tarjeta-azul")));

        List<Node[]> filas = new ArrayList<>();
        for (var e : envios) {
            String ce = e.codigo();
            filas.add(new Node[]{link(ce, () -> nav.ir("estado_envio", ce)), mono(e.matricula()),
                    t(miles(e.cantidad())), t(e.zonas()), t(e.liberadas() > 0 ? miles(e.liberadas()) : "—"), pill(e.estado())});
        }
        Node tablaEnvios = caja(h2("Envíos y dispersiones de este lote"),
                tabla(List.of("Envío", "Camioneta", "Enviadas", "Zonas", "Dispersadas", "Estado"), filas, Set.of(2, 4), "Este lote aún no tiene envíos."));

        VBox lateral = new VBox(16);
        if (sesion.puede("estado_lote", true)) lateral.getChildren().add(formularioCalidad(calidades.get(etapa)));
        lateral.getChildren().add(aviso("El estado del lote no se escribe a mano: el sistema lo calcula con sus pruebas, envíos y liberaciones. "
                + "Así las dos sedes nunca lo editan al mismo tiempo.", "ambar"));

        return new VBox(18, encabezado(new VBox(4, lbl("Lotes / Estado", "ruta"), titulo), derecha),
                columnas(new VBox(18, datos, calidad, tablaEnvios), lateral));
    }

    private Node selectorLote() {
        String actual = lote.codigo();
        ComboBox<Lote> lotes = new ComboBox<>();
        lotes.getItems().addAll(todos);
        lotes.setConverter(convertidor(l -> l.codigo() + " · " + l.estado() + " · " + fecha(l.fechaProduccion())));
        lotes.setVisibleRowCount(12);
        for (var l : todos) if (actual.equals(l.codigo())) lotes.setValue(l);
        lotes.setOnAction(e -> {
            var elegido = lotes.getValue();
            if (elegido != null && !actual.equals(elegido.codigo())) nav.ir("estado_lote", elegido.codigo());
        });
        return campo("Ver otro lote", lotes, null);
    }

    private static Node tarjetaCalidad(String etapa, ControlCalidad c, String clase) {
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox cab = new HBox(lbl("Etapa " + etapa, "dato"), hueco,
                c == null ? pill("SIN PRUEBA", "") : pill(c.resultado().toUpperCase()));
        String texto = c != null
                ? "Emergencia " + c.emergencia() + " % · Vuelo " + c.vuelo() + " %\nEsterilidad " + c.esterilidad()
                  + " % · Peso " + c.pesoMg() + " mg\n" + fecha(c.fecha()) + " · " + c.numEmpleado()
                : etapa.equals("empaque") ? "Es opcional. La registra el centro de empaque al recibir el lote."
                : "Pendiente. Sin ella el lote no se puede enviar.";
        VBox v = new VBox(8, cab, lbl(texto, "gris"));
        v.getStyleClass().add("tarjeta");
        if (c != null) v.getStyleClass().add(clase);
        v.setMaxWidth(Double.MAX_VALUE);
        return v;
    }

    private Node formularioCalidad(ControlCalidad actual) {
        TextField em = new TextField(actual != null ? s(actual.emergencia()) : "");
        TextField vu = new TextField(actual != null ? s(actual.vuelo()) : "");
        TextField es = new TextField(actual != null ? s(actual.esterilidad()) : "");
        TextField pe = new TextField(actual != null ? s(actual.pesoMg()) : "");
        TextArea obs = new TextArea(actual != null ? s(actual.observaciones()) : "");
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

        var guardar = btn(actual != null ? "Guardar cambios de la prueba" : "Registrar control de calidad", () -> mostrarErrores(errores,
                control.guardarCalidad(lote, etapa, new EntradaCalidad(em.getText(), vu.getText(), es.getText(), pe.getText(), obs.getText()))));
        guardar.setMaxWidth(Double.MAX_VALUE);
        String tituloForm = etapa.equals("PLANTA") ? (actual != null ? "Modificar prueba de la planta" : "Registrar prueba de la planta") : "Registrar prueba al recibir";
        return caja(new VBox(4, h2(tituloForm), lbl(etapa.equals("PLANTA") ? "Etapa PLANTA · solo la planta puede cambiarla"
                        : "Etapa EMPAQUE · se guarda con id par del centro de empaque", "gris", "chico")), errores,
                campos(2, campo("Emergencia (%)", em, null), campo("Vuelo (%)", vu, null), campo("Esterilidad (%)", es, null), campo("Peso pupa (mg)", pe, null)),
                caja, campo("Observaciones", obs, null), guardar);
    }
}
