package com.UserMC.MJB.listeners;

import com.UserMC.MJB.DrugManager;
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
import java.util.*;

public class BlackMarketListener implements Listener {

    private final MJB plugin;
    public static final String BLACK_MARKET_TAG = "black_market";
    private static final String SHOP_GUI_TITLE  = "§4§lBlack Market";
    private final NamespacedKey SHOP_ITEM_KEY;

    // Current rotation — 4 slots, regenerated every 8 hours
    private final List<RotationSlot> currentRotation = new ArrayList<>();

    // All possible items that can appear in rotation
    private record PoolEntry(String id, String category) {}

    private static final List<PoolEntry> ITEM_POOL = List.of(
            new PoolEntry("weapon_pistol",    "weapon"),
            new PoolEntry("weapon_rifle",     "weapon"),
            new PoolEntry("weapon_shotgun",   "weapon"),
            new PoolEntry("weapon_knife",     "weapon"),
            new PoolEntry("ammo_pistol",      "ammo"),
            new PoolEntry("ammo_rifle",       "ammo"),
            new PoolEntry("ammo_shotgun",     "ammo"),
            new PoolEntry("drug_WEED",        "drug"),
            new PoolEntry("drug_COCAINE",     "drug"),
            new PoolEntry("drug_HEROIN",      "drug"),
            new PoolEntry("drug_MORPHINE",    "drug"),
            new PoolEntry("drug_FENTANYL",    "drug"),
            new PoolEntry("seed_WEED",        "seed"),
            new PoolEntry("seed_COCAINE",     "seed"),
            new PoolEntry("seed_HEROIN",      "seed")
    );

    // Holds one rotation entry
    private static class RotationSlot {
        final String shopType;    // e.g. "weapon_pistol", "drug_WEED"
        final double basePrice;
        final double modifier;    // 0.5 to 1.5
        final double finalPrice;

        RotationSlot(String shopType, double basePrice, double modifier) {
            this.shopType   = shopType;
            this.basePrice  = basePrice;
            this.modifier   = modifier;
            this.finalPrice = Math.round(basePrice * modifier * 100.0) / 100.0;
        }

        String modifierLabel() {
            int pct = (int) Math.round((modifier - 1.0) * 100);
            if (pct > 0) return "§c+" + pct + "% §7(scarce)";
            if (pct < 0) return "§a" + pct + "% §7(surplus)";
            return "§7Normal price";
        }
    }

    public BlackMarketListener(MJB plugin) {
        this.plugin = plugin;
        SHOP_ITEM_KEY = new NamespacedKey(plugin, "shop_item_type");
        generateRotation(); // generate on startup
    }

    // ---- Rotation generation ----

    private void generateRotation() {
        currentRotation.clear();
        Random rand = new Random();

        // Pick 4 unique items from the pool
        List<PoolEntry> pool = new ArrayList<>(ITEM_POOL);
        Collections.shuffle(pool, rand);

        for (int i = 0; i < 4 && i < pool.size(); i++) {
            PoolEntry entry = pool.get(i);
            double base     = getBasePrice(entry.id);
            // Modifier: 0.5 to 1.5 in 0.05 steps
            double modifier = 0.5 + (rand.nextInt(21) * 0.05);
            currentRotation.add(new RotationSlot(entry.id, base, modifier));
        }

        plugin.getLogger().info("[BlackMarket] Rotation refreshed: " +
                currentRotation.stream().map(s -> s.shopType).toList());
    }

