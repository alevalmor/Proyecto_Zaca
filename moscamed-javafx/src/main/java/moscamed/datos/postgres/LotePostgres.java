package moscamed.datos.postgres;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import moscamed.datos.Conexion;
import moscamed.interfaces.datos.LoteDao;
import moscamed.modelo.CentroEmpaque;
import moscamed.modelo.ControlCalidad;
import moscamed.modelo.DatosCalidad;
import moscamed.modelo.DatosLote;
import moscamed.modelo.Envio;
import moscamed.modelo.Lote;
import moscamed.modelo.Pagina;
import moscamed.modelo.ResumenPlanta;
import moscamed.modelo.Sesion;

public final class LotePostgres implements LoteDao {
    @Override
    public Pagina<Lote> buscar(String codigo, String estado, LocalDate desde, LocalDate hasta, int pagina) throws SQLException {
        List<String> filtros = new ArrayList<>(List.of("true"));
        List<Object> p = new ArrayList<>();
        if (codigo != null && !codigo.isBlank()) { filtros.add("l.codigo ILIKE ?"); p.add("%" + codigo.trim() + "%"); }
        if (estado != null) { filtros.add("l.estado = ?"); p.add(estado); }
        if (desde != null) { filtros.add("l.fecha_produccion >= ?"); p.add(desde); }
        if (hasta != null) { filtros.add("l.fecha_produccion <= ?"); p.add(hasta); }
        String donde = String.join(" AND ", filtros);
        long total = Filas.l(Conexion.q1("SELECT count(*) AS n FROM v_lote l WHERE " + donde, p.toArray()), "n");
        p.add(Pagina.TAMANO);
        p.add(Pagina.desplazamiento(pagina));
        var filas = Conexion.q("SELECT l.*, c.clave AS centro_clave FROM v_lote l JOIN centro_empaque c ON c.id = l.centro_empaque_id WHERE "
                + donde + " ORDER BY l.fecha_produccion DESC, l.codigo DESC LIMIT ? OFFSET ?", p.toArray());
        return new Pagina<>(filas.stream().map(Filas::lote).toList(), total);
    }

    @Override
    public Optional<Lote> porCodigo(String codigo) throws SQLException {
        return Optional.ofNullable(Conexion.q1("SELECT * FROM v_lote WHERE codigo = ?", codigo)).map(Filas::lote);
    }

    @Override
    public Optional<Lote> detalle(String codigo) throws SQLException {
        return Optional.ofNullable(Conexion.q1("""
                SELECT l.*, c.clave AS centro_clave, c.nombre AS centro_nombre, r.num_empleado, r.nombre AS r_nombre, r.ap_paterno
                FROM v_lote l JOIN centro_empaque c ON c.id = l.centro_empaque_id JOIN responsable r ON r.id = l.responsable_id
                WHERE l.codigo = ?""", codigo)).map(Filas::lote);
    }

    @Override
    public Optional<String> codigoPorOmision() throws SQLException {
        return Optional.ofNullable(Conexion.q1("SELECT codigo FROM v_lote ORDER BY (estado = 'PRODUCIDO') DESC, fecha_produccion DESC LIMIT 1"))
                .map(r -> Filas.s(r, "codigo"));
    }

    @Override
    public List<Lote> todos() throws SQLException {
        return Conexion.q("SELECT * FROM v_lote ORDER BY fecha_produccion DESC, codigo DESC").stream().map(Filas::lote).toList();
    }

    @Override
    public List<Lote> recientes(int cuantos) throws SQLException {
        return Conexion.q("SELECT * FROM v_lote ORDER BY fecha_produccion DESC, codigo DESC LIMIT ?", cuantos).stream().map(Filas::lote).toList();
    }

    @Override
    public List<Lote> pendientesDeCalidad(int cuantos) throws SQLException {
        return Conexion.q("SELECT * FROM v_lote WHERE estado = 'PRODUCIDO' ORDER BY fecha_produccion DESC LIMIT ?", cuantos)
                .stream().map(Filas::lote).toList();
    }

    @Override
    public ResumenPlanta resumenPlanta() throws SQLException {
        var k = Conexion.q1("""
                SELECT count(*) FILTER (WHERE fecha_produccion >= date_trunc('week', current_date)) AS semana,
                       coalesce(sum(cantidad_pupas) FILTER (WHERE fecha_produccion >= date_trunc('week', current_date)), 0) AS pupas,
                       count(*) FILTER (WHERE estado = 'PRODUCIDO') AS sin_cc,
                       count(*) FILTER (WHERE estado = 'RECHAZADO' AND fecha_produccion >= date_trunc('month', current_date)) AS rechazados
                FROM v_lote""");
        var rechazo = Conexion.q1("""
                SELECT l.codigo, c.vuelo < 65 AS vuelo, c.emergencia < 75 AS emergencia FROM v_lote l
                JOIN control_calidad c ON c.lote_id = l.id AND NOT c.aprobado
                WHERE l.estado = 'RECHAZADO' AND l.fecha_produccion >= date_trunc('month', current_date)
                ORDER BY l.fecha_produccion DESC LIMIT 1""");
        return new ResumenPlanta(Filas.l(k, "semana"), Filas.l(k, "pupas"), Filas.l(k, "sin_cc"), Filas.l(k, "rechazados"),
                rechazo == null ? null : Filas.s(rechazo, "codigo"),
                rechazo != null && Filas.si(rechazo, "vuelo"), rechazo != null && Filas.si(rechazo, "emergencia"));
    }

