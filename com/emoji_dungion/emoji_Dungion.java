package com.emoji_dungion;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Random;
import javax.swing.*;


/**
 * Pro 732 study GAME: 2D grid Dungeon Crawler
 *
 * MAPS PRO732 EXAM FOCUS AREAS:
 * 1. Swing layouts & Components (BorderLayout, FlowLayout, JProgressBar, KeyListener)
 * 2. Swing custom graphics (paintComponent, Graphics)
 * 3. JDBC CRUD Operations (SQLite, PreparedStatement 1-based indexing)
 * 4. Multithreaded TCP Client Sockets (Socket, InetAddress, PrintWriter auto-flush)
 * 5. Java Servlet Data Mapping (HttpServletRequest parameter parsing) - client-side URL
 *    builder only (getServletUrlQuery); there is no servlet class in this file
 */

public class emoji_Dungion extends JFrame implements KeyListener, ActionListener {

    // GAME & GRID STATE
    private int playerX = 1, playerY = 1;
    private final int TILE_SIZE = 36, COLS = 12, ROWS = 10;
    private int hp = 100, applesCollected = 0, floor = 1;
    // 0 = clear path, 1 = dense trees, 2 = wild apples, 3 = wild wolf, 4 = campsite exit
    private int[][] map = new int[ROWS][COLS];
    private Random rand = new Random();

    // SWING COMPONENTS
    private GameCanvas canvas;
    private JProgressBar hpBar;
    private JLabel appleLabel, floorLabel;
    private Timer gameTimer;

    // SQLITE & NETWORK CONFIG
    private final String DB_URL = "jdbc:sqlite:forest.db";
    private final String HOST = "localhost";
    private final int PORT = 5000;
    private final String PLAYER_NAME = "WizardPlayer";

    public emoji_Dungion() {
        // 1. SWING GUI & BORDERLAYOUT SETUP
        setTitle("PRO732 Emoji_Dungionforest ");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Top HUD panel (FlowLayout inside BorderLayout.NORTH)
        JPanel hud = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        hud.setBackground(new Color(20, 32, 24));

        floorLabel = new JLabel("Area: " + floor + " 🌲");
        floorLabel.setForeground(Color.CYAN);
        hud.add(floorLabel);

        appleLabel = new JLabel("Apples: " + applesCollected + " 🍏");
        appleLabel.setForeground(Color.YELLOW);
        hud.add(appleLabel);

        hpBar = new JProgressBar(0, 100);
        hpBar.setValue(hp);
        hpBar.setStringPainted(true);
        hpBar.setForeground(Color.GREEN);
        hud.add(hpBar);

        // Action buttons
        JButton saveBtn = new JButton("save (JDBC)");
        saveBtn.setFocusable(false);
        saveBtn.addActionListener(e -> saveRunToSQLite());
        hud.add(saveBtn);

        JButton syncBtn = new JButton("Sync Net(TCP)");
        syncBtn.setFocusable(false);
        syncBtn.addActionListener(e -> sendScoreOverSocket());
        hud.add(syncBtn);

        add(hud, BorderLayout.NORTH);

        // Center custom canvas
        canvas = new GameCanvas();
        add(canvas, BorderLayout.CENTER);

        // Procedural forest generation & SQLite DB setup
        generateProceduralForest();
        initDatabaseTable();

        addKeyListener(this);
        setFocusable(true);

        gameTimer = new Timer(16, this);
        gameTimer.start();

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
        requestFocusInWindow();
    }

