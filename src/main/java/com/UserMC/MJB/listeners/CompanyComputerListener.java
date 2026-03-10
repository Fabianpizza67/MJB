package com.UserMC.MJB.listeners;

import com.UserMC.MJB.CompanyManager;
import com.UserMC.MJB.CompanyManager.*;
import com.UserMC.MJB.MJB;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Handles the "Company" app inside the computer GUI.
 * Triggered by ComputerListener when the player opens the company tab.
 */
public class CompanyComputerListener implements Listener {

    private final MJB plugin;

    // GUI title constants — must be unique to avoid clashing with ComputerListener
    public static final String COMPANY_MENU_TITLE = "§b§lMy Company";
    public static final String COMPANY_MEMBERS_TITLE = "§b§lEmployees";
    public static final String COMPANY_BANK_TITLE = "§b§lCompany Bank";
    public static final String COMPANY_ROLES_TITLE = "§b§lManage Roles";
    public static final String COMPANY_SELL_TITLE = "§b§lSell Company";

    // Chat input states
    private final Map<UUID, ChatInputSession> awaitingInput = new HashMap<>();

    public CompanyComputerListener(MJB plugin) {
        this.plugin = plugin;
    }

    // ---- Called by ComputerListener when player opens company tab ----

    public void openCompanyMenu(Player player) {
        CompanyInfo info = plugin.getCompanyManager().getCompanyForPlayer(player.getUniqueId());

        if (info == null) {
            player.sendMessage("§4You are not part of any company.");
            player.sendMessage("§7Visit the §fGovernment Office NPC §7to register one.");
            return;
        }

        Inventory gui = plugin.getServer().createInventory(null, 27, COMPANY_MENU_TITLE);

        // Company info display
        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7Type: §f" + capitalize(info.type));
        infoLore.add("§7About: §f" + info.description);
        infoLore.add("§7Balance: §f" + plugin.getEconomyManager().format(info.bankBalance));
        infoLore.add(info.isBankrupt ? "§4§l  ⚠ BANKRUPT" : "§aOperating normally");
        gui.setItem(4, createItem(Material.GOLD_BLOCK, "§b§l" + info.name, infoLore.toArray(new String[0])));

        CompanyMember myRole = plugin.getCompanyManager().getMember(info.id, player.getUniqueId());
        boolean isOwnerOrManager = info.ownerUuid.equals(player.getUniqueId()) ||
                plugin.getCompanyManager().hasPermission(info.id, player.getUniqueId(), "hire_fire");

        gui.setItem(10, createItem(Material.PLAYER_HEAD, "§f§lEmployees",
                "§7View and manage your team.",
                "§7Hire, fire, and set salaries."));

        gui.setItem(12, createItem(Material.GOLD_INGOT, "§f§lCompany Bank",
                "§7Balance: §f" + plugin.getEconomyManager().format(info.bankBalance),
                "§7Deposit or withdraw funds."));

        if (isOwnerOrManager) {
            gui.setItem(14, createItem(Material.WRITABLE_BOOK, "§f§lManage Roles",
                    "§7Create or view custom roles.",
                    "§7Assign permissions to team members."));
        }

        if (info.ownerUuid.equals(player.getUniqueId())) {
            gui.setItem(16, createItem(Material.BARRIER, "§4§lSell Company",
                    "§7Transfer ownership to another player.",
                    "§7You will receive the agreed sale price."));
        }

        // Store company ID in each button via NBT using back button as carrier
        storeCompanyId(gui, info.id, plugin);
        gui.setItem(18, createItem(Material.ARROW, "§fBack", "§7Return to main menu."));

        player.openInventory(gui);
    }

