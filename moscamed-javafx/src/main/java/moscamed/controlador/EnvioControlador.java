package moscamed.controlador;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import moscamed.datos.Conexion;
import moscamed.interfaces.controlador.Navegador;
import moscamed.interfaces.datos.EnvioDao;
import moscamed.interfaces.vista.VistaPaginada;
import moscamed.modelo.DatosEnvio;
import moscamed.modelo.Envio;
import moscamed.modelo.LoteDisponible;
import moscamed.modelo.Reglas;
import moscamed.modelo.Vehiculo;
import moscamed.vista.Formato;
import moscamed.vista.operativo.EnvioEstadoVista;
import moscamed.vista.operativo.EnvioFormularioVista;
import moscamed.vista.operativo.EnviosVista;

public final class EnvioControlador {
    public record EntradaEnvio(LoteDisponible lote, Vehiculo vehiculo, String cantidad, String salida, String temperatura) {}

    private final EnvioDao envios;
    private final Navegador nav;

    public EnvioControlador(EnvioDao envios, Navegador nav) {
        this.envios = envios;
        this.nav = nav;
    }

    public Node lista() throws SQLException {
        return new EnviosVista(this, nav, envios.conteoPorEstado()).vista();
    }

    public void buscar(String texto, String estado, int pagina, VistaPaginada<Envio> vista) {
        try {
            vista.mostrar(envios.buscar(texto, estado, pagina));
        } catch (SQLException e) {
            vista.mostrarError(Conexion.mensaje(e));
        }
    }

    public Node formulario(String codigo) throws SQLException {
        Envio envio = null;
        if (codigo != null) {
            envio = envios.porCodigo(codigo).orElseThrow(() -> new SQLException("No existe el envío " + codigo));
            if (!envio.enCamino()) {
                nav.avisar("Solo se modifican envíos en camino.");
                nav.ir("estado_envio", codigo);
                return null;
            }
        }
        return new EnvioFormularioVista(this, nav, envio, envios.lotesDisponibles(envio), envios.vehiculos()).vista();
    }

    public List<String> guardar(Envio original, EntradaEnvio e) {
        var l = e.lote();
        var v = e.vehiculo();
        List<String> errs = new ArrayList<>();
        long n = 0;
        LocalDateTime salida = null;
        BigDecimal temperatura = null;
        try {
            n = Reglas.entero(e.cantidad());
            salida = Entradas.fechaHora(e.salida());
            temperatura = new BigDecimal(e.temperatura().trim());
        } catch (NumberFormatException | DateTimeParseException ex) {
            errs.add("Revisa la cantidad, la fecha (dd/mm/aaaa hh:mm) y la temperatura.");
        }
        if (l == null) errs.add("Elige un lote aprobado y con pupas disponibles.");
        if (v == null || !v.activo()) errs.add("Elige un vehículo en servicio.");
        if (errs.isEmpty()) errs.addAll(Reglas.erroresEnvio(n, v.capacidad(), l.disponibles()));
        if (!errs.isEmpty()) return errs;

        DatosEnvio datos = new DatosEnvio(l.id(), v.id(), salida, n, temperatura);
        String guardado;
        try {
            if (original != null) {
                envios.modificar(original.id(), original.codigo(), datos);
                guardado = original.codigo();
            } else {
                guardado = envios.registrar(datos);
            }
        } catch (SQLException ex) {
            return List.of(Conexion.mensaje(ex));
        }
        nav.avisar("Envío " + guardado + " guardado.");
        nav.ir("estado_envio", guardado);
        return List.of();
    }

    public Node estado(String codigo) throws SQLException {
        if (codigo == null) {
            var porOmision = envios.codigoPorOmision();
            if (porOmision.isEmpty()) {
                nav.avisar("Todavía no hay envíos registrados.");
                nav.ir("inicio");
                return null;
            }
            codigo = porOmision.get();
        }
        String buscado = codigo;
        Envio envio = envios.detalle(codigo).orElseThrow(() -> new SQLException("No existe el envío " + buscado));
        return new EnvioEstadoVista(this, nav, envio, envios.dispersiones(envio.id())).vista();
    }

    public List<String> recibir(Envio envio, String llegada) {
        LocalDateTime f;
        try {
            f = Entradas.fechaHora(llegada);
        } catch (DateTimeParseException ex) {
            return List.of("Revisa la fecha (dd/mm/aaaa hh:mm).");
        }
        if (f.isBefore(envio.fechaSalida())) {
            return List.of("La llegada no puede ser antes de la salida de Metapa (" + Formato.fechaHora(envio.fechaSalida()) + ").");
        }
        try {
            envios.recibir(envio.codigo(), f);
        } catch (SQLException ex) {
            return List.of(Conexion.mensaje(ex));
        }
        nav.avisar("Envío " + envio.codigo() + " recibido en Tapachula.");
        nav.ir("estado_envio", envio.codigo());
        return List.of();
    }

    public void cancelar(String codigo) {
        try {
            envios.cancelar(codigo);
            nav.avisar("Envío " + codigo + " cancelado.");
        } catch (SQLException ex) {
            nav.avisar(Conexion.mensaje(ex));
        }
        nav.ir("estado_envio", codigo);
    }
}
