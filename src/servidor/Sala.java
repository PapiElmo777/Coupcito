package servidor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Sala implements Serializable {
    private static final long serialVersionUID = 1L;
    private static int contadorIds = 1;

    private int id;
    private int capacidadMaxima;
    private boolean esPrivada;
    private boolean enJuego;
    private String nombreAdmin;

   
   
}