package moscamed.datos.postgres;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import moscamed.modelo.Envio;
import moscamed.modelo.Existencia;
import moscamed.modelo.InformeDispersion;
import moscamed.modelo.Lote;
import moscamed.modelo.Responsable;
import moscamed.modelo.Zona;

final class Filas {
    private Filas() {}

    static String s(Map<String, Object> r, String col) {
        Object o = r.get(col);
        return o == null ? null : o.toString();
    }

    static long l(Map<String, Object> r, String col) {
        Object o = r.get(col);
        return o == null ? 0 : ((Number) o).longValue();
    }

    static int i(Map<String, Object> r, String col) {
        return (int) l(r, col);
    }

    static Integer entero(Map<String, Object> r, String col) {
        Object o = r.get(col);
        return o == null ? null : ((Number) o).intValue();
    }

    static boolean si(Map<String, Object> r, String col) {
        return Boolean.TRUE.equals(r.get(col));
    }

    static BigDecimal dec(Map<String, Object> r, String col) {
        Object o = r.get(col);
        return o == null ? null : o instanceof BigDecimal b ? b : new BigDecimal(o.toString());
    }

    static LocalDate fecha(Map<String, Object> r, String col) {
        return (LocalDate) r.get(col);
    }

    static LocalDateTime fechaHora(Map<String, Object> r, String col) {
        return (LocalDateTime) r.get(col);
    }

    private static String registro(Map<String, Object> r) {
        return r.get("r_nombre") == null ? null : Responsable.firma(s(r, "num_empleado"), s(r, "r_nombre"), s(r, "ap_paterno"));
    }

    static Lote lote(Map<String, Object> r) {
        return new Lote(i(r, "id"), s(r, "codigo"), fecha(r, "fecha_produccion"), fecha(r, "fecha_irradiacion"), dec(r, "dosis_gy"),
                l(r, "cantidad_pupas"), s(r, "cepa"), s(r, "tinte"), i(r, "centro_empaque_id"), s(r, "observaciones"),
                s(r, "estado"), (Boolean) r.get("calidad_planta"), l(r, "enviadas"), l(r, "liberadas"),
                s(r, "centro_clave"), s(r, "centro_nombre"), registro(r));
    }

    static Envio envio(Map<String, Object> r) {
        return new Envio(i(r, "id"), s(r, "codigo"), i(r, "lote_id"), i(r, "vehiculo_id"), fechaHora(r, "fecha_salida"),
                l(r, "cantidad"), dec(r, "temperatura"), fechaHora(r, "recibido"), si(r, "cancelado"), l(r, "liberadas"),
                s(r, "estado"), s(r, "lote"), s(r, "matricula"), s(r, "modelo"), registro(r), s(r, "zonas"));
    }

    static InformeDispersion informe(Map<String, Object> r) {
        return new InformeDispersion(l(r, "id"), entero(r, "envio_id"), entero(r, "embarque_id"), s(r, "punto"), i(r, "zona_id"),
                fechaHora(r, "fecha"), l(r, "cantidad"), dec(r, "superficie_ha"), i(r, "altitud_m"), s(r, "avioneta"),
                s(r, "piloto"), s(r, "observaciones"), s(r, "clave"), s(r, "nombre"), s(r, "envio"), s(r, "embarque"));
    }

    static Existencia existencia(Map<String, Object> r) {
        return new Existencia(s(r, "origen"), i(r, "id"), s(r, "codigo"), s(r, "referencia"), s(r, "punto"),
                fechaHora(r, "desde"), l(r, "recibidas"), l(r, "quedan"));
    }

    static Zona zona(Map<String, Object> r) {
        return new Zona(i(r, "id"), s(r, "clave"), s(r, "nombre"), s(r, "prioridad"), dec(r, "mtd"), l(r, "silvestres"), l(r, "sugeridas"));
    }

    static Responsable responsable(Map<String, Object> r) {
        return new Responsable(i(r, "id"), s(r, "num_empleado"), s(r, "nombre"), s(r, "ap_paterno"), s(r, "ap_materno"),
                s(r, "puesto"), s(r, "telefono"), s(r, "correo"), s(r, "sede"), s(r, "perfil"), s(r, "usuario_bd"),
                si(r, "activo"), fecha(r, "fecha_alta"));
    }
}
