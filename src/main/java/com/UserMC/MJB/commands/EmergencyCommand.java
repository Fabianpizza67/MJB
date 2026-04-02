package com.UserMC.MJB.commands;

import com.UserMC.MJB.MJB;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class EmergencyCommand implements CommandExecutor {

    private final MJB plugin;

    // ---- Keyword lists ----
    private static final String[] POLICE_KEYWORDS = {
            "shot", "shooting", "gunfire", "gun", "armed", "robbery", "robbed",
            "stolen", "steal", "fight", "attack", "attacked", "assault",
            "murder", "kill", "killed", "weapon", "hostage", "threat",
            "breaking in", "break in", "stabbed", "knife"
    };
    private static final String[] MEDICAL_KEYWORDS = {
            "hurt", "injured", "injury", "bleeding", "unconscious", "downed",
            "overdose", "sick", "collapsed", "collapse", "heart", "breathing",
            "ambulance", "medic", "doctor", "hospital", "broken", "burn",
            "burned", "poisoned"
    };
    private static final String[] BOTH_KEYWORDS = {
            "fire", "burning", "explosion", "explode", "crash", "accident"
    };

    public EmergencyCommand(MJB plugin) {
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
            player.sendMessage("§4Usage: /" + label + " <message>");
            player.sendMessage("§7Example: §f/" + label + " shots fired near Main Street!");
            return true;
        }

        StringBuilder sb = new StringBuilder();
        for (String arg : args) sb.append(arg).append(" ");
        String msg = sb.toString().trim();
        String lower = msg.toLowerCase();

        // ---- Triage ----
        boolean needPolice  = false;
        boolean needMedical = false;

        for (String kw : POLICE_KEYWORDS)  if (lower.contains(kw)) { needPolice  = true; break; }
        for (String kw : MEDICAL_KEYWORDS) if (lower.contains(kw)) { needMedical = true; break; }
        for (String kw : BOTH_KEYWORDS)    if (lower.contains(kw)) { needPolice  = true; needMedical = true; break; }

        // Confirm to caller
        player.sendMessage("§c§l[911] §7Your call has been dispatched.");

        List<String> categories = new ArrayList<>();
        if (needPolice)  categories.add("§9POLICE§7");
        if (needMedical) categories.add("§aMEDICAL§7");
        if (!categories.isEmpty()) {
            player.sendMessage("§7Dispatched to: " + String.join(" + ", categories));
        }

        // ---- Build relay messages ----
        String baseInfo = "§7Caller: §f" + player.getName() +
                " §7near §f" + player.getWorld().getName() +
                " §f" + player.getLocation().getBlockX() +
                ", " + player.getLocation().getBlockZ();

        if (needPolice || needMedical) {
            if (needPolice) {
                String policeMsg = "§c§l[911][POLICE] §c" + player.getName() +
                        "§c: §f" + msg;
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    if (plugin.getPoliceManager().isOfficer(p.getUniqueId())) {
                        p.sendMessage(policeMsg);
                        p.sendMessage(baseInfo);
                    }
                }
            }
            if (needMedical) {
                String medMsg = "§a§l[911][MEDICAL] §a" + player.getName() +
                        "§a: §f" + msg;
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    if (plugin.getHospitalManager().isDoctor(p.getUniqueId())) {
                        p.sendMessage(medMsg);
                        p.sendMessage(baseInfo);
                    }
                }
            }
        } else {
            // No keywords matched — relay to all officers, no category tag
            int count = 0;
            String fallbackMsg = "§c§l[911] §c" + player.getName() + "§c: §f" + msg;
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (plugin.getPoliceManager().isOfficer(p.getUniqueId())) {
                    p.sendMessage(fallbackMsg);
                    p.sendMessage(baseInfo);
                    count++;
                }
            }
            if (count == 0) {
                player.sendMessage("§4§l[911] §4No officers are online. You're on your own!");
            }
        }

        // Console log with triage result
        String triageResult = needPolice && needMedical ? "POLICE+MEDICAL"
                : needPolice ? "POLICE"
                : needMedical ? "MEDICAL"
                : "UNKNOWN (no keywords)";
        plugin.getLogger().info("[911] " + player.getName() + " | Triage: " +
                triageResult + " | Message: " + msg);

        return true;
    }
}