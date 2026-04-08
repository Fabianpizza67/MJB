package com.UserMC.MJB.listeners;

import com.UserMC.MJB.MJB;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class PlayerDeathListener implements Listener {

    private final MJB plugin;

    public PlayerDeathListener(MJB plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        event.setDeathMessage(null);
        Player player = event.getEntity();

        // Find all cash items in the drops
        List<ItemStack> toRemove = new ArrayList<>();
        for (ItemStack drop : event.getDrops()) {
            if (plugin.getCashItemManager().isCash(drop)) {
                toRemove.add(drop);
            }
        }

        // Remove cash from normal drops — we'll spawn it manually so it can't despawn quickly
        event.getDrops().removeAll(toRemove);

        // Drop the cash at the death location
        for (ItemStack cash : toRemove) {
            player.getWorld().dropItemNaturally(player.getLocation(), cash);
        }
    }
}