package moscamed.vista.operativo;

import static moscamed.vista.Componentes.*;
import static moscamed.vista.Formato.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import moscamed.controlador.LoteControlador;
import moscamed.interfaces.controlador.Navegador;
import moscamed.interfaces.vista.VistaPaginada;
import moscamed.modelo.Lote;
import moscamed.modelo.Pagina;
import moscamed.modelo.Sesion;

public final class LotesVista implements VistaPaginada<Lote> {
    private final LoteControlador control;
    private final Navegador nav;
    private final Sesion sesion = Sesion.actual();
    private final TextField q = new TextField();
    private final ComboBox<String> estado = new ComboBox<>();
    private final DatePicker desde = new DatePicker(), hasta = new DatePicker();
    private final VBox resultados = new VBox();
    private final int[] pagina = {1};

    public LotesVista(LoteControlador control, Navegador nav) {
        this.control = control;
        this.nav = nav;
    }

    @Override
    public Node vista() {
        q.setPromptText("MET-2026-…");
        q.getStyleClass().add("mono");
        estado.getItems().add("Todos");
        estado.getItems().addAll(Lote.ESTADOS);
        estado.getSelectionModel().select(0);
        cargar();

        Runnable filtrar = () -> { pagina[0] = 1; cargar(); };
        q.setOnAction(e -> filtrar.run());
        HBox filtros = filtros(campo("Buscar por código", q, null), campo("Estado", estado, null),
                campo("Desde", desde, null), campo("Hasta", hasta, null), btn("Filtrar", "borde", filtrar));

        var derecha = new HBox();
        if (sesion.puede("nuevo_lote", true)) derecha.getChildren().add(btn("+  Nuevo lote", () -> nav.ir("nuevo_lote")));
        return new VBox(18, encabezado(titulo("Inicio / Lotes", "Lista de lotes"), derecha),
                sesion.esPes() ? aviso("Como PES puedes ver y modificar los lotes de la planta.")
                        : aviso("Como " + sesion.perfil() + " solo ves los lotes: los registra y modifica la planta de Metapa.", "azul"),
                caja(filtros), caja(resultados));
    }

    private void cargar() {
        control.buscar(q.getText(), estado.getSelectionModel().getSelectedIndex() > 0 ? estado.getValue() : null,
                desde.getValue(), hasta.getValue(), pagina[0], this);
    }

    @Override
    public void mostrar(Pagina<Lote> encontrados) {
        List<Node[]> nodos = new ArrayList<>();
        for (var lote : encontrados.filas()) {
            String codigo = lote.codigo();
            Node acciones = sesion.puede("lotes", true) && lote.modificable()
                    ? juntos(link("Ver", () -> nav.ir("estado_lote", codigo)), link("Modificar", () -> nav.ir("modificar_lote", codigo)))
                    : juntos(link("Ver", () -> nav.ir("estado_lote", codigo)));
            nodos.add(new Node[]{mono(codigo), t(fecha(lote.fechaProduccion())), t(miles(lote.cantidadPupas())),
                    t(lote.cepa()), t(lote.centroClave()), t(lote.textoCalidadPlanta()), pill(lote.estado()), acciones});
        }
        resultados.getChildren().setAll(tabla(List.of("Código", "Producción", "Pupas", "Cepa", "Destino", "Calidad planta", "Estado", "Acciones"),
                nodos, Set.of(2, 7), "Sin resultados."), paginacion(pagina, encontrados.total(), this::cargar, null));
    }

    @Override
    public void mostrarError(String mensaje) {
        resultados.getChildren().setAll(aviso(mensaje, "error"));
    }
}
