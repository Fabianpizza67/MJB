package com.UserMC.MJB;

import java.sql.*;

public class DatabaseManager {

    private final MJB plugin;
    private Connection connection;

    public DatabaseManager(MJB plugin) {
        this.plugin = plugin;
    }

    public boolean connect() {
        try {
            Class.forName("org.mariadb.jdbc.Driver");

            String host = plugin.getConfig().getString("database.host", "localhost");
            int port = plugin.getConfig().getInt("database.port", 3306);
            String database = plugin.getConfig().getString("database.name", "MJB");
            String username = plugin.getConfig().getString("database.username", "root");
            String password = plugin.getConfig().getString("database.password", "");

            String url = "jdbc:mariadb://" + host + ":" + port + "/" + database
                    + "?autoReconnect=true&useSSL=false";

            java.util.Properties props = new java.util.Properties();
            props.setProperty("user", username);
            props.setProperty("password", password);
            props.setProperty("autoReconnect", "true");
            props.setProperty("connectionTimeout", "30000");
            props.setProperty("socketTimeout", "60000");
            connection = DriverManager.getConnection(url, props);
            plugin.getLogger().info("Connected to MariaDB successfully!");
            return true;

        } catch (ClassNotFoundException e) {
            plugin.getLogger().severe("MariaDB driver class not found: " + e.getMessage());
            return false;
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

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS players (" +
                            "uuid VARCHAR(36) PRIMARY KEY," +
                            "username VARCHAR(16) NOT NULL," +
                            "bank_balance DOUBLE NOT NULL DEFAULT 0," +
                            "cash_balance DOUBLE NOT NULL DEFAULT 0," +
                            "wanted_level INT NOT NULL DEFAULT 0," +
                            "has_claimed_starter BOOLEAN NOT NULL DEFAULT FALSE," +
                            "joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "card_version INT NOT NULL DEFAULT 1," +
                            "thirst INT NOT NULL DEFAULT 20" +
                            ")"
            );

            // Add has_claimed_starter column if it doesn't exist yet
            try {
                stmt.execute("ALTER TABLE players ADD COLUMN has_claimed_starter BOOLEAN NOT NULL DEFAULT FALSE");
                plugin.getLogger().info("Added has_claimed_starter column to players table.");
            } catch (SQLException ignored) {
                // Column already exists, that's fine
            }

            try {
                stmt.execute("ALTER TABLE players ADD COLUMN thirst INT NOT NULL DEFAULT 20");
                plugin.getLogger().info("Added thirst column to players table.");
            } catch (SQLException ignored) {}

            try {
                stmt.execute("ALTER TABLE supply_orders ADD COLUMN company_id INT DEFAULT NULL");
                plugin.getLogger().info("Added company_id column to supply_orders table.");
            } catch (SQLException ignored) {}

            try {
                stmt.execute("ALTER TABLE company_roles ADD COLUMN place_orders BOOLEAN NOT NULL DEFAULT FALSE");
                plugin.getLogger().info("Added place_orders column to company_roles table.");
            } catch (SQLException ignored) {}


            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS starter_apartments (" +
                            "region_id VARCHAR(64) PRIMARY KEY," +
                            "world VARCHAR(64) NOT NULL," +
                            "is_claimed BOOLEAN NOT NULL DEFAULT FALSE," +
                            "claimed_by VARCHAR(36) DEFAULT NULL" +
                            ")"
            );

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS plots (" +
                            "region_id VARCHAR(64) NOT NULL," +
                            "world VARCHAR(64) NOT NULL," +
                            "owner_uuid VARCHAR(36) NOT NULL," +
                            "plot_type VARCHAR(32) NOT NULL DEFAULT 'apartment'," +
                            "purchased_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (region_id, world)" +
                            ")"
            );
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS terminals (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "world VARCHAR(64) NOT NULL," +
                            "x INT NOT NULL," +
                            "y INT NOT NULL," +
                            "z INT NOT NULL," +
                            "owner_uuid VARCHAR(36) NOT NULL," +
                            "store_region VARCHAR(64) NOT NULL," +
                            "current_price DOUBLE NOT NULL DEFAULT 0," +
                            "UNIQUE KEY location_key (world, x, y, z)" +
                            ")"
            );
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS supply_orders (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "owner_uuid VARCHAR(36) NOT NULL," +
                            "district VARCHAR(64) NOT NULL," +
                            "status VARCHAR(16) NOT NULL DEFAULT 'pending'," +
                            "total_cost DOUBLE NOT NULL DEFAULT 0," +
                            "ordered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "ready_at TIMESTAMP NULL" +
                            ")"
            );

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS supply_order_items (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "order_id INT NOT NULL," +
                            "material VARCHAR(64) NOT NULL," +
                            "quantity INT NOT NULL," +
                            "price_per_item DOUBLE NOT NULL," +
                            "FOREIGN KEY (order_id) REFERENCES supply_orders(id) ON DELETE CASCADE" +
                            ")"
            );
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS supply_order_authorizations (" +
                            "order_id INT NOT NULL," +
                            "authorized_uuid VARCHAR(36) NOT NULL," +
                            "PRIMARY KEY (order_id, authorized_uuid)," +
                            "FOREIGN KEY (order_id) REFERENCES supply_orders(id) ON DELETE CASCADE" +
                            ")"
            );
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS supply_items (" +
                            "material VARCHAR(64) PRIMARY KEY," +
                            "license_required VARCHAR(64) NOT NULL," +
                            "price_per_item DOUBLE NOT NULL," +
                            "delivery_seconds INT NOT NULL DEFAULT 1800" +
                            ")"
            );
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS pickup_npcs (" +
                            "npc_id INT PRIMARY KEY," +
                            "district VARCHAR(64) NOT NULL UNIQUE" +
                            ")"
            );
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS computers (" +
                            "world VARCHAR(64) NOT NULL," +
                            "x INT NOT NULL," +
                            "y INT NOT NULL," +
                            "z INT NOT NULL," +
                            "owner_uuid VARCHAR(36) NOT NULL," +
                            "PRIMARY KEY (world, x, y, z)" +
                            ")"
            );
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS cancelled_cards (" +
                            "player_uuid VARCHAR(36) PRIMARY KEY" +
                            ")"
            );

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS companies (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "name VARCHAR(64) NOT NULL UNIQUE," +
                            "type VARCHAR(32) NOT NULL," +
                            "description TEXT," +
                            "owner_uuid VARCHAR(36) NOT NULL," +
                            "bank_balance DOUBLE NOT NULL DEFAULT 0," +
                            "is_bankrupt BOOLEAN NOT NULL DEFAULT FALSE," +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "company_id INT DEFAULT NULL" +
                            ")"
            );

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS company_members (" +
                            "company_id INT NOT NULL," +
                            "player_uuid VARCHAR(36) NOT NULL," +
                            "role_name VARCHAR(32) NOT NULL DEFAULT 'employee'," +
                            "salary DOUBLE NOT NULL DEFAULT 0," +
                            "joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (company_id, player_uuid)," +
                            "FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE" +
                            ")"
            );

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS company_roles (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "company_id INT NOT NULL," +
                            "role_name VARCHAR(32) NOT NULL," +
                            "can_hire_fire BOOLEAN NOT NULL DEFAULT FALSE," +
                            "can_set_prices BOOLEAN NOT NULL DEFAULT FALSE," +
                            "can_access_bank BOOLEAN NOT NULL DEFAULT FALSE," +
                            "UNIQUE KEY role_per_company (company_id, role_name)," +
                            "FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE" +
                            ")"
            );

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS company_plots (" +
                            "region_id VARCHAR(64) NOT NULL," +
                            "world VARCHAR(64) NOT NULL," +
                            "company_id INT NOT NULL," +
                            "PRIMARY KEY (region_id, world)," +
                            "FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE" +
                            ")"
            );
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS property_listings (" +
                            "region_id VARCHAR(64) NOT NULL," +
                            "world VARCHAR(64) NOT NULL," +
                            "plot_type VARCHAR(32) NOT NULL," +
                            "district VARCHAR(64) NOT NULL DEFAULT 'unknown'," +
                            "price DOUBLE NOT NULL," +
                            "is_available BOOLEAN NOT NULL DEFAULT TRUE," +
                            "listed_by VARCHAR(36) DEFAULT NULL," + // NULL = city listing
                            "listed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (region_id, world)" +
                            ")"
            );

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS city_treasury (" +
                            "id INT PRIMARY KEY DEFAULT 1," +
                            "balance DOUBLE NOT NULL DEFAULT 0" +
                            ")"
            );

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS license_types (" +
                            "type_name VARCHAR(64) PRIMARY KEY," +
                            "display_name VARCHAR(64) NOT NULL," +
                            "cost DOUBLE NOT NULL DEFAULT 100.0," +
                            "renewal_cost DOUBLE NOT NULL DEFAULT 50.0," +
                            "description VARCHAR(256)" +
                            ")"
            );

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS licenses (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "player_uuid VARCHAR(36) NOT NULL," +
                            "license_type VARCHAR(64) NOT NULL," +
                            "issued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "expires_at TIMESTAMP NOT NULL," +
                            "is_revoked BOOLEAN NOT NULL DEFAULT FALSE," +
                            "revoked_by VARCHAR(36) DEFAULT NULL," +
                            "UNIQUE KEY unique_player_license (player_uuid, license_type)," +
                            "FOREIGN KEY (license_type) REFERENCES license_types(type_name) ON DELETE CASCADE" +
                            ")"
            );

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS license_craft_rules (" +
                            "result_material VARCHAR(64) PRIMARY KEY," +
                            "license_type VARCHAR(64) NOT NULL," +
                            "FOREIGN KEY (license_type) REFERENCES license_types(type_name) ON DELETE CASCADE" +
                            ")"
            );

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS parties (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "name VARCHAR(64) NOT NULL UNIQUE," +
                            "leader_uuid VARCHAR(36) NOT NULL," +
                            "description VARCHAR(256)," +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ")"
            );
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS party_members (" +
                            "party_id INT NOT NULL," +
                            "player_uuid VARCHAR(36) NOT NULL," +
                            "joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (party_id, player_uuid)," +
                            "FOREIGN KEY (party_id) REFERENCES parties(id) ON DELETE CASCADE" +
                            ")"
            );
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS elections (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "status VARCHAR(16) NOT NULL DEFAULT 'active'," +
                            "started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "ends_at TIMESTAMP NOT NULL" +
                            ")"
            );
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS election_votes (" +
                            "election_id INT NOT NULL," +
                            "voter_uuid VARCHAR(36) NOT NULL," +
                            "party_id INT NOT NULL," +
                            "PRIMARY KEY (election_id, voter_uuid)," +
                            "FOREIGN KEY (election_id) REFERENCES elections(id) ON DELETE CASCADE" +
                            ")"
            );
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS election_results (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "election_id INT NOT NULL," +
                            "party_id INT NOT NULL," +
                            "seats INT NOT NULL DEFAULT 0," +
                            "is_winner BOOLEAN NOT NULL DEFAULT FALSE," +
                            "FOREIGN KEY (election_id) REFERENCES elections(id) ON DELETE CASCADE" +
                            ")"
            );
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS council_sessions (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "status VARCHAR(16) NOT NULL DEFAULT 'active'," +
                            "started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "ends_at TIMESTAMP NOT NULL" +
                            ")"
            );
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS proposals (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "session_id INT NOT NULL," +
                            "proposer_uuid VARCHAR(36) NOT NULL," +
                            "text VARCHAR(512) NOT NULL," +
                            "law_type VARCHAR(64) NOT NULL DEFAULT 'custom'," +
                            "law_value VARCHAR(128) NOT NULL DEFAULT 'true'," +
                            "status VARCHAR(16) NOT NULL DEFAULT 'open'," +
                            "yes_votes INT NOT NULL DEFAULT 0," +
                            "no_votes INT NOT NULL DEFAULT 0," +
                            "proposed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "FOREIGN KEY (session_id) REFERENCES council_sessions(id) ON DELETE CASCADE" +
                            ")"
            );
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS proposal_votes (" +
                            "proposal_id INT NOT NULL," +
                            "voter_uuid VARCHAR(36) NOT NULL," +
                            "vote BOOLEAN NOT NULL," +
                            "seats_used INT NOT NULL DEFAULT 1," +
                            "PRIMARY KEY (proposal_id, voter_uuid)," +
                            "FOREIGN KEY (proposal_id) REFERENCES proposals(id) ON DELETE CASCADE" +
                            ")"
            );
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS laws (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "title VARCHAR(512) NOT NULL," +
                            "law_type VARCHAR(64) NOT NULL," +
                            "law_value VARCHAR(128) NOT NULL DEFAULT 'true'," +
                            "passed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "passed_by_proposal_id INT DEFAULT NULL," +
                            "is_active BOOLEAN NOT NULL DEFAULT TRUE" +
                            ")"
            );
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS council_regions (" +
                            "region_id VARCHAR(64) NOT NULL," +
                            "world VARCHAR(64) NOT NULL," +
                            "PRIMARY KEY (region_id, world)" +
                            ")"
            );
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS government_settings (" +
                            "setting_key VARCHAR(64) PRIMARY KEY," +
                            "value VARCHAR(256) NOT NULL" +
                            ")"
            );
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS government_state (" +
                            "id INT PRIMARY KEY DEFAULT 1," +
                            "mayor_uuid VARCHAR(36) DEFAULT NULL" +
                            ")"
            );
            stmt.execute("INSERT IGNORE INTO government_state (id) VALUES (1)");

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS police_officers (" +
                            "uuid VARCHAR(36) PRIMARY KEY," +
                            "appointed_by VARCHAR(36) DEFAULT NULL," +
                            "appointed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "rank VARCHAR(16) NOT NULL DEFAULT 'OFFICER'" +
                            ")"
            );

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS crime_records (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "player_uuid VARCHAR(36) NOT NULL," +
                            "offence VARCHAR(256) NOT NULL," +
                            "witnessed_by VARCHAR(36) DEFAULT NULL," +
                            "recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "processed BOOLEAN NOT NULL DEFAULT FALSE" +
                            ")"
            );

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS police_budget (" +
                            "id INT PRIMARY KEY DEFAULT 1," +
                            "balance DOUBLE NOT NULL DEFAULT 0" +
                            ")"
            );

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS police_requisitions (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "officer_uuid VARCHAR(36) NOT NULL," +
                            "item_type VARCHAR(32) NOT NULL," +
                            "quantity INT NOT NULL DEFAULT 1," +
                            "status VARCHAR(16) NOT NULL DEFAULT 'pending'," +
                            "requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "ordered_at TIMESTAMP NULL" +
                            ")"
            );

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS police_pending_deliveries (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "officer_uuid VARCHAR(36) NOT NULL," +
                            "item_type VARCHAR(32) NOT NULL," +
                            "quantity INT NOT NULL DEFAULT 1," +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ")"
            );

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS phone_numbers (" +
                            "player_uuid VARCHAR(36) PRIMARY KEY," +
                            "phone_number VARCHAR(16) NOT NULL UNIQUE" +
                            ")"
            );
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS contacts (" +
                            "owner_uuid VARCHAR(36) NOT NULL," +
                            "contact_name VARCHAR(32) NOT NULL," +
                            "phone_number VARCHAR(16) NOT NULL," +
                            "PRIMARY KEY (owner_uuid, phone_number)" +
                            ")"
            );
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS conversations (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "is_group BOOLEAN NOT NULL DEFAULT FALSE," +
                            "group_name VARCHAR(64) DEFAULT NULL," +
                            "participant_a VARCHAR(36) DEFAULT NULL," +
                            "participant_b VARCHAR(36) DEFAULT NULL," +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ")"
            );
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS group_members (" +
                            "conversation_id INT NOT NULL," +
                            "player_uuid VARCHAR(36) NOT NULL," +
                            "joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (conversation_id, player_uuid)," +
                            "FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE" +
                            ")"
            );
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS messages (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "conversation_id INT NOT NULL," +
                            "sender_uuid VARCHAR(36) NOT NULL," +
                            "text TEXT NOT NULL," +
                            "sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE" +
                            ")"
            );
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS message_reads (" +
                            "message_id INT NOT NULL," +
                            "reader_uuid VARCHAR(36) NOT NULL," +
                            "PRIMARY KEY (message_id, reader_uuid)," +
                            "FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE" +
                            ")"
            );
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS church (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "player_uuid VARCHAR(36) ," +
                            "last_visit TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ")"
            );

            try {
                stmt.execute("ALTER TABLE police_officers ADD COLUMN salary DOUBLE NOT NULL DEFAULT 0");
                plugin.getLogger().info("Added salary column to police_officers.");
            } catch (SQLException ignored) {}

            try {
                stmt.execute(
                        "CREATE TABLE IF NOT EXISTS black_market_locations (" +
                                "id INT AUTO_INCREMENT PRIMARY KEY," +
                                "world VARCHAR(64) NOT NULL," +
                                "x INT NOT NULL," +
                                "y INT NOT NULL," +
                                "z INT NOT NULL" +
                                ")"
                );
            } catch (SQLException e) {
                plugin.getLogger().severe("Error creating black_market_locations table: " + e.getMessage());
            }

// Seed treasury row if not exists
            stmt.execute("INSERT IGNORE INTO city_treasury (id, balance) VALUES (1, 0)");

            plugin.getLogger().info("Database tables created/verified.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error creating tables: " + e.getMessage());
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed() || !connection.isValid(3)) {
                plugin.getLogger().info("[DB] Connection lost — reconnecting...");
                connect();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[DB] Failed to validate connection: " + e.getMessage());
            connect();
        }
        return connection;
    }
    public void startKeepAlive() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                if (connection == null || connection.isClosed() || !connection.isValid(3)) {
                    plugin.getLogger().info("[DB] Keep-alive: connection lost — reconnecting...");
                    connect();
                } else {
                    // Ping the server to keep the connection alive
                    try (PreparedStatement stmt = connection.prepareStatement("SELECT 1")) {
                        stmt.executeQuery();
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[DB] Keep-alive failed: " + e.getMessage());
                connect();
            }
        }, 20L * 60 * 30, 20L * 60 * 30); // Every 30 minutes
    }
}