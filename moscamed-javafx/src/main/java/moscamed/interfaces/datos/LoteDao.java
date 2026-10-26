package moscamed.interfaces.datos;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import moscamed.modelo.CentroEmpaque;
import moscamed.modelo.ControlCalidad;
import moscamed.modelo.DatosCalidad;
import moscamed.modelo.DatosLote;
import moscamed.modelo.Envio;
import moscamed.modelo.Lote;
import moscamed.modelo.Pagina;
import moscamed.modelo.ResumenPlanta;

public interface LoteDao {
    Pagina<Lote> buscar(String codigo, String estado, LocalDate desde, LocalDate hasta, int pagina) throws SQLException;

    Optional<Lote> porCodigo(String codigo) throws SQLException;

    Optional<Lote> detalle(String codigo) throws SQLException;

    Optional<String> codigoPorOmision() throws SQLException;

    List<Lote> todos() throws SQLException;

    List<Lote> recientes(int cuantos) throws SQLException;

    List<Lote> pendientesDeCalidad(int cuantos) throws SQLException;

    ResumenPlanta resumenPlanta() throws SQLException;

    List<CentroEmpaque> centros() throws SQLException;

    String siguienteCodigo(int anio) throws SQLException;

    String registrar(DatosLote datos) throws SQLException;

    void modificar(int id, String codigo, DatosLote datos) throws SQLException;

    List<ControlCalidad> calidades(int loteId) throws SQLException;

    void guardarCalidad(int loteId, String codigo, String etapa, DatosCalidad datos) throws SQLException;

    List<Envio> envios(int loteId) throws SQLException;
}
