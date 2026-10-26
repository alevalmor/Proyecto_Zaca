package moscamed.vista;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class Formato {
    private Formato() {}

    public static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public static String miles(Object n) {
        return String.format(Locale.US, "%,d", n == null ? 0L : ((Number) n).longValue());
    }

    public static String millones(Object n) {
        double v = n == null ? 0 : ((Number) n).doubleValue();
        return String.format(Locale.US, "%,.1f M", v / 1e6);
    }

    public static String fecha(Object d) {
        if (d instanceof LocalDate f) return f.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        if (d instanceof LocalDateTime f) return f.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        return "—";
    }

    public static String fechaHora(Object d) {
        return d instanceof LocalDateTime f ? f.format(FECHA_HORA) : "—";
    }

    public static String dec(Object n, int decimales) {
        return n == null ? "—" : String.format(Locale.US, "%." + decimales + "f", ((Number) n).doubleValue());
    }

    public static String s(Object o) {
        return o == null ? "" : o.toString();
    }

    public static String oGuion(Object o) {
        return o == null ? "—" : o.toString();
    }
}
