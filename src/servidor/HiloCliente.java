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

    
    private static final String REGEX_USUARIO = "^[a-zA-Z0-9]{6,12}$";
    private static final String REGEX_PASS = "^[a-zA-Z0-9]{6,12}$";

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

         
            enviarMensaje(new Mensaje(Constantes.ESTADO,
                    "--------------------------------------------------\n" +
                            " BIENVENIDO A COUP \n" +
                            " Para jugar, identifícate:\n" +
                            "  > /registrar <usuario> <pass> <pass>\n" +
                            "  > /login <usuario> <pass> <pass>\n" +
                            "--------------------------------------------------"));

            while (true) {
                Mensaje msj = (Mensaje) entrada.readObject();

                if (msj.tipo.equals(Constantes.TEXTO)) {
                    String texto = (String) msj.contenido;

                    if (texto.startsWith("/")) {
                        procesarComando(texto);
                    } else {
                        if (autenticado) {
                            // Chat Global del Lobby 
                            ServidorCoup.broadcast(new Mensaje(Constantes.TEXTO, nombreJugador + ": " + texto));
                        } else {
                            enviarMensaje(new Mensaje(Constantes.ESTADO, "Debes iniciar sesión primero."));
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.out.println(">> Cliente " + (nombreJugador != null ? nombreJugador : "anónimo") + " desconectado.");
        } finally {
            desconectar();
        }
    }

    private void procesarComando(String texto) {
        String[] partes = texto.trim().split("\\s+");
        String comando = partes[0];

        //  COMANDOS DE REGISTRO Y LOGIN 
        if (comando.equals("/registrar")) {
            if (autenticado) {
                enviarMensaje(new Mensaje(Constantes.ESTADO, "Ya has iniciado sesión como " + nombreJugador));
                return;
            }
            if (partes.length != 4) {
                enviarMensaje(new Mensaje(Constantes.ESTADO, "Uso: /registrar <usuario> <pass> <pass>"));
                return;
            }
            registro(partes[1], partes[2], partes[3]);
            return;
        }

        if (comando.equals("/login")) {
            if (autenticado) {
                enviarMensaje(new Mensaje(Constantes.ESTADO, "Ya estás dentro como " + nombreJugador));
                return;
            }
            if (partes.length != 4) {
                enviarMensaje(new Mensaje(Constantes.ESTADO, "Uso: /login <usuario> <pass> <pass>"));
                return;
            }
            login(partes[1], partes[2]);
            return;
        }

        // COMANDOS DE LOBBY 
        if (autenticado) {
            switch (comando) {
                case "/crear":
                 
                    enviarMensaje(new Mensaje(Constantes.ESTADO, ">> [Sistema] Solicitud recibida: Crear sala. (En construcción)"));
                    break;
                case "/unirse":
                
                    enviarMensaje(new Mensaje(Constantes.ESTADO, ">> [Sistema] Solicitud recibida: Unirse a sala. (En construcción)"));
                    break;
                case "/lista":
                  
                    enviarMensaje(new Mensaje(Constantes.ESTADO, ">> [Sistema] Buscando salas disponibles... (En construcción)"));
                    break;
                case "/salir":
                    enviarMensaje(new Mensaje(Constantes.ESTADO, "Cerrando sesión..."));
                    desconectar();
                    break;
                case "/ayuda":
                    mostrarMenuPrincipal();
                    break;
                default:
                    enviarMensaje(new Mensaje(Constantes.ESTADO, "Comando desconocido. Escribe /ayuda para ver las opciones."));
            }
        } else {
           
            enviarMensaje(new Mensaje(Constantes.ESTADO, "Comando desconocido o no tienes permiso. Inicia sesión primero."));
        }
    }

    private void registro(String user, String pass, String confirm) {
        if (!Pattern.matches(REGEX_USUARIO, user)) {
            enviarMensaje(new Mensaje(Constantes.ESTADO, "Error: Usuario debe tener 6-12 caracteres alfanuméricos."));
            return;
        }
        if (!Pattern.matches(REGEX_PASS, pass)) {
            enviarMensaje(new Mensaje(Constantes.ESTADO, "Error: Contraseña debe tener 6-12 caracteres alfanuméricos."));
            return;
        }
        if (!pass.equals(confirm)) {
            enviarMensaje(new Mensaje(Constantes.ESTADO, "Error: Las contraseñas no coinciden."));
            return;
        }

        if (BaseDatos.registrarUsuario(user, pass)) {
            enviarMensaje(new Mensaje(Constantes.ESTADO, "¡Registro exitoso! Ahora usa /login."));
        } else {
            enviarMensaje(new Mensaje(Constantes.ESTADO, "Error: El usuario ya existe."));
        }
    }

    private void login(String user, String pass) {
        if (BaseDatos.validarLogin(user, pass)) {
            this.nombreJugador = user;
            this.autenticado = true;
            
          
            ServidorCoup.clientesConectados.add(this);
            
            enviarMensaje(new Mensaje(Constantes.ESTADO, "Login correcto. Bienvenido al Lobby, " + user));
            ServidorCoup.broadcast(new Mensaje(Constantes.TEXTO, ">> " + user + " ha entrado al Lobby."));

            mostrarMenuPrincipal();

        } else {
            enviarMensaje(new Mensaje(Constantes.ESTADO, "Credenciales incorrectas."));
        }
    }


    private void mostrarMenuPrincipal() {
        String menu = "\n============== MENÚ PRINCIPAL ==============\n" +
                      "1. /crear <capacidad> <privada/publica> \n" +
                      "   (Ej: /crear 4 publica)\n" +
                      "2. /unirse <id_sala>\n" +
                      "3. /lista (Ver salas disponibles)\n" +
                      "4. /salir (Cerrar sesión)\n" +
                      "============================================";
        enviarMensaje(new Mensaje(Constantes.ESTADO, menu));
    }

    public void enviarMensaje(Mensaje msj) {
        try {
            salida.writeObject(msj);
            salida.flush();
        } catch (IOException e) {
            
        }
    }

    private void desconectar() {
        try {
            if (autenticado) {
                ServidorCoup.clientesConectados.remove(this);
                ServidorCoup.broadcast(new Mensaje(Constantes.TEXTO, ">> " + nombreJugador + " ha salido."));
            }
            socket.close();
  
            this.interrupt();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}