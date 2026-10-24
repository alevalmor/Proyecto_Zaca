package moscamed;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Conexión a PostgreSQL con el usuario de la persona: los permisos los aplica la base de datos con sus roles.
 * Guarda la sesión abierta (una persona por programa de escritorio).
 */
public final class Db {
    private Db() {}

    public static final Map<String, String[]> SERVIDORES = new LinkedHashMap<>();
    static {
        SERVIDORES.put("PLANTA", new String[]{env("PG_HOST_PLANTA", "192.168.10.10"), env("PG_PORT_PLANTA", "9999")});
        SERVIDORES.put("EMPAQUE", new String[]{env("PG_HOST_EMPAQUE", "192.168.20.10"), env("PG_PORT_EMPAQUE", "9999")});
    }
    private static final String BASE = env("PG_DB", "moscamed");

    // Sesión abierta
    public static String sede, usuario, perfil, nombre, nombreCorto, empleado, entrada;
    public static int responsableId;
    public static Map<String, boolean[]> permisos = Map.of();
    private static String clave;
    private static Connection con;

    private static String env(String nombre, String defecto) {
        String v = System.getenv(nombre);
        return v == null || v.isBlank() ? defecto : v;
    }

    public interface Accion { void correr() throws SQLException; }

    public static Connection conectar(String sede, String usuario, String clave) throws SQLException {
        return DriverManager.getConnection(url(sede), propiedades(usuario, clave));
    }

    /** Se conecta al primer servidor que responda. Los usuarios existen en los dos, así que si la contraseña
     *  está mal o el usuario está dado de baja, el otro servidor diría lo mismo y no se intenta. */
    public static Map.Entry<String, Connection> conectarCualquiera(String usuario, String clave) throws SQLException {
        SQLException ultima = null;
        for (String sede : SERVIDORES.keySet()) {
            try {
                return Map.entry(sede, conectar(sede, usuario, clave));
            } catch (SQLException e) {
                if (!String.valueOf(e.getSQLState()).startsWith("08")) throw e;
                ultima = e;
            }
        }
        throw ultima;
    }

    private static String url(String sede) {
        String[] s = SERVIDORES.get(sede);
        return "jdbc:postgresql://" + s[0] + ":" + s[1] + "/" + BASE;
    }

    private static Properties propiedades(String usuario, String clave) {
        Properties p = new Properties();
        p.setProperty("user", usuario);
        p.setProperty("password", clave);
        p.setProperty("connectTimeout", "5");
        return p;
    }

