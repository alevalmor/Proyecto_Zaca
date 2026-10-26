package moscamed.vista.operativo;

import static moscamed.vista.Componentes.*;
import static moscamed.vista.Formato.*;

import java.time.LocalDateTime;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import moscamed.controlador.EnvioControlador;
import moscamed.controlador.EnvioControlador.EntradaEnvio;
import moscamed.interfaces.controlador.Navegador;
import moscamed.interfaces.vista.Vista;
import moscamed.modelo.Envio;
import moscamed.modelo.LoteDisponible;
import moscamed.modelo.Reglas;
import moscamed.modelo.Sesion;
import moscamed.modelo.Vehiculo;

public final class EnvioFormularioVista implements Vista {
    private final EnvioControlador control;
    private final Navegador nav;
    private final Envio envio;
    private final List<LoteDisponible> lotes;
    private final List<Vehiculo> vehiculos;

    public EnvioFormularioVista(EnvioControlador control, Navegador nav, Envio envio, List<LoteDisponible> lotes, List<Vehiculo> vehiculos) {
        this.control = control;
        this.nav = nav;
        this.envio = envio;
        this.lotes = lotes;
        this.vehiculos = vehiculos;
    }

    @Override
    public Node vista() {
        ComboBox<LoteDisponible> lote = new ComboBox<>();
        lote.getItems().addAll(lotes);
        lote.setConverter(convertidor(l -> l.codigo() + " · aprobado · " + miles(l.disponibles()) + " disponibles"));
        ComboBox<Vehiculo> veh = new ComboBox<>();
        veh.getItems().addAll(vehiculos);
        veh.setConverter(convertidor(v -> v.matricula() + " · " + v.modelo()
                + " · cabe " + miles(v.capacidad()) + (v.activo() ? "" : " (fuera de servicio)")));
        for (var l : lotes) if (envio != null && l.id() == envio.loteId()) lote.setValue(l);
        for (var v : vehiculos) if (envio != null && v.id() == envio.vehiculoId()) veh.setValue(v);
        if (lote.getValue() == null && !lotes.isEmpty()) lote.setValue(lotes.get(0));
        if (veh.getValue() == null && !vehiculos.isEmpty()) veh.setValue(vehiculos.get(0));
        TextField cantidad = new TextField(envio != null ? s(envio.cantidad()) : "");
        TextField salida = new TextField((envio != null ? envio.fechaSalida() : LocalDateTime.now()).format(FECHA_HORA));
        TextField temp = new TextField(envio != null ? s(envio.temperatura()) : "3.0");
        TextField resp = fijo(Sesion.actual().firmaEnSesion());

        Label pupas = lbl("—", "kpi-n"), enviadas = lbl("—", "kpi-n"), cc = lbl("—", "kpi-n");
        pupas.setWrapText(false);
        enviadas.setWrapText(false);
        VBox checks = new VBox(12);
        VBox errores = errores();
        var guardar = btn(envio != null ? "Guardar cambios" : "Guardar envío", () -> mostrarErrores(errores, control.guardar(envio,
                new EntradaEnvio(lote.getValue(), veh.getValue(), cantidad.getText(), salida.getText(), temp.getText()))));

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
                pupas.setText(miles(l.cantidadPupas()));
                enviadas.setText(miles(l.enviadas()));
                cc.setText("Aprobado " + fecha(l.fechaCalidad()));
            }
            boolean cabe = v != null && n > 0 && n <= v.capacidad();
            boolean alcanza = l != null && n <= l.disponibles();
            boolean activo = v != null && v.activo();
            checks.getChildren().setAll(
                    marcado(l != null, "Lote aprobado y sin rechazos", "Solo se listan lotes aprobados"),
                    marcado(cabe, "Cabe en el vehículo", v == null ? "" : miles(n) + " de " + miles(v.capacidad()) + " moscas"),
                    marcado(alcanza, "No supera lo que queda del lote", l == null ? "" : "Quedan " + miles(l.disponibles()) + " pupas disponibles"),
                    marcado(activo, "Vehículo activo", v == null ? "" : v.matricula() + (activo ? " en servicio" : " fuera de servicio")));
            guardar.setDisable(!(l != null && cabe && alcanza && activo));
        };
        lote.valueProperty().addListener((o, a, b) -> revisar.run());
        veh.valueProperty().addListener((o, a, b) -> revisar.run());
        cantidad.textProperty().addListener((o, a, b) -> revisar.run());
        revisar.run();

        VBox i1 = new VBox(2, lbl("Pupas del lote", "kpi-t"), pupas), i2 = new VBox(2, lbl("Ya enviadas", "kpi-t"), enviadas),
                i3 = new VBox(2, lbl("Calidad planta", "kpi-t"), cc);
        for (VBox i : List.of(i1, i2, i3)) i.getStyleClass().addAll("kpi", "kpi-gris");
        VBox form = caja(errores);
        if (lotes.isEmpty()) form.getChildren().add(aviso("No hay lotes aprobados con pupas disponibles. La planta debe registrar su control de calidad primero.", "ambar"));
        form.getChildren().addAll(campo("Lote", lote, "Solo aparecen lotes aprobados en la planta y sin rechazos"), fila(14, i1, i2, i3),
                campos(2, campo("Camioneta", veh, "Traslado terrestre Metapa → Tapachula"), campo("Moscas a enviar", cantidad, null),
                        campo("Fecha y hora de salida de Metapa", salida, "dd/mm/aaaa hh:mm"), campo("Temperatura de salida (°C)", temp, null)),
                campo("Responsable", resp, null), accionesFin(btn("Cancelar", "gris", () -> nav.ir("envios")), guardar));
        form.setSpacing(18);
        VBox lateral = new VBox(16, caja(h2("Validación del envío"), checks),
                aviso("Por tierra solo se trasladan las pupas de la planta de Metapa al centro de empaque de Tapachula; no se dispersan. "
                        + "Al llegar, el centro registra la llegada y la encargada de dispersión ya puede usarlas.", "azul"));
        return new VBox(20, encabezado(titulo("Inicio / Envíos / " + (envio != null ? "Modificar" : "Nuevo"),
                envio != null ? "Modificar envío " + envio.codigo() : "Nuevo envío"), lbl("Solo PES · planta Metapa", "chip")), columnas(form, lateral));
    }
}
