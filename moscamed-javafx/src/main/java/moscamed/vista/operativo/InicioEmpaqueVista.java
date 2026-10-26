package moscamed.vista.operativo;

import static moscamed.vista.Componentes.*;
import static moscamed.vista.Formato.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import moscamed.interfaces.controlador.Navegador;
import moscamed.modelo.Envio;
import moscamed.modelo.Lote;
import moscamed.modelo.ResumenEmpaque;
import moscamed.modelo.Zona;

public final class InicioEmpaqueVista extends InicioVista {
    private final ResumenEmpaque k;
    private final List<Envio> envios;
    private final List<Zona> altas;

    public InicioEmpaqueVista(Navegador nav, ResumenEmpaque k, List<Envio> envios, List<Zona> altas) {
        super(nav);
        this.k = k;
        this.envios = envios;
        this.altas = altas;
    }

    @Override
    public Node vista() {
        Node kpis = fila(14,
                kpi("Lotes aprobados por despachar", k.porDespachar(), "listos en la planta de Metapa", null),
                kpi("Envíos en camino", k.enCamino(), "falta registrar su llegada", "ambar"),
                kpi("Moscas en el centro", millones(k.enCentro()), "por dispersar (Metapa y Perú)", null),
                kpi("Zonas con prioridad alta", altas.size(), "hubo capturas silvestres", "rojo"));

        var acciones = acciones();
        if (sesion.puede("nuevo_envio", true)) acciones.getChildren().add(btn("+  Nuevo envío", () -> nav.ir("nuevo_envio")));
        if (!envios.isEmpty()) {
            String codigo = envios.get(0).codigo();
            acciones.getChildren().add(btn("Registrar llegada", "borde", () -> nav.ir("estado_envio", codigo)));
        }
        acciones.getChildren().add(btn("Calidad al recibir un lote", "gris", () -> nav.ir("estado_lote")));

        List<Node[]> filas = new ArrayList<>();
        for (var e : envios) {
            String codigo = e.codigo();
            filas.add(new Node[]{mono(codigo), mono(e.loteCodigo()), mono(e.matricula()),
                    t(miles(e.cantidad())), link("Recibir", () -> nav.ir("estado_envio", codigo))});
        }
        VBox camino = caja(encabezado(lbl("Envíos en camino desde Metapa", "h2", "h2-grande"), link("Ver todos los envíos", () -> nav.ir("envios"))),
                tabla(List.of("Envío", "Lote", "Camioneta", "Moscas", ""), filas, Set.of(3, 4), "No hay envíos en camino."));

        VBox lista = new VBox(10);
        for (var z : altas.subList(0, Math.min(4, altas.size()))) {
            lista.getChildren().add(item(new VBox(2, lbl(z.clave() + " · " + z.nombre(), "dato"),
                    lbl("MTD " + dec(z.mtd(), 2) + " · " + z.silvestres() + " silvestres", "gris", "chico")), pill("ALTA"), null, null));
        }
        if (altas.isEmpty()) lista.getChildren().add(lbl("Sin capturas silvestres en el periodo.", "gris"));
        VBox zonas = caja(lbl("Dónde dispersar más", "h2", "h2-grande"),
                lbl("Según el trampeo de los últimos 14 días (base de trampeo vía FDW)", "gris", "chico"), lista);
        return new VBox(22, saludo("Centro de empaque · Tapachula", "Sincronizado con la planta"), kpis, acciones, columnas(camino, zonas));
    }
}
