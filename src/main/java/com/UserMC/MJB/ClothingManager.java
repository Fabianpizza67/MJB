package com.UserMC.MJB;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

public class ClothingManager {

    private final MJB plugin;

    public ClothingManager(MJB plugin) {
        this.plugin = plugin;
    }

    public boolean isProperlyDressed(Player player) {
        ItemStack chest = player.getInventory().getChestplate();
        ItemStack legs  = player.getInventory().getLeggings();
        return isWorn(chest) && isWorn(legs);
    }

    private boolean isWorn(ItemStack item) {
        return item != null && item.getType() != org.bukkit.Material.AIR;
    }

    public void drainDurability(Player player) {
        var inv = player.getInventory();
        ItemStack[] slots = {
                inv.getHelmet(), inv.getChestplate(),
                inv.getLeggings(), inv.getBoots()
        };

        for (int i = 0; i < slots.length; i++) {
            ItemStack item = slots[i];
            if (item == null || item.getType() == org.bukkit.Material.AIR) continue;
            if (!(item.getItemMeta() instanceof Damageable meta)) continue;

            int maxDurability = item.getType().getMaxDurability();
            if (maxDurability <= 0) continue; // unbreakable material — skip

            int newDamage = meta.getDamage() + 1;
            int remaining = maxDurability - newDamage;

            // Warn at 10% remaining, every 5 ticks to avoid spam
            if (remaining > 0 && remaining <= (maxDurability * 0.10) && remaining % 5 == 0) {
                String slotName = slotName(i);
                player.sendMessage("§e§l[Clothing] §eYour §f" + slotName +
                        " §eis almost worn out! §f" + remaining + " §eminutes left.");
            }

            if (newDamage >= maxDurability) {
                // Remove from slot and notify
                setArmorSlot(inv, i, null);
                player.sendMessage("§4§l[Clothing] §4Your §f" + slotName(i) + " §4has worn out and broken!");
            } else {
                meta.setDamage(newDamage);
                item.setItemMeta(meta);
                setArmorSlot(inv, i, item);
            }
        }
    }

    private String slotName(int slot) {
        return switch (slot) {
            case 0 -> "helmet";
            case 1 -> "chestplate";
            case 2 -> "leggings";
            case 3 -> "boots";
            default -> "armor";
        };
    }

    private void setArmorSlot(org.bukkit.inventory.PlayerInventory inv, int slot, ItemStack item) {
        switch (slot) {
            case 0 -> inv.setHelmet(item);
            case 1 -> inv.setChestplate(item);
            case 2 -> inv.setLeggings(item);
            case 3 -> inv.setBoots(item);
        }
    }

    public void startDrainScheduler() {
        // 20 ticks/sec * 60 sec = 1200 ticks per minute
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                drainDurability(player);
            }
        }, 1200L, 1200L);
    }
}