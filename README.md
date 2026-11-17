# Emoji Dungeon Forest

A small Java Swing dungeon-crawler game built as a practical demonstration of Java GUI programming, procedural map generation, SQLite database access with JDBC, and TCP client/server communication.

## Features

- **2D grid-based game:** Explore a forest map displayed with emoji tiles.
- **Procedural level generation:** Each area receives a randomly generated layout. The game checks that the campsite exit is reachable before accepting a map.
- **Player health:** Start with 100 HP. Collecting an apple restores 15 HP, up to a maximum of 100. Walking into a wolf costs 25 HP.
- **Combat:** Press Space to defeat wolves in the four tiles immediately around the player. A defeated wolf becomes an apple.
- **Progression:** Reach the campsite to advance to the next area. The game automatically saves a run when an area is completed.
- **SQLite persistence:** Save player, apple count, and area using JDBC.
- **TCP score synchronisation:** Send the current run to a local server, which stores received runs in a separate SQLite database.
- **Servlet query-string example:** Includes a helper method that builds a URL query string; this project does not contain or run a servlet.

## Technologies

- Java 26
- Java Swing and AWT
- Maven
- SQLite
- Xerial SQLite JDBC (`org.xerial:sqlite-jdbc:3.53.2.1`)
- Java TCP sockets

## Project structure

The Maven project is inside the `demo` directory:

```text
emoji_dungion/
├── README.md
└── demo/
    ├── pom.xml
    └── src/
        └── main/
            └── java/
                └── com/
                    └── emoji_dungion/
                        ├── emoji_Dungion.java
                        └── ForestServer.java
```

## Requirements

1. Install a JDK. The project is configured to compile with **Java 26**.
2. Install Apache Maven and make sure the `mvn` command is available in your terminal.
3. Optional: install [DB Browser for SQLite](https://sqlitebrowser.org/) to inspect the database files.

Check the Java and Maven installations:

```powershell
java -version
mvn -version
```

Maven should report that it is using JDK 26. If it reports a different Java version, configure `JAVA_HOME` and your Windows `Path` accordingly, then restart the terminal.

## Build the project

Open a terminal in the `demo` directory (the directory containing `pom.xml`):

```powershell
cd path\to\emoji_dungion\demo
mvn clean compile
```

To download/resolve the project's Maven dependencies explicitly:

```powershell
mvn dependency:resolve
```

## Run the game

From the `demo` directory, run:

```powershell
mvn exec:java
```

The Maven Exec Plugin is configured to launch:

```text
com.emoji_dungion.emoji_Dungion
```

Run the application through Maven (or ensure your IDE includes Maven dependencies on its runtime classpath). Launching only the compiled `.class` files without the SQLite JDBC dependency can cause `No suitable driver found for jdbc:sqlite:forest.db`.

## How to play

| Action                                     | Control                       |
| ------------------------------------------ | ----------------------------- |
| Move up                                    | `W` or `↑`               |
| Move down                                  | `S` or `↓`               |
| Move left                                  | `A` or `←`               |
| Move right                                 | `D` or `→`               |
| Attack adjacent wolves                     | `Space`                     |
| Save the current run                       | Click **save (JDBC)**   |
| Send the current score to the local server | Click **Sync Net(TCP)** |

### Game objects

| Emoji | Object   | Behaviour                                                         |
| ----- | -------- | ----------------------------------------------------------------- |
| 🧙    | Player   | The wizard controlled by the player                               |
| 🌲    | Tree     | Blocks movement                                                   |
| 🍏    | Apple    | Collect to increase the apple count and restore 15 HP             |
| 🐺    | Wolf     | Costs 25 HP if walked into; can be defeated from an adjacent tile |
| 🏕️  | Campsite | Advances the player to the next area                              |

The player starts each area at 100 HP. If health reaches zero, the game resets to area 1 with zero collected apples and 100 HP. The map is generated again.

## SQLite databases

The game and the TCP server use **two separate SQLite database files**. Both database URLs use relative filenames, so the files are created in the process's current working directory. When you run the commands below from `demo`, they will normally appear in `demo`:

- `forest.db` — created by the game. Contains the `forest_runs` table with `id`, `player`, `apples`, and `floor` columns.
- `forest_server.db` — created by the server. Contains the `synced_runs` table with `id`, `player`, `apples`, `floor`, and `synced_at` columns.

The game creates its table when it starts. Clicking **save (JDBC)** inserts the current player name, apple count, and area. Reaching a campsite also triggers an automatic save.

To inspect either database with DB Browser for SQLite:

1. Open DB Browser for SQLite.
2. Choose **Open Database**.
3. Select `demo/forest.db` or `demo/forest_server.db` (depending on which database you want to inspect).
4. Open **Browse Data** and choose `forest_runs` or `synced_runs` from the table selector.

You can also use the **Execute SQL** tab to run:

```sql
SELECT * FROM forest_runs;
```

For the server database, use:

```sql
SELECT * FROM synced_runs ORDER BY id DESC;
```

The database file may not exist until the corresponding application successfully opens its SQLite connection. If you cannot find it, check the working directory shown by your IDE/terminal and look for JDBC errors in the console.

## Run the TCP server

The server must be running before you click **Sync Net(TCP)** in the game.

1. Open a **first terminal** in the `demo` directory and start the server:

   ```powershell
   mvn exec:java -Dexec.mainClass=com.emoji_dungion.ForestServer
   ```
2. Keep that terminal open. It should report that it is listening on `localhost:5000` and display the location of `forest_server.db`.
3. Open a **second terminal** in the same `demo` directory and start the game:

   ```powershell
   mvn exec:java
   ```
4. In the game, click **Sync Net(TCP)**. The server should receive the run, save it to `forest_server.db`, print recent records in its terminal, and return a response to the game.

The server binds to the loopback interface (`localhost`) only. It is intended for local testing and does not accept connections from other computers. Port `5000` must be available.

## Troubleshooting

### `mvn` is not recognised

Install Apache Maven, add Maven's `bin` directory to the Windows `Path`, close and reopen PowerShell/VS Code, and verify with `mvn -version`.

### `No suitable driver found for jdbc:sqlite:...`

The SQLite JDBC driver is missing from the runtime classpath. Run the application using `mvn exec:java` from `demo`, confirm that `sqlite-jdbc` appears in `mvn dependency:tree`, and make sure the IDE has imported the Maven project. Do not launch only the compiled classes folder unless you also configure the SQLite JDBC dependency on the runtime classpath.

### `Could not reach server` when synchronising

Start `ForestServer` first, keep it running, and confirm that port `5000` is not being used by another application. The game connects to `localhost:5000`.

### Database file is not where expected

`forest.db` and `forest_server.db` are relative paths. They are created in the current working directory of the game/server process, which may differ when launching from an IDE. Run both Maven commands from the `demo` directory for predictable locations.

## Notes and current scope

- The game saves run snapshots; it does not currently load a previous save or resume a game from the database.
- The TCP server stores synchronised snapshots separately from the game's local save records.
- `getServletUrlQuery()` demonstrates building a URL query string for a hypothetical `ForestReportServlet`. A servlet implementation and servlet container are not included.
- The game uses a Swing `Timer` to repaint the game canvas; player movement is handled by keyboard events.

## License

No license has been specified for this project. Add a `LICENSE` file if you intend to distribute it with explicit reuse permissions.
