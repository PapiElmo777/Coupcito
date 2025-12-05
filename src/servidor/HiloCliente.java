package servidor;

import comun.Constantes;
import comun.Mensaje;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class HiloCliente extends Thread {

    private Socket socket;
    private ObjectOutputStream salida;
    private ObjectInputStream entrada;
    private String nombreJugador;

    public HiloCliente(Socket socket) {
        this.socket = socket;
    }

    public String getNombreJugador() {
        return nombreJugador;
    }

    @Override
    public void run() {
        try {
            salida = new ObjectOutputStream(socket.getOutputStream());
            salida.flush();
            entrada = new ObjectInputStream(socket.getInputStream());
            while (true) {
                Mensaje msjRecibido = (Mensaje) entrada.readObject();

                if (msjRecibido.tipo.equals(Constantes.TIPO_LOGIN)) {
                    this.nombreJugador = (String) msjRecibido.contenido;
                    enviarMensaje(new Mensaje(Constantes.TIPO_ESTADO, ">> Conectado exitosamente como: " + nombreJugador));
                    ServidorCoup.enviarATodos(new Mensaje(Constantes.TIPO_MENSAJE, ">> El jugador " + nombreJugador + " ha entrado al lobby."));
                    System.out.println("Login procesado: " + nombreJugador);
                }
                else if (msjRecibido.tipo.equals(Constantes.TIPO_MENSAJE)) {
                    ServidorCoup.enviarATodos(new Mensaje(Constantes.TIPO_MENSAJE, nombreJugador + ": " + msjRecibido.contenido));
                }
                else {
                    System.out.println("Comando desconocido de " + nombreJugador + ": " + msjRecibido.tipo);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println(">> Cliente " + (nombreJugador != null ? nombreJugador : "Desconocido") + " se ha desconectado.");
        } finally {
            desconectar();
        }
    }

    public void enviarMensaje(Mensaje msj) {
        try {
            salida.writeObject(msj);
            salida.flush();
        } catch (IOException e) {
            System.out.println("Error enviando mensaje a " + nombreJugador);
        }
    }

    private void desconectar() {
        try {
            ServidorCoup.clientesConectados.remove(this);
            if(nombreJugador != null) {
                ServidorCoup.enviarATodos(new Mensaje(Constantes.TIPO_MENSAJE, ">> " + nombreJugador + " ha abandonado el servidor."));
            }
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}