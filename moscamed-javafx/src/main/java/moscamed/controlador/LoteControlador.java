package moscamed.controlador;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.scene.Node;
import moscamed.datos.Conexion;
import moscamed.interfaces.controlador.Navegador;
import moscamed.interfaces.datos.LoteDao;
import moscamed.interfaces.vista.VistaPaginada;
import moscamed.modelo.CentroEmpaque;
import moscamed.modelo.ControlCalidad;
import moscamed.modelo.DatosCalidad;
import moscamed.modelo.DatosLote;
import moscamed.modelo.Lote;
import moscamed.modelo.Reglas;
import moscamed.modelo.Sesion;
import moscamed.vista.operativo.LoteEstadoVista;
import moscamed.vista.operativo.LoteFormularioVista;
import moscamed.vista.operativo.LotesVista;

public final class LoteControlador {
    public record EntradaLote(String pupas, LocalDate produccion, LocalDate irradiacion, String dosis, String cepa, String tinte,
                       CentroEmpaque centro, String observaciones) {}

    public record EntradaCalidad(String emergencia, String vuelo, String esterilidad, String peso, String observaciones) {}

    private final LoteDao lotes;
    private final Navegador nav;

    public LoteControlador(LoteDao lotes, Navegador nav) {
        this.lotes = lotes;
        this.nav = nav;
    }

    public Node lista() {
        return new LotesVista(this, nav).vista();
    }

    public void buscar(String codigo, String estado, LocalDate desde, LocalDate hasta, int pagina, VistaPaginada<Lote> vista) {
        try {
            vista.mostrar(lotes.buscar(codigo, estado, desde, hasta, pagina));
        } catch (SQLException e) {
            vista.mostrarError(Conexion.mensaje(e));
        }
    }

    public Node formulario(String codigo) throws SQLException {
        Lote lote = null;
        if (codigo != null) {
            lote = lotes.porCodigo(codigo).orElseThrow(() -> new SQLException("No existe el lote " + codigo));
            if (!lote.modificable()) {
                nav.avisar("Solo se modifican lotes que aún no salen de la planta.");
                nav.ir("estado_lote", codigo);
                return null;
            }
        }
        String codigoMostrado = lote != null ? codigo : lotes.siguienteCodigo(LocalDate.now().getYear());
        return new LoteFormularioVista(this, nav, lote, codigoMostrado, lotes.centros()).vista();
    }

    public List<String> guardar(Lote original, EntradaLote e) {
        List<String> errs = new ArrayList<>();
        long n = 0;
        BigDecimal gy = null;
        try {
            n = Reglas.entero(e.pupas());
            gy = new BigDecimal(e.dosis().trim());
        } catch (NumberFormatException ex) {
            errs.add("Revisa que la cantidad de pupas y la dosis sean números.");
        }
        if (e.produccion() == null || e.irradiacion() == null) errs.add("Llena las dos fechas.");
        else if (e.irradiacion().isBefore(e.produccion())) errs.add("La irradiación debe ser el mismo día o después de la producción.");
        if (gy != null && (gy.compareTo(BigDecimal.valueOf(90)) < 0 || gy.compareTo(BigDecimal.valueOf(160)) > 0)) errs.add("La dosis debe estar entre 90 y 160 Gy.");
        if (errs.isEmpty() && n <= 0) errs.add("La cantidad de pupas debe ser mayor que cero.");
        if (e.centro() == null) errs.add("Elige el centro de empaque destino.");
        if (!errs.isEmpty()) return errs;

        DatosLote datos = new DatosLote(e.produccion(), e.irradiacion(), gy, n, e.cepa(), e.tinte(), e.centro().id(),
                Entradas.opcional(e.observaciones()));
        String guardado;
        try {
            if (original != null) {
                lotes.modificar(original.id(), original.codigo(), datos);
                guardado = original.codigo();
            } else {
                guardado = lotes.registrar(datos);
            }
        } catch (SQLException ex) {
            return List.of(Conexion.mensaje(ex));
        }
        nav.avisar("Lote " + guardado + " guardado.");
        nav.ir("estado_lote", guardado);
        return List.of();
    }

    public Node estado(String codigo) throws SQLException {
        if (codigo == null) {
            var porOmision = lotes.codigoPorOmision();
            if (porOmision.isEmpty()) {
                nav.avisar("Todavía no hay lotes registrados.");
                nav.ir("inicio");
                return null;
            }
            codigo = porOmision.get();
        }
        String buscado = codigo;
        Lote lote = lotes.detalle(codigo).orElseThrow(() -> new SQLException("No existe el lote " + buscado));
        Map<String, ControlCalidad> calidades = new HashMap<>();
        for (var c : lotes.calidades(lote.id())) calidades.put(c.etapa(), c);
        String etapa = Sesion.actual().esPes() ? "PLANTA" : "EMPAQUE";
        return new LoteEstadoVista(this, nav, lote, calidades, etapa, lotes.envios(lote.id()), lotes.todos()).vista();
    }

    public List<String> guardarCalidad(Lote lote, String etapa, EntradaCalidad e) {
        BigDecimal[] v = new BigDecimal[4];
        List<String> errs = new ArrayList<>();
        try {
            v[0] = new BigDecimal(e.emergencia().trim());
            v[1] = new BigDecimal(e.vuelo().trim());
            v[2] = new BigDecimal(e.esterilidad().trim());
            v[3] = new BigDecimal(e.peso().trim());
            for (int i = 0; i < 3; i++) {
                if (v[i].signum() < 0 || v[i].compareTo(BigDecimal.valueOf(100)) > 0) { errs.add("Los porcentajes van de 0 a 100."); break; }
            }
            if (v[3].signum() <= 0) errs.add("El peso debe ser mayor que cero.");
        } catch (NumberFormatException ex) {
            errs.add("Llena las cuatro mediciones con números.");
        }
        if (!errs.isEmpty()) return errs;
        try {
            lotes.guardarCalidad(lote.id(), lote.codigo(), etapa, new DatosCalidad(v[0], v[1], v[2], v[3], Entradas.opcional(e.observaciones())));
        } catch (SQLException ex) {
            return List.of(Conexion.mensaje(ex));
        }
        nav.avisar("Control de calidad guardado.");
        nav.ir("estado_lote", lote.codigo());
        return List.of();
    }
}
