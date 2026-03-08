package com.UserMC.MJB;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

public class DebitCardManager {

    private final MJB plugin;
    public final NamespacedKey CARD_KEY;
    public final NamespacedKey CARD_OWNER_KEY;
    public static final double CARD_PRICE = 25.0;

    public DebitCardManager(MJB plugin) {
        this.plugin = plugin;
        this.CARD_KEY = new NamespacedKey(plugin, "debit_card");
        this.CARD_OWNER_KEY = new NamespacedKey(plugin, "debit_card_owner");
    }

    public ItemStack createDebitCard(Player player) {
        ItemStack card = new ItemStack(Material.PAPER);
        ItemMeta meta = card.getItemMeta();

        meta.setDisplayName("§b§lDebit Card");
        meta.setLore(List.of(
                "§7Issued to: §f" + player.getName(),
                "§7Use at any card terminal to pay.",
                "§7Keep it safe — it can be stolen!"
        ));

        meta.getPersistentDataContainer().set(CARD_KEY, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(CARD_OWNER_KEY, PersistentDataType.STRING, player.getUniqueId().toString());
        card.setItemMeta(meta);
        return card;
    }

    public boolean isDebitCard(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(CARD_KEY, PersistentDataType.BOOLEAN);
    }

    public UUID getCardOwner(ItemStack item) {
        if (!isDebitCard(item)) return null;
        String uuidStr = item.getItemMeta().getPersistentDataContainer()
                .get(CARD_OWNER_KEY, PersistentDataType.STRING);
        if (uuidStr == null) return null;
        return UUID.fromString(uuidStr);
    }

    public boolean playerHasCard(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isDebitCard(item)) return true;
        }
        return false;
    }
}