    private double getBasePrice(String shopType) {
        if (shopType.startsWith("weapon_")) {
            WeaponType type = WeaponType.fromId(shopType.substring(7));
            return type != null ? WeaponManager.WEAPON_PRICES.getOrDefault(type.id, 500.0) : 500.0;
        }
        if (shopType.startsWith("ammo_")) {
            WeaponType type = WeaponType.fromId(shopType.substring(5));
            return type != null ? WeaponManager.AMMO_PRICES.getOrDefault(type.id, 100.0) : 100.0;
        }
        if (shopType.startsWith("drug_")) {
            DrugManager.DrugType type = DrugManager.DrugType.fromId(shopType.substring(5));
            return type != null ? type.blackMarketPrice : 200.0;
        }
        if (shopType.startsWith("seed_")) {
            DrugManager.DrugType type = DrugManager.DrugType.fromId(shopType.substring(5));
            return type != null ? type.blackMarketPrice * 0.5 : 100.0;
        }
        return 100.0;
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
        Inventory gui = plugin.getServer().createInventory(null, 27, SHOP_GUI_TITLE);

        gui.setItem(4, info(Material.BARRIER, "§4§lBlack Market",
                "§7Today's supply — rotates every §f8 hours§7.",
                "§7Prices fluctuate with supply and demand.",
                "§7All purchases charged to bank."));

        // 4 rotation slots
        int[] slots = {10, 12, 14, 16};
        for (int i = 0; i < currentRotation.size(); i++) {
            RotationSlot slot = currentRotation.get(i);
            ItemStack item = buildShopItem(slot);
            if (item != null) gui.setItem(slots[i], item);
        }

        gui.setItem(22, info(Material.BARRIER, "§4Leave", "§7Close the black market."));
        player.openInventory(gui);
    }

