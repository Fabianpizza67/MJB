package com.UserMC.MJB.commands;

import com.UserMC.MJB.JailManager;
import com.UserMC.MJB.MJB;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class JudgeCommand implements CommandExecutor, TabCompleter {

    private final MJB plugin;
    private final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm");

    // Hardcoded judge UUIDs — same pattern as council chairs
    private static final Set<String> JUDGE_UUIDS = Set.of(
            "98a21ccd-7799-4b8b-8cc6-ef2905889c59"  // Bl3R
    );

    public JudgeCommand(MJB plugin) {
        this.plugin = plugin;
    }

    private boolean isJudge(Player player) {
        return JUDGE_UUIDS.contains(player.getUniqueId().toString());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!isJudge(player)) {
            player.sendMessage("§4Only the judge can use this command.");
            return true;
        }
        if (args.length == 0) { sendHelp(player); return true; }

        switch (args[0].toLowerCase()) {

            case "sentence" -> {
                if (args.length < 3) {
                    player.sendMessage("§4Usage: /judge sentence <player> <minutes>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage("§4Player not found or not online.");
                    return true;
                }
                int minutes;
                try { minutes = Integer.parseInt(args[2]); }
                catch (NumberFormatException e) {
                    player.sendMessage("§4Invalid number of minutes.");
                    return true;
                }
                if (minutes <= 0) {
                    player.sendMessage("§4Minutes must be greater than zero.");
                    return true;
                }

                boolean ok = plugin.getJailManager()
                        .sentence(target.getUniqueId(), player.getUniqueId(), minutes);
                if (ok) {
                    player.sendMessage("§9§l[Judge] §f" + target.getName() +
                            " §fhas been sentenced to §b" + minutes + " minutes §fin jail.");
                } else {
                    player.sendMessage("§4Failed to sentence player. Check console.");
                }
            }

            case "release" -> {
                if (args.length < 2) {
                    player.sendMessage("§4Usage: /judge release <player>");
                    return true;
                }
                UUID targetUuid = resolveUuid(args[1]);
                if (targetUuid == null) {
                    player.sendMessage("§4Player not found.");
                    return true;
                }
                if (!plugin.getJailManager().isJailed(targetUuid)) {
                    player.sendMessage("§4" + args[1] + " is not currently jailed.");
                    return true;
                }
                boolean ok = plugin.getJailManager().release(targetUuid, false);
                if (ok) {
                    player.sendMessage("§9§l[Judge] §f" + args[1] +
                            " §fhas been released from jail.");
                } else {
                    player.sendMessage("§4Failed to release player.");
                }
            }

            case "reduce" -> {
                if (args.length < 3) {
                    player.sendMessage("§4Usage: /judge reduce <player> <minutes>");
                    return true;
                }
                UUID targetUuid = resolveUuid(args[1]);
                if (targetUuid == null) {
                    player.sendMessage("§4Player not found.");
                    return true;
                }
                if (!plugin.getJailManager().isJailed(targetUuid)) {
                    player.sendMessage("§4" + args[1] + " is not currently jailed.");
                    return true;
                }
                int minutes;
                try { minutes = Integer.parseInt(args[2]); }
                catch (NumberFormatException e) {
                    player.sendMessage("§4Invalid number of minutes.");
                    return true;
                }
                if (minutes <= 0) {
                    player.sendMessage("§4Minutes must be greater than zero.");
                    return true;
                }
                boolean ok = plugin.getJailManager().reduceTime(targetUuid, minutes);
                if (ok) {
                    player.sendMessage("§9§l[Judge] §fSentence for §f" + args[1] +
                            " §freduced by §b" + minutes + " minutes§f.");
                } else {
                    player.sendMessage("§4Failed to reduce sentence.");
                }
            }

            case "list" -> {
                List<JailManager.JailRecord> active =
                        plugin.getJailManager().getActiveSentences();
                if (active.isEmpty()) {
                    player.sendMessage("§9§l[Judge] §7No players are currently jailed.");
                    return true;
                }
                player.sendMessage("§9§l--- Active Sentences ---");
                for (JailManager.JailRecord record : active) {
                    String name = plugin.getServer()
                            .getOfflinePlayer(record.playerUuid).getName();
                    long remaining = plugin.getJailManager()
                            .getRemainingSeconds(record.playerUuid);
                    String timeLeft = formatTime(remaining);
                    player.sendMessage("§f" + (name != null ? name : record.playerUuid) +
                            " §7| §f" + record.originalMinutes + "min sentence" +
                            " §7| §fremaining: §c" + timeLeft +
                            " §7| §fsentenced: §7" + sdf.format(record.sentencedAt));
                }
            }

            default -> sendHelp(player);
        }
        return true;
    }

    private UUID resolveUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        @SuppressWarnings("deprecation")
        OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        return op.getUniqueId();
    }

    private String formatTime(long seconds) {
        if (seconds <= 0) return "0s";
        long mins = seconds / 60;
        long secs = seconds % 60;
        return mins > 0 ? mins + "m " + secs + "s" : secs + "s";
    }

    private void sendHelp(Player player) {
        player.sendMessage("§9§l--- Judge Commands ---");
        player.sendMessage("§f/judge sentence <player> <minutes> §7- Sentence a player");
        player.sendMessage("§f/judge release <player> §7- Release a player early");
        player.sendMessage("§f/judge reduce <player> <minutes> §7- Reduce a sentence");
        player.sendMessage("§f/judge list §7- List all active sentences");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String label, String[] args) {
        if (!(sender instanceof Player player) || !isJudge(player)) return List.of();

        List<String> subs = List.of("sentence", "release", "reduce", "list");
        if (args.length == 1) {
            return subs.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("list")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("sentence")
                || args[0].equalsIgnoreCase("reduce"))) {
            return List.of("<minutes>");
        }
        return List.of();
    }
}