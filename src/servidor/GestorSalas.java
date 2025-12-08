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
    public Sala buscarSala(int id) {
        for (Sala s : salas) {
            if (s.getId() == id) {
                return s;
            }
        }
        return null;
    }
    public List<Sala> getSalas() {
        return new ArrayList<>(salas);
    }
    public void eliminarSala(Sala s) {
        salas.remove(s);
    }
}
