package com.UserMC.MJB.commands;

import com.UserMC.MJB.CompanyManager;
import com.UserMC.MJB.MJB;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AcceptSaleCommand implements CommandExecutor {
    private final MJB plugin;
    public AcceptSaleCommand(MJB plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player buyer)) { sender.sendMessage("Players only."); return true; }

        CompanyManager.CompanySaleOffer offer =
                plugin.getCompanyManager().getSaleOffer(buyer.getUniqueId());
        if (offer == null) {
            buyer.sendMessage("§4You don't have a pending sale offer, or it has expired.");
            return true;
        }

        plugin.getCompanyManager().removeSaleOffer(buyer.getUniqueId());

        boolean ok = plugin.getCompanyManager()
                .sellCompany(offer.companyId, buyer.getUniqueId(), offer.price);

        if (!ok) {
            buyer.sendMessage("§4Sale failed — you may not have enough bank balance.");
            return true;
        }

        CompanyManager.CompanyInfo info =
                plugin.getCompanyManager().getCompanyById(offer.companyId);
        String companyName = info != null ? info.name : "the company";

        buyer.sendMessage("§b§l[Company] §fYou are now the owner of §b" + companyName +
                "§f! §b" + plugin.getEconomyManager().format(offer.price) +
                " §fhas been deducted from your bank.");

        Player seller = plugin.getServer().getPlayer(offer.sellerUuid);
        if (seller != null) {
            seller.sendMessage("§b§l[Company] §b" + buyer.getName() +
                    " §faccepted your sale offer! §b" +
                    plugin.getEconomyManager().format(offer.price) +
                    " §fhas been added to your bank.");
        }

        plugin.getNameTagManager().refresh(buyer);
        return true;
    }
}