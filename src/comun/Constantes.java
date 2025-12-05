package comun;

public class Constantes {
    public static final int PUERTO = 8080;

    // Tipos de Mensajes
    public static final String LOGIN = "LOGIN";         // del cliente al Servidor
    public static final String MENSAJE = "TEXTO";       // mensajes de sistema o chat
    public static final String ESTADO = "ESTADO";       // del servidor al cliente
    public static final String ACCION = "ACCION";       // del cliente al servidor
    public static final String TURNO = "TURNO";         // del servidor  cliente

    // Roles del Juego
    public static final String DUQUE = "Duque";
    public static final String ASESINO = "Asesino";
    public static final String CAPITAN = "Capitan";
    public static final String EMBAJADOR = "Embajador";
    public static final String CONDESA = "Condesa";

    // Acciones
    public static final String ACCION_INGRESOS = "Toma una";       // 1 moneda
    public static final String ACCION_AYUDA = "Toma dos";    // 2 monedas
    public static final String ACCION_IMPUESTOS = "Toma tres";     // 3 monedas (Duque)
    public static final String ACCION_ASESINAR = "Asesinar";       // -3 monedas (Asesino)
    public static final String ACCION_ROBAR = "Robar";             // Robar 2 (Capitán)
    public static final String ACCION_CAMBIO = "Cambio";           // Cambiar cartas (Embajador)
    public static final String ACCION_GOLPE = "Coup";   // 7 monedas
}