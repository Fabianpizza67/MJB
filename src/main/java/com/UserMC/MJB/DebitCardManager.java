package com.UserMC.MJB;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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

    // Checks the DATABASE — works even if the card was stolen
    public boolean isCardCancelled(ItemStack item) {
        UUID owner = getCardOwner(item);
        if (owner == null) return false;
        return isUUIDCancelled(owner);
    }

    public boolean isUUIDCancelled(UUID uuid) {
        String sql = "SELECT 1 FROM cancelled_cards WHERE player_uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking cancelled card: " + e.getMessage());
            return false;
        }
    }

    // Cancel by UUID — no physical card needed
    public boolean cancelCard(UUID uuid) {
        String sql = "INSERT IGNORE INTO cancelled_cards (player_uuid) VALUES (?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error cancelling card: " + e.getMessage());
            return false;
        }
    }

    // Re-activate when player buys a new card
    public boolean reinstateCard(UUID uuid) {
        String sql = "DELETE FROM cancelled_cards WHERE player_uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error reinstating card: " + e.getMessage());
            return false;
        }
    }

    public boolean playerHasCard(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (isDebitCard(item)) {
                UUID owner = getCardOwner(item);
                // Only count cards that belong to this player and aren't cancelled
                if (owner != null && owner.equals(player.getUniqueId()) && !isUUIDCancelled(owner)) return true;
            }
        }
        return false;
    }
}