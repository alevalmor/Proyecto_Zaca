package moscamed.vista.operativo;

import static moscamed.vista.Componentes.*;
import static moscamed.vista.Formato.*;

import java.time.LocalDate;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import moscamed.controlador.LoteControlador;
import moscamed.controlador.LoteControlador.EntradaLote;
import moscamed.interfaces.controlador.Navegador;
import moscamed.interfaces.vista.Vista;
import moscamed.modelo.CentroEmpaque;
import moscamed.modelo.Lote;
import moscamed.modelo.Sesion;

public final class LoteFormularioVista implements Vista {
    private final LoteControlador control;
    private final Navegador nav;
    private final Lote lote;
    private final String codigo;
    private final List<CentroEmpaque> centros;

    public LoteFormularioVista(LoteControlador control, Navegador nav, Lote lote, String codigo, List<CentroEmpaque> centros) {
        this.control = control;
        this.nav = nav;
        this.lote = lote;
        this.codigo = codigo;
        this.centros = centros;
    }

    @Override
    public Node vista() {
        LocalDate hoy = LocalDate.now();
        TextField cod = fijo(codigo);
        cod.getStyleClass().add("mono");
        TextField pupas = new TextField(lote != null ? s(lote.cantidadPupas()) : "");
        DatePicker fp = new DatePicker(lote != null ? lote.fechaProduccion() : hoy);
        DatePicker fi = new DatePicker(lote != null ? lote.fechaIrradiacion() : hoy);
        TextField dosis = new TextField(lote != null ? s(lote.dosisGy()) : "120.0");
        ComboBox<String> cepa = combo(Lote.CEPAS, lote != null ? lote.cepa() : null);
        ComboBox<String> tinte = combo(Lote.TINTES, lote != null ? lote.tinte() : null);
        ComboBox<CentroEmpaque> centro = new ComboBox<>();
        centro.getItems().addAll(centros);
        centro.setConverter(convertidor(CentroEmpaque::etiqueta));
        for (var c : centros) if (lote != null && c.id() == lote.centroEmpaqueId()) centro.setValue(c);
        if (centro.getValue() == null && !centro.getItems().isEmpty()) centro.getSelectionModel().select(0);
        TextField resp = fijo(Sesion.actual().firmaEnSesion());
        TextArea obs = new TextArea(lote != null ? s(lote.observaciones()) : "");
        obs.setPrefRowCount(3);
        VBox errores = errores();

        var guardar = btn(lote != null ? "Guardar cambios" : "Guardar lote", () -> mostrarErrores(errores, control.guardar(lote,
                new EntradaLote(pupas.getText(), fp.getValue(), fi.getValue(), dosis.getText(), cepa.getValue(), tinte.getValue(),
                        centro.getValue(), obs.getText()))));

        VBox form = caja(lbl("Datos de producción", "h2", "h2-grande"), errores,
                campos(2, campo("Código del lote", cod, lote != null ? "No cambia al modificar" : "Se genera solo con el prefijo de la planta"),
                        campo("Cantidad de pupas", pupas, null), campo("Fecha de producción", fp, null), campo("Fecha de irradiación", fi, null),
                        campo("Dosis de irradiación (Gy)", dosis, null), campo("Cepa", cepa, null), campo("Color de tinte", tinte, null),
                        campo("Centro de empaque destino", centro, null)),
                campo("Responsable", resp, null), campo("Observaciones (opcional)", obs, null),
                accionesFin(btn("Cancelar", "gris", () -> nav.ir("lotes")), guardar));
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
}
