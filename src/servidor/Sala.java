package servidor;

import comun.Constantes;
import comun.Mensaje;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public class Sala implements Serializable {
    private transient List<HiloCliente> espectadores;
    private static final int MAX_ESPECTADORES = 4;

    private static final long serialVersionUID = 1L;
    private static int contadorIds = 1;

    private int id;
    private int capacidadMaxima;
    private boolean esPrivada;
    private boolean enJuego;
    private String nombreAdmin;
    private boolean esperandoDesafio = false;
    private String cartaRequerida = null;
    private String accionPendiente = null;

    private List<String> listaInvitados;
    private transient List<HiloCliente> jugadores;
    private transient List<String> mazo;
    private int turnoActual = 0;

    // --- variables condesa ---
    private HiloCliente jugadorAtacante;
    private HiloCliente jugadorObjetivo;
    private boolean esperandoBloqueo = false;
    private int monedasAsesina = 0; //
    public boolean isEsperandoDesafio() { return esperandoDesafio; }
    public void setEsperandoDesafio(boolean esperandoDesafio) { this.esperandoDesafio = esperandoDesafio; }

    public String getCartaRequerida() { return cartaRequerida; }
    public void setCartaRequerida(String cartaRequerida) { this.cartaRequerida = cartaRequerida; }

    public String getAccionPendiente() { return accionPendiente; }
    public void setAccionPendiente(String accionPendiente) { this.accionPendiente = accionPendiente; }

    public void limpiarEstadoDesafio() {
        this.esperandoDesafio = false;
        this.cartaRequerida = null;
        this.accionPendiente = null;

    }

    public Sala(HiloCliente creador, int capacidad, boolean privada) {
        this.id = contadorIds++;
        this.capacidadMaxima = capacidad;
        this.esPrivada = privada;
        this.nombreAdmin = creador.getNombreJugador();
        this.jugadores = new ArrayList<>();
        this.jugadores.add(creador);
        this.enJuego = false;
        this.mazo = new ArrayList<String>();
        this.listaInvitados = new ArrayList<>();
        this.listaInvitados.add(creador.getNombreJugador());
        this.espectadores = new ArrayList<>();
    }

    public void agregarInvitado(String nombreJugador) {
        if (!listaInvitados.contains(nombreJugador)) {
            listaInvitados.add(nombreJugador);
        }
    }
    public boolean agregarEspectador(HiloCliente cliente) {
        if (espectadores.size() < MAX_ESPECTADORES) {
            espectadores.add(cliente);
            return true;
        }
        return false;
    }
    public boolean esInvitado(String nombreJugador) {
        return !esPrivada || listaInvitados.contains(nombreJugador);
    }
    public boolean esAdmin(String nombreJugador) {
        return this.nombreAdmin.equals(nombreJugador);
    }
    public boolean agregarJugador(HiloCliente jugador) {
        if (jugadores.size() < capacidadMaxima && !enJuego) {
            jugadores.add(jugador);
            return true;
        }
        return false;
    }

    public void removerJugador(HiloCliente jugador) {
        if (jugadores == null) return;
        if (jugadores.contains(jugador)) {
            if (enJuego && jugador.isEstaVivo()) {
                broadcastSala(new Mensaje(Constantes.ESTADO, ">> " + jugador.getNombreJugador() + " abandono la partida y ha sido ELIMINADO."));
                for (String c : jugador.getCartasEnMano()) {
                    devolverCartaAlMazo(c);
                }
                jugador.getCartasEnMano().clear();
            }
            jugadores.remove(jugador);
            if (enJuego) {
                verificarGanador();
                if (jugadores.isEmpty()) enJuego = false;
            }
        }
        else if (espectadores.contains(jugador)) {
            espectadores.remove(jugador);
            broadcastSala(new Mensaje(Constantes.TEXTO, ">> El espectador " + jugador.getNombreJugador() + " salio de la sala."));
        }
    }

    void verificarGanador() {
        if (!enJuego) return;
        List<HiloCliente> vivos = new ArrayList<>();
        for (HiloCliente j : jugadores) {
            if (j.isEstaVivo()) {
                vivos.add(j);
            }
        }
        if (vivos.size() == 1) {
            HiloCliente ganador = vivos.get(0);
            String mensajeVictoria = "\n" +
                    "********************************************\n" +
                    "   ¡VICTORIA PARA!: " + ganador.getNombreJugador() + "\n" +
                    "********************************************\n";

            broadcastSala(new Mensaje(Constantes.ESTADO, mensajeVictoria));
            BaseDatos.sumarVictoria(ganador.getNombreJugador());
            broadcastSala(new Mensaje(Constantes.ESTADO, "La partida ha finalizado. Usen /iniciar para jugar de nuevo."));
        }
    }

    public void broadcastSala(Mensaje msj) {
        if (jugadores != null) {
            for (HiloCliente j : jugadores) {
                j.enviarMensaje(msj);
            }
        }
        if (espectadores != null) {
            for (HiloCliente e : espectadores) {
                e.enviarMensaje(msj);
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

        String ayuda = "\n=== LISTA DE COMANDOS ===\n" +
                "INGRESOS:\n" +
                "  /tomar_moneda  -> Toma 1 moneda (Nadie puede bloquear).\n" +
                "  /dos_monedas   -> Toma 2 monedas (Bloqueable por Duque).\n" +
                "  /soy_duque     -> Toma 3 monedas (Desafiable).\n" +
                "ACCIONES DE ATAQUE:\n" +
                "  /asesinar <jugador> -> Paga 3 monedas para eliminar una carta (Bloqueable por Condesa).\n" +
                "  /robar <jugador>    -> Capitan: Roba 2 monedas (Bloqueable por Capitan/Embajador).\n" +
                "  /coupear <jugador>  -> Paga 7 monedas para matar a un jugador de manera inevitable.\n" +
                "OTRAS:\n" +
                "  /embajador -> Cambia tus cartas con el mazo.\n" +
                "============================\n";

        for (HiloCliente jugador : jugadores) {
            jugador.reiniciarEstadoJuego();

            if (mazo.size() >= 2) {
                jugador.agregarCarta(mazo.remove(0));
                jugador.agregarCarta(mazo.remove(0));
            }
            jugador.enviarMensaje(new Mensaje(Constantes.ESTADO, ayuda));

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
        for (HiloCliente j : jugadores) {
            if (j == jugadorActivo) {
                j.enviarMensaje(new Mensaje(Constantes.TURNO, "\n>>> Es tu turno shavalon <<< \n(Usa un comando de la lista)"));
            } else {
                j.enviarMensaje(new Mensaje(Constantes.ESTADO, "\nEs el turno de: " + jugadorActivo.getNombreJugador()));
            }
        }
    }

    public void siguienteTurno() {
        if (!enJuego) return;
        int intentos = 0;
        do {
            turnoActual = (turnoActual + 1) % jugadores.size();
            intentos++;
        } while (!jugadores.get(turnoActual).isEstaVivo() && intentos < jugadores.size());

        notificarTurno();
    }
    public boolean esTurnoDe(HiloCliente jugador) {
        return enJuego && jugadores.get(turnoActual).equals(jugador);
    }
    //metodos para el embajador
    public String tomarCartaDelMazo() {
        if (mazo.isEmpty()) return null;
        return mazo.remove(0);
    }

    public void devolverCartaAlMazo(String carta) {
        mazo.add(carta);
        Collections.shuffle(mazo);
    }
    //setters y getters
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

    public boolean isEsperandoBloqueo() {
        return esperandoBloqueo;
    }
    public void setEsperandoBloqueo(boolean espera) {
        this.esperandoBloqueo = espera;
    }

    public HiloCliente getJugadorObjetivo() {
        return jugadorObjetivo;
    }
    public void setJugadorObjetivo(HiloCliente objetivo) {
        this.jugadorObjetivo = objetivo;
    }

    public HiloCliente getJugadorAtacante() {
        return jugadorAtacante;
    }
    public void setJugadorAtacante(HiloCliente atacante) { this.jugadorAtacante = atacante; }

    public int getMonedasEnJuego() {
        return monedasAsesina;
    }
    public void setMonedasEnJuego(int monedas) {
        this.monedasAsesina = monedas;
    }
    public List<HiloCliente> getEspectadores() {
        return espectadores;
    }

    @Override
    public String toString() {
        return "Sala #" + id + " de " + nombreAdmin + " [" + (jugadores != null ? jugadores.size() : 0) + "/" + capacidadMaxima + "]";
    }
}