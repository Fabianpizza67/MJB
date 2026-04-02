package com.UserMC.MJB.listeners;

import com.UserMC.MJB.CrimeManager;
import com.UserMC.MJB.CrimeManager.CrimeRecord;
import com.UserMC.MJB.MJB;
import com.UserMC.MJB.PoliceBudgetManager;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import com.UserMC.MJB.PoliceManager;

import java.text.SimpleDateFormat;
import java.util.*;

public class PoliceStationListener implements Listener {

    private final MJB plugin;
    public static final String STATION_NPC_TAG = "police_station";

    private static final String STATION_GUI   = "§9§lPolice Station";
    private static final String REQUEST_GUI   = "§9§lRequest Equipment";
    private static final String BUDGET_GUI    = "§9§lPolice Budget";
    private static final String PENDING_GUI   = "§9§lPending Requests";

    private final NamespacedKey ACTION_KEY;
    private final NamespacedKey REQ_ID_KEY;
    private final NamespacedKey SUSPECT_KEY;
    private final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm");

    // Track which suspect the officer is processing
    private final Map<UUID, UUID> processingSession = new HashMap<>();

    public PoliceStationListener(MJB plugin) {
        this.plugin = plugin;
        ACTION_KEY  = new NamespacedKey(plugin, "station_action");
        REQ_ID_KEY  = new NamespacedKey(plugin, "req_id");
        SUSPECT_KEY = new NamespacedKey(plugin, "suspect_uuid");
    }

    // ---- NPC click ----

    @EventHandler
    public void onNPCRightClick(NPCRightClickEvent event) {
        NPC npc = event.getNPC();
        if (!npc.data().has(STATION_NPC_TAG)) return;

        Player player = event.getClicker();
        if (!plugin.getPoliceManager().isOfficer(player.getUniqueId())) {
            player.sendMessage("§4This terminal is for police use only.");
            return;
        }
        openStationMenu(player);
    }

    // ---- Main station menu ----

    private void openStationMenu(Player player) {
        Inventory gui = plugin.getServer().createInventory(null, 27, STATION_GUI);
        CrimeManager.PoliceRank rank = plugin.getCrimeManager().getRank(player.getUniqueId());
        boolean isSgt = rank == CrimeManager.PoliceRank.SERGEANT;

        double budget = plugin.getPoliceBudgetManager().getBudget();
        double salary = plugin.getPoliceBudgetManager().getSalary(player.getUniqueId());

        gui.setItem(4, item(Material.GOLD_INGOT, "§9§lPolice Station",
                "§7Budget: §f" + plugin.getEconomyManager().format(budget),
                "§7Your salary: §f" + plugin.getEconomyManager().format(salary) + " §7/day",
                "§7Rank: §b" + (rank != null ? rank.displayName : "Officer")));

        // Request equipment (all officers)
        ItemStack reqBtn = item(Material.CHEST, "§f§lRequest Equipment",
                "§7Submit a requisition request.", "§7Sergeant must approve.");
        setAction(reqBtn, "open_request");
        gui.setItem(10, reqBtn);

        // Process suspect (all officers — pick nearest cuffed player)
        ItemStack processBtn = item(Material.PAPER, "§f§lProcess Suspect",
                "§7Process a cuffed suspect at this station.",
                "§7Detectives and Sergeants only.");
        setAction(processBtn, "process_suspect");
        gui.setItem(12, processBtn);

        // Budget management (sergeant only)
        if (isSgt) {
            ItemStack budgetBtn = item(Material.GOLD_BLOCK, "§f§lManage Budget",
                    "§7Deposit, withdraw, set salaries.",
                    "§7View and order pending requests.");
            setAction(budgetBtn, "open_budget");
            gui.setItem(14, budgetBtn);
        }

        gui.setItem(22, item(Material.BARRIER, "§4Close", "§7Close this menu."));
        player.openInventory(gui);
    }

    // ---- Request equipment GUI (officer) ----

