package com.UserMC.MJB;

import org.bukkit.entity.Player;

import java.sql.*;
import java.util.UUID;

public class EconomyManager {

    private final MJB plugin;
    private final DatabaseManager db;

    public EconomyManager(MJB plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
    }

    public boolean isNewPlayer(UUID uuid) {
        String sql = "SELECT uuid FROM players WHERE uuid = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            return !rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    public void initPlayer(UUID uuid, String username) {
        String sql = """
            INSERT IGNORE INTO players (uuid, username, bank_balance, cash_balance)
            VALUES (?, ?, 500.0, 0.0)
        """;
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error initializing player: " + e.getMessage());
        }
    }

    public double getBankBalance(UUID uuid) {
        String sql = "SELECT bank_balance FROM players WHERE uuid = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getDouble("bank_balance");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting bank balance: " + e.getMessage());
        }
        return 0;
    }

    public double getCashBalance(UUID uuid) {
        String sql = "SELECT cash_balance FROM players WHERE uuid = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getDouble("cash_balance");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting cash balance: " + e.getMessage());
        }
        return 0;
    }

    public boolean depositCash(Player player, double amount) {
        if (amount <= 0) return false;
        double inInventory = plugin.getCashItemManager().getTotalCashInInventory(player);
        if (inInventory < amount - 0.001) return false;

        double total = plugin.getCashItemManager().removeAllCash(player);
        double change = Math.round((total - amount) * 100.0) / 100.0;

        String sql = "UPDATE players SET bank_balance = bank_balance + ? WHERE uuid = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
            stmt.setDouble(1, amount);
            stmt.setString(2, player.getUniqueId().toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error depositing cash: " + e.getMessage());
            return false;
        }

        if (change > 0.001) {
            plugin.getCashItemManager().giveCash(player, change);
        }

        return true;
    }

    public boolean withdrawCash(Player player, double amount) {
        if (amount <= 0) return false;
        if (getBankBalance(player.getUniqueId()) < amount) return false;

        String sql = "UPDATE players SET bank_balance = bank_balance - ? WHERE uuid = ?";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(sql)) {
            stmt.setDouble(1, amount);
            stmt.setString(2, player.getUniqueId().toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error withdrawing cash: " + e.getMessage());
            return false;
        }

        plugin.getCashItemManager().giveCash(player, amount);
        return true;
    }

    public boolean transferBank(UUID from, UUID to, double amount) {
        if (amount <= 0) return false;
        if (getBankBalance(from) < amount) return false;

        String sql1 = "UPDATE players SET bank_balance = bank_balance - ? WHERE uuid = ?";
        String sql2 = "UPDATE players SET bank_balance = bank_balance + ? WHERE uuid = ?";
        try (PreparedStatement stmt1 = db.getConnection().prepareStatement(sql1);
             PreparedStatement stmt2 = db.getConnection().prepareStatement(sql2)) {
            stmt1.setDouble(1, amount);
            stmt1.setString(2, from.toString());
            stmt1.executeUpdate();
            stmt2.setDouble(1, amount);
            stmt2.setString(2, to.toString());
            stmt2.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error transferring balance: " + e.getMessage());
            return false;
        }
    }

    public String format(double amount) {
        if (amount == Math.floor(amount)) {
            return "$" + String.format("%.0f", amount);
        }
        return "$" + String.format("%.2f", amount);
    }
}