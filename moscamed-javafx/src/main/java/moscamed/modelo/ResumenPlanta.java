package moscamed.modelo;

public record ResumenPlanta(long semana, long pupas, long sinCalidad, long rechazados,
                            String rechazoCodigo, boolean rechazoVuelo, boolean rechazoEmergencia) {
    public String motivoRechazo() {
        if (rechazoCodigo == null) return "ninguno";
        return rechazoCodigo + " · " + (rechazoVuelo ? "vuelo bajo" : rechazoEmergencia ? "emergencia baja" : "esterilidad baja");
    }
}
