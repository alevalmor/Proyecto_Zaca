package moscamed;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReglasTest {

    @Test
    void permisosSoloRestan() {
        var p = Reglas.permisos("PEM", Map.of("envios", new boolean[]{true, false}, "nuevo_lote", new boolean[]{true, true}));
        assertArrayEquals(new boolean[]{true, false}, p.get("envios"));
        assertArrayEquals(new boolean[]{false, false}, p.get("nuevo_lote")); // el admin no da más de lo que permite el perfil
        var sinVer = Reglas.permisos("PES", Map.of("lotes", new boolean[]{false, true}));
        assertArrayEquals(new boolean[]{false, false}, sinVer.get("lotes")); // sin ver no se modifica
    }

    @Test
    void calidad() {  // la prioridad de zonas vive en la base: la prueba infra/probar.sh
        assertTrue(Reglas.calidadAprobada(75, 65, 99.5));
        assertFalse(Reglas.calidadAprobada(86.4, 64.9, 99.9));
    }

    @Test
    void envio() {
        assertEquals(List.of(), Reglas.erroresEnvio(24_800_000, 32_000_000, 49_107_000));
        assertEquals(2, Reglas.erroresEnvio(40_000_000, 32_000_000, 30_000_000).size());
        assertEquals(List.of("La cantidad debe ser mayor que cero"), Reglas.erroresEnvio(0, 1, 1));
    }

    @Test
    void perfilesNuevos() {
        // cada perfil escribe solo lo suyo: aeropuerto los embarques, dispersión los informes de la avioneta
        assertArrayEquals(new boolean[]{true, true}, Reglas.permisos("PEA", Map.of()).get("nuevo_embarque"));
        assertArrayEquals(new boolean[]{true, false}, Reglas.permisos("PEA", Map.of()).get("dispersiones"));
        assertArrayEquals(new boolean[]{true, true}, Reglas.permisos("PED", Map.of()).get("nueva_dispersion"));
        assertArrayEquals(new boolean[]{false, false}, Reglas.permisos("PED", Map.of()).get("nuevo_envio"));
        assertArrayEquals(new boolean[]{false, false}, Reglas.permisos("PEM", Map.of()).get("nueva_dispersion"));
        for (String perfil : Reglas.PERFILES.keySet()) {
            assertEquals(Reglas.VISTAS.size(), Reglas.PERMISOS_PERFIL.get(perfil).size(), perfil + " define todas las vistas");
        }
        assertEquals("PLANTA", Reglas.sede("PES"));
        assertEquals("EMPAQUE", Reglas.sede("PED"));
    }

    @Test
    void embarqueYDispersion() {
        assertEquals(List.of(), Reglas.erroresEmbarque(40_000_000, 15_000_000));
        assertEquals(List.of(), Reglas.erroresEmbarque(40_000_000, 40_000_000)); // todo se queda en el aeropuerto
        assertEquals(1, Reglas.erroresEmbarque(40_000_000, 40_000_001).size());
        assertEquals(List.of(), Reglas.erroresDispersion(10, 10, 2300));
        assertEquals(List.of("Supera las moscas que quedan en esa existencia"), Reglas.erroresDispersion(11, 10, 2300));
        assertEquals(List.of("La altitud de vuelo es obligatoria"), Reglas.erroresDispersion(5, 10, 0));
    }

    @Test
    void claveTemporalEsValida() {
        for (int i = 0; i < 50; i++) assertTrue(Reglas.contrasenaValida(Programa2.claveTemporal()));
    }

    @Test
    void entradas() {
        assertEquals(52_000_000, Reglas.entero("52,000,000"));
        assertTrue(Reglas.contrasenaValida("Moscamed#7Q2k"));
        assertFalse(Reglas.contrasenaValida("corta1#"));
        assertFalse(Reglas.contrasenaValida("sinnumeros!!"));
        assertTrue(Reglas.usuarioBdValido("i.coutino"));
        assertFalse(Reglas.usuarioBdValido("x\"; DROP ROLE"));
        assertFalse(Reglas.usuarioBdValido("Mayus"));
    }
}
