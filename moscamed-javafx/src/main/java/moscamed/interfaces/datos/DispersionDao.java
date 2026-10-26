package moscamed.interfaces.datos;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import moscamed.modelo.Existencia;
import moscamed.modelo.InformeDispersion;
import moscamed.modelo.Pagina;
import moscamed.modelo.ResumenDispersion;
import moscamed.modelo.Zona;

public interface DispersionDao {
    Pagina<InformeDispersion> buscar(String texto, String punto, int pagina) throws SQLException;

    ResumenDispersion resumen() throws SQLException;

    List<Existencia> existencias() throws SQLException;

    List<Existencia> existenciasPara(InformeDispersion modificando) throws SQLException;

    Optional<InformeDispersion> porId(long id) throws SQLException;

    void registrar(InformeDispersion informe) throws SQLException;

    void modificar(InformeDispersion informe) throws SQLException;

    void borrar(long id, String descripcion) throws SQLException;

    List<Zona> prioridad(int dias, LocalDate corte) throws SQLException;
}
