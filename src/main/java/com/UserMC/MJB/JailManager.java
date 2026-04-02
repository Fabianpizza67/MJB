package com.UserMC.MJB;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class JailManager {

    private final MJB plugin;
    // Tracks scheduled release task IDs so we can cancel+reschedule on /judge reduce
    private final Map<UUID, Integer> scheduledReleases = new ConcurrentHashMap<>();

    public JailManager(MJB plugin) {
        this.plugin = plugin;
    }

    // ---- Release location ----

    public boolean setReleaseLocation(Location loc) {
        String sql = "INSERT INTO jail_release_location " +
                "(id, world, x, y, z, yaw, pitch) VALUES (1, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE world=?, x=?, y=?, z=?, yaw=?, pitch=?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, loc.getWorld().getName());
            stmt.setDouble(2, loc.getX());
            stmt.setDouble(3, loc.getY());
            stmt.setDouble(4, loc.getZ());
            stmt.setFloat(5, loc.getYaw());
            stmt.setFloat(6, loc.getPitch());
            stmt.setString(7, loc.getWorld().getName());
            stmt.setDouble(8, loc.getX());
            stmt.setDouble(9, loc.getY());
            stmt.setDouble(10, loc.getZ());
            stmt.setFloat(11, loc.getYaw());
            stmt.setFloat(12, loc.getPitch());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error setting jail release location: " + e.getMessage());
            return false;
        }
    }

    public Location getReleaseLocation() {
        String sql = "SELECT world, x, y, z, yaw, pitch FROM jail_release_location WHERE id = 1";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                World world = plugin.getServer().getWorld(rs.getString("world"));
                if (world == null) return null;
                return new Location(world,
                        rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                        rs.getFloat("yaw"), rs.getFloat("pitch"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting jail release location: " + e.getMessage());
        }
        return null;
    }

    // ---- Sentences ----

    public boolean sentence(UUID playerUuid, UUID judgeUuid, int minutes) {
        // Cancel any existing active sentence first
        clearActiveSentence(playerUuid);

        String sql = "INSERT INTO jail_sentences " +
                "(player_uuid, sentenced_by, original_minutes, release_at) " +
                "VALUES (?, ?, ?, DATE_ADD(NOW(), INTERVAL ? MINUTE))";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, judgeUuid.toString());
            stmt.setInt(3, minutes);
            stmt.setInt(4, minutes);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error inserting sentence: " + e.getMessage());
            return false;
        }

        // Notify the player if online
        Player player = plugin.getServer().getPlayer(playerUuid);
        if (player != null) {
            player.sendMessage("§4§l[Judge] §4You have been sentenced to §f" + minutes +
                    " minutes §4in jail.");
            player.sendMessage("§7Comply with the police and serve your time.");
        }

        scheduleRelease(playerUuid, (long) minutes * 60 * 20);
        return true;
    }

    public boolean release(UUID playerUuid, boolean timed) {
        if (!isJailed(playerUuid)) return false;

        // Get original minutes for the crime record note
        long servedSeconds = getOriginalMinutes(playerUuid) * 60L - getRemainingSeconds(playerUuid);
        long servedMinutes = Math.max(0, servedSeconds / 60);

        clearActiveSentence(playerUuid);

        // Cancel scheduled task if releasing early
        Integer taskId = scheduledReleases.remove(playerUuid);
        if (taskId != null) plugin.getServer().getScheduler().cancelTask(taskId);

        // Add served time to crime record (processed = TRUE, no wanted status)
        String recordSql = "INSERT INTO crime_records " +
                "(player_uuid, offence, witnessed_by, processed) VALUES (?, ?, NULL, TRUE)";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(recordSql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, "Served " + servedMinutes + " minute(s) in jail");
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error adding served time record: " + e.getMessage());
        }

        // Teleport to release location if online
        Player player = plugin.getServer().getPlayer(playerUuid);
        Location releaseLoc = getReleaseLocation();
        if (player != null) {
            if (releaseLoc != null) {
                player.teleport(releaseLoc);
            }
            if (timed) {
                player.sendMessage("§a§l[Jail] §aYour sentence is complete. You are free to go.");
            } else {
                player.sendMessage("§a§l[Jail] §aYou have been released early by the judge.");
            }
        }

        // Notify all online officers to return the inventory
        String playerName = plugin.getServer().getOfflinePlayer(playerUuid).getName();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (plugin.getPoliceManager().isOfficer(p.getUniqueId())) {
                p.sendMessage("§b§l[Jail] §f" + (playerName != null ? playerName : "A prisoner") +
                        " §fhas been released. §7Please return their confiscated items.");
            }
        }
        return true;
    }

    public boolean reduceTime(UUID playerUuid, int minutes) {
        long remaining = getRemainingSeconds(playerUuid);
        if (remaining <= 0) return false;

        // If reduction covers the rest of the sentence, just release
        if (remaining <= (long) minutes * 60) {
            return release(playerUuid, false);
        }

        String sql = "UPDATE jail_sentences " +
                "SET release_at = DATE_SUB(release_at, INTERVAL ? MINUTE) " +
                "WHERE player_uuid = ? AND is_released = FALSE AND release_at > NOW()";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setInt(1, minutes);
            stmt.setString(2, playerUuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error reducing sentence: " + e.getMessage());
            return false;
        }

        // Cancel old task and reschedule with updated time
        Integer oldTask = scheduledReleases.remove(playerUuid);
        if (oldTask != null) plugin.getServer().getScheduler().cancelTask(oldTask);

        long newRemaining = getRemainingSeconds(playerUuid);
        scheduleRelease(playerUuid, newRemaining * 20L);

        Player player = plugin.getServer().getPlayer(playerUuid);
        if (player != null) {
            player.sendMessage("§a§l[Judge] §aYour sentence has been reduced by §f" +
                    minutes + " minutes§a.");
        }
        return true;
    }

    // ---- Queries ----

    public boolean isJailed(UUID playerUuid) {
        String sql = "SELECT 1 FROM jail_sentences WHERE player_uuid = ? " +
                "AND is_released = FALSE AND release_at > NOW()";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }

    public long getRemainingSeconds(UUID playerUuid) {
        String sql = "SELECT TIMESTAMPDIFF(SECOND, NOW(), release_at) AS remaining " +
                "FROM jail_sentences " +
                "WHERE player_uuid = ? AND is_released = FALSE AND release_at > NOW()";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getLong("remaining");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting remaining time: " + e.getMessage());
        }
        return 0;
    }

    private int getOriginalMinutes(UUID playerUuid) {
        String sql = "SELECT original_minutes FROM jail_sentences " +
                "WHERE player_uuid = ? AND is_released = FALSE ORDER BY sentenced_at DESC LIMIT 1";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("original_minutes");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting original minutes: " + e.getMessage());
        }
        return 0;
    }

    public List<JailRecord> getActiveSentences() {
        List<JailRecord> records = new ArrayList<>();
        String sql = "SELECT player_uuid, sentenced_by, sentenced_at, release_at, original_minutes " +
                "FROM jail_sentences WHERE is_released = FALSE AND release_at > NOW()";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                records.add(new JailRecord(
                        UUID.fromString(rs.getString("player_uuid")),
                        UUID.fromString(rs.getString("sentenced_by")),
                        rs.getTimestamp("sentenced_at"),
                        rs.getTimestamp("release_at"),
                        rs.getInt("original_minutes")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching active sentences: " + e.getMessage());
        }
        return records;
    }

    // ---- Internal helpers ----

    private void clearActiveSentence(UUID playerUuid) {
        String sql = "UPDATE jail_sentences SET is_released = TRUE " +
                "WHERE player_uuid = ? AND is_released = FALSE";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error clearing sentence: " + e.getMessage());
        }
    }

    private void scheduleRelease(UUID playerUuid, long ticks) {
        Integer existing = scheduledReleases.get(playerUuid);
        if (existing != null) plugin.getServer().getScheduler().cancelTask(existing);

        int taskId = plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            scheduledReleases.remove(playerUuid);
            if (!isJailed(playerUuid)) return; // already released manually
            release(playerUuid, true);
        }, ticks);
        scheduledReleases.put(playerUuid, taskId);
    }

    // ---- Restart recovery ----

    public void recoverPendingReleases() {
        // Mark overdue sentences as released (server was offline when they expired)
        String clearSql = "UPDATE jail_sentences SET is_released = TRUE " +
                "WHERE is_released = FALSE AND release_at <= NOW()";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(clearSql)) {
            int released = stmt.executeUpdate();
            if (released > 0) {
                plugin.getLogger().info("[Jail] Cleared " + released +
                        " sentence(s) that expired while server was offline.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error clearing overdue sentences: " + e.getMessage());
        }

        // Reschedule still-active sentences
        String sql = "SELECT player_uuid, release_at FROM jail_sentences " +
                "WHERE is_released = FALSE AND release_at > NOW()";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            int count = 0;
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                long releaseMs = rs.getTimestamp("release_at").getTime();
                long remainingMs = releaseMs - System.currentTimeMillis();
                long ticks = Math.max(1L, remainingMs / 50L);
                scheduleRelease(uuid, ticks);
                count++;
            }
            if (count > 0) {
                plugin.getLogger().info("[Jail] Recovered " + count +
                        " active sentence(s).");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error recovering jail sentences: " + e.getMessage());
        }
    }

    // ---- Data class ----

    public static class JailRecord {
        public final UUID playerUuid;
        public final UUID sentencedBy;
        public final Timestamp sentencedAt;
        public final Timestamp releaseAt;
        public final int originalMinutes;

        public JailRecord(UUID playerUuid, UUID sentencedBy,
                          Timestamp sentencedAt, Timestamp releaseAt,
                          int originalMinutes) {
            this.playerUuid = playerUuid;
            this.sentencedBy = sentencedBy;
            this.sentencedAt = sentencedAt;
            this.releaseAt = releaseAt;
            this.originalMinutes = originalMinutes;
        }
    }
}