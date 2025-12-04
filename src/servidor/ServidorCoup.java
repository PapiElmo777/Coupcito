package servidor;

import comun.Constantes;
import comun.Mensaje;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class ServidorCoup {
    
public static void enviarATodos(Mensaje msj) {
    for (HiloCliente cliente : clientesConectados) {
      
        cliente.enviarMensaje(msj);
    }
}
    public static ArrayList<HiloCliente> clientesConectados = new ArrayList<>();
    public static void main(String[] args) {
        System.out.println(">> Iniciando Servidor Coup Shavalon...");
        try (ServerSocket serverSocket = new ServerSocket(Constantes.PUERTO)) {
            System.out.println(">> Servidor esperando jugadores en puerto: " + Constantes.PUERTO);

            while (true) {
                Socket socketCliente = serverSocket.accept();
                System.out.println(">> ¡Nuevo cliente conectado!: " + socketCliente.getInetAddress());
                HiloCliente nuevoHilo = new HiloCliente(socketCliente);
                clientesConectados.add(nuevoHilo);
                nuevoHilo.start();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
