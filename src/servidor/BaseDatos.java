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

    public static boolean validarLogin(String usuario, String password) {
        String sql = "SELECT password FROM usuarios WHERE nombre = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, usuario);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("password").equals(password);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // MÉTODOS PARA RANKING Y VICTORIAS ---

    public static synchronized void incrementarVictoria(String usuario) {
        String sql = "UPDATE usuarios SET victorias = victorias + 1 WHERE nombre = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, usuario);
            pstmt.executeUpdate();
            System.out.println(">> Victoria registrada para: " + usuario);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static String obtenerRanking() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== RANKING DE JUGADORES ===\n");
        sb.append(String.format("%-15s | %s\n", "JUGADOR", "VICTORIAS"));
        sb.append("-----------------------------\n");
        
        String sql = "SELECT nombre, victorias FROM usuarios ORDER BY victorias DESC LIMIT 10";
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            int pos = 1;
            while (rs.next()) {
                String nombre = rs.getString("nombre");
                int victorias = rs.getInt("victorias");
                sb.append(String.format("#%d %-12s | %d\n", pos++, nombre, victorias));
            }
        } catch (SQLException e) {
            return "Error al obtener ranking.";
        }
        sb.append("=============================\n");
        return sb.toString();
    }
}