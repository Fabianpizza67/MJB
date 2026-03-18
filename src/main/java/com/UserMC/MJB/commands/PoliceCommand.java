package com.UserMC.MJB.commands;

import com.UserMC.MJB.CrimeManager;
import com.UserMC.MJB.CrimeManager.CrimeRecord;
import com.UserMC.MJB.CrimeManager.PoliceRank;
import com.UserMC.MJB.MJB;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class PoliceCommand implements CommandExecutor, TabCompleter {

    private final MJB plugin;
    private final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    public PoliceCommand(MJB plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (!plugin.getPoliceManager().isOfficer(player.getUniqueId())) {
            player.sendMessage("§4You are not a police officer.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "info" -> {
                // /police info <player>
                if (args.length < 2) { player.sendMessage("§4Usage: /police info <player>"); return true; }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (target == null) { player.sendMessage("§4Player not found."); return true; }

                boolean isWanted = plugin.getCrimeManager().isWanted(target.getUniqueId());
                List<CrimeRecord> records = plugin.getCrimeManager().getCrimeRecord(target.getUniqueId());

                player.sendMessage("§b§m-----------------------------");
                player.sendMessage("§b§lCrime Record: §f" + target.getName());
                player.sendMessage("§b§m-----------------------------");
                player.sendMessage("§7Wanted: " + (isWanted ? "§c§lYES" : "§aNo"));

                if (records.isEmpty()) {
                    player.sendMessage("§7No criminal record.");
                } else {
                    player.sendMessage("§7Offences (" + records.size() + "):");
                    for (CrimeRecord r : records) {
                        String status = r.processed ? "§7[Processed]" : "§c[Active]";
                        String witness = r.witnessedBy != null
                                ? Bukkit.getOfflinePlayer(r.witnessedBy).getName()
                                : "Reported";
                        player.sendMessage("§f  " + status + " §f" + r.offence);
                        player.sendMessage("§7    Recorded: §f" + sdf.format(r.recordedAt) +
                                " §7by §f" + witness);
                    }
                }
                player.sendMessage("§b§m-----------------------------");
            }

            case "wanted" -> {
                // /police wanted — list all wanted players
                List<UUID> wanted = plugin.getCrimeManager().getAllWantedPlayers();
                if (wanted.isEmpty()) {
                    player.sendMessage("§aNo wanted players at this time.");
                    return true;
                }
                player.sendMessage("§c§l--- Wanted Players ---");
                for (UUID uuid : wanted) {
                    OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
                    String name = p.getName() != null ? p.getName() : uuid.toString();
                    String online = p.isOnline() ? "§a[Online]" : "§7[Offline]";
                    List<CrimeRecord> crimes = plugin.getCrimeManager().getUnprocessedRecords(uuid);
                    player.sendMessage("§f- §c" + name + " " + online +
                            " §7(" + crimes.size() + " active charge(s))");
                }
            }

            case "charge" -> {
                // /police charge <player> <offence...>
                if (args.length < 3) {
                    player.sendMessage("§4Usage: /police charge <player> <offence description>");
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (target == null) { player.sendMessage("§4Player not found."); return true; }

                StringBuilder offence = new StringBuilder();
                for (int i = 2; i < args.length; i++) offence.append(args[i]).append(" ");

                plugin.getCrimeManager().addOffence(
                        target.getUniqueId(),
                        offence.toString().trim(),
                        player.getUniqueId()
                );
                player.sendMessage("§fCharge added to §b" + target.getName() +
                        "§f: §c" + offence.toString().trim());
                player.sendMessage("§7They are now marked as wanted.");

                // Notify target if online
                Player online = plugin.getServer().getPlayer(target.getUniqueId());
                if (online != null) {
                    online.sendMessage("§c§l[Police] §cOfficer §f" + player.getName() +
                            " §chas charged you with: §f" + offence.toString().trim());
                }
            }

            case "process" -> {
                // /police process <player> — mark charges as processed and clear wanted
                if (args.length < 2) {
                    player.sendMessage("§4Usage: /police process <player>");
                    return true;
                }
                // Must be sergeant or detective
                PoliceRank rank = plugin.getCrimeManager().getRank(player.getUniqueId());
                if (rank == PoliceRank.OFFICER) {
                    player.sendMessage("§4Only detectives and sergeants can process suspects.");
                    return true;
                }

                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (target == null) { player.sendMessage("§4Player not found."); return true; }

                List<CrimeRecord> active = plugin.getCrimeManager()
                        .getUnprocessedRecords(target.getUniqueId());
                if (active.isEmpty()) {
                    player.sendMessage("§4" + target.getName() + " has no active charges to process.");
                    return true;
                }

                plugin.getCrimeManager().markRecordsProcessed(target.getUniqueId());
                plugin.getCrimeManager().setWanted(target.getUniqueId(), false);

                player.sendMessage("§f§l[Police] §f" + target.getName() +
                        " §fhas been processed. §7" + active.size() + " charge(s) recorded.");
                player.sendMessage("§7Charges remain on record. Use §f/police clear §7to fully expunge.");

                Player online = plugin.getServer().getPlayer(target.getUniqueId());
                if (online != null) {
                    online.sendMessage("§b§l[Police] §fYou have been processed by §b" +
                            player.getName() + "§f.");
                    online.sendMessage("§7Your charges are on record. A trial may be scheduled.");
                }
            }

            case "clear" -> {
                // /police clear <player> — sergeant only, fully expunge record
                if (args.length < 2) {
                    player.sendMessage("§4Usage: /police clear <player>");
                    return true;
                }
                if (!plugin.getCrimeManager().isSergeanT(player.getUniqueId())) {
                    player.sendMessage("§4Only the Sergeant can expunge criminal records.");
                    return true;
                }

                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (target == null) { player.sendMessage("§4Player not found."); return true; }

                plugin.getCrimeManager().clearCrimeRecord(target.getUniqueId());
                player.sendMessage("§fRecord fully expunged for §b" + target.getName() + "§f.");

                Player online = plugin.getServer().getPlayer(target.getUniqueId());
                if (online != null) {
                    online.sendMessage("§a§l[Police] §aYour criminal record has been expunged by the Sergeant.");
                }
            }

            case "rank" -> {
                // /police rank <player> <officer|detective|sergeant> — sergeant only
                if (args.length < 3) {
                    player.sendMessage("§4Usage: /police rank <player> <officer|detective|sergeant>");
                    return true;
                }
                if (!plugin.getCrimeManager().isSergeanT(player.getUniqueId())) {
                    player.sendMessage("§4Only the Sergeant can change ranks.");
                    return true;
                }

                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage("§4Player not online.");
                    return true;
                }
                if (!plugin.getPoliceManager().isOfficer(target.getUniqueId())) {
                    player.sendMessage("§4" + target.getName() + " is not on the force.");
                    return true;
                }

                PoliceRank newRank = PoliceRank.fromString(args[2]);
                if (newRank == null) {
                    player.sendMessage("§4Unknown rank. Use: officer, detective, sergeant");
                    return true;
                }

                plugin.getCrimeManager().setRank(target.getUniqueId(), newRank);
                player.sendMessage("§f" + target.getName() + " §fis now a §b" + newRank.displayName + "§f.");
                target.sendMessage("§b§l[Police] §fThe Sergeant has promoted you to §b" +
                        newRank.displayName + "§f.");
            }

            case "myrank" -> {
                PoliceRank rank = plugin.getCrimeManager().getRank(player.getUniqueId());
                String rankStr = rank != null ? rank.displayName : "Officer";
                player.sendMessage("§b§l[Police] §fYour rank: §b" + rankStr);
            }

            default -> sendHelp(player);
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§b§l--- Police Commands ---");
        player.sendMessage("§f/police info <player> §7- View criminal record");
        player.sendMessage("§f/police wanted §7- List all wanted players");
        player.sendMessage("§f/police charge <player> <offence> §7- Add a charge");
        player.sendMessage("§f/police process <player> §7- Process a suspect (det/sgt)");
        player.sendMessage("§f/police clear <player> §7- Expunge record (sgt only)");
        player.sendMessage("§f/police rank <player> <rank> §7- Change rank (sgt only)");
        player.sendMessage("§f/police myrank §7- View your rank");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        if (!plugin.getPoliceManager().isOfficer(player.getUniqueId())) return List.of();

        List<String> subs = Arrays.asList("info", "wanted", "charge", "process", "clear", "rank", "myrank");

        if (args.length == 1) {
            return subs.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && !args[0].equalsIgnoreCase("wanted") && !args[0].equalsIgnoreCase("myrank")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("rank")) {
            return Arrays.asList("officer", "detective", "sergeant").stream()
                    .filter(r -> r.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return List.of();
    }
}