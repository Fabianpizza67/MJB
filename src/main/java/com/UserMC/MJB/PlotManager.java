package com.UserMC.MJB;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlotManager {

    private final MJB plugin;

    public PlotManager(MJB plugin) {
        this.plugin = plugin;
    }

    private RegionManager getRegionManager(World world) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        return container.get(BukkitAdapter.adapt(world));
    }

    // ---- Starter Apartment Pool ----

    public boolean registerStarterApartment(String regionId, World world) {
        RegionManager manager = getRegionManager(world);
        if (manager == null || manager.getRegion(regionId) == null) return false;

        String sql = "INSERT IGNORE INTO starter_apartments (region_id, world) VALUES (?, ?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, regionId);
            stmt.setString(2, world.getName());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error registering starter apartment: " + e.getMessage());
            return false;
        }
    }

    public boolean unregisterStarterApartment(String regionId) {
        String sql = "DELETE FROM starter_apartments WHERE region_id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, regionId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error unregistering starter apartment: " + e.getMessage());
            return false;
        }
    }

    public List<String> getAvailableStarterApartments() {
        List<String> available = new ArrayList<>();
        String sql = "SELECT region_id FROM starter_apartments WHERE is_claimed = FALSE";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) available.add(rs.getString("region_id"));
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching starter apartments: " + e.getMessage());
        }
        return available;
    }

    public boolean hasClaimedStarter(UUID uuid) {
        String sql = "SELECT has_claimed_starter FROM players WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getBoolean("has_claimed_starter");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking starter claim: " + e.getMessage());
        }
        return false;
    }

    public String claimStarterApartment(Player player) {
        if (hasClaimedStarter(player.getUniqueId())) return null;

        List<String> available = getAvailableStarterApartments();
        if (available.isEmpty()) return null;

        String regionId = available.get((int) (Math.random() * available.size()));
        String worldName = getWorldForStarterApartment(regionId);
        if (worldName == null) return null;
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) return null;

        RegionManager manager = getRegionManager(world);
        if (manager == null) return null;
        ProtectedRegion region = manager.getRegion(regionId);
        if (region == null) return null;
        region.getOwners().addPlayer(player.getUniqueId());

        String sql1 = "UPDATE starter_apartments SET is_claimed = TRUE, claimed_by = ? WHERE region_id = ?";
        String sql2 = "UPDATE players SET has_claimed_starter = TRUE WHERE uuid = ?";
        try (PreparedStatement stmt1 = plugin.getDatabaseManager().getConnection().prepareStatement(sql1);
             PreparedStatement stmt2 = plugin.getDatabaseManager().getConnection().prepareStatement(sql2)) {
            stmt1.setString(1, player.getUniqueId().toString());
            stmt1.setString(2, regionId);
            stmt1.executeUpdate();
            stmt2.setString(1, player.getUniqueId().toString());
            stmt2.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error claiming starter apartment: " + e.getMessage());
            return null;
        }

        assignPlotInDB(regionId, world, player.getUniqueId(), "starter_apartment");
        return regionId;
    }

    // Admin unclaim — removes player from starter apartment and resets it back to available
    // NOTE: has_claimed_starter stays TRUE forever — player can never claim another starter
    public boolean unclaimStarterApartment(String regionId) {
        String worldName = getWorldForStarterApartment(regionId);
        if (worldName == null) return false;
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) return false;

        RegionManager manager = getRegionManager(world);
        if (manager == null) return false;
        ProtectedRegion region = manager.getRegion(regionId);
        if (region == null) return false;

        // Remove all owners and members from WorldGuard
        region.getOwners().getUniqueIds().forEach(region.getOwners()::removePlayer);
        region.getMembers().getUniqueIds().forEach(region.getMembers()::removePlayer);

        // Reset apartment back to available, remove from plots
        String sql1 = "UPDATE starter_apartments SET is_claimed = FALSE, claimed_by = NULL WHERE region_id = ?";
        String sql2 = "DELETE FROM plots WHERE region_id = ?";
        try (PreparedStatement stmt1 = plugin.getDatabaseManager().getConnection().prepareStatement(sql1);
             PreparedStatement stmt2 = plugin.getDatabaseManager().getConnection().prepareStatement(sql2)) {
            stmt1.setString(1, regionId);
            stmt1.executeUpdate();
            stmt2.setString(1, regionId);
            stmt2.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error unclaiming starter apartment: " + e.getMessage());
            return false;
        }
    }

    // Checks if player should lose their starter — owns another plot for 24+ hours
    public boolean shouldLoseStarterApartment(UUID uuid) {
        String sql = "SELECT purchased_at FROM plots WHERE owner_uuid = ? AND plot_type != 'starter_apartment'";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Timestamp purchasedAt = rs.getTimestamp("purchased_at");
                long ageMs = System.currentTimeMillis() - purchasedAt.getTime();
                long twentyFourHours = 24 * 60 * 60 * 1000L;
                if (ageMs >= twentyFourHours) return true;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking starter loss condition: " + e.getMessage());
        }
        return false;
    }

    // Called on login — auto reclaims starter if player has owned another property for 24h+
    public void reclaimStarterIfNeeded(Player player) {
        if (!hasClaimedStarter(player.getUniqueId())) return;
        if (!shouldLoseStarterApartment(player.getUniqueId())) return;

        String plotSql = "SELECT region_id, plot_type, purchased_at FROM plots WHERE owner_uuid = ? " +
                "AND plot_type != 'starter_apartment' " +
                "AND purchased_at IS NOT NULL " +
                "AND purchased_at <= NOW() - INTERVAL 24 HOUR";
        try (java.sql.PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(plotSql)) {
            stmt.setString(1, player.getUniqueId().toString());
            java.sql.ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                plugin.getLogger().info("[DEBUG] No qualifying plot found for " + player.getName() + " — skipping reclaim.");
                return;
            }
            plugin.getLogger().info("[DEBUG] Qualifying plot found: " + rs.getString("region_id") +
                    " type=" + rs.getString("plot_type") + " purchased=" + rs.getTimestamp("purchased_at"));
        } catch (java.sql.SQLException e) {
            plugin.getLogger().severe("Error checking plots for reclaim: " + e.getMessage());
            return;
        }
    }

    private String getWorldForStarterApartment(String regionId) {
        String sql = "SELECT world FROM starter_apartments WHERE region_id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, regionId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("world");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting world for starter apartment: " + e.getMessage());
        }
        return null;
    }

    // ---- General Plot Management ----

    public boolean assignPlot(Player player, World world, String regionId, String plotType) {
        RegionManager manager = getRegionManager(world);
        if (manager == null) return false;
        ProtectedRegion region = manager.getRegion(regionId);
        if (region == null) return false;
        if (!region.getOwners().getUniqueIds().isEmpty()) return false;
        region.getOwners().addPlayer(player.getUniqueId());
        assignPlotInDB(regionId, world, player.getUniqueId(), plotType);
        return true;
    }

    private void assignPlotInDB(String regionId, World world, UUID ownerUuid, String plotType) {
        String sql = "INSERT IGNORE INTO plots (region_id, world, owner_uuid, plot_type) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, regionId);
            stmt.setString(2, world.getName());
            stmt.setString(3, ownerUuid.toString());
            stmt.setString(4, plotType);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error assigning plot in DB: " + e.getMessage());
        }
    }

    public boolean removePlot(Player player, World world, String regionId) {
        RegionManager manager = getRegionManager(world);
        if (manager == null) return false;
        ProtectedRegion region = manager.getRegion(regionId);
        if (region == null) return false;
        region.getOwners().removePlayer(player.getUniqueId());
        region.getMembers().getUniqueIds().forEach(region.getMembers()::removePlayer);
        String sql = "DELETE FROM plots WHERE region_id = ? AND world = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, regionId);
            stmt.setString(2, world.getName());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error removing plot from DB: " + e.getMessage());
        }
        return true;
    }

    public boolean addMember(UUID memberUuid, World world, String regionId) {
        RegionManager manager = getRegionManager(world);
        if (manager == null) return false;
        ProtectedRegion region = manager.getRegion(regionId);
        if (region == null) return false;
        region.getMembers().addPlayer(memberUuid);
        return true;
    }

    public boolean removeMember(UUID memberUuid, World world, String regionId) {
        RegionManager manager = getRegionManager(world);
        if (manager == null) return false;
        ProtectedRegion region = manager.getRegion(regionId);
        if (region == null) return false;
        region.getMembers().removePlayer(memberUuid);
        return true;
    }

    // Gets the region ID the player is currently standing in
    public String getRegionAtPlayer(Player player) {
        RegionManager manager = getRegionManager(player.getWorld());
        if (manager == null) return null;
        var regions = manager.getApplicableRegions(
                BukkitAdapter.adapt(player.getLocation()).toVector().toBlockPoint()
        );
        for (ProtectedRegion region : regions) {
            if (region.getId().equals("__global__")) continue;
            return region.getId();
        }
        return null;
    }

    public PlotInfo getPlotInfo(String regionId, World world) {
        String sql = "SELECT owner_uuid, plot_type, purchased_at FROM plots WHERE region_id = ? AND world = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, regionId);
            stmt.setString(2, world.getName());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                UUID ownerUuid = UUID.fromString(rs.getString("owner_uuid"));
                String plotType = rs.getString("plot_type");
                Timestamp purchasedAt = rs.getTimestamp("purchased_at");

                RegionManager manager = getRegionManager(world);
                List<UUID> members = new ArrayList<>();
                if (manager != null) {
                    ProtectedRegion region = manager.getRegion(regionId);
                    if (region != null) members.addAll(region.getMembers().getUniqueIds());
                }

                return new PlotInfo(regionId, ownerUuid, plotType, purchasedAt, members);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting plot info: " + e.getMessage());
        }
        return null;
    }

    // Simple data class for plot info
    public static class PlotInfo {
        public final String regionId;
        public final UUID ownerUuid;
        public final String plotType;
        public final Timestamp purchasedAt;
        public final List<UUID> members;

        public PlotInfo(String regionId, UUID ownerUuid, String plotType, Timestamp purchasedAt, List<UUID> members) {
            this.regionId = regionId;
            this.ownerUuid = ownerUuid;
            this.plotType = plotType;
            this.purchasedAt = purchasedAt;
            this.members = members;
        }
    }

    public String getRegionAtLocation(org.bukkit.Location loc) {
        com.sk89q.worldguard.protection.managers.RegionManager manager =
                com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer()
                        .get(BukkitAdapter.adapt(loc.getWorld()));
        if (manager == null) return null;

        com.sk89q.worldguard.protection.ApplicableRegionSet regions =
                manager.getApplicableRegions(com.sk89q.worldedit.math.BlockVector3.at(
                        loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));

        for (com.sk89q.worldguard.protection.regions.ProtectedRegion region : regions) {
            if (!region.getId().equals("__global__")) return region.getId();
        }
        return null;
    }

    // Check if a player owns a given region in the plots table
    public boolean isPlotOwner(java.util.UUID playerUuid, String regionId) {
        String sql = "SELECT 1 FROM plots WHERE region_id = ? AND owner_uuid = ?";
        try (java.sql.PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, regionId);
            stmt.setString(2, playerUuid.toString());
            java.sql.ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (java.sql.SQLException e) {
            plugin.getLogger().severe("Error checking plot owner: " + e.getMessage());
            return false;
        }
    }
}