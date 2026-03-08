package com.UserMC.MJB;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.List;

public class CashItemManager {

    private final MJB plugin;
    public final NamespacedKey CASH_KEY;

    public CashItemManager(MJB plugin) {
        this.plugin = plugin;
        this.CASH_KEY = new NamespacedKey(plugin, "cash_value");
    }

    // Creates a cash item representing a given amount
    public ItemStack createCash(double amount) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§a§l$" + String.format("%.2f", amount));
        meta.setLore(List.of(
                "§7Physical cash.",
                "§7Deposit at a bank to keep it safe.",
                "§c§lWarning: §7Dropped on death!"
        ));

        // Store the value as NBT
        meta.getPersistentDataContainer().set(CASH_KEY, PersistentDataType.DOUBLE, amount);
        item.setItemMeta(meta);
        return item;
    }

    // Gets the cash value from an item, returns -1 if not a cash item
    public double getCashValue(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return -1;
        ItemMeta meta = item.getItemMeta();
        if (!meta.getPersistentDataContainer().has(CASH_KEY, PersistentDataType.DOUBLE)) return -1;
        return meta.getPersistentDataContainer().get(CASH_KEY, PersistentDataType.DOUBLE);
    }

    // Checks if an item is a cash item
    public boolean isCash(ItemStack item) {
        return getCashValue(item) >= 0;
    }

    // Counts total cash value in a player's inventory
    public double getTotalCashInInventory(org.bukkit.entity.Player player) {
        double total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            double value = getCashValue(item);
            if (value >= 0) total += value * item.getAmount();
        }
        return total;
    }

    public double removeAllCash(org.bukkit.entity.Player player) {
        double total = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            double value = getCashValue(contents[i]);
            if (value >= 0) {
                total += value * contents[i].getAmount();
                player.getInventory().setItem(i, null);
            }
        }
        return total;
    }

    // Gives cash item(s) to a player, splitting into bills if needed
    public void giveCash(org.bukkit.entity.Player player, double amount) {
        // Split into sensible denominations
        double[] denominations = {1000, 500, 100, 50, 20, 10, 5, 1};
        double remaining = amount;

        for (double denom : denominations) {
            while (remaining >= denom) {
                player.getInventory().addItem(createCash(denom));
                remaining -= denom;
                remaining = Math.round(remaining * 100.0) / 100.0;
            }
        }

        // Handle leftover cents
        if (remaining > 0.001) {
            player.getInventory().addItem(createCash(Math.round(remaining * 100.0) / 100.0));
        }
    }
}