package servidor;

import comun.Constantes;
import comun.Mensaje;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class ServidorCoup {

    public static ArrayList<HiloCliente> clientesConectados = new ArrayList<>();
    
    public static void main(String[] args) {
        System.out.println(">> Iniciando Servidor Coup Shavalon...");
        BaseDatos.conectar();
        try (ServerSocket serverSocket = new ServerSocket(Constantes.PUERTO)) {
            System.out.println(">> Servidor esperando jugadores en puerto: " + Constantes.PUERTO);

            while (true) {
                Socket socketCliente = serverSocket.accept();
                System.out.println(">> ¡Nuevo cliente conectado!: " + socketCliente.getInetAddress());
                HiloCliente nuevoHilo = new HiloCliente(socketCliente);
                nuevoHilo.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void broadcast(Mensaje msj) {
        for (HiloCliente cliente : clientesConectados) {
            cliente.enviarMensaje(msj);
        }
    }

    public static synchronized HiloCliente buscarClientePorNombre(String nombre) {
        for (HiloCliente cliente : clientesConectados) {
            if (cliente.getNombreJugador() != null && cliente.getNombreJugador().equals(nombre)) {
                return cliente;
            }
        }
        return null;
    }
    
    // SE ELIMINARON: crearSala, buscarSala y obtenerListaSalasPublicas 
    // Razón: Eran redundantes o código muerto. La lógica real está en GestorSalas.
}