    private void openRequestMenu(Player player) {
        Inventory gui = plugin.getServer().createInventory(null, 54, REQUEST_GUI);

        int slot = 0;
        for (Map.Entry<String, Double> entry : PoliceBudgetManager.EQUIPMENT_PRICES.entrySet()) {
            if (slot >= 45) break;
            String key = entry.getKey();
            String name = PoliceBudgetManager.EQUIPMENT_NAMES.getOrDefault(key, key);
            double price = entry.getValue();

            ItemStack btn = item(iconForItem(key), "§f" + name,
                    "§7Price: §b" + plugin.getEconomyManager().format(price),
                    "§7Click to request 1",
                    "§7Shift-click to request 5");
            setAction(btn, "request_" + key);
            gui.setItem(slot++, btn);
        }

        gui.setItem(45, item(Material.ARROW, "§fBack", "§7Return to station menu."));
        player.openInventory(gui);
    }

    // ---- Budget GUI (sergeant) ----

    private void openBudgetMenu(Player player) {
        Inventory gui = plugin.getServer().createInventory(null, 27, BUDGET_GUI);
        double budget = plugin.getPoliceBudgetManager().getBudget();

        gui.setItem(4, item(Material.GOLD_BLOCK, "§6§lPolice Budget",
                "§7Current balance: §f" + plugin.getEconomyManager().format(budget),
                "§7Funded by the city council.",
                "§7Use §f/council fundpolice §7to add funds."));

        // Pending requisitions only — no personal bank buttons
        List<PoliceBudgetManager.Requisition> pending =
                plugin.getPoliceBudgetManager().getPendingRequisitions();
        ItemStack pendingBtn = item(Material.WRITABLE_BOOK,
                "§f§lPending Requests §7(" + pending.size() + ")",
                "§7Review and order equipment requests.");
        setAction(pendingBtn, "open_pending");
        gui.setItem(11, pendingBtn);

        ItemStack salaryBtn = item(Material.PLAYER_HEAD, "§f§lSet Salaries",
                "§7Set daily salary per officer.");
        setAction(salaryBtn, "open_salaries");
        gui.setItem(15, salaryBtn);

        gui.setItem(22, item(Material.ARROW, "§fBack", "§7Return to station menu."));
        player.openInventory(gui);
    }
    // ---- Pending requisitions GUI (sergeant) ----

    private void openPendingMenu(Player player) {
        List<PoliceBudgetManager.Requisition> pending =
                plugin.getPoliceBudgetManager().getPendingRequisitions();
        Inventory gui = plugin.getServer().createInventory(null, 54, PENDING_GUI);

        if (pending.isEmpty()) {
            gui.setItem(22, item(Material.LIME_DYE, "§aNo pending requests", "§7All clear!"));
        }

        int slot = 0;
        for (PoliceBudgetManager.Requisition req : pending) {
            if (slot >= 45) break;
            String officerName = plugin.getServer().getOfflinePlayer(req.officerUuid).getName();
            String itemName = PoliceBudgetManager.EQUIPMENT_NAMES.getOrDefault(req.itemType, req.itemType);
            double cost = PoliceBudgetManager.EQUIPMENT_PRICES.getOrDefault(req.itemType, 0.0) * req.quantity;

            ItemStack btn = item(iconForItem(req.itemType),
                    "§f" + req.quantity + "x " + itemName,
                    "§7Officer: §b" + officerName,
                    "§7Cost: §c" + plugin.getEconomyManager().format(cost),
                    "§7Requested: §f" + sdf.format(req.requestedAt),
                    "",
                    "§aClick §7to order (deducts from budget)",
                    "§cShift-click §7to dismiss without ordering");

            ItemMeta meta = btn.getItemMeta();
            meta.getPersistentDataContainer().set(REQ_ID_KEY, PersistentDataType.INTEGER, req.id);
            btn.setItemMeta(meta);
            gui.setItem(slot++, btn);
        }

        gui.setItem(45, item(Material.ARROW, "§fBack", "§7Return to budget menu."));
        player.openInventory(gui);
    }

    // ---- Salary list GUI (sergeant) ----

