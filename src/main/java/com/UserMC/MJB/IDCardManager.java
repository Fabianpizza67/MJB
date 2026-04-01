package com.UserMC.MJB;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class IDCardManager {

    private final MJB plugin;

    public final NamespacedKey IS_ID_CARD_KEY;
    public final NamespacedKey ID_CARD_OWNER_KEY;
    public final NamespacedKey ID_CARD_VERSION_KEY;

    public static final String ID_CARD_GUI_TITLE = "§b§lID Card";

    public IDCardManager(MJB plugin) {
        this.plugin = plugin;
        IS_ID_CARD_KEY      = new NamespacedKey(plugin, "is_id_card");
        ID_CARD_OWNER_KEY   = new NamespacedKey(plugin, "id_card_owner");
        ID_CARD_VERSION_KEY = new NamespacedKey(plugin, "id_card_version");
    }

    // ---- Item creation ----

    public ItemStack createIDCard(UUID ownerUuid, String ownerName, int version) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b§lID Card §7— §f" + ownerName);
        meta.setLore(List.of(
                "§7Issued by: §fUserMC City Hall",
                "§7Right-click to view details.",
                "§b§lOfficial Government Document"
        ));
        meta.getPersistentDataContainer().set(IS_ID_CARD_KEY,      PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(ID_CARD_OWNER_KEY,   PersistentDataType.STRING,  ownerUuid.toString());
        meta.getPersistentDataContainer().set(ID_CARD_VERSION_KEY, PersistentDataType.INTEGER, version);
        item.setItemMeta(meta);
        return item;
    }

    // ---- NBT checks ----

    public boolean isIDCard(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(IS_ID_CARD_KEY, PersistentDataType.BOOLEAN);
    }

    public UUID getCardOwner(ItemStack item) {
        if (!isIDCard(item)) return null;
        String uuidStr = item.getItemMeta().getPersistentDataContainer()
                .get(ID_CARD_OWNER_KEY, PersistentDataType.STRING);
        if (uuidStr == null) return null;
        try { return UUID.fromString(uuidStr); }
        catch (IllegalArgumentException e) { return null; }
    }

    public int getCardVersion(ItemStack item) {
        if (!isIDCard(item)) return -1;
        Integer ver = item.getItemMeta().getPersistentDataContainer()
                .get(ID_CARD_VERSION_KEY, PersistentDataType.INTEGER);
        return ver != null ? ver : 1;
    }

    // ---- Version management ----
    // Version only increments when the player reports their card lost at the Gov NPC.
    // That's the only time the old physical card becomes invalid.

    public int getCurrentCardVersion(UUID uuid) {
        String sql = "SELECT id_card_version FROM players WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("id_card_version");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting ID card version: " + e.getMessage());
        }
        return 1;
    }

    public int incrementCardVersion(UUID uuid) {
        String sql = "UPDATE players SET id_card_version = id_card_version + 1 WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
            return getCurrentCardVersion(uuid);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error incrementing ID card version: " + e.getMessage());
            return -1;
        }
    }

    public boolean isCardValid(ItemStack item) {
        if (!isIDCard(item)) return false;
        UUID owner = getCardOwner(item);
        if (owner == null) return false;
        return getCardVersion(item) == getCurrentCardVersion(owner);
    }

    public boolean playerHasValidIDCard(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (!isIDCard(item)) continue;
            UUID owner = getCardOwner(item);
            if (owner != null && owner.equals(player.getUniqueId()) && isCardValid(item))
                return true;
        }
        return false;
    }

    // ---- GUI — opens live data from DB ----

    public void openIDCardGUI(Player viewer, UUID ownerUuid) {
        String ownerName = plugin.getServer().getOfflinePlayer(ownerUuid).getName();
        if (ownerName == null) ownerName = "Unknown";

        MedicalRecordManager.BloodType blood =
                plugin.getMedicalRecordManager().getBloodType(ownerUuid);
        String bloodStr = blood != null ? blood.name() : "Unknown";

        String phone = plugin.getPhoneManager().getPhoneNumber(ownerUuid);
        String phoneStr = phone != null ? phone : "None";

        List<LicenseManager.PlayerLicense> activeLicenses = plugin.getLicenseManager()
                .getPlayerLicenses(ownerUuid).stream()
                .filter(l -> !l.isRevoked &&
                        plugin.getLicenseManager().hasActiveLicense(ownerUuid, l.licenseType))
                .toList();

        Inventory gui = plugin.getServer().createInventory(null, 27, ID_CARD_GUI_TITLE);

        // Header — main identity info
        gui.setItem(4, guiItem(Material.BOOK, "§b§l" + ownerName,
                "§7Full Name: §f" + ownerName,
                "§7Blood Type: §f" + bloodStr,
                "§7Phone: §b" + phoneStr,
                "§8§m-------------------",
                "§b§lUserMC City Hall"));

        // Licenses — live from DB
        if (activeLicenses.isEmpty()) {
            gui.setItem(13, guiItem(Material.BARRIER, "§cNo Active Licenses",
                    "§7This person holds no active licenses."));
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
            int slot = 9;
            for (LicenseManager.PlayerLicense lic : activeLicenses) {
                if (slot >= 18) break;
                LicenseManager.LicenseType type =
                        plugin.getLicenseManager().getLicenseType(lic.licenseType);
                String displayName = type != null ? type.displayName : lic.licenseType;
                gui.setItem(slot++, guiItem(Material.EMERALD,
                        "§a" + displayName,
                        "§7Expires: §f" + sdf.format(lic.expiresAt)));
            }
        }

        gui.setItem(22, guiItem(Material.BARRIER, "§4Close", "§7Close."));
        viewer.openInventory(gui);
    }

    private ItemStack guiItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }
}