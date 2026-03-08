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