package com.UserMC.MJB.listeners;

import com.UserMC.MJB.CompanyManager;
import com.UserMC.MJB.LicenseManager;
import com.UserMC.MJB.LicenseManager.LicenseType;
import com.UserMC.MJB.LicenseManager.PlayerLicense;
import com.UserMC.MJB.MJB;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.text.SimpleDateFormat;
import java.util.*;

public class GovernmentNPCListener implements Listener {

    private final MJB plugin;
    public static final String GOV_NPC_TAG = "government_office";

    private static final String GOV_MENU_TITLE    = "§b§lGovernment Office";
    private static final String LICENSE_GUI_TITLE = "§b§lLicenses";
    private static final String MY_LIC_GUI_TITLE  = "§b§lMy Licenses";

    private final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
    private final Map<UUID, RegistrationSession> regSessions = new HashMap<>();

    public GovernmentNPCListener(MJB plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onNPCRightClick(NPCRightClickEvent event) {
        NPC npc = event.getNPC();
        if (!npc.data().has(GOV_NPC_TAG)) return;
        openGovMenu(event.getClicker());
    }

    private void openGovMenu(Player player) {
        Inventory gui = plugin.getServer().createInventory(null, 27, GOV_MENU_TITLE);
        gui.setItem(11, createItem(Material.GOLD_BLOCK, "§f§lRegister a Company",
                "§7Start your own business.",
                "§7Fee: §f" + plugin.getEconomyManager().format(CompanyManager.REGISTRATION_FEE)));
        gui.setItem(13, createItem(Material.PAPER, "§f§lLicenses",
                "§7Browse and purchase licenses.",
                "§7Required to operate certain businesses."));
        gui.setItem(15, createItem(Material.BOOK, "§f§lMy Licenses",
                "§7View and renew your licenses."));
        gui.setItem(22, createItem(Material.BARRIER, "§4Close", "§7Close this menu."));
        player.openInventory(gui);
    }

    private void openLicenseMenu(Player player) {
        List<LicenseType> types = plugin.getLicenseManager().getAllLicenseTypes();
        Inventory gui = plugin.getServer().createInventory(null, 54, LICENSE_GUI_TITLE);

        int slot = 0;
        for (LicenseType type : types) {
            if (slot >= 45) break;

            PlayerLicense existing = plugin.getLicenseManager().getLicense(player.getUniqueId(), type.typeName);
            boolean hasActive = plugin.getLicenseManager().hasActiveLicense(player.getUniqueId(), type.typeName);
            boolean inWarning = plugin.getLicenseManager().isInWarningPeriod(player.getUniqueId(), type.typeName);

            String statusLine;
            String actionLine;
            Material mat;

            if (existing == null) {
                mat = Material.PAPER;
                statusLine = "§7Status: §cNot owned";
                actionLine = "§eClick §7to purchase for §f" + plugin.getEconomyManager().format(type.cost);
            } else if (existing.isRevoked) {
                mat = Material.BARRIER;
                statusLine = "§7Status: §4Revoked";
                actionLine = "§cContact an admin or judge to appeal.";
            } else if (inWarning) {
                mat = Material.CLOCK;
                statusLine = "§7Status: §eExpired (grace period)";
                actionLine = "§eClick §7to renew for §f" + plugin.getEconomyManager().format(type.renewalCost);
            } else if (hasActive) {
                mat = Material.EMERALD;
                statusLine = "§7Status: §aActive — expires §f" + sdf.format(existing.expiresAt);
                actionLine = "§eClick §7to renew early for §f" + plugin.getEconomyManager().format(type.renewalCost);
            } else {
                mat = Material.REDSTONE;
                statusLine = "§7Status: §4Expired";
                actionLine = "§eClick §7to purchase again for §f" + plugin.getEconomyManager().format(type.cost);
            }

            ItemStack item = createItem(mat, "§b§l" + type.displayName,
                    "§7" + type.description, "", statusLine, actionLine);
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "license_type"), PersistentDataType.STRING, type.typeName);
            item.setItemMeta(meta);
            gui.setItem(slot++, item);
        }

