package com.UserMC.MJB;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.sql.*;
import java.util.*;

public class PoliceBudgetManager {

    private final MJB plugin;

    public final NamespacedKey IS_BATON_KEY;
    public final NamespacedKey IS_UNIFORM_KEY;

    // Police equipment prices (cheaper than black market)
    public static final Map<String, Double> EQUIPMENT_PRICES = new LinkedHashMap<>();
    public static final Map<String, String> EQUIPMENT_NAMES = new LinkedHashMap<>();

    static {
        EQUIPMENT_PRICES.put("pistol",       3000.0);
        EQUIPMENT_PRICES.put("rifle",        6000.0);
        EQUIPMENT_PRICES.put("shotgun",      4000.0);
        EQUIPMENT_PRICES.put("handcuffs",     500.0);
        EQUIPMENT_PRICES.put("badge",         500.0);
        EQUIPMENT_PRICES.put("baton",         800.0);
        EQUIPMENT_PRICES.put("ammo_pistol",    75.0);
        EQUIPMENT_PRICES.put("ammo_rifle",    150.0);
        EQUIPMENT_PRICES.put("ammo_shotgun",  100.0);
        EQUIPMENT_PRICES.put("uniform",       200.0);

        EQUIPMENT_NAMES.put("pistol",       "Pistol");
        EQUIPMENT_NAMES.put("rifle",        "Rifle");
        EQUIPMENT_NAMES.put("shotgun",      "Shotgun");
        EQUIPMENT_NAMES.put("handcuffs",    "Handcuffs");
        EQUIPMENT_NAMES.put("badge",        "Police Badge");
        EQUIPMENT_NAMES.put("baton",        "Baton");
        EQUIPMENT_NAMES.put("ammo_pistol",  "Pistol Ammo");
        EQUIPMENT_NAMES.put("ammo_rifle",   "Rifle Ammo");
        EQUIPMENT_NAMES.put("ammo_shotgun", "Shotgun Ammo");
        EQUIPMENT_NAMES.put("uniform",      "Police Uniform");
    }

    public PoliceBudgetManager(MJB plugin) {
        this.plugin = plugin;
        IS_BATON_KEY   = new NamespacedKey(plugin, "is_baton");
        IS_UNIFORM_KEY = new NamespacedKey(plugin, "is_uniform");
    }

    // ---- Budget bank ----

    public double getBudget() {
        String sql = "SELECT balance FROM police_budget WHERE id = 1";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getDouble("balance");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting police budget: " + e.getMessage());
        }
        return 0;
    }

    public void addToBudget(double amount) {
        String sql = "UPDATE police_budget SET balance = balance + ? WHERE id = 1";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setDouble(1, amount);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error adding to police budget: " + e.getMessage());
        }
    }

    public boolean deductFromBudget(double amount) {
        if (getBudget() < amount) return false;
        String sql = "UPDATE police_budget SET balance = balance - ? WHERE id = 1";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setDouble(1, amount);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error deducting from police budget: " + e.getMessage());
            return false;
        }
    }

    // Sergeant deposits from personal bank into police budget
    public boolean depositFromPlayer(UUID uuid, double amount) {
        double balance = plugin.getEconomyManager().getBankBalance(uuid);
        if (balance < amount) return false;
        String sql = "UPDATE players SET bank_balance = bank_balance - ? WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setDouble(1, amount);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error deducting from player for budget deposit: " + e.getMessage());
            return false;
        }
        addToBudget(amount);
        return true;
    }

    // Sergeant withdraws from police budget to personal bank
    public boolean withdrawToPlayer(UUID uuid, double amount) {
        if (!deductFromBudget(amount)) return false;
        String sql = "UPDATE players SET bank_balance = bank_balance + ? WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setDouble(1, amount);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error crediting player from budget withdrawal: " + e.getMessage());
            return false;
        }
    }

    // ---- Officer salaries ----

    public boolean setSalary(UUID officerUuid, double salary) {
        String sql = "UPDATE police_officers SET salary = ? WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setDouble(1, salary);
            stmt.setString(2, officerUuid.toString());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error setting police salary: " + e.getMessage());
            return false;
        }
    }

    public double getSalary(UUID officerUuid) {
        String sql = "SELECT salary FROM police_officers WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, officerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getDouble("salary");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting police salary: " + e.getMessage());
        }
        return 0;
    }

    public void processDailySalaries() {
        String sql = "SELECT uuid, salary FROM police_officers WHERE salary > 0";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                double salary = rs.getDouble("salary");

                if (!deductFromBudget(salary)) {
                    // Budget too low — notify sergeant
                    notifySergeants("§4§l[Police Budget] §4Insufficient funds to pay salaries! " +
                            "Budget needs topping up.");
                    return;
                }

                // Pay officer
                String addSql = "UPDATE players SET bank_balance = bank_balance + ? WHERE uuid = ?";
                try (PreparedStatement addStmt = plugin.getDatabaseManager().getConnection()
                        .prepareStatement(addSql)) {
                    addStmt.setDouble(1, salary);
                    addStmt.setString(2, uuid.toString());
                    addStmt.executeUpdate();
                }

                Player officer = plugin.getServer().getPlayer(uuid);
                if (officer != null) {
                    officer.sendMessage("§b§l[Police] §fYou received your daily salary of §b" +
                            plugin.getEconomyManager().format(salary) + "§f.");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error processing police salaries: " + e.getMessage());
        }
    }

    public void startSchedulers() {
        // Daily salary payout
        long oneDayTicks = 20L * 60 * 60 * 24;
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () ->
                        plugin.getServer().getScheduler().runTask(plugin, this::processDailySalaries),
                oneDayTicks, oneDayTicks);
        // Weekly 2K server top-up
