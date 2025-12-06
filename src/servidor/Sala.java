package servidor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Sala implements Serializable {
    private static final long serialVersionUID = 1L;
    private static int contadorIds = 1;

    private int id;
    private int capacidadMaxima;
    private boolean esPrivada;
    private boolean enJuego;
    private String nombreAdmin;

   
    private transient List<HiloCliente> jugadores; 

    
    public Sala(HiloCliente creador, int capacidad, boolean privada) {
        this.id = contadorIds++;
        this.capacidadMaxima = capacidad;
        this.esPrivada = privada;
        this.nombreAdmin = creador.getNombreJugador();
        this.jugadores = new ArrayList<>();
        this.jugadores.add(creador); 
        this.enJuego = false;
    }

   
    public boolean agregarJugador(HiloCliente jugador) {
        if (jugadores.size() < capacidadMaxima && !enJuego) {
            jugadores.add(jugador);
            return true;
        }
        return false;
    }

    
}