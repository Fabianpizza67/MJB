package com.UserMC.MJB;

import java.sql.*;
import java.util.Properties;

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

            String url = "jdbc:mariadb://" + host + ":" + port + "/" + database + "?useSSL=false";

            if (connection != null && !connection.isClosed()) {
                connection.close();
            }

            Properties props = new Properties();
            props.setProperty("user", username);
            props.setProperty("password", password);
            props.setProperty("autoReconnect", "true");
            props.setProperty("connectTimeout", "30000");
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

    public void createTables() {
        Connection conn = getConnection();
        if (conn == null) {
            plugin.getLogger().severe("[DB] Cannot create tables because connection is null.");
            return;
        }

        try (Statement stmt = conn.createStatement()) {

            // =========================================================
            // BASE TABLES
            // =========================================================

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS players (" +
                            "uuid VARCHAR(36) PRIMARY KEY," +
                            "username VARCHAR(16) NOT NULL," +
                            "bank_balance DOUBLE NOT NULL DEFAULT 0," +
                            "cash_balance DOUBLE NOT NULL DEFAULT 0," +
                            "wanted_level INT NOT NULL DEFAULT 0," +
                            "has_claimed_starter BOOLEAN NOT NULL DEFAULT FALSE," +
                            "joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "card_version INT NOT NULL DEFAULT 1," +
                            "thirst INT NOT NULL DEFAULT 20," +
                            "blood_type VARCHAR(4) DEFAULT NULL," +
                            "id_card_version INT NOT NULL DEFAULT 1" +
                            ")",
                    "Create players table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS starter_apartments (" +
                            "region_id VARCHAR(64) PRIMARY KEY," +
                            "world VARCHAR(64) NOT NULL," +
                            "is_claimed BOOLEAN NOT NULL DEFAULT FALSE," +
                            "claimed_by VARCHAR(36) DEFAULT NULL" +
                            ")",
                    "Create starter_apartments table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS plots (" +
                            "region_id VARCHAR(64) NOT NULL," +
                            "world VARCHAR(64) NOT NULL," +
                            "owner_uuid VARCHAR(36) NOT NULL," +
                            "plot_type VARCHAR(32) NOT NULL DEFAULT 'apartment'," +
                            "purchased_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (region_id, world)" +
                            ")",
                    "Create plots table");

            execute(stmt,
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
                            ")",
                    "Create terminals table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS supply_orders (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "owner_uuid VARCHAR(36) NOT NULL," +
                            "district VARCHAR(64) NOT NULL," +
                            "status VARCHAR(16) NOT NULL DEFAULT 'pending'," +
                            "total_cost DOUBLE NOT NULL DEFAULT 0," +
                            "ordered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "ready_at TIMESTAMP NULL," +
                            "company_id INT DEFAULT NULL" +
                            ")",
                    "Create supply_orders table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS supply_order_items (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "order_id INT NOT NULL," +
                            "material VARCHAR(64) NOT NULL," +
                            "quantity INT NOT NULL," +
                            "price_per_item DOUBLE NOT NULL," +
                            "FOREIGN KEY (order_id) REFERENCES supply_orders(id) ON DELETE CASCADE" +
                            ")",
                    "Create supply_order_items table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS supply_order_authorizations (" +
                            "order_id INT NOT NULL," +
                            "authorized_uuid VARCHAR(36) NOT NULL," +
                            "PRIMARY KEY (order_id, authorized_uuid)," +
                            "FOREIGN KEY (order_id) REFERENCES supply_orders(id) ON DELETE CASCADE" +
                            ")",
                    "Create supply_order_authorizations table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS supply_items (" +
                            "material VARCHAR(64) PRIMARY KEY," +
                            "license_required VARCHAR(64) NOT NULL," +
                            "price_per_item DOUBLE NOT NULL," +
                            "delivery_seconds INT NOT NULL DEFAULT 1800" +
                            ")",
                    "Create supply_items table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS pickup_npcs (" +
                            "npc_id INT PRIMARY KEY," +
                            "district VARCHAR(64) NOT NULL UNIQUE" +
                            ")",
                    "Create pickup_npcs table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS computers (" +
                            "world VARCHAR(64) NOT NULL," +
                            "x INT NOT NULL," +
                            "y INT NOT NULL," +
                            "z INT NOT NULL," +
                            "owner_uuid VARCHAR(36) NOT NULL," +
                            "PRIMARY KEY (world, x, y, z)" +
                            ")",
                    "Create computers table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS cancelled_cards (" +
                            "player_uuid VARCHAR(36) PRIMARY KEY" +
                            ")",
                    "Create cancelled_cards table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS companies (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "name VARCHAR(64) NOT NULL UNIQUE," +
                            "type VARCHAR(32) NOT NULL," +
                            "description TEXT," +
                            "owner_uuid VARCHAR(36) NOT NULL," +
                            "bank_balance DOUBLE NOT NULL DEFAULT 0," +
                            "is_bankrupt BOOLEAN NOT NULL DEFAULT FALSE," +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ")",
                    "Create companies table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS company_members (" +
                            "company_id INT NOT NULL," +
                            "player_uuid VARCHAR(36) NOT NULL," +
                            "role_name VARCHAR(32) NOT NULL DEFAULT 'employee'," +
                            "salary DOUBLE NOT NULL DEFAULT 0," +
                            "joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (company_id, player_uuid)," +
                            "FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE" +
                            ")",
                    "Create company_members table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS company_roles (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "company_id INT NOT NULL," +
                            "role_name VARCHAR(32) NOT NULL," +
                            "can_hire_fire BOOLEAN NOT NULL DEFAULT FALSE," +
                            "can_set_prices BOOLEAN NOT NULL DEFAULT FALSE," +
                            "can_access_bank BOOLEAN NOT NULL DEFAULT FALSE," +
                            "place_orders BOOLEAN NOT NULL DEFAULT FALSE," +
                            "UNIQUE KEY role_per_company (company_id, role_name)," +
                            "FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE" +
                            ")",
                    "Create company_roles table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS company_plots (" +
                            "region_id VARCHAR(64) NOT NULL," +
                            "world VARCHAR(64) NOT NULL," +
                            "company_id INT NOT NULL," +
                            "PRIMARY KEY (region_id, world)," +
                            "FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE" +
                            ")",
                    "Create company_plots table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS property_listings (" +
                            "region_id VARCHAR(64) NOT NULL," +
                            "world VARCHAR(64) NOT NULL," +
                            "plot_type VARCHAR(32) NOT NULL," +
                            "district VARCHAR(64) NOT NULL DEFAULT 'unknown'," +
                            "price DOUBLE NOT NULL," +
                            "is_available BOOLEAN NOT NULL DEFAULT TRUE," +
                            "listed_by VARCHAR(36) DEFAULT NULL," +
                            "listed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (region_id, world)" +
                            ")",
                    "Create property_listings table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS city_treasury (" +
                            "id INT PRIMARY KEY DEFAULT 1," +
                            "balance DOUBLE NOT NULL DEFAULT 0" +
                            ")",
                    "Create city_treasury table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS license_types (" +
                            "type_name VARCHAR(64) PRIMARY KEY," +
                            "display_name VARCHAR(64) NOT NULL," +
                            "cost DOUBLE NOT NULL DEFAULT 100.0," +
                            "renewal_cost DOUBLE NOT NULL DEFAULT 50.0," +
                            "description VARCHAR(256)" +
                            ")",
                    "Create license_types table");

            execute(stmt,
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
                            ")",
                    "Create licenses table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS license_craft_rules (" +
                            "result_material VARCHAR(64) PRIMARY KEY," +
                            "license_type VARCHAR(64) NOT NULL," +
                            "FOREIGN KEY (license_type) REFERENCES license_types(type_name) ON DELETE CASCADE" +
                            ")",
                    "Create license_craft_rules table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS parties (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "name VARCHAR(64) NOT NULL UNIQUE," +
                            "leader_uuid VARCHAR(36) NOT NULL," +
                            "description VARCHAR(256)," +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ")",
                    "Create parties table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS party_members (" +
                            "party_id INT NOT NULL," +
                            "player_uuid VARCHAR(36) NOT NULL," +
                            "joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (party_id, player_uuid)," +
                            "FOREIGN KEY (party_id) REFERENCES parties(id) ON DELETE CASCADE" +
                            ")",
                    "Create party_members table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS elections (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "status VARCHAR(16) NOT NULL DEFAULT 'active'," +
                            "started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "ends_at TIMESTAMP NOT NULL" +
                            ")",
                    "Create elections table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS election_votes (" +
                            "election_id INT NOT NULL," +
                            "voter_uuid VARCHAR(36) NOT NULL," +
                            "party_id INT NOT NULL," +
                            "PRIMARY KEY (election_id, voter_uuid)," +
                            "FOREIGN KEY (election_id) REFERENCES elections(id) ON DELETE CASCADE" +
                            ")",
                    "Create election_votes table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS election_results (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "election_id INT NOT NULL," +
                            "party_id INT NOT NULL," +
                            "seats INT NOT NULL DEFAULT 0," +
                            "is_winner BOOLEAN NOT NULL DEFAULT FALSE," +
                            "FOREIGN KEY (election_id) REFERENCES elections(id) ON DELETE CASCADE" +
                            ")",
                    "Create election_results table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS council_sessions (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "status VARCHAR(16) NOT NULL DEFAULT 'active'," +
                            "started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "ends_at TIMESTAMP NOT NULL" +
                            ")",
                    "Create council_sessions table");

            execute(stmt,
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
                            ")",
                    "Create proposals table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS proposal_votes (" +
                            "proposal_id INT NOT NULL," +
                            "voter_uuid VARCHAR(36) NOT NULL," +
                            "vote BOOLEAN NOT NULL," +
                            "seats_used INT NOT NULL DEFAULT 1," +
                            "PRIMARY KEY (proposal_id, voter_uuid)," +
                            "FOREIGN KEY (proposal_id) REFERENCES proposals(id) ON DELETE CASCADE" +
                            ")",
                    "Create proposal_votes table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS laws (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "title VARCHAR(512) NOT NULL," +
                            "law_type VARCHAR(64) NOT NULL," +
                            "law_value VARCHAR(128) NOT NULL DEFAULT 'true'," +
                            "passed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "passed_by_proposal_id INT DEFAULT NULL," +
                            "is_active BOOLEAN NOT NULL DEFAULT TRUE" +
                            ")",
                    "Create laws table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS council_regions (" +
                            "region_id VARCHAR(64) NOT NULL," +
                            "world VARCHAR(64) NOT NULL," +
                            "PRIMARY KEY (region_id, world)" +
                            ")",
                    "Create council_regions table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS government_settings (" +
                            "setting_key VARCHAR(64) PRIMARY KEY," +
                            "value VARCHAR(256) NOT NULL" +
                            ")",
                    "Create government_settings table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS government_state (" +
                            "id INT PRIMARY KEY DEFAULT 1," +
                            "mayor_uuid VARCHAR(36) DEFAULT NULL" +
                            ")",
                    "Create government_state table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS police_officers (" +
                            "uuid VARCHAR(36) PRIMARY KEY," +
                            "appointed_by VARCHAR(36) DEFAULT NULL," +
                            "appointed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "rank VARCHAR(16) NOT NULL DEFAULT 'OFFICER'," +
                            "salary DOUBLE NOT NULL DEFAULT 0" +
                            ")",
                    "Create police_officers table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS crime_records (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "player_uuid VARCHAR(36) NOT NULL," +
                            "offence VARCHAR(256) NOT NULL," +
                            "witnessed_by VARCHAR(36) DEFAULT NULL," +
                            "recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "processed BOOLEAN NOT NULL DEFAULT FALSE" +
                            ")",
                    "Create crime_records table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS police_budget (" +
                            "id INT PRIMARY KEY DEFAULT 1," +
                            "balance DOUBLE NOT NULL DEFAULT 0" +
                            ")",
                    "Create police_budget table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS police_requisitions (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "officer_uuid VARCHAR(36) NOT NULL," +
                            "item_type VARCHAR(32) NOT NULL," +
                            "quantity INT NOT NULL DEFAULT 1," +
                            "status VARCHAR(16) NOT NULL DEFAULT 'pending'," +
                            "requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "ordered_at TIMESTAMP NULL" +
                            ")",
                    "Create police_requisitions table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS police_pending_deliveries (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "officer_uuid VARCHAR(36) NOT NULL," +
                            "item_type VARCHAR(32) NOT NULL," +
                            "quantity INT NOT NULL DEFAULT 1," +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ")",
                    "Create police_pending_deliveries table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS phone_numbers (" +
                            "player_uuid VARCHAR(36) PRIMARY KEY," +
                            "phone_number VARCHAR(16) NOT NULL UNIQUE" +
                            ")",
                    "Create phone_numbers table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS contacts (" +
                            "owner_uuid VARCHAR(36) NOT NULL," +
                            "contact_name VARCHAR(32) NOT NULL," +
                            "phone_number VARCHAR(16) NOT NULL," +
                            "PRIMARY KEY (owner_uuid, phone_number)" +
                            ")",
                    "Create contacts table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS conversations (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "is_group BOOLEAN NOT NULL DEFAULT FALSE," +
                            "group_name VARCHAR(64) DEFAULT NULL," +
                            "participant_a VARCHAR(36) DEFAULT NULL," +
                            "participant_b VARCHAR(36) DEFAULT NULL," +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ")",
                    "Create conversations table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS group_members (" +
                            "conversation_id INT NOT NULL," +
                            "player_uuid VARCHAR(36) NOT NULL," +
                            "joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (conversation_id, player_uuid)," +
                            "FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE" +
                            ")",
                    "Create group_members table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS messages (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "conversation_id INT NOT NULL," +
                            "sender_uuid VARCHAR(36) NOT NULL," +
                            "text TEXT NOT NULL," +
                            "sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE" +
                            ")",
                    "Create messages table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS message_reads (" +
                            "message_id INT NOT NULL," +
                            "reader_uuid VARCHAR(36) NOT NULL," +
                            "PRIMARY KEY (message_id, reader_uuid)," +
                            "FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE" +
                            ")",
                    "Create message_reads table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS church (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "player_uuid VARCHAR(36)," +
                            "last_visit TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ")",
                    "Create church table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS tutorial_progress (" +
                            "player_uuid VARCHAR(36) PRIMARY KEY," +
                            "visited_bank BOOLEAN NOT NULL DEFAULT FALSE," +
                            "claimed_apartment BOOLEAN NOT NULL DEFAULT FALSE," +
                            "checked_phone BOOLEAN NOT NULL DEFAULT FALSE," +
                            "visited_gov BOOLEAN NOT NULL DEFAULT FALSE," +
                            "visited_realestate BOOLEAN NOT NULL DEFAULT FALSE," +
                            "made_choice BOOLEAN NOT NULL DEFAULT FALSE," +
                            "completed BOOLEAN NOT NULL DEFAULT FALSE" +
                            ")",
                    "Create tutorial_progress table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS hospital_budget (" +
                            "id INT PRIMARY KEY DEFAULT 1," +
                            "balance DOUBLE NOT NULL DEFAULT 0" +
                            ")",
                    "Create hospital_budget table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS hospital_doctors (" +
                            "uuid VARCHAR(36) PRIMARY KEY," +
                            "appointed_by VARCHAR(36) DEFAULT NULL," +
                            "appointed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "rank VARCHAR(16) NOT NULL DEFAULT 'INTERN'," +
                            "salary DOUBLE NOT NULL DEFAULT 0" +
                            ")",
                    "Create hospital_doctors table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS hospital_supply_requests (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "requester_uuid VARCHAR(36) NOT NULL," +
                            "injury_type VARCHAR(32) NOT NULL," +
                            "status VARCHAR(16) NOT NULL DEFAULT 'pending'," +
                            "requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "approved_at TIMESTAMP NULL" +
                            ")",
                    "Create hospital_supply_requests table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS hospital_pending_deliveries (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "doctor_uuid VARCHAR(36) NOT NULL," +
                            "injury_type VARCHAR(32) NOT NULL," +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ")",
                    "Create hospital_pending_deliveries table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS patient_medical_records (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "patient_uuid VARCHAR(36) NOT NULL," +
                            "doctor_uuid VARCHAR(36) NOT NULL," +
                            "injury_type VARCHAR(32) NOT NULL," +
                            "treatment_cost DOUBLE NOT NULL DEFAULT 0," +
                            "blood_type_used VARCHAR(4) DEFAULT NULL," +
                            "morphine_used BOOLEAN NOT NULL DEFAULT FALSE," +
                            "notes VARCHAR(256) DEFAULT NULL," +
                            "treated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ")",
                    "Create patient_medical_records table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS drug_planted_locations (" +
                            "world VARCHAR(64) NOT NULL," +
                            "x INT NOT NULL," +
                            "y INT NOT NULL," +
                            "z INT NOT NULL," +
                            "drug_type VARCHAR(16) NOT NULL," +
                            "planted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (world, x, y, z)" +
                            ")",
                    "Create drug_planted_locations table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS drug_usage (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "player_uuid VARCHAR(36) NOT NULL," +
                            "drug_type VARCHAR(16) NOT NULL," +
                            "used_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ")",
                    "Create drug_usage table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS drug_addiction (" +
                            "player_uuid VARCHAR(36) NOT NULL," +
                            "drug_type VARCHAR(16) NOT NULL," +
                            "stage INT NOT NULL DEFAULT 1," +
                            "PRIMARY KEY (player_uuid, drug_type)" +
                            ")",
                    "Create drug_addiction table"
            );

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS drug_harvest_cooldowns (" +
                            "world VARCHAR(64) NOT NULL," +
                            "x INT NOT NULL," +
                            "y INT NOT NULL," +
                            "z INT NOT NULL," +
                            "last_harvested TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (world, x, y, z)" +
                            ")",
                    "Create drug_harvest_cooldowns table"
            );

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS morphine_usage (" +
                            "player_uuid VARCHAR(36) NOT NULL," +
                            "used_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ")",
                    "Create morphine_usage table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS morphine_addiction (" +
                            "player_uuid VARCHAR(36) PRIMARY KEY," +
                            "stage INT NOT NULL DEFAULT 1," +
                            "last_use TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ")",
                    "Create morphine_addiction table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS black_market_locations (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "world VARCHAR(64) NOT NULL," +
                            "x INT NOT NULL," +
                            "y INT NOT NULL," +
                            "z INT NOT NULL" +
                            ")",
                    "Create black_market_locations table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS jail_sentences (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY," +
                            "player_uuid VARCHAR(36) NOT NULL," +
                            "sentenced_by VARCHAR(36) NOT NULL," +
                            "original_minutes INT NOT NULL DEFAULT 0," +
                            "sentenced_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "release_at TIMESTAMP NOT NULL," +
                            "is_released BOOLEAN NOT NULL DEFAULT FALSE" +
                            ")",
                    "Create jail_sentences table");

            execute(stmt,
                    "CREATE TABLE IF NOT EXISTS jail_release_location (" +
                            "id INT PRIMARY KEY DEFAULT 1," +
                            "world VARCHAR(64) NOT NULL," +
                            "x DOUBLE NOT NULL," +
                            "y DOUBLE NOT NULL," +
                            "z DOUBLE NOT NULL," +
                            "yaw FLOAT NOT NULL DEFAULT 0," +
                            "pitch FLOAT NOT NULL DEFAULT 0" +
                            ")",
                    "Create jail_release_location table");

            // =========================================================
            // MIGRATIONS FOR EXISTING DATABASES
            // =========================================================

            safeAlter(stmt, "ALTER TABLE players ADD COLUMN has_claimed_starter BOOLEAN NOT NULL DEFAULT FALSE", "players.has_claimed_starter");
            safeAlter(stmt, "ALTER TABLE players ADD COLUMN thirst INT NOT NULL DEFAULT 20", "players.thirst");
            safeAlter(stmt, "ALTER TABLE players ADD COLUMN blood_type VARCHAR(4) DEFAULT NULL", "players.blood_type");
            safeAlter(stmt, "ALTER TABLE players ADD COLUMN id_card_version INT NOT NULL DEFAULT 1", "players.id_card_version");

            safeAlter(stmt, "ALTER TABLE supply_orders ADD COLUMN company_id INT DEFAULT NULL", "supply_orders.company_id");

            safeAlter(stmt, "ALTER TABLE company_roles ADD COLUMN place_orders BOOLEAN NOT NULL DEFAULT FALSE", "company_roles.place_orders");

            safeAlter(stmt, "ALTER TABLE hospital_doctors ADD COLUMN rank VARCHAR(16) NOT NULL DEFAULT 'INTERN'", "hospital_doctors.rank");
            safeAlter(stmt, "ALTER TABLE hospital_doctors ADD COLUMN salary DOUBLE NOT NULL DEFAULT 0", "hospital_doctors.salary");

            safeAlter(stmt, "ALTER TABLE police_officers ADD COLUMN salary DOUBLE NOT NULL DEFAULT 0", "police_officers.salary");

            // =========================================================
            // DEFAULT ROWS
            // =========================================================

            execute(stmt, "INSERT IGNORE INTO government_state (id) VALUES (1)", "Insert default government_state row");
            execute(stmt, "INSERT IGNORE INTO police_budget (id, balance) VALUES (1, 2000)", "Insert default police_budget row");
            execute(stmt, "INSERT IGNORE INTO hospital_budget (id, balance) VALUES (1, 0)", "Insert default hospital_budget row");
            execute(stmt, "INSERT IGNORE INTO city_treasury (id, balance) VALUES (1, 0)", "Insert default city_treasury row");

            plugin.getLogger().info("[DB] Database tables created/verified successfully.");

        } catch (SQLException e) {
            plugin.getLogger().severe("[DB] Fatal error while creating/verifying tables: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void execute(Statement stmt, String sql, String description) {
        try {
            stmt.execute(sql);
            plugin.getLogger().info("[DB] OK: " + description);
        } catch (SQLException e) {
            plugin.getLogger().severe("[DB] FAILED: " + description + " -> " + e.getMessage());
        }
    }

    private void safeAlter(Statement stmt, String sql, String description) {
        try {
            stmt.execute(sql);
            plugin.getLogger().info("[DB] Migration applied: " + description);
        } catch (SQLException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();

            if (msg.contains("duplicate column")
                    || msg.contains("already exists")
                    || msg.contains("check that column/key exists")) {
                // Harmless: column already exists
                return;
            }

            plugin.getLogger().warning("[DB] Migration skipped/failed: " + description + " -> " + e.getMessage());
        }
    }
}