package com.UserMC.MJB;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WeaponManager {

    private final MJB plugin;

    public final NamespacedKey IS_WEAPON_KEY;
    public final NamespacedKey WEAPON_TYPE_KEY;
    public final NamespacedKey WEAPON_AMMO_KEY;
    public final NamespacedKey IS_AMMO_KEY;
    public final NamespacedKey AMMO_TYPE_KEY;
    public final NamespacedKey IS_WEAPON_PROJECTILE_KEY;
    public final NamespacedKey PROJECTILE_DAMAGE_KEY;
    public final NamespacedKey PROJECTILE_SHOOTER_KEY;
    public final NamespacedKey PROJECTILE_WEAPON_TYPE_KEY;

    // uuid -> reload finish timestamp (ms)
    private final Map<UUID, Long> reloadFinishTime = new ConcurrentHashMap<>();
    // victim uuid -> time they were last attacked (ms) — used for self-defense window
    private final Map<UUID, Long> lastAttackedTime = new ConcurrentHashMap<>();

    // ---- Weapon types ----

    public enum WeaponType {
        PISTOL  (Material.NETHER_STAR,        "Pistol",  "pistol",  12, 3,  20.0, 3.5f, "§6"),
        RIFLE   (Material.BLAZE_ROD,   "Rifle",   "rifle",   30, 8,  10.0, 5.0f, "§c"),
        SHOTGUN (Material.GHAST_TEAR,  "Shotgun", "shotgun",  6, 15,  12.0, 2.5f, "§5"),
        KNIFE   (Material.IRON_SWORD, "Knife",   "knife",    0,  0, 16.0, 0.0f, "§7");

        public final Material material;
        public final String displayName;
        public final String id;
        public final int magSize;
        public final int reloadSeconds;
        public final double damage;
        public final float projectileSpeed;
        public final String color;

        WeaponType(Material material, String displayName, String id,
                   int magSize, int reloadSeconds, double damage,
                   float projectileSpeed, String color) {
            this.material = material;
            this.displayName = displayName;
            this.id = id;
            this.magSize = magSize;
            this.reloadSeconds = reloadSeconds;
            this.damage = damage;
            this.projectileSpeed = projectileSpeed;
            this.color = color;
        }

        public static WeaponType fromId(String id) {
            if (id == null) return null;
            for (WeaponType t : values()) if (t.id.equals(id)) return t;
            return null;
        }
    }

    // ---- Prices ----

    public static final Map<String, Double> WEAPON_PRICES = Map.of(
            "pistol",  6000.0,
            "rifle",   12000.0,
            "shotgun", 8000.0,
            "knife",   2000.0
    );

    public static final Map<String, Double> AMMO_PRICES = Map.of(
            "pistol",  150.0,
            "rifle",   300.0,
            "shotgun", 200.0
    );

    // ---- Constructor ----

    public WeaponManager(MJB plugin) {
        this.plugin = plugin;
        IS_WEAPON_KEY            = new NamespacedKey(plugin, "is_weapon");
        WEAPON_TYPE_KEY          = new NamespacedKey(plugin, "weapon_type");
        WEAPON_AMMO_KEY          = new NamespacedKey(plugin, "weapon_ammo");
        IS_AMMO_KEY              = new NamespacedKey(plugin, "is_ammo");
        AMMO_TYPE_KEY            = new NamespacedKey(plugin, "ammo_type");
        IS_WEAPON_PROJECTILE_KEY = new NamespacedKey(plugin, "weapon_projectile");
        PROJECTILE_DAMAGE_KEY    = new NamespacedKey(plugin, "projectile_damage");
        PROJECTILE_SHOOTER_KEY   = new NamespacedKey(plugin, "projectile_shooter");
        PROJECTILE_WEAPON_TYPE_KEY = new NamespacedKey(plugin, "projectile_weapon_type");
    }

    // ---- Item creation ----

    public ItemStack createWeapon(WeaponType type) {
        ItemStack item = new ItemStack(type.material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(type.color + "§l" + type.displayName);

        List<String> lore = new ArrayList<>();
        if (type != WeaponType.KNIFE) {
            lore.add("§7Ammo: §f" + type.magSize + "/" + type.magSize);
            lore.add("§7Damage: §f" + (type == WeaponType.SHOTGUN
                    ? type.damage + " x3 pellets" : String.valueOf(type.damage)));
            lore.add("§7Reload: §f" + type.reloadSeconds + "s");
        } else {
            lore.add("§7Type: §fMelee");
            lore.add("§7Damage: §f" + type.damage);
        }
        lore.add("§c§lPossession is illegal!");

        meta.setLore(lore);
        meta.getPersistentDataContainer().set(IS_WEAPON_KEY,   PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(WEAPON_TYPE_KEY, PersistentDataType.STRING,  type.id);
        if (type != WeaponType.KNIFE) {
            meta.getPersistentDataContainer().set(WEAPON_AMMO_KEY, PersistentDataType.INTEGER, type.magSize);
        }
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createAmmo(WeaponType type) {
        if (type == WeaponType.KNIFE) return null;
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§f§l" + type.displayName + " Ammo");
        meta.setLore(List.of(
                "§7" + type.magSize + " rounds per magazine",
                "§7For: §f" + type.displayName,
                "§c§lIllegal item"
        ));
        meta.getPersistentDataContainer().set(IS_AMMO_KEY,   PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(AMMO_TYPE_KEY, PersistentDataType.STRING,  type.id);
        item.setItemMeta(meta);
        return item;
    }

    // ---- Item checks ----

    public boolean isWeapon(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(IS_WEAPON_KEY, PersistentDataType.BOOLEAN);
    }

    public WeaponType getWeaponType(ItemStack item) {
        if (!isWeapon(item)) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(WEAPON_TYPE_KEY, PersistentDataType.STRING);
        return WeaponType.fromId(id);
    }

    public int getCurrentAmmo(ItemStack item) {
        if (!isWeapon(item)) return -1;
        Integer ammo = item.getItemMeta().getPersistentDataContainer().get(WEAPON_AMMO_KEY, PersistentDataType.INTEGER);
        return ammo != null ? ammo : 0;
    }

    public void setCurrentAmmo(ItemStack item, int ammo) {
        if (!isWeapon(item)) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(WEAPON_AMMO_KEY, PersistentDataType.INTEGER, ammo);
        // Refresh ammo line in lore
        WeaponType type = getWeaponType(item);
        if (type != null && type != WeaponType.KNIFE && meta.getLore() != null) {
            List<String> lore = new ArrayList<>(meta.getLore());
            for (int i = 0; i < lore.size(); i++) {
                if (lore.get(i).contains("Ammo:")) {
                    lore.set(i, "§7Ammo: §f" + ammo + "/" + type.magSize);
                    break;
                }
            }
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
    }

    public boolean isAmmo(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(IS_AMMO_KEY, PersistentDataType.BOOLEAN);
    }

    public String getAmmoType(ItemStack item) {
        if (!isAmmo(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(AMMO_TYPE_KEY, PersistentDataType.STRING);
    }

    // ---- Reload ----

    public boolean isReloading(UUID uuid) {
        Long finish = reloadFinishTime.get(uuid);
        if (finish == null) return false;
        if (System.currentTimeMillis() >= finish) { reloadFinishTime.remove(uuid); return false; }
        return true;
    }

    public void startReload(Player player, WeaponType type) {
        if (isReloading(player.getUniqueId())) return;
        if (!hasAmmoInInventory(player, type)) {
            player.sendMessage("§4§l[!] §4Out of ammo! Buy more at the black market.");
            return;
        }

        long finishAt = System.currentTimeMillis() + (type.reloadSeconds * 1000L);
        reloadFinishTime.put(player.getUniqueId(), finishAt);

        player.sendMessage("§e§l[!] §eReloading " + type.displayName + "... §7(" + type.reloadSeconds + "s)");
        player.playSound(player.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.8f, 1.4f);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            reloadFinishTime.remove(player.getUniqueId());
            if (!player.isOnline()) return;

            if (!consumeAmmoFromInventory(player, type)) {
                player.sendMessage("§4§l[!] §4No ammo found! Buy more at the black market.");
                return;
            }

            // Apply to held weapon if still same type
            ItemStack current = player.getInventory().getItemInMainHand();
            if (isWeapon(current) && getWeaponType(current) == type) {
                setCurrentAmmo(current, type.magSize);
                player.getInventory().setItemInMainHand(current);
            }

            player.sendMessage("§a§l[!] §aReloaded! §7(" + type.magSize + " rounds)");
            player.playSound(player.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_OPEN, 0.8f, 1.4f);
        }, type.reloadSeconds * 20L);
    }

    private boolean hasAmmoInInventory(Player player, WeaponType type) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isAmmo(item) && type.id.equals(getAmmoType(item))) return true;
        }
        return false;
    }

    private boolean consumeAmmoFromInventory(Player player, WeaponType type) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (isAmmo(item) && type.id.equals(getAmmoType(item))) {
                if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
                else player.getInventory().setItem(i, null);
                return true;
            }
        }
        return false;
    }

    // ---- Self-defense tracking ----

    public void recordAttacked(UUID victimUuid) {
        lastAttackedTime.put(victimUuid, System.currentTimeMillis());
    }

    public boolean isInSelfDefense(UUID playerUuid) {
        Long last = lastAttackedTime.get(playerUuid);
        if (last == null) return false;
        return (System.currentTimeMillis() - last) < 15_000L;
    }

    // ---- Black market locations (DB) ----

    public boolean addBlackMarketLocation(Location loc) {
        String sql = "INSERT INTO black_market_locations (world, x, y, z) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, loc.getWorld().getName());
            stmt.setInt(2, loc.getBlockX());
            stmt.setInt(3, loc.getBlockY());
            stmt.setInt(4, loc.getBlockZ());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error adding black market location: " + e.getMessage());
            return false;
        }
    }

    public boolean removeBlackMarketLocation(int id) {
        String sql = "DELETE FROM black_market_locations WHERE id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public List<BlackMarketLocation> getBlackMarketLocations() {
        List<BlackMarketLocation> locs = new ArrayList<>();
        String sql = "SELECT id, world, x, y, z FROM black_market_locations";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                World w = plugin.getServer().getWorld(rs.getString("world"));
                if (w == null) continue;
                locs.add(new BlackMarketLocation(
                        rs.getInt("id"),
                        new Location(w, rs.getInt("x"), rs.getInt("y"), rs.getInt("z"))
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching black market locations: " + e.getMessage());
        }
        return locs;
    }

    public List<BlackMarketLocation> listBlackMarketLocationsRaw() {
        List<BlackMarketLocation> locs = new ArrayList<>();
        String sql = "SELECT id, world, x, y, z FROM black_market_locations";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                // Don't resolve world — just report coords for listing
                locs.add(new BlackMarketLocation(
                        rs.getInt("id"),
                        rs.getString("world"),
                        rs.getInt("x"), rs.getInt("y"), rs.getInt("z")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error listing black market locations: " + e.getMessage());
        }
        return locs;
    }

    // ---- Data classes ----

    public static class BlackMarketLocation {
        public final int id;
        public final Location location;
        public final String worldName;
        public final int x, y, z;

        public BlackMarketLocation(int id, Location location) {
            this.id = id;
            this.location = location;
            this.worldName = location.getWorld().getName();
            this.x = location.getBlockX();
            this.y = location.getBlockY();
            this.z = location.getBlockZ();
        }

        public BlackMarketLocation(int id, String worldName, int x, int y, int z) {
            this.id = id;
            this.location = null;
            this.worldName = worldName;
            this.x = x; this.y = y; this.z = z;
        }
    }
}