        gui.setItem(45, createItem(Material.ARROW, "§fBack", "§7Return to government office."));
        player.openInventory(gui);
    }

    private void openMyLicensesMenu(Player player) {
        List<PlayerLicense> licenses = plugin.getLicenseManager().getPlayerLicenses(player.getUniqueId());
        Inventory gui = plugin.getServer().createInventory(null, 54, MY_LIC_GUI_TITLE);

        if (licenses.isEmpty()) {
            gui.setItem(22, createItem(Material.BARRIER, "§7No Licenses",
                    "§7You don't own any licenses yet.",
                    "§7Browse available licenses to get started."));
        }

        int slot = 0;
        for (PlayerLicense license : licenses) {
            if (slot >= 45) break;

            LicenseType type = plugin.getLicenseManager().getLicenseType(license.licenseType);
            String displayName = type != null ? type.displayName : license.licenseType;
            boolean hasActive = plugin.getLicenseManager().hasActiveLicense(player.getUniqueId(), license.licenseType);
            boolean inWarning = plugin.getLicenseManager().isInWarningPeriod(player.getUniqueId(), license.licenseType);

            Material mat;
            String statusLine;
            String actionLine;

            if (license.isRevoked) {
                mat = Material.BARRIER;
                statusLine = "§4Revoked";
                actionLine = "§cContact an admin or judge to appeal.";
            } else if (inWarning) {
                mat = Material.CLOCK;
                statusLine = "§eExpired — grace period active";
                actionLine = type != null
                        ? "§eClick §7to renew for §f" + plugin.getEconomyManager().format(type.renewalCost)
                        : "§eRenew from the licenses menu.";
            } else if (hasActive) {
                mat = Material.EMERALD;
                statusLine = "§aActive";
                actionLine = type != null
                        ? "§eClick §7to renew early for §f" + plugin.getEconomyManager().format(type.renewalCost)
                        : "";
            } else {
                mat = Material.REDSTONE;
                statusLine = "§4Expired";
                actionLine = "§eRepurchase from the licenses menu.";
            }

            ItemStack item = createItem(mat, "§b§l" + displayName,
                    "§7Issued: §f" + sdf.format(license.issuedAt),
                    "§7Expires: §f" + sdf.format(license.expiresAt),
                    "§7Status: " + statusLine, "", actionLine);
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "license_type"), PersistentDataType.STRING, license.licenseType);
            item.setItemMeta(meta);
            gui.setItem(slot++, item);
        }

        gui.setItem(45, createItem(Material.ARROW, "§fBack", "§7Return to government office."));
        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        if (!title.equals(GOV_MENU_TITLE) && !title.equals(LICENSE_GUI_TITLE) &&
                !title.equals(MY_LIC_GUI_TITLE)) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (title.equals(GOV_MENU_TITLE)) {
            switch (clicked.getType()) {
                case GOLD_BLOCK -> { player.closeInventory(); startCompanyRegistration(player); }
                case PAPER      -> openLicenseMenu(player);
                case BOOK       -> openMyLicensesMenu(player);
                case BARRIER    -> player.closeInventory();
            }
            return;
        }

        if (title.equals(LICENSE_GUI_TITLE)) {
            if (clicked.getType() == Material.ARROW) { openGovMenu(player); return; }
            if (!clicked.hasItemMeta()) return;
            NamespacedKey key = new NamespacedKey(plugin, "license_type");
            if (!clicked.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.STRING)) return;
            String licType = clicked.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
            handleLicensePurchaseOrRenew(player, licType);
            openLicenseMenu(player);
            return;
        }

        if (title.equals(MY_LIC_GUI_TITLE)) {
            if (clicked.getType() == Material.ARROW) { openGovMenu(player); return; }
            if (!clicked.hasItemMeta()) return;
            NamespacedKey key = new NamespacedKey(plugin, "license_type");
            if (!clicked.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.STRING)) return;
            String licType = clicked.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
            handleLicensePurchaseOrRenew(player, licType);
            openMyLicensesMenu(player);
        }
    }

    private void handleLicensePurchaseOrRenew(Player player, String licType) {
        LicenseType type = plugin.getLicenseManager().getLicenseType(licType);
        if (type == null) { player.sendMessage("§4License type not found."); return; }

        PlayerLicense existing = plugin.getLicenseManager().getLicense(player.getUniqueId(), licType);

        if (existing != null && existing.isRevoked) {
            player.sendMessage("§4Your §f" + type.displayName + " §4license has been revoked.");
            player.sendMessage("§7Contact an admin or judge to appeal.");
            return;
        }

        if (existing != null) {
            // Renew
            boolean ok = plugin.getLicenseManager().renewLicense(player, licType);
            if (ok) {
                player.sendMessage("§b§l[License] §fYour §b" + type.displayName + " §flicense has been renewed for 30 days!");
                player.sendMessage("§7Renewal cost: §f" + plugin.getEconomyManager().format(type.renewalCost) + " §7deducted.");
            } else {
                player.sendMessage("§4Renewal failed — insufficient bank balance.");
                player.sendMessage("§7Renewal cost: §f" + plugin.getEconomyManager().format(type.renewalCost));
            }
            return;
        }

        // Fresh purchase
        boolean ok = plugin.getLicenseManager().issueLicense(player, licType);
        if (ok) {
            player.sendMessage("§b§l[License] §fYou purchased the §b" + type.displayName + " §flicense!");
            player.sendMessage("§7Cost: §f" + plugin.getEconomyManager().format(type.cost) + " §7deducted.");
            player.sendMessage("§7Valid for §f30 days§7. Renew before it expires!");
        } else {
            double balance = plugin.getEconomyManager().getBankBalance(player.getUniqueId());
            if (balance < type.cost) {
                player.sendMessage("§4Insufficient bank balance.");
                player.sendMessage("§7Cost: §f" + plugin.getEconomyManager().format(type.cost) +
                        " §7| Balance: §f" + plugin.getEconomyManager().format(balance));
            } else {
                player.sendMessage("§4You already have an active license of this type.");
            }
        }
    }

    private void startCompanyRegistration(Player player) {
        if (plugin.getCompanyManager().ownsCompany(player.getUniqueId())) {
            CompanyManager.CompanyInfo info = plugin.getCompanyManager().getCompanyByOwner(player.getUniqueId());
            player.sendMessage("§4You already own §b" + info.name + "§4. Manage it from your computer.");
            return;
        }
        double fee = CompanyManager.REGISTRATION_FEE;
        double balance = plugin.getEconomyManager().getBankBalance(player.getUniqueId());
        player.sendMessage("§b§m-----------------------------");
        player.sendMessage("§b§l  Company Registration");
        player.sendMessage("§b§m-----------------------------");
        player.sendMessage("§7Fee: §f" + plugin.getEconomyManager().format(fee));
        player.sendMessage("§7Your balance: §f" + plugin.getEconomyManager().format(balance));
        if (balance < fee) {
            player.sendMessage("§4Insufficient funds.");
            player.sendMessage("§b§m-----------------------------");
            return;
        }
        player.sendMessage("§eType your §fcompany name §ein chat, or §fcancel§e.");
        player.sendMessage("§b§m-----------------------------");
        regSessions.put(player.getUniqueId(), new RegistrationSession(RegistrationStep.NAME));
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!regSessions.containsKey(player.getUniqueId())) return;

        event.setCancelled(true);
        String input = event.getMessage().trim();

        if (input.equalsIgnoreCase("cancel")) {
            regSessions.remove(player.getUniqueId());
            player.sendMessage("§7Registration cancelled.");
            return;
        }

        RegistrationSession session = regSessions.get(player.getUniqueId());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            switch (session.step) {
                case NAME -> {
                    if (input.length() < 3 || input.length() > 32) { player.sendMessage("§43–32 characters."); return; }
                    if (!input.matches("[a-zA-Z0-9 _-]+")) { player.sendMessage("§4Letters, numbers, spaces, dashes, underscores only."); return; }
                    if (plugin.getCompanyManager().nameExists(input)) { player.sendMessage("§4Name already taken."); return; }
                    session.name = input; session.step = RegistrationStep.TYPE;
                    player.sendMessage("§fName: §b" + input);
                    player.sendMessage("§eEnter company §ftype §e(e.g. Bakery, Farm, Clothing):");
                }
                case TYPE -> {
                    if (input.length() < 2 || input.length() > 32) { player.sendMessage("§42–32 characters."); return; }
                    session.type = input; session.step = RegistrationStep.DESCRIPTION;
                    player.sendMessage("§fType: §b" + input);
                    player.sendMessage("§eEnter a short §fdescription §eof your business:");
                }
                case DESCRIPTION -> {
                    if (input.length() < 5 || input.length() > 128) { player.sendMessage("§45–128 characters."); return; }
                    session.description = input;
                    player.sendMessage("§b§m-----------------------------");
                    player.sendMessage("§7Name: §f" + session.name + " §7| Type: §f" + session.type);
                    player.sendMessage("§7About: §f" + session.description);
                    player.sendMessage("§7Fee: §f" + plugin.getEconomyManager().format(CompanyManager.REGISTRATION_FEE));
                    player.sendMessage("§eType §fconfirm §eor §fcancel§e.");
                    player.sendMessage("§b§m-----------------------------");
                    session.step = RegistrationStep.CONFIRM;
                }
                case CONFIRM -> {
                    if (!input.equalsIgnoreCase("confirm")) { player.sendMessage("§7Type §fconfirm §7or §fcancel§7."); return; }
                    regSessions.remove(player.getUniqueId());
                    int result = plugin.getCompanyManager().registerCompany(player, session.name, session.type, session.description);
                    switch (result) {
                        case -2 -> player.sendMessage("§4Insufficient bank balance.");
                        case -3 -> player.sendMessage("§4Name just taken — try another.");
                        case -4 -> player.sendMessage("§4You already own a company.");
                        case -1 -> player.sendMessage("§4An error occurred.");
                        default -> {
                            player.sendMessage("§b§l[Company] §b" + session.name + " §fhas been registered!");
                            player.sendMessage("§7Manage it from your computer terminal.");
                        }
                    }
                }
            }
        });
    }

    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    private enum RegistrationStep { NAME, TYPE, DESCRIPTION, CONFIRM }
    private static class RegistrationSession {
        RegistrationStep step; String name, type, description;
        RegistrationSession(RegistrationStep step) { this.step = step; }
    }
}