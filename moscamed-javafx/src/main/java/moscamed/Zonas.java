package moscamed;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Prioridad de zonas: cruza el trampeo (postgres_fdw) con las liberaciones. La usan el inicio y la dispersión. */
final class Zonas {
    private Zonas() {}

    /** Zonas ordenadas por prioridad; la regla vive en la base (función zonas_prioritarias de moscamed.sql). */
    static List<Map<String, Object>> prioridad(int dias, LocalDate corte) throws SQLException {
        return Db.q("SELECT * FROM zonas_prioritarias(?, ?)", corte, dias);
    }
}
