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
            plugin.getMedicalRecordManager().assignBloodType(player.getUniqueId());
            int idVersion = plugin.getIDCardManager().getCurrentCardVersion(player.getUniqueId());
            player.getInventory().addItem(
                    plugin.getIDCardManager().createIDCard(
                            player.getUniqueId(), player.getName(), idVersion));
            int addictionStage = plugin.getMedicalRecordManager()
                    .getAddictionStage(player.getUniqueId());
            if (addictionStage > 0) {
                plugin.getMedicalRecordManager().applyAddictionEffects(player, addictionStage);
            }
            plugin.getTutorialManager().initPlayer(player.getUniqueId());
            plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                    plugin.getTutorialManager().sendWelcome(player), 40L);
        } else {
            plugin.getPlotManager().reclaimStarterIfNeeded(player);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!plugin.getIDCardManager().playerHasValidIDCard(player)) {
                    int version = plugin.getIDCardManager().getCurrentCardVersion(player.getUniqueId());
                    player.getInventory().addItem(
                            plugin.getIDCardManager().createIDCard(
                                    player.getUniqueId(), player.getName(), version));
                    player.sendMessage("§b§l[City Hall] §fYou have been issued an ID card. Keep it with you!");
                }
            }, 20L);
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
                // Add inside the else block (returning players), after existing code:
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.getHealth() <= 1.5) {
                        player.setHealth(20.0);
                        player.setWalkSpeed(0.2f);
                        player.sendMessage("§b§l[Hospital] §fYou have recovered from your injuries.");
                    }
                }, 10L);
                if (plugin.getHospitalManager().isDoctor(player.getUniqueId())) {
                    plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                            plugin.getHospitalManager().deliverPendingSupplies(player), 20L);
                }
            }, 20L);
        }
    }
}