    private void openSalaryMenu(Player player) {
        List<PoliceManager.OfficerInfo> officers = plugin.getPoliceManager().getAllOfficers();
        Inventory gui = plugin.getServer().createInventory(null, 54, "§9§lSet Salaries");

        int slot = 0;
        for (PoliceManager.OfficerInfo officer : officers) {
            if (slot >= 45) break;
            String name = plugin.getServer().getOfflinePlayer(officer.uuid).getName();
            double salary = plugin.getPoliceBudgetManager().getSalary(officer.uuid);
            CrimeManager.PoliceRank rank = plugin.getCrimeManager().getRank(officer.uuid);

            ItemStack btn = item(Material.PLAYER_HEAD,
                    "§b" + (name != null ? name : officer.uuid.toString()),
                    "§7Rank: §f" + (rank != null ? rank.displayName : "Officer"),
                    "§7Salary: §f" + plugin.getEconomyManager().format(salary) + " §7/day",
                    "",
                    "§eClick §7to set salary (type in chat)");

            ItemMeta meta = btn.getItemMeta();
            meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING,
                    "set_salary_" + officer.uuid.toString());
            btn.setItemMeta(meta);
            gui.setItem(slot++, btn);
        }

        gui.setItem(45, item(Material.ARROW, "§fBack", "§7Return to budget menu."));
        player.openInventory(gui);
    }

    // ---- Click handler ----

    // Chat input sessions
    private final Map<UUID, String> awaitingChat = new HashMap<>();

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        if (!title.equals(STATION_GUI) && !title.equals(REQUEST_GUI) &&
                !title.equals(BUDGET_GUI) && !title.equals(PENDING_GUI) &&
                !title.equals("§9§lSet Salaries")) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Back buttons
        if (clicked.getType() == Material.ARROW) {
            if (title.equals(REQUEST_GUI) || title.equals(BUDGET_GUI)) openStationMenu(player);
            else if (title.equals(PENDING_GUI) || title.equals("§9§lSet Salaries")) openBudgetMenu(player);
            return;
        }
        if (clicked.getType() == Material.BARRIER) { player.closeInventory(); return; }

        // ---- Station main menu ----
        if (title.equals(STATION_GUI)) {
            String action = getAction(clicked);
            if (action == null) return;
            switch (action) {
                case "open_request"   -> openRequestMenu(player);
                case "open_budget"    -> openBudgetMenu(player);
                case "process_suspect" -> handleProcessSuspect(player);
            }
            return;
        }

        // ---- Request menu ----
        if (title.equals(REQUEST_GUI)) {
            String action = getAction(clicked);
            if (action == null || !action.startsWith("request_")) return;
            String itemType = action.substring(8);
            int qty = event.isShiftClick() ? 5 : 1;

            int reqId = plugin.getPoliceBudgetManager().submitRequisition(
                    player.getUniqueId(), itemType, qty);
            if (reqId == -1) {
                player.sendMessage("§4Failed to submit request.");
                return;
            }
            String itemName = PoliceBudgetManager.EQUIPMENT_NAMES.getOrDefault(itemType, itemType);
            player.sendMessage("§b§l[Police] §fRequest submitted: §f" + qty + "x " + itemName +
                    "§f. §7Waiting for Sergeant approval.");
            player.closeInventory();

            // Notify online sergeants
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (!plugin.getPoliceManager().isOfficer(p.getUniqueId())) continue;
                if (plugin.getCrimeManager().getRank(p.getUniqueId()) == CrimeManager.PoliceRank.SERGEANT) {
                    p.sendMessage("§9§l[Police] §b" + player.getName() +
                            " §frequested §f" + qty + "x " + itemName +
                            "§f. Check the station terminal to order.");
                }
            }
            return;
        }

        // ---- Pending requests menu ----
        if (title.equals(PENDING_GUI)) {
            if (!clicked.hasItemMeta()) return;
            Integer reqId = clicked.getItemMeta().getPersistentDataContainer()
                    .get(REQ_ID_KEY, PersistentDataType.INTEGER);
            if (reqId == null) return;

            if (event.isShiftClick()) {
                // Dismiss without ordering
                String sql = "UPDATE police_requisitions SET status = 'dismissed' WHERE id = ?";
                try (java.sql.PreparedStatement stmt = plugin.getDatabaseManager()
                        .getConnection().prepareStatement(sql)) {
                    stmt.setInt(1, reqId);
                    stmt.executeUpdate();
                } catch (java.sql.SQLException e) {
                    plugin.getLogger().severe("Error dismissing requisition: " + e.getMessage());
                }
                player.sendMessage("§7Request dismissed.");
            } else {
                // Order it
                boolean ok = plugin.getPoliceBudgetManager().orderRequisition(reqId);
                if (ok) {
                    player.sendMessage("§a§l[Police] §aRequisition ordered! Will be delivered in 10 minutes.");
                } else {
                    player.sendMessage("§4Insufficient police budget to order this item.");
                }
            }
            openPendingMenu(player);
            return;
        }

        // ---- Budget menu ----
        if (title.equals(BUDGET_GUI)) {
            String action = getAction(clicked);
            if (action == null) return;
            switch (action) {
                case "open_pending"  -> openPendingMenu(player);
                case "open_salaries" -> openSalaryMenu(player);
            }
            return;
        }

        // ---- Salary menu ----
        if (title.equals("§9§lSet Salaries")) {
            if (!clicked.hasItemMeta()) return;
            String action = clicked.getItemMeta().getPersistentDataContainer()
                    .get(ACTION_KEY, PersistentDataType.STRING);
            if (action == null || !action.startsWith("set_salary_")) return;
            String targetUuidStr = action.substring(11);
            player.closeInventory();
            player.sendMessage("§9§lSet Salary §7— Type the daily salary amount, or §fcancel§7.");
            awaitingChat.put(player.getUniqueId(), "set_salary_" + targetUuidStr);
        }
    }

    @EventHandler
    public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!awaitingChat.containsKey(player.getUniqueId())) return;

        event.setCancelled(true);
        String input = event.getMessage().trim();
        String session = awaitingChat.remove(player.getUniqueId());

        if (input.equalsIgnoreCase("cancel")) {
            player.sendMessage("§7Cancelled.");
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            double amount;
            try { amount = Double.parseDouble(input); }
            catch (NumberFormatException e) { player.sendMessage("§4Invalid amount."); return; }
            if (amount <= 0) { player.sendMessage("§4Amount must be positive."); return; }
            if (session.equals("budget_deposit")) {
                boolean ok = plugin.getPoliceBudgetManager().depositFromPlayer(player.getUniqueId(), amount);
                if (ok) player.sendMessage("§aDeposited §f" + plugin.getEconomyManager().format(amount) +
                        " §ainto the police budget.");
                else player.sendMessage("§4Insufficient personal bank balance.");

            } else if (session.equals("budget_withdraw")) {
                boolean ok = plugin.getPoliceBudgetManager().withdrawToPlayer(player.getUniqueId(), amount);
                if (ok) player.sendMessage("§aWithdrew §f" + plugin.getEconomyManager().format(amount) +
                        " §afrom the police budget.");
                else player.sendMessage("§4Insufficient police budget balance.");
            } else if (session.startsWith("set_salary_")) {
                UUID targetUuid = UUID.fromString(session.substring(11));
                plugin.getPoliceBudgetManager().setSalary(targetUuid, amount);
                String name = plugin.getServer().getOfflinePlayer(targetUuid).getName();
                player.sendMessage("§fSalary for §b" + name + " §fset to §b" +
                        plugin.getEconomyManager().format(amount) + " §f/day.");
                Player target = plugin.getServer().getPlayer(targetUuid);
                if (target != null) {
                    target.sendMessage("§b§l[Police] §fThe Sergeant has set your daily salary to §b" +
                            plugin.getEconomyManager().format(amount) + "§f.");
                }
            }
        });
    }

    // ---- Process suspect ----

    private void handleProcessSuspect(Player officer) {
        CrimeManager.PoliceRank rank = plugin.getCrimeManager().getRank(officer.getUniqueId());
        if (rank == CrimeManager.PoliceRank.OFFICER) {
            officer.sendMessage("§4Only Detectives and Sergeants can process suspects.");
            return;
        }

        // Find nearest cuffed player within 10 blocks
        Player nearest = null;
        double nearestDist = 10.0;
        for (UUID cuffed : plugin.getPoliceManager().getCuffedPlayers()) {
            Player suspect = plugin.getServer().getPlayer(cuffed);
            if (suspect == null) continue;
            if (!suspect.getWorld().equals(officer.getWorld())) continue;
            double dist = suspect.getLocation().distance(officer.getLocation());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = suspect;
            }
        }

        if (nearest == null) {
            officer.sendMessage("§4No cuffed suspects nearby. Escort them to the station first.");
            return;
        }

        // Show processing GUI
        openProcessMenu(officer, nearest);
    }

    private void openProcessMenu(Player officer, Player suspect) {
        List<CrimeRecord> active = plugin.getCrimeManager()
                .getUnprocessedRecords(suspect.getUniqueId());

        Inventory gui = plugin.getServer().createInventory(null, 27,
                "§9§lProcess: §f" + suspect.getName());

        gui.setItem(4, item(Material.PAPER, "§c§lActive Charges",
                active.isEmpty() ? "§7No active charges." :
                        active.stream().map(r -> "§f- " + r.offence)
                                .reduce("", (a, b) -> a + "\n" + b)));

        // Process — marks charges as processed, clears wanted
        ItemStack processBtn = item(Material.EMERALD, "§a§lProcess & Release",
                "§7Mark charges as processed.",
                "§7Clears wanted status.",
                "§7Charges remain on record.");
        ItemMeta pm = processBtn.getItemMeta();
        pm.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "do_process");
        pm.getPersistentDataContainer().set(SUSPECT_KEY, PersistentDataType.STRING, suspect.getUniqueId().toString());
        processBtn.setItemMeta(pm);
        gui.setItem(11, processBtn);

        // Uncuff only
        ItemStack uncuffBtn = item(Material.IRON_CHAIN, "§e§lUncuff Only",
                "§7Release handcuffs without processing.",
                "§7Charges and wanted status remain.");
        ItemMeta um = uncuffBtn.getItemMeta();
        um.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "do_uncuff");
        um.getPersistentDataContainer().set(SUSPECT_KEY, PersistentDataType.STRING, suspect.getUniqueId().toString());
        uncuffBtn.setItemMeta(um);
        gui.setItem(15, uncuffBtn);

        gui.setItem(22, item(Material.BARRIER, "§4Cancel", "§7Close."));
        officer.openInventory(gui);
    }

    @EventHandler
    public void onProcessClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player officer)) return;
        String title = event.getView().getTitle();
        if (!title.startsWith("§9§lProcess: ")) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        if (clicked.getType() == Material.BARRIER) { officer.closeInventory(); return; }

        String action = clicked.getItemMeta().getPersistentDataContainer()
                .get(ACTION_KEY, PersistentDataType.STRING);
        String suspectStr = clicked.getItemMeta().getPersistentDataContainer()
                .get(SUSPECT_KEY, PersistentDataType.STRING);
        if (action == null || suspectStr == null) return;

        UUID suspectUuid = UUID.fromString(suspectStr);
        Player suspect = plugin.getServer().getPlayer(suspectUuid);

        if ("do_process".equals(action)) {
            plugin.getCrimeManager().markRecordsProcessed(suspectUuid);
            plugin.getCrimeManager().setWanted(suspectUuid, false);
            plugin.getPoliceManager().uncuff(suspectUuid);
            officer.sendMessage("§f§l[Police] §f" + (suspect != null ? suspect.getName() : "Suspect") +
                    " §fhas been processed and released.");
            if (suspect != null) {
                suspect.sendMessage("§b§l[Police] §fYou have been processed by §b" + officer.getName() +
                        "§f. Your charges are on record.");
            }
        } else if ("do_uncuff".equals(action)) {
            plugin.getPoliceManager().uncuff(suspectUuid);
            officer.sendMessage("§f" + (suspect != null ? suspect.getName() : "Suspect") +
                    " §fhas been uncuffed. Charges remain active.");
            if (suspect != null) {
                suspect.sendMessage("§7You have been uncuffed. Your charges are still active.");
            }
        }
        officer.closeInventory();
    }

    // ---- Helpers ----

    private ItemStack item(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    private void setAction(ItemStack item, String action) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
    }

    private String getAction(ItemStack item) {
        if (!item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(ACTION_KEY, PersistentDataType.STRING);
    }

    private Material iconForItem(String itemType) {
        return switch (itemType) {
            case "pistol"       -> Material.NETHER_STAR;
            case "rifle"        -> Material.BLAZE_ROD;
            case "shotgun"      -> Material.GHAST_TEAR;
            case "handcuffs"    -> Material.IRON_CHAIN;
            case "badge"        -> Material.GOLDEN_SWORD;
            case "baton"        -> Material.STICK;
            case "ammo_pistol", "ammo_rifle", "ammo_shotgun" -> Material.PAPER;
            case "uniform"      -> Material.LEATHER_CHESTPLATE;
            case "police_radio" -> Material.CLOCK;
            default             -> Material.GRAY_DYE;
        };
    }
}