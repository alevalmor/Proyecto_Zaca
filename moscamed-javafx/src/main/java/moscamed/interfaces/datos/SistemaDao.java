package moscamed.interfaces.datos;

import java.sql.SQLException;
import java.util.List;
import moscamed.modelo.Auditoria;
import moscamed.modelo.EstadoSede;

public interface SistemaDao {
    List<String> TABLAS = List.of("responsable", "centro_empaque", "lote_produccion", "control_calidad", "vehiculo",
            "zona_liberacion", "envio", "embarque_aereo", "liberacion", "historial");

    List<EstadoSede> estadoSedes();

    List<Auditoria> bitacora(int cuantos) throws SQLException;
}
