package com.UserMC.MJB.commands;

import com.UserMC.MJB.GovernmentManager;
import com.UserMC.MJB.MJB;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class CouncilCommand implements CommandExecutor, TabCompleter {

    private final MJB plugin;

    // Hardcoded chair UUIDs
    private static final Set<String> CHAIR_UUIDS = Set.of(
            "af5b1cb2-1d2d-4cbe-b5a6-7c601b1dbc7c", // pizzacloud_nl
            "98a21ccd-7799-4b8b-8cc6-ef2905889c59"  // Bl3R
    );

    public CouncilCommand(MJB plugin) {
        this.plugin = plugin;
    }

    private boolean isChair(Player player) {
        return CHAIR_UUIDS.contains(player.getUniqueId().toString());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (!isChair(player)) {
            player.sendMessage("§4Only council chairs can use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        // Check session active for law-changing commands
        boolean requiresSession = !args[0].equalsIgnoreCase("session") &&
                !args[0].equalsIgnoreCase("status");

        if (requiresSession && !plugin.getGovernmentManager().isSessionActive()) {
            player.sendMessage("§4No council session is active right now.");
            player.sendMessage("§7Use §f/council session open §7to open one.");
            return true;
        }

        switch (args[0].toLowerCase()) {

            // ---- Session management ----
            case "session" -> {
                if (args.length < 2) {
                    player.sendMessage("§4Usage: /council session <open|close>");
                    return true;
                }
                switch (args[1].toLowerCase()) {
                    case "open" -> {
                        int id = plugin.getGovernmentManager().openSession();
                        player.sendMessage(id != -1
                                ? "§9§l[Council] §fSession opened."
                                : "§4A session is already active.");
                    }
                    case "close" -> {
                        plugin.getGovernmentManager().closeSession();
                        player.sendMessage("§9§l[Council] §fSession closed.");
                    }
                    default -> player.sendMessage("§4Usage: /council session <open|close>");
                }
            }

            // ---- Enact a custom law (text only) ----
            case "enact" -> {
                // /council enact <law text...>
                if (args.length < 2) {
                    player.sendMessage("§4Usage: /council enact <law description>");
                    return true;
                }
                String text = joinArgs(args, 1);
                enactAndAnnounce(player, text, GovernmentManager.LAW_CUSTOM, "true");
            }

            // ---- Repeal a law ----
            case "repeal" -> {
                // /council repeal <law_id>
                if (args.length < 2) {
                    player.sendMessage("§4Usage: /council repeal <law_id>");
                    return true;
                }
                try {
                    int id = Integer.parseInt(args[1]);
                    boolean ok = plugin.getGovernmentManager().repealLaw(id);
                    if (ok) {
                        broadcastLaw("§c§l[Council] §fLaw §b#" + id + " §fhas been repealed.");
                        player.sendMessage("§fLaw #" + id + " repealed.");
                    } else {
                        player.sendMessage("§4Law not found or already inactive.");
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage("§4Invalid law ID.");
                }
            }

            // ---- Tax rate ----
            case "settax" -> {
                // /council settax <0-50>
                if (args.length < 2) {
                    player.sendMessage("§4Usage: /council settax <0-50>");
                    return true;
                }
                try {
                    double rate = Double.parseDouble(args[1]);
                    if (rate < 0 || rate > 50) {
                        player.sendMessage("§4Tax rate must be between 0 and 50.");
                        return true;
                    }
                    enactAndAnnounce(player,
                            "Tax rate set to " + rate + "%",
                            GovernmentManager.LAW_TAX_RATE,
                            String.valueOf(rate));
                } catch (NumberFormatException e) {
                    player.sendMessage("§4Invalid rate.");
                }
            }

            // ---- Gun legality ----
            case "gunslegal" -> {
                // /council gunslegal <true|false>
                if (args.length < 2) {
                    player.sendMessage("§4Usage: /council gunslegal <true|false>");
                    return true;
                }
                boolean legal = args[1].equalsIgnoreCase("true");
                enactAndAnnounce(player,
                        "Firearms are now " + (legal ? "§alegal" : "§cillegal"),
                        GovernmentManager.LAW_GUNS_LEGAL,
                        String.valueOf(legal));
            }

            // ---- Property price modifier ----
            case "setproperty" -> {
                // /council setproperty <modifier> e.g. 1.5 = 150% of base
                if (args.length < 2) {
                    player.sendMessage("§4Usage: /council setproperty <modifier>");
                    player.sendMessage("§71.0 = normal, 1.5 = 50% more expensive, 0.8 = 20% cheaper");
                    return true;
                }
                try {
                    double mod = Double.parseDouble(args[1]);
                    enactAndAnnounce(player,
                            "Property price modifier set to " + mod + "x",
                            GovernmentManager.LAW_PROPERTY_PRICE,
                            String.valueOf(mod));
                } catch (NumberFormatException e) {
                    player.sendMessage("§4Invalid modifier.");
                }
            }

            // ---- Police funding ----
            case "fundpolice" -> {
                // /council fundpolice <amount> — one-time contribution
                if (args.length < 2) {
                    player.sendMessage("§4Usage: /council fundpolice <amount>");
                    return true;
                }
                try {
                    double amount = Double.parseDouble(args[1]);
                    enactAndAnnounce(player,
                            "City contributed §b" + plugin.getEconomyManager().format(amount) +
                                    "§f to the police budget",
                            GovernmentManager.LAW_POLICE_FUND,
                            String.valueOf(amount));
                } catch (NumberFormatException e) {
                    player.sendMessage("§4Invalid amount.");
                }
            }

            case "weeklypolice" -> {
                // /council weeklypolice <amount> — recurring weekly contribution
                if (args.length < 2) {
                    player.sendMessage("§4Usage: /council weeklypolice <amount>");
                    return true;
                }
                try {
                    double amount = Double.parseDouble(args[1]);
                    enactAndAnnounce(player,
                            "Weekly police city contribution set to §b" +
                                    plugin.getEconomyManager().format(amount),
                            GovernmentManager.LAW_POLICE_WEEKLY,
                            String.valueOf(amount));
                } catch (NumberFormatException e) {
                    player.sendMessage("§4Invalid amount.");
                }
            }

            case "defundpolice" -> {
                enactAndAnnounce(player,
                        "City contributions to the police have been stopped",
                        GovernmentManager.LAW_POLICE_DEFUNDED,
                        "true");
            }

            case "vehiclelicense" -> {
                if (args.length < 2) {
                    player.sendMessage("§4Usage: /council vehiclelicense <true|false>");
                    return true;
                }
                boolean required = args[1].equalsIgnoreCase("true");
                enactAndAnnounce(player,
                        "Vehicle licenses are now " + (required ? "required" : "not required"),
                        GovernmentManager.LAW_VEHICLE_LICENSE,
                        String.valueOf(required));
            }

            // ---- Pardon ----
            case "pardon" -> {
                // /council pardon <player>
                if (args.length < 2) {
                    player.sendMessage("§4Usage: /council pardon <player>");
                    return true;
                }
                enactAndAnnounce(player,
                        "Player §b" + args[1] + "§f has been pardoned by the council",
                        GovernmentManager.LAW_PARDON,
                        args[1]);
            }

            // ---- Status ----
            case "status" -> {
                player.sendMessage("§9§l--- Council Status ---");
                player.sendMessage("§7Session: " + (plugin.getGovernmentManager().isSessionActive()
                        ? "§aOpen" : "§7Closed"));
                player.sendMessage("§7Election: " + (plugin.getGovernmentManager().isElectionActive()
                        ? "§aActive" : "§7None"));
                player.sendMessage("§7Tax rate: §f" +
                        plugin.getGovernmentManager().getTaxRate() + "%");
                player.sendMessage("§7Guns legal: §f" +
                        (plugin.getGovernmentManager().areGunsLegal() ? "§aYes" : "§cNo"));
                player.sendMessage("§7Use §f/laws §7to see all active laws.");
            }

            default -> sendHelp(player);
        }

        return true;
    }

    // Enacts the law, records it in DB, and broadcasts to all players
    private void enactAndAnnounce(Player chair, String title,
                                  String lawType, String lawValue) {
        // Use a fake proposal ID of -1 for chair-enacted laws
        plugin.getGovernmentManager().enactLaw(title, lawType, lawValue, -1);
        broadcastLaw("§9§l[Council] §fNew law passed: §b" + title);
        chair.sendMessage("§fLaw enacted: §b" + title);
    }

    private void broadcastLaw(String message) {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.sendMessage(message);
        }
    }

    private String joinArgs(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) sb.append(" ");
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private void sendHelp(Player player) {
        player.sendMessage("§9§l--- Council Chair Commands ---");
        player.sendMessage("§f/council session <open|close> §7- Open or close a session");
        player.sendMessage("§f/council enact <text> §7- Enact a custom law");
        player.sendMessage("§f/council repeal <id> §7- Repeal a law");
        player.sendMessage("§f/council settax <0-50> §7- Set tax rate");
        player.sendMessage("§f/council gunslegal <true|false> §7- Toggle gun legality");
        player.sendMessage("§f/council setproperty <modifier> §7- Set property price modifier");
        player.sendMessage("§f/council fundpolice <amount> §7- One-time police funding");
        player.sendMessage("§f/council weeklypolice <amount> §7- Set weekly police funding");
        player.sendMessage("§f/council defundpolice §7- Stop city police contributions");
        player.sendMessage("§f/council pardon <player> §7- Pardon a player");
        player.sendMessage("§f/council status §7- View current government state");
        player.sendMessage("§f/council vehiclelicense <true|false> §7- Toggle vehicle license requirement");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String label, String[] args) {
        if (!(sender instanceof Player player) || !isChair(player)) return List.of();

        List<String> subs = Arrays.asList("session", "enact", "repeal", "settax",
                "gunslegal", "setproperty", "fundpolice", "weeklypolice",
                "defundpolice", "pardon", "status", "vehiclelicense");

        if (args.length == 1)
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "session"    -> Arrays.asList("open", "close");
                case "gunslegal"  -> Arrays.asList("true", "false");
                case "pardon"     -> Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                default -> List.of();
            };
        }

        return List.of();
    }
}
