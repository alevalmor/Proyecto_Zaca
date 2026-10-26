package moscamed.vista.admin;

import static moscamed.vista.Componentes.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import moscamed.controlador.UsuarioControlador;
import moscamed.interfaces.controlador.Navegador;
import moscamed.interfaces.vista.VistaPaginada;
import moscamed.modelo.Pagina;
import moscamed.modelo.Responsable;
import moscamed.modelo.ResumenUsuarios;

public final class UsuariosVista implements VistaPaginada<Responsable> {
    private final UsuarioControlador control;
    private final Navegador nav;
    private final ResumenUsuarios resumen;
    private final TextField q = new TextField();
    private final ComboBox<String> perfil = combo(List.of("Todos", "PES · planta", "PEM · empaque", "PEA · aeropuerto", "PED · dispersión"), null);
    private final ComboBox<String> estado = combo(List.of("Activos y de baja", "Activos", "De baja"), null);
    private final VBox resultados = new VBox();
    private final int[] pagina = {1};

    public UsuariosVista(UsuarioControlador control, Navegador nav, ResumenUsuarios resumen) {
        this.control = control;
        this.nav = nav;
        this.resumen = resumen;
    }

    @Override
    public Node vista() {
        q.setPromptText("Nombre, MET-… o TAP-…");
        cargar();
        Runnable filtrar = () -> { pagina[0] = 1; cargar(); };
        q.setOnAction(e -> filtrar.run());

        HBox filtros = filtros(campo("Buscar nombre o número de empleado", q, null), campo("Perfil", perfil, null), campo("Estado", estado, null),
                btn("Filtrar", "borde", filtrar));
        return new VBox(18, encabezado(titulo("Programa 2 · Administrador", "Usuarios y perfiles"), btn("+  Nuevo usuario", () -> nav.ir("nuevo", null))),
                fila(14, kpi("PES · planta", resumen.pes(), null, null), kpi("PEM · empaque", resumen.pem(), null, null),
                        kpi("PEA · aeropuerto", resumen.pea(), null, null), kpi("PED · dispersión", resumen.ped(), null, null),
                        kpi("Dados de baja", resumen.bajas(), null, null)),
                caja(filtros), caja(resultados));
    }

    private void cargar() {
        int e = estado.getSelectionModel().getSelectedIndex();
        control.buscar(q.getText(), perfil.getSelectionModel().getSelectedIndex() > 0 ? perfil.getValue().substring(0, 3) : null,
                e == 0 ? null : e == 1, pagina[0], this);
    }

    @Override
    public void mostrar(Pagina<Responsable> encontrados) {
        List<Node[]> filas = new ArrayList<>();
        for (var u : encontrados.filas()) {
            boolean activo = u.activo();
            String login = u.usuarioBd();
            int id = u.id();
            filas.add(new Node[]{t(u.nombreCompleto()), mono(u.numEmpleado()), t(u.puesto()), pill(u.perfil()), mono(login),
                    pill(activo ? "Activo" : "De baja", activo ? "Activo" : ""),
                    juntos(link("Restablecer contraseña", () -> accion(id, login, "clave")),
                            link(activo ? "Dar de baja" : "Reactivar", () -> accion(id, login, activo ? "baja" : "reactivar")))});
        }
        resultados.getChildren().setAll(tabla(List.of("Nombre", "Empleado", "Puesto", "Perfil", "Usuario de acceso", "Estado", "Acciones"),
                        filas, Set.of(6), "Sin resultados."),
                paginacion(pagina, encontrados.total(), this::cargar, " · los usuarios dados de baja no pueden registrar nada"));
    }

    @Override
    public void mostrarError(String mensaje) {
        resultados.getChildren().setAll(aviso(mensaje, "error"));
    }

    private void accion(int id, String login, String accion) {
        String pregunta = switch (accion) {
            case "clave" -> "¿Restablecer la contraseña de " + login + "?";
            case "baja" -> "¿Dar de baja a " + login + "? Ya no podrá entrar.";
            default -> "¿Reactivar a " + login + "?";
        };
        if (confirmar(pregunta)) control.accion(id, login, accion);
    }
}