    /** Nodos de Pgpool-II de una sede. Pgpool solo contesta SHOW pool_nodes en el protocolo simple;
     *  con el normal de JDBC la orden llega a PostgreSQL y falla. Sin Pgpool también falla. */
    public static List<Map<String, Object>> poolNodes(String sede) throws SQLException {
        Properties p = propiedades(usuario, clave);
        p.setProperty("preferQueryMode", "simple");
        try (Connection c = DriverManager.getConnection(url(sede), p);
             ResultSet rs = c.createStatement().executeQuery("SHOW pool_nodes")) {
            List<Map<String, Object>> filas = new ArrayList<>();
            while (rs.next()) { // Pgpool no manda tipos de columna: getObject falla, se lee todo como texto
                Map<String, Object> fila = new LinkedHashMap<>();
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) fila.put(rs.getMetaData().getColumnLabel(i), rs.getString(i));
                filas.add(fila);
            }
            return filas;
        }
    }

    public static void abrir(String sede, String usuario, String clave, Connection conexion) {
        Db.sede = sede;
        Db.usuario = usuario;
        Db.clave = clave;
        Db.con = conexion;
        entrada = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    public static void cerrar() {
        try {
            if (con != null) con.close();
        } catch (SQLException ignorada) {
            // la conexión ya estaba rota; no hay nada que cerrar
        }
        con = null;
        clave = null;
        sede = usuario = perfil = null;
        permisos = Map.of();
    }

    public static boolean claveCorrecta(String intento) {
        return intento.equals(clave);
    }

    public static void cambiarClaveGuardada(String nueva) {
        clave = nueva;
    }

    public static String otraSede() {
        return "PLANTA".equals(sede) ? "EMPAQUE" : "PLANTA";
    }

    public static Connection con() throws SQLException {
        if (con == null || con.isClosed()) con = conectar(sede, usuario, clave);
        return con;
    }

    /** Conexión al servidor de la otra sede con el mismo usuario (los roles no se replican). */
    public static Connection conEn(String otra) throws SQLException {
        return conectar(otra, usuario, clave);
    }

    public static List<Map<String, Object>> q(String sql, Object... params) throws SQLException {
        return q(con(), sql, params);
    }

    public static List<Map<String, Object>> q(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement st = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) st.setObject(i + 1, params[i]);
            try (ResultSet rs = st.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                List<Map<String, Object>> filas = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> fila = new LinkedHashMap<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        Object v = rs.getObject(i);
                        if (v instanceof java.sql.Date d) v = d.toLocalDate();
                        else if (v instanceof Timestamp t) v = t.toLocalDateTime();
                        fila.put(md.getColumnLabel(i), v);
                    }
                    filas.add(fila);
                }
                return filas;
            }
        }
    }

    public static Map<String, Object> q1(String sql, Object... params) throws SQLException {
        List<Map<String, Object>> filas = q(sql, params);
        return filas.isEmpty() ? null : filas.get(0);
    }

    public static int exec(String sql, Object... params) throws SQLException {
        return exec(con(), sql, params);
    }

    public static int exec(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement st = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) st.setObject(i + 1, params[i]);
            return st.executeUpdate();
        }
    }

    /** Corre varias órdenes en una transacción: o todas o ninguna. */
    public static void tx(Accion accion) throws SQLException {
        Connection c = con();
        c.setAutoCommit(false);
        try {
            accion.correr();
            c.commit();
        } catch (SQLException | RuntimeException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(true);
        }
    }

    public static void auditar(String accion) throws SQLException {
        exec("INSERT INTO auditoria (accion) VALUES (?)", accion);
    }

    public static String errorConexion(SQLException e, String servidor) {
        String texto = String.valueOf(e.getMessage());
        if (texto.contains("not permitted to log in")) return "Tu usuario está dado de baja.";
        // Directo PostgreSQL manda 28P01; Pgpool lo envuelve en XX000 "backend authentication failed".
        if (String.valueOf(e.getSQLState()).startsWith("28") || texto.contains("password") || texto.contains("authentication failed"))
            return "Usuario o contraseña incorrectos.";
        return "No responde el servidor de " + servidor + ".";
    }

    public static String mensaje(SQLException e) {
        String estado = String.valueOf(e.getSQLState());
        if (estado.equals("42501")) return "La base de datos no permite esta acción con tu perfil.";
        if (estado.equals("23505")) return "Ya existe un registro con ese código o usuario; vuelve a intentarlo.";
        if (estado.startsWith("08")) return "Se perdió la conexión con el servidor. Vuelve a intentarlo.";
        // Regla de negocio de los triggers de esquema.sql: su texto ya está escrito para la persona.
        if (estado.equals("P0001") && e instanceof org.postgresql.util.PSQLException p && p.getServerErrorMessage() != null)
            return p.getServerErrorMessage().getMessage();
        return "La base de datos rechazó los datos: " + e.getMessage().lines().findFirst().orElse("");
    }

    /** Escapa un identificador para DDL de roles (CREATE/ALTER ROLE no aceptan parámetros). */
    public static String ident(String nombre) {
        return "\"" + nombre.replace("\"", "\"\"") + "\"";
    }

    /** Escapa una cadena literal para DDL de roles. */
    public static String literal(String texto) {
        return "'" + texto.replace("'", "''") + "'";
    }
}
