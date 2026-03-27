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
            String phoneNumber = plugin.getPhoneManager()
                    .assignPhoneNumber(player.getUniqueId());
            if (phoneNumber != null) {
                player.getInventory().addItem(
                        plugin.getPhoneManager().createPhone(phoneNumber));
            }
            plugin.getTutorialManager().initPlayer(player.getUniqueId());
            plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                    plugin.getTutorialManager().sendWelcome(player), 40L);
        } else {
            plugin.getPlotManager().reclaimStarterIfNeeded(player);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                int thirst = plugin.getThirstManager().getThirst(player.getUniqueId());
                if (thirst == 0) {
                    plugin.getThirstManager().applyThirstEffects(player);
                }
                if (plugin.getPoliceManager().isOfficer(player.getUniqueId())) {
                    plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                            plugin.getPoliceBudgetManager().deliverPendingItems(player), 20L);
                }
                plugin.getServer().getScheduler().runTaskLater(plugin,
                        () -> plugin.getNameTagManager().refresh(player), 5L);
            }, 20L);
        }
    }
}