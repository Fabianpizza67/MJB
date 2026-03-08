package com.UserMC.MJB;

import org.bukkit.Location;
import org.bukkit.World;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class TerminalManager {

    private final MJB plugin;

    public TerminalManager(MJB plugin) {
        this.plugin = plugin;
    }

    public boolean registerTerminal(Location loc, UUID ownerUuid, String storeRegion) {
        String sql = "INSERT INTO terminals (world, x, y, z, owner_uuid, store_region) " +
                "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE owner_uuid = ?, store_region = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, loc.getWorld().getName());
            stmt.setInt(2, loc.getBlockX());
            stmt.setInt(3, loc.getBlockY());
            stmt.setInt(4, loc.getBlockZ());
            stmt.setString(5, ownerUuid.toString());
            stmt.setString(6, storeRegion);
            stmt.setString(7, ownerUuid.toString());
            stmt.setString(8, storeRegion);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error registering terminal: " + e.getMessage());
            return false;
        }
    }

    public boolean unregisterTerminal(Location loc) {
        String sql = "DELETE FROM terminals WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, loc.getWorld().getName());
            stmt.setInt(2, loc.getBlockX());
            stmt.setInt(3, loc.getBlockY());
            stmt.setInt(4, loc.getBlockZ());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error unregistering terminal: " + e.getMessage());
            return false;
        }
    }

    public boolean isTerminal(Location loc) {
        return getTerminalData(loc) != null;
    }

    public boolean setPrice(Location loc, double price) {
        String sql = "UPDATE terminals SET current_price = ? WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setDouble(1, price);
            stmt.setString(2, loc.getWorld().getName());
            stmt.setInt(3, loc.getBlockX());
            stmt.setInt(4, loc.getBlockY());
            stmt.setInt(5, loc.getBlockZ());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error setting terminal price: " + e.getMessage());
            return false;
        }
    }

    public TerminalData getTerminalData(Location loc) {
        String sql = "SELECT owner_uuid, store_region, current_price FROM terminals " +
                "WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, loc.getWorld().getName());
            stmt.setInt(2, loc.getBlockX());
            stmt.setInt(3, loc.getBlockY());
            stmt.setInt(4, loc.getBlockZ());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new TerminalData(
                        UUID.fromString(rs.getString("owner_uuid")),
                        rs.getString("store_region"),
                        rs.getDouble("current_price")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting terminal data: " + e.getMessage());
        }
        return null;
    }

    public static class TerminalData {
        public final UUID ownerUuid;
        public final String storeRegion;
        public final double currentPrice;

        public TerminalData(UUID ownerUuid, String storeRegion, double currentPrice) {
            this.ownerUuid = ownerUuid;
            this.storeRegion = storeRegion;
            this.currentPrice = currentPrice;
        }
    }
}