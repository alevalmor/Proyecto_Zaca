package moscamed.controlador;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import moscamed.datos.Conexion;
import moscamed.interfaces.controlador.Navegador;
import moscamed.interfaces.datos.DispersionDao;
import moscamed.interfaces.vista.VistaPaginada;
import moscamed.modelo.Existencia;
import moscamed.modelo.InformeDispersion;
import moscamed.modelo.Reglas;
import moscamed.modelo.Zona;
import moscamed.vista.operativo.DispersionFormularioVista;
import moscamed.vista.operativo.DispersionesVista;

public final class DispersionControlador {
    public record EntradaDispersion(Existencia fuente, Zona zona, String fecha, String cantidad, String superficie, String altitud,
                             String avioneta, String piloto, String observaciones) {}

    private final DispersionDao dispersiones;
    private final Navegador nav;

    public DispersionControlador(DispersionDao dispersiones, Navegador nav) {
        this.dispersiones = dispersiones;
        this.nav = nav;
    }

    public Node lista() throws SQLException {
        return new DispersionesVista(this, nav, dispersiones.resumen(), dispersiones.existencias()).vista();
    }

    public void buscar(String texto, String punto, int pagina, VistaPaginada<InformeDispersion> vista) {
        try {
            vista.mostrar(dispersiones.buscar(texto, punto, pagina));
        } catch (SQLException e) {
            vista.mostrarError(Conexion.mensaje(e));
        }
    }

    public Node formulario(String elegida, String idInforme) throws SQLException {
        InformeDispersion informe = null;
        if (idInforme != null) {
            informe = dispersiones.porId(Long.parseLong(idInforme)).orElseThrow(() -> new SQLException("No existe el informe " + idInforme));
        }
        List<Existencia> existencias = new ArrayList<>(dispersiones.existenciasPara(informe));
        Existencia preseleccion = null;
        for (int i = 0; i < existencias.size(); i++) {
            Existencia e = existencias.get(i);
            if (informe != null && e.esDe(informe)) {
                e = e.conQuedan(e.quedan() + informe.cantidad());
                existencias.set(i, e);
                preseleccion = e;
            } else if (e.clave().equals(elegida)) {
                preseleccion = e;
            }
        }
        return new DispersionFormularioVista(this, nav, informe, existencias, preseleccion, dispersiones.prioridad(14, LocalDate.now())).vista();
    }

    public List<String> guardar(InformeDispersion original, EntradaDispersion e) {
        var f = e.fuente();
        List<String> errs = new ArrayList<>();
        LocalDateTime cuando = null;
        long n = 0, altitud = 0;
        BigDecimal superficie = null;
        try {
            cuando = Entradas.fechaHora(e.fecha());
            n = Reglas.entero(e.cantidad());
            altitud = Reglas.entero(e.altitud());
            superficie = new BigDecimal(e.superficie().replace(",", "").trim());
        } catch (NumberFormatException | DateTimeParseException ex) {
            errs.add("Revisa la fecha (dd/mm/aaaa hh:mm) y que la cantidad, la superficie y la altitud sean números.");
        }
        if (f == null) errs.add("Elige de dónde salieron las moscas.");
        if (e.zona() == null) errs.add("Elige la zona.");
        if (e.avioneta().isBlank()) errs.add("Falta la matrícula de la avioneta.");
        if (e.piloto().isBlank()) errs.add("Falta el piloto que reporta.");
        if (errs.isEmpty()) errs.addAll(Reglas.erroresDispersion(n, f.quedan(), altitud));
        if (errs.isEmpty() && superficie.signum() <= 0) errs.add("La superficie debe ser mayor que cero.");
        if (!errs.isEmpty()) return errs;

        boolean deEnvio = f.deEnvio();
        var informe = new InformeDispersion(original == null ? 0 : original.id(), deEnvio ? f.id() : null, deEnvio ? null : f.id(),
                f.punto(), e.zona().id(), cuando, n, superficie, (int) altitud, e.avioneta().trim().toUpperCase(), e.piloto().trim(),
                Entradas.opcional(e.observaciones()), e.zona().clave(), e.zona().nombre(),
                deEnvio ? f.codigo() : null, deEnvio ? null : f.codigo());
        try {
            if (original == null) dispersiones.registrar(informe);
            else dispersiones.modificar(informe);
        } catch (SQLException ex) {
            return List.of(Conexion.mensaje(ex));
        }
        nav.avisar(original == null ? "Informe de dispersión guardado." : "Informe de dispersión modificado.");
        nav.ir("dispersiones");
        return List.of();
    }

    public void borrar(long id, String descripcion) {
        try {
            dispersiones.borrar(id, descripcion);
            nav.avisar("Informe de dispersión borrado.");
        } catch (SQLException ex) {
            nav.avisar(Conexion.mensaje(ex));
        }
        nav.ir("dispersiones");
    }
}