// Weekly server top-up — goes to city treasury, council decides how to spend it
        long oneWeekTicks = oneDayTicks * 7;
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () ->
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            String sql = "UPDATE city_treasury SET balance = balance + 25000 WHERE id = 1";
                            try (java.sql.PreparedStatement stmt = plugin.getDatabaseManager()
                                    .getConnection().prepareStatement(sql)) {
                                stmt.executeUpdate();
                            } catch (java.sql.SQLException e) {
                                plugin.getLogger().severe("Error adding weekly treasury top-up: " + e.getMessage());
                            }
                        }),
                oneWeekTicks, oneWeekTicks);
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () ->
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            try {
                                double contribution = Double.parseDouble(
                                        plugin.getGovernmentManager().getGovernmentSetting(
                                                "police_weekly_contribution", "0"));
                                if (contribution > 0) {
                                    String deductSql = "UPDATE city_treasury SET balance = balance - ? " +
                                            "WHERE id = 1 AND balance >= ?";
                                    try (java.sql.PreparedStatement stmt = plugin.getDatabaseManager()
                                            .getConnection().prepareStatement(deductSql)) {
                                        stmt.setDouble(1, contribution);
                                        stmt.setDouble(2, contribution);
                                        if (stmt.executeUpdate() > 0) {
                                            addToBudget(contribution);
                                            notifySergeants("§b§l[Police Budget] §fThe city contributed §b" +
                                                    plugin.getEconomyManager().format(contribution) +
                                                    " §ffrom the treasury to the police budget this week.");
                                        } else {
                                            notifySergeants("§4§l[Police Budget] §4Weekly city contribution failed " +
                                                    "— city treasury has insufficient funds!");
                                        }
                                    }
                                }
                            } catch (Exception ignored) { }
                        }),
                oneWeekTicks, oneWeekTicks);
    }

    private void notifySergeants(String message) {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (plugin.getPoliceManager().isOfficer(p.getUniqueId())) {
                CrimeManager.PoliceRank rank = plugin.getCrimeManager().getRank(p.getUniqueId());
                if (rank == CrimeManager.PoliceRank.SERGEANT) {
                    p.sendMessage(message);
                }
            }
        }
    }

    // ---- Requisitions ----

    public int submitRequisition(UUID officerUuid, String itemType, int quantity) {
        String sql = "INSERT INTO police_requisitions (officer_uuid, item_type, quantity) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, officerUuid.toString());
            stmt.setString(2, itemType);
            stmt.setInt(3, quantity);
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error submitting requisition: " + e.getMessage());
        }
        return -1;
    }

    public List<Requisition> getPendingRequisitions() {
        List<Requisition> list = new ArrayList<>();
        String sql = "SELECT * FROM police_requisitions WHERE status = 'pending' ORDER BY requested_at";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) list.add(requisitionFromRs(rs));
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching requisitions: " + e.getMessage());
        }
        return list;
    }

    public boolean orderRequisition(int id) {
        double price = 0;
        String itemType = null;
        int quantity = 1;

        String fetchSql = "SELECT item_type, quantity FROM police_requisitions WHERE id = ? AND status = 'pending'";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(fetchSql)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) return false;
            itemType = rs.getString("item_type");
            quantity = rs.getInt("quantity");
            price = EQUIPMENT_PRICES.getOrDefault(itemType, 0.0) * quantity;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching requisition for order: " + e.getMessage());
            return false;
        }

        if (!deductFromBudget(price)) return false; // insufficient budget

        String updateSql = "UPDATE police_requisitions SET status = 'ordered', ordered_at = NOW() WHERE id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(updateSql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error ordering requisition: " + e.getMessage());
            return false;
        }

        // Schedule delivery after 10 minutes
        final String finalItemType = itemType;
        final int finalQuantity = quantity;
        final int finalId = id;
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                        deliverRequisition(finalId, finalItemType, finalQuantity),
                20L * 60 * 10);

        return true;
    }

    void deliverRequisition(int id, String itemType, int quantity) {
        // Get officer uuid
        String sql = "SELECT officer_uuid FROM police_requisitions WHERE id = ?";
        UUID officerUuid = null;
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) officerUuid = UUID.fromString(rs.getString("officer_uuid"));
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching officer for delivery: " + e.getMessage());
            return;
        }

        if (officerUuid == null) return;

        // Mark delivered
        String updateSql = "UPDATE police_requisitions SET status = 'delivered' WHERE id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(updateSql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error marking requisition delivered: " + e.getMessage());
        }

        Player officer = plugin.getServer().getPlayer(officerUuid);
        if (officer == null) {
            // Officer offline — hold items for next login via pending_deliveries table
            storePendingDelivery(officerUuid, itemType, quantity);
            return;
        }

        giveEquipment(officer, itemType, quantity);
        officer.sendMessage("§b§l[Police] §fYour requisition has arrived: §f" + quantity + "x " +
                EQUIPMENT_NAMES.getOrDefault(itemType, itemType));
    }

    public void giveEquipment(Player player, String itemType, int quantity) {
        for (int i = 0; i < quantity; i++) {
            ItemStack item = createEquipmentItem(itemType, player.getName());
            if (item != null) player.getInventory().addItem(item);
        }
    }

    private ItemStack createEquipmentItem(String itemType, String officerName) {
        return switch (itemType) {
            case "pistol"       -> plugin.getWeaponManager().createWeapon(WeaponManager.WeaponType.PISTOL);
            case "rifle"        -> plugin.getWeaponManager().createWeapon(WeaponManager.WeaponType.RIFLE);
            case "shotgun"      -> plugin.getWeaponManager().createWeapon(WeaponManager.WeaponType.SHOTGUN);
            case "handcuffs"    -> plugin.getPoliceManager().createHandcuffs();
            case "badge"        -> plugin.getPoliceManager().createBadge(officerName);
            case "baton"        -> createBaton();
            case "ammo_pistol"  -> plugin.getWeaponManager().createAmmo(WeaponManager.WeaponType.PISTOL);
            case "ammo_rifle"   -> plugin.getWeaponManager().createAmmo(WeaponManager.WeaponType.RIFLE);
            case "ammo_shotgun" -> plugin.getWeaponManager().createAmmo(WeaponManager.WeaponType.SHOTGUN);
            case "uniform"      -> createUniform();
            default -> null;
        };
    }

    public ItemStack createBaton() {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§f§lPolice Baton");
        meta.setLore(List.of(
                "§7Standard issue baton.",
                "§7Deals moderate melee damage.",
                "§b§lPolice Equipment"
        ));
        meta.getPersistentDataContainer().set(IS_BATON_KEY, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createUniform() {
        // Police uniform = blue leather chestplate dyed
        ItemStack item = new ItemStack(Material.LEATHER_CHESTPLATE);
        org.bukkit.inventory.meta.LeatherArmorMeta meta =
                (org.bukkit.inventory.meta.LeatherArmorMeta) item.getItemMeta();
        meta.setDisplayName("§9§lPolice Uniform");
        meta.setColor(org.bukkit.Color.fromRGB(0, 50, 160)); // dark blue
        meta.setLore(List.of(
                "§7Standard issue police uniform.",
                "§b§lPolice Equipment"
        ));
        meta.getPersistentDataContainer().set(IS_UNIFORM_KEY, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isBaton(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(IS_BATON_KEY, PersistentDataType.BOOLEAN);
    }

    // ---- Pending deliveries for offline officers ----

    private void storePendingDelivery(UUID uuid, String itemType, int quantity) {
        String sql = "INSERT INTO police_pending_deliveries (officer_uuid, item_type, quantity) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, itemType);
            stmt.setInt(3, quantity);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error storing pending delivery: " + e.getMessage());
        }
    }

    public void deliverPendingItems(Player player) {
        String sql = "SELECT id, item_type, quantity FROM police_pending_deliveries WHERE officer_uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, player.getUniqueId().toString());
            ResultSet rs = stmt.executeQuery();
            boolean hadItems = false;
            while (rs.next()) {
                int id = rs.getInt("id");
                String itemType = rs.getString("item_type");
                int quantity = rs.getInt("quantity");
                giveEquipment(player, itemType, quantity);
                hadItems = true;

                // Delete
                String del = "DELETE FROM police_pending_deliveries WHERE id = ?";
                try (PreparedStatement delStmt = plugin.getDatabaseManager().getConnection()
                        .prepareStatement(del)) {
                    delStmt.setInt(1, id);
                    delStmt.executeUpdate();
                }
            }
            if (hadItems) {
                player.sendMessage("§b§l[Police] §fYou have received equipment that was delivered while offline.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error delivering pending items: " + e.getMessage());
        }
    }

    private Requisition requisitionFromRs(ResultSet rs) throws SQLException {
        return new Requisition(
                rs.getInt("id"),
                UUID.fromString(rs.getString("officer_uuid")),
                rs.getString("item_type"),
                rs.getInt("quantity"),
                rs.getString("status"),
                rs.getTimestamp("requested_at")
        );
    }

    // ---- Data class ----

    public static class Requisition {
        public final int id;
        public final UUID officerUuid;
        public final String itemType;
        public final int quantity;
        public final String status;
        public final Timestamp requestedAt;

        public Requisition(int id, UUID officerUuid, String itemType, int quantity,
                           String status, Timestamp requestedAt) {
            this.id = id;
            this.officerUuid = officerUuid;
            this.itemType = itemType;
            this.quantity = quantity;
            this.status = status;
            this.requestedAt = requestedAt;
        }
    }

    public void recoverPendingRequisitions() {
        String sql = "SELECT id, item_type, quantity, officer_uuid, ordered_at " +
                "FROM police_requisitions WHERE status = 'ordered'";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            int count = 0;
            while (rs.next()) {
                int id          = rs.getInt("id");
                String itemType = rs.getString("item_type");
                int quantity    = rs.getInt("quantity");
                Timestamp orderedAt = rs.getTimestamp("ordered_at");

                // Work out how many ticks remain on the 10-minute delivery window
                long orderedMs   = orderedAt.getTime();
                long elapsedMs   = System.currentTimeMillis() - orderedMs;
                long remainingMs = (10L * 60 * 1000) - elapsedMs;

                if (remainingMs <= 0) {
                    // Already overdue — deliver immediately on next tick
                    plugin.getServer().getScheduler().runTaskLater(plugin,
                            () -> deliverRequisition(id, itemType, quantity), 1L);
                } else {
                    // Reschedule for remaining time
                    long remainingTicks = remainingMs / 50L; // 50ms per tick
                    plugin.getServer().getScheduler().runTaskLater(plugin,
                            () -> deliverRequisition(id, itemType, quantity), remainingTicks);
                }
                count++;
            }
            if (count > 0) {
                plugin.getLogger().info("[Police] Recovered " + count + " pending requisition(s).");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error recovering requisitions: " + e.getMessage());
        }
    }
    public void setPoliceContribution(double amount) {
        plugin.getGovernmentManager().setGovernmentSetting("police_contribution", String.valueOf(amount));
    }
}