package com.emoji_dungion;


import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Localhost TCP server for emoji_Dungion's "Sync Net(TCP)" button.
 *
 * Run this FIRST, then start the game and press the sync button.
 * Each message looks like:  FOREST_SYNC|player|apples|floor
 * The server stores it in forest_server.db (created next to where you run it)
 * and replies with one line: OK|saved run #<id>   or   ERROR|<reason>
 *
 * Needs the sqlite-jdbc jar on the classpath, same as the game.
 */
public class ForestServer {

    private static final int PORT = 5000;
    private static final String DB_FILE = "forest_server.db";
    private static final String DB_URL = "jdbc:sqlite:" + DB_FILE;

    // SQLite allows one writer at a time, so database work is done one thread at a time
    private static final Object DB_LOCK = new Object();

    public static void main(String[] args) {
        initDatabase();

        // Bind to the loopback address only, so other computers cannot connect
        try (ServerSocket server = new ServerSocket(PORT, 50, InetAddress.getLoopbackAddress())) {
            System.out.println("ForestServer listening on localhost:" + PORT);
            System.out.println("Database file: " + new File(DB_FILE).getAbsolutePath());

            while (true) {
                Socket client = server.accept();
                // One thread per client so a slow client never blocks the others
                new Thread(() -> handleClient(client)).start();
            }
        } catch (IOException ex) {
            System.err.println("Server error: " + ex.getMessage());
        }
    }

    private static void handleClient(Socket client) {
        try (client;
             BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             // 'true' enables automatic flushing, same as the client side
             PrintWriter out = new PrintWriter(client.getOutputStream(), true)) {

            client.setSoTimeout(5000); // don't wait forever for a silent client
            String line = in.readLine();
            System.out.println("Received: " + line);
            out.println(processMessage(line));

        } catch (IOException ex) {
            System.err.println("Client error: " + ex.getMessage());
        }
    }

    // Parse and validate one FOREST_SYNC message, store it, and build the reply
    private static String processMessage(String line) {
        if (line == null) return "ERROR|empty message";

        String[] parts = line.split("\\|");
        if (parts.length != 4 || !parts[0].equals("FOREST_SYNC")) {
            return "ERROR|unknown message format";
        }

        String player = parts[1].trim();
        if (player.isEmpty() || player.length() > 40) return "ERROR|bad player name";

        try {
            int apples = Integer.parseInt(parts[2].trim());
            int floor = Integer.parseInt(parts[3].trim());
            if (apples < 0 || floor < 1) return "ERROR|values out of range";

            long id = saveRun(player, apples, floor);
            printLatestRuns();
            return "OK|saved run #" + id;
        } catch (NumberFormatException ex) {
            return "ERROR|apples and floor must be whole numbers";
        } catch (SQLException ex) {
            System.err.println("JDBC error: " + ex.getMessage());
            return "ERROR|database problem";
        }
    }

    private static void initDatabase() {
        String sql = "CREATE TABLE IF NOT EXISTS synced_runs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "player TEXT NOT NULL, apples INTEGER NOT NULL, floor INTEGER NOT NULL," +
                "synced_at TEXT DEFAULT CURRENT_TIMESTAMP)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException ex) {
            System.err.println("JDBC Init Error: " + ex.getMessage());
        }
    }

    // CREATE: insert one run and return the new row id
    private static long saveRun(String player, int apples, int floor) throws SQLException {
        String sql = "INSERT INTO synced_runs(player, apples, floor) VALUES (?,?,?)";
        synchronized (DB_LOCK) {
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                // JDBC positional parameters start at index 1 (NOT 0!)
                stmt.setString(1, player);
                stmt.setInt(2, apples);
                stmt.setInt(3, floor);
                stmt.executeUpdate();

                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    return keys.next() ? keys.getLong(1) : -1;
                }
            }
        }
    }

    // READ: show the 5 newest rows in the server console so you can see the data arriving
    private static void printLatestRuns() {
        String sql = "SELECT id, player, apples, floor, synced_at FROM synced_runs ORDER BY id DESC LIMIT 5";
        synchronized (DB_LOCK) {
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                System.out.println("--- latest runs in " + DB_FILE + " ---");
                while (rs.next()) {
                    System.out.println("#" + rs.getInt("id")
                            + "  " + rs.getString("player")
                            + "  apples=" + rs.getInt("apples")
                            + "  floor=" + rs.getInt("floor")
                            + "  at " + rs.getString("synced_at"));
                }
            } catch (SQLException ex) {
                System.err.println("JDBC Read Error: " + ex.getMessage());
            }
        }
    }
}
