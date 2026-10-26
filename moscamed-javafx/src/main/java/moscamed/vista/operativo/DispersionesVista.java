package moscamed.vista.operativo;

import static moscamed.vista.Componentes.*;
import static moscamed.vista.Formato.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import moscamed.controlador.DispersionControlador;
import moscamed.interfaces.controlador.Navegador;
import moscamed.interfaces.vista.VistaPaginada;
import moscamed.modelo.Existencia;
import moscamed.modelo.InformeDispersion;
import moscamed.modelo.Pagina;
import moscamed.modelo.ResumenDispersion;
import moscamed.modelo.Sesion;

public final class DispersionesVista implements VistaPaginada<InformeDispersion> {
    private final DispersionControlador control;
    private final Navegador nav;
    private final Sesion sesion = Sesion.actual();
    private final ResumenDispersion resumen;
    private final List<Existencia> existencias;
    private final TextField q = new TextField();
    private final ComboBox<String> lugar = combo(List.of("Aeropuerto y centro de empaque", "Aeropuerto", "Centro de empaque"), null);
    private final VBox resultados = new VBox();
    private final int[] pagina = {1};

    public DispersionesVista(DispersionControlador control, Navegador nav, ResumenDispersion resumen, List<Existencia> existencias) {
        this.control = control;
        this.nav = nav;
        this.resumen = resumen;
        this.existencias = existencias;
    }

    @Override
    public Node vista() {
        q.setPromptText("Avioneta, zona, envío o embarque");
        cargar();
        Runnable filtrar = () -> { pagina[0] = 1; cargar(); };
        q.setOnAction(e -> filtrar.run());

        Node kpis = fila(14, kpi("Dispersadas este mes", millones(resumen.dispersadasMes()), resumen.informesMes() + " informes de vuelo", null),
                kpi("Por dispersar en el centro", millones(resumen.enEmpaque()), "envíos de Metapa y embarques", "ambar"),
                kpi("Por dispersar en el aeropuerto", millones(resumen.enAeropuerto()), "embarques de Perú", "ambar"));

        List<Node[]> filas = new ArrayList<>();
        for (var e : existencias) {
            String c = e.clave();
            filas.add(new Node[]{mono(e.codigo()), t((e.deEnvio() ? "Lote " : "Guía ") + e.referencia()),
                    t(Existencia.nombrePunto(e.punto())), t(fechaHora(e.desde())), t(miles(e.recibidas())), t(miles(e.quedan())),
                    sesion.puede("nueva_dispersion", true) ? link("Dispersar", () -> nav.ir("nueva_dispersion", c)) : t("")});
        }
        Node tablaExistencias = caja(h2("Moscas listas para dispersar"),
                tabla(List.of("Viene de", "Referencia", "Están en", "Desde", "Recibidas", "Quedan", ""), filas, Set.of(4, 5, 6),
                        "No hay moscas por dispersar: falta que lleguen envíos o embarques."));

        HBox filtros = filtros(campo("Buscar", q, null), campo("Salieron de", lugar, null), btn("Filtrar", "borde", filtrar));
        var derecha = new HBox();
        if (sesion.puede("nueva_dispersion", true)) derecha.getChildren().add(btn("+  Nuevo informe", () -> nav.ir("nueva_dispersion")));
        return new VBox(18, encabezado(titulo("Inicio / Dispersión aérea", "Informes de dispersión"), derecha),
                aviso("La dispersión es solo aérea. Cada informe es lo que reporta la avioneta: zona, moscas, superficie y altitud.", "azul"),
                kpis, tablaExistencias, caja(filtros), caja(resultados));
    }

    private void cargar() {
        int i = lugar.getSelectionModel().getSelectedIndex();
        control.buscar(q.getText(), i == 1 ? "AEROPUERTO" : i == 2 ? "EMPAQUE" : null, pagina[0], this);
    }

    @Override
    public void mostrar(Pagina<InformeDispersion> encontrados) {
        List<Node[]> nodos = new ArrayList<>();
        for (var li : encontrados.filas()) {
            String envio = li.envioCodigo();
            Node fuente = envio != null ? link(envio, () -> nav.ir("estado_envio", envio)) : mono(li.embarqueCodigo());
            String id = String.valueOf(li.id());
            HBox acc = new HBox(12);
            if (sesion.puede("dispersiones", true)) {
                String descripcion = li.zonaClave() + " del " + fechaHora(li.fecha());
                acc.getChildren().addAll(link("Modificar", () -> nav.ir("modificar_dispersion", id)),
                        link("Borrar", () -> {
                            if (confirmar("¿Borrar el informe de dispersión de " + descripcion + "? No se puede deshacer.")) control.borrar(li.id(), descripcion);
                        }));
            }
            nodos.add(new Node[]{t(fechaHora(li.fecha())), t(li.zonaClave() + " · " + li.zonaNombre()), fuente,
                    t(Existencia.nombrePunto(li.punto())), mono(li.avioneta()), t(li.piloto()), t(miles(li.cantidad())),
                    t(miles(li.superficieHa()) + " ha"), t(miles(li.altitudM()) + " m"), acc});
        }
        resultados.getChildren().setAll(tabla(List.of("Fecha", "Zona", "Moscas de", "Salieron de", "Avioneta", "Piloto", "Moscas",
                        "Superficie", "Altitud", ""), nodos, Set.of(6, 7, 8, 9), "Todavía no hay informes de dispersión."),
                paginacion(pagina, encontrados.total(), this::cargar, null));
    }

    @Override
    public void mostrarError(String mensaje) {
        resultados.getChildren().setAll(aviso(mensaje, "error"));
    }
}
