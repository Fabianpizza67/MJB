package com.UserMC.MJB;

import org.bukkit.plugin.java.JavaPlugin;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private final MJB plugin;
    private Connection connection;

    public DatabaseManager(MJB plugin) {
        this.plugin = plugin;
    }

    public boolean connect() {
        try {
            String host = plugin.getConfig().getString("database.host", "localhost");
            int port = plugin.getConfig().getInt("database.port", 3306);
            String database = plugin.getConfig().getString("database.name", "citylife");
            String username = plugin.getConfig().getString("database.username", "root");
            String password = plugin.getConfig().getString("database.password", "");

            String url = "jdbc:mariadb://" + host + ":" + port + "/" + database
                    + "?autoReconnect=true&useSSL=false";

            connection = DriverManager.getConnection(url, username, password);
            plugin.getLogger().info("Connected to MariaDB successfully!");
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not connect to MariaDB: " + e.getMessage());
            return false;
        }
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Database connection closed.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error closing database connection: " + e.getMessage());
        }
    }

    public void createTables() {
        try (Statement stmt = connection.createStatement()) {
            // Players table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS players (
                    uuid VARCHAR(36) PRIMARY KEY,
                    username VARCHAR(16) NOT NULL,
                    bank_balance DOUBLE NOT NULL DEFAULT 0,
                    cash_balance DOUBLE NOT NULL DEFAULT 0,
                    wanted_level INT NOT NULL DEFAULT 0,
                    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            plugin.getLogger().info("Database tables created/verified.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error creating tables: " + e.getMessage());
        }
    }

    public Connection getConnection() {
        return connection;
    }
}