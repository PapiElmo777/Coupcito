package servidor;

import comun.Constantes;
import comun.Mensaje;
import java.util.regex.Pattern;

public class ProcesadorComandos {
    private final HiloCliente cliente;
    private static final String REST_USUARIO = "^[a-zA-Z0-9]{6,12}$";
    private static final String REST_PASS = "^[a-zA-Z0-9]{6,12}$";

    public ProcesadorComandos(HiloCliente cliente) {
        this.cliente = cliente;
    }

    public void procesar(String texto) {
        String[] partes = texto.trim().split("\\s+");
        String comando = partes[0];

        switch (comando) {
            case "/registrar":
                manejarRegistro(partes);
                break;
            case "/login":
                manejarLogin(partes);
                break;
            case "/crear":
                manejarCrearSala(partes);
                break;
            case "/unir":
                manejarUnirse(partes);
                break;
            case "/lista":
                manejarListarSalas();
                break;
            case "/salir":
                manejarSalir();
                break;
            case "/salir_sala":
                manejarSalirDeSalaAlLobby();
                break;
            case "/iniciar":
                manejarIniciarPartida();
                break;
            case "/tomar_moneda":
                tomarUnaMoneda();
                break;
            case "/coupear":
                coupear(partes);
                break;
            case "/estado":
                manejarEstado();
                break;    
            default:
                cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Comando desconocido."));
        }
    }

