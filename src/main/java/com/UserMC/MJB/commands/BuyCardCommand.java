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

        if (!plugin.getBankNPCListener().isNearBankTeller(player)) {
            player.sendMessage("§4You need to be at a bank to buy a debit card.");
            return true;
        }

        // Check if they already have a valid (current version, non-cancelled) card
        if (plugin.getDebitCardManager().playerHasValidCard(player)) {
            player.sendMessage("§4You already have an active debit card!");
            player.sendMessage("§7If it was stolen, use §f/cancelcard §7first, then buy a new one.");
            return true;
        }

        double price = plugin.getDebitCardManager().CARD_PRICE;
        double bank = plugin.getEconomyManager().getBankBalance(player.getUniqueId());

        if (bank < price) {
            player.sendMessage("§4Insufficient bank balance.");
            player.sendMessage("§7A debit card costs §f" + plugin.getEconomyManager().format(price) + "§7.");
            return true;
        }

        // Increment card version — this invalidates ALL previous physical cards
        // No reinstate needed — version bump handles it
        int newVersion = plugin.getDebitCardManager().incrementCardVersion(player.getUniqueId());
        if (newVersion == -1) {
            player.sendMessage("§4An error occurred. Please try again.");
            return true;
        }

        // Clear any cancellation flag now that a new card is being issued
        plugin.getDebitCardManager().clearCancellation(player.getUniqueId());

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

        // Give card with the new version baked in
        player.getInventory().addItem(plugin.getDebitCardManager().createDebitCard(player, newVersion));
        player.sendMessage("§b§m-----------------------------");
        player.sendMessage("§b§l  Debit Card Issued!");
        player.sendMessage("§b§m-----------------------------");
        player.sendMessage("§f" + plugin.getEconomyManager().format(price) + " §7has been deducted from your bank.");
        player.sendMessage("§7Keep your card safe — it can be stolen!");
        player.sendMessage("§7Any previously issued cards are now §4invalid§7.");
        player.sendMessage("§b§m-----------------------------");

        return true;
    }
}