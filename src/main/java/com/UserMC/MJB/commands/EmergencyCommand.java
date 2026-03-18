package com.UserMC.MJB.commands;

import com.UserMC.MJB.MJB;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class EmergencyCommand implements CommandExecutor {

    private final MJB plugin;

    public EmergencyCommand(MJB plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§4Usage: /" + label + " <message>");
            player.sendMessage("§7Example: §f/" + label + " shots fired near Main Street!");
            return true;
        }

        StringBuilder message = new StringBuilder();
        for (String arg : args) message.append(arg).append(" ");
        String msg = message.toString().trim();

        // Send confirmation to caller
        player.sendMessage("§7§l[" + label.toUpperCase() + "] §7Your message has been sent to all online officers.");

        // Relay to all online officers
        int count = plugin.getCrimeManager().relay911(player, msg);

        if (count == 0) {
            player.sendMessage("§4§l[" + label.toUpperCase() + "] §4No officers are currently online. You're on your own!");
        } else {
            player.sendMessage("§7" + count + " officer(s) notified.");
        }

        return true;
    }
}