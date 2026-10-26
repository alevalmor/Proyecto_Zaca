package moscamed.vista.operativo;

import static moscamed.vista.Componentes.*;
import static moscamed.vista.Formato.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import moscamed.controlador.EnvioControlador;
import moscamed.interfaces.controlador.Navegador;
import moscamed.interfaces.vista.Vista;
import moscamed.modelo.Envio;
import moscamed.modelo.Existencia;
import moscamed.modelo.InformeDispersion;
import moscamed.modelo.Sesion;

public final class EnvioEstadoVista implements Vista {
    private final EnvioControlador control;
    private final Navegador nav;
    private final Sesion sesion = Sesion.actual();
    private final Envio envio;
    private final List<InformeDispersion> dispersiones;

    public EnvioEstadoVista(EnvioControlador control, Navegador nav, Envio envio, List<InformeDispersion> dispersiones) {
        this.control = control;
        this.nav = nav;
        this.envio = envio;
        this.dispersiones = dispersiones;
    }

    @Override
    public Node vista() {
        String codigo = envio.codigo(), est = envio.estado();
        long cantidad = envio.cantidad(), liberadas = envio.liberadas();
        HBox titulo = new HBox(12, lbl(codigo, "h1", "mono"), pill(est));
        titulo.setAlignment(Pos.CENTER_LEFT);
        var derecha = new HBox(12);
        if (envio.enCamino() && sesion.puede("envios", true)) {
            derecha.getChildren().addAll(btn("Cancelar envío", "gris", () -> {
                if (confirmar("¿Cancelar el envío " + codigo + "? No se puede deshacer.")) control.cancelar(codigo);
            }), btn("Modificar envío", "gris", () -> nav.ir("modificar_envio", codigo)));
        }
        if (est.equals("RECIBIDO") && sesion.puede("nueva_dispersion", true)) {
            derecha.getChildren().add(btn("Registrar dispersión", () -> nav.ir("nueva_dispersion", Existencia.clave("ENVIO", codigo, "EMPAQUE"))));
        }

        ProgressBar progreso = new ProgressBar(cantidad == 0 ? 0 : (double) liberadas / cantidad);
        progreso.getStyleClass().add("progreso");
        progreso.setMaxWidth(Double.MAX_VALUE);
        String loteCod = envio.loteCodigo();
        var datos = datos(4, "Lote", loteCod, "Camioneta", envio.matricula() + " · " + envio.modelo(),
                "Salida de Metapa", fechaHora(envio.fechaSalida()), "Llegada a Tapachula", fechaHora(envio.recibido()),
                "Moscas enviadas", miles(cantidad), "Dispersadas", miles(liberadas), "Temperatura", envio.temperatura() + " °C",
                "Despachó", envio.registro());
        Node resumen = caja(datos, link("Ver lote " + loteCod, () -> nav.ir("estado_lote", loteCod)), progreso);

        List<Node[]> filas = new ArrayList<>();
        for (var li : dispersiones) {
            filas.add(new Node[]{t(li.zonaClave() + " · " + li.zonaNombre()), t(fechaHora(li.fecha())), mono(li.avioneta()),
                    t(miles(li.cantidad())), t(miles(li.superficieHa()) + " ha"), t(miles(li.altitudM()) + " m")});
        }
        VBox principal = new VBox(18, caja(h2("Dispersiones aéreas con moscas de este envío"),
                tabla(List.of("Zona", "Fecha", "Avioneta", "Moscas", "Superficie", "Altitud"), filas, Set.of(3, 4, 5),
                        envio.cancelado() ? "Este envío fue cancelado." : "Todavía no hay dispersiones con este envío.")));

        VBox lateral = new VBox(16);
        if (envio.enCamino() && sesion.puede("estado_envio", true)) lateral.getChildren().add(formularioLlegada());
        lateral.getChildren().add(aviso("Recorrido: la planta de Metapa produce el lote, la camioneta lo lleva a Tapachula y, al llegar, "
                + "la encargada de dispersión registra los informes de la avioneta. Por tierra no se dispersa.", "azul"));
        return new VBox(18, encabezado(new VBox(4, lbl("Envíos / Estado", "ruta"), titulo), derecha), resumen, columnas(principal, lateral));
    }

    private Node formularioLlegada() {
        TextField llegada = new TextField(LocalDateTime.now().format(FECHA_HORA));
        VBox errores = errores();
        var recibir = btn("Registrar llegada", () -> mostrarErrores(errores, control.recibir(envio, llegada.getText())));
        recibir.setMaxWidth(Double.MAX_VALUE);
        return caja(h2("Llegada al centro de empaque"), errores, campo("Fecha y hora de llegada", llegada, "dd/mm/aaaa hh:mm"), recibir);
    }
}
