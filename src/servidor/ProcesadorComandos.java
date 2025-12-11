package servidor;

import comun.Constantes;
import comun.Mensaje;
import java.util.ArrayList;
import java.util.List;
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

        // Si esta función devuelve true, es que hay algo bloqueando (ej. tienes 10 monedas)
        // y no debemos procesar nada más.
        if (bloquearPorRestricciones(comando)) {
            return;
        }

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
            //comandos juego
            case "/tomar_moneda":
                tomarUnaMoneda();
                break;
            case "/dos_monedas":
                tomarDos(); 
                break;
            case "/soy_duque"://duque
                tomarTres();
                break;
            case "/coupear":
                coupear(partes);
                break;
            case "/robar"://capitan
                robar(partes);
                break;
            case "/asesinar"://asesina
                asesinar(partes);
                break;
            case "/embajador"://embajador
                iniciarEmbajador();
                break;
            case "/seleccionar"://embajador
                finalizarEmbajador(partes);
                break;
                //condesa
            case "/bloquear":
                manejarBloqueoCondesa();
                break;
            case "/aceptar":
                manejarAceptarMuerte();
                break;
                case "/continuar":
                manejarContinuar();
                break;
            case "/desafiar":
                manejarDesafio(partes);
                break;
            default:
                cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Comando desconocido."));
        }
    }

   
    private boolean bloquearPorRestricciones(String comando) {
        // Regla: Obligado a Coupear con 10 o más monedas
        if (cliente.getMonedas() >= 10) {
            boolean esComandoPermitido = comando.equals("/coupear") ||
                    comando.equals("/estado") ||
                    comando.equals("/salir") ||
                    comando.equals("/salir_sala");

            if (!esComandoPermitido) {
                cliente.enviarMensaje(new Mensaje(Constantes.ESTADO,
                        "¡TIENES " + cliente.getMonedas() + " MONEDAS! \n" +
                                "Estas OBLIGADO a Coupear shavalon.\n" +
                                "Usa: /coupear <jugador>"));
                return true; // Bloquea el resto del proceso
            }
        }
        // Regla: Embajador (exceso de cartas)
        if (cliente.getCartasEnMano().size() > 2) {
            if (!comando.startsWith("/seleccionar")) {
                cliente.enviarMensaje(new Mensaje(Constantes.ESTADO,
                        "Debes usar /seleccionar <carta1> [carta2] para continuar el juego"));
                return true; // Bloquea el resto del proceso
            }
        }
        return false; // No hay bloqueos, puede continuar
    }

    

    private void enviarEstadoActualizado(HiloCliente j) {
        if (j.getSalaActual() != null && j.getSalaActual().isEnJuego()) {
            j.enviarMensaje(new Mensaje(Constantes.ESTADO,
                    "TUS DATOS | monedas: " + cliente.getMonedas() + " | Cartas: " + cliente.getCartasEnMano()));
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
        enviarEstadoActualizado(cliente);
        enviarEstadoActualizado(victima);
        sala.siguienteTurno();
    }

    private void tomarUnaMoneda() {
        if (!verificarTurno()) return;
        cliente.sumarMonedas(1);
        Sala sala = cliente.getSalaActual();
        sala.broadcastSala(new Mensaje(Constantes.ACCION, ">> " + cliente.getNombreJugador() + " tomo 1 moneda."));
        enviarEstadoActualizado(cliente);
        sala.siguienteTurno();
    }

    private void tomarDos() {
        if (!verificarTurno()) return;
        cliente.sumarMonedas(2);
        Sala sala = cliente.getSalaActual();
        sala.broadcastSala(new Mensaje(Constantes.ACCION, ">> " + cliente.getNombreJugador() + " tomo dos monedas(+2 monedas)."));
        enviarEstadoActualizado(cliente);
        sala.siguienteTurno();
    }
    //duque
   private void tomarTres() {
        if (!verificarTurno()) return;
        Sala sala = cliente.getSalaActual();
        
        
        sala.setJugadorAtacante(cliente);
        sala.setCartaRequerida(Constantes.DUQUE);
        sala.setAccionPendiente("TOMAR_3");
        sala.setEsperandoDesafio(true);
        
        sala.broadcastSala(new Mensaje(Constantes.ACCION, 
            ">> " + cliente.getNombreJugador() + " dice ser DUQUE y quiere tomar 3 monedas.\n" +
            "   Usa /desafiar o el jugador activo usa /continuar si nadie desafía."));
    }
    //capitan
    private void robar(String[] partes) {
        if (!verificarTurno()) return;
        if (partes.length < 2) return; 
        
        Sala sala = cliente.getSalaActual();
        HiloCliente victima = buscarObjetivo(sala, partes[1]);
        if (victima == null || !victima.isEstaVivo() || victima == cliente) {
             cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Objetivo inválido.")); return;
        }

       
        sala.setJugadorAtacante(cliente);
        sala.setJugadorObjetivo(victima);
        sala.setCartaRequerida(Constantes.CAPITAN);
        sala.setAccionPendiente("ROBAR");
        sala.setEsperandoDesafio(true);

        sala.broadcastSala(new Mensaje(Constantes.ACCION, 
            ">> " + cliente.getNombreJugador() + " dice ser CAPITAN y quiere robar a " + victima.getNombreJugador() + ".\n" +
            "   Usa /desafiar o /continuar."));
    }

    private void ejecutarRobar(HiloCliente ladron, HiloCliente victima) {
        int monto = 2;
        if (victima.getMonedas() < 2) monto = victima.getMonedas();
        if (monto > 0) {
            victima.sumarMonedas(-monto);
            ladron.sumarMonedas(monto);
            ladron.getSalaActual().broadcastSala(new Mensaje(Constantes.ACCION, ">> Robo exitoso: " + monto + " monedas."));
        }
        enviarEstadoActualizado(ladron);
        enviarEstadoActualizado(victima);
        ladron.getSalaActual().siguienteTurno();
    }
   
    private void asesinar(String[] partes) {
        if (!verificarTurno()) return;
        if (cliente.getMonedas() < 3) {
             cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Faltan monedas (3).")); return;
        }
        Sala sala = cliente.getSalaActual();
        HiloCliente victima = buscarObjetivo(sala, partes.length > 1 ? partes[1] : "");
        if (victima == null) return;

    
        cliente.sumarMonedas(-3); 

        sala.setJugadorAtacante(cliente);
        sala.setJugadorObjetivo(victima);
        sala.setCartaRequerida(Constantes.ASESINO);
        sala.setAccionPendiente("ASESINAR");
        sala.setEsperandoDesafio(true);

        sala.broadcastSala(new Mensaje(Constantes.ACCION, 
             ">> " + cliente.getNombreJugador() + " paga 3 y dice ser ASESINO contra " + victima.getNombreJugador() + ".\n" +
             "   ¿Alguien duda que sea Asesino? (/desafiar) o (/continuar)"));
    }
    private void ejecutarAsesinar(HiloCliente asesino, HiloCliente victima) {
        
        Sala sala = asesino.getSalaActual();
        sala.setEsperandoBloqueo(true); 
        sala.setMonedasEnJuego(3);
        
        sala.broadcastSala(new Mensaje(Constantes.ACCION,
            ">> ¡El Asesinato procede! " + victima.getNombreJugador() + ", ¿tienes a la Condesa?\n" +
            ">> /bloquear (si tienes Condesa/mientes) o /aceptar"));
    }
    //embajador
    private void iniciarEmbajador() {
         if (!verificarTurno()) return;
         Sala sala = cliente.getSalaActual();
         
         sala.setJugadorAtacante(cliente);
         sala.setCartaRequerida(Constantes.EMBAJADOR);
         sala.setAccionPendiente("EMBAJADOR");
         sala.setEsperandoDesafio(true);

         sala.broadcastSala(new Mensaje(Constantes.ACCION, 
             ">> " + cliente.getNombreJugador() + " dice ser EMBAJADOR.\n" +
             "   Usa /desafiar o /continuar."));
    }
    private void ejecutarEmbajador(HiloCliente actor) {
  
        Sala sala = actor.getSalaActual();
        String c1 = sala.tomarCartaDelMazo();
        String c2 = sala.tomarCartaDelMazo();
        if (c1 != null && c2 != null) {
            actor.agregarCarta(c1);
            actor.agregarCarta(c2);
            actor.enviarMensaje(new Mensaje(Constantes.ESTADO, "Selecciona cartas a conservar con /seleccionar ..."));
          
        }
    }


    private void finalizarEmbajador(String[] partes) {
        if (!verificarTurno()) return;
        List<String> manoActual = cliente.getCartasEnMano();
        if (manoActual.size() <= 2) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "No estas en medio de un intercambio de Embajador."));
            return;
        }
        int cartasAConservar = manoActual.size() - 2;
        if (partes.length - 1 != cartasAConservar) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Debes seleccionar exactamente " + cartasAConservar + " carta(s)."));
            return;
        }
        List<String> nuevaMano = new ArrayList<>();
        List<String> copiaMano = new ArrayList<>(manoActual);

        for (int i = 1; i < partes.length; i++) {
            String cartaElegida = partes[i];
            boolean encontrada = false;
            for (String c : copiaMano) {
                if (c.equalsIgnoreCase(cartaElegida)) {
                    nuevaMano.add(c);
                    copiaMano.remove(c);
                    encontrada = true;
                    break;
                }
            }
            if (!encontrada) {
                cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "No tienes la carta: " + cartaElegida));
                return;
            }
        }
        Sala sala = cliente.getSalaActual();
        for (String sobrante : copiaMano) {
            sala.devolverCartaAlMazo(sobrante);
        }
        cliente.getCartasEnMano().clear();
        cliente.getCartasEnMano().addAll(nuevaMano);

        sala.broadcastSala(new Mensaje(Constantes.ACCION, ">> " + cliente.getNombreJugador() + " ha cabiado sus cartas con el Embajador."));
        enviarEstadoActualizado(cliente);
        sala.siguienteTurno();
    }
    //condesa
    private void manejarAceptarMuerte() {
        Sala sala = cliente.getSalaActual();
        if (!sala.isEsperandoBloqueo() || !sala.getJugadorObjetivo().equals(cliente)) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "No tienes nada que aceptar."));
            return;
        }
        HiloCliente victima = cliente;
        String cartaPerdida = victima.getCartasEnMano().get(0);
        victima.perderCarta(cartaPerdida);
        sala.broadcastSala(new Mensaje(Constantes.ACCION, ">> " + victima.getNombreJugador() + " acepta su destino y pierde: " + cartaPerdida));
        if (!victima.isEstaVivo()) {
            sala.broadcastSala(new Mensaje(Constantes.ESTADO, ">> " + victima.getNombreJugador() + " ha sido ELIMINADO."));
        }
        enviarEstadoActualizado(victima);
        limpiarEstadoAsesinato(sala);
        sala.siguienteTurno();
    }

    private void manejarBloqueoCondesa() {
        Sala sala = cliente.getSalaActual();
        if (!sala.isEsperandoBloqueo()) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "No hay ningun ataque que bloquear."));
            return;
        }
        if (!sala.getJugadorObjetivo().equals(cliente)) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "¡No te están atacando a ti, metiche!"));
            return;
        }
        HiloCliente asesino = sala.getJugadorAtacante();
        sala.broadcastSala(new Mensaje(Constantes.ACCION,
                ">> ¡" + cliente.getNombreJugador() + " ha bloqueado el asesinato porque es la condesa!"));
        sala.broadcastSala(new Mensaje(Constantes.ESTADO,
                ">> El asesinato ha fallado. " + asesino.getNombreJugador() + " pierde sus 3 monedas."));
        limpiarEstadoAsesinato(sala);
        sala.siguienteTurno();
    }

    private void limpiarEstadoAsesinato(Sala sala) {
        sala.setEsperandoBloqueo(false);
        sala.setJugadorAtacante(null);
        sala.setJugadorObjetivo(null);
        sala.setMonedasEnJuego(0);
    }

    private boolean verificarTurno() {
        Sala sala = cliente.getSalaActual();
        if (sala == null || !sala.isEnJuego()) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "No estas en una partida activa."));
            return false;
        }
        if (!sala.esTurnoDe(cliente)) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "¡No es tu turno! Espera, tramposo."));
            return false;
        }
        if (sala.isEsperandoBloqueo()) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Hay una accion pendiente de resolución (Asesinato). Espera."));
            return false;
        }
        return true;
    }

    private HiloCliente buscarObjetivo(Sala sala, String nombreObjetivo) {
        for (HiloCliente j : sala.getJugadores()) {
            if (j.getNombreJugador().equalsIgnoreCase(nombreObjetivo)) {
                return j;
            }
        }
        return null;
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

        // Verificar si ya está conectado 
        if (ServidorCoup.buscarClientePorNombre(user) != null) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Error: El usuario '" + user + "' ya está conectado."));
            return;
        }
        

        if (BaseDatos.validarLogin(user, pass)) {
            cliente.setNombreJugador(user);
            cliente.setAutenticado(true);
            
           
            ServidorCoup.clientesConectados.add(cliente);
          
            
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

            // Validar privacidad ---
            if (s.isEsPrivada()) {
                cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Esta sala es PRIVADA. Solo puedes entrar por invitación."));
                return;
            }
      

            if (s.agregarJugador(cliente)) {
                cliente.setSalaActual(s);
                cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Te has unido a la sala #" + s.getId()));
                mostrarMenuPrincipal();
            } else {
                cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "No puedes unirte (Sala llena o en juego)."));
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
    private void manejarContinuar() {
        Sala sala = cliente.getSalaActual();
        if (sala == null || !sala.isEsperandoDesafio()) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "No hay ninguna acción pausada para continuar."));
            return;
        }
        
        // Solo el jugador que está actuando debería poder forzar el continuar 
        // (o podrías dejar que cualquiera lo haga si todos dicen "paso")
        if (!sala.getJugadorAtacante().equals(cliente)) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Solo el jugador activo puede confirmar para continuar si nadie desafía."));
            return;
        }

        sala.broadcastSala(new Mensaje(Constantes.ESTADO, ">> Nadie desafió. Se ejecuta la acción de " + cliente.getNombreJugador()));
        ejecutarAccionPendiente(sala);
    }

    // Este método centraliza la ejecución real después de pasar el desafío
    private void ejecutarAccionPendiente(Sala sala) {
        String accion = sala.getAccionPendiente();
        sala.limpiarEstadoDesafio(); 
        
        switch (accion) {
            case "TOMAR_3":
                ejecutarTomarTres(sala.getJugadorAtacante());
                break;
            case "ROBAR":
                ejecutarRobar(sala.getJugadorAtacante(), sala.getJugadorObjetivo());
                break;
            case "ASESINAR":
                ejecutarAsesinar(sala.getJugadorAtacante(), sala.getJugadorObjetivo());
                break;
            case "EMBAJADOR":
                ejecutarEmbajador(sala.getJugadorAtacante());
                break;
            case "BLOQUEO_CONDESA": 
            
                 sala.broadcastSala(new Mensaje(Constantes.ESTADO, ">> El bloqueo fue exitoso. El asesinato se cancela."));
                 sala.setEsperandoBloqueo(false);
                 sala.siguienteTurno();
                 break;
        }

        
    }
    
    private void ejecutarTomarTres(HiloCliente actor) {
        actor.sumarMonedas(3);
        actor.getSalaActual().broadcastSala(new Mensaje(Constantes.ACCION, ">> " + actor.getNombreJugador() + " toma 3 monedas (Duque exitoso)."));
        enviarEstadoActualizado(actor);
        actor.getSalaActual().siguienteTurno();
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