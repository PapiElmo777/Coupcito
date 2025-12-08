package servidor;

import comun.Constantes;
import comun.Mensaje;

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

    public void removerJugador(HiloCliente jugador) {
        if (jugadores != null) {
            jugadores.remove(jugador);
        }
    }

    public void broadcastSala(Mensaje msj) {
        if (jugadores != null) {
            for (HiloCliente j : jugadores) {
                j.enviarMensaje(msj);
            }
        }
    }
    public boolean iniciarPartida(HiloCliente solicitante) {
        if (!solicitante.getNombreJugador().equals(nombreAdmin)) {
            solicitante.enviarMensaje(new Mensaje(Constantes.ESTADO, "Solo el admin puede iniciar."));
            return false;
        }
        if (jugadores.size() < 2) {
            solicitante.enviarMensaje(new Mensaje(Constantes.ESTADO, "Mínimo 2 jugadores."));
            return false;
        }
        this.enJuego = true;
        return true;
    }

   
    public int getId() { return id; }
    public boolean isEsPrivada() { return esPrivada; }
    public boolean isEnJuego() { return enJuego; }
    public List<HiloCliente> getJugadores() { return jugadores; }

    @Override
    public String toString() {
        return "Sala #" + id + " de " + nombreAdmin + " [" + (jugadores != null ? jugadores.size() : 0) + "/" + capacidadMaxima + "]";
    }
}