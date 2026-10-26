package moscamed.datos.postgres;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import moscamed.datos.Conexion;
import moscamed.interfaces.datos.UsuarioDao;
import moscamed.modelo.NuevoUsuario;
import moscamed.modelo.Pagina;
import moscamed.modelo.Responsable;
import moscamed.modelo.ResumenUsuarios;

public final class UsuarioPostgres implements UsuarioDao {
    @Override
    public Pagina<Responsable> buscar(String texto, String perfil, Boolean activo, int pagina) throws SQLException {
        List<String> filtros = new ArrayList<>(List.of("true"));
        List<Object> p = new ArrayList<>();
        if (texto != null && !texto.isBlank()) {
            filtros.add("(nombre || ' ' || ap_paterno || ' ' || coalesce(ap_materno, '') ILIKE ? OR num_empleado ILIKE ?)");
            p.add("%" + texto.trim() + "%");
            p.add("%" + texto.trim() + "%");
        }
        if (perfil != null) { filtros.add("perfil = ?"); p.add(perfil); }
        if (activo != null) { filtros.add("activo = ?"); p.add(activo); }
        String donde = String.join(" AND ", filtros);
        long total = Filas.l(Conexion.q1("SELECT count(*) AS n FROM responsable WHERE " + donde, p.toArray()), "n");
        p.add(Pagina.TAMANO);
        p.add(Pagina.desplazamiento(pagina));
        var filas = Conexion.q("SELECT * FROM responsable WHERE " + donde + " ORDER BY activo DESC, perfil, num_empleado LIMIT ? OFFSET ?", p.toArray());
        return new Pagina<>(filas.stream().map(Filas::responsable).toList(), total);
    }

    @Override
    public ResumenUsuarios resumen() throws SQLException {
        var r = Conexion.q1("""
                SELECT count(*) FILTER (WHERE perfil = 'PES') AS pes, count(*) FILTER (WHERE perfil = 'PEM') AS pem,
                       count(*) FILTER (WHERE perfil = 'PEA') AS pea, count(*) FILTER (WHERE perfil = 'PED') AS ped,
                       count(*) FILTER (WHERE NOT activo) AS bajas FROM responsable""");
        return new ResumenUsuarios(Filas.l(r, "pes"), Filas.l(r, "pem"), Filas.l(r, "pea"), Filas.l(r, "ped"), Filas.l(r, "bajas"));
    }

    @Override
    public void restablecerClave(String usuario, String clave) throws SQLException {
        Conexion.enAmbos("ALTER ROLE " + Conexion.ident(usuario) + " PASSWORD " + Conexion.literal(clave));
        Conexion.auditar("Restableció la contraseña de " + usuario);
    }

    @Override
    public void cambiarAcceso(int id, String usuario, boolean activo) throws SQLException {
        Conexion.enAmbos("ALTER ROLE " + Conexion.ident(usuario) + (activo ? " LOGIN" : " NOLOGIN"));
        Conexion.exec("UPDATE responsable SET activo = ? WHERE id = ?", activo, id);
        Conexion.auditar((activo ? "Reactivó a " : "Dio de baja a ") + usuario);
    }

    @Override
    public void crearRol(NuevoUsuario n) throws SQLException {
        Conexion.enAmbos("CREATE ROLE " + Conexion.ident(n.usuario()) + " LOGIN PASSWORD " + Conexion.literal(n.clave())
                + " CONNECTION LIMIT 30 IN ROLE " + Conexion.ident(n.rol()));
    }

    @Override
    public String registrar(NuevoUsuario n) throws SQLException {
        String[] num = new String[1];
        Conexion.tx(() -> {
            String prefijo = n.prefijoEmpleado();
            long siguiente = Filas.l(Conexion.q1("SELECT coalesce(max(substr(num_empleado, 5)::int), 0) + 1 AS n FROM responsable WHERE num_empleado LIKE ?",
                    prefijo + "%"), "n");
            num[0] = prefijo + String.format("%04d", siguiente);
            int rid = Filas.i(Conexion.q1("""
                    INSERT INTO responsable (num_empleado, nombre, ap_paterno, ap_materno, puesto, telefono, correo, sede, perfil, usuario_bd)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""",
                    num[0], n.nombre(), n.apPaterno(), n.apMaterno(), n.puesto(), n.telefono(), n.correo(), n.sede(), n.perfil(), n.usuario()), "id");
            for (var e : n.quitados().entrySet()) {
                Conexion.exec("INSERT INTO permiso_vista VALUES (?, ?, ?, ?)", rid, e.getKey(), e.getValue()[0], e.getValue()[1]);
            }
            Conexion.auditar("Creó el usuario " + n.usuario() + " (" + n.perfil() + ")");
        });
        return num[0];
    }
}
