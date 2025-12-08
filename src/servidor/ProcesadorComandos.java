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
                manejarCrearSala();
                break;
            case "/unir":
                manejarUnirse(partes);
                break;
            case "/salas":
                manejarListarSalas();
                break;
            case "/salir":
                manejarSalirSala();
                break;
            case "/iniciar":
                manejarIniciarPartida();
                break;
            default:
                cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "Comando desconocido."));
        }
    }
    private void manejarRegistro(String[] partes) {
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
        } else
            cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "El usuario ya existe."));
        }
    }
}
