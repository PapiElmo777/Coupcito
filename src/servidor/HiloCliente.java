package servidor;

import comun.Constantes;
import comun.Mensaje;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.regex.Pattern;

public class HiloCliente extends Thread {

    private Socket socket;
    private ObjectOutputStream salida;
    private ObjectInputStream entrada;
    private String nombreJugador = null;
    private boolean autenticado = false;
    private Sala salaActual = null;
    private ProcesadorComandos procesador;

    public HiloCliente(Socket socket) {
        this.socket = socket;
        this.procesador = new ProcesadorComandos(this);
    }

    @Override
    public void run() {
        try {
            salida = new ObjectOutputStream(socket.getOutputStream());
            salida.flush();
            entrada = new ObjectInputStream(socket.getInputStream());

            enviarMensaje(new Mensaje(Constantes.ESTADO,
                    "--------------------------------------------------\n" +
                            " BIENVENIDO A COUP \n" +
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
}