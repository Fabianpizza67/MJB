package com.UserMC.MJB;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SupplyOrderManager {

    private final MJB plugin;

    public SupplyOrderManager(MJB plugin) {
        this.plugin = plugin;
    }

    // ---- Supply Item Catalogue ----

    public boolean registerSupplyItem(String material, String licenseRequired, double pricePerItem, int deliverySeconds) {
        String sql = "INSERT INTO supply_items (material, license_required, price_per_item, delivery_seconds) " +
                "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE license_required = ?, price_per_item = ?, delivery_seconds = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, material);
            stmt.setString(2, licenseRequired);
            stmt.setDouble(3, pricePerItem);
            stmt.setInt(4, deliverySeconds);
            stmt.setString(5, licenseRequired);
            stmt.setDouble(6, pricePerItem);
            stmt.setInt(7, deliverySeconds);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error registering supply item: " + e.getMessage());
            return false;
        }
    }

    public boolean removeSupplyItem(String material) {
        String sql = "DELETE FROM supply_items WHERE material = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, material);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error removing supply item: " + e.getMessage());
            return false;
        }
    }

    public List<SupplyItem> getAvailableItems(String license) {
        List<SupplyItem> items = new ArrayList<>();
        String sql = "SELECT * FROM supply_items WHERE license_required = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, license);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                items.add(new SupplyItem(
                        rs.getString("material"),
                        rs.getString("license_required"),
                        rs.getDouble("price_per_item"),
                        rs.getInt("delivery_seconds")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching supply items: " + e.getMessage());
        }
        return items;
    }

    public SupplyItem getSupplyItem(String material) {
        String sql = "SELECT * FROM supply_items WHERE material = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, material);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new SupplyItem(
                        rs.getString("material"),
                        rs.getString("license_required"),
                        rs.getDouble("price_per_item"),
                        rs.getInt("delivery_seconds")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching supply item: " + e.getMessage());
        }
        return null;
    }

    // ---- Orders ----

    public int placeOrder(UUID ownerUuid, int companyId, String district, List<OrderLine> lines) {
        double totalCost = lines.stream().mapToDouble(l -> l.quantity * l.pricePerItem).sum();

        String sql = "INSERT INTO supply_orders (owner_uuid, company_id, district, total_cost) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, ownerUuid.toString());
            stmt.setObject(2, companyId == -1 ? null : companyId);
            stmt.setString(3, district);
            stmt.setDouble(4, totalCost);
            stmt.executeUpdate();

            ResultSet keys = stmt.getGeneratedKeys();
            if (!keys.next()) return -1;
            int orderId = keys.getInt(1);

            String itemSql = "INSERT INTO supply_order_items (order_id, material, quantity, price_per_item) VALUES (?, ?, ?, ?)";
            try (PreparedStatement itemStmt = plugin.getDatabaseManager().getConnection().prepareStatement(itemSql)) {
                for (OrderLine line : lines) {
                    itemStmt.setInt(1, orderId);
                    itemStmt.setString(2, line.material);
                    itemStmt.setInt(3, line.quantity);
                    itemStmt.setDouble(4, line.pricePerItem);
                    itemStmt.addBatch();
                }
                itemStmt.executeBatch();
            }

            int maxDelivery = lines.stream()
                    .mapToInt(l -> l.quantity * l.deliverySeconds)
                    .max()
                    .orElse(1800);
            scheduleDelivery(orderId, maxDelivery);

            return orderId;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error placing order: " + e.getMessage());
            return -1;
        }
    }

    private void markOrderReady(int orderId) {
        String sql = "UPDATE supply_orders SET status = 'ready', ready_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, orderId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error marking order ready: " + e.getMessage());
            return;
        }

        Order order = getOrder(orderId);
        if (order == null) return;
        Player owner = plugin.getServer().getPlayer(order.ownerUuid);
        if (owner != null) {
            owner.sendMessage("§b§l[Supply] §fYour order §b#" + orderId + " §fis ready for pickup!");
            owner.sendMessage("§7Visit the pickup NPC in §b" + order.district + " §7to collect it.");
        }
    }


    private void scheduleDelivery(int orderId, int seconds) {
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> markOrderReady(orderId), (long) seconds * 20L);
    }

    public boolean authorizePlayer(int orderId, UUID uuid) {
        String sql = "INSERT IGNORE INTO supply_order_authorizations (order_id, authorized_uuid) VALUES (?, ?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, orderId);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error authorizing player: " + e.getMessage());
            return false;
        }
    }

    public List<UUID> getAuthorizedPlayers(int orderId) {
        List<UUID> authorized = new ArrayList<>();
        String sql = "SELECT authorized_uuid FROM supply_order_authorizations WHERE order_id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, orderId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) authorized.add(UUID.fromString(rs.getString("authorized_uuid")));
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching authorized players: " + e.getMessage());
        }
        return authorized;
    }

    public boolean removeAuthorization(int orderId, UUID uuid) {
        String sql = "DELETE FROM supply_order_authorizations WHERE order_id = ? AND authorized_uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, orderId);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error removing authorization: " + e.getMessage());
            return false;
        }
    }

    public boolean isAuthorized(int orderId, UUID uuid) {
        Order order = getOrder(orderId);
        if (order == null) return false;
        if (order.ownerUuid.equals(uuid)) return true;

        String sql = "SELECT 1 FROM supply_order_authorizations WHERE order_id = ? AND authorized_uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, orderId);
            stmt.setString(2, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking authorization: " + e.getMessage());
            return false;
        }
    }

    public List<Order> getReadyOrdersForDistrict(String district) {
        List<Order> orders = new ArrayList<>();
        String sql = "SELECT * FROM supply_orders WHERE district = ? AND status = 'ready'";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, district);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) orders.add(orderFromResultSet(rs));
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching ready orders: " + e.getMessage());
        }
        return orders;
    }

    public List<Order> getOrdersForPlayer(UUID uuid) {
        List<Order> orders = new ArrayList<>();
        String sql = "SELECT * FROM supply_orders WHERE owner_uuid = ? AND status != 'collected' ORDER BY ordered_at DESC LIMIT 10";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) orders.add(orderFromResultSet(rs));
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching player orders: " + e.getMessage());
        }
        return orders;
    }

    public Order getOrder(int orderId) {
        String sql = "SELECT * FROM supply_orders WHERE id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, orderId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return orderFromResultSet(rs);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching order: " + e.getMessage());
        }
        return null;
    }

    // Collect an order — gives a brown Package shulker box to the collector
    public boolean collectOrder(int orderId, Player collector) {
        if (!isAuthorized(orderId, collector.getUniqueId())) {
            collector.sendMessage("§4You are not authorized to collect this order.");
            return false;
        }

        Order order = getOrder(orderId);
        if (order == null) {
            collector.sendMessage("§4Order not found.");
            return false;
        }
        if (!order.status.equals("ready")) {
            collector.sendMessage("§4This order is not ready yet.");
            return false;
        }

        List<OrderLine> lines = getOrderLines(orderId);
        if (lines.isEmpty()) {
            collector.sendMessage("§4Order has no items.");
            return false;
        }

        // Build the full flat list of stacks first
        String ownerName = plugin.getServer().getOfflinePlayer(order.ownerUuid).getName();
        if (ownerName == null) ownerName = order.ownerUuid.toString();

        org.bukkit.NamespacedKey stockKey = new org.bukkit.NamespacedKey(plugin, "stock_item");
        org.bukkit.NamespacedKey orderIdKey = new org.bukkit.NamespacedKey(plugin, "order_id");

        List<ItemStack> allStacks = new ArrayList<>();
        for (OrderLine line : lines) {
            Material mat = Material.valueOf(line.material);
            int remaining = line.quantity;
            while (remaining > 0) {
                int stackSize = Math.min(remaining, mat.getMaxStackSize());
                ItemStack stack = new ItemStack(mat, stackSize);
                ItemMeta meta = stack.getItemMeta();
                meta.getPersistentDataContainer().set(
                        stockKey, org.bukkit.persistence.PersistentDataType.BOOLEAN, true
                );
                meta.setLore(List.of(
                        "§7Ordered by: §b" + ownerName,
                        "§7Order §b#" + orderId,
                        "§7District: §f" + order.district
                ));
                stack.setItemMeta(meta);
                allStacks.add(stack);
                remaining -= stackSize;
            }
        }

        // Split into batches of 27 (one shulker box each)
        int SHULKER_SIZE = 27;
        List<List<ItemStack>> batches = new ArrayList<>();
        for (int i = 0; i < allStacks.size(); i += SHULKER_SIZE) {
            batches.add(allStacks.subList(i, Math.min(i + SHULKER_SIZE, allStacks.size())));
        }

        // Check player has enough free slots for all the shulker boxes
        int freeSlots = 0;
        for (ItemStack item : collector.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) freeSlots++;
        }
        if (freeSlots < batches.size()) {
            collector.sendMessage("§4Not enough inventory space! Need §f" + batches.size() +
                    " §4free slots for §f" + batches.size() + " §4package(s).");
            return false;
        }

        // Create one shulker box per batch
        int boxNumber = 1;
        for (List<ItemStack> batch : batches) {
            ItemStack shulker = new ItemStack(Material.BROWN_SHULKER_BOX);
            org.bukkit.inventory.meta.BlockStateMeta bsm =
                    (org.bukkit.inventory.meta.BlockStateMeta) shulker.getItemMeta();

            String boxLabel = batches.size() > 1
                    ? "§6§lPackage §b#" + orderId + " §7(" + boxNumber + "/" + batches.size() + ")"
                    : "§6§lPackage §b#" + orderId;

            bsm.setDisplayName(boxLabel);
            bsm.setLore(List.of(
                    "§7Ordered by: §b" + ownerName,
                    "§7District: §f" + order.district,
                    "§7Order §b#" + orderId
            ));
            bsm.getPersistentDataContainer().set(
                    orderIdKey, org.bukkit.persistence.PersistentDataType.INTEGER, orderId
            );

            org.bukkit.block.ShulkerBox shulkerBox = (org.bukkit.block.ShulkerBox) bsm.getBlockState();
            org.bukkit.inventory.Inventory shulkerInv = shulkerBox.getInventory();
            for (ItemStack stack : batch) {
                shulkerInv.addItem(stack);
            }

            bsm.setBlockState(shulkerBox);
            shulker.setItemMeta(bsm);
            collector.getInventory().addItem(shulker);
            boxNumber++;
        }

        // Mark as collected
        String sql = "UPDATE supply_orders SET status = 'collected' WHERE id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, orderId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error marking order collected: " + e.getMessage());
            return false;
        }
    }

    public List<OrderLine> getOrderLines(int orderId) {
        List<OrderLine> lines = new ArrayList<>();
        String sql = "SELECT * FROM supply_order_items WHERE order_id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, orderId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                lines.add(new OrderLine(
                        rs.getString("material"),
                        rs.getInt("quantity"),
                        rs.getDouble("price_per_item"),
                        0
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching order lines: " + e.getMessage());
        }
        return lines;
    }

    // ---- District Detection ----

    public String getNearestDistrict(org.bukkit.Location playerLocation) {
        String sql = "SELECT npc_id, district FROM pickup_npcs";
        String nearestDistrict = null;
        double nearestDistance = Double.MAX_VALUE;

        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int npcId = rs.getInt("npc_id");
                String district = rs.getString("district");

                net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(npcId);
                if (npc == null || !npc.isSpawned()) continue;

                org.bukkit.entity.Entity entity = npc.getEntity();
                if (entity == null) continue;
                if (!entity.getWorld().equals(playerLocation.getWorld())) continue;

                double distance = entity.getLocation().distance(playerLocation);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestDistrict = district;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error finding nearest district: " + e.getMessage());
        }

        return nearestDistrict != null ? nearestDistrict : "central";
    }

    // ---- Pickup NPC Registration ----

    public boolean registerPickupNPC(int npcId, String district) {
        String sql = "INSERT INTO pickup_npcs (npc_id, district) VALUES (?, ?) ON DUPLICATE KEY UPDATE district = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, npcId);
            stmt.setString(2, district);
            stmt.setString(3, district);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error registering pickup NPC: " + e.getMessage());
            return false;
        }
    }

    public String getDistrictForNPC(int npcId) {
        String sql = "SELECT district FROM pickup_npcs WHERE npc_id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, npcId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("district");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching pickup NPC district: " + e.getMessage());
        }
        return null;
    }

    // ---- Computer Registration ----

    public boolean registerComputer(org.bukkit.Location loc, UUID ownerUuid) {
        String sql = "INSERT INTO computers (world, x, y, z, owner_uuid) VALUES (?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE owner_uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, loc.getWorld().getName());
            stmt.setInt(2, loc.getBlockX());
            stmt.setInt(3, loc.getBlockY());
            stmt.setInt(4, loc.getBlockZ());
            stmt.setString(5, ownerUuid.toString());
            stmt.setString(6, ownerUuid.toString());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error registering computer: " + e.getMessage());
            return false;
        }
    }

    public boolean unregisterComputer(org.bukkit.Location loc) {
        String sql = "DELETE FROM computers WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, loc.getWorld().getName());
            stmt.setInt(2, loc.getBlockX());
            stmt.setInt(3, loc.getBlockY());
            stmt.setInt(4, loc.getBlockZ());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error unregistering computer: " + e.getMessage());
            return false;
        }
    }

    public boolean isComputer(org.bukkit.Location loc) {
        String sql = "SELECT 1 FROM computers WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, loc.getWorld().getName());
            stmt.setInt(2, loc.getBlockX());
            stmt.setInt(3, loc.getBlockY());
            stmt.setInt(4, loc.getBlockZ());
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking computer: " + e.getMessage());
            return false;
        }
    }

    public UUID getComputerOwner(org.bukkit.Location loc) {
        String sql = "SELECT owner_uuid FROM computers WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, loc.getWorld().getName());
            stmt.setInt(2, loc.getBlockX());
            stmt.setInt(3, loc.getBlockY());
            stmt.setInt(4, loc.getBlockZ());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return UUID.fromString(rs.getString("owner_uuid"));
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting computer owner: " + e.getMessage());
        }
        return null;
    }

    private Order orderFromResultSet(ResultSet rs) throws SQLException {
        return new Order(
                rs.getInt("id"),
                UUID.fromString(rs.getString("owner_uuid")),
                rs.getString("district"),
                rs.getString("status"),
                rs.getDouble("total_cost"),
                rs.getTimestamp("ordered_at"),
                rs.getTimestamp("ready_at")
        );
    }

    // ---- Data Classes ----

    public static class SupplyItem {
        public final String material;
        public final String licenseRequired;
        public final double pricePerItem;
        public final int deliverySeconds;

        public SupplyItem(String material, String licenseRequired, double pricePerItem, int deliverySeconds) {
            this.material = material;
            this.licenseRequired = licenseRequired;
            this.pricePerItem = pricePerItem;
            this.deliverySeconds = deliverySeconds;
        }
    }

    public static class OrderLine {
        public final String material;
        public final int quantity;
        public final double pricePerItem;
        public final int deliverySeconds;

        public OrderLine(String material, int quantity, double pricePerItem, int deliverySeconds) {
            this.material = material;
            this.quantity = quantity;
            this.pricePerItem = pricePerItem;
            this.deliverySeconds = deliverySeconds;
        }
    }

    public static class Order {
        public final int id;
        public final UUID ownerUuid;
        public final String district;
        public final String status;
        public final double totalCost;
        public final Timestamp orderedAt;
        public final Timestamp readyAt;

        public Order(int id, UUID ownerUuid, String district, String status, double totalCost, Timestamp orderedAt, Timestamp readyAt) {
            this.id = id;
            this.ownerUuid = ownerUuid;
            this.district = district;
            this.status = status;
            this.totalCost = totalCost;
            this.orderedAt = orderedAt;
            this.readyAt = readyAt;
        }
    }


    public void recoverPendingOrders() {
        String sql = "SELECT id, ordered_at FROM supply_orders WHERE status = 'pending'";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            int count = 0;
            while (rs.next()) {
                int orderId        = rs.getInt("id");
                Timestamp orderedAt = rs.getTimestamp("ordered_at");

                // We don't store the original delivery duration, so we need to recalculate
                // from the order items. Use the same logic as placeOrder().
                List<OrderLine> lines = getOrderLines(orderId);
                if (lines.isEmpty()) continue;

                int maxDelivery = lines.stream()
                        .mapToInt(l -> l.quantity * l.deliverySeconds)
                        .max()
                        .orElse(1800);

                long orderedMs   = orderedAt.getTime();
                long elapsedMs   = System.currentTimeMillis() - orderedMs;
                long remainingMs = ((long) maxDelivery * 1000) - elapsedMs;

                if (remainingMs <= 0) {
                    // Overdue — deliver on next tick
                    plugin.getServer().getScheduler().runTaskLater(plugin,
                            () -> markOrderReady(orderId), 1L);
                } else {
                    long remainingTicks = remainingMs / 50L;
                    plugin.getServer().getScheduler().runTaskLater(plugin,
                            () -> markOrderReady(orderId), remainingTicks);
                }
                count++;
            }
            if (count > 0) {
                plugin.getLogger().info("[Supply] Recovered " + count + " pending order(s).");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error recovering supply orders: " + e.getMessage());
        }
    }

}