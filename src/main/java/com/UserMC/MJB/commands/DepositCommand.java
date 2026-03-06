package com.UserMC.MJB.commands;

import com.UserMC.MJB.MJB;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DepositCommand implements CommandExecutor {

    private final MJB plugin;

    public DepositCommand(MJB plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!plugin.getBankNPCListener().isNearBankTeller(player)) {
            player.sendMessage("§cYou need to be at a bank to deposit money.");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage("§cUsage: /deposit <amount>");
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

        boolean success = plugin.getEconomyManager().depositCash(player, amount);
        if (success) {
            player.sendMessage("§aSuccessfully deposited §f" + plugin.getEconomyManager().format(amount) + " §ainto your bank account.");
        } else {
            player.sendMessage("§cYou don't have enough cash to deposit that amount.");
        }

        return true;
    }
}