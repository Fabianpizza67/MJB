package com.UserMC.MJB;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PoliceManager {

    private final MJB plugin;

    // NBT keys
    public final NamespacedKey IS_HANDCUFFS_KEY;
    public final NamespacedKey IS_BADGE_KEY;
    public final NamespacedKey IS_EVIDENCE_BAG_KEY;
    public final NamespacedKey IS_ILLEGAL_KEY; // generic illegal item marker for future use

    // In-memory cuffed state: victim UUID -> officer UUID
    private final Map<UUID, UUID> cuffedPlayers = new ConcurrentHashMap<>();

    // Auto-uncuff timer: victim UUID -> scheduled task id
    private final Map<UUID, Integer> uncuffTimers = new ConcurrentHashMap<>();

    public PoliceManager(MJB plugin) {
        this.plugin = plugin;
        IS_HANDCUFFS_KEY  = new NamespacedKey(plugin, "is_handcuffs");
        IS_BADGE_KEY      = new NamespacedKey(plugin, "is_badge");
        IS_EVIDENCE_BAG_KEY = new NamespacedKey(plugin, "is_evidence_bag");
        IS_ILLEGAL_KEY    = new NamespacedKey(plugin, "is_illegal");
    }

    // ---- Police rank DB ----

    public boolean isOfficer(UUID uuid) {
        String sql = "SELECT 1 FROM police_officers WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking officer: " + e.getMessage());
            return false;
        }
    }

    public boolean addOfficer(UUID uuid, UUID appointedBy) {
        String sql = "INSERT IGNORE INTO police_officers (uuid, appointed_by) VALUES (?, ?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, appointedBy != null ? appointedBy.toString() : null);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error adding officer: " + e.getMessage());
            return false;
        }
    }

    public boolean removeOfficer(UUID uuid) {
        String sql = "DELETE FROM police_officers WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error removing officer: " + e.getMessage());
            return false;
        }
    }

    public List<OfficerInfo> getAllOfficers() {
        List<OfficerInfo> officers = new ArrayList<>();
        String sql = "SELECT uuid, appointed_by, appointed_at FROM police_officers";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String apptBy = rs.getString("appointed_by");
                officers.add(new OfficerInfo(
                        UUID.fromString(rs.getString("uuid")),
                        apptBy != null ? UUID.fromString(apptBy) : null,
                        rs.getTimestamp("appointed_at")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching officers: " + e.getMessage());
        }
        return officers;
    }

    // ---- Cuffing ----

    public boolean isCuffed(UUID uuid) {
        return cuffedPlayers.containsKey(uuid);
    }

    public UUID getCuffingOfficer(UUID victim) {
        return cuffedPlayers.get(victim);
    }

    public void cuff(UUID victimUuid, UUID officerUuid) {
        cuffedPlayers.put(victimUuid, officerUuid);

        // Auto-uncuff after 10 minutes if officer disconnects
        int taskId = plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            if (isCuffed(victimUuid) && getCuffingOfficer(victimUuid).equals(officerUuid)) {
                uncuff(victimUuid);
                Player victim = plugin.getServer().getPlayer(victimUuid);
                if (victim != null) victim.sendMessage("§7You have been released — officer disconnected.");
            }
        }, 20L * 60 * 10); // 10 minutes

        uncuffTimers.put(victimUuid, taskId);
    }

    public void uncuff(UUID victimUuid) {
        cuffedPlayers.remove(victimUuid);
        Integer taskId = uncuffTimers.remove(victimUuid);
        if (taskId != null) plugin.getServer().getScheduler().cancelTask(taskId);

        Player victim = plugin.getServer().getPlayer(victimUuid);
        if (victim != null) {
            victim.setWalkSpeed(0.2f); // restore default walk speed
        }
    }

    public Set<UUID> getCuffedPlayers() {
        return Collections.unmodifiableSet(cuffedPlayers.keySet());
    }

    // ---- Illegal item checks ----

    public boolean isIllegal(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        // Weapons are always illegal
        if (plugin.getWeaponManager().isWeapon(item)) return true;
        // Ammo is illegal
        if (plugin.getWeaponManager().isAmmo(item)) return true;
        // Generic illegal tag for future items (drugs etc.)
        return item.getItemMeta().getPersistentDataContainer()
                .has(IS_ILLEGAL_KEY, PersistentDataType.BOOLEAN);
    }

    public List<ItemStack> searchAndSeize(Player suspect) {
        List<ItemStack> seized = new ArrayList<>();
        ItemStack[] contents = suspect.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isIllegal(contents[i])) {
                seized.add(contents[i].clone());
                suspect.getInventory().setItem(i, null);
            }
        }
        return seized;
    }

    // ---- Item creation ----

    public ItemStack createHandcuffs() {
        ItemStack item = new ItemStack(Material.IRON_CHAIN);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§f§lHandcuffs");
        meta.setLore(List.of(
                "§7Right-click a player to arrest them.",
                "§7Right-click again to release.",
                "§b§lPolice Equipment"
        ));
        meta.getPersistentDataContainer().set(IS_HANDCUFFS_KEY, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createBadge(String officerName) {
        ItemStack item = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6§lPolice Badge");
        meta.setLore(List.of(
                "§7Officer: §f" + officerName,
                "§7Right-click a cuffed player to search them.",
                "§b§lPolice Equipment"
        ));
        meta.getPersistentDataContainer().set(IS_BADGE_KEY, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createEvidenceBag(String officerName, List<ItemStack> items) {
        ItemStack bag = new ItemStack(Material.BUNDLE);
        org.bukkit.inventory.meta.BundleMeta meta =
                (org.bukkit.inventory.meta.BundleMeta) bag.getItemMeta();
        meta.setDisplayName("§c§lEvidence Bag");

        List<String> lore = new ArrayList<>();
        lore.add("§7Seized by: §f" + officerName);
        lore.add("§7Contents:");
        for (ItemStack seized : items) {
            if (seized != null) {
                lore.add("§f  - " + seized.getAmount() + "x " +
                        formatMaterial(seized.getType().name()));
            }
        }
        lore.add("§c§lDo not tamper with evidence.");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(IS_EVIDENCE_BAG_KEY, PersistentDataType.BOOLEAN, true);

        // Store items in bundle
        for (ItemStack seized : items) {
            if (seized != null) meta.addItem(seized);
        }
        bag.setItemMeta(meta);
        return bag;
    }

    public boolean isHandcuffs(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(IS_HANDCUFFS_KEY, PersistentDataType.BOOLEAN);
    }

    public boolean isBadge(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(IS_BADGE_KEY, PersistentDataType.BOOLEAN);
    }

    // ---- Scheduler: cuffed players follow officer ----

    public void startEscortScheduler() {
        // Every 2 ticks, teleport cuffed players to their officer
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, UUID> entry : cuffedPlayers.entrySet()) {
                Player victim = plugin.getServer().getPlayer(entry.getKey());
                Player officer = plugin.getServer().getPlayer(entry.getValue());

                if (victim == null) continue;

                if (officer == null || !officer.isOnline()) {
                    // Officer offline — start 10 min countdown already scheduled, just slow victim
                    victim.setWalkSpeed(0.1f);
                    continue;
                }

                // Teleport victim to 1.5 blocks behind officer
                org.bukkit.Location officerLoc = officer.getLocation();
                org.bukkit.Location behind = officerLoc.clone().subtract(
                        officerLoc.getDirection().multiply(1.5));
                behind.setY(officerLoc.getY());
                behind.setYaw(officerLoc.getYaw());
                behind.setPitch(officerLoc.getPitch());

                victim.teleport(behind);
                victim.setWalkSpeed(0.0f); // can't walk independently
            }
        }, 2L, 2L);
    }

    // ---- Helpers ----

    private String formatMaterial(String material) {
        String[] words = material.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words)
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
        return sb.toString().trim();
    }

    // ---- Data classes ----

    public static class OfficerInfo {
        public final UUID uuid;
        public final UUID appointedBy;
        public final Timestamp appointedAt;

        public OfficerInfo(UUID uuid, UUID appointedBy, Timestamp appointedAt) {
            this.uuid = uuid;
            this.appointedBy = appointedBy;
            this.appointedAt = appointedAt;
        }
    }
}