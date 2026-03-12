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
    public final NamespacedKey CARD_VERSION_KEY;
    public static final double CARD_PRICE = 25.0;

    public DebitCardManager(MJB plugin) {
        this.plugin = plugin;
        this.CARD_KEY = new NamespacedKey(plugin, "debit_card");
        this.CARD_OWNER_KEY = new NamespacedKey(plugin, "debit_card_owner");
        this.CARD_VERSION_KEY = new NamespacedKey(plugin, "debit_card_version");
    }

    /**
     * Creates a debit card item with the given version number baked in.
     * Version is used to invalidate old physical cards when a new one is issued.
     */
    public ItemStack createDebitCard(Player player, int version) {
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
        meta.getPersistentDataContainer().set(CARD_VERSION_KEY, PersistentDataType.INTEGER, version);
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

    public int getCardVersion(ItemStack item) {
        if (!isDebitCard(item)) return -1;
        Integer version = item.getItemMeta().getPersistentDataContainer()
                .get(CARD_VERSION_KEY, PersistentDataType.INTEGER);
        return version != null ? version : 0;
    }

    // ---- Version management ----

    public int incrementCardVersion(UUID uuid) {
        String sql = "UPDATE players SET card_version = card_version + 1 WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
            return getCurrentCardVersion(uuid);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error incrementing card version: " + e.getMessage());
            return -1;
        }
    }

    public int getCurrentCardVersion(UUID uuid) {
        String sql = "SELECT card_version FROM players WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("card_version");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting card version: " + e.getMessage());
        }
        return 0;
    }

    public boolean isCardValid(ItemStack item) {
        if (!isDebitCard(item)) return false;
        UUID owner = getCardOwner(item);
        if (owner == null) return false;

        // Check version
        int itemVersion = getCardVersion(item);
        int currentVersion = getCurrentCardVersion(owner);
        if (itemVersion != currentVersion) return false;

        // Check cancellation
        return !isUUIDCancelled(owner);
    }

    // ---- Cancellation ----

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

    public boolean clearCancellation(UUID uuid) {
        String sql = "DELETE FROM cancelled_cards WHERE player_uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error clearing cancellation: " + e.getMessage());
            return false;
        }
    }

    public boolean playerHasValidCard(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (!isDebitCard(item)) continue;
            UUID owner = getCardOwner(item);
            if (owner == null || !owner.equals(player.getUniqueId())) continue;
            if (isCardValid(item)) return true;
        }
        return false;
    }
}