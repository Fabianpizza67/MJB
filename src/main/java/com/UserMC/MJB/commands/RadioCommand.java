package com.UserMC.MJB.commands;

import com.UserMC.MJB.MJB;
import com.UserMC.MJB.RadioManager;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class RadioCommand implements CommandExecutor, TabCompleter {

    private final MJB plugin;

    private static final List<String> ALL_FREQUENCIES = List.of(
            RadioManager.CHANNEL_POLICE,
            RadioManager.CHANNEL_MEDICAL,
            RadioManager.CHANNEL_DISPATCH,
            RadioManager.CHANNEL_PUBLIC
    );

    public RadioCommand(MJB plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        ItemStack radio = plugin.getRadioManager().getHeldRadio(player);
        if (radio == null) {
            player.sendMessage("§4You need to hold a radio to transmit.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§4Usage: /radio [police|medical|dispatch|public] <message>");
            return true;
        }

        String baseChannel = plugin.getRadioManager().getRadioChannel(radio);
        String frequency   = plugin.getRadioManager().getDefaultFrequency(baseChannel);
        int messageStart   = 0;

        // Check if first arg is a frequency name
        if (ALL_FREQUENCIES.contains(args[0].toLowerCase())) {
            frequency    = args[0].toLowerCase();
            messageStart = 1;
        }

        if (args.length <= messageStart) {
            player.sendMessage("§4You need to include a message!");
            return true;
        }

        if (!plugin.getRadioManager().canUseFrequency(baseChannel, frequency)) {
            player.sendMessage("§4Your radio cannot access the §f" +
                    frequency + " §4frequency.");
            return true;
        }

        // Build message from remaining args
        StringBuilder sb = new StringBuilder();
        for (int i = messageStart; i < args.length; i++) {
            if (i > messageStart) sb.append(" ");
            sb.append(args[i]);
        }

        plugin.getRadioManager().broadcast(player, frequency, sb.toString());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String label, String[] args) {
        if (!(sender instanceof Player player)) return List.of();

        ItemStack radio = plugin.getRadioManager().getHeldRadio(player);
        if (radio == null) return List.of();

        String baseChannel = plugin.getRadioManager().getRadioChannel(radio);

        if (args.length == 1) {
            return ALL_FREQUENCIES.stream()
                    .filter(f -> plugin.getRadioManager().canUseFrequency(baseChannel, f))
                    .filter(f -> f.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}