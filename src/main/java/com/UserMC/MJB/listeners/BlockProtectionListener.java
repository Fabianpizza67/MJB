package com.UserMC.MJB.listeners;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class BlockProtectionListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (isAllowed(player)) return;

        Location blockLoc = event.getBlock().getLocation();
        ApplicableRegionSet regions = getRegions(player, blockLoc);

        if (regions == null || regions.size() == 0) {
            event.setCancelled(true);
            player.sendMessage("§4You can't modify this area.");
            return;
        }

        if (!WorldGuardPlugin.inst().createProtectionQuery().testBlockBreak(player, event.getBlock())) {
            event.setCancelled(true);
            player.sendMessage("§4This isn't your property.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (isAllowed(player)) return;

        Location blockLoc = event.getBlock().getLocation();
        ApplicableRegionSet regions = getRegions(player, blockLoc);

        if (regions == null || regions.size() == 0) {
            event.setCancelled(true);
            player.sendMessage("§4You can't modify this area.");
            return;
        }

        if (!WorldGuardPlugin.inst().createProtectionQuery().testBlockPlace(player, event.getBlock().getLocation(), event.getBlock().getType())) {
            event.setCancelled(true);
            player.sendMessage("§4This isn't your property.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(org.bukkit.event.player.PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (isAllowed(player)) return;
        // Block water/lava in starter store regions entirely — even for the owner
        if (event.getBucket() == org.bukkit.Material.WATER_BUCKET ||
                event.getBucket() == org.bukkit.Material.LAVA_BUCKET) {
            if (isStarterStoreRegion(event.getBlock().getLocation())) {
                event.setCancelled(true);
                return;
            }
        }

        Location blockLoc = event.getBlock().getLocation();
        ApplicableRegionSet regions = getRegions(player, blockLoc);

        if (regions == null || regions.size() == 0) {
            event.setCancelled(true);
            player.sendMessage("§4You can't modify this area.");
            return;
        }

        if (!WorldGuardPlugin.inst().createProtectionQuery()
                .testBlockPlace(player, blockLoc,
                        org.bukkit.Material.WATER)) {
            event.setCancelled(true);
            player.sendMessage("§4This isn't your property.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(org.bukkit.event.player.PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        if (isAllowed(player)) return;

        Location blockLoc = event.getBlock().getLocation();
        ApplicableRegionSet regions = getRegions(player, blockLoc);

        if (regions == null || regions.size() == 0) return;

        if (!WorldGuardPlugin.inst().createProtectionQuery()
                .testBlockBreak(player, event.getBlock())) {
            event.setCancelled(true);
            player.sendMessage("§4This isn't your property.");
        }
    }

    private boolean isStarterStoreRegion(Location loc) {
        com.sk89q.worldguard.protection.managers.RegionManager rm =
                WorldGuard.getInstance().getPlatform().getRegionContainer()
                        .get(BukkitAdapter.adapt(loc.getWorld()));
        if (rm == null) return false;
        var regions = rm.getApplicableRegions(
                BukkitAdapter.adapt(loc).toVector().toBlockPoint());
        for (var region : regions) {
            String id = region.getId();
            if (id.equals("__global__")) continue;
            // Check if this region is a registered starter store
            String sql = "SELECT 1 FROM starter_stores WHERE region_id = ?";
            try (java.sql.PreparedStatement stmt = com.UserMC.MJB.MJB.getInstance()
                    .getDatabaseManager().getConnection().prepareStatement(sql)) {
                stmt.setString(1, id);
                if (stmt.executeQuery().next()) return true;
            } catch (java.sql.SQLException e) {
                com.UserMC.MJB.MJB.getInstance().getLogger()
                        .warning("Error checking starter store region: " + e.getMessage());
            }
        }
        return false;
    }

    private boolean isAllowed(Player player) {
        return player.isOp() && player.getGameMode() == GameMode.CREATIVE;
    }

    private ApplicableRegionSet getRegions(Player player, Location location) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager manager = container.get(BukkitAdapter.adapt(location.getWorld()));
        if (manager == null) return null;
        return manager.getApplicableRegions(BukkitAdapter.adapt(location).toVector().toBlockPoint());
    }
}