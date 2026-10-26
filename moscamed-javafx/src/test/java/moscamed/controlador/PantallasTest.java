package moscamed.controlador;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import moscamed.datos.Conexion;
import moscamed.modelo.Sesion;
import moscamed.vista.acceso.SelectorVista;

@EnabledIfEnvironmentVariable(named = "MOSCAMED_BD", matches = "si")
class PantallasTest {
    static final String SIN_PERMISO = "Tu perfil no tiene permiso para esa acción.";

    record Vista(List<String> titulos, List<String> avisos, List<String> errores) {}

    @BeforeAll
    static void iniciarJavaFx() throws InterruptedException {
        CountDownLatch listo = new CountDownLatch(1);
        try {
            Platform.startup(listo::countDown);
        } catch (IllegalStateException yaIniciado) {
            listo.countDown();
        }
        listo.await();
        Platform.setImplicitExit(false);
    }

    @AfterEach
    void salir() {
        AccesoControlador.salir();
    }

    static <T> T enFx(Callable<T> tarea) throws Exception {
        FutureTask<T> f = new FutureTask<>(tarea);
        Platform.runLater(f);
        return f.get(60, TimeUnit.SECONDS);
    }

    static Vista leer(Scene scene) {
        Parent raiz = scene.getRoot();
        raiz.applyCss();
        raiz.layout();
        List<Node> nodos = new ArrayList<>();
        List<String> titulos = new ArrayList<>(), avisos = new ArrayList<>(), errores = new ArrayList<>();
        recorrer(raiz, Collections.newSetFromMap(new IdentityHashMap<>()), nodos);
        for (Node n : nodos) {
            if (!(n instanceof Label l) || !l.isVisible()) continue;
            if (l.getStyleClass().contains("h1")) titulos.add(l.getText());
            if (l.getStyleClass().contains("aviso")) avisos.add(l.getText());
            if (l.getStyleClass().contains("aviso-error")) errores.add(l.getText());
        }
        return new Vista(titulos, avisos, errores);
    }

    static void recorrer(Node n, Set<Node> vistos, List<Node> nodos) {
        if (!vistos.add(n)) return;
        nodos.add(n);
        if (n instanceof ScrollPane sp && sp.getContent() != null) recorrer(sp.getContent(), vistos, nodos);
        if (n instanceof Parent p) for (Node h : p.getChildrenUnmodifiable()) recorrer(h, vistos, nodos);
    }

    static Programa1 entrar(String usuario) throws Exception {
        return enFx(() -> {
            Programa1 p = new Programa1();
            Stage stage = new Stage();
            p.preparar(stage);
            stage.setScene(p.scene);
            stage.show();
            assertNull(p.entrar(usuario, "moscamed"), usuario);
            return p;
        });
    }

    static Vista abrir(Programa1 p, String pantalla, String arg, boolean permitida) throws Exception {
        Vista v = enFx(() -> {
            p.ir(pantalla, arg);
            assertSame(p.marco, p.scene.getRoot(), pantalla + " se abre dentro del marco con menú");
            return leer(p.scene);
        });
        String quien = Sesion.actual().perfil() + " · " + pantalla + (arg == null ? "" : " " + arg);
        assertEquals(List.of(), v.errores(), quien + ": la pantalla mostró un error");
        assertEquals(!permitida, v.avisos().contains(SIN_PERMISO), quien + (permitida ? " debe abrirse" : " no debe abrirse"));
        assertFalse(v.titulos().isEmpty(), quien + " sin título");
        System.out.println("  ✓ " + quien + " → " + v.titulos().get(0));
        return v;
    }

    static String uno(String sql) throws Exception {
        return String.valueOf(Conexion.q1(sql).values().iterator().next());
    }

