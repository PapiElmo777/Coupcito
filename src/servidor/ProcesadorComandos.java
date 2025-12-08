package servidor;

import comun.Constantes;
import comun.Mensaje;

public class ProcesadorComandos {
    private HiloCliente cliente;

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
                cliente.enviarMensaje(new Mensaje(Constantes.ESTADO, "❌ Comando desconocido."));
        }
    }
}
