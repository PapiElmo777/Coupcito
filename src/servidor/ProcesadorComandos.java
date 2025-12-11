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

        if (bloquearPorRestricciones(comando)) {
            return;
        }

        switch (comando) {
            // --- GESTIÓN DE CUENTA Y SALAS ---
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

            // --- ACCIONES SIMPLES (Sin desafío) ---
            case "/tomar_moneda":
                tomarUnaMoneda();
                break;
            case "/dos_monedas":
                tomarDos();
                break;
            case "/coupear":
                coupear(partes);
                break;

            // --- ACCIONES DESAFIABLES (MENTIRAS) ---
       
            case "/soy_duque": 
                tomarTres();
                break;
            case "/robar":     
                robar(partes);
                break;
            case "/asesinar":  
                asesinar(partes);
                break;
            case "/embajador": 
                iniciarEmbajador();
                break;

            // --- RESOLUCIÓN DE ESTADOS ---
            case "/continuar": // Confirmar que nadie desafía
                manejarContinuar();
                break;
            case "/desafiar":  // Desafiar una mentira
                manejarDesafio(partes);
                break;
            case "/bloquear":  // Bloqueo de Condesa
                manejarBloqueoCondesa();
                break;
            case "/aceptar":   // Aceptar muerte por Asesino
                manejarAceptarMuerte();
                break;
            case "/seleccionar": // Selección de cartas Embajador
                finalizarEmbajador(partes);
                break;

            default:
                cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Comando desconocido."));
        }
    }

    
    //   ACCIONES INICIALES 


    private void tomarUnaMoneda() {
        if (!verificarTurno()) return;
        cliente.sumarMonedas(1);
        Sala sala = cliente.getSalaActual();
        sala.broadcastSala(new Mensaje(Constantes.ACCION, ">> " + cliente.getNombreJugador() + " tomó 1 moneda."));
        enviarEstadoActualizado(cliente);
        sala.siguienteTurno();
    }

    private void tomarDos() {
        if (!verificarTurno()) return;
        cliente.sumarMonedas(2);
        Sala sala = cliente.getSalaActual();
        sala.broadcastSala(new Mensaje(Constantes.ACCION, ">> " + cliente.getNombreJugador() + " tomó 2 monedas (Ayuda Exterior)."));
        enviarEstadoActualizado(cliente);
        sala.siguienteTurno();
    }

    private void tomarTres() {
        if (!verificarTurno()) return;
        Sala sala = cliente.getSalaActual();

        sala.setJugadorAtacante(cliente);
        sala.setCartaRequerida(Constantes.DUQUE);
        sala.setAccionPendiente("TOMAR_3");
        sala.setEsperandoDesafio(true);

        sala.broadcastSala(new Mensaje(Constantes.ACCION, 
            ">> " + cliente.getNombreJugador() + " dice ser DUQUE y quiere tomar 3 monedas.\n" +
            "   Usa /desafiar o el jugador OBJETIVO/OTROS usa /continuar si nadie desafía."));
    }

    private void robar(String[] partes) {
        if (!verificarTurno()) return;
        if (partes.length < 2) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Uso: /robar <jugador>"));
            return;
        }
        Sala sala = cliente.getSalaActual();
        HiloCliente victima = buscarObjetivo(sala, partes[1]);

        if (victima == null || !victima.isEstaVivo() || victima == cliente) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Objetivo inválido."));
            return;
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

    private void asesinar(String[] partes) {
        if (!verificarTurno()) return;
        Sala sala = cliente.getSalaActual();

        if (sala.isEsperandoBloqueo()) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Hay una acción pendiente de resolución."));
            return;
        }
        if (cliente.getMonedas() < 3) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Necesitas 3 monedas. Tienes: " + cliente.getMonedas()));
            return;
        }
        if (partes.length < 2) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Uso: /asesinar <nombre_jugador>"));
            return;
        }

        HiloCliente victima = buscarObjetivo(sala, partes[1]);
        if (victima == null || !victima.isEstaVivo() || victima == cliente) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Objetivo inválido."));
            return;
        }

        // Se pagan las monedas al anunciar
        cliente.sumarMonedas(-3);

        sala.setJugadorAtacante(cliente);
        sala.setJugadorObjetivo(victima);
        sala.setCartaRequerida(Constantes.ASESINO);
        sala.setAccionPendiente("ASESINAR");
        sala.setEsperandoDesafio(true);

        sala.broadcastSala(new Mensaje(Constantes.ACCION, 
             ">> " + cliente.getNombreJugador() + " paga 3 y dice ser ASESINO contra " + victima.getNombreJugador() + ".\n" +
             "   ¿Alguien duda que sea Asesino? (/desafiar) o (/continuar)"));
        
        enviarEstadoActualizado(cliente);
    }

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

    private void coupear(String[] partes) {
        if (!verificarTurno()) return;
        Sala sala = cliente.getSalaActual();
        if (cliente.getMonedas() < 7) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Necesitas 7 monedas para coupear."));
            return;
        }
        if (partes.length < 2) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Uso: /coupear <nombre_jugador>"));
            return;
        }
        HiloCliente victima = buscarObjetivo(sala, partes[1]);
        if (victima == null || !victima.isEstaVivo() || victima == cliente) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Objetivo inválido."));
            return;
        }

        cliente.sumarMonedas(-7);
        sala.broadcastSala(new Mensaje(Constantes.ACCION, ">> ¡" + cliente.getNombreJugador() + " hizo un COUP a " + victima.getNombreJugador() + "!"));
        
        String cartaPerdida = victima.getCartasEnMano().get(0);
        victima.perderCarta(cartaPerdida);
        sala.broadcastSala(new Mensaje(Constantes.ESTADO, ">> " + victima.getNombreJugador() + " perdió su carta: " + cartaPerdida));
        
        if (!victima.isEstaVivo()) {
            sala.broadcastSala(new Mensaje(Constantes.ESTADO, ">> " + victima.getNombreJugador() + " ha sido ELIMINADO."));
        }
        enviarEstadoActualizado(cliente);
        enviarEstadoActualizado(victima);
        sala.siguienteTurno();
    }

    
    //   LÓGICA DE DESAFÍOS
    
    private void manejarContinuar() {
        Sala sala = cliente.getSalaActual();
        if (sala == null || !sala.isEsperandoDesafio()) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "No hay ninguna acción pausada para continuar."));
            return;
        }
        
        // El atacante NO puede auto-confirmarse. Debe esperar a los demás.
        if (sala.getJugadorAtacante().equals(cliente)) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "✋ Tú eres el atacante. Espera a que los demás confirmen con /continuar o te desafíen."));
            return;
        }

        sala.broadcastSala(new Mensaje(Constantes.ESTADO, ">> " + cliente.getNombreJugador() + " permite continuar (Nadie desafió)."));
        ejecutarAccionPendiente(sala);
    }

    private void manejarDesafio(String[] partes) {
        Sala sala = cliente.getSalaActual();
        if (sala == null || !sala.isEsperandoDesafio()) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "No hay nada que desafiar ahora."));
            return;
        }
        
        HiloCliente acusado = sala.getJugadorAtacante(); 
        HiloCliente retador = cliente; 

        if (acusado.equals(retador)) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "No puedes desafiarte a ti mismo."));
            return;
        }

        String cartaReclamada = sala.getCartaRequerida();
        boolean tieneLaCarta = acusado.getCartasEnMano().contains(cartaReclamada);

        sala.broadcastSala(new Mensaje(Constantes.ACCION, "!!! DESAFÍO !!! " + retador.getNombreJugador() + " desafía a " + acusado.getNombreJugador()));

        if (tieneLaCarta) {
            // --- EL ACUSADO DICE LA VERDAD ---
            aplicarCastigo(retador, sala); // Retador pierde vida
            
            // Cambiamos la carta manualmente para que no muera si tiene 1 vida
            sala.broadcastSala(new Mensaje(Constantes.ESTADO, ">> " + acusado.getNombreJugador() + " ENSEÑA LA CARTA: " + cartaReclamada));
            
            acusado.getCartasEnMano().remove(cartaReclamada); 
            sala.devolverCartaAlMazo(cartaReclamada);
            String nueva = sala.tomarCartaDelMazo();
            acusado.agregarCarta(nueva);
            
            sala.broadcastSala(new Mensaje(Constantes.ESTADO, ">> " + acusado.getNombreJugador() + " baraja y toma carta nueva."));

         
            ejecutarAccionPendiente(sala);

        } else {
            // --- EL ACUSADO MENTÍA ---
            aplicarCastigo(acusado, sala); // Acusado pierde vida

            sala.broadcastSala(new Mensaje(Constantes.ESTADO, 
                ">> ¡CACHADO! " + acusado.getNombreJugador() + " NO tenía " + cartaReclamada + ".\n" +
                ">> La acción ha sido CANCELADA."));

            // Limpieza
            sala.limpiarEstadoDesafio();
            limpiarEstadoAsesinato(sala); 
            
            // Como la acción falló, debemos pasar el turno AQUÍ manualmente
            sala.siguienteTurno();
        }
    }

    private void aplicarCastigo(HiloCliente perdedor, Sala sala) {
        if (perdedor.getCartasEnMano().isEmpty()) return;
        // Pierde la primera carta automáticamente
        String cartaPerdida = perdedor.getCartasEnMano().get(0);
        perdedor.perderCarta(cartaPerdida);
        sala.devolverCartaAlMazo(cartaPerdida); 
        
        sala.broadcastSala(new Mensaje(Constantes.ESTADO, ">> " + perdedor.getNombreJugador() + " ha perdido la carta: " + cartaPerdida));
        
        if (!perdedor.isEstaVivo()) {
            sala.broadcastSala(new Mensaje(Constantes.ESTADO, ">> JUGADOR ELIMINADO: " + perdedor.getNombreJugador()));
        }
        enviarEstadoActualizado(perdedor);
    }

    private void ejecutarAccionPendiente(Sala sala) {
        String accion = sala.getAccionPendiente();
        sala.limpiarEstadoDesafio(); 

        if (accion == null) return; 

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
        }
    }

   
    private void ejecutarTomarTres(HiloCliente actor) {
        actor.sumarMonedas(3);
        actor.getSalaActual().broadcastSala(new Mensaje(Constantes.ACCION, ">> " + actor.getNombreJugador() + " toma 3 monedas (Duque exitoso)."));
        enviarEstadoActualizado(actor);
        actor.getSalaActual().siguienteTurno();
    }

    private void ejecutarRobar(HiloCliente ladron, HiloCliente victima) {
        int monto = 2;
        if (victima.getMonedas() < 2) monto = victima.getMonedas();
        if (monto > 0) {
            victima.sumarMonedas(-monto);
            ladron.sumarMonedas(monto);
            ladron.getSalaActual().broadcastSala(new Mensaje(Constantes.ACCION, ">> Robo exitoso: " + monto + " monedas transferidas."));
        }
        enviarEstadoActualizado(ladron);
        enviarEstadoActualizado(victima);
        ladron.getSalaActual().siguienteTurno();
    }

    private void ejecutarAsesinar(HiloCliente asesino, HiloCliente victima) {
        Sala sala = asesino.getSalaActual();
        // El desafío del Asesino pasó, ahora activamos bloqueo de Condesa
        sala.setEsperandoBloqueo(true); 
        sala.setMonedasEnJuego(3);
        
        sala.broadcastSala(new Mensaje(Constantes.ACCION,
            ">> ¡Nadie dudó del Asesino! " + victima.getNombreJugador() + ", ¿tienes a la Condesa?\n" +
            ">> /bloquear (si tienes Condesa) o /aceptar"));
            
       
    }

    private void ejecutarEmbajador(HiloCliente actor) {
        Sala sala = actor.getSalaActual();
        String c1 = sala.tomarCartaDelMazo();
        String c2 = sala.tomarCartaDelMazo();
        if (c1 != null && c2 != null) {
            actor.agregarCarta(c1);
            actor.agregarCarta(c2);
            StringBuilder sb = new StringBuilder();
            sb.append("--- INTERCAMBIO DE EMBAJADOR ---\n");
            sb.append("Has tomado: ").append(c1).append(" y ").append(c2).append("\n");
            sb.append("Usa: /seleccionar <carta1> [carta2] para quedarte con ellas.");
            actor.enviarMensaje(new Mensaje(Constantes.ESTADO, sb.toString()));
        }
        // No pasamos turno, esperamos a /seleccionar
    }

    private void finalizarEmbajador(String[] partes) {
        if (!verificarTurno()) return;
        List<String> manoActual = cliente.getCartasEnMano();
        if (manoActual.size() <= 2) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "No estás en medio de un intercambio de Embajador."));
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

        sala.broadcastSala(new Mensaje(Constantes.ACCION, ">> " + cliente.getNombreJugador() + " ha cambiado sus cartas con el Embajador."));
        enviarEstadoActualizado(cliente);
        sala.siguienteTurno();
    }


    //   CONDESA

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
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "No hay ningún ataque que bloquear."));
            return;
        }
        if (!sala.getJugadorObjetivo().equals(cliente)) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "¡No te están atacando a ti!"));
            return;
        }
        HiloCliente asesino = sala.getJugadorAtacante();
        sala.broadcastSala(new Mensaje(Constantes.ACCION,
                ">> ¡" + cliente.getNombreJugador() + " ha bloqueado el asesinato porque es la Condesa!"));
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

   
    //   VALIDACIONES Y AUXILIARES
   

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
                                "Estás OBLIGADO a Coupear.\n" +
                                "Usa: /coupear <jugador>"));
                return true; 
            }
        }
        // Regla: Embajador (exceso de cartas)
        if (cliente.getCartasEnMano().size() > 2) {
            if (!comando.startsWith("/seleccionar")) {
                cliente.enviarMensaje(new Mensaje(Constantes.ESTADO,
                        "Debes usar /seleccionar <carta1> [carta2] para continuar el juego"));
                return true;
            }
        }
        return false;
    }

    private boolean verificarTurno() {
        Sala sala = cliente.getSalaActual();
        if (sala == null || !sala.isEnJuego()) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "No estás en una partida activa."));
            return false;
        }
        if (!sala.esTurnoDe(cliente)) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "¡No es tu turno!"));
            return false;
        }
        if (sala.isEsperandoBloqueo()) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Hay una acción pendiente de resolución (Asesinato). Espera."));
            return false;
        }
        if (sala.isEsperandoDesafio()) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Se está esperando un desafío o continuación."));
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

    private void enviarEstadoActualizado(HiloCliente j) {
        if (j.getSalaActual() != null && j.getSalaActual().isEnJuego()) {
            j.enviarMensaje(new Mensaje(Constantes.ESTADO,
                    "TUS DATOS | Monedas: " + j.getMonedas() + " | Cartas: " + j.getCartasEnMano()));
        }
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
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "No estás en ninguna sala."));
        }
    }

    private void manejarRegistro(String[] partes) {
        if (cliente.isAutenticado()) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Ya has iniciado sesión."));
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
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Usuario inválido (6-12 carácteres)."));
            return;
        }
        if (!Pattern.matches(REST_PASS, pass)) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Contraseña inválida (6-12 carácteres)."));
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
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Ya has iniciado sesión."));
            return;
        }
        if (partes.length < 3) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Uso: /login <usuario> <pass>"));
            return;
        }
        String user = partes[1];
        String pass = partes[2];

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
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Ya estás en una sala."));
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
        cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Sesión cerrada."));
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