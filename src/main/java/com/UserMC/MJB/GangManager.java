package com.UserMC.MJB;

import org.bukkit.entity.Player;

import java.sql.*;
import java.util.*;

public class GangManager {

    private final MJB plugin;
    private final Map<UUID, Integer> pendingInvites = new HashMap<>();

    public GangManager(MJB plugin) {
        this.plugin = plugin;
    }

    // ---- Creation & disbanding ----

    public boolean nameExists(String name) {
        String sql = "SELECT 1 FROM gangs WHERE LOWER(name) = LOWER(?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, name);
            return stmt.executeQuery().next();
        } catch (SQLException e) { return false; }
    }

    public int createGang(UUID leaderUuid, String name) {
        if (nameExists(name)) return -2;
        if (getGangByMember(leaderUuid) != null) return -3;

        String sql = "INSERT INTO gangs (name, leader_uuid) VALUES (?, ?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setString(2, leaderUuid.toString());
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            if (!keys.next()) return -1;
            int id = keys.getInt(1);
            addMember(id, leaderUuid);
            return id;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error creating gang: " + e.getMessage());
            return -1;
        }
    }

    public boolean disbandGang(int gangId, UUID requesterUuid) {
        GangInfo info = getGangById(gangId);
        if (info == null || !info.leaderUuid.equals(requesterUuid)) return false;
        String sql = "DELETE FROM gangs WHERE id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setInt(1, gangId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) { return false; }
    }

    // ---- Members ----

    public boolean addMember(int gangId, UUID uuid) {
        String sql = "INSERT IGNORE INTO gang_members (gang_id, player_uuid) VALUES (?, ?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setInt(1, gangId);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) { return false; }
    }

    public boolean removeMember(int gangId, UUID uuid) {
        GangInfo info = getGangById(gangId);
        if (info != null && info.leaderUuid.equals(uuid)) return false;
        String sql = "DELETE FROM gang_members WHERE gang_id = ? AND player_uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setInt(1, gangId);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) { return false; }
    }

    public List<UUID> getMembers(int gangId) {
        List<UUID> members = new ArrayList<>();
        String sql = "SELECT player_uuid FROM gang_members WHERE gang_id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setInt(1, gangId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) members.add(UUID.fromString(rs.getString("player_uuid")));
        } catch (SQLException e) { }
        return members;
    }

    // ---- Invites ----

    public void sendInvite(int gangId, UUID targetUuid) {
        pendingInvites.put(targetUuid, gangId);
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> pendingInvites.remove(targetUuid), 20L * 60 * 5);
    }

    public Integer getPendingInvite(UUID uuid) {
        return pendingInvites.get(uuid);
    }

    public void removeInvite(UUID uuid) {
        pendingInvites.remove(uuid);
    }

    // ---- Lookups ----

    public GangInfo getGangById(int id) {
        String sql = "SELECT * FROM gangs WHERE id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return gangFromRs(rs);
        } catch (SQLException e) { }
        return null;
    }

    public GangInfo getGangByName(String name) {
        String sql = "SELECT * FROM gangs WHERE LOWER(name) = LOWER(?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return gangFromRs(rs);
        } catch (SQLException e) { }
        return null;
    }

    public GangInfo getGangByMember(UUID uuid) {
        String sql = "SELECT g.* FROM gangs g JOIN gang_members m " +
                "ON g.id = m.gang_id WHERE m.player_uuid = ? LIMIT 1";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return gangFromRs(rs);
        } catch (SQLException e) { }
        return null;
    }

    public List<GangInfo> getAllGangs() {
        List<GangInfo> gangs = new ArrayList<>();
        String sql = "SELECT * FROM gangs ORDER BY name";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) gangs.add(gangFromRs(rs));
        } catch (SQLException e) { }
        return gangs;
    }

    private GangInfo gangFromRs(ResultSet rs) throws SQLException {
        return new GangInfo(
                rs.getInt("id"),
                rs.getString("name"),
                UUID.fromString(rs.getString("leader_uuid")),
                rs.getTimestamp("created_at")
        );
    }

    // ---- Data class ----

    public static class GangInfo {
        public final int id;
        public final String name;
        public final UUID leaderUuid;
        public final Timestamp createdAt;

        public GangInfo(int id, String name, UUID leaderUuid, Timestamp createdAt) {
            this.id = id;
            this.name = name;
            this.leaderUuid = leaderUuid;
            this.createdAt = createdAt;
        }
    }
}