package moscamed.vista.operativo;

import static moscamed.vista.Componentes.*;
import static moscamed.vista.Formato.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import moscamed.interfaces.controlador.Navegador;
import moscamed.modelo.Lote;
import moscamed.modelo.ResumenPlanta;

public final class InicioPlantaVista extends InicioVista {
    private final ResumenPlanta k;
    private final List<Lote> lotes, pendientes;

    public InicioPlantaVista(Navegador nav, ResumenPlanta k, List<Lote> lotes, List<Lote> pendientes) {
        super(nav);
        this.k = k;
        this.lotes = lotes;
        this.pendientes = pendientes;
    }

    @Override
    public Node vista() {
        Node kpis = fila(14,
                kpi("Lotes esta semana", k.semana(), "producidos desde el lunes", null),
                kpi("Sin control de calidad", k.sinCalidad(), "no se pueden enviar aún", "ambar"),
                kpi("Rechazados del mes", k.rechazados(), k.motivoRechazo(), "rojo"),
                kpi("Pupas esta semana", millones(k.pupas()), "millones de pupas", null));

        var acciones = acciones();
        if (sesion.puede("nuevo_lote", true)) acciones.getChildren().add(btn("+  Nuevo lote", () -> nav.ir("nuevo_lote")));
        if (sesion.puede("nuevo_envio", true)) acciones.getChildren().add(btn("+  Nuevo envío", "borde", () -> nav.ir("nuevo_envio")));
        if (!pendientes.isEmpty()) {
            String codigo = pendientes.get(0).codigo();
            acciones.getChildren().add(btn("Registrar control de calidad", "borde", () -> nav.ir("estado_lote", codigo)));
        }
        acciones.getChildren().add(btn("Ver todos los lotes", "gris", () -> nav.ir("lotes")));

        List<Node[]> filas = new ArrayList<>();
        for (var l : lotes) {
            String codigo = l.codigo();
            filas.add(new Node[]{mono(codigo), t(fecha(l.fechaProduccion())), t(miles(l.cantidadPupas())),
                    pill(l.estado()), link("Ver", () -> nav.ir("estado_lote", codigo))});
        }
        VBox ultimos = caja(encabezado(lbl("Últimos lotes", "h2", "h2-grande"), link("Ver lista completa", () -> nav.ir("lotes"))),
                tabla(List.of("Código", "Producción", "Pupas", "Estado", ""), filas, Set.of(2, 4), "Todavía no hay lotes."));

        VBox lista = new VBox(10);
        for (var p : pendientes) {
            String codigo = p.codigo();
            lista.getChildren().add(item(new VBox(2, mono(codigo), lbl("Irradiado " + fecha(p.fechaIrradiacion()), "gris", "chico")),
                    lbl("Registrar", "ambar", "dato"), "item-pendiente", () -> nav.ir("estado_lote", codigo)));
        }
        if (pendientes.isEmpty()) lista.getChildren().add(lbl("Todos los lotes tienen su control de calidad.", "gris"));
        VBox lateral = new VBox(18,
                caja(lbl("Pendientes de control de calidad", "h2", "h2-grande"), lista),
                caja(lbl("Lo que puedes hacer como PES", "h2", "h2-grande"),
                        lbl("Registrar y modificar lotes, y su control de calidad en la planta.", "gris"),
                        lbl("Registrar los envíos a Tapachula y modificarlos o cancelarlos mientras van en camino.", "gris"),
                        lbl("Consultar dispersiones sin modificarlas: esos datos son de Tapachula.", "gris")));
        return new VBox(22, saludo("Planta esterilizadora · Metapa", "Sincronizado con el centro de empaque"), kpis, acciones,
                columnas(ultimos, lateral));
    }
}
