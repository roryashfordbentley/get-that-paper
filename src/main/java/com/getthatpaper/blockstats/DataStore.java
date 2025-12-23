package com.getthatpaper.blockstats;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite-backed store for block break statistics.
 */
public class DataStore {
    private final File databaseFile;
    private final Logger logger;
    private Connection connection;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public DataStore(File databaseFile, Logger logger) {
        this.databaseFile = databaseFile;
        this.logger = logger;
    }

    public void init() throws SQLException {
        try {
            if (databaseFile.getParentFile() != null) {
                databaseFile.getParentFile().mkdirs();
            }
            String url = "jdbc:sqlite:" + databaseFile.getAbsolutePath() + "?busy_timeout=5000";
            this.connection = DriverManager.getConnection(url);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS block_breaks (" +
                                "player_uuid TEXT NOT NULL," +
                                "material TEXT NOT NULL," +
                                "count INTEGER NOT NULL," +
                                "PRIMARY KEY (player_uuid, material)" +
                                ")");
                statement.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS player_names (" +
                                "player_uuid TEXT PRIMARY KEY," +
                                "player_name TEXT NOT NULL" +
                                ")");
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to initialize database", ex);
            throw ex;
        }
    }

    public void close() {
        executor.shutdown();
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ex) {
                logger.log(Level.WARNING, "Error closing database", ex);
            }
        }
    }

    public void recordBlockBreak(UUID playerId, String playerName, String material) {
        executor.submit(() -> doRecordBlockBreak(playerId, playerName, material));
    }

    private void doRecordBlockBreak(UUID playerId, String playerName, String material) {
        String upsertName = "INSERT INTO player_names (player_uuid, player_name) VALUES (?, ?) " +
                "ON CONFLICT(player_uuid) DO UPDATE SET player_name = excluded.player_name";
        String upsertBreak = "INSERT INTO block_breaks (player_uuid, material, count) VALUES (?, ?, 1) " +
                "ON CONFLICT(player_uuid, material) DO UPDATE SET count = count + 1";
        try (PreparedStatement nameStmt = connection.prepareStatement(upsertName);
             PreparedStatement breakStmt = connection.prepareStatement(upsertBreak)) {
            nameStmt.setString(1, playerId.toString());
            nameStmt.setString(2, playerName);
            nameStmt.executeUpdate();

            breakStmt.setString(1, playerId.toString());
            breakStmt.setString(2, material);
            breakStmt.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to record block break", ex);
        }
    }

    public List<MaterialStat> getTopBlocks(int limit) throws SQLException {
        String sql = "SELECT material, SUM(count) as total FROM block_breaks " +
                "GROUP BY material ORDER BY total DESC LIMIT ?";
        List<MaterialStat> results = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new MaterialStat(rs.getString("material"), rs.getLong("total")));
                }
            }
        }
        return results;
    }

    public List<MaterialStat> getTopBlocksForPlayerName(String playerName, int limit) throws SQLException {
        String sql = "SELECT b.material, b.count as total FROM block_breaks b " +
                "JOIN player_names n ON b.player_uuid = n.player_uuid " +
                "WHERE LOWER(n.player_name) = LOWER(?) " +
                "ORDER BY total DESC LIMIT ?";
        List<MaterialStat> results = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerName);
            stmt.setInt(2, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new MaterialStat(rs.getString("material"), rs.getLong("total")));
                }
            }
        }
        return results;
    }

    public boolean hasPlayer(String playerName) throws SQLException {
        String sql = "SELECT 1 FROM player_names WHERE LOWER(player_name) = LOWER(?) LIMIT 1";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }
}
