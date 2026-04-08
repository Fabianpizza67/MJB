package com.UserMC.MJB.listeners;

import com.UserMC.MJB.MJB;
import com.UserMC.MJB.TerminalManager.TerminalData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class TerminalListener implements Listener {

    private final MJB plugin;

    public TerminalListener(MJB plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // Ignore off-hand firing — prevents double trigger
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.PURPUR_STAIRS) return;

        org.bukkit.Location loc = event.getClickedBlock().getLocation();
        if (!plugin.getTerminalManager().isTerminal(loc)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        TerminalData data = plugin.getTerminalManager().getTerminalData(loc);

        // Must be holding a debit card
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!plugin.getDebitCardManager().isDebitCard(held)) {
            player.sendMessage("§4Hold a debit card to pay here.");
            player.sendMessage("§7Store owner: use §f/terminal set <price> §7to set the price.");
            return;
        }

        if (data.currentPrice <= 0) {
            player.sendMessage("§4No price has been set on this terminal yet.");
            return;
        }

        // Charge whoever the card belongs to — not necessarily the holder
        UUID cardOwner = plugin.getDebitCardManager().getCardOwner(held);
        if (cardOwner == null) {
            player.sendMessage("§4This card is invalid.");
            return;
        }

        // Check if card is cancelled
        if (!plugin.getDebitCardManager().isCardValid(held)) {
            player.sendMessage("§4This card is invalid or has been cancelled.");
            return;
        }

        double balance = plugin.getEconomyManager().getBankBalance(cardOwner);
        String cardOwnerName = plugin.getServer().getOfflinePlayer(cardOwner).getName();

        if (balance < data.currentPrice) {
            player.sendMessage("§4Insufficient funds on this card.");
            player.sendMessage("§7Price: §f" + plugin.getEconomyManager().format(data.currentPrice));
            return;
        }

        String terminalRegion = plugin.getPlotManager().getRegionAtLocation(loc);
        boolean success;
        if (terminalRegion != null) {
            int companyId = plugin.getCompanyManager().getCompanyForPlot(terminalRegion, loc.getWorld().getName());
            if (companyId != -1) {
                // Pay into company bank
                success = plugin.getCompanyManager().payFromPlayerToCompany(cardOwner, companyId, data.currentPrice);
            } else {
                // Pay to terminal owner's personal bank
                success = plugin.getEconomyManager().transferBank(cardOwner, data.ownerUuid, data.currentPrice);
            }
        } else {
            success = plugin.getEconomyManager().transferBank(cardOwner, data.ownerUuid, data.currentPrice);
        }

        if (success) {
            player.sendMessage("§b§m-----------------------------");
            player.sendMessage("§b§l  Payment Successful");
            player.sendMessage("§b§m-----------------------------");
            player.sendMessage("§7Amount: §f" + plugin.getEconomyManager().format(data.currentPrice));
            player.sendMessage("§7Charged to: §b" + cardOwnerName);
            player.sendMessage("§7Paid to: §b" + plugin.getServer().getOfflinePlayer(data.ownerUuid).getName());
            player.sendMessage("§b§m-----------------------------");

            // Alert the card owner if someone else used their card
            if (!cardOwner.equals(player.getUniqueId())) {
                Player cardOwnerPlayer = plugin.getServer().getPlayer(cardOwner);
                if (cardOwnerPlayer != null) {
                    cardOwnerPlayer.sendMessage("§4§l[Alert] §fSomeone used your debit card and charged §b" +
                            plugin.getEconomyManager().format(data.currentPrice) + "§f!");
                    cardOwnerPlayer.sendMessage("§7If this wasn't you, go to the bank and use §f/cancelcard§7.");
                }
            }

            // Notify the store owner
            Player storeOwner = plugin.getServer().getPlayer(data.ownerUuid);
            if (storeOwner != null) {
                storeOwner.sendMessage("§b§l[Terminal] §f" + player.getName() + " §fpaid §b" +
                        plugin.getEconomyManager().format(data.currentPrice) + " §fat your terminal!");
            }

            // Reset price after payment
            plugin.getTerminalManager().setPrice(loc, 0);
        } else {
            player.sendMessage("§4Payment failed. Please try again.");
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.PURPUR_STAIRS) return;
        org.bukkit.Location loc = event.getBlock().getLocation();
        if (!plugin.getTerminalManager().isTerminal(loc)) return;

        Player player = event.getPlayer();
        TerminalData data = plugin.getTerminalManager().getTerminalData(loc);

        if (!data.ownerUuid.equals(player.getUniqueId()) && !player.hasPermission("mjb.admin")) {
            event.setCancelled(true);
            player.sendMessage("§4You can't break someone else's terminal.");
            return;
        }

        plugin.getTerminalManager().unregisterTerminal(loc);
        player.sendMessage("§7Terminal unregistered.");
    }
}