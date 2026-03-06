package com.UserMC.MJB.commands;

import com.UserMC.MJB.MJB;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PayCommand implements CommandExecutor {

    private final MJB plugin;

    public PayCommand(MJB plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length != 2) {
            player.sendMessage("§cUsage: /pay <player> <amount>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage("§cPlayer not found or not online.");
            return true;
        }

        if (target.equals(player)) {
            player.sendMessage("§cYou can't pay yourself.");
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid amount.");
            return true;
        }

        if (amount <= 0) {
            player.sendMessage("§cAmount must be greater than zero.");
            return true;
        }

        boolean success = plugin.getEconomyManager().transferBank(
                player.getUniqueId(),
                target.getUniqueId(),
                amount
        );

        if (success) {
            String formatted = plugin.getEconomyManager().format(amount);
            player.sendMessage("§aYou paid §f" + target.getName() + " §a" + formatted + " from your bank account.");
            target.sendMessage("§aYou received §f" + formatted + " §afrom §f" + player.getName() + "§a.");
        } else {
            player.sendMessage("§cInsufficient bank balance.");
        }

        return true;
    }
}