package moscamed.vista.operativo;

import static moscamed.vista.Componentes.*;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import moscamed.interfaces.controlador.Navegador;
import moscamed.interfaces.vista.Vista;
import moscamed.modelo.Sesion;

public abstract class InicioVista implements Vista {
    protected final Navegador nav;
    protected final Sesion sesion = Sesion.actual();

    protected InicioVista(Navegador nav) {
        this.nav = nav;
    }

    protected Node saludo(String sede, String sincronizado) {
        Label chip = lbl("●  " + sincronizado, "chip");
        Label perfil = lbl("Perfil " + sesion.perfil(), "chip", "chip-blanco");
        return encabezado(titulo(sede, "Buen día, " + sesion.nombreCorto()), new HBox(8, chip, perfil));
    }
}
