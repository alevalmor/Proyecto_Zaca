package moscamed.vista.admin;

import static moscamed.vista.Componentes.*;
import static moscamed.vista.Formato.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import moscamed.interfaces.controlador.Navegador;
import moscamed.interfaces.vista.Vista;
import moscamed.modelo.Auditoria;
import moscamed.modelo.EstadoSede;

public final class SistemaVista implements Vista {
    private final Navegador nav;
    private final List<EstadoSede> sedes;
    private final List<Auditoria> bitacora;
    private final List<String> tablas;

    public SistemaVista(Navegador nav, List<EstadoSede> sedes, List<Auditoria> bitacora, List<String> tablas) {
        this.nav = nav;
        this.sedes = sedes;
        this.bitacora = bitacora;
        this.tablas = tablas;
    }

    @Override
    public Node vista() {
        List<Node> tarjetas = new ArrayList<>();
        for (var s : sedes) {
            boolean planta = s.esPlanta();
            HBox cab = new HBox(lbl(planta ? "Planta Metapa" : "Centro de empaque Tapachula", "dato"));
            Region hueco = new Region();
            HBox.setHgrow(hueco, Priority.ALWAYS);
            cab.getChildren().addAll(hueco, pill(s.ok() ? "EN LÍNEA" : "SIN CONEXIÓN", s.ok() ? "APROBADO" : "RECHAZADO"));
            VBox t = new VBox(4, cab, lbl(s.host() + " · ids " + (planta ? "impares" : "pares"), "gris", "mono", "chico"));
            if (s.ok()) {
                t.getChildren().add(lbl("Publica: " + (s.publicaciones().isEmpty() ? "ninguna" : String.join(", ", s.publicaciones()))));
                if (s.suscripciones().isEmpty()) t.getChildren().add(lbl("Sin suscripciones: no recibe datos de la otra sede"));
                for (var sub : s.suscripciones()) t.getChildren().add(lbl("Suscripción " + sub.nombre() + ": " + (sub.activa() ? "activa" : "detenida")));
                for (var r : s.envia()) t.getChildren().add(lbl("Envía a " + r.nombre() + " · " + miles(r.retraso()) + " bytes de retraso"));
            } else {
                t.getChildren().add(lbl(s(s.error()), "rojo"));
            }
            t.getStyleClass().addAll("tarjeta", planta ? "tarjeta-verde" : "tarjeta-azul");
            tarjetas.add(t);
        }

        VBox servicios = new VBox(10);
        for (var s : sedes) {
            if (!s.ok()) continue;
            String sede = s.sede().toLowerCase();
            if (s.pgpool() == null) {
                servicios.getChildren().add(item(new VBox(2, lbl("Pgpool " + sede, "dato"),
                        lbl("Conexión directa a PostgreSQL, no pasa por Pgpool-II", "gris", "chico")), pill("SIN DATOS", ""), null, null));
            } else {
                for (var n : s.pgpool()) {
                    servicios.getChildren().add(item(new VBox(2, lbl("Pgpool " + sede + " · nodo " + n.id(), "dato"),
                                    lbl(n.host() + ":" + n.puerto() + " · " + n.rol(), "gris", "chico")),
                            pill(n.estado().toUpperCase(), n.arriba() ? "APROBADO" : "RECHAZADO"), null, null));
                }
            }
            Long trampas = s.trampas();
            servicios.getChildren().add(item(new VBox(2, lbl("FDW base de trampeo · " + sede, "dato"),
                            lbl(trampas != null ? trampas + " trampas leídas" : "No se pudo leer trampeo.revision", "gris", "chico")),
                    pill(trampas != null ? "CONECTADO" : "FALLA", trampas != null ? "APROBADO" : "RECHAZADO"), null, null));
        }

        List<Node[]> conteo = new ArrayList<>();
        var p = sedes.get(0);
        var em = sedes.get(1);
        for (String t : tablas) {
            conteo.add(new Node[]{mono(t), t(p.ok() ? p.conteos().get(t) : "—"), t(em.ok() ? em.conteos().get(t) : "—")});
        }
        boolean iguales = p.ok() && em.ok() && p.conteos().equals(em.conteos());
        Label coinciden = lbl(iguales ? "Las dos sedes tienen los mismos datos" : "Las sedes no coinciden: revisa la replicación", "dato");
        coinciden.setStyle("-fx-text-fill: " + (iguales ? "#145232;" : "#9b1c1c;"));

        List<Node[]> filas = new ArrayList<>();
        for (var b : bitacora) filas.add(new Node[]{t(fechaHora(b.fecha())), mono(b.usuario()), t(b.accion())});
        return new VBox(18,
                encabezado(titulo("Programa 2 · Administrador", "Estado del sistema"), btn("Actualizar", "gris", () -> nav.ir("sistema", null))),
                caja(h2("Replicación master-master"), fila(14, tarjetas.toArray(Node[]::new))),
                fila(18, caja(h2("Pgpool-II y FDW"), servicios),
                        caja(h2("Registros por tabla"), tabla(List.of("Tabla", "Planta", "Empaque"), conteo, Set.of(1, 2), ""), coinciden)),
                caja(h2("Bitácora de auditoría reciente"), tabla(List.of("Fecha", "Usuario", "Acción"), filas, Set.of(), "Sin movimientos.")));
    }
}