    @Override
    public List<CentroEmpaque> centros() throws SQLException {
        return Conexion.q("SELECT * FROM centro_empaque ORDER BY clave").stream()
                .map(c -> new CentroEmpaque(Filas.i(c, "id"), Filas.s(c, "clave"), Filas.s(c, "nombre"))).toList();
    }

    @Override
    public String siguienteCodigo(int anio) throws SQLException {
        String prefijo = "MET-" + anio + "-";
        var fila = Conexion.q1("SELECT coalesce(max(substr(codigo, ?)::int), 0) + 1 AS n FROM lote_produccion WHERE codigo LIKE ?",
                prefijo.length() + 1, prefijo + "%");
        return prefijo + String.format("%04d", Filas.l(fila, "n"));
    }

    @Override
    public String registrar(DatosLote d) throws SQLException {
        String[] codigo = new String[1];
        Conexion.tx(() -> {
            codigo[0] = Filas.s(Conexion.q1("""
                    INSERT INTO lote_produccion (fecha_produccion, fecha_irradiacion, dosis_gy, cantidad_pupas, cepa, tinte,
                    centro_empaque_id, responsable_id, observaciones) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING codigo""",
                    d.fechaProduccion(), d.fechaIrradiacion(), d.dosisGy(), d.cantidadPupas(), d.cepa(), d.tinte(),
                    d.centroEmpaqueId(), Sesion.actual().responsableId(), d.observaciones()), "codigo");
            Conexion.auditar("Registró el lote " + codigo[0]);
        });
        return codigo[0];
    }

    @Override
    public void modificar(int id, String codigo, DatosLote d) throws SQLException {
        Conexion.tx(() -> {
            Conexion.exec("""
                    UPDATE lote_produccion SET fecha_produccion = ?, fecha_irradiacion = ?, dosis_gy = ?, cantidad_pupas = ?,
                    cepa = ?, tinte = ?, centro_empaque_id = ?, observaciones = ? WHERE id = ?""",
                    d.fechaProduccion(), d.fechaIrradiacion(), d.dosisGy(), d.cantidadPupas(), d.cepa(), d.tinte(),
                    d.centroEmpaqueId(), d.observaciones(), id);
            Conexion.auditar("Modificó el lote " + codigo);
        });
    }

    @Override
    public List<ControlCalidad> calidades(int loteId) throws SQLException {
        return Conexion.q("SELECT c.*, r.num_empleado FROM control_calidad c JOIN responsable r ON r.id = c.responsable_id WHERE c.lote_id = ?", loteId)
                .stream().map(c -> new ControlCalidad(Filas.s(c, "etapa"), Filas.dec(c, "emergencia"), Filas.dec(c, "vuelo"),
                        Filas.dec(c, "esterilidad"), Filas.dec(c, "peso_mg"), Filas.si(c, "aprobado"), Filas.fecha(c, "fecha"),
                        Filas.s(c, "observaciones"), Filas.s(c, "num_empleado"))).toList();
    }

    @Override
    public void guardarCalidad(int loteId, String codigo, String etapa, DatosCalidad d) throws SQLException {
        Conexion.tx(() -> {
            Conexion.exec("""
                    INSERT INTO control_calidad (lote_id, etapa, emergencia, vuelo, esterilidad, peso_mg, responsable_id, observaciones)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (lote_id, etapa) DO UPDATE SET emergencia = EXCLUDED.emergencia, vuelo = EXCLUDED.vuelo,
                      esterilidad = EXCLUDED.esterilidad, peso_mg = EXCLUDED.peso_mg, fecha = current_date,
                      responsable_id = EXCLUDED.responsable_id, observaciones = EXCLUDED.observaciones""",
                    loteId, etapa, d.emergencia(), d.vuelo(), d.esterilidad(), d.pesoMg(), Sesion.actual().responsableId(), d.observaciones());
            Conexion.auditar("Registró calidad " + etapa.toLowerCase() + " de " + codigo);
        });
    }

    @Override
    public List<Envio> envios(int loteId) throws SQLException {
        return Conexion.q("""
                SELECT e.*, v.matricula,
                       (SELECT string_agg(DISTINCT z.clave || ' · ' || z.municipio, ', ') FROM liberacion li
                        JOIN zona_liberacion z ON z.id = li.zona_id WHERE li.envio_id = e.id) AS zonas
                FROM v_envio e JOIN vehiculo v ON v.id = e.vehiculo_id WHERE e.lote_id = ? ORDER BY e.fecha_salida""", loteId)
                .stream().map(Filas::envio).toList();
    }
}
