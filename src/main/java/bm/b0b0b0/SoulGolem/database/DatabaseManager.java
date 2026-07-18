package bm.b0b0b0.SoulGolem.database;

import bm.b0b0b0.SoulGolem.config.settings.Settings;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.bukkit.plugin.Plugin;

public final class DatabaseManager {

    private final Plugin plugin;
    private final ExecutorService executor;
    private HikariDataSource dataSource;

    public DatabaseManager(Plugin plugin) {
        this.plugin = plugin;
        this.executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "SoulGolem-DB");
            thread.setDaemon(true);
            return thread;
        });
    }

    public CompletableFuture<Void> start(Settings.Database database, Path dataFolder) {
        return CompletableFuture.runAsync(() -> {
            HikariConfig hikari = new HikariConfig();
            hikari.setPoolName("SoulGolem-Hikari");
            hikari.setMaximumPoolSize(Math.max(1, database.poolSize));
            hikari.setConnectionTimeout(database.connectionTimeoutMs);
            hikari.setAutoCommit(true);

            if ("mysql".equalsIgnoreCase(database.type)) {
                hikari.setJdbcUrl("jdbc:mysql://" + database.mysqlHost + ":" + database.mysqlPort + "/" + database.mysqlDatabase
                        + "?useSSL=false&allowPublicKeyRetrieval=true");
                hikari.setUsername(database.mysqlUsername);
                hikari.setPassword(database.mysqlPassword);
                hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
            } else {
                Path dbFile = resolveSqliteFile(dataFolder, database.sqliteFile);
                hikari.setJdbcUrl("jdbc:sqlite:" + dbFile.toAbsolutePath());
                hikari.setDriverClassName("org.sqlite.JDBC");
                hikari.setMaximumPoolSize(1);
            }

            this.dataSource = new HikariDataSource(hikari);
            migrate();
        }, this.executor);
    }

    private static Path resolveSqliteFile(Path dataFolder, String sqliteFile) {
        String relative = sqliteFile == null || sqliteFile.isBlank() ? "db/data.db" : sqliteFile;
        Path dbFile = dataFolder.resolve(relative).normalize();
        try {
            Path parent = dbFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path legacy = dataFolder.resolve("data.db");
            if (Files.exists(legacy) && !Files.exists(dbFile) && !legacy.equals(dbFile)) {
                Files.move(legacy, dbFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to prepare SQLite file " + dbFile, exception);
        }
        return dbFile;
    }

    private void migrate() {
        try (Connection connection = this.dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS soul_golems (
                        id VARCHAR(36) PRIMARY KEY NOT NULL,
                        owner_uuid VARCHAR(36) NOT NULL,
                        type VARCHAR(32) NOT NULL,
                        world VARCHAR(128) NOT NULL,
                        x DOUBLE NOT NULL,
                        y DOUBLE NOT NULL,
                        z DOUBLE NOT NULL,
                        home_x DOUBLE NOT NULL DEFAULT 0,
                        home_y DOUBLE NOT NULL DEFAULT 0,
                        home_z DOUBLE NOT NULL DEFAULT 0,
                        yaw DOUBLE NOT NULL,
                        pitch DOUBLE NOT NULL,
                        chest_x DOUBLE NOT NULL,
                        chest_y DOUBLE NOT NULL,
                        chest_z DOUBLE NOT NULL,
                        craft_x DOUBLE NOT NULL DEFAULT -1,
                        craft_y DOUBLE NOT NULL DEFAULT -1,
                        craft_z DOUBLE NOT NULL DEFAULT -1,
                        seat_x DOUBLE NOT NULL DEFAULT -1,
                        seat_y DOUBLE NOT NULL DEFAULT -1,
                        seat_z DOUBLE NOT NULL DEFAULT -1,
                        compost_x DOUBLE NOT NULL DEFAULT -1,
                        compost_y DOUBLE NOT NULL DEFAULT -1,
                        compost_z DOUBLE NOT NULL DEFAULT -1,
                        dig_start_y INT NOT NULL DEFAULT -2147483648,
                        dig_layer_y INT NOT NULL DEFAULT -2147483648,
                        dig_stair_index INT NOT NULL DEFAULT 0,
                        crew_leader_id VARCHAR(36),
                        entity_uuid VARCHAR(36),
                        level INT NOT NULL,
                        energy INT NOT NULL,
                        paused TINYINT NOT NULL,
                        blocks_mined BIGINT NOT NULL,
                        upgrades_json TEXT NOT NULL,
                        last_action_at BIGINT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_soul_golems_owner ON soul_golems (owner_uuid)
                    """);
            addColumnIgnoreError(statement, "ALTER TABLE soul_golems ADD COLUMN home_x DOUBLE NOT NULL DEFAULT 0");
            addColumnIgnoreError(statement, "ALTER TABLE soul_golems ADD COLUMN home_y DOUBLE NOT NULL DEFAULT 0");
            addColumnIgnoreError(statement, "ALTER TABLE soul_golems ADD COLUMN home_z DOUBLE NOT NULL DEFAULT 0");
            addColumnIgnoreError(statement, "ALTER TABLE soul_golems ADD COLUMN craft_x DOUBLE NOT NULL DEFAULT -1");
            addColumnIgnoreError(statement, "ALTER TABLE soul_golems ADD COLUMN craft_y DOUBLE NOT NULL DEFAULT -1");
            addColumnIgnoreError(statement, "ALTER TABLE soul_golems ADD COLUMN craft_z DOUBLE NOT NULL DEFAULT -1");
            addColumnIgnoreError(statement, "ALTER TABLE soul_golems ADD COLUMN seat_x DOUBLE NOT NULL DEFAULT -1");
            addColumnIgnoreError(statement, "ALTER TABLE soul_golems ADD COLUMN seat_y DOUBLE NOT NULL DEFAULT -1");
            addColumnIgnoreError(statement, "ALTER TABLE soul_golems ADD COLUMN seat_z DOUBLE NOT NULL DEFAULT -1");
            addColumnIgnoreError(statement, "ALTER TABLE soul_golems ADD COLUMN compost_x DOUBLE NOT NULL DEFAULT -1");
            addColumnIgnoreError(statement, "ALTER TABLE soul_golems ADD COLUMN compost_y DOUBLE NOT NULL DEFAULT -1");
            addColumnIgnoreError(statement, "ALTER TABLE soul_golems ADD COLUMN compost_z DOUBLE NOT NULL DEFAULT -1");
            addColumnIgnoreError(statement, "ALTER TABLE soul_golems ADD COLUMN dig_start_y INT NOT NULL DEFAULT -2147483648");
            addColumnIgnoreError(statement, "ALTER TABLE soul_golems ADD COLUMN dig_layer_y INT NOT NULL DEFAULT -2147483648");
            addColumnIgnoreError(statement, "ALTER TABLE soul_golems ADD COLUMN dig_stair_index INT NOT NULL DEFAULT 0");
            addColumnIgnoreError(statement, "ALTER TABLE soul_golems ADD COLUMN crew_leader_id VARCHAR(36)");
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to migrate SoulGolem database", exception);
        }
    }

    private static void addColumnIgnoreError(Statement statement, String sql) {
        try {
            statement.execute(sql);
        } catch (SQLException ignored) {
        }
    }

    public DataSource dataSource() {
        return this.dataSource;
    }

    public ExecutorService executor() {
        return this.executor;
    }

    public <T> CompletableFuture<T> supplyAsync(java.util.concurrent.Callable<T> callable) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return callable.call();
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }, this.executor);
    }

    public CompletableFuture<Void> runAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, this.executor);
    }

    public void shutdown() {
        if (this.dataSource != null) {
            this.dataSource.close();
        }
        this.executor.shutdown();
        try {
            if (!this.executor.awaitTermination(5L, TimeUnit.SECONDS)) {
                this.executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            this.executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
