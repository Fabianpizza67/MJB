package com.UserMC.MJB.commands;

import com.UserMC.MJB.MJB;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BuyPhoneCommand implements CommandExecutor {

    private final MJB plugin;
    public static final double PHONE_PRICE = 100.0;

    public BuyPhoneCommand(MJB plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        double balance = plugin.getEconomyManager().getBankBalance(player.getUniqueId());
        if (balance < PHONE_PRICE) {
            player.sendMessage("§4Insufficient bank balance. A replacement phone costs §f" +
                    plugin.getEconomyManager().format(PHONE_PRICE) + "§4.");
            return true;
        }

        // Get their existing number (or assign one if somehow missing)
        String number = plugin.getPhoneManager().getPhoneNumber(player.getUniqueId());
        if (number == null) {
            number = plugin.getPhoneManager().assignPhoneNumber(player.getUniqueId());
        }

        // Deduct cost from bank
        String sql = "UPDATE players SET bank_balance = bank_balance - ? WHERE uuid = ?";
        try (java.sql.PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setDouble(1, PHONE_PRICE);
            stmt.setString(2, player.getUniqueId().toString());
            stmt.executeUpdate();
        } catch (java.sql.SQLException e) {
            player.sendMessage("§4An error occurred. Please try again.");
            plugin.getLogger().severe("BuyPhone payment error: " + e.getMessage());
            return true;
        }

        player.getInventory().addItem(plugin.getPhoneManager().createPhone(number));

        player.sendMessage("§b§m-----------------------------");
        player.sendMessage("§b§l  Replacement Phone");
        player.sendMessage("§b§m-----------------------------");
        player.sendMessage("§fYou received a replacement phone.");
        player.sendMessage("§7Your number is still: §b" + number);
        player.sendMessage("§7Cost: §f" + plugin.getEconomyManager().format(PHONE_PRICE) +
                " §7deducted from bank.");
        player.sendMessage("§b§m-----------------------------");
        return true;
    }
}