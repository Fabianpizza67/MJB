package com.UserMC.MJB.commands;

import com.UserMC.MJB.listeners.BankNPCListener;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AdminCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!player.hasPermission("mjb.admin")) {
            player.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§cUsage: /mjbadmin <subcommand>");
            player.sendMessage("§7Subcommands: §fsetbankteller, removebankteller, listbanktellers");
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "setbankteller" -> {
                if (args.length != 2) {
                    player.sendMessage("§cUsage: /mjbadmin setbankteller <npc_id>");
                    return true;
                }
                int id;
                try {
                    id = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cInvalid NPC ID.");
                    return true;
                }
                NPC npc = CitizensAPI.getNPCRegistry().getById(id);
                if (npc == null) {
                    player.sendMessage("§cNo NPC found with ID " + id + ".");
                    return true;
                }
                npc.data().setPersistent(BankNPCListener.BANK_TELLER_TAG, true);
                player.sendMessage("§aNPC §f" + npc.getName() + " §a(ID: " + id + ") is now a bank teller!");
            }

            case "removebankteller" -> {
                if (args.length != 2) {
                    player.sendMessage("§cUsage: /mjbadmin removebankteller <npc_id>");
                    return true;
                }
                int id;
                try {
                    id = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cInvalid NPC ID.");
                    return true;
                }
                NPC npc = CitizensAPI.getNPCRegistry().getById(id);
                if (npc == null) {
                    player.sendMessage("§cNo NPC found with ID " + id + ".");
                    return true;
                }
                npc.data().remove(BankNPCListener.BANK_TELLER_TAG);
                player.sendMessage("§aNPC §f" + npc.getName() + " §a(ID: " + id + ") is no longer a bank teller.");
            }

            case "listbanktellers" -> {
                player.sendMessage("§6§l--- Bank Teller NPCs ---");
                boolean found = false;
                for (NPC npc : CitizensAPI.getNPCRegistry()) {
                    if (npc.data().has(BankNPCListener.BANK_TELLER_TAG)) {
                        player.sendMessage("§a- " + npc.getName() + " §7(ID: " + npc.getId() + ")");
                        found = true;
                    }
                }
                if (!found) player.sendMessage("§7No bank teller NPCs found.");
            }

            default -> {
                player.sendMessage("§cUnknown subcommand. Use: setbankteller, removebankteller, listbanktellers");
            }
        }

        return true;
    }
}