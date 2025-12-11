package servidor;

import comun.Constantes;
import comun.Mensaje;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;

public class HiloCliente extends Thread {

    private Socket socket;
    private ObjectOutputStream salida;
    private ObjectInputStream entrada;
    private String nombreJugador = null;
    private boolean autenticado = false;
    private Sala salaActual = null;
    private ProcesadorComandos procesador;
    //atributos juego
    private int monedas;
    private List<String> cartasEnMano;
    private boolean estaVivo;

    public HiloCliente(Socket socket) {
        this.socket = socket;
        this.procesador = new ProcesadorComandos(this);
        this.cartasEnMano = new ArrayList<>();
    }

    @Override
    public void run() {
        try {
            salida = new ObjectOutputStream(socket.getOutputStream());
            salida.flush();
            entrada = new ObjectInputStream(socket.getInputStream());

            enviarMensaje(new Mensaje(Constantes.ESTADO,
                    "--------------------------------------------------\n" +
                            " BIENVENIDO A COUPCITO \n" +
                            " Usa: /registrar o /login para entrar.\n" +
                            "--------------------------------------------------"));

            while (true) {
                Mensaje msj = (Mensaje) entrada.readObject();

                if (msj.tipo.equals(Constantes.TEXTO)) {
                    String texto = (String) msj.contenido;

                    if (texto.startsWith("/")) {
                        procesador.procesar(texto);
                    } else {
                        manejarChat(texto);
                    }
                }
            }

        } catch (Exception e) {
            System.out.println(">> Cliente desconectado.");
        } finally {
            desconectar();
        }
    }
    private void manejarChat(String texto) {
        if (!autenticado) {
            enviarMensaje(new Mensaje(Constantes.ESTADO, "Debes iniciar sesión primero."));
            return;
        }
        if (salaActual != null) {
            salaActual.broadcastSala(new Mensaje(Constantes.TEXTO, "[Sala " + salaActual.getId() + "] " + nombreJugador + ": " + texto));
        } else {
            ServidorCoup.broadcast(new Mensaje(Constantes.TEXTO, "[Lobby] " + nombreJugador + ": " + texto));
        }
    }

    public void enviarMensaje(Mensaje msj) {
        try {
            salida.writeObject(msj);
            salida.flush();
        } catch (IOException e) {}
    }

    private void desconectar() {
        try {
            if (salaActual != null) {
                salaActual.removerJugador(this);
                if (salaActual.getJugadores().isEmpty()) {
                    GestorSalas.getInstancia().eliminarSala(salaActual);
                }
            }
            if (autenticado) {
                ServidorCoup.clientesConectados.remove(this);
                ServidorCoup.broadcast(new Mensaje(Constantes.TEXTO, ">> " + nombreJugador + " se desconectó."));
            }
            socket.close();
        } catch (IOException e) {}
    }
    //metodos de juego
    public void reiniciarEstadoJuego() {
        this.monedas = 2;
        if (this.cartasEnMano == null) {
            this.cartasEnMano = new ArrayList<>();
        } else {
            this.cartasEnMano.clear();
        }
        this.estaVivo = true;
    }

    public void agregarCarta(String carta) {
        this.cartasEnMano.add(carta);
    }
    public void perderCarta(String carta) {
        this.cartasEnMano.remove(carta);
        if (this.cartasEnMano.isEmpty()) {
            this.estaVivo = false;
        }
    }
    public boolean tieneCarta(String cartaBuscada) {
        if (cartasEnMano == null) return false;
        for (String c : cartasEnMano) {
            if (c.equalsIgnoreCase(cartaBuscada)) {
                return true;
            }
        }
        return false;
    }

    public void sumarMonedas(int cantidad) {
        this.monedas += cantidad;
    }
    //Getters y Setters
    public boolean isAutenticado() {
        return autenticado;
    }

    public void setAutenticado(boolean autenticado) {
        this.autenticado = autenticado;
    }

    public String getNombreJugador() {
        return nombreJugador;
    }

    public void setNombreJugador(String nombreJugador) {
        this.nombreJugador = nombreJugador;
    }

    public Sala getSalaActual() {
        return salaActual;
    }

    public void setSalaActual(Sala salaActual) {
        this.salaActual = salaActual;
    }
    public int getMonedas() {
        return monedas;
    }
    public List<String> getCartasEnMano() {
        return cartasEnMano;
    }
    public boolean isEstaVivo() {
        return estaVivo;
    }

}