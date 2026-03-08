package com.UserMC.MJB.commands;

import com.UserMC.MJB.MJB;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BuyCardCommand implements CommandExecutor {

    private final MJB plugin;

    public BuyCardCommand(MJB plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        // Must be near a bank teller
        if (!plugin.getBankNPCListener().isNearBankTeller(player)) {
            player.sendMessage("§4You need to be at a bank to buy a debit card.");
            return true;
        }

        // Check if they already have one
        if (plugin.getDebitCardManager().playerHasCard(player)) {
            player.sendMessage("§4You already have a debit card!");
            player.sendMessage("§7Lost it? Visit the bank and use §f/buycard §7to get a replacement.");
            return true;
        }

        double price = plugin.getDebitCardManager().CARD_PRICE;
        double bank = plugin.getEconomyManager().getBankBalance(player.getUniqueId());

        if (bank < price) {
            player.sendMessage("§4Insufficient bank balance.");
            player.sendMessage("§7A debit card costs §f" + plugin.getEconomyManager().format(price) + "§7.");
            return true;
        }

        // Deduct from bank
        String sql = "UPDATE players SET bank_balance = bank_balance - ? WHERE uuid = ?";
        try (java.sql.PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setDouble(1, price);
            stmt.setString(2, player.getUniqueId().toString());
            stmt.executeUpdate();
        } catch (java.sql.SQLException e) {
            player.sendMessage("§4An error occurred. Please try again.");
            return true;
        }

        // Give card
        player.getInventory().addItem(plugin.getDebitCardManager().createDebitCard(player));
        player.sendMessage("§b§m-----------------------------");
        player.sendMessage("§b§l  Debit Card Issued!");
        player.sendMessage("§b§m-----------------------------");
        player.sendMessage("§f" + plugin.getEconomyManager().format(price) + " §7has been deducted from your bank.");
        player.sendMessage("§7Keep your card safe — it can be stolen!");
        player.sendMessage("§b§m-----------------------------");

        return true;
    }
}