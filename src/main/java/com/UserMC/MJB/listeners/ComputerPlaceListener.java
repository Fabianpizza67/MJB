package com.UserMC.MJB.listeners;

import com.UserMC.MJB.MJB;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class ComputerPlaceListener implements Listener {

    private final MJB plugin;

    public ComputerPlaceListener(MJB plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() != Material.GREEN_GLAZED_TERRACOTTA) return;
        Player player = event.getPlayer();
        plugin.getSupplyOrderManager().registerComputer(
                event.getBlock().getLocation(),
                player.getUniqueId()
        );
        player.sendMessage("§f§lComputer placed! §7Right-click to use it.");
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.GREEN_GLAZED_TERRACOTTA) return;
        if (!plugin.getSupplyOrderManager().isComputer(event.getBlock().getLocation())) return;
        plugin.getSupplyOrderManager().unregisterComputer(event.getBlock().getLocation());
    }
}