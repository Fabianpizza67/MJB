package com.UserMC.MJB.listeners;

import com.UserMC.MJB.MJB;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final MJB plugin;

    public PlayerJoinListener(MJB plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean isNew = plugin.getEconomyManager().isNewPlayer(player.getUniqueId());
        plugin.getEconomyManager().initPlayer(player.getUniqueId(), player.getName());
        plugin.getLicenseManager().checkAndWarnOnLogin(player);

        if (isNew) {
            plugin.getCashItemManager().giveCash(player, 100.0);
            player.sendMessage("§b§lWelcome to the city!");
            player.sendMessage("§fYou've been given §b$100.00 §fcash to get started.");
            player.sendMessage("§7Visit the housing office NPC to claim your starter apartment.");
            player.sendMessage("§7Then visit the bank to deposit your cash safely!");
        } else {
            // Check if starter apartment should be reclaimed
            plugin.getPlotManager().reclaimStarterIfNeeded(player);
        }
    }
}