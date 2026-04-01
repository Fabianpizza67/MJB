package com.UserMC.MJB;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.*;

public class CrimeManager {

    private final MJB plugin;

    public CrimeManager(MJB plugin) {
        this.plugin = plugin;
    }

    // ---- Ranks ----

    public enum PoliceRank {
        OFFICER("Officer"),
        DETECTIVE("Detective"),
        SERGEANT("Sergeant");

        public final String displayName;
        PoliceRank(String displayName) { this.displayName = displayName; }

        public static PoliceRank fromString(String s) {
            if (s == null) return null;
            for (PoliceRank r : values()) if (r.name().equalsIgnoreCase(s)) return r;
            return null;
        }
    }

    public PoliceRank getRank(UUID uuid) {
        String sql = "SELECT rank FROM police_officers WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String rank = rs.getString("rank");
                return rank != null ? PoliceRank.fromString(rank) : PoliceRank.OFFICER;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting rank: " + e.getMessage());
        }
        return null;
    }

    public boolean setRank(UUID uuid, PoliceRank rank) {
        String sql = "UPDATE police_officers SET rank = ? WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, rank.name());
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) plugin.getNameTagManager().refresh(p);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error setting rank: " + e.getMessage());
            return false;
        }
    }

    public boolean isSergeanT(UUID uuid) {
        return getRank(uuid) == PoliceRank.SERGEANT;
    }

    // ---- Crime records ----

    public boolean addOffence(UUID suspect, String offence, UUID witnessedBy) {
        String sql = "INSERT INTO crime_records (player_uuid, offence, witnessed_by) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, suspect.toString());
            stmt.setString(2, offence);
            stmt.setString(3, witnessedBy != null ? witnessedBy.toString() : null);
            stmt.executeUpdate();
            // Mark as wanted
            setWanted(suspect, true);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error adding offence: " + e.getMessage());
            return false;
        }
    }

    public List<CrimeRecord> getCrimeRecord(UUID playerUuid) {
        List<CrimeRecord> records = new ArrayList<>();
        String sql = "SELECT * FROM crime_records WHERE player_uuid = ? ORDER BY recorded_at DESC";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String wb = rs.getString("witnessed_by");
                records.add(new CrimeRecord(
                        rs.getInt("id"),
                        UUID.fromString(rs.getString("player_uuid")),
                        rs.getString("offence"),
                        wb != null ? UUID.fromString(wb) : null,
                        rs.getTimestamp("recorded_at"),
                        rs.getBoolean("processed")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching crime record: " + e.getMessage());
        }
        return records;
    }

    public List<CrimeRecord> getUnprocessedRecords(UUID playerUuid) {
        List<CrimeRecord> records = new ArrayList<>();
        String sql = "SELECT * FROM crime_records WHERE player_uuid = ? AND processed = FALSE ORDER BY recorded_at DESC";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String wb = rs.getString("witnessed_by");
                records.add(new CrimeRecord(
                        rs.getInt("id"),
                        UUID.fromString(rs.getString("player_uuid")),
                        rs.getString("offence"),
                        wb != null ? UUID.fromString(wb) : null,
                        rs.getTimestamp("recorded_at"),
                        false
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching unprocessed records: " + e.getMessage());
        }
        return records;
    }

    public void markRecordsProcessed(UUID playerUuid) {
        String sql = "UPDATE crime_records SET processed = TRUE WHERE player_uuid = ? AND processed = FALSE";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error marking records processed: " + e.getMessage());
        }
    }

    public void clearCrimeRecord(UUID playerUuid) {
        String sql = "DELETE FROM crime_records WHERE player_uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.executeUpdate();
            setWanted(playerUuid, false);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error clearing crime record: " + e.getMessage());
        }
    }

    // ---- Wanted status ----

    public void setWanted(UUID playerUuid, boolean wanted) {
        String sql = "UPDATE players SET wanted_level = ? WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, wanted ? 1 : 0);
            stmt.setString(2, playerUuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error setting wanted: " + e.getMessage());
        }
    }

    public boolean isWanted(UUID playerUuid) {
        String sql = "SELECT wanted_level FROM players WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("wanted_level") > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking wanted: " + e.getMessage());
        }
        return false;
    }

    public List<UUID> getAllWantedPlayers() {
        List<UUID> wanted = new ArrayList<>();
        String sql = "SELECT uuid FROM players WHERE wanted_level > 0";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) wanted.add(UUID.fromString(rs.getString("uuid")));
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching wanted players: " + e.getMessage());
        }
        return wanted;
    }

    // ---- Police witness check ----

    /**
     * Returns the nearest online police officer within range, or null if none.
     * Used to determine if a crime was witnessed.
     */
    public Player getNearbyOfficer(Location loc, double range) {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (!plugin.getPoliceManager().isOfficer(p.getUniqueId())) continue;
            if (!p.getWorld().equals(loc.getWorld())) continue;
            if (p.getLocation().distance(loc) <= range) return p;
        }
        return null;
    }

    // ---- Relay 911 to officers ----

    public int relay911(Player caller, String message) {
        int count = 0;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (plugin.getPoliceManager().isOfficer(p.getUniqueId())) {
                p.sendMessage("§c§l[911] §c" + "§c: §f" + message);
                count++;
            }
        }
        return count;
    }

    // ---- Data classes ----

    public static class CrimeRecord {
        public final int id;
        public final UUID playerUuid;
        public final String offence;
        public final UUID witnessedBy;
        public final Timestamp recordedAt;
        public final boolean processed;

        public CrimeRecord(int id, UUID playerUuid, String offence, UUID witnessedBy,
                           Timestamp recordedAt, boolean processed) {
            this.id = id;
            this.playerUuid = playerUuid;
            this.offence = offence;
            this.witnessedBy = witnessedBy;
            this.recordedAt = recordedAt;
            this.processed = processed;
        }
    }
}