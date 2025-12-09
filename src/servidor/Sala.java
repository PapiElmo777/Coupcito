package servidor;

import comun.Constantes;
import comun.Mensaje;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public class Sala implements Serializable {
    private static final long serialVersionUID = 1L;
    private static int contadorIds = 1;

    private int id;
    private int capacidadMaxima;
    private boolean esPrivada;
    private boolean enJuego;
    private String nombreAdmin;

   
    private transient List<HiloCliente> jugadores; 
    private transient List<String> mazo;
    private int turnoActual = 0;
    
    public Sala(HiloCliente creador, int capacidad, boolean privada) {
        this.id = contadorIds++;
        this.capacidadMaxima = capacidad;
        this.esPrivada = privada;
        this.nombreAdmin = creador.getNombreJugador();
        this.jugadores = new ArrayList<>();
        this.jugadores.add(creador); 
        this.enJuego = false;
        this.mazo = new ArrayList<String>();
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
        prepararJuego();
        return true;
    }

    private void prepararJuego() {
        mazo.clear();
        String[] tipos = {Constantes.DUQUE, Constantes.ASESINO, Constantes.CAPITAN, Constantes.EMBAJADOR, Constantes.CONDESA};
        for (String tipo : tipos) {
            for (int i = 0; i < 3; i++) {
                mazo.add(tipo);
            }
        }
        Collections.shuffle(mazo);

        for (HiloCliente jugador : jugadores) {
            jugador.reiniciarEstadoJuego();

            if (mazo.size() >= 2) {
                jugador.agregarCarta(mazo.remove(0));
                jugador.agregarCarta(mazo.remove(0));
            }
            StringBuilder info = new StringBuilder();
            info.append("¡La partida ha empezado shavalones!\n");
            info.append("Monedas: ").append(jugador.getMonedas()).append("\n");
            info.append("Tus Cartas: ").append(jugador.getCartasEnMano()).append("\n");

            jugador.enviarMensaje(new Mensaje(Constantes.ESTADO, info.toString()));
        }

        broadcastSala(new Mensaje(Constantes.ESTADO, ">> Se han repartido las cartas. ¡Suerte a todos!"));

        this.turnoActual = 0;
        notificarTurno();
    }

    private void notificarTurno() {
        if (jugadores.isEmpty()) return;
        HiloCliente jugadorActivo = jugadores.get(turnoActual);
        if (!jugadorActivo.isEstaVivo()) {
            siguienteTurno();
            return;
        }
        jugadorActivo.enviarMensaje(new Mensaje(Constantes.TURNO, ">> ES TU TURNO. (aqui van los comandos del juego)")); //Aqui van los comandos del juego
        for (HiloCliente j : jugadores) {
            if (j != jugadorActivo) {
                j.enviarMensaje(new Mensaje(Constantes.ESTADO, "Turno de " + jugadorActivo.getNombreJugador()));
            }
        }
    }

    private void siguienteTurno() {
    }


    public int getId() {
        return id;
    }
    public boolean isEsPrivada() {
        return esPrivada;
    }
    public boolean isEnJuego() {
        return enJuego;
    }
    public List<HiloCliente> getJugadores() {
        return jugadores;
    }

    @Override
    public String toString() {
        return "Sala #" + id + " de " + nombreAdmin + " [" + (jugadores != null ? jugadores.size() : 0) + "/" + capacidadMaxima + "]";
    }
}