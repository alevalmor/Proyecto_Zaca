package moscamed.controlador;

import java.sql.SQLException;
import java.time.LocalDate;
import javafx.scene.Node;
import moscamed.interfaces.controlador.Navegador;
import moscamed.interfaces.datos.DispersionDao;
import moscamed.interfaces.datos.EnvioDao;
import moscamed.interfaces.datos.LoteDao;
import moscamed.modelo.Zona;
import moscamed.vista.operativo.InicioEmpaqueVista;
import moscamed.vista.operativo.InicioPlantaVista;

public final class InicioControlador {
    private final LoteDao lotes;
    private final EnvioDao envios;
    private final DispersionDao zonas;
    private final Navegador nav;

    public InicioControlador(LoteDao lotes, EnvioDao envios, DispersionDao zonas, Navegador nav) {
        this.lotes = lotes;
        this.envios = envios;
        this.zonas = zonas;
        this.nav = nav;
    }

    public Node planta() throws SQLException {
        return new InicioPlantaVista(nav, lotes.resumenPlanta(), lotes.recientes(6), lotes.pendientesDeCalidad(3)).vista();
    }

    public Node empaque() throws SQLException {
        var resumen = envios.resumenEmpaque();
        var enCamino = envios.enCamino(5);
        var altas = zonas.prioridad(14, LocalDate.now()).stream().filter(Zona::alta).toList();
        return new InicioEmpaqueVista(nav, resumen, enCamino, altas).vista();
    }
}
