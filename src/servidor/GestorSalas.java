package servidor;

import java.util.ArrayList;
import java.util.List;

public class GestorSalas {
    private static GestorSalas instancia;
    private List<Sala> salas;

    private GestorSalas() {
        this.salas = new ArrayList<>();
    }

    public static synchronized GestorSalas getInstancia() {
        if (instancia == null) {
            instancia = new GestorSalas();
        }
        return instancia;
    }
    public Sala crearSala(HiloCliente creador, int capacidad, boolean privada) {
        Sala nueva = new Sala(creador, capacidad, privada);
        salas.add(nueva);
        return nueva;
    }
}