    @Test
    void planta() throws Exception {
        Programa1 p = entrar("usr_planta");
        String producido = uno("SELECT codigo FROM v_lote WHERE estado = 'PRODUCIDO' ORDER BY codigo LIMIT 1");
        String enCamino = uno("SELECT codigo FROM v_envio WHERE estado = 'EN CAMINO' ORDER BY codigo LIMIT 1");
        assertEquals("Buen día, Diana", abrir(p, "inicio", null, true).titulos().get(0));
        for (String pantalla : List.of("lotes", "nuevo_lote", "estado_lote", "envios", "nuevo_envio", "estado_envio", "dispersiones", "perfil")) {
            abrir(p, pantalla, null, true);
        }
        assertEquals(producido, abrir(p, "estado_lote", producido, true).titulos().get(0));
        assertEquals("Modificar lote " + producido, abrir(p, "modificar_lote", producido, true).titulos().get(0));
        assertEquals(enCamino, abrir(p, "estado_envio", enCamino, true).titulos().get(0));
        assertEquals("Modificar envío " + enCamino, abrir(p, "modificar_envio", enCamino, true).titulos().get(0));
        abrir(p, "nueva_dispersion", null, false);

        Vista login = enFx(() -> {
            p.salir(null);
            assertNull(Sesion.actual());
            return leer(p.scene);
        });
        assertEquals(List.of("Iniciar sesión"), login.titulos());
    }

    @Test
    void empaque() throws Exception {
        Programa1 p = entrar("usr_empaque");
        String enCamino = uno("SELECT codigo FROM v_envio WHERE estado = 'EN CAMINO' ORDER BY codigo LIMIT 1");
        String existencia = uno("SELECT origen || '|' || codigo || '|' || punto FROM v_existencia WHERE quedan > 0 ORDER BY desde LIMIT 1");
        String informe = uno("SELECT min(id) FROM liberacion");
        for (String pantalla : List.of("inicio", "lotes", "estado_lote", "envios", "estado_envio", "dispersiones", "nueva_dispersion", "perfil")) {
            abrir(p, pantalla, null, true);
        }
        abrir(p, "estado_envio", enCamino, true);
        abrir(p, "nueva_dispersion", existencia, true);
        assertEquals("Modificar informe de dispersión aérea", abrir(p, "modificar_dispersion", informe, true).titulos().get(0));
        abrir(p, "nuevo_lote", null, false);
        abrir(p, "nuevo_envio", null, false);
        abrir(p, "modificar_envio", enCamino, false);
    }

    @Test
    void aeropuerto() throws Exception {
        Programa1 p = entrar("usr_aeropuerto");
        assertEquals("Informes de dispersión", abrir(p, "inicio", null, true).titulos().get(0));
        for (String pantalla : List.of("lotes", "estado_lote", "envios", "estado_envio", "dispersiones", "perfil")) abrir(p, pantalla, null, true);
        abrir(p, "nueva_dispersion", null, false);
        abrir(p, "modificar_dispersion", uno("SELECT min(id) FROM liberacion"), false);
    }

    @Test
    void dispersion() throws Exception {
        Programa1 p = entrar("usr_dispersion");
        for (String pantalla : List.of("inicio", "lotes", "estado_lote", "envios", "estado_envio", "dispersiones", "nueva_dispersion", "perfil")) {
            abrir(p, pantalla, null, true);
        }
        abrir(p, "modificar_dispersion", uno("SELECT max(id) FROM liberacion"), true);
        abrir(p, "nuevo_lote", null, false);
    }

    @Test
    void administrador() throws Exception {
        Programa2 p = enFx(() -> {
            Programa2 a = new Programa2();
            Stage stage = new Stage();
            a.preparar(stage);
            stage.setScene(a.scene);
            stage.show();
            assertNull(a.entrar("usr_admin", "moscamed"));
            return a;
        });
        for (String[] pantalla : new String[][]{{"usuarios", null}, {"nuevo", "PES"}, {"nuevo", "PEM"}, {"sistema", null}}) {
            Vista v = enFx(() -> {
                p.ir(pantalla[0], pantalla[1]);
                return leer(p.scene);
            });
            assertEquals(List.of(), v.errores(), "admin · " + pantalla[0]);
            System.out.println("  ✓ admin · " + pantalla[0] + (pantalla[1] == null ? "" : " " + pantalla[1]) + " → " + v.titulos().get(0));
        }
        assertEquals("Este usuario es de administrador: no puede entrar como operativo.",
                enFx(() -> new Programa1().entrar("usr_admin", "moscamed")));
    }

    @Test
    void inicioDeSesionUnico() throws Exception {
        Vista v = enFx(() -> {
            Stage ventanita = new Stage();
            Scene scene = new Scene(new SelectorVista(ventanita, (admin, u, c) -> null).raiz("Tu sesión terminó por inactividad."));
            return leer(scene);
        });
        assertEquals(List.of("Iniciar sesión"), v.titulos());
        assertTrue(v.avisos().contains("Tu sesión terminó por inactividad."));
    }
}
