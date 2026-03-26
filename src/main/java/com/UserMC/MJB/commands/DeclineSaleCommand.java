package com.UserMC.MJB.commands;

import com.UserMC.MJB.CompanyManager;
import com.UserMC.MJB.MJB;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DeclineSaleCommand implements CommandExecutor {
    private final MJB plugin;
    public DeclineSaleCommand(MJB plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player buyer)) { sender.sendMessage("Players only."); return true; }

        CompanyManager.CompanySaleOffer offer =
                plugin.getCompanyManager().getSaleOffer(buyer.getUniqueId());
        if (offer == null) {
            buyer.sendMessage("§4You don't have a pending sale offer.");
            return true;
        }

        plugin.getCompanyManager().removeSaleOffer(buyer.getUniqueId());
        buyer.sendMessage("§7You declined the sale offer.");

        Player seller = plugin.getServer().getPlayer(offer.sellerUuid);
        if (seller != null) {
            seller.sendMessage("§7§b" + buyer.getName() +
                    " §7declined your company sale offer.");
        }
        return true;
    }
}