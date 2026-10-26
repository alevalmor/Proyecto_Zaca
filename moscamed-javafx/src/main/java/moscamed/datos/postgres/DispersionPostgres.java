package moscamed.datos.postgres;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import moscamed.datos.Conexion;
import moscamed.interfaces.datos.DispersionDao;
import moscamed.modelo.Existencia;
import moscamed.modelo.InformeDispersion;
import moscamed.modelo.Pagina;
import moscamed.modelo.ResumenDispersion;
import moscamed.modelo.Sesion;
import moscamed.modelo.Zona;

public final class DispersionPostgres implements DispersionDao {
    @Override
    public Pagina<InformeDispersion> buscar(String texto, String punto, int pagina) throws SQLException {
        List<String> filtros = new ArrayList<>(List.of("true"));
        List<Object> p = new ArrayList<>();
        if (texto != null && !texto.isBlank()) {
            filtros.add("(li.avioneta ILIKE ? OR z.clave ILIKE ? OR z.nombre ILIKE ? OR coalesce(e.codigo, b.codigo) ILIKE ?)");
            for (int i = 0; i < 4; i++) p.add("%" + texto.trim() + "%");
        }
        if (punto != null) { filtros.add("li.punto = ?"); p.add(punto); }
        String desde = " FROM liberacion li JOIN zona_liberacion z ON z.id = li.zona_id"
                + " LEFT JOIN envio e ON e.id = li.envio_id LEFT JOIN embarque_aereo b ON b.id = li.embarque_id WHERE "
                + String.join(" AND ", filtros);
        long total = Filas.l(Conexion.q1("SELECT count(*) AS n" + desde, p.toArray()), "n");
        p.add(Pagina.TAMANO);
        p.add(Pagina.desplazamiento(pagina));
        var filas = Conexion.q("SELECT li.*, z.clave, z.nombre, e.codigo AS envio, b.codigo AS embarque" + desde
                + " ORDER BY li.fecha DESC LIMIT ? OFFSET ?", p.toArray());
        return new Pagina<>(filas.stream().map(Filas::informe).toList(), total);
    }

    @Override
    public ResumenDispersion resumen() throws SQLException {
        var k = Conexion.q1("""
                SELECT (SELECT coalesce(sum(cantidad), 0) FROM liberacion WHERE fecha >= date_trunc('month', current_date)) AS mes,
                       (SELECT count(*) FROM liberacion WHERE fecha >= date_trunc('month', current_date)) AS informes,
                       (SELECT coalesce(sum(quedan), 0) FROM v_existencia WHERE punto = 'EMPAQUE') AS empaque,
                       (SELECT coalesce(sum(quedan), 0) FROM v_existencia WHERE punto = 'AEROPUERTO') AS aeropuerto""");
        return new ResumenDispersion(Filas.l(k, "mes"), Filas.l(k, "informes"), Filas.l(k, "empaque"), Filas.l(k, "aeropuerto"));
    }

    @Override
    public List<Existencia> existencias() throws SQLException {
        return Conexion.q("SELECT * FROM v_existencia WHERE quedan > 0 ORDER BY desde").stream().map(Filas::existencia).toList();
    }

    @Override
    public List<Existencia> existenciasPara(InformeDispersion m) throws SQLException {
        return Conexion.q("""
                SELECT * FROM v_existencia WHERE quedan > 0 OR (origen = 'ENVIO' AND id = ?) OR (origen = 'EMBARQUE' AND id = ? AND punto = ?)
                ORDER BY desde""", m == null ? null : m.envioId(), m == null ? null : m.embarqueId(), m == null ? null : m.punto())
                .stream().map(Filas::existencia).toList();
    }

    @Override
    public Optional<InformeDispersion> porId(long id) throws SQLException {
        return Optional.ofNullable(Conexion.q1("SELECT * FROM liberacion WHERE id = ?", id)).map(Filas::informe);
    }

    @Override
    public void registrar(InformeDispersion i) throws SQLException {
        Conexion.tx(() -> {
            Conexion.exec("""
                    INSERT INTO liberacion (envio_id, embarque_id, punto, zona_id, fecha, cantidad, superficie_ha, altitud_m,
                    avioneta, piloto, responsable_id, observaciones) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    i.envioId(), i.embarqueId(), i.punto(), i.zonaId(), i.fecha(), i.cantidad(), i.superficieHa(), i.altitudM(),
                    i.avioneta(), i.piloto(), Sesion.actual().responsableId(), i.observaciones());
            Conexion.auditar("Registró dispersión de " + i.fuenteCodigo() + " en " + i.zonaClave());
        });
    }

    @Override
    public void modificar(InformeDispersion i) throws SQLException {
        Conexion.tx(() -> {
            Conexion.exec("""
                    UPDATE liberacion SET envio_id = ?, embarque_id = ?, punto = ?, zona_id = ?, fecha = ?, cantidad = ?,
                    superficie_ha = ?, altitud_m = ?, avioneta = ?, piloto = ?, observaciones = ? WHERE id = ?""",
                    i.envioId(), i.embarqueId(), i.punto(), i.zonaId(), i.fecha(), i.cantidad(), i.superficieHa(), i.altitudM(),
                    i.avioneta(), i.piloto(), i.observaciones(), i.id());
            Conexion.auditar("Modificó dispersión de " + i.fuenteCodigo() + " en " + i.zonaClave());
        });
    }

    @Override
    public void borrar(long id, String descripcion) throws SQLException {
        Conexion.tx(() -> {
            Conexion.exec("DELETE FROM liberacion WHERE id = ?", id);
            Conexion.auditar("Borró el informe de dispersión de " + descripcion);
        });
    }

    @Override
    public List<Zona> prioridad(int dias, LocalDate corte) throws SQLException {
        return Conexion.q("SELECT * FROM zonas_prioritarias(?, ?)", corte, dias).stream().map(Filas::zona).toList();
    }
}
