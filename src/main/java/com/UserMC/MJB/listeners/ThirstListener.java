package com.UserMC.MJB.listeners;

import com.UserMC.MJB.MJB;
import com.UserMC.MJB.ThirstManager.DrinkEntry;
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

        // Cancel the vanilla glass bottle return by setting replacement to null.
        // This is what prevents refilling — the bottle simply disappears.
        event.setReplacement(null);

        // Process thirst restore + effects after vanilla consumption finishes
        plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.getThirstManager().onDrink(player, item));
    }
}