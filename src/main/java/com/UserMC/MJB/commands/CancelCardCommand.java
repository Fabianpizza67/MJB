package com.UserMC.MJB.commands;

import com.UserMC.MJB.MJB;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CancelCardCommand implements CommandExecutor {

    private final MJB plugin;

    public CancelCardCommand(MJB plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!plugin.getBankNPCListener().isNearBankTeller(player)) {
            player.sendMessage("§4You need to be at a bank to cancel your card.");
            return true;
        }

        if (plugin.getDebitCardManager().isUUIDCancelled(player.getUniqueId())) {
            player.sendMessage("§4Your card is already cancelled.");
            player.sendMessage("§7Get a replacement with §f/buycard§7.");
            return true;
        }

        plugin.getDebitCardManager().cancelCard(player.getUniqueId());

        player.sendMessage("§b§m-----------------------------");
        player.sendMessage("§b§l  Card Cancelled");
        player.sendMessage("§b§m-----------------------------");
        player.sendMessage("§fYour debit card has been cancelled.");
        player.sendMessage("§7Any card with your name on it is now worthless.");
        player.sendMessage("§7Get a replacement with §f/buycard§7.");
        player.sendMessage("§b§m-----------------------------");

        return true;
    }
}