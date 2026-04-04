package com.UserMC.MJB.commands;

import com.UserMC.MJB.MJB;
import com.UserMC.MJB.PlotManager.PlotInfo;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.UUID;

public class PlotInfoCommand implements CommandExecutor {

    private final MJB plugin;
    private final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    public PlotInfoCommand(MJB plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        // Get the region the player is standing in
        String regionId = plugin.getPlotManager().getRegionAtPlayer(player);

        if (regionId == null) {
            player.sendMessage("§4You are not standing inside any plot.");
            return true;
        }

        PlotInfo info = plugin.getPlotManager().getPlotInfo(regionId, player.getWorld());

        if (info == null) {
            // Show region ID even without an owner
            player.sendMessage("§8§m-----------------------------");
            player.sendMessage("§b§l  " + regionId);
            player.sendMessage("§8§m-----------------------------");
            player.sendMessage("§7Address: §b" + regionId);
            player.sendMessage("§7Owner: §7None — this plot is not owned by anyone.");

            // Check if it's listed for sale
            com.UserMC.MJB.PropertyManager.PropertyListing listing =
                    plugin.getPropertyManager().getListing(regionId, player.getWorld().getName());
            if (listing != null && listing.isAvailable) {
                player.sendMessage("§7For sale: §b" +
                        plugin.getEconomyManager().format(listing.price));
            }
            player.sendMessage("§8§m-----------------------------");
            return true;
        }

        // Get owner name
        OfflinePlayer owner = Bukkit.getOfflinePlayer(info.ownerUuid);
        String ownerName = owner.getName() != null ? owner.getName() : "§7Unknown";

        // Format plot type nicely
        String plotType = formatPlotType(info.plotType);

        player.sendMessage("§8§m-----------------------------");
        player.sendMessage("§b§l  " + regionId);
        player.sendMessage("§8§m-----------------------------");
        player.sendMessage("§7Address: §b" + regionId);
        player.sendMessage("§7Type: §f" + plotType);
        player.sendMessage("§7Owner: §b" + ownerName);
        player.sendMessage("§7Since: §f" + sdf.format(info.purchasedAt));

        if (!info.members.isEmpty()) {
            player.sendMessage("§7Members:");
            for (UUID memberUuid : info.members) {
                OfflinePlayer member = Bukkit.getOfflinePlayer(memberUuid);
                String memberName = member.getName() != null ? member.getName() : "§7Unknown";
                player.sendMessage("§7  - §b" + memberName);
            }
        } else {
            player.sendMessage("§7Members: §fnone");
        }

        player.sendMessage("§8§m-----------------------------");
        return true;
    }

    private String formatPlotType(String type) {
        return switch (type) {
            case "starter_apartment" -> "Starter Apartment";
            case "apartment" -> "Apartment";
            case "store" -> "Store";
            case "house" -> "House";
            default -> type;
        };
    }
}