package com.UserMC.MJB;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.sql.*;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class DrugManager {

    private final MJB plugin;

    public final NamespacedKey IS_DRUG_KEY;
    public final NamespacedKey DRUG_TYPE_KEY;
    public final NamespacedKey IS_SEED_KEY;
    public final NamespacedKey SEED_TYPE_KEY;

    // Plant block for each drug type (null = no plant, e.g. morphine/fentanyl)
    private static final java.util.Map<DrugType, org.bukkit.Material> PLANT_BLOCKS =
            java.util.Map.of(
                    DrugType.WEED, org.bukkit.Material.SHORT_GRASS,
                    DrugType.COCAINE, org.bukkit.Material.FERN,
                    DrugType.HEROIN, org.bukkit.Material.SWEET_BERRY_BUSH
            );

    // Seed item material for each plantable drug
    private static final java.util.Map<DrugType, org.bukkit.Material> SEED_MATERIALS =
            java.util.Map.of(
                    DrugType.WEED, org.bukkit.Material.WHEAT_SEEDS,
                    DrugType.COCAINE, org.bukkit.Material.NETHER_WART,
                    DrugType.HEROIN, org.bukkit.Material.TORCHFLOWER_SEEDS
            );

    // High duration: 5 seconds
    private static final int HIGH_TICKS = 100;
    // Crash durations
    private static final int WEED_CRASH_TICKS = 3600;  // 3 min
    private static final int COCAINE_CRASH_TICKS = 6000;  // 5 min
    private static final int HEROIN_CRASH_TICKS = 8400;  // 7 min

    // 4 hour harvest cooldown
    public static final long HARVEST_COOLDOWN_MS = 4 * 60 * 60 * 1000L;

    public enum DrugType {
        WEED("Weed", Material.GREEN_DYE, 100.0),
        COCAINE("Cocaine", Material.SUGAR, 300.0),
        HEROIN("Heroin", Material.GLASS_BOTTLE, 500.0),
        MORPHINE("Morphine (Street)", Material.POTION, 200.0),
        FENTANYL("Fentanyl", Material.FERMENTED_SPIDER_EYE, 1000.0);

        public final String displayName;
        public final Material material;
        public final double blackMarketPrice;

        DrugType(String displayName, Material material, double blackMarketPrice) {
            this.displayName = displayName;
            this.material = material;
            this.blackMarketPrice = blackMarketPrice;
        }

        public static DrugType fromId(String id) {
            if (id == null) return null;
            for (DrugType t : values())
                if (t.name().equalsIgnoreCase(id)) return t;
            return null;
        }
    }

    public DrugManager(MJB plugin) {
        this.plugin = plugin;
        IS_DRUG_KEY = new NamespacedKey(plugin, "is_drug");
        DRUG_TYPE_KEY = new NamespacedKey(plugin, "drug_type");
        IS_SEED_KEY = new NamespacedKey(plugin, "is_drug_seed");
        SEED_TYPE_KEY = new NamespacedKey(plugin, "drug_seed_type");
    }

    // ---- Item creation ----

    public ItemStack createDrug(DrugType type) {
        ItemStack item = new ItemStack(type.material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§4§l" + type.displayName);
        meta.setLore(List.of(
                "§7Right-click to use."
        ));
        meta.getPersistentDataContainer().set(
                IS_DRUG_KEY, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(
                DRUG_TYPE_KEY, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    // ---- NBT checks ----

    public boolean isDrug(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(IS_DRUG_KEY, PersistentDataType.BOOLEAN);
    }

    public DrugType getDrugType(ItemStack item) {
        if (!isDrug(item)) return null;
        return DrugType.fromId(item.getItemMeta().getPersistentDataContainer()
                .get(DRUG_TYPE_KEY, PersistentDataType.STRING));
    }

    // ---- Legality ----

    public boolean isLegal(DrugType type) {
        if (type == null) return false;
        return switch (type) {
            case WEED -> plugin.getGovernmentManager().areSoftDrugsLegal();
            case COCAINE, HEROIN, MORPHINE, FENTANYL -> plugin.getGovernmentManager().areHardDrugsLegal();
        };
    }

    public boolean isIllegalDrug(ItemStack item) {
        if (!isDrug(item)) return false;
        DrugType type = getDrugType(item);
        return type != null && !isLegal(type);
    }

    // ---- Drug use ----

    public void useDrug(Player player, ItemStack item) {
        DrugType type = getDrugType(item);
        if (type == null) return;

        // Consume one item
        if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
        else player.getInventory().setItemInMainHand(null);

        // Apply high
        applyHigh(player, type);
        player.sendMessage("§5§l[" + type.displayName + "] §5You feel the rush...");

        // Schedule crash after 5 seconds
        long highTicks = type == DrugType.FENTANYL ? 40L : HIGH_TICKS;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) applyCrash(player, type);
        }, highTicks);

        // Record for addiction tracking
        recordUsage(player.getUniqueId(), type);
        int stage = getAddictionStage(player.getUniqueId(), type);
        if (stage > 0) saveAddictionStage(player.getUniqueId(), type, stage);
    }

    private void applyHigh(Player player, DrugType type) {
        int ticks = type == DrugType.FENTANYL ? 40 : HIGH_TICKS;
        switch (type) {
            case WEED -> {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SPEED, ticks, 0, false, true));
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.JUMP_BOOST, ticks, 0, false, true));
            }
            case COCAINE -> {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SPEED, ticks, 1, false, true));
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.STRENGTH, ticks, 0, false, true));
            }
            case HEROIN -> {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SPEED, ticks, 0, false, true));
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOW_FALLING, ticks, 0, false, true));
            }
            case MORPHINE -> {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SPEED, ticks, 0, false, true));
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.REGENERATION, ticks, 0, false, true));
            }
            case FENTANYL -> {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SPEED, ticks, 2, false, true));
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.STRENGTH, ticks, 1, false, true));
            }
        }
    }

    private void applyCrash(Player player, DrugType type) {
        switch (type) {
            case WEED -> {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS, WEED_CRASH_TICKS, 0, false, true));
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.HUNGER, WEED_CRASH_TICKS, 0, false, true));
                player.sendMessage("§5§l[Weed] §7The high is gone. You feel sluggish and hungry.");
            }
            case COCAINE -> {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.WEAKNESS, COCAINE_CRASH_TICKS, 0, false, true));
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.NAUSEA, COCAINE_CRASH_TICKS, 0, false, true));
                player.sendMessage("§5§l[Cocaine] §7The rush is gone. You feel sick and drained.");
            }
            case HEROIN -> {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS, HEROIN_CRASH_TICKS, 1, false, true));
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.BLINDNESS, HEROIN_CRASH_TICKS, 0, false, true));
                player.sendMessage("§5§l[Heroin] §7You crash hard. Your vision goes dark.");
            }
            case MORPHINE -> {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS, COCAINE_CRASH_TICKS, 0, false, true));
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.WEAKNESS, COCAINE_CRASH_TICKS, 0, false, true));
                player.sendMessage("§5§l[Morphine] §7It wears off. You feel hollow.");
            }
            case FENTANYL -> {
                // Worst crash in the game — Slowness III + Blindness + Wither for 10 min
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS, 12000, 2, false, true));
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.BLINDNESS, 12000, 0, false, true));
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.WITHER, 12000, 0, false, true));
                player.sendMessage("§5§l[Fentanyl] §4Your body is shutting down.");
            }
        }
    }

    // ---- Addiction ----

    public void recordUsage(UUID uuid, DrugType type) {
        String sql = "INSERT INTO drug_usage (player_uuid, drug_type) VALUES (?, ?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, type.name());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error recording drug use: " + e.getMessage());
        }
    }

    public int getUsesLast24h(UUID uuid, DrugType type) {
        String sql = "SELECT COUNT(*) FROM drug_usage WHERE player_uuid = ? " +
                "AND drug_type = ? AND used_at > NOW() - INTERVAL 24 HOUR";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, type.name());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting drug uses: " + e.getMessage());
        }
        return 0;
    }

    // Stage 1: 3+ uses/24h, Stage 2: 6+ uses/24h, Stage 3: 10+ uses/24h
    public int getAddictionStage(UUID uuid, DrugType type) {
        int uses = getUsesLast24h(uuid, type);
        // Fentanyl addicts much faster
        if (type == DrugType.FENTANYL) {
            if (uses >= 7) return 3;
            if (uses >= 4) return 2;
            if (uses >= 2) return 1;
            return 0;
        }
        if (uses >= 10) return 3;
        if (uses >= 6) return 2;
        if (uses >= 3) return 1;
        return 0;
    }

    private void saveAddictionStage(UUID uuid, DrugType type, int stage) {
        String sql = "INSERT INTO drug_addiction (player_uuid, drug_type, stage) " +
                "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE stage = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, type.name());
            stmt.setInt(3, stage);
            stmt.setInt(4, stage);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error saving addiction stage: " + e.getMessage());
        }
    }

    public void clearAddiction(UUID uuid, DrugType type) {
        String sql = "DELETE FROM drug_addiction WHERE player_uuid = ? AND drug_type = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, type.name());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error clearing drug addiction: " + e.getMessage());
        }
        // Remove active effects
        Player p = plugin.getServer().getPlayer(uuid);
        if (p != null) {
            p.removePotionEffect(PotionEffectType.SLOWNESS);
            p.removePotionEffect(PotionEffectType.WEAKNESS);
            p.removePotionEffect(PotionEffectType.NAUSEA);
        }
    }

    public void applyAddictionEffects(Player player, DrugType type, int stage) {
        int duration = 20 * 60 * 10; // 10 min, refreshed by scheduler
        switch (stage) {
            case 1 -> {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS, duration, 0, false, true));
                player.sendMessage("§5§l[Addiction] §5Your body is craving §f" +
                        type.displayName + "§5.");
            }
            case 2 -> {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS, duration, 0, false, true));
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.WEAKNESS, duration, 0, false, true));
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.NAUSEA, duration, 0, false, true));
            }
            case 3 -> {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS, duration, 1, false, true));
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.WEAKNESS, duration, 1, false, true));
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.NAUSEA, duration, 0, false, true));
                // Drugs cause overdose at stage 3
                int delay = (120 + new Random().nextInt(180)) * 20;
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (getAddictionStage(player.getUniqueId(), type) < 3) return;
                    if (plugin.getHospitalManager().isDowned(player.getUniqueId())) return;

                    saveAddictionStage(player.getUniqueId(), type, 2);
                    
                    player.sendMessage("§4You just overdosed!");
                    plugin.getHospitalManager().goDown(player,
                            HospitalManager.InjuryType.OVERDOSE);
                }, delay);
            }
        }
    }

    // ---- Addiction scheduler (every 5 min) ----

    public void startAddictionScheduler() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            String sql = "SELECT player_uuid, drug_type FROM drug_addiction";
            try (PreparedStatement stmt = plugin.getDatabaseManager()
                    .getConnection().prepareStatement(sql)) {
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    DrugType type = DrugType.fromId(rs.getString("drug_type"));
                    if (type == null) continue;

                    int uses = getUsesLast24h(uuid, type);
                    int stage = getAddictionStage(uuid, type);

                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (uses < 3) {
                            clearAddiction(uuid, type);
                        } else {
                            Player p = plugin.getServer().getPlayer(uuid);
                            if (p != null) applyAddictionEffects(p, type, stage);
                        }
                    });
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Drug addiction scheduler error: " + e.getMessage());
            }
        }, 0L, 20L * 60 * 5);
    }

    // ---- Harvest cooldown ----

    public boolean canHarvest(org.bukkit.Location loc) {
        String sql = "SELECT last_harvested FROM drug_harvest_cooldowns " +
                "WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, loc.getWorld().getName());
            stmt.setInt(2, loc.getBlockX());
            stmt.setInt(3, loc.getBlockY());
            stmt.setInt(4, loc.getBlockZ());
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) return true;
            long last = rs.getTimestamp("last_harvested").getTime();
            return System.currentTimeMillis() - last >= HARVEST_COOLDOWN_MS;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking harvest cooldown: " + e.getMessage());
            return false;
        }
    }

    public void setHarvestCooldown(org.bukkit.Location loc) {
        String sql = "INSERT INTO drug_harvest_cooldowns " +
                "(world, x, y, z, last_harvested) VALUES (?, ?, ?, ?, NOW()) " +
                "ON DUPLICATE KEY UPDATE last_harvested = NOW()";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, loc.getWorld().getName());
            stmt.setInt(2, loc.getBlockX());
            stmt.setInt(3, loc.getBlockY());
            stmt.setInt(4, loc.getBlockZ());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error setting harvest cooldown: " + e.getMessage());
        }
    }

    public long getHarvestRemainingMs(org.bukkit.Location loc) {
        String sql = "SELECT last_harvested FROM drug_harvest_cooldowns " +
                "WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, loc.getWorld().getName());
            stmt.setInt(2, loc.getBlockX());
            stmt.setInt(3, loc.getBlockY());
            stmt.setInt(4, loc.getBlockZ());
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) return 0;
            long last = rs.getTimestamp("last_harvested").getTime();
            return Math.max(0, HARVEST_COOLDOWN_MS - (System.currentTimeMillis() - last));
        } catch (SQLException e) {
            return 0;
        }
    }

    private String formatTime(long ms) {
        long mins = ms / 60000;
        if (mins >= 60) return (mins / 60) + "h " + (mins % 60) + "m";
        return mins + "m";
    }

    // ---- Harvest logic ----

    public void handleHarvest(Player player, org.bukkit.block.Block block) {
        // Only registered (player-planted) plants are harvestable
        if (!isRegisteredPlant(block.getLocation())) {
            return;
        }

        DrugType type = getPlantedDrugType(block.getLocation());
        if (type == null) return;

        if (!canHarvest(block.getLocation())) {
            long remaining = getHarvestRemainingMs(block.getLocation());
            player.sendMessage("§4This plant was recently harvested. " +
                    "§7Come back in §f" + formatTime(remaining) + "§7.");
            return;
        }

        int amount = 1 + new Random().nextInt(2);
        for (int i = 0; i < amount; i++) {
            player.getInventory().addItem(createDrug(type));
        }
        setHarvestCooldown(block.getLocation());

        player.sendMessage("§aYou harvested §f" + amount + "x §a" +
                type.displayName + "§a.");
        player.sendMessage("§7This plant will be ready again in §f4 hours§7.");
    }
    // ---- Seed items ----

    public ItemStack createSeed(DrugType type) {
        Material seedMat = SEED_MATERIALS.get(type);
        if (seedMat == null) return null;
        ItemStack item = new ItemStack(seedMat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§2§l" + type.displayName + " Seeds");
        meta.setLore(List.of(
                "§7Right-click on grass or dirt to plant.",
                "§7Harvest after §f4 hours§7.",
                "§c§lIllegal substance"
        ));
        meta.getPersistentDataContainer().set(IS_SEED_KEY,
                PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(SEED_TYPE_KEY,
                PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    public boolean isSeed(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(IS_SEED_KEY, PersistentDataType.BOOLEAN);
    }

    public DrugType getSeedDrugType(ItemStack item) {
        if (!isSeed(item)) return null;
        return DrugType.fromId(item.getItemMeta().getPersistentDataContainer()
                .get(SEED_TYPE_KEY, PersistentDataType.STRING));
    }

    public org.bukkit.Material getPlantMaterial(DrugType type) {
        return PLANT_BLOCKS.get(type);
    }

// ---- Planted plant registration ----

    public boolean isRegisteredPlant(org.bukkit.Location loc) {
        String sql = "SELECT 1 FROM drug_planted_locations " +
                "WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, loc.getWorld().getName());
            stmt.setInt(2, loc.getBlockX());
            stmt.setInt(3, loc.getBlockY());
            stmt.setInt(4, loc.getBlockZ());
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }

    public DrugType getPlantedDrugType(org.bukkit.Location loc) {
        String sql = "SELECT drug_type FROM drug_planted_locations " +
                "WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, loc.getWorld().getName());
            stmt.setInt(2, loc.getBlockX());
            stmt.setInt(3, loc.getBlockY());
            stmt.setInt(4, loc.getBlockZ());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return DrugType.fromId(rs.getString("drug_type"));
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting planted drug type: " + e.getMessage());
        }
        return null;
    }

    public void registerPlant(org.bukkit.Location loc, DrugType type) {
        String sql = "INSERT INTO drug_planted_locations " +
                "(world, x, y, z, drug_type) VALUES (?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE drug_type = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, loc.getWorld().getName());
            stmt.setInt(2, loc.getBlockX());
            stmt.setInt(3, loc.getBlockY());
            stmt.setInt(4, loc.getBlockZ());
            stmt.setString(5, type.name());
            stmt.setString(6, type.name());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error registering drug plant: " + e.getMessage());
        }
    }

    public void unregisterPlant(org.bukkit.Location loc) {
        String sql = "DELETE FROM drug_planted_locations " +
                "WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, loc.getWorld().getName());
            stmt.setInt(2, loc.getBlockX());
            stmt.setInt(3, loc.getBlockY());
            stmt.setInt(4, loc.getBlockZ());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error unregistering drug plant: " + e.getMessage());
        }
    }
}