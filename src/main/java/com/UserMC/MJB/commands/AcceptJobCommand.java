package com.UserMC.MJB.commands;

import com.UserMC.MJB.CompanyManager;
import com.UserMC.MJB.MJB;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AcceptJobCommand implements CommandExecutor {
    private final MJB plugin;
    public AcceptJobCommand(MJB plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }

        CompanyManager.CompanyInvite invite = plugin.getCompanyManager().getInvite(player.getUniqueId());
        if (invite == null) {
            player.sendMessage("§4You don't have a pending job offer, or it has expired.");
            return true;
        }

        plugin.getCompanyManager().removeInvite(player.getUniqueId());
        plugin.getCompanyManager().addMember(invite.companyId, player.getUniqueId(), "employee", 0);

        CompanyManager.CompanyInfo info = plugin.getCompanyManager().getCompanyById(invite.companyId);
        String companyName = info != null ? info.name : "the company";

        player.sendMessage("§b§l[Company] §fYou accepted the job offer at §b" + companyName + "§f!");
        player.sendMessage("§7Your employer can set your salary from their computer.");

        Player inviter = plugin.getServer().getPlayer(invite.inviterUuid);
        if (inviter != null) {
            inviter.sendMessage("§b§l[Company] §b" + player.getName() +
                    " §faccepted your job offer and joined §b" + companyName + "§f!");
        }
        return true;
    }
}
