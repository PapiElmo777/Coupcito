package servidor;

import comun.Constantes;
import comun.Mensaje;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class HiloCliente extends Thread {


public String getNombreJugador() { 
    return nombreJugador;
}
    private Socket socket;
    private ObjectOutputStream salida;
    private ObjectInputStream entrada;
    private String nombreJugador;

    public HiloCliente(Socket socket) {
        this.socket = socket;
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
        
     
        enviarMensaje(new Mensaje(Constantes.TIPO_ESTADO, "Conectado como " + nombreJugador));

       
        ServidorCoup.enviarATodos(new Mensaje(Constantes.TIPO_MENSAJE, ">> " + nombreJugador + " se ha unido a la sala."));
        
        System.out.println("Login procesado para: " + nombreJugador);
    } 
    else {
   
        System.out.println("Mensaje recibido: " + msjRecibido.tipo);
    }
}

        } catch (Exception e) {
            System.out.println(">> Cliente desconectado.");
        } finally {
            try { socket.close(); } catch (IOException ex) {}
            ServidorCoup.clientesConectados.remove(this);
        }
    }
    public void enviarMensaje(Mensaje msj) {
        try {
            salida.writeObject(msj);
            salida.flush();
        } catch (IOException e) {
            System.out.println("Error enviando mensaje a cliente.");
        }
    }
}