package com.UserMC.MJB;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.UUID;

public class NameTagManager {

    private final MJB plugin;
    private final Scoreboard scoreboard;

    public NameTagManager(MJB plugin) {
        this.plugin = plugin;
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
    }

    public void refresh(Player player) {
        String prefix = buildPrefix(player.getUniqueId());
        String teamName = teamName(player.getUniqueId());

        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }

        team.setPrefix(prefix);

        // Only add the entry if not already in the team
        if (!team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }

        player.setScoreboard(scoreboard);
    }

    public void remove(Player player) {
        String teamName = teamName(player.getUniqueId());
        Team team = scoreboard.getTeam(teamName);
        if (team != null) {
            team.unregister();
        }
    }

    private String buildPrefix(UUID uuid) {
        // Priority 1: Mayor
        if (plugin.getGovernmentManager().isMayor(uuid)) {
            return "§6§l[Mayor] §r";
        }

        // Priority 2: Police rank
        if (plugin.getPoliceManager().isOfficer(uuid)) {
            CrimeManager.PoliceRank rank = plugin.getCrimeManager().getRank(uuid);
            String rankName = rank != null ? rank.displayName : "Officer";
            return "§9§l[" + rankName + "] §r";
        }

        // Priority 3: Party membership
        GovernmentManager.PartyInfo party =
                plugin.getGovernmentManager().getPartyByMember(uuid);
        if (party != null) {
            return "§5§l[" + truncate(party.name) + "] §r";
        }

        // Priority 4: Company membership
        CompanyManager.CompanyInfo company =
                plugin.getCompanyManager().getCompanyForPlayer(uuid);
        if (company != null) {
            return "§b§l[" + truncate(company.name) + "] §r";
        }

        // No role — plain white name
        return "§f";
    }

    private String truncate(String name) {
        if (name.length() <= 12) return name;
        return name.substring(0, 11) + ".";
    }

    // "mjb_" + first 12 chars of UUID without dashes = exactly 16 chars (team name limit)
    private String teamName(UUID uuid) {
        return "mjb_" + uuid.toString().replace("-", "").substring(0, 12);
    }
}