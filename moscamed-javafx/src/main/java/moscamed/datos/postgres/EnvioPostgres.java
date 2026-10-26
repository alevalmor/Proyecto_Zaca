package moscamed.datos.postgres;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import moscamed.datos.Conexion;
import moscamed.interfaces.datos.EnvioDao;
import moscamed.modelo.DatosEnvio;
import moscamed.modelo.Envio;
import moscamed.modelo.InformeDispersion;
import moscamed.modelo.LoteDisponible;
import moscamed.modelo.Pagina;
import moscamed.modelo.ResumenEmpaque;
import moscamed.modelo.Sesion;
import moscamed.modelo.Vehiculo;

public final class EnvioPostgres implements EnvioDao {
    @Override
    public Pagina<Envio> buscar(String texto, String estado, int pagina) throws SQLException {
        List<String> filtros = new ArrayList<>(List.of("true"));
        List<Object> p = new ArrayList<>();
        if (texto != null && !texto.isBlank()) {
            filtros.add("(e.codigo ILIKE ? OR l.codigo ILIKE ?)");
            p.add("%" + texto.trim() + "%");
            p.add("%" + texto.trim() + "%");
        }
        if (estado != null) { filtros.add("e.estado = ?"); p.add(estado); }
        String desde = " FROM v_envio e JOIN lote_produccion l ON l.id = e.lote_id JOIN vehiculo v ON v.id = e.vehiculo_id WHERE "
                + String.join(" AND ", filtros);
        long total = Filas.l(Conexion.q1("SELECT count(*) AS n" + desde, p.toArray()), "n");
        p.add(Pagina.TAMANO);
        p.add(Pagina.desplazamiento(pagina));
        var filas = Conexion.q("SELECT e.*, l.codigo AS lote, v.matricula" + desde + " ORDER BY e.fecha_salida DESC LIMIT ? OFFSET ?", p.toArray());
        return new Pagina<>(filas.stream().map(Filas::envio).toList(), total);
    }

    @Override
    public Map<String, Long> conteoPorEstado() throws SQLException {
        Map<String, Long> resumen = new HashMap<>();
        for (var r : Conexion.q("SELECT estado, count(*) AS n FROM v_envio GROUP BY estado")) resumen.put(Filas.s(r, "estado"), Filas.l(r, "n"));
        return resumen;
    }

    @Override
    public Optional<Envio> porCodigo(String codigo) throws SQLException {
        return Optional.ofNullable(Conexion.q1("SELECT * FROM v_envio WHERE codigo = ?", codigo)).map(Filas::envio);
    }

    @Override
    public Optional<Envio> detalle(String codigo) throws SQLException {
        return Optional.ofNullable(Conexion.q1("""
                SELECT e.*, l.codigo AS lote, v.matricula, v.modelo, r.num_empleado, r.nombre AS r_nombre, r.ap_paterno
                FROM v_envio e JOIN lote_produccion l ON l.id = e.lote_id JOIN vehiculo v ON v.id = e.vehiculo_id
                JOIN responsable r ON r.id = e.responsable_id WHERE e.codigo = ?""", codigo)).map(Filas::envio);
    }

    @Override
    public Optional<String> codigoPorOmision() throws SQLException {
        return Optional.ofNullable(Conexion.q1("SELECT codigo FROM v_envio ORDER BY (estado = 'EN CAMINO') DESC, fecha_salida DESC LIMIT 1"))
                .map(r -> Filas.s(r, "codigo"));
    }

    @Override
    public List<Envio> enCamino(int cuantos) throws SQLException {
        return Conexion.q("""
                SELECT e.*, l.codigo AS lote, v.matricula FROM v_envio e
                JOIN lote_produccion l ON l.id = e.lote_id JOIN vehiculo v ON v.id = e.vehiculo_id
                WHERE e.estado = 'EN CAMINO' ORDER BY e.fecha_salida DESC LIMIT ?""", cuantos).stream().map(Filas::envio).toList();
    }

