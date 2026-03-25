package com.UserMC.MJB.commands;

import com.UserMC.MJB.GovernmentManager;
import com.UserMC.MJB.GovernmentManager.*;
import com.UserMC.MJB.MJB;
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
            if (laws.isEmpty()) {
                player.sendMessage("§7No laws are currently in effect.");
                return true;
            }
            player.sendMessage("§9§l--- Active Laws (" + laws.size() + ") ---");
            for (Law law : laws) {
                boolean isCustom = law.lawType.equals(GovernmentManager.LAW_CUSTOM);
                String detail = isCustom
                        ? ""
                        : " §8[" + law.lawType + ": " + law.lawValue + "]";
                player.sendMessage("§f#" + law.id + " §b" + law.title + detail);
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