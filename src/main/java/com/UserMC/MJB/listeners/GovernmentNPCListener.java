package com.UserMC.MJB.listeners;

import com.UserMC.MJB.CompanyManager;
import com.UserMC.MJB.MJB;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GovernmentNPCListener implements Listener {

    private final MJB plugin;
    public static final String GOV_NPC_TAG = "government_office";

    // Registration wizard state
    private final Map<UUID, RegistrationSession> sessions = new HashMap<>();

    public GovernmentNPCListener(MJB plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onNPCRightClick(NPCRightClickEvent event) {
        NPC npc = event.getNPC();
        if (!npc.data().has(GOV_NPC_TAG)) return;

        Player player = event.getClicker();

        // Check if they already own a company
        if (plugin.getCompanyManager().ownsCompany(player.getUniqueId())) {
            CompanyManager.CompanyInfo info = plugin.getCompanyManager().getCompanyByOwner(player.getUniqueId());
            player.sendMessage("§b§m-----------------------------");
            player.sendMessage("§b§l  Government Office");
            player.sendMessage("§b§m-----------------------------");
            player.sendMessage("§fYou already own §b" + info.name + "§f.");
            player.sendMessage("§7Manage it from your computer terminal.");
            player.sendMessage("§b§m-----------------------------");
            return;
        }

        double fee = CompanyManager.REGISTRATION_FEE;
        double balance = plugin.getEconomyManager().getBankBalance(player.getUniqueId());

        player.sendMessage("§b§m-----------------------------");
        player.sendMessage("§b§l  Government Office");
        player.sendMessage("§b§m-----------------------------");
        player.sendMessage("§fWelcome! You can register a new company here.");
        player.sendMessage("§7Registration fee: §f" + plugin.getEconomyManager().format(fee));
        player.sendMessage("§7Your bank balance: §f" + plugin.getEconomyManager().format(balance));
        player.sendMessage("§b§m-----------------------------");

        if (balance < fee) {
            player.sendMessage("§4You don't have enough funds to register a company.");
            return;
        }

        player.sendMessage("§eTo register, type your §fcompany name §ein chat.");
        player.sendMessage("§7Type §fcancel §7at any time to stop.");
        player.sendMessage("§b§m-----------------------------");

        sessions.put(player.getUniqueId(), new RegistrationSession(RegistrationStep.NAME));
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!sessions.containsKey(player.getUniqueId())) return;

        event.setCancelled(true);
        String input = event.getMessage().trim();

        if (input.equalsIgnoreCase("cancel")) {
            sessions.remove(player.getUniqueId());
            player.sendMessage("§7Registration cancelled.");
            return;
        }

        RegistrationSession session = sessions.get(player.getUniqueId());

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            switch (session.step) {
                case NAME -> {
                    if (input.length() < 3 || input.length() > 32) {
                        player.sendMessage("§4Company name must be 3–32 characters.");
                        return;
                    }
                    if (!input.matches("[a-zA-Z0-9 _-]+")) {
                        player.sendMessage("§4Name can only contain letters, numbers, spaces, dashes, and underscores.");
                        return;
                    }
                    if (plugin.getCompanyManager().nameExists(input)) {
                        player.sendMessage("§4That company name is already taken.");
                        return;
                    }
                    session.name = input;
                    session.step = RegistrationStep.TYPE;
                    player.sendMessage("§fCompany name: §b" + input);
                    player.sendMessage("§eNow enter your §fcompany type §e(e.g. Bakery, Farm, Clothing, Delivery, Tech, Services):");
                }
                case TYPE -> {
                    if (input.length() < 2 || input.length() > 32) {
                        player.sendMessage("§4Company type must be 2–32 characters.");
                        return;
                    }
                    session.type = input;
                    session.step = RegistrationStep.DESCRIPTION;
                    player.sendMessage("§fType: §b" + input);
                    player.sendMessage("§eFinally, enter a short §fdescription §eof your business:");
                }
                case DESCRIPTION -> {
                    if (input.length() < 5 || input.length() > 128) {
                        player.sendMessage("§4Description must be 5–128 characters.");
                        return;
                    }
                    session.description = input;

                    // Final confirmation
                    player.sendMessage("§b§m-----------------------------");
                    player.sendMessage("§b§l  Confirm Registration");
                    player.sendMessage("§b§m-----------------------------");
                    player.sendMessage("§7Name: §f" + session.name);
                    player.sendMessage("§7Type: §f" + session.type);
                    player.sendMessage("§7About: §f" + session.description);
                    player.sendMessage("§7Fee: §f" + plugin.getEconomyManager().format(CompanyManager.REGISTRATION_FEE));
                    player.sendMessage("§b§m-----------------------------");
                    player.sendMessage("§eType §fconfirm §eto register, or §fcancel §eto abort.");
                    session.step = RegistrationStep.CONFIRM;
                }
                case CONFIRM -> {
                    if (!input.equalsIgnoreCase("confirm")) {
                        player.sendMessage("§7Type §fconfirm §7or §fcancel§7.");
                        return;
                    }

                    sessions.remove(player.getUniqueId());
                    int result = plugin.getCompanyManager().registerCompany(
                            player, session.name, session.type, session.description
                    );

                    switch (result) {
                        case -2 -> player.sendMessage("§4Insufficient bank balance.");
                        case -3 -> player.sendMessage("§4That company name was just taken — try a different name.");
                        case -4 -> player.sendMessage("§4You already own a company.");
                        case -1 -> player.sendMessage("§4An error occurred. Please try again.");
                        default -> {
                            player.sendMessage("§b§m-----------------------------");
                            player.sendMessage("§b§l  Company Registered!");
                            player.sendMessage("§b§m-----------------------------");
                            player.sendMessage("§fWelcome to the business world, §b" + session.name + "§f!");
                            player.sendMessage("§7Hire employees and manage everything from your computer.");
                            player.sendMessage("§7Deposit funds into your company account to pay salaries.");
                            player.sendMessage("§b§m-----------------------------");
                        }
                    }
                }
            }
        });
    }

    // ---- Registration wizard ----

    private enum RegistrationStep { NAME, TYPE, DESCRIPTION, CONFIRM }

    private static class RegistrationSession {
        RegistrationStep step;
        String name, type, description;

        RegistrationSession(RegistrationStep step) {
            this.step = step;
        }
    }
}