package servidor;

import java.sql.*;

public class BaseDatos {
    private static Connection conn;

    public static void conectar() {
        try {
            String url = "jdbc:sqlite:clientes.db";
            conn = DriverManager.getConnection(url);
            crearTablas();
            System.out.println(">> Base de Datos conectada correctamente.");
        } catch (SQLException e) {
            System.out.println(">> Error crítico en BD: " + e.getMessage());
        }
    }

    private static void crearTablas() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS usuarios ("
                + " nombre TEXT PRIMARY KEY,"
                + " password TEXT NOT NULL,"
                + " victorias INTEGER DEFAULT 0"
                + ");";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }
    public static synchronized boolean registrarUsuario(String usuario, String password) {
        String sql = "INSERT INTO usuarios(nombre, password) VALUES(?,?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, usuario);
            pstmt.setString(2, password);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }
}