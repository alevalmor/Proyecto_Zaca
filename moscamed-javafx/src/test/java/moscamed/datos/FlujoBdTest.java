package moscamed.datos;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import moscamed.controlador.AccesoControlador;
import moscamed.datos.postgres.AccesoPostgres;
import moscamed.datos.postgres.DispersionPostgres;
import moscamed.datos.postgres.EnvioPostgres;
import moscamed.datos.postgres.LotePostgres;
import moscamed.datos.postgres.SistemaPostgres;
import moscamed.datos.postgres.UsuarioPostgres;
import moscamed.interfaces.datos.DispersionDao;
import moscamed.interfaces.datos.EnvioDao;
import moscamed.interfaces.datos.LoteDao;
import moscamed.interfaces.datos.SistemaDao;
import moscamed.interfaces.datos.UsuarioDao;
import moscamed.modelo.DatosCalidad;
import moscamed.modelo.DatosEnvio;
import moscamed.modelo.DatosLote;
import moscamed.modelo.InformeDispersion;
import moscamed.modelo.NuevoUsuario;
import moscamed.modelo.Sesion;
import moscamed.modelo.Vehiculo;

@EnabledIfEnvironmentVariable(named = "MOSCAMED_BD", matches = "si")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlujoBdTest {
    static final String CLAVE = "moscamed", USUARIO_PRUEBA = "prueba.mvc";
    static final List<String> TABLAS = List.of("lote_produccion", "control_calidad", "envio", "liberacion", "responsable", "auditoria");

    static final AccesoControlador acceso = new AccesoControlador(new AccesoPostgres());
    static final LoteDao lotes = new LotePostgres();
    static final EnvioDao envios = new EnvioPostgres();
    static final DispersionDao dispersiones = new DispersionPostgres();
    static final UsuarioDao usuarios = new UsuarioPostgres();
    static final SistemaDao sistema = new SistemaPostgres();

    static Timestamp inicio;
    static Map<String, Long> antes;
    static final LocalDateTime t0 = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
    static String lote, envio;

    static Connection superusuario(String sede) throws SQLException {
        return Conexion.conectar(sede, "postgres", System.getenv().getOrDefault("MOSCAMED_PG_CLAVE", "postgres"));
    }

    static Map<String, Long> conteos(String sede) throws SQLException {
        try (Connection c = superusuario(sede)) {
            var m = new java.util.LinkedHashMap<String, Long>();
            for (String t : TABLAS) m.put(t, ((Number) Conexion.q(c, "SELECT count(*) AS n FROM " + t).get(0).get("n")).longValue());
            return m;
        }
    }

    static void esperar(String que, BooleanSupplier listo) {
        long fin = System.currentTimeMillis() + 30_000;
        while (!listo.getAsBoolean()) {
            if (System.currentTimeMillis() > fin) fail("No llegó por replicación: " + que);
            try { Thread.sleep(250); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    static boolean igualesEnLasDosSedes() {
        try {
            return conteos("PLANTA").equals(conteos("EMPAQUE"));
        } catch (SQLException e) {
            return false;
        }
    }

    @BeforeAll
    static void marcarInicio() throws SQLException {
        esperar("las dos sedes iguales antes de empezar", FlujoBdTest::igualesEnLasDosSedes);
        antes = conteos("PLANTA");
        try (Connection c = superusuario("PLANTA")) {
            inicio = Timestamp.valueOf((LocalDateTime) Conexion.q(c, "SELECT localtimestamp AS t").get(0).get("t"));
        }
    }

    @AfterAll
    static void limpiar() throws SQLException {
        AccesoControlador.salir();
        esperar("lo escrito en empaque", FlujoBdTest::igualesEnLasDosSedes);
        try (Connection c = superusuario("PLANTA")) {
            c.setAutoCommit(false);
            for (String sql : List.of("DELETE FROM liberacion WHERE creado >= ?", "DELETE FROM control_calidad WHERE creado >= ?",
                    "DELETE FROM envio WHERE creado >= ?", "DELETE FROM lote_produccion WHERE creado >= ?",
                    "DELETE FROM permiso_vista WHERE responsable_id IN (SELECT id FROM responsable WHERE creado >= ?)",
                    "DELETE FROM responsable WHERE creado >= ?", "DELETE FROM auditoria WHERE fecha >= ?",
                    "DELETE FROM historial WHERE fecha >= ?")) {
                try (var st = c.prepareStatement(sql)) {
                    st.setTimestamp(1, inicio);
                    st.executeUpdate();
                }
            }
            c.commit();
        }
        for (String sede : Conexion.SERVIDORES.keySet()) {
            try (Connection c = superusuario(sede); Statement st = c.createStatement()) {
                st.execute("DROP ROLE IF EXISTS " + Conexion.ident(USUARIO_PRUEBA));
            }
        }
        esperar("la limpieza en empaque", FlujoBdTest::igualesEnLasDosSedes);
        assertEquals(antes, conteos("PLANTA"), "la base quedó como estaba");
    }

    @AfterEach
    void cerrarSesion() {
        AccesoControlador.salir();
    }

    static void entrar(String usuario, String sede) {
        var intento = acceso.entrarOperativo(usuario, CLAVE);
        assertTrue(intento.ok(), usuario + ": " + intento.mensaje());
        assertEquals(sede, Sesion.actual().sede(), usuario + " trabaja en el servidor de su sede");
    }

    static int idLote() throws SQLException {
        return lotes.porCodigo(lote).orElseThrow().id();
    }

    @Test
    @Order(1)
    void cadaQuienEntraASuPrograma() {
        assertEquals("Este usuario es de administrador: no puede entrar como operativo.", acceso.entrarOperativo("usr_admin", CLAVE).mensaje());
        assertEquals("Este usuario no es administrador.", acceso.entrarAdmin("usr_planta", CLAVE).mensaje());
        assertEquals("Usuario o contraseña incorrectos.", acceso.entrarOperativo("usr_planta", "equivocada").mensaje());
        assertNull(Sesion.actual());
        for (String[] u : new String[][]{{"usr_planta", "PLANTA", "PES"}, {"usr_empaque", "EMPAQUE", "PEM"},
                {"usr_aeropuerto", "EMPAQUE", "PEA"}, {"usr_dispersion", "EMPAQUE", "PED"}}) {
            entrar(u[0], u[1]);
            assertEquals(u[2], Sesion.actual().perfil());
            AccesoControlador.salir();
        }
        assertTrue(acceso.entrarAdmin("usr_admin", CLAVE).ok());
    }

    @Test
    @Order(2)
    void plantaRegistraLoteCalidadYEnvio() throws SQLException {
        entrar("usr_planta", "PLANTA");
        int centro = lotes.centros().get(0).id();
        assertEquals(lotes.siguienteCodigo(LocalDate.now().getYear()), lote = lotes.registrar(new DatosLote(LocalDate.now(), LocalDate.now(),
                new BigDecimal("120.0"), 30_000_000, "Tapachula-7 TSL", "Rosa fluorescente", centro, "Prueba automática")));
        assertEquals("PRODUCIDO", lotes.porCodigo(lote).orElseThrow().estado());
        Vehiculo camioneta = envios.vehiculos().stream().filter(Vehiculo::activo).findFirst().orElseThrow();

        var sinCalidad = assertThrows(SQLException.class, () -> envios.registrar(new DatosEnvio(idLote(), camioneta.id(), t0, 1000, BigDecimal.ONE)));
        assertEquals("El lote no está aprobado por la planta o tiene un control de calidad rechazado.", Conexion.mensaje(sinCalidad));

        lotes.guardarCalidad(idLote(), lote, "PLANTA", new DatosCalidad(new BigDecimal("86"), new BigDecimal("78"), new BigDecimal("99.9"),
                new BigDecimal("7.85"), null));
        assertEquals("APROBADO", lotes.porCodigo(lote).orElseThrow().estado());
        assertTrue(envios.lotesDisponibles(null).stream().anyMatch(l -> l.codigo().equals(lote) && l.disponibles() == 30_000_000));

        var noCabe = assertThrows(SQLException.class,
                () -> envios.registrar(new DatosEnvio(idLote(), camioneta.id(), t0, camioneta.capacidad() + 1, BigDecimal.ONE)));
        assertTrue(Conexion.mensaje(noCabe).startsWith("No cabe en el vehículo"), Conexion.mensaje(noCabe));

        envio = envios.registrar(new DatosEnvio(idLote(), camioneta.id(), t0, 20_000_000, new BigDecimal("3.1")));
        assertTrue(envio.startsWith("TAP-ENV-"));
        assertEquals("EN CAMINO", envios.porCodigo(envio).orElseThrow().estado());
        assertEquals("ENVIADO", lotes.detalle(lote).orElseThrow().estado());
        assertEquals(1, lotes.envios(idLote()).size());
        assertEquals(1, lotes.buscar(lote, null, null, null, 1).total());
        assertEquals(1, envios.buscar(envio, "EN CAMINO", 1).total());

        var llegada = assertThrows(SQLException.class, () -> envios.recibir(envio, t0.plusMinutes(1)));
        assertEquals("42501", llegada.getSQLState());

        String otro = envios.registrar(new DatosEnvio(idLote(), camioneta.id(), t0, 1_000_000, new BigDecimal("3.0")));
        envios.cancelar(otro);
        assertEquals("CANCELADO", envios.porCodigo(otro).orElseThrow().estado());
        assertEquals(20_000_000, lotes.porCodigo(lote).orElseThrow().enviadas());
    }

    @Test
    @Order(3)
    void empaqueRecibeElEnvio() throws SQLException {
        entrar("usr_empaque", "EMPAQUE");
        esperar("el envío en empaque", () -> {
            try { return envios.porCodigo(envio).isPresent(); } catch (SQLException e) { return false; }
        });
        var noCrea = assertThrows(SQLException.class, () -> envios.registrar(new DatosEnvio(idLote(), 1, t0, 1000, BigDecimal.ONE)));
        assertEquals("42501", noCrea.getSQLState());

        envios.recibir(envio, t0.plusMinutes(1));
        var recibido = envios.detalle(envio).orElseThrow();
        assertEquals("RECIBIDO", recibido.estado());
        assertEquals(t0.plusMinutes(1), recibido.recibido());

        lotes.guardarCalidad(idLote(), lote, "EMPAQUE", new DatosCalidad(new BigDecimal("84"), new BigDecimal("76"), new BigDecimal("99.8"),
                new BigDecimal("7.80"), "Al recibir"));
        assertEquals(2, lotes.calidades(idLote()).size());
        var planta = assertThrows(SQLException.class, () -> lotes.guardarCalidad(idLote(), lote, "PLANTA",
                new DatosCalidad(BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE, null)));
        assertEquals("Solo la planta registra la calidad de la etapa PLANTA.", Conexion.mensaje(planta));
    }

    @Test
    @Order(4)
    void encargadaDispersaModificaYBorra() throws SQLException {
        entrar("usr_dispersion", "EMPAQUE");
        var existencia = dispersiones.existencias().stream().filter(e -> e.codigo().equals(envio)).findFirst().orElseThrow();
        assertEquals(20_000_000, existencia.quedan());
        var zonasPrioridad = dispersiones.prioridad(14, LocalDate.now());
        assertFalse(zonasPrioridad.isEmpty());
        var zona = zonasPrioridad.get(0);
        var informe = new InformeDispersion(0, existencia.id(), null, "EMPAQUE", zona.id(), t0.plusMinutes(2), 5_000_000,
                new BigDecimal("100"), 2300, "XB-TST", "Cap. Prueba", null, zona.clave(), zona.nombre(), envio, null);

        var demasiadas = assertThrows(SQLException.class, () -> dispersiones.registrar(conCantidad(informe, 0, 20_000_001)));
        assertTrue(Conexion.mensaje(demasiadas).startsWith("Se dispersarían más moscas de las que hay"), Conexion.mensaje(demasiadas));

        dispersiones.registrar(informe);
        assertEquals(15_000_000, quedan(envio));
        var guardado = dispersiones.buscar("XB-TST", "EMPAQUE", 1).filas().get(0);
        assertEquals(envio, guardado.envioCodigo());
        assertEquals(5_000_000, dispersiones.porId(guardado.id()).orElseThrow().cantidad());

        dispersiones.modificar(conCantidad(informe, guardado.id(), 6_000_000));
        assertEquals(14_000_000, quedan(envio));
        assertEquals(6_000_000, envios.porCodigo(envio).orElseThrow().liberadas());
        assertTrue(dispersiones.existenciasPara(dispersiones.porId(guardado.id()).orElseThrow()).stream().anyMatch(e -> e.codigo().equals(envio)));

        dispersiones.borrar(guardado.id(), "prueba automática");
        assertEquals(20_000_000, quedan(envio));
        assertTrue(dispersiones.resumen().enEmpaque() >= 20_000_000);

        var sinPermiso = assertThrows(SQLException.class, () -> lotes.registrar(new DatosLote(LocalDate.now(), LocalDate.now(),
                new BigDecimal("120"), 1, "VIENNA-8 TSL", "Rosa fluorescente", 1, null)));
        assertEquals("42501", sinPermiso.getSQLState());
    }

    @Test
    @Order(5)
    void aeropuertoSoloConsultaLaDispersion() throws SQLException {
        entrar("usr_aeropuerto", "EMPAQUE");
        var existencia = dispersiones.existencias().stream().filter(e -> e.codigo().equals(envio)).findFirst().orElseThrow();
        var zona = dispersiones.prioridad(14, LocalDate.now()).get(0);
        var noPuede = assertThrows(SQLException.class, () -> dispersiones.registrar(new InformeDispersion(0, existencia.id(), null, "EMPAQUE",
                zona.id(), t0.plusMinutes(2), 1, BigDecimal.ONE, 1000, "XB-TST", "Cap. Prueba", null, zona.clave(), zona.nombre(), envio, null)));
        assertEquals("42501", noPuede.getSQLState());
    }

    @Test
    @Order(6)
    void administradorCreaYDaDeBaja() throws SQLException {
        assertTrue(acceso.entrarAdmin("usr_admin", CLAVE).ok());
        int pemAntes = (int) usuarios.resumen().pem();
        var nuevo = new NuevoUsuario("PEM", "Prueba", "Automática", null, null, "prueba@moscamed.ejemplo.mx", USUARIO_PRUEBA, "Prueba#2026x",
                Map.of("dispersiones", new boolean[]{true, false}));
        usuarios.crearRol(nuevo);
        String num = usuarios.registrar(nuevo);
        assertTrue(num.startsWith("TAP-"), num);
        assertEquals(pemAntes + 1, usuarios.resumen().pem());
        var persona = usuarios.buscar(num, "PEM", true, 1).filas().get(0);
        assertEquals(USUARIO_PRUEBA, persona.usuarioBd());
        assertEquals("Administrador Empacadora SENASICA", persona.puesto());
        AccesoControlador.salir();

        esperar("la persona nueva en empaque", () -> {
            try (Connection c = superusuario("EMPAQUE")) {
                return !Conexion.q(c, "SELECT 1 FROM permiso_vista p JOIN responsable r ON r.id = p.responsable_id WHERE r.usuario_bd = ?",
                        USUARIO_PRUEBA).isEmpty();
            } catch (SQLException e) {
                return false;
            }
        });
        var intento = acceso.entrarOperativo(USUARIO_PRUEBA, "Prueba#2026x");
        assertTrue(intento.ok(), intento.mensaje());
        assertEquals("EMPAQUE", Sesion.actual().sede());
        assertTrue(Sesion.actual().puede("dispersiones", false));
        assertFalse(Sesion.actual().puede("dispersiones", true), "el administrador le quitó modificar");
        AccesoControlador.salir();

        assertTrue(acceso.entrarAdmin("usr_admin", CLAVE).ok());
        usuarios.restablecerClave(USUARIO_PRUEBA, "Otra#clave2026");
        usuarios.cambiarAcceso(persona.id(), USUARIO_PRUEBA, false);
        AccesoControlador.salir();
        assertEquals("Tu usuario está dado de baja.", acceso.entrarOperativo(USUARIO_PRUEBA, "Otra#clave2026").mensaje());
    }

    @Test
    @Order(7)
    void estadoDelSistemaVeLasDosSedes() throws SQLException {
        esperar("las dos sedes iguales", FlujoBdTest::igualesEnLasDosSedes);
        assertTrue(acceso.entrarAdmin("usr_admin", CLAVE).ok());
        var sedes = sistema.estadoSedes();
        assertEquals(2, sedes.size());
        for (var s : sedes) {
            assertTrue(s.ok(), s.sede() + ": " + s.error());
            assertNotNull(s.trampas(), s.sede() + " lee el trampeo por FDW");
            assertFalse(s.suscripciones().isEmpty(), s.sede() + " recibe datos de la otra sede");
            assertTrue(s.suscripciones().stream().allMatch(sub -> sub.activa()), s.sede() + ": suscripción detenida");
        }
        assertEquals(sedes.get(0).conteos(), sedes.get(1).conteos(), "las dos sedes tienen los mismos datos");
        assertTrue(sistema.bitacora(50).stream().anyMatch(b -> b.accion().equals("Registró el lote " + lote)));
        assertTrue(sistema.bitacora(50).stream().anyMatch(b -> b.accion().equals("Recibió en Tapachula el envío " + envio)));
    }

    static InformeDispersion conCantidad(InformeDispersion i, long id, long cantidad) {
        return new InformeDispersion(id, i.envioId(), i.embarqueId(), i.punto(), i.zonaId(), i.fecha(), cantidad, i.superficieHa(),
                i.altitudM(), i.avioneta(), i.piloto(), i.observaciones(), i.zonaClave(), i.zonaNombre(), i.envioCodigo(), i.embarqueCodigo());
    }

    static long quedan(String codigo) throws SQLException {
        return dispersiones.existencias().stream().filter(e -> e.codigo().equals(codigo)).mapToLong(e -> e.quedan()).sum();
    }
}
