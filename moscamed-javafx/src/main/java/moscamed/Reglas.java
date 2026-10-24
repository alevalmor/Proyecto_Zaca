package moscamed;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Reglas de negocio sin base de datos (se prueban en ReglasTest). */
public final class Reglas {
    private Reglas() {}

    /** Vistas del Programa 1 con permisos: clave y nombre. */
    public static final List<String[]> VISTAS = List.of(
            new String[]{"nuevo_lote", "Nuevo lote"},
            new String[]{"lotes", "Lista de lotes"},
            new String[]{"estado_lote", "Estado de lote / calidad"},
            new String[]{"nuevo_envio", "Nuevo envío"},
            new String[]{"envios", "Lista de envíos"},
            new String[]{"estado_envio", "Estado de envío / llegada"},
            new String[]{"nuevo_embarque", "Nuevo embarque aéreo"},
            new String[]{"embarques", "Embarques aéreos de Perú"},
            new String[]{"nueva_dispersion", "Nuevo informe de dispersión"},
            new String[]{"dispersiones", "Informes de dispersión"},
            new String[]{"zonas", "Zonas prioritarias"});

    /** Lo máximo que permite cada perfil: {ver, modificar}. La base de datos lo hace cumplir con sus roles. */
    public static final Map<String, Map<String, boolean[]>> PERMISOS_PERFIL = Map.of(
            "PES", mapa(new Object[][]{
                    {"nuevo_lote", true, true}, {"lotes", true, true}, {"estado_lote", true, true},
                    {"nuevo_envio", false, false}, {"envios", true, false}, {"estado_envio", true, false},
                    {"nuevo_embarque", false, false}, {"embarques", true, false},
                    {"nueva_dispersion", false, false}, {"dispersiones", true, false}, {"zonas", true, false}}),
            "PEM", mapa(new Object[][]{
                    {"nuevo_lote", false, false}, {"lotes", true, false}, {"estado_lote", true, true},
                    {"nuevo_envio", true, true}, {"envios", true, true}, {"estado_envio", true, true},
                    {"nuevo_embarque", false, false}, {"embarques", true, false},
                    {"nueva_dispersion", false, false}, {"dispersiones", true, false}, {"zonas", true, false}}),
            "PEA", mapa(new Object[][]{
                    {"nuevo_lote", false, false}, {"lotes", true, false}, {"estado_lote", true, false},
                    {"nuevo_envio", false, false}, {"envios", true, false}, {"estado_envio", true, false},
                    {"nuevo_embarque", true, true}, {"embarques", true, true},
                    {"nueva_dispersion", false, false}, {"dispersiones", true, false}, {"zonas", true, false}}),
            "PED", mapa(new Object[][]{
                    {"nuevo_lote", false, false}, {"lotes", true, false}, {"estado_lote", true, false},
                    {"nuevo_envio", false, false}, {"envios", true, false}, {"estado_envio", true, false},
                    {"nuevo_embarque", false, false}, {"embarques", true, false},
                    {"nueva_dispersion", true, true}, {"dispersiones", true, true}, {"zonas", true, false}}));

    /** Perfil: {nombre, lugar de trabajo, sede (servidor al que entra), rol de PostgreSQL}. */
    public static final Map<String, String[]> PERFILES = new LinkedHashMap<>();
    static {
        PERFILES.put("PES", new String[]{"Esterilizador de la planta", "Planta esterilizadora · Metapa", "PLANTA", "rol_produccion"});
        PERFILES.put("PEM", new String[]{"Empacador del centro", "Centro de empaque · Tapachula", "EMPAQUE", "rol_empaque"});
        PERFILES.put("PEA", new String[]{"Recepción de embarques de Perú", "Aeropuerto de Tapachula", "EMPAQUE", "rol_aeropuerto"});
        PERFILES.put("PED", new String[]{"Encargada de dispersión aérea", "Dispersión aérea · Tapachula", "EMPAQUE", "rol_dispersion"});
    }

    public static String sede(String perfil) {
        return PERFILES.get(perfil)[2];
    }

    private static Map<String, boolean[]> mapa(Object[][] filas) {
        Map<String, boolean[]> m = new LinkedHashMap<>();
        for (Object[] f : filas) m.put((String) f[0], new boolean[]{(Boolean) f[1], (Boolean) f[2]});
        return m;
    }

    /** quitados: {vista: {ver, modificar}} guardado por el administrador; solo puede restar. */
    public static Map<String, boolean[]> permisos(String perfil, Map<String, boolean[]> quitados) {
        Map<String, boolean[]> res = new LinkedHashMap<>();
        PERMISOS_PERFIL.get(perfil).forEach((vista, base) -> {
            boolean[] q = quitados.getOrDefault(vista, new boolean[]{true, true});
            boolean ver = base[0] && q[0];
            res.put(vista, new boolean[]{ver, base[1] && q[1] && ver});
        });
        return res;
    }

    /** Misma fórmula que la columna generada control_calidad.aprobado. */
    public static boolean calidadAprobada(double emergencia, double vuelo, double esterilidad) {
        return emergencia >= 75 && vuelo >= 65 && esterilidad >= 99.5;
    }

    /** quedaAeropuerto: lo que se queda en el aeropuerto; el resto va al centro de empaque. */
    public static List<String> erroresEmbarque(long cantidad, long quedaAeropuerto) {
        var errores = new java.util.ArrayList<String>();
        if (cantidad <= 0) errores.add("La cantidad del embarque debe ser mayor que cero");
        if (quedaAeropuerto < 0 || quedaAeropuerto > cantidad) errores.add("Lo que se queda en el aeropuerto va de 0 a lo que trae el embarque");
        return errores;
    }

    /** La dispersión es solo aérea: siempre lleva altitud de vuelo. */
    public static List<String> erroresDispersion(long cantidad, long quedan, long altitud) {
        var errores = new java.util.ArrayList<String>();
        if (cantidad <= 0) errores.add("La cantidad dispersada debe ser mayor que cero");
        if (cantidad > quedan) errores.add("Supera las moscas que quedan en esa existencia");
        if (altitud <= 0) errores.add("La altitud de vuelo es obligatoria");
        return errores;
    }

    public static List<String> erroresEnvio(long cantidad, long capacidad, long disponibles) {
        var errores = new java.util.ArrayList<String>();
        if (cantidad <= 0) errores.add("La cantidad debe ser mayor que cero");
        if (cantidad > capacidad) errores.add("No cabe en el vehículo");
        if (cantidad > disponibles) errores.add("Supera lo que queda del lote");
        return errores;
    }

    public static boolean contrasenaValida(String clave) {
        return clave.length() >= 10 && clave.matches(".*\\d.*") && Pattern.compile("[^\\w\\s]").matcher(clave).find();
    }

    /** Se usa como identificador en CREATE ROLE: solo letras, números, punto y guion bajo. */
    public static boolean usuarioBdValido(String usuario) {
        return usuario.matches("[a-z][a-z0-9_.]{2,30}");
    }

    /** "52,000,000" -> 52000000; acepta lo que escribe la gente en los formularios. */
    public static long entero(String texto) {
        String limpio = texto == null ? "" : texto.replace(",", "").replace(" ", "").trim();
        return limpio.isEmpty() ? 0 : Long.parseLong(limpio);
    }
}
