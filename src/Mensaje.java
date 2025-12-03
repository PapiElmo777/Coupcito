import java.io.Serializable;

public class Mensaje implements Serializable {
    private static final long serialVersionUID = 1L;

    public String tipo;
    public Object contenido;

    public Mensaje() {
    }
    public Mensaje(String tipo, Object contenido){
        this.tipo = tipo;
        this.contenido = contenido;
    }

    @Override
    public String toString() {
        return "Mensaje{" + "tipo='" + tipo + '\'' + ", contenido=" + contenido + '}';
    }
}
