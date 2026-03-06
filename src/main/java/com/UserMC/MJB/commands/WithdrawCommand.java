package com.UserMC.MJB.commands;

import com.UserMC.MJB.MJB;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class WithdrawCommand implements CommandExecutor {

    private final MJB plugin;

    public WithdrawCommand(MJB plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!plugin.getBankNPCListener().isNearBankTeller(player)) {
            player.sendMessage("§cYou need to be at a bank to withdraw money.");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage("§cUsage: /withdraw <amount>");
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid amount.");
            return true;
        }

        if (amount <= 0) {
            player.sendMessage("§cAmount must be greater than zero.");
            return true;
        }

        boolean success = plugin.getEconomyManager().withdrawCash(player, amount);
        if (success) {
            player.sendMessage("§aSuccessfully withdrew §f" + plugin.getEconomyManager().format(amount) + " §ain cash.");
        } else {
            player.sendMessage("§cInsufficient bank balance.");
        }

        return true;
    }
}