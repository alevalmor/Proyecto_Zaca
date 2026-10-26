package moscamed.interfaces.controlador;

@FunctionalInterface
public interface Autenticador {
    String entrar(String usuario, String clave);
}
