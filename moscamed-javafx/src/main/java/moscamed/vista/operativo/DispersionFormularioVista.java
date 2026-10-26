package moscamed.vista.operativo;

import static moscamed.vista.Componentes.*;
import static moscamed.vista.Formato.*;

import java.time.LocalDateTime;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import moscamed.controlador.DispersionControlador;
import moscamed.controlador.DispersionControlador.EntradaDispersion;
import moscamed.interfaces.controlador.Navegador;
import moscamed.interfaces.vista.Vista;
import moscamed.modelo.Existencia;
import moscamed.modelo.InformeDispersion;
import moscamed.modelo.Sesion;
import moscamed.modelo.Zona;

public final class DispersionFormularioVista implements Vista {
    private final DispersionControlador control;
    private final Navegador nav;
    private final InformeDispersion original;
    private final List<Existencia> existencias;
    private final Existencia preseleccion;
    private final List<Zona> zonas;

    public DispersionFormularioVista(DispersionControlador control, Navegador nav, InformeDispersion original,
                                     List<Existencia> existencias, Existencia preseleccion, List<Zona> zonas) {
        this.control = control;
        this.nav = nav;
        this.original = original;
        this.existencias = existencias;
        this.preseleccion = preseleccion;
        this.zonas = zonas;
    }

    @Override
    public Node vista() {
        ComboBox<Existencia> fuente = new ComboBox<>();
        fuente.setValue(preseleccion);
        fuente.getItems().addAll(existencias);
        fuente.setConverter(convertidor(e -> e.codigo() + " · " + Existencia.nombrePunto(e.punto()) + " · quedan " + miles(e.quedan())));
        if (fuente.getValue() == null && !existencias.isEmpty()) fuente.setValue(existencias.get(0));
        ComboBox<Zona> zona = new ComboBox<>();
        zona.getItems().addAll(zonas);
        zona.setConverter(convertidor(z -> z.clave() + " · " + z.nombre() + " (" + s(z.prioridad()).toLowerCase() + ")"));
        for (var z : zonas) if (original != null && z.id() == original.zonaId()) zona.setValue(z);
        if (zona.getValue() == null && !zonas.isEmpty()) zona.setValue(zonas.get(0));

        TextField fecha = new TextField(LocalDateTime.now().format(FECHA_HORA));
        TextField cantidad = new TextField(fuente.getValue() == null ? "" : s(fuente.getValue().quedan()));
        fuente.valueProperty().addListener((o, a, b) -> cantidad.setText(b == null ? "" : s(b.quedan())));
        TextField sup = new TextField(), alt = new TextField(), avioneta = new TextField(), piloto = new TextField();
        avioneta.setPromptText("XB-TWE");
        avioneta.getStyleClass().add("mono");
        TextArea obs = new TextArea();
        obs.setPrefRowCount(2);
        if (original != null) {
            fecha.setText(fechaHora(original.fecha()));
            cantidad.setText(s(original.cantidad()));
            sup.setText(s(original.superficieHa()));
            alt.setText(s(original.altitudM()));
            avioneta.setText(s(original.avioneta()));
            piloto.setText(s(original.piloto()));
            obs.setText(s(original.observaciones()));
        }
        TextField resp = fijo(Sesion.actual().firmaEnSesion());
        VBox errores = errores();

        var guardar = btn(original == null ? "Guardar informe" : "Guardar cambios", () -> mostrarErrores(errores, control.guardar(original,
                new EntradaDispersion(fuente.getValue(), zona.getValue(), fecha.getText(), cantidad.getText(), sup.getText(), alt.getText(),
                        avioneta.getText(), piloto.getText(), obs.getText()))));

        VBox form = caja(errores);
        if (existencias.isEmpty()) form.getChildren().add(aviso("No hay moscas por dispersar: falta que el centro reciba un envío de Metapa o que llegue un embarque de Perú.", "ambar"));
        form.getChildren().addAll(campo("Moscas de", fuente, "Envío recibido de Metapa o embarque de Perú, y dónde están"),
                campos(2, campo("Zona", zona, "O elígela en las zonas sugeridas"), campo("Fecha y hora del vuelo", fecha, "dd/mm/aaaa hh:mm"),
                        campo("Moscas dispersadas", cantidad, null), campo("Superficie cubierta (ha)", sup, null),
                        campo("Altitud de vuelo (m s. n. m.)", alt, "Obligatoria: la dispersión es aérea"), campo("Avioneta (matrícula)", avioneta, null),
                        campo("Piloto", piloto, "Como viene en el informe de vuelo"), campo("Capturó", resp, null)),
                campo("Observaciones (opcional)", obs, null),
                accionesFin(btn("Cancelar", "gris", () -> nav.ir("dispersiones")), guardar));
        form.setSpacing(18);

        VBox sugeridas = new VBox(10);
        for (var z : zonas.subList(0, Math.min(5, zonas.size()))) {
            sugeridas.getChildren().add(item(new VBox(2, lbl(z.clave() + " · " + z.nombre(), "dato"),
                            lbl("MTD " + dec(z.mtd(), 2) + " · sugeridas " + millones(z.sugeridas()), "gris", "chico")),
                    pill(s(z.prioridad())), null, () -> zona.setValue(z)));
        }
        VBox lateral = caja(h2("Zonas sugeridas por el trampeo"),
                lbl("Ordenadas por prioridad (base de trampeo vía FDW, últimos 14 días). Pulsa una para elegirla.", "gris", "chico"), sugeridas);
        return new VBox(20, encabezado(original == null ? titulo("Inicio / Dispersión / Nuevo", "Nuevo informe de dispersión aérea")
                        : titulo("Inicio / Dispersión / Modificar", "Modificar informe de dispersión aérea"), lbl("PED y PEM · dispersión", "chip")),
                columnas(form, lateral));
    }
}
