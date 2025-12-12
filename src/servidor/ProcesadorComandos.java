package servidor;

import comun.Constantes;
import comun.Mensaje;
import java.util.ArrayList;
import java.util.List;

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

        // Bloqueo si tienes más de 10 monedas o cartas de embajador pendientes
        if (bloquearPorRestricciones(comando)) {
            return;
        }

        switch (comando) {
            // --- GESTIÓN DE CUENTA Y SALAS ---
            case "/registrar": manejarRegistro(partes); break;
            case "/login": manejarLogin(partes); break;
            case "/crear": manejarCrearSala(partes); break;
            case "/unir": manejarUnirse(partes); break;
            case "/lista": manejarListarSalas(); break;
            case "/salir": manejarSalir(); break;
            case "/salir_sala": manejarSalirDeSalaAlLobby(); break;
            case "/iniciar": manejarIniciarPartida(); break;

            // --- ACCIONES DIRECTAS (No desafiables) ---
            case "/tomar_moneda": tomarUnaMoneda(); break;
            case "/dos_monedas": tomarDos(); break;
            case "/coupear": coupear(partes); break;

            // --- ACCIONES CON ROL (Anuncios / Mentiras) ---
            // Estos métodos SOLO anuncian. No ejecutan la acción todavía.
            case "/soy_duque": anunciarDuque(); break;
            case "/robar": anunciarRobo(partes); break;
            case "/asesinar": anunciarAsesinato(partes); break;
            case "/embajador": anunciarEmbajador(); break;

            // --- RESOLUCIÓN DE DESAFÍOS ---
            case "/continuar": manejarContinuar(); break; // Confirmar acción
            case "/desafiar": manejarDesafio(partes); break; // Acusar mentira

            // --- RESPUESTAS ESPECÍFICAS (Condesa / Embajador / Muerte) ---
            case "/bloquear": manejarBloqueoCondesa(); break;
            case "/aceptar": manejarAceptarMuerte(); break;
            case "/seleccionar": finalizarEmbajador(partes); break;

            default:
                cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Comando desconocido."));
        }
    }

    private void anunciarDuque() {
        if (!verificarTurno()) return;
        Sala sala = cliente.getSalaActual();

        prepararEscenarioDesafio(sala, Constantes.DUQUE, "TOMAR_3", null);
        
        sala.broadcastSala(new Mensaje(Constantes.ACCION, 
            ">> " + cliente.getNombreJugador() + " dice ser DUQUE y quiere tomar 3 monedas.\n" +
            "   (Esperando: /desafiar o /continuar)"));
    }

    private void anunciarRobo(String[] partes) {
        if (!verificarTurno()) return;
        if (partes.length < 2) { cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Uso: /robar <jugador>")); return; }

        Sala sala = cliente.getSalaActual();
        HiloCliente victima = buscarObjetivo(sala, partes[1]);

        if (victima == null || !victima.isEstaVivo() || victima == cliente) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Objetivo inválido.")); return;
        }

        prepararEscenarioDesafio(sala, Constantes.CAPITAN, "ROBAR", victima);

        String msgGeneral = ">> " + cliente.getNombreJugador() + " dice ser CAPITAN y quiere robar a " + victima.getNombreJugador() + ".";

        for (HiloCliente j : sala.getJugadores()) {
            if (j.equals(victima)) {
                j.enviarMensaje(new Mensaje(Constantes.ACCION,
                        msgGeneral + "\n   ¿Dudas que sea Capitán? (/desafiar) o (/continuar)"));
            } else {
                j.enviarMensaje(new Mensaje(Constantes.ACCION,
                        msgGeneral + "\n   (Esperando: /desafiar o /continuar)"));
            }
        }
    }
