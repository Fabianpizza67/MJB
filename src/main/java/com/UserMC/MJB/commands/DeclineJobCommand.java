package com.UserMC.MJB.commands;

import com.UserMC.MJB.CompanyManager;
import com.UserMC.MJB.MJB;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DeclineJobCommand implements CommandExecutor {
    private final MJB plugin;
    public DeclineJobCommand(MJB plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }

        CompanyManager.CompanyInvite invite = plugin.getCompanyManager().getInvite(player.getUniqueId());
        if (invite == null) {
            player.sendMessage("§4You don't have a pending job offer.");
            return true;
        }

        plugin.getCompanyManager().removeInvite(player.getUniqueId());
        CompanyManager.CompanyInfo info = plugin.getCompanyManager().getCompanyById(invite.companyId);
        player.sendMessage("§7You declined the job offer from §b" + (info != null ? info.name : "a company") + "§7.");

        Player inviter = plugin.getServer().getPlayer(invite.inviterUuid);
        if (inviter != null) {
            inviter.sendMessage("§7§b" + player.getName() + " §7declined your job offer.");
        }
        return true;
    }
}
