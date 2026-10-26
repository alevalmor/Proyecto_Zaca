package moscamed.modelo;

import java.time.LocalDateTime;

public record Existencia(String origen, int id, String codigo, String referencia, String punto, LocalDateTime desde,
                         long recibidas, long quedan) {
    public boolean deEnvio() {
        return "ENVIO".equals(origen);
    }

    public String clave() {
        return origen + "|" + codigo + "|" + punto;
    }

    public static String clave(String origen, String codigo, String punto) {
        return origen + "|" + codigo + "|" + punto;
    }

    public static String nombrePunto(String punto) {
        return "AEROPUERTO".equals(punto) ? "Aeropuerto" : "Centro de empaque";
    }

    public boolean esDe(InformeDispersion informe) {
        return deEnvio() ? informe.envioId() != null && id == informe.envioId()
                : informe.embarqueId() != null && id == informe.embarqueId() && punto.equals(informe.punto());
    }

    public Existencia conQuedan(long nuevas) {
        return new Existencia(origen, id, codigo, referencia, punto, desde, recibidas, nuevas);
    }
}
