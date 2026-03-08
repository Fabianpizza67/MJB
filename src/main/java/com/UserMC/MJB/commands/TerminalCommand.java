package com.UserMC.MJB.commands;

import com.UserMC.MJB.MJB;
import com.UserMC.MJB.TerminalManager.TerminalData;
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
            player.sendMessage("§7Usage: §f/terminal <register|remove|set <price>>");
            return true;
        }

        Block target = player.getTargetBlockExact(5);

        switch (args[0].toLowerCase()) {
            case "register" -> {
                if (target == null || target.getType() != Material.PURPUR_STAIRS) {
                    player.sendMessage("§4You must be looking at a Purpur Stairs block.");
                    return true;
                }

                String regionId = plugin.getPlotManager().getRegionAtLocation(target.getLocation());
                if (regionId == null) {
                    player.sendMessage("§4This block is not inside any plot.");
                    return true;
                }

                if (!plugin.getPlotManager().isPlotOwner(player.getUniqueId(), regionId)) {
                    player.sendMessage("§4You don't own this plot.");
                    return true;
                }

                if (plugin.getTerminalManager().isTerminal(target.getLocation())) {
                    player.sendMessage("§4This block is already a registered terminal.");
                    return true;
                }

                boolean success = plugin.getTerminalManager().registerTerminal(
                        target.getLocation(),
                        player.getUniqueId(),
                        regionId
                );

                if (success) {
                    player.sendMessage("§fTerminal registered in plot §b" + regionId + "§f!");
                    player.sendMessage("§7Use §f/terminal set <price> §7while looking at it to set a price.");
                } else {
                    player.sendMessage("§4Failed to register terminal.");
                }
            }

            case "remove" -> {
                if (target == null || target.getType() != Material.PURPUR_STAIRS) {
                    player.sendMessage("§4You must be looking at a Purpur Stairs block.");
                    return true;
                }

                if (!plugin.getTerminalManager().isTerminal(target.getLocation())) {
                    player.sendMessage("§4That block is not a registered terminal.");
                    return true;
                }

                TerminalData data = plugin.getTerminalManager().getTerminalData(target.getLocation());
                if (!data.ownerUuid.equals(player.getUniqueId()) && !player.hasPermission("mjb.admin")) {
                    player.sendMessage("§4That terminal doesn't belong to you.");
                    return true;
                }

                plugin.getTerminalManager().unregisterTerminal(target.getLocation());
                player.sendMessage("§fTerminal removed successfully.");
            }

            case "set" -> {
                if (args.length < 2) {
                    player.sendMessage("§7Usage: §f/terminal set <price>");
                    return true;
                }

                if (target == null || target.getType() != Material.PURPUR_STAIRS) {
                    player.sendMessage("§4You must be looking at a Purpur Stairs block.");
                    return true;
                }

                if (!plugin.getTerminalManager().isTerminal(target.getLocation())) {
                    player.sendMessage("§4That block is not a registered terminal.");
                    return true;
                }

                TerminalData data = plugin.getTerminalManager().getTerminalData(target.getLocation());
                if (!data.ownerUuid.equals(player.getUniqueId()) && !player.hasPermission("mjb.admin")) {
                    player.sendMessage("§4That terminal doesn't belong to you.");
                    return true;
                }

                double price;
                try {
                    price = Double.parseDouble(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§4Invalid price.");
                    return true;
                }

                if (price < 0) {
                    player.sendMessage("§4Price cannot be negative.");
                    return true;
                }

                plugin.getTerminalManager().setPrice(target.getLocation(), price);
                player.sendMessage("§fTerminal price set to §b" + plugin.getEconomyManager().format(price) + "§f.");
            }

            default -> player.sendMessage("§7Usage: §f/terminal <register|remove|set <price>>");
        }

        return true;
    }
}