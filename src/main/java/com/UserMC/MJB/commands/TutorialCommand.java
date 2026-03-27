package com.UserMC.MJB.commands;

import com.UserMC.MJB.MJB;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TutorialCommand implements CommandExecutor, TabCompleter {

    private final MJB plugin;

    public TutorialCommand(MJB plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length == 0) {
            plugin.getTutorialManager().sendOverview(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "company" -> plugin.getTutorialManager()
                    .onMadeChoice(player, "company");
            case "job"     -> plugin.getTutorialManager()
                    .onMadeChoice(player, "job");
            case "skip"    -> plugin.getTutorialManager()
                    .onMadeChoice(player, "skip");
            case "reset"   -> {
                // Admin only — reset tutorial for a player
                if (!player.hasPermission("mjb.admin")) {
                    player.sendMessage("§4No permission.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§4Usage: /tutorial reset <player>");
                    return true;
                }
                String sql = "UPDATE tutorial_progress SET " +
                        "visited_bank=FALSE, claimed_apartment=FALSE, " +
                        "checked_phone=FALSE, visited_gov=FALSE, " +
                        "visited_realestate=FALSE, made_choice=FALSE, " +
                        "completed=FALSE WHERE player_uuid = (" +
                        "SELECT uuid FROM players WHERE username = ?)";
                try (java.sql.PreparedStatement stmt = plugin.getDatabaseManager()
                        .getConnection().prepareStatement(sql)) {
                    stmt.setString(1, args[1]);
                    int rows = stmt.executeUpdate();
                    player.sendMessage(rows > 0
                            ? "§fTutorial reset for §b" + args[1] + "§f."
                            : "§4Player not found.");
                } catch (java.sql.SQLException e) {
                    player.sendMessage("§4Error resetting tutorial.");
                }
            }
            default -> plugin.getTutorialManager().sendOverview(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String label, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("company", "job", "skip", "reset")
                    .stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}