    private ItemStack buildShopItem(RotationSlot slot) {
        ItemStack base = null;
        String label   = "Unknown";

        if (slot.shopType.startsWith("weapon_")) {
            WeaponType type = WeaponType.fromId(slot.shopType.substring(7));
            if (type == null) return null;
            base  = plugin.getWeaponManager().createWeapon(type);
            label = type.displayName;
        } else if (slot.shopType.startsWith("ammo_")) {
            WeaponType type = WeaponType.fromId(slot.shopType.substring(5));
            if (type == null) return null;
            base  = plugin.getWeaponManager().createAmmo(type);
            label = type.displayName + " Ammo";
        } else if (slot.shopType.startsWith("drug_")) {
            DrugManager.DrugType type =
                    DrugManager.DrugType.fromId(slot.shopType.substring(5));
            if (type == null) return null;
            base  = plugin.getDrugManager().createDrug(type);
            label = type.displayName;
        } else if (slot.shopType.startsWith("seed_")) {
            DrugManager.DrugType type =
                    DrugManager.DrugType.fromId(slot.shopType.substring(5));
            if (type == null) return null;
            base  = plugin.getDrugManager().createSeed(type);
            label = type.displayName + " Seeds";
        }

        if (base == null) return null;

        ItemMeta meta = base.getItemMeta();
        List<String> lore = new ArrayList<>(
                meta.getLore() != null ? meta.getLore() : new ArrayList<>());
        lore.add("");
        lore.add("§7Price: §b" + plugin.getEconomyManager().format(slot.finalPrice));
        lore.add(slot.modifierLabel());
        lore.add("§eClick §7to purchase");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(
                SHOP_ITEM_KEY, PersistentDataType.STRING, slot.shopType);
        // Store the final price in NBT so click handler uses the rotated price
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "bm_price"),
                PersistentDataType.DOUBLE, slot.finalPrice);
        base.setItemMeta(meta);
        return base;
    }

    private ItemStack info(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta  = item.getItemMeta();
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
        if (clicked == null || clicked.getType() == Material.AIR ||
                !clicked.hasItemMeta()) return;
        if (clicked.getType() == Material.BARRIER) {
            player.closeInventory(); return;
        }

        if (!clicked.getItemMeta().getPersistentDataContainer()
                .has(SHOP_ITEM_KEY, PersistentDataType.STRING)) return;

        String shopType = clicked.getItemMeta().getPersistentDataContainer()
                .get(SHOP_ITEM_KEY, PersistentDataType.STRING);

        // Get the stored rotated price
        NamespacedKey priceKey = new NamespacedKey(plugin, "bm_price");
        if (!clicked.getItemMeta().getPersistentDataContainer()
                .has(priceKey, PersistentDataType.DOUBLE)) return;
        double price = clicked.getItemMeta().getPersistentDataContainer()
                .get(priceKey, PersistentDataType.DOUBLE);

        if (shopType.startsWith("weapon_")) {
            WeaponType type = WeaponType.fromId(shopType.substring(7));
            if (type == null) return;
            handlePurchase(player, price, () -> {
                player.getInventory().addItem(
                        plugin.getWeaponManager().createWeapon(type));
                player.sendMessage("§a§l[Black Market] §aYou purchased a §f" +
                        type.displayName + " §afor §f" +
                        plugin.getEconomyManager().format(price) + "§a.");
                player.sendMessage("§c§lWarning: §cPossessing this weapon is illegal. " +
                        "Use at your own risk.");
            });
        } else if (shopType.startsWith("ammo_")) {
            WeaponType type = WeaponType.fromId(shopType.substring(5));
            if (type == null) return;
            handlePurchase(player, price, () -> {
                ItemStack ammo = plugin.getWeaponManager().createAmmo(type);
                if (ammo != null) player.getInventory().addItem(ammo);
                player.sendMessage("§a§l[Black Market] §aYou purchased §f" +
                        type.displayName + " Ammo §afor §f" +
                        plugin.getEconomyManager().format(price) + "§a.");
            });
        } else if (shopType.startsWith("drug_")) {
            DrugManager.DrugType type =
                    DrugManager.DrugType.fromId(shopType.substring(5));
            if (type == null) return;
            handlePurchase(player, price, () -> {
                player.getInventory().addItem(
                        plugin.getDrugManager().createDrug(type));
                player.sendMessage("§a§l[Black Market] §aYou purchased §f" +
                        type.displayName + " §afor §f" +
                        plugin.getEconomyManager().format(price) + "§a.");
                player.sendMessage("§c§lWarning: §cPossession may be illegal. " +
                        "Check §f/laws§c.");
            });
        } else if (shopType.startsWith("seed_")) {
            DrugManager.DrugType type =
                    DrugManager.DrugType.fromId(shopType.substring(5));
            if (type == null) return;
            handlePurchase(player, price, () -> {
                ItemStack seed = plugin.getDrugManager().createSeed(type);
                if (seed != null) player.getInventory().addItem(seed);
                player.sendMessage("§a§l[Black Market] §aYou purchased §f" +
                        type.displayName + " seeds §afor §f" +
                        plugin.getEconomyManager().format(price) + "§a.");
            });
        }

        openShopMenu(player); // refresh
    }

    private void handlePurchase(Player player, double price, Runnable onSuccess) {
        double balance = plugin.getEconomyManager().getBankBalance(player.getUniqueId());
        if (balance < price) {
            player.sendMessage("§4Insufficient bank balance. Need §f" +
                    plugin.getEconomyManager().format(price) + "§4.");
            return;
        }
        String sql = "UPDATE players SET bank_balance = bank_balance - ? WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setDouble(1, price);
            stmt.setString(2, player.getUniqueId().toString());
            stmt.executeUpdate();
            onSuccess.run();
        } catch (SQLException e) {
            player.sendMessage("§4Transaction failed. Please try again.");
            plugin.getLogger().severe("Black market purchase error: " + e.getMessage());
        }
    }

    // ---- 8-hour rotation + teleport scheduler ----

    public void startTeleportScheduler() {
        long eightHours = 20L * 60 * 60 * 8;
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            generateRotation();         // regenerate items + prices
            teleportBlackMarketNPC();   // move the NPC
        }, eightHours, eightHours);
    }

    public void teleportNow() {
        generateRotation();
        teleportBlackMarketNPC();
    }

    private void teleportBlackMarketNPC() {
        List<WeaponManager.BlackMarketLocation> locations =
                plugin.getWeaponManager().getBlackMarketLocations();
        if (locations.isEmpty()) {
            plugin.getLogger().warning("[BlackMarket] No spawn locations registered.");
            return;
        }
        NPC marketNpc = null;
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.data().has(BLACK_MARKET_TAG)) { marketNpc = npc; break; }
        }
        if (marketNpc == null) {
            plugin.getLogger().warning("[BlackMarket] No NPC tagged as black_market found.");
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