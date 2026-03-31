package com.UserMC.MJB;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class ThirstManager {

    private final MJB plugin;

    public static final int MAX_THIRST = 20;

    // Registry: maps a Material (+optional PotionType) to drink data.
    // Adding future drinks (coffee, juice, etc.) = one registerDrink() call.
    private final List<DrinkEntry> drinkRegistry = new ArrayList<>();

    public ThirstManager(MJB plugin) {
        this.plugin = plugin;

        // Vanilla water bottle — drunk via vanilla consume mechanic
        // returnsBottle = false: we delete the glass bottle so players can't refill
        registerDrink(new DrinkEntry(
                Material.POTION,
                PotionType.WATER,
                MAX_THIRST,   // full restore
                false,        // do NOT return glass bottle
                List.of()     // no extra effects
        ));
    }

    // ---- Drink registry ----

    public void registerDrink(DrinkEntry entry) {
        drinkRegistry.add(entry);
    }

    /**
     * Returns the DrinkEntry for a given item, or null if it's not a registered drink.
     */
    public DrinkEntry getDrinkEntry(ItemStack item) {
        if (item == null) return null;
        for (DrinkEntry entry : drinkRegistry) {
            if (item.getType() != entry.material) continue;
            if (entry.requiredPotionType != null) {
                if (!(item.getItemMeta() instanceof PotionMeta meta)) continue;
                if (meta.getBasePotionType() != entry.requiredPotionType) continue;
            }
            return entry;
        }
        return null;
    }

    // ---- Thirst DB operations ----

    public int getThirst(UUID uuid) {
        String sql = "SELECT thirst FROM players WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("thirst");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting thirst: " + e.getMessage());
        }
        return MAX_THIRST;
    }

    public void setThirst(UUID uuid, int thirst) {
        int clamped = Math.max(0, Math.min(MAX_THIRST, thirst));
        String sql = "UPDATE players SET thirst = ? WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, clamped);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error setting thirst: " + e.getMessage());
        }
    }

    // ---- Called by ThirstListener on PlayerItemConsumeEvent ----

    /**
     * Handles a player finishing drinking a registered drink.
     * Called AFTER vanilla consumption — we just update thirst and kill the bottle return.
     * Returns true if the item was a registered drink.
     */
    public boolean onDrink(Player player, ItemStack item) {
        DrinkEntry entry = getDrinkEntry(item);
        if (entry == null) return false;

        int current = getThirst(player.getUniqueId());
        int newThirst = Math.min(MAX_THIRST, current + entry.thirstRestore);
        setThirst(player.getUniqueId(), newThirst);

        // Apply drink-specific effects (future: coffee = speed, etc.)
        for (PotionEffect effect : entry.extraEffects) {
            player.addPotionEffect(effect);
        }

        // Remove thirst debuffs now that player has drunk
        if (newThirst > 0) {
            removeThirstEffects(player);
        }

        player.sendMessage("§b§l[Thirst] §fYou feel refreshed. §7(" + newThirst + "/" + MAX_THIRST + ")");
        return true;
    }

    // ---- Effects ----

    public void applyThirstEffects(Player player) {
        int duration = 20 * 60 * 3; // 3 min — refreshed every 2 min drain tick
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 0, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, duration, 0, false, true));
        player.setSprinting(false);
        player.sendMessage("§4§l[Thirst] §4You are dehydrated! Find water immediately.");
    }

    public void removeThirstEffects(Player player) {
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.WEAKNESS);
    }

    // ---- Scheduler ----

    public void startDrainScheduler() {
        long twoMinutes = 20L * 60 * 2;
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                int current = getThirst(player.getUniqueId());
                int newThirst = Math.max(0, current - 1);
                setThirst(player.getUniqueId(), newThirst);

                if (newThirst == 0) {
                    applyThirstEffects(player);
                    // 0.5 hearts damage per drain tick
                    double newHealth = player.getHealth() - 1.0;
                    if (newHealth <= 0) {
                        // Go downed instead of dying
                        plugin.getHospitalManager().goDown(player,
                                HospitalManager.InjuryType.BLEEDING);
                    } else {
                        player.setHealth(newHealth);
                    }
                } else if (newThirst <= 3) {
                    player.sendMessage("§e§l[Thirst] §eYou are getting thirsty. §7(" + newThirst + "/" + MAX_THIRST + ")");
                }
            }
        }, twoMinutes, twoMinutes);
    }

    // ---- Data class ----

    public static class DrinkEntry {
        public final Material material;
        public final PotionType requiredPotionType; // null = match any
        public final int thirstRestore;
        public final boolean returnsBottle; // reserved for future drinks that should give something back
        public final List<PotionEffect> extraEffects;

        public DrinkEntry(Material material, PotionType requiredPotionType,
                          int thirstRestore, boolean returnsBottle,
                          List<PotionEffect> extraEffects) {
            this.material = material;
            this.requiredPotionType = requiredPotionType;
            this.thirstRestore = thirstRestore;
            this.returnsBottle = returnsBottle;
            this.extraEffects = extraEffects;
        }
    }
}