//solo aparece el mensaje a las victimas
    private void anunciarAsesinato(String[] partes) {
        if (!verificarTurno()) return;
        if (cliente.getMonedas() < 3) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Necesitas 3 monedas."));
            return;
        }
        if (partes.length < 2) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Uso: /asesinar <jugador>"));
            return;
        }

        Sala sala = cliente.getSalaActual();
        HiloCliente victima = buscarObjetivo(sala, partes[1]);

        if (victima == null || !victima.isEstaVivo() || victima == cliente) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Objetivo inválido."));
            return;
        }
        cliente.sumarMonedas(-3);
        prepararEscenarioDesafio(sala, Constantes.ASESINO, "ASESINAR", victima);
        String msgGeneral = ">> " + cliente.getNombreJugador() + " paga 3 y dice ser ASESINO contra " + victima.getNombreJugador() + ".";

        for (HiloCliente j : sala.getJugadores()) {
            if (j.equals(victima)) {
                j.enviarMensaje(new Mensaje(Constantes.ACCION,
                        msgGeneral + "\n   ¿Dudas que sea Asesino? (/desafiar) o (/continuar)"));
            } else {
                j.enviarMensaje(new Mensaje(Constantes.ACCION,
                        msgGeneral + "\n   (Esperando: /desafiar o /continuar)"));
            }
        }

        enviarEstadoActualizado(cliente);
    }

    private void anunciarEmbajador() {
        if (!verificarTurno()) return;
        Sala sala = cliente.getSalaActual();

        prepararEscenarioDesafio(sala, Constantes.EMBAJADOR, "EMBAJADOR", null);

        sala.broadcastSala(new Mensaje(Constantes.ACCION, 
            ">> " + cliente.getNombreJugador() + " dice ser EMBAJADOR.\n" +
            "   (Esperando: /desafiar o /continuar)"));
    }

    private void prepararEscenarioDesafio(Sala sala, String carta, String accion, HiloCliente objetivo) {
        sala.setJugadorAtacante(cliente);
        sala.setJugadorObjetivo(objetivo);
        sala.setCartaRequerida(carta);
        sala.setAccionPendiente(accion);
        sala.setEsperandoDesafio(true);
    }

    //   RESOLUCIÓN DE DESAFÍOS Y CONTINUACIÓN

    private void manejarContinuar() {
        Sala sala = cliente.getSalaActual();
        if (sala == null || !sala.isEsperandoDesafio()) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "No hay acción pausada."));
            return;
        }

        // FIX: El atacante NO puede validarse a sí mismo
        if (sala.getJugadorAtacante().equals(cliente)) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "✋ Tú eres el atacante. Espera a que los demás confirmen."));
            return;
        }

        sala.broadcastSala(new Mensaje(Constantes.ESTADO, ">> " + cliente.getNombreJugador() + " permite continuar (Nadie desafió)."));
        ejecutarAccionPendiente(sala);
    }

    private void manejarDesafio(String[] partes) {
        Sala sala = cliente.getSalaActual();
        if (sala == null || !sala.isEsperandoDesafio()) return;

        String accion = sala.getAccionPendiente();
        HiloCliente acusado;
        HiloCliente retador = cliente;

        if (accion.equals("BLOQUEO_CONDESA")) {
            acusado = sala.getJugadorObjetivo();
        } else {
            acusado = sala.getJugadorAtacante();
        }

        if (acusado.equals(retador)) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "No puedes desafiarte a ti mismo."));
            return;
        }

        String cartaReclamada = sala.getCartaRequerida();
        boolean tieneLaCarta = acusado.tieneCarta(cartaReclamada);

        if (tieneLaCarta) {
            aplicarCastigo(retador, sala);

            if (!retador.isEstaVivo()) {
                sala.broadcastSala(new Mensaje(Constantes.ESTADO, ">> El jugador " + retador.getNombreJugador() + " murió tras fallar el desafío."));

                acusado.perderCarta(cartaReclamada);
                sala.devolverCartaAlMazo(cartaReclamada);
                String nueva = sala.tomarCartaDelMazo();
                acusado.agregarCarta(nueva);
                acusado.enviarMensaje(new Mensaje(Constantes.ESTADO, "Carta cambiada. Nueva: " + nueva));

                limpiarEstadoAsesinato(sala);
                sala.limpiarEstadoDesafio();
                sala.siguienteTurno();
                return;
            }

            acusado.perderCarta(cartaReclamada);
            sala.devolverCartaAlMazo(cartaReclamada);
            String nueva = sala.tomarCartaDelMazo();
            acusado.agregarCarta(nueva);
            acusado.enviarMensaje(new Mensaje(Constantes.ESTADO, "Mostraste tu carta. Nueva carta: " + nueva));

            if (accion.equals("BLOQUEO_CONDESA")) {
                sala.broadcastSala(new Mensaje(Constantes.ESTADO, ">> El bloqueo es legítimo. El asesinato falla."));
                sala.siguienteTurno();
                limpiarEstadoAsesinato(sala);
            } else {
                ejecutarAccionPendiente(sala);
                if (!accion.equals("ASESINAR")) {
                    limpiarEstadoAsesinato(sala);
                }
            }

        } else {
            aplicarCastigo(acusado, sala);

            if (accion.equals("BLOQUEO_CONDESA")) {
                sala.broadcastSala(new Mensaje(Constantes.ESTADO, ">> ¡NO ERA CONDESA! El bloqueo falla."));
                aplicarCastigo(acusado, sala);
                sala.siguienteTurno();
            } else {
                sala.broadcastSala(new Mensaje(Constantes.ESTADO, ">> La acción ha sido CANCELADA por mentiroso."));
                sala.siguienteTurno();
            }
            limpiarEstadoAsesinato(sala);
        }
        sala.limpiarEstadoDesafio();
    }

    //   EJECUCIÓN REAL (Post-Validación)


    private void ejecutarAccionPendiente(Sala sala) {
        String accion = sala.getAccionPendiente();
        sala.limpiarEstadoDesafio();
        
        if (accion == null) return;

        switch (accion) {
            case "TOMAR_3":
                HiloCliente duque = sala.getJugadorAtacante();
                duque.sumarMonedas(3);
                sala.broadcastSala(new Mensaje(Constantes.ACCION, ">> " + duque.getNombreJugador() + " toma 3 monedas (Duque exitoso)."));
                enviarEstadoActualizado(duque);
                sala.siguienteTurno();
                break;

            case "ROBAR":
                HiloCliente ladron = sala.getJugadorAtacante();
                HiloCliente victima = sala.getJugadorObjetivo();
                int monto = 2;
                if (victima.getMonedas() < 2) monto = victima.getMonedas();
                
                if (monto > 0) {
                    victima.sumarMonedas(-monto);
                    ladron.sumarMonedas(monto);
                    sala.broadcastSala(new Mensaje(Constantes.ACCION, ">> Robo exitoso: " + ladron.getNombreJugador() + " roba " + monto + " a " + victima.getNombreJugador()));
                } else {
                    sala.broadcastSala(new Mensaje(Constantes.ACCION, ">> El robo falló porque la víctima no tiene dinero."));
                }
                enviarEstadoActualizado(ladron);
                enviarEstadoActualizado(victima);
                sala.siguienteTurno();
                break;

            case "ASESINAR":
                sala.setEsperandoBloqueo(true);
                HiloCliente target = sala.getJugadorObjetivo();
                HiloCliente attacker = sala.getJugadorAtacante();
                target.enviarMensaje(new Mensaje(Constantes.ACCION,
                        "¿Tienes a la Condesa?\n/bloquear (si tienes Condesa) o /aceptar"));
                for (HiloCliente j : sala.getJugadores()) {
                    if (!j.equals(target)) {
                        j.enviarMensaje(new Mensaje(Constantes.ESTADO,
                                ">> Esperando a ver si " + target.getNombreJugador() + " bloquea o acepta la muerte..."));
                    }
                }
                break;

            case "BLOQUEO_CONDESA":
                sala.broadcastSala(new Mensaje(Constantes.ESTADO,
                        ">> " + sala.getJugadorAtacante().getNombreJugador() + " acepto el bloqueo. El asesinato fallo."));
                sala.siguienteTurno();
                break;

            case "EMBAJADOR":
                HiloCliente embajador = sala.getJugadorAtacante();
                String c1 = sala.tomarCartaDelMazo();
                String c2 = sala.tomarCartaDelMazo();
                if (c1 != null && c2 != null) {
                    embajador.agregarCarta(c1);
                    embajador.agregarCarta(c2);
                    StringBuilder sb = new StringBuilder();
                    sb.append("--- INTERCAMBIO DE EMBAJADOR ---\n");
                    sb.append("Has tomado: ").append(c1).append(" y ").append(c2).append("\n");
                    sb.append("Usa: /seleccionar <carta1> [carta2] para quedarte con ellas.");
                    embajador.enviarMensaje(new Mensaje(Constantes.ESTADO, sb.toString()));
                }
                break;
        }
    }


    //  OTRAS MECÁNICAS DE JUEGO


    private void tomarUnaMoneda() {
        if (!verificarTurno()) return;
        cliente.sumarMonedas(1);
        cliente.getSalaActual().broadcastSala(new Mensaje(Constantes.ACCION, ">> " + cliente.getNombreJugador() + " tomó 1 moneda."));
        enviarEstadoActualizado(cliente);
        cliente.getSalaActual().siguienteTurno();
    }

    private void tomarDos() {
        if (!verificarTurno()) return;
        cliente.sumarMonedas(2);
        cliente.getSalaActual().broadcastSala(new Mensaje(Constantes.ACCION, ">> " + cliente.getNombreJugador() + " tomó 2 monedas (Ayuda Exterior)."));
        enviarEstadoActualizado(cliente);
        cliente.getSalaActual().siguienteTurno();
    }

    private void coupear(String[] partes) {
        if (!verificarTurno()) return;
        Sala sala = cliente.getSalaActual();
        if (cliente.getMonedas() < 7) { cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Necesitas 7 monedas.")); return; }
        if (partes.length < 2) { cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Uso: /coupear <jugador>")); return; }
        
        HiloCliente victima = buscarObjetivo(sala, partes[1]);
        if (victima == null || !victima.isEstaVivo() || victima == cliente) {
             cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Objetivo inválido.")); return;
        }

        cliente.sumarMonedas(-7);
        sala.broadcastSala(new Mensaje(Constantes.ACCION, ">> ¡" + cliente.getNombreJugador() + " hizo un COUP a " + victima.getNombreJugador() + "!"));
        aplicarCastigo(victima, sala); // El Coup es daño inevitable
        
        enviarEstadoActualizado(cliente);
        enviarEstadoActualizado(victima);
        sala.siguienteTurno();
    }

    private void finalizarEmbajador(String[] partes) {
        if (!verificarTurno()) return;
        List<String> manoActual = cliente.getCartasEnMano();
        int cartasAConservar = manoActual.size() - 2;
        
        if (cartasAConservar < 0) { 
             cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "No estás en modo Embajador.")); return;
        }
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

        sala.broadcastSala(new Mensaje(Constantes.ACCION, ">> " + cliente.getNombreJugador() + " completó el Embajador."));
        enviarEstadoActualizado(cliente);
        sala.siguienteTurno();
    }

    private void manejarBloqueoCondesa() {
        Sala sala = cliente.getSalaActual();
        if (!sala.isEsperandoBloqueo() || !sala.getJugadorObjetivo().equals(cliente)) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "No puedes bloquear ahora."));
            return;
        }
        sala.setEsperandoBloqueo(false);
        sala.setCartaRequerida(Constantes.CONDESA);
        sala.setAccionPendiente("BLOQUEO_CONDESA");
        sala.setEsperandoDesafio(true);
        HiloCliente asesino = sala.getJugadorAtacante();

        asesino.enviarMensaje(new Mensaje(Constantes.ACCION,
                ">> " + cliente.getNombreJugador() + " bloquea con Condesa.\n" +
                        "¿El jugador dice que es Condesa? (/desafiar) o (/continuar)"));
        for (HiloCliente j : sala.getJugadores()) {
            if (!j.equals(asesino)) {
                j.enviarMensaje(new Mensaje(Constantes.ESTADO,
                        ">> " + cliente.getNombreJugador() + " dice tener Condesa. Esperando si el Asesino desafía..."));
            }
        }
    }

    private void manejarAceptarMuerte() {
        Sala sala = cliente.getSalaActual();
        if (!sala.isEsperandoBloqueo() || !sala.getJugadorObjetivo().equals(cliente)) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "No tienes nada que aceptar."));
            return;
        }
        
        aplicarCastigo(cliente, sala);
        limpiarEstadoAsesinato(sala);
        sala.siguienteTurno();
    }
    private void aplicarCastigo(HiloCliente perdedor, Sala sala) {
        if (perdedor.getCartasEnMano().isEmpty()) return;
        String cartaPerdida = perdedor.getCartasEnMano().get(0);
        perdedor.perderCarta(cartaPerdida);
        sala.devolverCartaAlMazo(cartaPerdida);

        sala.broadcastSala(new Mensaje(Constantes.ESTADO, ">> " + perdedor.getNombreJugador() + " pierde una vida (" + cartaPerdida + ")."));
        if (!perdedor.isEstaVivo()) {
            String msgMuerte = "\n" +
                    "****************************************\n" +
                    "   JUGADOR " + perdedor.getNombreJugador() +  " ELIMINADO"+"\n" +
                    "****************************************\n";
            sala.broadcastSala(new Mensaje(Constantes.ESTADO, msgMuerte));

            perdedor.enviarMensaje(new Mensaje(Constantes.ESTADO, "Has sido eliminado. Ahora eres espectador."));
        }

        enviarEstadoActualizado(perdedor);
    }

    private boolean bloquearPorRestricciones(String comando) {
        Sala sala = cliente.getSalaActual();
        if (sala == null || !sala.isEnJuego()) {
            return false;
        }
        if (!sala.esTurnoDe(cliente)) {
            return false;
        }
        if (comando.equals("/salir") || comando.equals("/salir_sala") ||
                comando.equals("/chat") || comando.equals("/estado") ||
                comando.equals("/seleccionar")) {
            return false;
        }
        if (cliente.getMonedas() >= 10) {
            if (!comando.equals("/coupear")) {
                cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "¡Tienes 10+ monedas! Estás OBLIGADO a usar /coupear <jugador>."));
                return true;
            }
        }
        if (cliente.getCartasEnMano().size() > 2) {
            if (!comando.startsWith("/seleccionar")) {
                cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Tienes cartas de mas. Usa /seleccionar para devolverlas."));
                return true;
            }
        }
        return false;
    }

    private boolean verificarTurno() {
        Sala sala = cliente.getSalaActual();
        if (sala == null || !sala.isEnJuego()) { cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "No hay partida activa.")); return false; }
        if (!sala.esTurnoDe(cliente)) { cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "¡No es tu turno!")); return false; }
        if (sala.isEsperandoDesafio() || sala.isEsperandoBloqueo()) { cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Hay una acción pendiente. Espera.")); return false; }
        return true;
    }

    private HiloCliente buscarObjetivo(Sala sala, String nombre) {
        for (HiloCliente j : sala.getJugadores()) {
            if (j.getNombreJugador().equalsIgnoreCase(nombre)) return j;
        }
        return null;
    }

    private void limpiarEstadoAsesinato(Sala sala) {
        sala.setEsperandoBloqueo(false);
        sala.setJugadorAtacante(null);
        sala.setJugadorObjetivo(null);
        sala.setMonedasEnJuego(0);
    }

    private void enviarEstadoActualizado(HiloCliente j) {
        if (j.getSalaActual() != null && j.getSalaActual().isEnJuego()) {
            j.enviarMensaje(new Mensaje(Constantes.ESTADO, "TUS DATOS | Monedas: " + j.getMonedas() + " | Cartas: " + j.getCartasEnMano()));
        }
    }


    // GESTIÓN DE SALA Y LOBBY

    private void manejarRegistro(String[] p) {
        if (cliente.isAutenticado()) { cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Ya iniciaste sesión.")); return; }
        if (p.length != 4 || !p[2].equals(p[3])) { cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Uso: /registrar <uValido> <pValido> <pValido>")); return; }
        if (BaseDatos.registrarUsuario(p[1], p[2])) cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Registrado."));
        else cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Error al registrar."));
    }

    private void manejarLogin(String[] p) {
        if (cliente.isAutenticado()) { cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Ya conectado.")); return; }
        if (p.length < 3) return;
        if (ServidorCoup.buscarClientePorNombre(p[1]) != null) { cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Usuario ya conectado.")); return; }
        if (BaseDatos.validarLogin(p[1], p[2])) {
            cliente.setNombreJugador(p[1]);
            cliente.setAutenticado(true);
            ServidorCoup.clientesConectados.add(cliente);
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Bienvenido " + p[1]));
            mostrarMenuPrincipal();
        } else cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Login fallido."));
    }

    private void manejarCrearSala(String[] p) {
        if (!cliente.isAutenticado() || cliente.getSalaActual() != null) return;
        int cap = 6; boolean priv = false;
        if (p.length > 1 && p[1].matches("\\d+")) cap = Integer.parseInt(p[1]);
        if ((p.length > 1 && p[1].equalsIgnoreCase("privada")) || (p.length > 2 && p[2].equalsIgnoreCase("privada"))) priv = true;
        
        Sala s = GestorSalas.getInstancia().crearSala(cliente, cap, priv);
        cliente.setSalaActual(s);
        cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Sala creada ID: " + s.getId()));
        mostrarMenuPrincipal();
    }

    private void manejarUnirse(String[] p) {
        if (!cliente.isAutenticado() || cliente.getSalaActual() != null || p.length < 2) return;
        try {
            Sala s = GestorSalas.getInstancia().buscarSala(Integer.parseInt(p[1]));
            if (s != null && !s.isEsPrivada() && s.agregarJugador(cliente)) {
                cliente.setSalaActual(s);
                cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Unido a sala " + s.getId()));
                mostrarMenuPrincipal();
            } else cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "No se pudo unir."));
        } catch (Exception e) { cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "ID inválido.")); }
    }

    private void manejarListarSalas() {
        StringBuilder sb = new StringBuilder("Salas:\n");
        for (Sala s : GestorSalas.getInstancia().getSalas()) sb.append(s.toString()).append("\n");
        cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, sb.toString()));
    }

    private void manejarSalirDeSalaAlLobby() {
        if (cliente.getSalaActual() != null) {
            cliente.getSalaActual().removerJugador(cliente);
            if (cliente.getSalaActual().getJugadores().isEmpty()) GestorSalas.getInstancia().eliminarSala(cliente.getSalaActual());
            cliente.setSalaActual(null);
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Saliste de la sala."));
            mostrarMenuPrincipal();
        }
    }

    private void manejarSalir() {
        cliente.setAutenticado(false);
        cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Desconectado."));
    }

    private void manejarIniciarPartida() {
        if (cliente.getSalaActual() != null && cliente.getSalaActual().iniciarPartida(cliente)) {
            cliente.getSalaActual().broadcastSala(new Mensaje(Constantes.ESTADO, "Partida iniciada."));
        }
    }

    private void mostrarMenuPrincipal() {
        if (cliente.getSalaActual() == null) {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "\n=== MENU ===\n/crear <2-6>\n/unir <id>\n/lista\n/salir"));
        } else {
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "\n=== EN SALA ===\n/iniciar\n/salir_sala"));
        }
    }
}