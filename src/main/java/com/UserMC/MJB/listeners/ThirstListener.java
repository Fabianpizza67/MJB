package com.UserMC.MJB.listeners;

import com.UserMC.MJB.MJB;
import com.UserMC.MJB.ThirstManager.DrinkEntry;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

public class ThirstListener implements Listener {

    private final MJB plugin;

    public ThirstListener(MJB plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        DrinkEntry entry = plugin.getThirstManager().getDrinkEntry(item);
        if (entry == null) return;

        // Suppress vanilla glass bottle return
        event.setReplacement(null);

        // Schedule on next tick — item consumption finishes first, then we sweep bottles
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // Remove all glass bottles from inventory
            // Safe to do this universally — players can't obtain glass bottles legitimately on this server
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack slot = player.getInventory().getItem(i);
                if (slot != null && slot.getType() == Material.GLASS_BOTTLE) {
                    player.getInventory().setItem(i, null);
                }
            }

            plugin.getThirstManager().onDrink(player, item);
        });
    }
}