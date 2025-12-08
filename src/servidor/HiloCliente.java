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
    
    // Referencia a la sala actual 
    private Sala salaActual = null; 

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
                        procesarChat(texto); // Refactorizamos el chat
                    }
                }
            }

        } catch (Exception e) {
            System.out.println(">> Cliente " + (nombreJugador != null ? nombreJugador : "anónimo") + " desconectado.");
        } finally {
            desconectar();
        }
    }

    // Método auxiliar para chat de sala vs chat global
    private void procesarChat(String texto) {
        if (autenticado) {
            if (salaActual != null) {
                // Chat de Sala Privado
                for (HiloCliente h : salaActual.getJugadores()) {
                    h.enviarMensaje(new Mensaje(Constantes.TEXTO, "[Sala] " + nombreJugador + ": " + texto));
                }
            } else {
                // Chat Global del Lobby 
                ServidorCoup.broadcast(new Mensaje(Constantes.TEXTO, "[Lobby] " + nombreJugador + ": " + texto));
            }
        } else {
            enviarMensaje(new Mensaje(Constantes.ESTADO, "Debes iniciar sesión primero."));
        }
    }

    private void procesarComando(String texto) {
        String[] partes = texto.trim().split("\\s+");
        String comando = partes[0];

        // COMANDOS DE REGISTRO Y LOGIN 
        if (comando.equals("/registrar")) {
            if (partes.length != 4) { enviarMensaje(new Mensaje(Constantes.ESTADO, "Uso: /registrar <user> <pass> <pass>")); return; }
            registro(partes[1], partes[2], partes[3]);
            return;
        }

        if (comando.equals("/login")) {
            if (partes.length != 3) { enviarMensaje(new Mensaje(Constantes.ESTADO, "Uso: /login <user> <pass>")); return; }
            login(partes[1], partes[2]);
            return;
        }

        // COMANDOS DE LOBBY
        if (autenticado) {
            switch (comando) {
                case "/crear":
                  
                    if (salaActual != null) {
                        enviarMensaje(new Mensaje(Constantes.ESTADO, "Error: Ya estás en una sala. Usa /salir_sala primero."));
                        break;
                    }
                    if (partes.length < 2) {
                        enviarMensaje(new Mensaje(Constantes.ESTADO, "Uso: /crear <2-6> [privada]"));
                        break;
                    }
                    try {
                        int capacidad = Integer.parseInt(partes[1]);
                        if (capacidad < 2 || capacidad > 6) {
                            enviarMensaje(new Mensaje(Constantes.ESTADO, "Error: Capacidad debe ser entre 2 y 6."));
                            break;
                        }
                        boolean privada = (partes.length > 2 && partes[2].equalsIgnoreCase("privada"));
                        
                        this.salaActual = ServidorCoup.crearSala(this, capacidad, privada);
                        enviarMensaje(new Mensaje(Constantes.ESTADO, ">> Sala #" + salaActual.getId() + " creada exitosamente."));
                    } catch (NumberFormatException e) {
                        enviarMensaje(new Mensaje(Constantes.ESTADO, "Error: La capacidad debe ser un número."));
                    }
                    break;

                case "/unirse":
                   
                    if (salaActual != null) {
                        enviarMensaje(new Mensaje(Constantes.ESTADO, "Error: Ya estás en una sala."));
                        break;
                    }
                    if (partes.length < 2) {
                        enviarMensaje(new Mensaje(Constantes.ESTADO, "Uso: /unirse <id_sala>"));
                        break;
                    }
                    try {
                        int id = Integer.parseInt(partes[1]);
                        Sala sala = ServidorCoup.buscarSala(id);
                        if (sala != null) {
                            if (sala.agregarJugador(this)) {
                                this.salaActual = sala;
                                enviarMensaje(new Mensaje(Constantes.ESTADO, ">> Te has unido a la sala #" + id));
                                // Notificar a los otros en la sala
                                for(HiloCliente h : sala.getJugadores()) {
                                    if(h != this) h.enviarMensaje(new Mensaje(Constantes.TEXTO, ">> " + nombreJugador + " se ha unido."));
                                }
                            } else {
                                enviarMensaje(new Mensaje(Constantes.ESTADO, "Error: Sala llena o en juego."));
                            }
                        } else {
                            enviarMensaje(new Mensaje(Constantes.ESTADO, "Error: Sala no encontrada."));
                        }
                    } catch (NumberFormatException e) {
                        enviarMensaje(new Mensaje(Constantes.ESTADO, "Error: ID inválido."));
                    }
                    break;

                case "/lista":
                    enviarMensaje(new Mensaje(Constantes.ESTADO, ServidorCoup.obtenerListaSalasPublicas()));
                    break;
                    
                case "/invitar":
                 
                    if (salaActual == null) {
                        enviarMensaje(new Mensaje(Constantes.ESTADO, "Error: Debes estar en una sala para invitar."));
                        break;
                    }
                    if (partes.length < 2) {
                        enviarMensaje(new Mensaje(Constantes.ESTADO, "Uso: /invitar <usuario>"));
                        break;
                    }
                    String invitadoNombre = partes[1];
                    HiloCliente invitado = ServidorCoup.buscarClientePorNombre(invitadoNombre);
                    
                    if (invitado != null) {
                        invitado.enviarMensaje(new Mensaje(Constantes.ESTADO, 
                            "******* INVITACIÓN *******\n" +
                            nombreJugador + " te invita a jugar en la Sala #" + salaActual.getId() + "\n" +
                            "Escribe: /unirse " + salaActual.getId() + "\n" +
                            "**************************"));
                        enviarMensaje(new Mensaje(Constantes.ESTADO, "Invitación enviada a " + invitadoNombre));
                    } else {
                        enviarMensaje(new Mensaje(Constantes.ESTADO, "Usuario no encontrado o desconectado."));
                    }
                    break;
                    
                case "/salir_sala":
                    if (salaActual != null) {
                        salaActual.removerJugador(this);
                        for(HiloCliente h : salaActual.getJugadores()) {
                             h.enviarMensaje(new Mensaje(Constantes.TEXTO, ">> " + nombreJugador + " abandonó la sala."));
                        }
                        this.salaActual = null;
                        enviarMensaje(new Mensaje(Constantes.ESTADO, "Has salido de la sala."));
                    } else {
                        enviarMensaje(new Mensaje(Constantes.ESTADO, "No estás en ninguna sala."));
                    }
                    break;

                case "/salir":
                    enviarMensaje(new Mensaje(Constantes.ESTADO, "Cerrando sesión..."));
                    desconectar();
                    break;
                case "/ayuda":
                    mostrarMenuPrincipal();
                    break;
                default:
                    enviarMensaje(new Mensaje(Constantes.ESTADO, "Comando desconocido. /ayuda"));
            }
        } else {
            enviarMensaje(new Mensaje(Constantes.ESTADO, "Inicia sesión primero."));
        }
    }

    //  MÉTODOS DE REGISTRO/LOGIN/DESCONEXION 

    private void registro(String user, String pass, String confirm) {
        if (!Pattern.matches(REGEX_USUARIO, user)) {
            enviarMensaje(new Mensaje(Constantes.ESTADO, "Error: Usuario 6-12 chars alfanuméricos."));
            return;
        }
        if (!Pattern.matches(REGEX_PASS, pass)) {
            enviarMensaje(new Mensaje(Constantes.ESTADO, "Error: Password 6-12 chars."));
            return;
        }
        if (!pass.equals(confirm)) {
            enviarMensaje(new Mensaje(Constantes.ESTADO, "Error: Las contraseñas no coinciden."));
            return;
        }

        if (BaseDatos.registrarUsuario(user, pass)) {
            enviarMensaje(new Mensaje(Constantes.ESTADO, "Registro exitoso. Usa /login."));
        } else {
            enviarMensaje(new Mensaje(Constantes.ESTADO, "Usuario ya existe."));
        }
    }

    private void login(String user, String pass) {
        if (BaseDatos.validarLogin(user, pass)) {
            this.nombreJugador = user;
            this.autenticado = true;
            ServidorCoup.clientesConectados.add(this);
            enviarMensaje(new Mensaje(Constantes.ESTADO, "Bienvenido " + user));
            ServidorCoup.broadcast(new Mensaje(Constantes.TEXTO, ">> " + user + " entró al Lobby."));
            mostrarMenuPrincipal();
        } else {
            enviarMensaje(new Mensaje(Constantes.ESTADO, "Credenciales incorrectas."));
        }
    }

    private void mostrarMenuPrincipal() {
        String menu = "\n=== MENÚ ===\n" +
                      "/crear <2-6> [privada]\n" +
                      "/unirse <id_sala>\n" +
                      "/invitar <usuario>\n" +
                      "/lista\n" +
                      "/salir_sala\n" +
                      "/salir";
        enviarMensaje(new Mensaje(Constantes.ESTADO, menu));
    }

    public void enviarMensaje(Mensaje msj) {
        try {
            salida.writeObject(msj);
            salida.flush();
        } catch (IOException e) {}
    }

    private void desconectar() {
        try {
            if (autenticado) {
                if(salaActual != null) salaActual.removerJugador(this);
                ServidorCoup.clientesConectados.remove(this);
                ServidorCoup.broadcast(new Mensaje(Constantes.TEXTO, ">> " + nombreJugador + " salió."));
            }
            socket.close();
            this.interrupt();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}