    @Override
    public ResumenEmpaque resumenEmpaque() throws SQLException {
        var k = Conexion.q1("""
                SELECT (SELECT count(*) FROM v_lote WHERE estado = 'APROBADO') AS por_despachar,
                       (SELECT count(*) FROM v_envio WHERE estado = 'EN CAMINO') AS en_camino,
                       (SELECT coalesce(sum(quedan), 0) FROM v_existencia WHERE punto = 'EMPAQUE') AS en_centro""");
        return new ResumenEmpaque(Filas.l(k, "por_despachar"), Filas.l(k, "en_camino"), Filas.l(k, "en_centro"));
    }

    @Override
    public List<LoteDisponible> lotesDisponibles(Envio modificando) throws SQLException {
        var filas = Conexion.q("""
                SELECT l.id, l.codigo, l.cantidad_pupas, l.enviadas, cp.fecha AS fecha_cc FROM v_lote l
                JOIN control_calidad cp ON cp.lote_id = l.id AND cp.etapa = 'PLANTA'
                WHERE l.calidad_planta AND l.estado <> 'RECHAZADO' AND (l.cantidad_pupas > l.enviadas OR l.id = ?)
                ORDER BY l.fecha_produccion""", modificando != null ? modificando.loteId() : null);
        List<LoteDisponible> lotes = new ArrayList<>();
        for (var l : filas) {
            int id = Filas.i(l, "id");
            long propio = modificando != null && modificando.loteId() == id ? modificando.cantidad() : 0;
            long pupas = Filas.l(l, "cantidad_pupas"), enviadas = Filas.l(l, "enviadas");
            lotes.add(new LoteDisponible(id, Filas.s(l, "codigo"), pupas, enviadas, Filas.fecha(l, "fecha_cc"), pupas - enviadas + propio));
        }
        return lotes;
    }

    @Override
    public List<Vehiculo> vehiculos() throws SQLException {
        return Conexion.q("SELECT * FROM vehiculo ORDER BY activo DESC, matricula").stream()
                .map(v -> new Vehiculo(Filas.i(v, "id"), Filas.s(v, "matricula"), Filas.s(v, "modelo"), Filas.l(v, "capacidad"), Filas.si(v, "activo")))
                .toList();
    }

    @Override
    public String registrar(DatosEnvio d) throws SQLException {
        String[] codigo = new String[1];
        Conexion.tx(() -> {
            codigo[0] = Filas.s(Conexion.q1("""
                    INSERT INTO envio (lote_id, vehiculo_id, fecha_salida, cantidad, temperatura, responsable_id)
                    VALUES (?, ?, ?, ?, ?, ?) RETURNING codigo""", d.loteId(), d.vehiculoId(), d.fechaSalida(), d.cantidad(),
                    d.temperatura(), Sesion.actual().responsableId()), "codigo");
            Conexion.auditar("Registró el envío " + codigo[0]);
        });
        return codigo[0];
    }

    @Override
    public void modificar(int id, String codigo, DatosEnvio d) throws SQLException {
        Conexion.tx(() -> {
            Conexion.exec("UPDATE envio SET lote_id = ?, vehiculo_id = ?, cantidad = ?, fecha_salida = ?, temperatura = ? WHERE id = ?",
                    d.loteId(), d.vehiculoId(), d.cantidad(), d.fechaSalida(), d.temperatura(), id);
            Conexion.auditar("Modificó el envío " + codigo);
        });
    }

    @Override
    public void recibir(String codigo, LocalDateTime llegada) throws SQLException {
        Conexion.exec("CALL recibir_envio(?, ?)", codigo, llegada);
    }

    @Override
    public void cancelar(String codigo) throws SQLException {
        Conexion.exec("CALL cancelar_envio(?)", codigo);
    }

    @Override
    public List<InformeDispersion> dispersiones(int envioId) throws SQLException {
        return Conexion.q("""
                SELECT li.*, z.clave, z.nombre FROM liberacion li JOIN zona_liberacion z ON z.id = li.zona_id
                WHERE li.envio_id = ? ORDER BY li.fecha""", envioId).stream().map(Filas::informe).toList();
    }
}
