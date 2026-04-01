package com.UserMC.MJB.listeners;

import com.UserMC.MJB.MJB;
import com.UserMC.MJB.WeaponManager;
import com.UserMC.MJB.WeaponManager.WeaponType;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BlackMarketListener implements Listener {

    private final MJB plugin;
    public static final String BLACK_MARKET_TAG = "black_market";
    private static final String SHOP_GUI_TITLE = "§4§lBlack Market";
    private static final NamespacedKey SHOP_ITEM_KEY_STATIC = null; // initialised per instance

    private final NamespacedKey SHOP_ITEM_KEY;

    public BlackMarketListener(MJB plugin) {
        this.plugin = plugin;
        SHOP_ITEM_KEY = new NamespacedKey(plugin, "shop_item_type");
    }

    // ---- NPC click ----

    @EventHandler
    public void onNPCRightClick(NPCRightClickEvent event) {
        NPC npc = event.getNPC();
        if (!npc.data().has(BLACK_MARKET_TAG)) return;
        openShopMenu(event.getClicker());
    }

    // ---- Shop GUI ----

    private void openShopMenu(Player player) {
        Inventory gui = plugin.getServer().createInventory(null, 54, SHOP_GUI_TITLE);

        gui.setItem(4, info(Material.BARRIER, "§4§lBlack Market",
                "§8Shady dealings only.", "§7All purchases charged to bank."));

        // Weapons row (slot 10-16)
        gui.setItem(10, weaponItem(WeaponType.PISTOL));
        gui.setItem(12, weaponItem(WeaponType.RIFLE));
        gui.setItem(14, weaponItem(WeaponType.SHOTGUN));
        gui.setItem(16, weaponItem(WeaponType.KNIFE));

        // Separators
        gui.setItem(27, info(Material.RED_STAINED_GLASS_PANE, "§c§lAmmo", "§7Buy magazines below."));

        // Ammo row (slot 28-32)
        gui.setItem(28, ammoItem(WeaponType.PISTOL));
        gui.setItem(30, ammoItem(WeaponType.RIFLE));
        gui.setItem(32, ammoItem(WeaponType.SHOTGUN));

        gui.setItem(49, info(Material.BARRIER, "§4Leave", "§7Close the black market."));

        player.openInventory(gui);
    }

    private ItemStack weaponItem(WeaponType type) {
        double price = WeaponManager.WEAPON_PRICES.getOrDefault(type.id, 0.0);
        ItemStack item = plugin.getWeaponManager().createWeapon(type);
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>(meta.getLore() != null ? meta.getLore() : new ArrayList<>());
        lore.add("");
        lore.add("§7Price: §b" + plugin.getEconomyManager().format(price));
        lore.add("§eClick §7to purchase");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(SHOP_ITEM_KEY, PersistentDataType.STRING, "weapon_" + type.id);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack ammoItem(WeaponType type) {
        double price = WeaponManager.AMMO_PRICES.getOrDefault(type.id, 0.0);
        ItemStack item = plugin.getWeaponManager().createAmmo(type);
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>(meta.getLore() != null ? meta.getLore() : new ArrayList<>());
        lore.add("");
        lore.add("§7Price: §b" + plugin.getEconomyManager().format(price));
        lore.add("§eClick §7to purchase");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(SHOP_ITEM_KEY, PersistentDataType.STRING, "ammo_" + type.id);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack info(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    // ---- Click handler ----

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(SHOP_GUI_TITLE)) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR || !clicked.hasItemMeta()) return;

        if (clicked.getType() == Material.BARRIER) { player.closeInventory(); return; }

        if (!clicked.getItemMeta().getPersistentDataContainer().has(SHOP_ITEM_KEY, PersistentDataType.STRING)) return;
        String shopType = clicked.getItemMeta().getPersistentDataContainer()
                .get(SHOP_ITEM_KEY, PersistentDataType.STRING);

        if (shopType.startsWith("weapon_")) {
            WeaponType type = WeaponType.fromId(shopType.substring(7));
            if (type == null) return;
            double price = WeaponManager.WEAPON_PRICES.getOrDefault(type.id, 0.0);
            handlePurchase(player, price, () -> {
                player.getInventory().addItem(plugin.getWeaponManager().createWeapon(type));
                player.sendMessage("§a§l[Black Market] §aYou purchased a §f" + type.displayName +
                        " §afor §f" + plugin.getEconomyManager().format(price) + "§a.");
                player.sendMessage("§c§lWarning: §cPossessing this weapon is illegal. Use at your own risk.");
            });

        } else if (shopType.startsWith("ammo_")) {
            WeaponType type = WeaponType.fromId(shopType.substring(5));
            if (type == null) return;
            double price = WeaponManager.AMMO_PRICES.getOrDefault(type.id, 0.0);
            handlePurchase(player, price, () -> {
                ItemStack ammo = plugin.getWeaponManager().createAmmo(type);
                if (ammo != null) player.getInventory().addItem(ammo);
                player.sendMessage("§a§l[Black Market] §aYou purchased §f" + type.displayName +
                        " Ammo §afor §f" + plugin.getEconomyManager().format(price) + "§a.");
            });
        }

        openShopMenu(player); // refresh balance display
    }

    private void handlePurchase(Player player, double price, Runnable onSuccess) {
        double balance = plugin.getEconomyManager().getBankBalance(player.getUniqueId());
        if (balance < price) {
            player.sendMessage("§4Insufficient bank balance. Need §f" +
                    plugin.getEconomyManager().format(price) + "§4.");
            return;
        }
        String sql = "UPDATE players SET bank_balance = bank_balance - ? WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setDouble(1, price);
            stmt.setString(2, player.getUniqueId().toString());
            stmt.executeUpdate();
            onSuccess.run();
        } catch (SQLException e) {
            player.sendMessage("§4Transaction failed. Please try again.");
            plugin.getLogger().severe("Black market purchase error: " + e.getMessage());
        }
    }

    // ---- 8-hour teleport scheduler ----

    public void startTeleportScheduler() {
        long eightHours = 20L * 60 * 60 * 8;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::teleportBlackMarketNPC,
                eightHours, eightHours);
    }

    public void teleportNow() {
        teleportBlackMarketNPC();
    }

    private void teleportBlackMarketNPC() {
        List<WeaponManager.BlackMarketLocation> locations =
                plugin.getWeaponManager().getBlackMarketLocations();
        if (locations.isEmpty()) {
            plugin.getLogger().warning("[BlackMarket] No spawn locations registered. Use /mjbadmin addblackmarket.");
            return;
        }

        // Find the black market NPC
        NPC marketNpc = null;
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.data().has(BLACK_MARKET_TAG)) { marketNpc = npc; break; }
        }
        if (marketNpc == null) {
            plugin.getLogger().warning("[BlackMarket] No NPC tagged as black_market found. Use /mjbadmin setnpc <id> blackmarket.");
            return;
        }

        WeaponManager.BlackMarketLocation chosen =
                locations.get((int) (Math.random() * locations.size()));

        if (!marketNpc.isSpawned()) {
            marketNpc.spawn(chosen.location);
        } else {
            marketNpc.teleport(chosen.location,
                    org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
        }
    }
}