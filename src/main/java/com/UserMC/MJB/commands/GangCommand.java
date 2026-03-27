package com.UserMC.MJB.commands;

import com.UserMC.MJB.GangManager;
import com.UserMC.MJB.GangManager.GangInfo;
import com.UserMC.MJB.MJB;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class GangCommand implements CommandExecutor, TabCompleter {

    private final MJB plugin;

    public GangCommand(MJB plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length == 0) { sendHelp(player); return true; }

        switch (args[0].toLowerCase()) {

            case "create" -> {
                if (args.length < 2) {
                    player.sendMessage("§4Usage: /gang create <name>");
                    return true;
                }
                String name = args[1];
                if (name.length() < 2 || name.length() > 24) {
                    player.sendMessage("§4Gang name must be 2-24 characters.");
                    return true;
                }
                if (!name.matches("[a-zA-Z0-9 _-]+")) {
                    player.sendMessage("§4Letters, numbers, spaces, dashes and underscores only.");
                    return true;
                }
                int result = plugin.getGangManager().createGang(
                        player.getUniqueId(), name);
                switch (result) {
                    case -2 -> player.sendMessage("§4That gang name is already taken.");
                    case -3 -> player.sendMessage("§4You are already in a gang. Leave it first.");
                    case -1 -> player.sendMessage("§4Something went wrong. Try again.");
                    default -> {
                        player.sendMessage("§8§m-----------------------------");
                        player.sendMessage("§f§lGang created: §r§f" + name);
                        player.sendMessage("§8§m-----------------------------");
                        player.sendMessage("§7Invite others with §f/gang invite <player>§7.");
                        player.sendMessage("§8§m-----------------------------");
                    }
                }
            }

            case "disband" -> {
                GangInfo gang = plugin.getGangManager()
                        .getGangByMember(player.getUniqueId());
                if (gang == null) {
                    player.sendMessage("§4You are not in a gang.");
                    return true;
                }
                if (!gang.leaderUuid.equals(player.getUniqueId())) {
                    player.sendMessage("§4Only the gang leader can disband the gang.");
                    return true;
                }
                // Notify members
                List<UUID> members = plugin.getGangManager().getMembers(gang.id);
                plugin.getGangManager().disbandGang(gang.id, player.getUniqueId());
                for (UUID uuid : members) {
                    Player member = plugin.getServer().getPlayer(uuid);
                    if (member != null && !member.equals(player)) {
                        member.sendMessage("§7§l[Gang] §7" + gang.name +
                                " §7has been disbanded by the leader.");
                    }
                }
                player.sendMessage("§7Gang §f" + gang.name + " §7has been disbanded.");
            }

            case "invite" -> {
                if (args.length < 2) {
                    player.sendMessage("§4Usage: /gang invite <player>");
                    return true;
                }
                GangInfo gang = plugin.getGangManager()
                        .getGangByMember(player.getUniqueId());
                if (gang == null) {
                    player.sendMessage("§4You are not in a gang.");
                    return true;
                }
                if (!gang.leaderUuid.equals(player.getUniqueId())) {
                    player.sendMessage("§4Only the gang leader can invite members.");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage("§4Player not online.");
                    return true;
                }
                if (plugin.getGangManager().getGangByMember(
                        target.getUniqueId()) != null) {
                    player.sendMessage("§4That player is already in a gang.");
                    return true;
                }
                plugin.getGangManager().sendInvite(gang.id, target.getUniqueId());
                player.sendMessage("§7Invite sent to §f" + target.getName() + "§7.");
                target.sendMessage("§8§m-----------------------------");
                target.sendMessage("§f§l[Gang] §r§fInvite from §f" + player.getName());
                target.sendMessage("§7You have been invited to join §f" + gang.name + "§7.");
                target.sendMessage("§7Type §f/gang accept §7or §f/gang decline§7.");
                target.sendMessage("§7Expires in §f5 minutes§7.");
                target.sendMessage("§8§m-----------------------------");
            }

            case "accept" -> {
                Integer gangId = plugin.getGangManager()
                        .getPendingInvite(player.getUniqueId());
                if (gangId == null) {
                    player.sendMessage("§4You don't have a pending gang invite.");
                    return true;
                }
                plugin.getGangManager().removeInvite(player.getUniqueId());
                GangInfo gang = plugin.getGangManager().getGangById(gangId);
                plugin.getGangManager().addMember(gangId, player.getUniqueId());
                player.sendMessage("§f§l[Gang] §r§fYou joined §f" +
                        (gang != null ? gang.name : "the gang") + "§f!");
                if (gang != null) {
                    Player leader = plugin.getServer().getPlayer(gang.leaderUuid);
                    if (leader != null) {
                        leader.sendMessage("§f§l[Gang] §r§f" + player.getName() +
                                " §fjoined the gang!");
                    }
                }
            }

            case "decline" -> {
                Integer gangId = plugin.getGangManager()
                        .getPendingInvite(player.getUniqueId());
                if (gangId == null) {
                    player.sendMessage("§4You don't have a pending gang invite.");
                    return true;
                }
                plugin.getGangManager().removeInvite(player.getUniqueId());
                GangInfo gang = plugin.getGangManager().getGangById(gangId);
                player.sendMessage("§7Invite declined.");
                if (gang != null) {
                    Player leader = plugin.getServer().getPlayer(gang.leaderUuid);
                    if (leader != null) {
                        leader.sendMessage("§7§f" + player.getName() +
                                " §7declined your gang invite.");
                    }
                }
            }

            case "leave" -> {
                GangInfo gang = plugin.getGangManager()
                        .getGangByMember(player.getUniqueId());
                if (gang == null) {
                    player.sendMessage("§4You are not in a gang.");
                    return true;
                }
                if (gang.leaderUuid.equals(player.getUniqueId())) {
                    player.sendMessage("§4You are the leader — use §f/gang disband §4instead.");
                    return true;
                }
                plugin.getGangManager().removeMember(gang.id, player.getUniqueId());
                player.sendMessage("§7You left §f" + gang.name + "§7.");
                Player leader = plugin.getServer().getPlayer(gang.leaderUuid);
                if (leader != null) {
                    leader.sendMessage("§7§f" + player.getName() +
                            " §7left the gang.");
                }
            }

            case "kick" -> {
                if (args.length < 2) {
                    player.sendMessage("§4Usage: /gang kick <player>");
                    return true;
                }
                GangInfo gang = plugin.getGangManager()
                        .getGangByMember(player.getUniqueId());
                if (gang == null || !gang.leaderUuid.equals(player.getUniqueId())) {
                    player.sendMessage("§4Only the gang leader can kick members.");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage("§4Player not online.");
                    return true;
                }
                boolean ok = plugin.getGangManager()
                        .removeMember(gang.id, target.getUniqueId());
                if (ok) {
                    player.sendMessage("§f" + target.getName() +
                            " §7has been removed from the gang.");
                    target.sendMessage("§7You have been removed from §f" +
                            gang.name + "§7.");
                } else {
                    player.sendMessage("§4Cannot kick the gang leader.");
                }
            }

            case "info" -> {
                GangInfo gang = args.length >= 2
                        ? plugin.getGangManager().getGangByName(args[1])
                        : plugin.getGangManager()
                        .getGangByMember(player.getUniqueId());
                if (gang == null) {
                    player.sendMessage("§4Gang not found.");
                    return true;
                }
                List<UUID> members = plugin.getGangManager().getMembers(gang.id);
                String leaderName = plugin.getServer()
                        .getOfflinePlayer(gang.leaderUuid).getName();
                player.sendMessage("§8§m-----------------------------");
                player.sendMessage("§f§l" + gang.name);
                player.sendMessage("§8§m-----------------------------");
                player.sendMessage("§7Leader: §f" + leaderName);
                player.sendMessage("§7Members (" + members.size() + "):");
                for (UUID uuid : members) {
                    String mName = plugin.getServer().getOfflinePlayer(uuid).getName();
                    player.sendMessage("§7  - §f" + mName +
                            (uuid.equals(gang.leaderUuid) ? " §8(Leader)" : ""));
                }
                player.sendMessage("§8§m-----------------------------");
            }

            case "list" -> {
                List<GangInfo> gangs = plugin.getGangManager().getAllGangs();
                if (gangs.isEmpty()) {
                    player.sendMessage("§7No gangs exist yet.");
                    return true;
                }
                player.sendMessage("§f§l--- Gangs ---");
                for (GangInfo g : gangs) {
                    int count = plugin.getGangManager().getMembers(g.id).size();
                    player.sendMessage("§f" + g.name + " §7(" + count + " members)");
                }
            }

            default -> sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§f§l--- Gang Commands ---");
        player.sendMessage("§f/gang create <name> §7- Start a new gang");
        player.sendMessage("§f/gang disband §7- Disband your gang");
        player.sendMessage("§f/gang invite <player> §7- Invite a player");
        player.sendMessage("§f/gang kick <player> §7- Remove a member");
        player.sendMessage("§f/gang leave §7- Leave your gang");
        player.sendMessage("§f/gang accept §7- Accept a gang invite");
        player.sendMessage("§f/gang decline §7- Decline a gang invite");
        player.sendMessage("§f/gang info [name] §7- View gang info");
        player.sendMessage("§f/gang list §7- List all gangs");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String label, String[] args) {
        if (!(sender instanceof Player)) return List.of();
        List<String> subs = Arrays.asList("create", "disband", "invite",
                "kick", "leave", "accept", "decline", "info", "list");
        if (args.length == 1) {
            return subs.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("invite")
                || args[0].equalsIgnoreCase("kick"))) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}