    // PROCEDURAL LEVEL GENERATION
    private void generateProceduralForest() {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (r == 0 || r == ROWS - 1 || c == 0 || c == COLS - 1) {
                    map[r][c] = 1; // outer forest boundary
                } else if (rand.nextInt(100) < 22) {
                    map[r][c] = 1; // procedural dense trees 🌲
                } else if (rand.nextInt(100) < 15) {
                    map[r][c] = 2; // wild apples 🍏
                } else if (rand.nextInt(100) < 10) {
                    map[r][c] = 3; // wild wolves 🐺
                } else {
                    map[r][c] = 0; // walkable path
                }
            }
        }
        playerX = 1;
        playerY = 1;
        map[playerY][playerX] = 0;  // clear start tile for the wizard
        map[ROWS - 2][COLS - 2] = 4; // campsite exit 🏕️
    }

    // SWING CUSTOM GRAPHICS CANVAS
    private class GameCanvas extends JPanel {
        public GameCanvas() {
            setPreferredSize(new Dimension(COLS * TILE_SIZE + 20, ROWS * TILE_SIZE + 40));
            setBackground(new Color(14, 24, 18));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 24));

            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    int x = c * TILE_SIZE + 10;
                    int y = r * TILE_SIZE + 30;
                    switch (map[r][c]) {
                        case 1 -> g.drawString("🌲", x, y);
                        case 2 -> g.drawString("🍏", x, y);
                        case 3 -> g.drawString("🐺", x, y);
                        case 4 -> g.drawString("🏕️", x, y);
                    }
                }
            }
            // Draw player wizard
            g.drawString("🧙", playerX * TILE_SIZE + 10, playerY * TILE_SIZE + 30);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        canvas.repaint();
    }

    // MOVEMENT & COLLISION EVENTS
    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();

        // Attack: defeat any wolf on the four tiles around the wizard
        if (code == KeyEvent.VK_SPACE) {
            int[] dx = {0, 0, -1, 1};
            int[] dy = {-1, 1, 0, 0};

            for (int i = 0; i < 4; i++) {
                int tX = playerX + dx[i];
                int tY = playerY + dy[i];

                if (map[tY][tX] == 3) {
                    map[tY][tX] = 2; // defeated wolf drops an apple 🍏
                }
            }
            return; // attacking is not a move
        }

        int nx = playerX, ny = playerY;
        if (code == KeyEvent.VK_W || code == KeyEvent.VK_UP)    ny--;
        if (code == KeyEvent.VK_S || code == KeyEvent.VK_DOWN)  ny++;
        if (code == KeyEvent.VK_A || code == KeyEvent.VK_LEFT)  nx--;
        if (code == KeyEvent.VK_D || code == KeyEvent.VK_RIGHT) nx++;

        // Tree collision check
        if (map[ny][nx] != 1) {
            playerX = nx;
            playerY = ny;

            if (map[playerY][playerX] == 2) { // collect apple 🍏
                applesCollected += 1;
                hp = Math.min(100, hp + 15);
                appleLabel.setText("Apples: " + applesCollected + " 🍏");
                map[playerY][playerX] = 0;
                hpBar.setValue(hp);
            } else if (map[playerY][playerX] == 3) { // ran into a wolf 🐺
                hp = Math.max(0, hp - 25);
                hpBar.setValue(hp);
                map[playerY][playerX] = 0;
                if (hp <= 0) {
                    JOptionPane.showMessageDialog(this, "The wizard was defeated! ☠️ Restarting from Area 1.");
                    floor = 1;
                    applesCollected = 0;
                    hp = 100;
                    hpBar.setValue(hp);
                    floorLabel.setText("Area: " + floor + " 🌲");
                    appleLabel.setText("Apples: " + applesCollected + " 🍏");
                    generateProceduralForest();
                }
            } else if (map[playerY][playerX] == 4) { // reached campsite 🏕️
                floor++;
                floorLabel.setText("Area: " + floor + " 🌲");
                saveRunToSQLite(); // auto-save run data
                JOptionPane.showMessageDialog(this, "Campsite Reached! Moving to level " + floor + "\n(Run saved via SQLite JDBC)");
                generateProceduralForest();
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {}

    @Override
    public void keyTyped(KeyEvent e) {}

    /**
     * FOCUS AREA 3: JDBC CRUD & SQLITE DATABASE OPERATIONS
     */
    private void initDatabaseTable() {
        String sql = "CREATE TABLE IF NOT EXISTS forest_runs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "player TEXT, apples INTEGER, floor INTEGER)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
        } catch (Exception ex) {
            System.err.println("JDBC Init Note: " + ex.getMessage());
        }
    }

    private void saveRunToSQLite() {
        String sql = "INSERT INTO forest_runs(player, apples, floor) VALUES (?,?,?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // JDBC positional parameters start at index 1 (NOT 0!)
            stmt.setString(1, PLAYER_NAME);
            stmt.setInt(2, applesCollected);
            stmt.setInt(3, floor);

            int rows = stmt.executeUpdate();
            if (rows > 0) System.out.println("Run record saved to SQLite DB.");
        } catch (Exception ex) {
            System.err.println("JDBC Save Error: " + ex.getMessage());
        }
    }

    /**
     * FOCUS AREA 4: MULTITHREADED TCP SOCKET CLIENT
     */
    private void sendScoreOverSocket() {
        // Read game state on the Swing thread before handing off to the worker thread
        final int apples = applesCollected;
        final int area = floor;

        new Thread(() -> {
            try {
                InetAddress serverAddr = InetAddress.getByName(HOST);
                try (Socket socket = new Socket(serverAddr, PORT);
                     // 'true' enables automatic flushing of output buffers!
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                     BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                    out.println("FOREST_SYNC|" + PLAYER_NAME + "|" + apples + "|" + area);
                    String response = in.readLine();
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(this, "Server response:\n" + response));
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(this, "Could not reach server: " + ex.getMessage()));
            }
        }).start();
    }

    /**
     * FOCUS AREA 5: Servlet query-string mapping (client side).
     * Builds the URL that a servlet would read with
     * request.getParameter("player"), getParameter("apples") and getParameter("floor").
     */
    public String getServletUrlQuery() {
        String player = URLEncoder.encode(PLAYER_NAME, StandardCharsets.UTF_8);
        return "/ForestReportServlet?player=" + player
                + "&apples=" + applesCollected
                + "&floor=" + floor;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(emoji_Dungion::new);
    }
}