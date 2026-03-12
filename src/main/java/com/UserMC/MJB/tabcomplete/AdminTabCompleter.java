package com.UserMC.MJB.tabcomplete;

import net.citizensnpcs.api.CitizensAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AdminTabCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "setnpc", "setbankteller", "removebankteller", "listbanktellers",
            "assignplot", "removeplot", "addmember",
            "addstarterapt", "removestarterapt", "liststarterapts", "unclaimstarter",
            "addcomputer", "setpickupnpc", "addsupplyitem", "listproperty", "unlistproperty", "treasury"
    );

    private static final List<String> NPC_TYPES = Arrays.asList("bankteller", "housing");
    private static final List<String> PLOT_TYPES = Arrays.asList("apartment", "house", "store");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }

        switch (args[0].toLowerCase()) {
            case "setnpc" -> {
                if (args.length == 2) return getNPCIds(args[1]);
                if (args.length == 3) return filter(NPC_TYPES, args[2]);
            }
            case "setbankteller", "removebankteller" -> {
                if (args.length == 2) return getNPCIds(args[1]);
            }
            case "assignplot" -> {
                if (args.length == 2) return getOnlinePlayers(args[1]);
                if (args.length == 4) return filter(PLOT_TYPES, args[3]);
            }
            case "removeplot", "addmember" -> {
                if (args.length == 2) return getOnlinePlayers(args[1]);
            }
            case "setpickupnpc" -> {
                if (args.length == 2) return getNPCIds(args[1]);
                if (args.length == 3) return List.of("<district>");
            }
            case "addsupplyitem" -> {
                if (args.length == 2) return getMaterials(args[1]);
                if (args.length == 3) return filter(Arrays.asList("baker", "farmer", "clothing"), args[2]);
                if (args.length == 4) return List.of("<price>");
                if (args.length == 5) return List.of("<delivery_seconds>");
            }
        }

        return completions;
    }

    private List<String> filter(List<String> list, String input) {
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(input.toLowerCase()))
                .collect(Collectors.toList());
    }

    private List<String> getOnlinePlayers(String input) {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input.toLowerCase()))
                .collect(Collectors.toList());
    }

    private List<String> getNPCIds(String input) {
        List<String> ids = new ArrayList<>();
        for (var npc : CitizensAPI.getNPCRegistry()) {
            String id = String.valueOf(npc.getId());
            if (id.startsWith(input)) ids.add(id + " (" + npc.getName() + ")");
        }
        return ids;
    }

    private List<String> getMaterials(String input) {
        return Arrays.stream(org.bukkit.Material.values())
                .map(m -> m.name().toLowerCase())
                .filter(m -> m.startsWith(input.toLowerCase()))
                .limit(20)
                .collect(Collectors.toList());
    }
}