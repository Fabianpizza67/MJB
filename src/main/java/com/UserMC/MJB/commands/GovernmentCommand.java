package com.UserMC.MJB.commands;

import com.UserMC.MJB.GovernmentManager;
import com.UserMC.MJB.GovernmentManager.*;
import com.UserMC.MJB.MJB;
import com.UserMC.MJB.listeners.CouncilListener;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class GovernmentCommand implements CommandExecutor, TabCompleter {

    private final MJB plugin;
    private final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    // Multi-step party creation sessions
    private final Map<UUID, PartyCreateSession> createSessions = new HashMap<>();

    public GovernmentCommand(MJB plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }

        String cmd = label.toLowerCase();

        // ---- /laws ----
        if (cmd.equals("laws")) {
            List<Law> laws = plugin.getGovernmentManager().getActiveLaws();
            if (laws.isEmpty()) { player.sendMessage("§7No laws are currently in effect."); return true; }
            player.sendMessage("§9§l--- Active Laws ---");
            for (Law law : laws) {
                player.sendMessage("§f[" + law.id + "] §b" + law.title);
                if (!law.lawType.equals(GovernmentManager.LAW_CUSTOM)) {
                    player.sendMessage("§7  Type: §f" + law.lawType + " §7| Value: §f" + law.lawValue);
                }
                player.sendMessage("§7  Passed: §f" + sdf.format(law.passedAt));
            }
            return true;
        }

        // ---- /mayor ----
        if (cmd.equals("mayor")) {
            UUID mayorUuid = plugin.getGovernmentManager().getMayorUuid();
            if (mayorUuid == null) { player.sendMessage("§7No mayor is currently in office."); return true; }
            String name = plugin.getServer().getOfflinePlayer(mayorUuid).getName();
            PartyInfo party = plugin.getGovernmentManager().getPartyByMember(mayorUuid);
            player.sendMessage("§6§l--- Current Mayor ---");
            player.sendMessage("§7Mayor: §f" + name);
            if (party != null) player.sendMessage("§7Party: §f" + party.name);
            // Mayor-only sub-commands
            if (args.length >= 1 && plugin.getGovernmentManager().isMayor(player.getUniqueId())) {
                switch (args[0].toLowerCase()) {
                    case "treasury" -> {
                        double bal = plugin.getPropertyManager().getTreasuryBalance();
                        player.sendMessage("§6§lCity Treasury: §f" + plugin.getEconomyManager().format(bal));
                    }
                }
            }
            return true;
        }

        // ---- /propose <text> ----
        if (cmd.equals("propose")) {
            if (args.length == 0) {
                player.sendMessage("§4Usage: /propose <your proposal text>");
                player.sendMessage("§7Example: §f/propose Make guns legal");
                player.sendMessage("§7Example: §f/propose Set tax rate to 10%");
                return true;
            }
            if (!plugin.getGovernmentManager().isSessionActive()) {
                player.sendMessage("§4The council is not in session right now.");
                player.sendMessage("§7Sessions: Wed 16:00 CET | Sat 13:00 CET | Sun 13:00 CET");
                return true;
            }
            if (!plugin.getGovernmentManager().hasSeat(player.getUniqueId())) {
                player.sendMessage("§4You do not hold a council seat.");
                return true;
            }
            if (!plugin.getGovernmentManager().isInCouncilRegion(player)) {
                player.sendMessage("§4You must be in the council chamber to propose a law.");
                return true;
            }


            String text = String.join(" ", args);

            // Auto-detect law type from text
            String lawType = GovernmentManager.LAW_CUSTOM;
            String lawValue = "true";
            String lowerText = text.toLowerCase();

            if (lowerText.contains("gun") || lowerText.contains("weapon")) {
                lawType = GovernmentManager.LAW_GUNS_LEGAL;
                lawValue = lowerText.contains("illegal") ? "false" : "true";
            } else if (lowerText.contains("tax") || lowerText.contains("belasting")) {
                lawType = GovernmentManager.LAW_TAX_RATE;
                // Try to extract number
                for (String word : args) {
                    try { lawValue = String.valueOf(Double.parseDouble(word.replace("%", ""))); break; }
                    catch (NumberFormatException ignored) { }
                }
            } else if (lowerText.contains("police") && lowerText.contains("defund")) {
                lawType = GovernmentManager.LAW_POLICE_DEFUNDED;
                lawValue = "true";
            } else if (lowerText.startsWith("pardon ")) {
                lawType = GovernmentManager.LAW_PARDON;
                lawValue = text.substring(7).trim();
            } else if (lowerText.contains("repeal")) {
                lawType = GovernmentManager.LAW_REPEAL;
                for (String word : args) {
                    String clean = word.replace("#", "");
                    try { lawValue = String.valueOf(Integer.parseInt(clean)); break; }
                    catch (NumberFormatException ignored) { }
                }
            } else if (lowerText.contains("police") && (lowerText.contains("fund") || lowerText.contains("give"))) {
                    lawType = GovernmentManager.LAW_POLICE_FUND;
                    for (String word : args) {
                        try { lawValue = String.valueOf(Double.parseDouble(word)); break; }
                        catch (NumberFormatException ignored) { }
                    }
            } else if (lowerText.contains("police") && lowerText.contains("weekly")) {
                    lawType = GovernmentManager.LAW_POLICE_WEEKLY;
                    for (String word : args) {
                        try { lawValue = String.valueOf(Double.parseDouble(word)); break; }
                        catch (NumberFormatException ignored) { }
                    }
                }

            int proposalId = plugin.getGovernmentManager().submitProposal(
                    player.getUniqueId(), text, lawType, lawValue);
            if (proposalId == -1) {
                player.sendMessage("§4Failed to submit proposal.");
                return true;
            }

            // Notify council and open voting GUIs
            List<Proposal> open = plugin.getGovernmentManager().getOpenProposals();
            for (Proposal p : open) {
                if (p.id == proposalId) {
                    plugin.getCouncilListener().broadcastProposal(player, p);
                    break;
                }
            }

            player.sendMessage("§9§l[Council] §fYour proposal has been submitted: §b\"" + text + "\"");
            return true;
        }

        // ---- /party ----
        if (cmd.equals("party")) {
            if (args.length == 0) { sendPartyHelp(player); return true; }
            switch (args[0].toLowerCase()) {

                case "create" -> {
                    if (args.length < 3) {
                        player.sendMessage("§4Usage: /party create <name> <description...>");
                        return true;
                    }
                    String name = args[1];
                    StringBuilder desc = new StringBuilder();
                    for (int i = 2; i < args.length; i++) desc.append(args[i]).append(" ");
                    int result = plugin.getGovernmentManager().createParty(
                            player.getUniqueId(), name, desc.toString().trim());
                    switch (result) {
                        case -2 -> player.sendMessage("§4Party name already taken.");
                        case -3 -> player.sendMessage("§4You are already in a party. Leave it first.");
                        case -1 -> player.sendMessage("§4An error occurred.");
                        default -> {
                            player.sendMessage("§b§l[Party] §fParty §b" + name + " §ffounded!");
                            player.sendMessage("§7Invite others with §f/party invite <player>§7.");
                        }
                    }
                }

                case "disband" -> {
                    PartyInfo party = plugin.getGovernmentManager().getPartyByMember(player.getUniqueId());
                    if (party == null) { player.sendMessage("§4You are not in a party."); return true; }
                    if (!party.leaderUuid.equals(player.getUniqueId())) {
                        player.sendMessage("§4Only the party leader can disband the party.");
                        return true;
                    }
                    plugin.getGovernmentManager().disbandParty(party.id, player.getUniqueId());
                    player.sendMessage("§7Party §b" + party.name + " §7has been disbanded.");
                }

                case "invite" -> {
                    if (args.length < 2) { player.sendMessage("§4Usage: /party invite <player>"); return true; }
                    PartyInfo party = plugin.getGovernmentManager().getPartyByMember(player.getUniqueId());
                    if (party == null) { player.sendMessage("§4You are not in a party."); return true; }
                    if (!party.leaderUuid.equals(player.getUniqueId())) {
                        player.sendMessage("§4Only the party leader can invite members.");
                        return true;
                    }
                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) { player.sendMessage("§4Player not online."); return true; }
                    if (plugin.getGovernmentManager().getPartyByMember(target.getUniqueId()) != null) {
                        player.sendMessage("§4That player is already in a party.");
                        return true;
                    }
                    // Send invite instead of auto-joining
                    plugin.getGovernmentManager().sendPartyInvite(target.getUniqueId(), party.id);
                    player.sendMessage("§fInvite sent to §b" + target.getName() + "§f.");
                    target.sendMessage("§9§l[Party] §b" + player.getName() + " §fhas invited you to join §b" + party.name + "§f!");
                    target.sendMessage("§7Type §f/party accept §7or §f/party decline §7to respond. Expires in 5 minutes.");
                }


                case "leave" -> {
                    PartyInfo party = plugin.getGovernmentManager().getPartyByMember(player.getUniqueId());
                    if (party == null) { player.sendMessage("§4You are not in a party."); return true; }
                    if (party.leaderUuid.equals(player.getUniqueId())) {
                        player.sendMessage("§4Party leaders cannot leave. Use §f/party disband §4instead.");
                        return true;
                    }
                    plugin.getGovernmentManager().removePartyMember(party.id, player.getUniqueId());
                    player.sendMessage("§7You have left §b" + party.name + "§7.");
                }

                case "kick" -> {
                    if (args.length < 2) { player.sendMessage("§4Usage: /party kick <player>"); return true; }
                    PartyInfo party = plugin.getGovernmentManager().getPartyByMember(player.getUniqueId());
                    if (party == null || !party.leaderUuid.equals(player.getUniqueId())) {
                        player.sendMessage("§4Only the party leader can kick members.");
                        return true;
                    }
                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) { player.sendMessage("§4Player not online."); return true; }
                    boolean ok = plugin.getGovernmentManager().removePartyMember(party.id, target.getUniqueId());
                    if (ok) {
                        player.sendMessage("§f" + target.getName() + " §fhas been removed from the party.");
                        target.sendMessage("§7You have been removed from §b" + party.name + "§7.");
                    } else {
                        player.sendMessage("§4Failed — cannot kick the party leader.");
                    }
                }

                case "info" -> {
                    PartyInfo party = args.length >= 2
                            ? plugin.getGovernmentManager().getPartyByName(args[1])
                            : plugin.getGovernmentManager().getPartyByMember(player.getUniqueId());
                    if (party == null) { player.sendMessage("§4Party not found."); return true; }
                    String leaderName = plugin.getServer().getOfflinePlayer(party.leaderUuid).getName();
                    int seats = plugin.getGovernmentManager().getSeatsForParty(party.id);
                    List<UUID> members = plugin.getGovernmentManager().getPartyMembers(party.id);
                    player.sendMessage("§9§m-----------------------------");
                    player.sendMessage("§9§l " + party.name);
                    player.sendMessage("§9§m-----------------------------");
                    player.sendMessage("§7Leader: §f" + leaderName);
                    player.sendMessage("§7Seats: §f" + seats);
                    player.sendMessage("§7About: §f" + party.description);
                    player.sendMessage("§7Members (" + members.size() + "):");
                    for (UUID uuid : members) {
                        String mName = plugin.getServer().getOfflinePlayer(uuid).getName();
                        player.sendMessage("§7  - §f" + mName +
                                (uuid.equals(party.leaderUuid) ? " §6(Leader)" : ""));
                    }
                }

                case "list" -> {
                    List<PartyInfo> parties = plugin.getGovernmentManager().getAllParties();
                    if (parties.isEmpty()) { player.sendMessage("§7No parties registered."); return true; }
                    player.sendMessage("§9§l--- Parties ---");
                    for (PartyInfo p : parties) {
                        int seats = plugin.getGovernmentManager().getSeatsForParty(p.id);
                        player.sendMessage("§f" + p.name + " §7(" + seats + " seats, " +
                                plugin.getGovernmentManager().getPartyMemberCount(p.id) + " members)");
                    }
                }

                case "accept" -> {
                    Integer partyId = plugin.getGovernmentManager().getPendingInvite(player.getUniqueId());
                    if (partyId == null) {
                        player.sendMessage("§4You don't have a pending party invite.");
                        return true;
                    }
                    plugin.getGovernmentManager().removePendingInvite(player.getUniqueId());
                    PartyInfo party = plugin.getGovernmentManager().getPartyById(partyId);
                    plugin.getGovernmentManager().addPartyMember(partyId, player.getUniqueId());
                    player.sendMessage("§b§l[Party] §fYou joined §b" + (party != null ? party.name : "the party") + "§f!");
                    // Notify leader
                    if (party != null) {
                        Player leader = plugin.getServer().getPlayer(party.leaderUuid);
                        if (leader != null) leader.sendMessage("§b§l[Party] §b" + player.getName() + " §fjoined the party!");
                    }
                }

                case "decline" -> {
                    Integer partyId = plugin.getGovernmentManager().getPendingInvite(player.getUniqueId());
                    if (partyId == null) {
                        player.sendMessage("§4You don't have a pending party invite.");
                        return true;
                    }
                    plugin.getGovernmentManager().removePendingInvite(player.getUniqueId());
                    PartyInfo party = plugin.getGovernmentManager().getPartyById(partyId);
                    player.sendMessage("§7You declined the party invite.");
                    if (party != null) {
                        Player leader = plugin.getServer().getPlayer(party.leaderUuid);
                        if (leader != null) leader.sendMessage("§7§b" + player.getName() + " §7declined your party invite.");
                    }
                }

                default -> sendPartyHelp(player);
            }
            return true;
        }

        // ---- /government ----
        if (cmd.equals("government") || cmd.equals("gov")) {
            player.sendMessage("§9§l--- Government Status ---");
            UUID mayor = plugin.getGovernmentManager().getMayorUuid();
            player.sendMessage("§7Mayor: §f" + (mayor != null
                    ? plugin.getServer().getOfflinePlayer(mayor).getName() : "None"));
            player.sendMessage("§7Tax Rate: §f" + plugin.getGovernmentManager().getTaxRate() + "%");
            player.sendMessage("§7Guns Legal: §f" + (plugin.getGovernmentManager().areGunsLegal() ? "§aYes" : "§cNo"));
            player.sendMessage("§7Session Active: §f" + (plugin.getGovernmentManager().isSessionActive() ? "§aYes" : "§7No"));
            player.sendMessage("§7Election Active: §f" + (plugin.getGovernmentManager().isElectionActive() ? "§aYes" : "§7No"));
            player.sendMessage("§7Total Seats: §f" + plugin.getGovernmentManager().getTotalSeats());
            player.sendMessage("§7Use §f/laws §7to see active laws.");
        }

        return true;
    }

    private void sendPartyHelp(Player player) {
        player.sendMessage("§9§l--- Party Commands ---");
        player.sendMessage("§f/party create <name> <description> §7- Found a new party");
        player.sendMessage("§f/party disband §7- Disband your party");
        player.sendMessage("§f/party invite <player> §7- Invite a player");
        player.sendMessage("§f/party kick <player> §7- Remove a member");
        player.sendMessage("§f/party leave §7- Leave your party");
        player.sendMessage("§f/party info [name] §7- View party info");
        player.sendMessage("§f/party list §7- List all parties");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return List.of();
        String cmd = label.toLowerCase();

        if (cmd.equals("party")) {
            List<String> subs = Arrays.asList("create", "disband", "invite", "kick", "leave", "info", "list", "accept", "decline");
            if (args.length == 1) return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
            if (args.length == 2 && (args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("kick"))) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
        }

        if (cmd.equals("mayor") && args.length == 1) {
            return Arrays.asList("pardon", "setbudget", "settax", "treasury", "repeallaw")
                    .stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }

        return List.of();
    }

    private static class PartyCreateSession {
        String name;
        PartyCreateSession(String name) { this.name = name; }
    }
}