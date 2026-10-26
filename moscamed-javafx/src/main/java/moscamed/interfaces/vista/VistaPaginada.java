package moscamed.interfaces.vista;

import moscamed.modelo.Pagina;

public interface VistaPaginada<T> extends Vista {
    void mostrar(Pagina<T> pagina);

    void mostrarError(String mensaje);
}