    private void openMembersMenu(Player player, int companyId) {
        CompanyInfo info = plugin.getCompanyManager().getCompanyById(companyId);
        if (info == null) return;

        List<CompanyMember> members = plugin.getCompanyManager().getMembers(companyId);
        Inventory gui = plugin.getServer().createInventory(null, 54, COMPANY_MEMBERS_TITLE);

        boolean canManage = info.ownerUuid.equals(player.getUniqueId()) ||
                plugin.getCompanyManager().hasPermission(companyId, player.getUniqueId(), "hire_fire");

        int slot = 0;
        for (CompanyMember member : members) {
            if (slot >= 45) break;
            String name = plugin.getServer().getOfflinePlayer(member.playerUuid).getName();
            if (name == null) name = member.playerUuid.toString();

            List<String> lore = new ArrayList<>();
            lore.add("§7Role: §b" + capitalize(member.roleName));
            lore.add("§7Salary: §f" + plugin.getEconomyManager().format(member.salary) + " §7/day");
            if (canManage && !member.playerUuid.equals(info.ownerUuid)) {
                lore.add("");
                lore.add("§eLeft-click §7to set salary");
                lore.add("§cRight-click §7to fire");
            }

            ItemStack item = createItem(Material.PLAYER_HEAD, "§b" + name, lore.toArray(new String[0]));
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "member_uuid"), PersistentDataType.STRING, member.playerUuid.toString());
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "company_id"), PersistentDataType.INTEGER, companyId);
            item.setItemMeta(meta);
            gui.setItem(slot++, item);
        }

        if (canManage) {
            ItemStack hireBtn = createItem(Material.LIME_DYE, "§a§lHire Employee",
                    "§7Type a player's name in chat to hire them.");
            ItemMeta hireMeta = hireBtn.getItemMeta();
            hireMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "company_id"), PersistentDataType.INTEGER, companyId);
            hireBtn.setItemMeta(hireMeta);
            gui.setItem(49, hireBtn);
        }

        gui.setItem(45, createItem(Material.ARROW, "§fBack", "§7Return to company menu."));
        player.openInventory(gui);
    }

    private void openBankMenu(Player player, int companyId) {
        CompanyInfo info = plugin.getCompanyManager().getCompanyById(companyId);
        if (info == null) return;

        boolean canAccess = info.ownerUuid.equals(player.getUniqueId()) ||
                plugin.getCompanyManager().hasPermission(companyId, player.getUniqueId(), "access_bank");

        Inventory gui = plugin.getServer().createInventory(null, 27, COMPANY_BANK_TITLE);

        gui.setItem(4, createItem(Material.GOLD_INGOT, "§b§lCompany Balance",
                "§7Balance: §f" + plugin.getEconomyManager().format(info.bankBalance),
                info.isBankrupt ? "§4§l  ⚠ BANKRUPT — deposit to restore" : "§aOperating normally"));

        // Deposit button (anyone can deposit)
        ItemStack depositBtn = createItem(Material.EMERALD, "§a§lDeposit Funds",
                "§7Transfer from your personal bank.",
                "§7Type an amount in chat.");
        ItemMeta depositMeta = depositBtn.getItemMeta();
        depositMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "company_id"), PersistentDataType.INTEGER, companyId);
        depositMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "bank_action"), PersistentDataType.STRING, "deposit");
        depositBtn.setItemMeta(depositMeta);
        gui.setItem(11, depositBtn);

        if (canAccess) {
            ItemStack withdrawBtn = createItem(Material.REDSTONE, "§c§lWithdraw Funds",
                    "§7Transfer to your personal bank.",
                    "§7Type an amount in chat.");
            ItemMeta withdrawMeta = withdrawBtn.getItemMeta();
            withdrawMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "company_id"), PersistentDataType.INTEGER, companyId);
            withdrawMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "bank_action"), PersistentDataType.STRING, "withdraw");
            withdrawBtn.setItemMeta(withdrawMeta);
            gui.setItem(15, withdrawBtn);
        }

        gui.setItem(18, createItem(Material.ARROW, "§fBack", "§7Return to company menu."));
        player.openInventory(gui);
    }

    private void openRolesMenu(Player player, int companyId) {
        List<CompanyRole> roles = plugin.getCompanyManager().getRoles(companyId);
        Inventory gui = plugin.getServer().createInventory(null, 54, COMPANY_ROLES_TITLE);

        int slot = 0;
        for (CompanyRole role : roles) {
            if (slot >= 45) break;
            gui.setItem(slot++, createItem(Material.NAME_TAG,
                    "§b" + capitalize(role.roleName),
                    "§7Hire/Fire: " + bool(role.canHireFire),
                    "§7Set Prices: " + bool(role.canSetPrices),
                    "§7Bank Access: " + bool(role.canAccessBank)
            ));
        }

        // Create custom role button (owner only)
        CompanyInfo info = plugin.getCompanyManager().getCompanyById(companyId);
        if (info != null && info.ownerUuid.equals(player.getUniqueId())) {
            ItemStack createBtn = createItem(Material.LIME_DYE, "§a§lCreate Custom Role",
                    "§7Define a new role with custom permissions.",
                    "§7Type the role name in chat to start.");
            ItemMeta meta = createBtn.getItemMeta();
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "company_id"), PersistentDataType.INTEGER, companyId);
            createBtn.setItemMeta(meta);
            gui.setItem(49, createBtn);
        }

        gui.setItem(45, createItem(Material.ARROW, "§fBack", "§7Return to company menu."));
        player.openInventory(gui);
    }

    // ---- Click Handler ----

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        if (!title.equals(COMPANY_MENU_TITLE) && !title.equals(COMPANY_MEMBERS_TITLE) &&
                !title.equals(COMPANY_BANK_TITLE) && !title.equals(COMPANY_ROLES_TITLE) &&
                !title.equals(COMPANY_SELL_TITLE)) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // ---- Company main menu ----
        if (title.equals(COMPANY_MENU_TITLE)) {
            if (clicked.getType() == Material.ARROW) {
                // Return to computer main menu — re-open via ComputerListener
                player.closeInventory();
                return;
            }

            // Retrieve company ID stored in inventory
            int companyId = getCompanyIdFromInventory(event.getInventory(), plugin);
            if (companyId == -1) return;

            switch (clicked.getType()) {
                case PLAYER_HEAD -> openMembersMenu(player, companyId);
                case GOLD_INGOT -> openBankMenu(player, companyId);
                case WRITABLE_BOOK -> openRolesMenu(player, companyId);
                case BARRIER -> openSellMenu(player, companyId);
            }
            return;
        }

        // ---- Members menu ----
        if (title.equals(COMPANY_MEMBERS_TITLE)) {
            if (clicked.getType() == Material.ARROW) {
                CompanyInfo info = plugin.getCompanyManager().getCompanyForPlayer(player.getUniqueId());
                if (info != null) openCompanyMenu(player);
                return;
            }

            if (clicked.getType() == Material.LIME_DYE) {
                // Hire button
                if (!clicked.hasItemMeta()) return;
                int companyId = getIntNBT(clicked, "company_id");
                if (companyId == -1) return;
                player.closeInventory();
                player.sendMessage("§b§lHire Employee §7— Type the player's name, or §fcancel§7.");
                awaitingInput.put(player.getUniqueId(), new ChatInputSession(ChatInputType.HIRE, companyId, null));
                return;
            }

            // Member head — left click = set salary, right click = fire
            if (clicked.getType() == Material.PLAYER_HEAD && clicked.hasItemMeta()) {
                String uuidStr = getStringNBT(clicked, "member_uuid");
                int companyId = getIntNBT(clicked, "company_id");
                if (uuidStr == null || companyId == -1) return;

                UUID targetUuid = UUID.fromString(uuidStr);
                CompanyInfo info = plugin.getCompanyManager().getCompanyById(companyId);
                if (info == null) return;

                boolean canManage = info.ownerUuid.equals(player.getUniqueId()) ||
                        plugin.getCompanyManager().hasPermission(companyId, player.getUniqueId(), "hire_fire");
                if (!canManage) return;
                if (targetUuid.equals(info.ownerUuid)) return;

                if (event.isRightClick()) {
                    // Fire
                    plugin.getCompanyManager().removeMember(companyId, targetUuid);
                    String firedName = plugin.getServer().getOfflinePlayer(targetUuid).getName();
                    player.sendMessage("§7Fired §b" + firedName + " §7from the company.");
                    Player firedPlayer = plugin.getServer().getPlayer(targetUuid);
                    if (firedPlayer != null) {
                        firedPlayer.sendMessage("§4§l[Company] §4You have been fired from §f" + info.name + "§4.");
                    }
                    openMembersMenu(player, companyId);
                } else {
                    // Set salary
                    player.closeInventory();
                    String targetName = plugin.getServer().getOfflinePlayer(targetUuid).getName();
                    player.sendMessage("§b§lSet Salary §7— Type the daily salary for §b" + targetName + "§7, or §fcancel§7.");
                    awaitingInput.put(player.getUniqueId(), new ChatInputSession(ChatInputType.SET_SALARY, companyId, targetUuid));
                }
            }
            return;
        }

        // ---- Bank menu ----
        if (title.equals(COMPANY_BANK_TITLE)) {
            if (clicked.getType() == Material.ARROW) {
                CompanyInfo info = plugin.getCompanyManager().getCompanyForPlayer(player.getUniqueId());
                if (info != null) openCompanyMenu(player);
                return;
            }

            if (!clicked.hasItemMeta()) return;
            String action = getStringNBT(clicked, "bank_action");
            int companyId = getIntNBT(clicked, "company_id");
            if (action == null || companyId == -1) return;

            player.closeInventory();
            if (action.equals("deposit")) {
                player.sendMessage("§b§lDeposit §7— Type the amount to deposit into the company, or §fcancel§7.");
                awaitingInput.put(player.getUniqueId(), new ChatInputSession(ChatInputType.BANK_DEPOSIT, companyId, null));
            } else if (action.equals("withdraw")) {
                player.sendMessage("§b§lWithdraw §7— Type the amount to withdraw to your personal bank, or §fcancel§7.");
                awaitingInput.put(player.getUniqueId(), new ChatInputSession(ChatInputType.BANK_WITHDRAW, companyId, null));
            }
            return;
        }

        // ---- Roles menu ----
        if (title.equals(COMPANY_ROLES_TITLE)) {
            if (clicked.getType() == Material.ARROW) {
                CompanyInfo info = plugin.getCompanyManager().getCompanyForPlayer(player.getUniqueId());
                if (info != null) openCompanyMenu(player);
                return;
            }

            if (clicked.getType() == Material.LIME_DYE) {
                int companyId = getIntNBT(clicked, "company_id");
                if (companyId == -1) return;
                player.closeInventory();
                player.sendMessage("§b§lCreate Role §7— Type the new role name, or §fcancel§7.");
                awaitingInput.put(player.getUniqueId(), new ChatInputSession(ChatInputType.CREATE_ROLE_NAME, companyId, null));
            }
            return;
        }

        // ---- Sell menu ----
        if (title.equals(COMPANY_SELL_TITLE)) {
            if (clicked.getType() == Material.ARROW) {
                CompanyInfo info = plugin.getCompanyManager().getCompanyForPlayer(player.getUniqueId());
                if (info != null) openCompanyMenu(player);
                return;
            }
            if (clicked.getType() == Material.GOLD_INGOT) {
                int companyId = getIntNBT(clicked, "company_id");
                if (companyId == -1) return;
                player.closeInventory();
                player.sendMessage("§b§lSell Company §7— Type the buyer's name, or §fcancel§7.");
                awaitingInput.put(player.getUniqueId(), new ChatInputSession(ChatInputType.SELL_BUYER, companyId, null));
            }
        }
    }

    private void openSellMenu(Player player, int companyId) {
        CompanyInfo info = plugin.getCompanyManager().getCompanyById(companyId);
        if (info == null || !info.ownerUuid.equals(player.getUniqueId())) return;

        Inventory gui = plugin.getServer().createInventory(null, 27, COMPANY_SELL_TITLE);
        gui.setItem(4, createItem(Material.GOLD_INGOT, "§c§lSell " + info.name,
                "§7This will transfer ownership permanently.",
                "§7You will receive the agreed price from the buyer's bank.",
                "",
                "§eClick to enter buyer name."));
        ItemMeta meta = gui.getItem(4).getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "company_id"), PersistentDataType.INTEGER, companyId);
        gui.getItem(4).setItemMeta(meta);
        gui.setItem(18, createItem(Material.ARROW, "§fBack", "§7Return to company menu."));
        player.openInventory(gui);
    }

    // ---- Chat input handler ----

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!awaitingInput.containsKey(player.getUniqueId())) return;

        event.setCancelled(true);
        String input = event.getMessage().trim();

        if (input.equalsIgnoreCase("cancel")) {
            awaitingInput.remove(player.getUniqueId());
            player.sendMessage("§7Cancelled.");
            return;
        }

        ChatInputSession session = awaitingInput.get(player.getUniqueId());

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            switch (session.type) {

                case HIRE -> {
                    awaitingInput.remove(player.getUniqueId());
                    Player target = plugin.getServer().getPlayer(input);
                    if (target == null) { player.sendMessage("§4Player not online."); return; }
                    if (target.equals(player)) { player.sendMessage("§4You can't hire yourself."); return; }
                    if (plugin.getCompanyManager().isMember(session.companyId, target.getUniqueId())) {
                        player.sendMessage("§4That player is already in your company."); return;
                    }
                    plugin.getCompanyManager().addMember(session.companyId, target.getUniqueId(), "employee", 0);
                    CompanyInfo info = plugin.getCompanyManager().getCompanyById(session.companyId);
                    player.sendMessage("§b" + target.getName() + " §fhas been hired as an employee.");
                    target.sendMessage("§b§l[Company] §b" + player.getName() + " §fhas hired you at §b" +
                            (info != null ? info.name : "a company") + "§f!");
                }

                case SET_SALARY -> {
                    double salary;
                    try { salary = Double.parseDouble(input); }
                    catch (NumberFormatException e) { player.sendMessage("§4Invalid amount."); return; }
                    if (salary < 0) { player.sendMessage("§4Salary cannot be negative."); return; }
                    awaitingInput.remove(player.getUniqueId());
                    plugin.getCompanyManager().setSalary(session.companyId, session.targetUuid, salary);
                    String name = plugin.getServer().getOfflinePlayer(session.targetUuid).getName();
                    player.sendMessage("§bSalary §ffor §b" + name + " §fset to §b" + plugin.getEconomyManager().format(salary) + " §f/day.");
                    Player emp = plugin.getServer().getPlayer(session.targetUuid);
                    if (emp != null) {
                        emp.sendMessage("§b§l[Company] §fYour daily salary has been updated to §b" +
                                plugin.getEconomyManager().format(salary) + "§f.");
                    }
                }

                case BANK_DEPOSIT -> {
                    double amount;
                    try { amount = Double.parseDouble(input); }
                    catch (NumberFormatException e) { player.sendMessage("§4Invalid amount."); return; }
                    if (amount <= 0) { player.sendMessage("§4Amount must be positive."); return; }
                    double personal = plugin.getEconomyManager().getBankBalance(player.getUniqueId());
                    if (personal < amount) { player.sendMessage("§4Insufficient personal bank balance."); return; }

                    awaitingInput.remove(player.getUniqueId());
                    String deductSql = "UPDATE players SET bank_balance = bank_balance - ? WHERE uuid = ?";
                    try (java.sql.PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(deductSql)) {
                        stmt.setDouble(1, amount);
                        stmt.setString(2, player.getUniqueId().toString());
                        stmt.executeUpdate();
                    } catch (java.sql.SQLException e) {
                        player.sendMessage("§4Error processing deposit."); return;
                    }
                    plugin.getCompanyManager().depositToCompany(session.companyId, amount);
                    CompanyInfo info = plugin.getCompanyManager().getCompanyById(session.companyId);
                    player.sendMessage("§fDeposited §b" + plugin.getEconomyManager().format(amount) +
                            " §finto §b" + (info != null ? info.name : "company") + "§f.");
                }

                case BANK_WITHDRAW -> {
                    double amount;
                    try { amount = Double.parseDouble(input); }
                    catch (NumberFormatException e) { player.sendMessage("§4Invalid amount."); return; }
                    if (amount <= 0) { player.sendMessage("§4Amount must be positive."); return; }
                    awaitingInput.remove(player.getUniqueId());
                    boolean ok = plugin.getCompanyManager().withdrawFromCompany(session.companyId, player.getUniqueId(), amount);
                    if (!ok) { player.sendMessage("§4Insufficient company balance or no permission."); return; }
                    String addSql = "UPDATE players SET bank_balance = bank_balance + ? WHERE uuid = ?";
                    try (java.sql.PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(addSql)) {
                        stmt.setDouble(1, amount);
                        stmt.setString(2, player.getUniqueId().toString());
                        stmt.executeUpdate();
                    } catch (java.sql.SQLException e) {
                        plugin.getLogger().severe("Error adding withdrawal to player: " + e.getMessage());
                    }
                    player.sendMessage("§fWithdrew §b" + plugin.getEconomyManager().format(amount) + " §fto your personal bank.");
                }

                case CREATE_ROLE_NAME -> {
                    if (input.length() < 2 || input.length() > 24) {
                        player.sendMessage("§4Role name must be 2–24 characters."); return;
                    }
                    session.pendingString = input;
                    session.type = ChatInputType.CREATE_ROLE_PERMS;
                    player.sendMessage("§fRole name: §b" + input);
                    player.sendMessage("§7Enter permissions as: §fhire_fire,set_prices,access_bank");
                    player.sendMessage("§7Use §fnone §7for no permissions. Example: §fhire_fire,set_prices");
                }

                case CREATE_ROLE_PERMS -> {
                    awaitingInput.remove(player.getUniqueId());
                    boolean canHire = input.contains("hire_fire");
                    boolean canPrices = input.contains("set_prices");
                    boolean canBank = input.contains("access_bank");
                    plugin.getCompanyManager().createRole(session.companyId, session.pendingString, canHire, canPrices, canBank);
                    player.sendMessage("§fRole §b" + session.pendingString + " §fcreated.");
                    player.sendMessage("§7Permissions: hire_fire=" + canHire + ", set_prices=" + canPrices + ", access_bank=" + canBank);
                }

                case SELL_BUYER -> {
                    Player buyer = plugin.getServer().getPlayer(input);
                    if (buyer == null) { player.sendMessage("§4Player not online."); return; }
                    if (buyer.equals(player)) { player.sendMessage("§4You can't sell to yourself."); return; }
                    if (plugin.getCompanyManager().ownsCompany(buyer.getUniqueId())) {
                        player.sendMessage("§4That player already owns a company."); return;
                    }
                    session.targetUuid = buyer.getUniqueId();
                    session.type = ChatInputType.SELL_PRICE;
                    player.sendMessage("§fBuyer: §b" + buyer.getName());
                    player.sendMessage("§7Enter the §fsale price §7(taken from buyer's bank), or §fcancel§7:");
                }

                case SELL_PRICE -> {
                    double price;
                    try { price = Double.parseDouble(input); }
                    catch (NumberFormatException e) { player.sendMessage("§4Invalid price."); return; }
                    if (price < 0) { player.sendMessage("§4Price cannot be negative."); return; }
                    awaitingInput.remove(player.getUniqueId());

                    Player buyer = plugin.getServer().getPlayer(session.targetUuid);
                    if (buyer == null) { player.sendMessage("§4Buyer went offline. Cancelled."); return; }

                    boolean ok = plugin.getCompanyManager().sellCompany(session.companyId, session.targetUuid, price);
                    if (!ok) {
                        player.sendMessage("§4Sale failed. Buyer may not have enough funds.");
                        return;
                    }
                    CompanyInfo info = plugin.getCompanyManager().getCompanyById(session.companyId);
                    String companyName = info != null ? info.name : "the company";
                    player.sendMessage("§b" + companyName + " §fhas been sold to §b" + buyer.getName() +
                            " §ffor §b" + plugin.getEconomyManager().format(price) + "§f.");
                    buyer.sendMessage("§b§l[Company] §fYou are now the owner of §b" + companyName + "§f!");
                }
            }
        });
    }

    // ---- Helpers ----

    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    /** Stores company ID in slot 26 (bottom-right corner) of any GUI as a hidden marker. */
    private void storeCompanyId(Inventory gui, int companyId, MJB plugin) {
        ItemStack marker = new ItemStack(Material.GRAY_DYE, 1);
        ItemMeta meta = marker.getItemMeta();
        meta.setDisplayName("§0");
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "company_id"), PersistentDataType.INTEGER, companyId);
        marker.setItemMeta(meta);
        gui.setItem(26, marker);
    }

    private int getCompanyIdFromInventory(Inventory inv, MJB plugin) {
        ItemStack marker = inv.getItem(26);
        if (marker == null || !marker.hasItemMeta()) return -1;
        return getIntNBT(marker, "company_id");
    }

    private int getIntNBT(ItemStack item, String key) {
        if (!item.hasItemMeta()) return -1;
        NamespacedKey nk = new NamespacedKey(plugin, key);
        if (!item.getItemMeta().getPersistentDataContainer().has(nk, PersistentDataType.INTEGER)) return -1;
        return item.getItemMeta().getPersistentDataContainer().get(nk, PersistentDataType.INTEGER);
    }

    private String getStringNBT(ItemStack item, String key) {
        if (!item.hasItemMeta()) return null;
        NamespacedKey nk = new NamespacedKey(plugin, key);
        if (!item.getItemMeta().getPersistentDataContainer().has(nk, PersistentDataType.STRING)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(nk, PersistentDataType.STRING);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String bool(boolean b) {
        return b ? "§aYes" : "§7No";
    }

    // ---- Chat session ----

    private enum ChatInputType {
        HIRE, SET_SALARY, BANK_DEPOSIT, BANK_WITHDRAW,
        CREATE_ROLE_NAME, CREATE_ROLE_PERMS,
        SELL_BUYER, SELL_PRICE
    }

    private static class ChatInputSession {
        ChatInputType type;
        int companyId;
        UUID targetUuid;
        String pendingString;

        ChatInputSession(ChatInputType type, int companyId, UUID targetUuid) {
            this.type = type;
            this.companyId = companyId;
            this.targetUuid = targetUuid;
        }
    }
}