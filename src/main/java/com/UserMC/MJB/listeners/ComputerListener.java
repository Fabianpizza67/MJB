package com.UserMC.MJB.listeners;

import com.UserMC.MJB.CompanyManager;
import com.UserMC.MJB.MJB;
import com.UserMC.MJB.SupplyOrderManager;
import com.UserMC.MJB.SupplyOrderManager.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import com.UserMC.MJB.listeners.RealEstateNPCListener;

import java.util.*;

public class ComputerListener implements Listener {

    private final MJB plugin;
    private final Map<UUID, String> playerDistrict = new HashMap<>();
    private final Map<UUID, List<OrderLine>> playerCart = new HashMap<>();
    private final Map<UUID, Integer> playerOrderPage = new HashMap<>();

    // Tracks which order a player is currently authorizing
    private final Map<UUID, Integer> awaitingAuthOrderId = new HashMap<>();

    private static final String MAIN_GUI_TITLE = "§b§lComputer";
    private static final String ORDER_GUI_TITLE = "§b§lSupply Order";
    private static final String CART_GUI_TITLE = "§b§lYour Cart";
    private static final String ORDERS_GUI_TITLE = "§b§lMy Orders";
    private static final String AUTH_GUI_TITLE = "§b§lAuthorize Pickup";
    private static final String POLICE_APP_TITLE  = "§9§lPolice Terminal";
    private static final String HOSPITAL_APP_TITLE = "§b§lHospital Terminal";

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

        String district = plugin.getSupplyOrderManager().getNearestDistrict(player.getLocation());
        playerDistrict.put(player.getUniqueId(), district);

