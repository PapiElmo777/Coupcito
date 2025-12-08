package servidor;

import comun.Constantes;
import comun.Mensaje;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ServidorCoup {

    
    public static ArrayList<HiloCliente> clientesConectados = new ArrayList<>();
    

    public static List<Sala> salasActivas = new ArrayList<>();

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

    // --- MÉTODOS DE GESTION DE SALAS 

    public static synchronized Sala crearSala(HiloCliente creador, int capacidad, boolean privada) {
        Sala nuevaSala = new Sala(creador, capacidad, privada);
        salasActivas.add(nuevaSala);
        System.out.println(">> Sala creada ID " + nuevaSala.getId() + " por " + creador.getNombreJugador());
        return nuevaSala;
    }

    public static synchronized Sala buscarSala(int id) {
        for (Sala s : salasActivas) {
            if (s.getId() == id) {
                return s;
            }
        }
        return null;
    }

    public static synchronized String obtenerListaSalasPublicas() {
        StringBuilder sb = new StringBuilder();
        sb.append("--- SALAS DISPONIBLES ---\n");
        boolean haySalas = false;
        for (Sala s : salasActivas) {
            if (!s.isEsPrivada() && !s.isEnJuego()) {
                sb.append(s.toString()).append("\n");
                haySalas = true;
            }
        }
        if (!haySalas) sb.append("No hay salas públicas disponibles.\n");
        sb.append("-------------------------");
        return sb.toString();
    }
    

}