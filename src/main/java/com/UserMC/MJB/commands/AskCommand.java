package com.UserMC.MJB.commands;

import com.UserMC.MJB.MJB;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AskCommand implements CommandExecutor {

    private final MJB plugin;
    private static final long COOLDOWN_MS = 20_000L;
    private final Map<UUID, Long> lastUsed = new ConcurrentHashMap<>();

    public AskCommand(MJB plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§4Usage: /ask <question>");
            player.sendMessage("§7Example: §f/ask how do I deposit money?");
            return true;
        }

        long now = System.currentTimeMillis();
        Long last = lastUsed.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) {
            long secsLeft = (COOLDOWN_MS - (now - last)) / 1000;
            player.sendMessage("§7Sophie is still thinking... try again in §f" +
                    secsLeft + "s§7.");
            return true;
        }
        lastUsed.put(player.getUniqueId(), now);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(" ");
            sb.append(args[i]);
        }

        plugin.getSophieManager().ask(player, sb.toString());
        return true;
    }
}