    private void coupear(String[] partes) {
        if (!verificarTurno()) return;
        Sala sala = cliente.getSalaActual();
        if (cliente.getMonedas() < 7) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Necesitas 7 monedas para un cupear a un jugador. Tienes: " + cliente.getMonedas()));
            return;
        }
        if (partes.length < 2) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Uso: /coupear <nombre_jugador>"));
            return;
        }
        String nombreVictima = partes[1];
        HiloCliente victima = null;
        for (HiloCliente j : sala.getJugadores()) {
            if (j.getNombreJugador().equalsIgnoreCase(nombreVictima)) {
                victima = j;
                break;
            }
        }
        if (victima == null) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Jugador " + nombreVictima + " no encontrado en la sala."));
            return;
        }
        if (!victima.isEstaVivo()) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Ese jugador ya está eliminado."));
            return;
        }
        if (victima.equals(cliente)) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "No puedes darte matarte a ti mismo, no seas bobo."));
            return;
        }
        cliente.sumarMonedas(-7);
        sala.broadcastSala(new Mensaje(Constantes.ACCION, ">> ¡" + cliente.getNombreJugador() + " coupeo a " + victima.getNombreJugador() + "!"));
        String cartaPerdida = victima.getCartasEnMano().get(0);
        victima.perderCarta(cartaPerdida);
        sala.broadcastSala(new Mensaje(Constantes.ESTADO, ">> " + victima.getNombreJugador() + " perdio su carta: " + cartaPerdida));
        if (!victima.isEstaVivo()) {
            sala.broadcastSala(new Mensaje(Constantes.ESTADO, ">> " + victima.getNombreJugador() + " ha sido ELIMINADO ."));
        }
        sala.siguienteTurno();
    }

    private void manejarEstado() {
        if (cliente.getSalaActual() != null && cliente.getSalaActual().isEnJuego()) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO,
                    "--- ESTADO ---\n" +
                            "Monedas: " + cliente.getMonedas() + "\n" +
                            "Cartas: " + cliente.getCartasEnMano()));
        } else {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "No estás jugando actualmente."));
        }
    }

    private void tomarUnaMoneda() {
        if (!verificarTurno()) return;
        cliente.sumarMonedas(1);

        Sala sala = cliente.getSalaActual();
        sala.broadcastSala(new Mensaje(Constantes.TEXTO, ">> " + cliente.getNombreJugador() + " tomo una moneda (+1 moneda)."));
        sala.siguienteTurno();
    }

    private boolean verificarTurno() {
        Sala sala = cliente.getSalaActual();
        if (sala == null || !sala.isEnJuego()) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "No estas en una partida activa."));
            return false;
        }
        if (!sala.esTurnoDe(cliente)) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "¡No es tu turno! Espera tu turno tramposo"));
            return false;
        }
        return true;
    }

    private void manejarSalirDeSalaAlLobby() {
        Sala sala = cliente.getSalaActual();
        if (sala != null) {
            sala.removerJugador(cliente);
            if (sala.getJugadores().isEmpty()) {
                GestorSalas.getInstancia().eliminarSala(sala);
            }
            cliente.setSalaActual(null);
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Has salido de la sala."));
            mostrarMenuPrincipal();
        } else {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "No estas en ninguna sala."));
        }
    }

    private void manejarRegistro(String[] partes) {
        if (cliente.isAutenticado()) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Ya has iniciado sesion. Cierra sesion para registrar otro usuario."));
            return;
        }
        if (partes.length != 4) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Uso: /registrar <usuario> <pass> <confirm>"));
            return;
        }
        String user = partes[1];
        String pass = partes[2];
        String confirm = partes[3];

        if (!Pattern.matches(REST_USUARIO, user)) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Usuario inválido (6-12 caracteres alfanuméricos)."));
            return;
        }
        if (!Pattern.matches(REST_PASS, pass)) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Contraseña inválida (6-12 caracteres alfanuméricos)."));
            return;
        }
        if (!pass.equals(confirm)) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Las contraseñas no coinciden."));
            return;
        }

        if (BaseDatos.registrarUsuario(user, pass)) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Registro exitoso. Ahora usa /login."));
        } else {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "El usuario ya existe."));
        }
    }
    private void manejarLogin(String[] partes) {
        if (cliente.isAutenticado()) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Ya has iniciado sesion."));
            return;
        }
        if (partes.length < 3) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Uso: /login <usuario> <pass>"));
            return;
        }
        String user = partes[1];
        String pass = partes[2];

        if (BaseDatos.validarLogin(user, pass)) {
            cliente.setNombreJugador(user);
            cliente.setAutenticado(true);
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Bienvenido " + user));
            mostrarMenuPrincipal();
        } else {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Credenciales incorrectas."));
        }
    }
    private void manejarCrearSala(String[] partes) {
        if (!cliente.isAutenticado()) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Debes iniciar sesión primero."));
            return;
        }
        if (cliente.getSalaActual() != null) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Ya estás en una sala. Usa /salir primero."));
            return;
        }
        int capacidad = 6;
        boolean privada = false;
        if (partes.length > 1) {
            String arg1 = partes[1];
            if (arg1.matches("\\d+")) {
                capacidad = Integer.parseInt(arg1);
                if (capacidad < 2 || capacidad > 6) {
                    cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "La sala debe ser de entre 2 y 6 jugadores."));
                    return;
                }
            } else if (arg1.equalsIgnoreCase("privada")) {
                privada = true;
            }
        }
        if (partes.length > 2 && partes[2].equalsIgnoreCase("privada")) {
            privada = true;
        }

        Sala nueva = GestorSalas.getInstancia().crearSala(cliente, capacidad, privada);
        cliente.setSalaActual(nueva);

        String tipo = privada ? "Privada" : "Pública";
        cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Sala creada (" + tipo + ") para " + capacidad + " jugadores. ID: " + nueva.getId()));
        mostrarMenuPrincipal();
    }
    private void manejarUnirse(String[] partes) {
        if (!cliente.isAutenticado()) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Debes iniciar sesión primero."));
            return;
        }
        if (cliente.getSalaActual() != null) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Ya estás en una sala."));
            return;
        }
        if (partes.length < 2) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Uso: /unir <id_sala>"));
            return;
        }
        try {
            int idSala = Integer.parseInt(partes[1]);
            Sala s = GestorSalas.getInstancia().buscarSala(idSala);

            if (s == null) {
                cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Sala no encontrada."));
                return;
            }

            if (s.agregarJugador(cliente)) {
                cliente.setSalaActual(s);
                cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Te has unido a la sala #" + s.getId()));
                mostrarMenuPrincipal();
            } else {
                cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "No puedes unirte."));
            }
        } catch (NumberFormatException e) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "El ID debe ser un número."));
        }
    }
    private void manejarListarSalas() {
        if (!cliente.isAutenticado()) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Debes iniciar sesión primero."));
            return;
        }
        StringBuilder sb = new StringBuilder("Salas disponibles:\n");
        var listaSalas = GestorSalas.getInstancia().getSalas();

        if (listaSalas.isEmpty()) {
            sb.append("No hay salas activas.");
        } else {
            for (Sala s : listaSalas) {
                sb.append(s.toString()).append("\n");
            }
        }
        cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, sb.toString()));
    }
    private void manejarSalir() {
        if (cliente.getSalaActual() != null) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Primero usa /salir_sala para abandonar la partida."));
            return;
        }
        cliente.setAutenticado(false);
        cliente.setNombreJugador(null);
        cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Sesion cerrada."));
        cliente.enviarMensaje(new Mensaje(Constantes.ESTADO,
                "--------------------------------------------------\n" +
                        " BIENVENIDO A COUPCITO \n" +
                        " Usa: /registrar o /login para entrar.\n" +
                        "--------------------------------------------------"));
    }
    private void manejarIniciarPartida() {
        Sala sala = cliente.getSalaActual();
        if (sala == null) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "No estás en ninguna sala."));
            return;
        }
        if (sala.iniciarPartida(cliente)) {
            sala.broadcastSala(new Mensaje(Constantes.ESTADO, "La partida ha iniciado."));
        }
    }
    private void mostrarMenuPrincipal() {
        if (cliente.getSalaActual() == null) {
            String menu = "\n=== MENU ===\n" +
                    "/crear <2-6> [privada-publica]\n" +
                    "/unir <id_sala>\n" +
                    "/lista\n" +
                    "/salir";
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, menu));
        } else {
            StringBuilder sb = new StringBuilder("\n=== MENU SALA ===\n");

            sb.append("--- Jugadores en tu sala ---\n");
            for (HiloCliente jugador : cliente.getSalaActual().getJugadores()) {
                sb.append(" > ").append(jugador.getNombreJugador()).append("\n");
            }

            sb.append("--- Usuarios en Lobby (Disponibles) ---\n");
            boolean hayGente = false;
            for (HiloCliente c : ServidorCoup.clientesConectados) {
                if (c.isAutenticado() && c.getSalaActual() == null) {
                    sb.append(" * ").append(c.getNombreJugador()).append("\n");
                    hayGente = true;
                }
            }
            if (!hayGente) {
                sb.append(" (Nadie en el lobby)\n");
            }
            sb.append("---------------------------------------\n");

            sb.append("/salir_sala\n");
            sb.append("/iniciar\n");

            if (cliente.getSalaActual().isEsPrivada()) {
                sb.append("/invitar <usuario>\n");
            }
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, sb.toString()));
        }
    }
}