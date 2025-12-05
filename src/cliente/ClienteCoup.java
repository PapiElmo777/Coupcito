package cliente;

import comun.Constantes;
import comun.Mensaje;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;

public class ClienteCoup {
    private static Socket socket;
    private static ObjectOutputStream salida;
    private static ObjectInputStream entrada;
    private static boolean conectado = true;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        try {
            socket = new Socket("localhost", Constantes.PUERTO);
            salida = new ObjectOutputStream(socket.getOutputStream());
            salida.flush();
            entrada = new ObjectInputStream(socket.getInputStream());
            Thread hiloEscucha = new Thread(() -> {
                try {
                    while (conectado) {
                        Mensaje msj = (Mensaje) entrada.readObject();
                        if (msj.tipo.equals(Constantes.TEXTO) || msj.tipo.equals(Constantes.ESTADO)) {
                            System.out.println(msj.contenido);
                        }
                    }
                } catch (Exception e) {
                    System.out.println("\nTe has desconectado del servidor.");
                    conectado = false;
                    System.exit(0);
                }
            });
            hiloEscucha.start();
            while (conectado) {
                if (scanner.hasNextLine()) {
                    String texto = scanner.nextLine();
                    if (!texto.isEmpty()) {
                        salida.writeObject(new Mensaje(Constantes.TEXTO, texto));
                        salida.flush();
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("Error: No se pudo conectar al servidor.");
            System.out.println("Asegúrate de ejecutar 'ServidorCoup' antes que este archivo.");
        }
    }
}
