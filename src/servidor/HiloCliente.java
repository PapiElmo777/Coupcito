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
                            if (salaActual != null) {
                                salaActual.broadcastSala(new Mensaje(Constantes.TEXTO, "[Sala " + salaActual.getId() + "] " + nombreJugador + ": " + texto));
                            } else {
                                ServidorCoup.broadcast(new Mensaje(Constantes.TEXTO, "[Lobby] " + nombreJugador + ": " + texto));
                            }
                        } else {
                            enviarMensaje(new Mensaje(Constantes.ESTADO, "Debes iniciar sesión primero."));
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.out.println(">> Cliente desconectado.");
        } finally {
            desconectar();
        }
    }

    private void procesarComando(String texto) {
        String[] partes = texto.trim().split("\\s+");
        String comando = partes[0];
    //comandos de acceso
        if (comando.equals("/registrar")) {
            if (autenticado) {
                enviarMensaje(new Mensaje(Constantes.ESTADO, "Ya has iniciado sesión."));
                return;
            }
            if (partes.length != 4) {
                enviarMensaje(new Mensaje(Constantes.ESTADO, "Uso: /registrar <usuario> <pass> <pass>"));
                return;
            }
            registro(partes[1], partes[2], partes[3]);

        } else if (comando.equals("/login")) {
            if (autenticado) {
                enviarMensaje(new Mensaje(Constantes.ESTADO, "Ya ests dentro."));
                return;
            }
            if (partes.length != 4) {
                enviarMensaje(new Mensaje(Constantes.ESTADO, "Uso: /login <usuario> <pass> <pass>"));
                return;
            }
            login(partes[1], partes[2]);

            if (!autenticado) {
                enviarMensaje(new Mensaje(Constantes.ESTADO, "Comando invalido. Inicia sesion primero."));
                return;
            }

        } else {
            enviarMensaje(new Mensaje(Constantes.ESTADO, "Comando desconocido."));
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
            enviarMensaje(new Mensaje(Constantes.ESTADO, "Login correcto. Bienvenido al Lobby, " + user));

            ServidorCoup.clientesConectados.add(this);

            ServidorCoup.broadcast(new Mensaje(Constantes.TEXTO, ">> " + user + " ha entrado al Lobby."));
        } else {
            enviarMensaje(new Mensaje(Constantes.ESTADO, "Credenciales incorrectas."));
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
            if (autenticado) {
                ServidorCoup.clientesConectados.remove(this);
                ServidorCoup.broadcast(new Mensaje(Constantes.TEXTO, ">> " + nombreJugador + " ha salido."));
            }
            socket.close();
        } catch (IOException e) {}
    }
}