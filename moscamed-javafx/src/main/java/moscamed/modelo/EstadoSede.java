package moscamed.modelo;

import java.util.List;
import java.util.Map;

public record EstadoSede(String sede, String host, boolean ok, String error, List<String> publicaciones,
                         List<Suscripcion> suscripciones, List<Replica> envia, Map<String, Long> conteos,
                         List<NodoPgpool> pgpool, Long trampas) {
    public record Suscripcion(String nombre, boolean activa) {}

    public record Replica(String nombre, long retraso) {}

    public record NodoPgpool(String id, String host, String puerto, String rol, String estado) {
        public boolean arriba() {
            return estado.equals("up") || estado.equals("waiting");
        }
    }

    public static EstadoSede caida(String sede, String host, String error) {
        return new EstadoSede(sede, host, false, error, List.of(), List.of(), List.of(), Map.of(), null, null);
    }

    public boolean esPlanta() {
        return sede.equals("PLANTA");
    }
}
