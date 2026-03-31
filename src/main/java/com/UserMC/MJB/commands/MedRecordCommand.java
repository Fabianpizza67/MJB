package com.UserMC.MJB.commands;

import com.UserMC.MJB.MJB;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class MedRecordCommand implements CommandExecutor, TabCompleter {

    private final MJB plugin;

    public MedRecordCommand(MJB plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        // Doctors and admins only
        if (!plugin.getHospitalManager().isDoctor(player.getUniqueId())
                && !player.hasPermission("mjb.admin")) {
            player.sendMessage("§4Only hospital staff can access medical records.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§4Usage: /medrecord <player>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target == null) {
            player.sendMessage("§4Player not found.");
            return true;
        }

        plugin.getMedicalRecordManager().printRecords(player, target.getUniqueId());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String label, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}