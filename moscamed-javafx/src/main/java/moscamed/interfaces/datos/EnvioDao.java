package moscamed.interfaces.datos;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import moscamed.modelo.DatosEnvio;
import moscamed.modelo.Envio;
import moscamed.modelo.InformeDispersion;
import moscamed.modelo.LoteDisponible;
import moscamed.modelo.Pagina;
import moscamed.modelo.ResumenEmpaque;
import moscamed.modelo.Vehiculo;

public interface EnvioDao {
    Pagina<Envio> buscar(String texto, String estado, int pagina) throws SQLException;

    Map<String, Long> conteoPorEstado() throws SQLException;

    Optional<Envio> porCodigo(String codigo) throws SQLException;

    Optional<Envio> detalle(String codigo) throws SQLException;

    Optional<String> codigoPorOmision() throws SQLException;

    List<Envio> enCamino(int cuantos) throws SQLException;

    ResumenEmpaque resumenEmpaque() throws SQLException;

    List<LoteDisponible> lotesDisponibles(Envio modificando) throws SQLException;

    List<Vehiculo> vehiculos() throws SQLException;

    String registrar(DatosEnvio datos) throws SQLException;

    void modificar(int id, String codigo, DatosEnvio datos) throws SQLException;

    void recibir(String codigo, LocalDateTime llegada) throws SQLException;

    void cancelar(String codigo) throws SQLException;

    List<InformeDispersion> dispersiones(int envioId) throws SQLException;
}