        openMainMenu(player);
    }

    // ---- Menus ----

    private void openMainMenu(Player player) {
        Inventory gui = plugin.getServer().createInventory(null, 9, MAIN_GUI_TITLE);
        gui.setItem(0, createGuiItem(Material.GOLD_BLOCK, "§f§lMy Company", "§7Manage your business.", "§7Hire employees, set salaries, manage bank."));
        gui.setItem(1, createGuiItem(Material.CHEST, "§f§lSupply Orders", "§7Order stock for your business."));
        gui.setItem(2, createGuiItem(Material.PAPER, "§f§lMy Orders", "§7View your pending & ready orders.", "§7Authorize others to pick up for you."));
        gui.setItem(3, createGuiItem(Material.OAK_DOOR, "§f§lProperties",
                "§7Browse and buy properties.",
                "§7List your own property for resale."));
        if (plugin.getPoliceManager().isOfficer(player.getUniqueId())) {
            gui.setItem(4, createGuiItem(Material.IRON_SWORD, "§9§lPolice App",
                    "§7View wanted players, your rank and salary.",
                    "§7Request equipment."));
        }
        if (plugin.getHospitalManager().isDoctor(player.getUniqueId())) {
            gui.setItem(4, createGuiItem(Material.RED_WOOL,
                    "§b§lHospital App",
                    "§7View downed players, request supplies,",
                    "§7check hospital budget."));
        }
        gui.setItem(8, createGuiItem(Material.BARRIER, "§4Close", "§7Close the computer."));

        player.openInventory(gui);
    }


    private void openOrderMenu(Player player) {
        openOrderMenu(player, 0);
    }

    private void openOrderMenu(Player player, int page) {
        playerOrderPage.put(player.getUniqueId(), page);

        List<String> activeLicenses = plugin.getLicenseManager()
                .getPlayerLicenses(player.getUniqueId())
                .stream()
                .filter(l -> !l.isRevoked && plugin.getLicenseManager()
                        .hasActiveLicense(player.getUniqueId(), l.licenseType))
                .map(l -> l.licenseType)
                .distinct()
                .toList();

        if (activeLicenses.isEmpty()) {
            player.sendMessage("§4You need a license to place supply orders.");
            player.closeInventory();
            return;
        }

        List<SupplyOrderManager.SupplyItem> allItems = new ArrayList<>();
        for (String license : activeLicenses) {
            allItems.addAll(plugin.getSupplyOrderManager().getAvailableItems(license));
        }

        if (allItems.isEmpty()) {
            player.sendMessage("§7No supply items available for your licenses yet.");
            player.closeInventory();
            return;
        }

        int pageSize     = 45;
        int totalPages   = (int) Math.ceil((double) allItems.size() / pageSize);
        int clampedPage  = Math.max(0, Math.min(page, totalPages - 1));
        int startIdx     = clampedPage * pageSize;
        int endIdx       = Math.min(startIdx + pageSize, allItems.size());

        Inventory gui = plugin.getServer().createInventory(null, 54, ORDER_GUI_TITLE);

        for (int i = startIdx; i < endIdx; i++) {
            SupplyOrderManager.SupplyItem item = allItems.get(i);
            org.bukkit.inventory.ItemStack display = item.toItemStack().clone();
            org.bukkit.inventory.meta.ItemMeta meta = display.getItemMeta();

            // Override display name and lore for shop display
            meta.setDisplayName("§f" + item.displayName);
            List<String> lore = new ArrayList<>();
            lore.add("§7Price: §f" + plugin.getEconomyManager().format(item.pricePerItem) + " §7each");
            lore.add("§7Delivery: §f" + formatTime(item.deliverySeconds));
            lore.add("§7License: §b" + item.licenseRequired);
            lore.add("");
            lore.add("§eLeft-click §7to add 1");
            lore.add("§eRight-click §7to add 16");
            lore.add("§eShift-click §7to add 64");
            meta.setLore(lore);

            // Store supply item ID in NBT so we can identify it on click
            meta.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(plugin, "supply_item_id"),
                    org.bukkit.persistence.PersistentDataType.INTEGER, item.id);
            display.setItemMeta(meta);
            gui.setItem(i - startIdx, display);
        }

        gui.setItem(45, createGuiItem(Material.ARROW, "§fBack", "§7Return to main menu."));

        // Prev page
        if (clampedPage > 0) {
            gui.setItem(46, createGuiItem(Material.SPECTRAL_ARROW,
                    "§fPrevious Page",
                    "§7Page " + clampedPage + " / " + totalPages));
        }

        // Page indicator
        gui.setItem(48, createGuiItem(Material.PAPER,
                "§7Page §f" + (clampedPage + 1) + " §7of §f" + totalPages, ""));

        // Next page
        if (clampedPage < totalPages - 1) {
            gui.setItem(50, createGuiItem(Material.SPECTRAL_ARROW,
                    "§fNext Page",
                    "§7Page " + (clampedPage + 2) + " / " + totalPages));
        }

        gui.setItem(49, createGuiItem(Material.GOLD_INGOT, "§f§lView Cart",
                "§7See your current cart."));

        player.openInventory(gui);
    }

    private void openCartMenu(Player player) {
        List<OrderLine> cart = playerCart.getOrDefault(player.getUniqueId(), new ArrayList<>());
        Inventory gui = plugin.getServer().createInventory(null, 54, CART_GUI_TITLE);

        double total = 0;
        int slot = 0;
        for (OrderLine line : cart) {
            if (slot >= 45) break;
            double lineTotal = line.quantity * line.pricePerItem;
            total += lineTotal;
            gui.setItem(slot++, createGuiItem(Material.valueOf(line.material),
                    "§f" + formatMaterial(line.material),
                    "§7Quantity: §f" + line.quantity,
                    "§7Price each: §f" + plugin.getEconomyManager().format(line.pricePerItem),
                    "§7Line total: §f" + plugin.getEconomyManager().format(lineTotal),
                    "",
                    "§cLeft-click §7to remove"
            ));
        }

        // Show whether this order charges the company or personal bank
        CompanyManager.CompanyInfo company = plugin.getCompanyManager().getCompanyForPlayer(player.getUniqueId());
        String paymentSource = company != null
                ? "§7Charged to: §bCompany bank §7(" + company.name + ")"
                : "§7Charged to: §fPersonal bank";

        double finalTotal = total;
        gui.setItem(45, createGuiItem(Material.ARROW, "§fBack", "§7Return to order menu."));
        gui.setItem(49, createGuiItem(Material.EMERALD, "§a§lConfirm Order",
                "§7Total: §f" + plugin.getEconomyManager().format(finalTotal),
                paymentSource,
                "",
                "§eClick to place order!"));
        gui.setItem(53, createGuiItem(Material.BARRIER, "§4Clear Cart", "§7Remove all items from cart."));

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
                    "§7Ordered: §f" + order.orderedAt.toString(),
                    "",
                    "§eClick §7to manage authorized pickups"
            );

            // Store order ID in NBT
            ItemMeta meta = guiItem.getItemMeta();
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "order_id"),
                    PersistentDataType.INTEGER,
                    order.id
            );
            guiItem.setItemMeta(meta);
            gui.setItem(slot++, guiItem);
        }

        gui.setItem(45, createGuiItem(Material.ARROW, "§fBack", "§7Return to main menu."));

        player.openInventory(gui);
    }

    private void openAuthMenu(Player player, int orderId) {
        List<UUID> authorized = plugin.getSupplyOrderManager().getAuthorizedPlayers(orderId);
        Inventory gui = plugin.getServer().createInventory(null, 54, AUTH_GUI_TITLE);

        // Show currently authorized players
        int slot = 0;
        for (UUID uuid : authorized) {
            if (slot >= 45) break;
            String name = plugin.getServer().getOfflinePlayer(uuid).getName();
            if (name == null) name = uuid.toString();

            ItemStack guiItem = createGuiItem(Material.PLAYER_HEAD,
                    "§b" + name,
                    "§7Authorized to pick up order §b#" + orderId,
                    "",
                    "§cClick §7to remove authorization"
            );

            // Store UUID in NBT
            ItemMeta meta = guiItem.getItemMeta();
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "auth_uuid"),
                    PersistentDataType.STRING,
                    uuid.toString()
            );
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "order_id"),
                    PersistentDataType.INTEGER,
                    orderId
            );
            guiItem.setItemMeta(meta);
            gui.setItem(slot++, guiItem);
        }

        // Add player button
        ItemStack addButton = createGuiItem(Material.LIME_DYE,
                "§a§lAuthorize a Player",
                "§7Type their name in chat after clicking.",
                "§7They will be able to pick up order §b#" + orderId + "§7."
        );
        ItemMeta addMeta = addButton.getItemMeta();
        addMeta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "order_id"),
                PersistentDataType.INTEGER,
                orderId
        );
        addButton.setItemMeta(addMeta);

        gui.setItem(49, addButton);
        gui.setItem(45, createGuiItem(Material.ARROW, "§fBack", "§7Return to my orders."));

        player.openInventory(gui);
    }

    private void openPoliceApp(Player player) {
        if (!plugin.getPoliceManager().isOfficer(player.getUniqueId())) {
            player.sendMessage("§4Access denied.");
            return;
        }

        com.UserMC.MJB.CrimeManager.PoliceRank rank =
                plugin.getCrimeManager().getRank(player.getUniqueId());
        double salary = plugin.getPoliceBudgetManager()
                .getSalary(player.getUniqueId());
        double budget = plugin.getPoliceBudgetManager().getBudget();

        Inventory gui = plugin.getServer().createInventory(
                null, 54, POLICE_APP_TITLE);

        // Officer info
        gui.setItem(4, createGuiItem(Material.GOLD_NUGGET,
                "§9§lYour Officer Info",
                "§7Rank: §b" + (rank != null ? rank.displayName : "Officer"),
                "§7Daily salary: §f" + plugin.getEconomyManager().format(salary),
                "§7Police budget: §f" + plugin.getEconomyManager().format(budget)));

        // Wanted list
        java.util.List<java.util.UUID> wanted =
                plugin.getCrimeManager().getAllWantedPlayers();
        int wantedSlot = 9;
        if (wanted.isEmpty()) {
            gui.setItem(wantedSlot, createGuiItem(Material.LIME_DYE,
                    "§aNo Wanted Players", "§7The city is at peace!"));
        } else {
            for (java.util.UUID uuid : wanted) {
                if (wantedSlot >= 36) break;
                String name = plugin.getServer().getOfflinePlayer(uuid).getName();
                java.util.List<com.UserMC.MJB.CrimeManager.CrimeRecord> crimes =
                        plugin.getCrimeManager().getUnprocessedRecords(uuid);
                boolean online = plugin.getServer().getPlayer(uuid) != null;
                gui.setItem(wantedSlot++, createGuiItem(Material.PAPER,
                        "§c" + (name != null ? name : uuid.toString()),
                        "§7Status: " + (online ? "§a[Online]" : "§7[Offline]"),
                        "§7Active charges: §f" + crimes.size()));
            }
        }

        // Downed players
        int downedSlot = 36;
        if (plugin.getHospitalManager().getAllDownedPlayers().isEmpty()) {
            gui.setItem(downedSlot, createGuiItem(Material.LIME_DYE,
                    "§aNo Downed Players", "§7Nobody needs help right now."));
        } else {
            for (var entry : plugin.getHospitalManager().getAllDownedPlayers().entrySet()) {
                if (downedSlot >= 45) break;
                String name = plugin.getServer().getOfflinePlayer(entry.getKey()).getName();
                long remaining = plugin.getHospitalManager()
                        .getDownedPlayer(entry.getKey()) != null
                        ? (entry.getValue().injury.bleedoutSeconds -
                           (System.currentTimeMillis() - entry.getValue().downerAt) / 1000)
                        : 0;
                gui.setItem(downedSlot++, createGuiItem(Material.RED_DYE,
                        "§c" + (name != null ? name : "Unknown"),
                        "§7Injury: §f" + entry.getValue().injury.displayName,
                        "§7Time left: §f" + Math.max(0, remaining) + "s"));
            }
        }

        // Request equipment info
        gui.setItem(49, createGuiItem(Material.CHEST,
                "§fRequest Equipment",
                "§7Visit the §bPolice Station NPC §7to",
                "§7submit equipment requisitions."));

        gui.setItem(45, createGuiItem(Material.ARROW, "§fBack",
                "§7Return to main menu."));
        gui.setItem(53, createGuiItem(Material.BARRIER, "§4Close", "§7Close."));

        player.openInventory(gui);
    }

    private void openHospitalApp(Player player) {
        if (!plugin.getHospitalManager().isDoctor(player.getUniqueId())) {
            player.sendMessage("§4Access denied.");
            return;
        }

        com.UserMC.MJB.HospitalManager.HospitalRank rank =
                plugin.getHospitalManager().getRank(player.getUniqueId());
        double salary  = plugin.getHospitalManager().getSalary(player.getUniqueId());
        double budget  = plugin.getHospitalBudgetManager().getBudget();

        Inventory gui = plugin.getServer().createInventory(
                null, 54, HOSPITAL_APP_TITLE);

        // Doctor info
        gui.setItem(4, createGuiItem(Material.NETHER_STAR,
                "§b§lYour Doctor Info",
                "§7Rank: §b" + rank.displayName,
                "§7Daily salary: §f" + plugin.getEconomyManager().format(salary),
                "§7Hospital budget: §f" + plugin.getEconomyManager().format(budget)));

        // Downed players
        int slot = 9;
        if (plugin.getHospitalManager().getAllDownedPlayers().isEmpty()) {
            gui.setItem(slot, createGuiItem(Material.LIME_DYE,
                    "§aNo Downed Players", "§7Nobody needs medical attention."));
        } else {
            for (var entry : plugin.getHospitalManager().getAllDownedPlayers().entrySet()) {
                if (slot >= 36) break;
                String name = plugin.getServer().getOfflinePlayer(
                        entry.getKey()).getName();
                long remaining = entry.getValue().injury.bleedoutSeconds -
                        (System.currentTimeMillis() - entry.getValue().downerAt) / 1000;
                boolean hasDrip = plugin.getMedicalRecordManager()
                        .hasIVDrip(entry.getKey());
                gui.setItem(slot++, createGuiItem(Material.RED_DYE,
                        "§c" + (name != null ? name : "Unknown"),
                        "§7Injury: §f" + entry.getValue().injury.displayName,
                        "§7Time left: §f" + Math.max(0, remaining) + "s",
                        hasDrip ? "§aIV drip active" : "§7No IV drip"));
            }
        }

        // Pending requests count
        int pending = plugin.getHospitalManager()
                .getPendingSupplyRequests().size();
        gui.setItem(45, createGuiItem(Material.ARROW, "§fBack",
                "§7Return to main menu."));
        gui.setItem(49, createGuiItem(Material.CHEST,
                "§fRequest Supplies §7(" + pending + " pending)",
                "§7Visit the §bHospital NPC §7to request",
                "§7medical supplies for approval."));
        gui.setItem(53, createGuiItem(Material.BARRIER, "§4Close", "§7Close."));

        player.openInventory(gui);
    }

    // ---- Click Handler ----

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        if (!title.equals(MAIN_GUI_TITLE) && !title.equals(ORDER_GUI_TITLE) &&
                !title.equals(CART_GUI_TITLE) && !title.equals(ORDERS_GUI_TITLE) &&
                !title.equals(AUTH_GUI_TITLE) && !title.equals(POLICE_APP_TITLE) &&
                !title.equals(HOSPITAL_APP_TITLE)) return;


        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (title.equals(POLICE_APP_TITLE)) {
            event.setCancelled(true);
            // Only process clicks on the top inventory (the GUI itself)
            if (event.getClickedInventory() == null ||
                    event.getClickedInventory() != event.getView().getTopInventory()) return;
            if (clicked == null || clicked.getType() == Material.AIR) return;

            if (clicked.getType() == Material.ARROW) {
                openMainMenu(player); return;
            }
            if (clicked.getType() == Material.BARRIER) {
                player.closeInventory(); return;
            }
            if (clicked.getType() == Material.CHEST) {
                player.closeInventory();
                player.sendMessage("§9§l[Police] §fVisit the §bPolice Station NPC §fto request equipment.");
                return;
            }
            // PAPER = wanted player entry — give as flyer
            if (clicked.getType() == Material.PAPER) {
                ItemStack flyer = clicked.clone();
                // Update lore to show it's a flyer
                org.bukkit.inventory.meta.ItemMeta meta = flyer.getItemMeta();
                java.util.List<String> lore = new java.util.ArrayList<>(
                        meta.getLore() != null ? meta.getLore() : new java.util.ArrayList<>());
                lore.add("");
                lore.add("§8§o[Wanted Flyer — printed from Police Terminal]");
                meta.setLore(lore);
                flyer.setItemMeta(meta);
                flyer.setAmount(1);
                player.getInventory().addItem(flyer);
                player.sendMessage("§9§l[Police] §fWanted flyer added to your inventory.");
                return;
            }
            return;
        }

        if (title.equals(HOSPITAL_APP_TITLE)) {
            event.setCancelled(true);
            if (event.getClickedInventory() == null ||
                    event.getClickedInventory() != event.getView().getTopInventory()) return;
            if (clicked == null || clicked.getType() == Material.AIR) return;

            if (clicked.getType() == Material.ARROW) {
                openMainMenu(player); return;
            }
            if (clicked.getType() == Material.BARRIER) {
                player.closeInventory(); return;
            }
            if (clicked.getType() == Material.CHEST) {
                player.closeInventory();
                player.sendMessage("§b§l[Hospital] §fVisit the §bHospital NPC §fto request supplies.");
                return;
            }
            return;
        }



        // Main menu
        if (title.equals(MAIN_GUI_TITLE)) {
            if (clicked.getType() == Material.CHEST) openOrderMenu(player);
            else if (clicked.getType() == Material.PAPER) openMyOrdersMenu(player);
            else if (clicked.getType() == Material.GOLD_BLOCK) {
                plugin.getCompanyComputerListener().openCompanyMenu(player);
            }
            else if (clicked.getType() == Material.OAK_DOOR) {
                plugin.getRealEstateNPCListener().openBrowseMenuPublic(player);
            }
            else if (clicked.getType() == Material.IRON_SWORD) {
                openPoliceApp(player);
            }
            else if (clicked.getType() == Material.NETHER_STAR) {
                openHospitalApp(player);
            }
            else if (clicked.getType() == Material.BARRIER) player.closeInventory();
            return;


        }

        // Order menu
        if (title.equals(ORDER_GUI_TITLE)) {
            if (clicked.getType() == Material.ARROW) { openMainMenu(player); return; }
            if (clicked.getType() == Material.GOLD_INGOT) { openCartMenu(player); return; }
            if (clicked.getType() == Material.SPECTRAL_ARROW) {
                // Prev or next page
                int currentPage = playerOrderPage.getOrDefault(player.getUniqueId(), 0);
                String name = clicked.getItemMeta() != null
                        ? clicked.getItemMeta().getDisplayName() : "";
                if (name.contains("Previous")) openOrderMenu(player, currentPage - 1);
                else openOrderMenu(player, currentPage + 1);
                return;
            }
            if (clicked.getType() == Material.PAPER) return;

            if (!clicked.hasItemMeta()) return;
            org.bukkit.NamespacedKey idKey =
                    new org.bukkit.NamespacedKey(plugin, "supply_item_id");
            if (!clicked.getItemMeta().getPersistentDataContainer()
                    .has(idKey, org.bukkit.persistence.PersistentDataType.INTEGER)) return;
            int supplyId = clicked.getItemMeta().getPersistentDataContainer()
                    .get(idKey, org.bukkit.persistence.PersistentDataType.INTEGER);
            SupplyOrderManager.SupplyItem supplyItem =
                    plugin.getSupplyOrderManager().getSupplyItemById(supplyId);
            if (supplyItem == null) return;
            String materialName = supplyItem.material;

            int amount = 1;
            if (event.isRightClick()) amount = 16;
            if (event.isShiftClick()) amount = 64;

            List<OrderLine> cart = playerCart.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
            boolean found = false;
            for (int i = 0; i < cart.size(); i++) {
                if (cart.get(i).material.equals(materialName)) {
                    OrderLine old = cart.get(i);
                    cart.set(i, new OrderLine(old.material, old.quantity + amount, old.pricePerItem, old.deliverySeconds, old.supplyItemId));
                    found = true;
                    break;
                }
            }
            if (!found) cart.add(new OrderLine(materialName, amount, supplyItem.pricePerItem, supplyItem.deliverySeconds, supplyItem.id));
            player.sendMessage("§7Added §f" + amount + "x " + formatMaterial(materialName) + " §7to cart.");
            return;
        }

        // Cart menu
        if (title.equals(CART_GUI_TITLE)) {
            if (clicked.getType() == Material.ARROW) { openOrderMenu(player); return; }
            if (clicked.getType() == Material.BARRIER) {
                playerCart.remove(player.getUniqueId());
                player.sendMessage("§7Cart cleared.");
                openOrderMenu(player);
                return;
            }
            if (clicked.getType() == Material.EMERALD) {
                handleConfirmOrder(player);
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
            if (clicked.getType() == Material.ARROW) { openMainMenu(player); return; }

            // Get order ID from NBT and open auth menu
            if (!clicked.hasItemMeta()) return;
            NamespacedKey orderKey = new NamespacedKey(plugin, "order_id");
            if (!clicked.getItemMeta().getPersistentDataContainer().has(orderKey, PersistentDataType.INTEGER)) return;
            int orderId = clicked.getItemMeta().getPersistentDataContainer().get(orderKey, PersistentDataType.INTEGER);
            openAuthMenu(player, orderId);
            return;
        }

        // Auth menu
        if (title.equals(AUTH_GUI_TITLE)) {
            if (clicked.getType() == Material.ARROW) { openMyOrdersMenu(player); return; }

            NamespacedKey orderKey = new NamespacedKey(plugin, "order_id");
            if (!clicked.hasItemMeta()) return;

            // Add player button
            if (clicked.getType() == Material.LIME_DYE) {
                if (!clicked.getItemMeta().getPersistentDataContainer().has(orderKey, PersistentDataType.INTEGER)) return;
                int orderId = clicked.getItemMeta().getPersistentDataContainer().get(orderKey, PersistentDataType.INTEGER);
                player.closeInventory();
                player.sendMessage("§b§lAuthorize Pickup §7— Type the player's name in chat, or §fcancel§7.");
                awaitingAuthOrderId.put(player.getUniqueId(), orderId);
                return;
            }

            // Remove authorization
            NamespacedKey uuidKey = new NamespacedKey(plugin, "auth_uuid");
            if (!clicked.getItemMeta().getPersistentDataContainer().has(uuidKey, PersistentDataType.STRING)) return;
            String uuidStr = clicked.getItemMeta().getPersistentDataContainer().get(uuidKey, PersistentDataType.STRING);
            int orderId = clicked.getItemMeta().getPersistentDataContainer().get(orderKey, PersistentDataType.INTEGER);

            plugin.getSupplyOrderManager().removeAuthorization(orderId, UUID.fromString(uuidStr));
            String removedName = plugin.getServer().getOfflinePlayer(UUID.fromString(uuidStr)).getName();
            player.sendMessage("§7Removed §b" + removedName + " §7from authorized pickups for order §b#" + orderId + "§7.");
            openAuthMenu(player, orderId);
            return;
        }
    }

    // ---- Chat listener for authorization input ----

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!awaitingAuthOrderId.containsKey(player.getUniqueId())) return;

        event.setCancelled(true);
        String input = event.getMessage().trim();

        if (input.equalsIgnoreCase("cancel")) {
            awaitingAuthOrderId.remove(player.getUniqueId());
            player.sendMessage("§7Authorization cancelled.");
            return;
        }

        int orderId = awaitingAuthOrderId.remove(player.getUniqueId());

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player target = plugin.getServer().getPlayer(input);
            if (target == null) {
                player.sendMessage("§4Player §f" + input + " §4not found or not online.");
                return;
            }

            if (target.equals(player)) {
                player.sendMessage("§4You are already authorized — you own this order.");
                return;
            }

            plugin.getSupplyOrderManager().authorizePlayer(orderId, target.getUniqueId());
            player.sendMessage("§b" + target.getName() + " §fis now authorized to pick up order §b#" + orderId + "§f.");
            target.sendMessage("§b§l[Supply] §b" + player.getName() + " §fhas authorized you to pick up order §b#" + orderId + "§f.");
        });
    }

    // ---- Order confirmation ----

    private void handleConfirmOrder(Player player) {
        List<OrderLine> cart = playerCart.getOrDefault(player.getUniqueId(), new ArrayList<>());

        if (cart.isEmpty()) {
            player.sendMessage("§4Your cart is empty!");
            return;
        }

        double total = cart.stream().mapToDouble(l -> l.quantity * l.pricePerItem).sum();

        // Check if player is in a company — if so, charge the company bank
        CompanyManager.CompanyInfo company = plugin.getCompanyManager().getCompanyForPlayer(player.getUniqueId());
        boolean usingCompanyBank = company != null;

        if (usingCompanyBank) {
            if (company.isBankrupt) {
                player.sendMessage("§4Your company is bankrupt and cannot place orders.");
                player.sendMessage("§7Deposit funds into the company account first.");
                return;
            }
            if (company.bankBalance < total) {
                player.sendMessage("§4Insufficient company bank balance!");
                player.sendMessage("§7Total: §f" + plugin.getEconomyManager().format(total));
                player.sendMessage("§7Company balance: §f" + plugin.getEconomyManager().format(company.bankBalance));
                return;
            }
            String sql = "UPDATE companies SET bank_balance = bank_balance - ? WHERE id = ?";
            try (java.sql.PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
                stmt.setDouble(1, total);
                stmt.setInt(2, company.id);
                stmt.executeUpdate();
            } catch (java.sql.SQLException e) {
                player.sendMessage("§4Payment failed. Please try again.");
                return;
            }
        } else {
            double balance = plugin.getEconomyManager().getBankBalance(player.getUniqueId());
            if (balance < total) {
                player.sendMessage("§4Insufficient bank balance!");
                player.sendMessage("§7Total: §f" + plugin.getEconomyManager().format(total));
                player.sendMessage("§7Your balance: §f" + plugin.getEconomyManager().format(balance));
                return;
            }
            String sql = "UPDATE players SET bank_balance = bank_balance - ? WHERE uuid = ?";
            try (java.sql.PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
                stmt.setDouble(1, total);
                stmt.setString(2, player.getUniqueId().toString());
                stmt.executeUpdate();
            } catch (java.sql.SQLException e) {
                player.sendMessage("§4Payment failed. Please try again.");
                return;
            }
        }

        String district = playerDistrict.getOrDefault(player.getUniqueId(), "central");
        int orderId = plugin.getSupplyOrderManager().placeOrder(
                player.getUniqueId(),
                usingCompanyBank ? company.id : -1,
                district,
                cart
        );

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
        player.sendMessage("§7Total charged: §f" + plugin.getEconomyManager().format(total) +
                (usingCompanyBank ? " §7(company bank)" : " §7(personal bank)"));
        player.sendMessage("§7You will be notified when it's ready.");
        player.sendMessage("§7Go to §fMy Orders §7to authorize others to pick it up.");
        player.sendMessage("§b§m-----------------------------");
    }

    // ---- Helpers ----

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