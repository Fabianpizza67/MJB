package com.UserMC.MJB.commands;

import com.UserMC.MJB.MJB;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TerminalCommand implements CommandExecutor {

    private final MJB plugin;

    public TerminalCommand(MJB plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§7Usage: §f/terminal register §7or §f/terminal remove");
            return true;
        }

        // Get the block the player is looking at
        Block target = player.getTargetBlockExact(5);
        if (target == null || target.getType() != Material.PURPUR_STAIRS) {
            player.sendMessage("§4You must be looking at a Purpur Stairs block.");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "register" -> {
                // Check player is in a plot they own
                String regionId = plugin.getPlotManager().getRegionAtPlayer(player);
                if (regionId == null) {
                    player.sendMessage("§4You must be inside your own plot to register a terminal.");
                    return true;
                }

                boolean success = plugin.getTerminalManager().registerTerminal(
                        target.getLocation(),
                        player.getUniqueId(),
                        regionId
                );

                if (success) {
                    player.sendMessage("§fTerminal registered in §b" + regionId + "§f!");
                    player.sendMessage("§7Sneak + right-click to set a price.");
                } else {
                    player.sendMessage("§4Failed to register terminal.");
                }
            }

            case "remove" -> {
                if (!plugin.getTerminalManager().isTerminal(target.getLocation())) {
                    player.sendMessage("§4That block is not a registered terminal.");
                    return true;
                }

                var data = plugin.getTerminalManager().getTerminalData(target.getLocation());
                if (!data.ownerUuid.equals(player.getUniqueId()) && !player.hasPermission("mjb.admin")) {
                    player.sendMessage("§4That terminal doesn't belong to you.");
                    return true;
                }

                plugin.getTerminalManager().unregisterTerminal(target.getLocation());
                player.sendMessage("§fTerminal removed successfully.");
            }

            default -> player.sendMessage("§7Usage: §f/terminal register §7or §f/terminal remove");
        }

        return true;
    }
}