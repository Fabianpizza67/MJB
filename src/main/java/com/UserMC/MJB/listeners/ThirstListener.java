package com.UserMC.MJB.listeners;

import com.UserMC.MJB.MJB;
import com.UserMC.MJB.ThirstManager.DrinkEntry;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

public class ThirstListener implements Listener {

    private final MJB plugin;

    public ThirstListener(MJB plugin) {
        this.plugin = plugin;
    }

    // 1. CLEAR BOTTLE AFTER DRINKING
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        DrinkEntry entry = plugin.getThirstManager().getDrinkEntry(item);
        
        if (entry != null) {
            // Forces the "result" of drinking to be nothing (instead of an empty bottle)
            event.setReplacement(null); 
            
            // Note: In some Spigot versions, null can be finicky. 
            // If it still gives a bottle, use: event.setReplacement(new ItemStack(Material.AIR));

            plugin.getThirstManager().onDrink(event.getPlayer(), item);
        }
    }

    // 2. CLEAR BOTTLE AFTER FILLING FROM WATER SOURCE
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWaterFill(PlayerInteractEvent event) {
        // Only trigger if they are right-clicking a block with an empty bottle
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.GLASS_BOTTLE) return;

        // Check if the block is water (or a cauldron)
        if (event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.WATER) {
            Player player = event.getPlayer();

            // Cancel the event so the "Water Bottle" is never actually created
            event.setCancelled(true);

            // Manually reduce the held item stack by 1 (simulating using the bottle)
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().setItem(event.getHand(), null);
            }

            // Optional: Trigger thirst logic here if filling is meant to count as drinking
            // plugin.getThirstManager().onDrink(player, ...);
        }
    }
}
