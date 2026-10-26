package moscamed.vista.operativo;

import static moscamed.vista.Componentes.*;
import static moscamed.vista.Formato.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import moscamed.controlador.EnvioControlador;
import moscamed.interfaces.controlador.Navegador;
import moscamed.interfaces.vista.VistaPaginada;
import moscamed.modelo.Envio;
import moscamed.modelo.Pagina;
import moscamed.modelo.Sesion;

public final class EnviosVista implements VistaPaginada<Envio> {
    private final EnvioControlador control;
    private final Navegador nav;
    private final Sesion sesion = Sesion.actual();
    private final Map<String, Long> conteo;
    private final TextField q = new TextField();
    private final ComboBox<String> estado;
    private final VBox resultados = new VBox();
    private final int[] pagina = {1};

    public EnviosVista(EnvioControlador control, Navegador nav, Map<String, Long> conteo) {
        this.control = control;
        this.nav = nav;
        this.conteo = conteo;
        List<String> opciones = new ArrayList<>(List.of("Todos"));
        opciones.addAll(Envio.ESTADOS);
        this.estado = combo(opciones, null);
    }

    @Override
    public Node vista() {
        q.setPromptText("TAP-ENV-… o MET-2026-…");
        q.getStyleClass().add("mono");
        cargar();
        Runnable filtrar = () -> { pagina[0] = 1; cargar(); };
        q.setOnAction(e -> filtrar.run());

        Node kpis = fila(14, kpi("Total", conteo.values().stream().mapToLong(Long::longValue).sum(), null, null),
                kpi("En camino", conteo.getOrDefault("EN CAMINO", 0L), "falta registrar su llegada", "ambar"),
                kpi("Recibidos en Tapachula", conteo.getOrDefault("RECIBIDO", 0L) + conteo.getOrDefault("DISPERSADO", 0L), null, null),
                kpi("Cancelados", conteo.getOrDefault("CANCELADO", 0L), null, null));

        HBox filtros = filtros(campo("Buscar envío o lote", q, null), campo("Estado", estado, null), btn("Filtrar", "borde", filtrar));
        var derecha = new HBox();
        if (sesion.puede("nuevo_envio", true)) derecha.getChildren().add(btn("+  Nuevo envío", () -> nav.ir("nuevo_envio")));
        return new VBox(18, encabezado(titulo("Inicio / Envíos", "Traslados terrestres Metapa → Tapachula"), derecha),
                sesion.puede("envios", true) ? aviso("Registras los traslados por tierra a Tapachula y puedes modificarlos o cancelarlos mientras van en camino. "
                        + "La llegada la registra el centro de empaque.", "azul")
                        : sesion.puede("estado_envio", true) ? aviso("Consultas los traslados y registras su llegada a Tapachula desde el estado de cada envío.", "azul")
                        : aviso("Solo consultas los traslados: los registra el centro de empaque. La dispersión es solo aérea."),
                kpis, caja(filtros), caja(resultados));
    }

    private void cargar() {
        control.buscar(q.getText(), estado.getSelectionModel().getSelectedIndex() > 0 ? estado.getValue() : null, pagina[0], this);
    }

    @Override
    public void mostrar(Pagina<Envio> encontrados) {
        List<Node[]> nodos = new ArrayList<>();
        for (var e : encontrados.filas()) {
            String codigo = e.codigo();
            List<Node> acc = new ArrayList<>(List.of(link("Ver", () -> nav.ir("estado_envio", codigo))));
            if (e.enCamino() && sesion.puede("estado_envio", true)) acc.add(link("Recibir", () -> nav.ir("estado_envio", codigo)));
            if (e.enCamino() && sesion.puede("envios", true)) acc.add(link("Modificar", () -> nav.ir("modificar_envio", codigo)));
            nodos.add(new Node[]{mono(codigo), mono(e.loteCodigo()), mono(e.matricula()),
                    t(fechaHora(e.fechaSalida())), t(fechaHora(e.recibido())), t(miles(e.cantidad())), t(e.temperatura()), pill(e.estado()),
                    juntos(acc.toArray(Node[]::new))});
        }
        resultados.getChildren().setAll(tabla(List.of("Envío", "Lote", "Camioneta", "Salida de Metapa", "Llegada a Tapachula", "Moscas", "°C", "Estado", "Acciones"),
                nodos, Set.of(5, 6, 8), "Sin resultados."), paginacion(pagina, encontrados.total(), this::cargar, null));
    }

    @Override
    public void mostrarError(String mensaje) {
        resultados.getChildren().setAll(aviso(mensaje, "error"));
    }
}
