package com.UserMC.MJB.listeners;

import com.UserMC.MJB.MJB;
import com.UserMC.MJB.SupplyOrderManager.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class ComputerListener implements Listener {

    private final MJB plugin;
    private final Map<UUID, String> playerDistrict = new HashMap<>();
    private final Map<UUID, List<OrderLine>> playerCart = new HashMap<>();

    private static final String ORDER_GUI_TITLE = "§b§lSupply Order";
    private static final String CART_GUI_TITLE = "§b§lYour Cart";
    private static final String ORDERS_GUI_TITLE = "§b§lYour Orders";

    public ComputerListener(MJB plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.GREEN_GLAZED_TERRACOTTA) return;

        org.bukkit.Location loc = event.getClickedBlock().getLocation();
        if (!plugin.getSupplyOrderManager().isComputer(loc)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        // Get district from the region they're in
        String district = plugin.getSupplyOrderManager().getNearestDistrict(player.getLocation());
        playerDistrict.put(player.getUniqueId(), district);

        openMainMenu(player);
    }

    private void openMainMenu(Player player) {
        Inventory gui = plugin.getServer().createInventory(null, 9, "§b§lComputer");

        // Supply Order app
        ItemStack orderApp = createGuiItem(Material.CHEST, "§f§lSupply Orders", "§7Order stock for your business.");
        // My Orders app
        ItemStack myOrdersApp = createGuiItem(Material.PAPER, "§f§lMy Orders", "§7View your pending orders.");
        // Close
        ItemStack close = createGuiItem(Material.BARRIER, "§4Close", "§7Close the computer.");

        gui.setItem(2, orderApp);
        gui.setItem(4, myOrdersApp);
        gui.setItem(6, close);

        player.openInventory(gui);
    }

    private void openOrderMenu(Player player) {
        // Get player's license — for now default to baker as example
        // This will hook into the license system later
        String license = "baker"; // TODO: hook into license system

        List<SupplyItem> items = plugin.getSupplyOrderManager().getAvailableItems(license);

        Inventory gui = plugin.getServer().createInventory(null, 54, ORDER_GUI_TITLE);

        int slot = 0;
        for (SupplyItem item : items) {
            if (slot >= 45) break;
            Material mat = Material.valueOf(item.material);
            ItemStack guiItem = createGuiItem(mat,
                    "§f" + formatMaterial(item.material),
                    "§7Price: §f" + plugin.getEconomyManager().format(item.pricePerItem) + " §7each",
                    "§7Delivery: §f" + formatTime(item.deliverySeconds),
                    "§7License: §b" + item.licenseRequired,
                    "",
                    "§eLeft-click §7to add 1",
                    "§eRight-click §7to add 16",
                    "§eShift-click §7to add 64"
            );
            gui.setItem(slot++, guiItem);
        }

        // Bottom row controls
        ItemStack viewCart = createGuiItem(Material.GOLD_INGOT, "§f§lView Cart",
                "§7See your current cart.");
        ItemStack back = createGuiItem(Material.ARROW, "§fBack", "§7Return to main menu.");

        gui.setItem(49, viewCart);
        gui.setItem(45, back);

        player.openInventory(gui);
    }

    private void openCartMenu(Player player) {
        List<OrderLine> cart = playerCart.getOrDefault(player.getUniqueId(), new ArrayList<>());
        Inventory gui = plugin.getServer().createInventory(null, 54, CART_GUI_TITLE);

        double total = 0;
        int slot = 0;
        for (OrderLine line : cart) {
            if (slot >= 45) break;
            Material mat = Material.valueOf(line.material);
            double lineTotal = line.quantity * line.pricePerItem;
            total += lineTotal;
            ItemStack guiItem = createGuiItem(mat,
                    "§f" + formatMaterial(line.material),
                    "§7Quantity: §f" + line.quantity,
                    "§7Price each: §f" + plugin.getEconomyManager().format(line.pricePerItem),
                    "§7Line total: §f" + plugin.getEconomyManager().format(lineTotal),
                    "",
                    "§cLeft-click §7to remove"
            );
            gui.setItem(slot++, guiItem);
        }

        double finalTotal = total;
        ItemStack confirm = createGuiItem(Material.EMERALD,
                "§a§lConfirm Order",
                "§7Total: §f" + plugin.getEconomyManager().format(finalTotal),
                "§7Funds will be deducted from your bank.",
                "",
                "§eClick to place order!"
        );
        ItemStack back = createGuiItem(Material.ARROW, "§fBack", "§7Return to order menu.");
        ItemStack clear = createGuiItem(Material.BARRIER, "§4Clear Cart", "§7Remove all items from cart.");

        gui.setItem(49, confirm);
        gui.setItem(45, back);
        gui.setItem(53, clear);

        player.openInventory(gui);
    }

    private void openMyOrdersMenu(Player player) {
        List<Order> orders = plugin.getSupplyOrderManager().getOrdersForPlayer(player.getUniqueId());
        Inventory gui = plugin.getServer().createInventory(null, 54, ORDERS_GUI_TITLE);

        int slot = 0;
        for (Order order : orders) {
            if (slot >= 45) break;
            Material statusMat = order.status.equals("ready") ? Material.EMERALD :
                    order.status.equals("pending") ? Material.CLOCK : Material.GRAY_DYE;
            String statusColor = order.status.equals("ready") ? "§a" :
                    order.status.equals("pending") ? "§e" : "§7";

            ItemStack guiItem = createGuiItem(statusMat,
                    "§fOrder §b#" + order.id,
                    "§7Status: " + statusColor + order.status.toUpperCase(),
                    "§7District: §f" + order.district,
                    "§7Total: §f" + plugin.getEconomyManager().format(order.totalCost),
                    "§7Ordered: §f" + order.orderedAt.toString()
            );
            gui.setItem(slot++, guiItem);
        }

        ItemStack back = createGuiItem(Material.ARROW, "§fBack", "§7Return to main menu.");
        gui.setItem(45, back);

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        if (!title.equals("§b§lComputer") && !title.equals(ORDER_GUI_TITLE) &&
                !title.equals(CART_GUI_TITLE) && !title.equals(ORDERS_GUI_TITLE)) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Main menu
        if (title.equals("§b§lComputer")) {
            if (clicked.getType() == Material.CHEST) openOrderMenu(player);
            else if (clicked.getType() == Material.PAPER) openMyOrdersMenu(player);
            else if (clicked.getType() == Material.BARRIER) player.closeInventory();
            return;
        }

        // Order menu
        if (title.equals(ORDER_GUI_TITLE)) {
            if (clicked.getType() == Material.ARROW) {
                openMainMenu(player);
                return;
            }
            if (clicked.getType() == Material.GOLD_INGOT) {
                openCartMenu(player);
                return;
            }

            // Add item to cart
            String materialName = clicked.getType().name();
            SupplyItem supplyItem = plugin.getSupplyOrderManager().getSupplyItem(materialName);
            if (supplyItem == null) return;

            int amount = 1;
            if (event.isRightClick()) amount = 16;
            if (event.isShiftClick()) amount = 64;

            List<OrderLine> cart = playerCart.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());

            // Check if already in cart
            boolean found = false;
            for (OrderLine line : cart) {
                if (line.material.equals(materialName)) {
                    cart.set(cart.indexOf(line), new OrderLine(
                            line.material, line.quantity + amount,
                            line.pricePerItem, line.deliverySeconds
                    ));
                    found = true;
                    break;
                }
            }
            if (!found) {
                cart.add(new OrderLine(materialName, amount, supplyItem.pricePerItem, supplyItem.deliverySeconds));
            }

            player.sendMessage("§7Added §f" + amount + "x " + formatMaterial(materialName) + " §7to cart.");
            return;
        }

        // Cart menu
        if (title.equals(CART_GUI_TITLE)) {
            if (clicked.getType() == Material.ARROW) {
                openOrderMenu(player);
                return;
            }
            if (clicked.getType() == Material.BARRIER) {
                playerCart.remove(player.getUniqueId());
                player.sendMessage("§7Cart cleared.");
                openOrderMenu(player);
                return;
            }
            if (clicked.getType() == Material.EMERALD) {
                // Confirm order
                List<OrderLine> cart = playerCart.getOrDefault(player.getUniqueId(), new ArrayList<>());
                if (cart.isEmpty()) {
                    player.sendMessage("§4Your cart is empty!");
                    return;
                }

                double total = cart.stream().mapToDouble(l -> l.quantity * l.pricePerItem).sum();
                double balance = plugin.getEconomyManager().getBankBalance(player.getUniqueId());

                if (balance < total) {
                    player.sendMessage("§4Insufficient bank balance!");
                    player.sendMessage("§7Total: §f" + plugin.getEconomyManager().format(total));
                    player.sendMessage("§7Your balance: §f" + plugin.getEconomyManager().format(balance));
                    return;
                }

                // Deduct cost
                String sql = "UPDATE players SET bank_balance = bank_balance - ? WHERE uuid = ?";
                try (java.sql.PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
                    stmt.setDouble(1, total);
                    stmt.setString(2, player.getUniqueId().toString());
                    stmt.executeUpdate();
                } catch (java.sql.SQLException e) {
                    player.sendMessage("§4Payment failed. Please try again.");
                    return;
                }

                String district = playerDistrict.getOrDefault(player.getUniqueId(), "central");
                int orderId = plugin.getSupplyOrderManager().placeOrder(player.getUniqueId(), district, cart);

                if (orderId == -1) {
                    player.sendMessage("§4Failed to place order. Please try again.");
                    return;
                }

                playerCart.remove(player.getUniqueId());
                player.closeInventory();
                player.sendMessage("§b§m-----------------------------");
                player.sendMessage("§b§l  Order Placed!");
                player.sendMessage("§b§m-----------------------------");
                player.sendMessage("§fOrder §b#" + orderId + " §fhas been placed.");
                player.sendMessage("§7Total charged: §f" + plugin.getEconomyManager().format(total));
                player.sendMessage("§7You will be notified when it's ready.");
                player.sendMessage("§b§m-----------------------------");
                return;
            }

            // Remove item from cart
            String materialName = clicked.getType().name();
            List<OrderLine> cart = playerCart.getOrDefault(player.getUniqueId(), new ArrayList<>());
            cart.removeIf(l -> l.material.equals(materialName));
            player.sendMessage("§7Removed §f" + formatMaterial(materialName) + " §7from cart.");
            openCartMenu(player);
            return;
        }

        // My orders menu
        if (title.equals(ORDERS_GUI_TITLE)) {
            if (clicked.getType() == Material.ARROW) openMainMenu(player);
        }
    }

    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    private String formatMaterial(String material) {
        String[] words = material.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    private String formatTime(int seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m";
        return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
    }
}