package com.UserMC.MJB.listeners;

import com.UserMC.MJB.MJB;
import com.UserMC.MJB.TerminalManager.TerminalData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TerminalListener implements Listener {

    private final MJB plugin;

    // Players currently setting a price — waiting for chat input
    private final Map<UUID, org.bukkit.Location> awaitingPrice = new HashMap<>();

    public TerminalListener(MJB plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.PURPUR_STAIRS) return;

        Player player = event.getPlayer();
        org.bukkit.Location loc = event.getClickedBlock().getLocation();

        // Sneaking = owner wants to set price
        if (player.isSneaking()) {
            if (!plugin.getTerminalManager().isTerminal(loc)) {
                player.sendMessage("§4This terminal is not registered.");
                return;
            }

            TerminalData data = plugin.getTerminalManager().getTerminalData(loc);
            if (!data.ownerUuid.equals(player.getUniqueId()) && !player.hasPermission("mjb.admin")) {
                player.sendMessage("§4This is not your terminal.");
                return;
            }

            event.setCancelled(true);
            player.sendMessage("§b§lTerminal — Set Price");
            player.sendMessage("§7Current price: §f" + plugin.getEconomyManager().format(data.currentPrice));
            player.sendMessage("§7Type the new price in chat, or §fcancel §7to abort.");
            awaitingPrice.put(player.getUniqueId(), loc);
            return;
        }

        // Not sneaking = customer wants to pay
        if (!plugin.getTerminalManager().isTerminal(loc)) return;

        ItemStack held = player.getInventory().getItemInMainHand();
        if (!plugin.getDebitCardManager().isDebitCard(held)) {
            player.sendMessage("§4You need to hold your debit card to pay.");
            player.sendMessage("§7Buy one at the bank!");
            return;
        }

        UUID cardOwner = plugin.getDebitCardManager().getCardOwner(held);
        if (cardOwner == null || !cardOwner.equals(player.getUniqueId())) {
            player.sendMessage("§4This debit card doesn't belong to you!");
            return;
        }

        TerminalData data = plugin.getTerminalManager().getTerminalData(loc);
        if (data.currentPrice <= 0) {
            player.sendMessage("§4No price has been set on this terminal yet.");
            return;
        }

        double balance = plugin.getEconomyManager().getBankBalance(player.getUniqueId());
        if (balance < data.currentPrice) {
            player.sendMessage("§4Insufficient bank balance.");
            player.sendMessage("§7Price: §f" + plugin.getEconomyManager().format(data.currentPrice));
            player.sendMessage("§7Your balance: §f" + plugin.getEconomyManager().format(balance));
            return;
        }

        // Process payment — deduct from customer, add to store owner
        boolean success = plugin.getEconomyManager().transferBank(
                player.getUniqueId(),
                data.ownerUuid,
                data.currentPrice
        );

        if (success) {
            event.setCancelled(true);
            player.sendMessage("§b§m-----------------------------");
            player.sendMessage("§b§l  Payment Successful");
            player.sendMessage("§b§m-----------------------------");
            player.sendMessage("§7Amount: §f" + plugin.getEconomyManager().format(data.currentPrice));
            player.sendMessage("§7Paid to: §b" + plugin.getServer().getOfflinePlayer(data.ownerUuid).getName());
            player.sendMessage("§b§m-----------------------------");

            // Notify the store owner if online
            Player owner = plugin.getServer().getPlayer(data.ownerUuid);
            if (owner != null) {
                owner.sendMessage("§b§l[Terminal] §f" + player.getName() + " §fpaid §b" +
                        plugin.getEconomyManager().format(data.currentPrice) + " §fat your store!");
            }

            // Reset terminal price to 0 after payment
            plugin.getTerminalManager().setPrice(loc, 0);
        } else {
            player.sendMessage("§4Payment failed. Please try again.");
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!awaitingPrice.containsKey(player.getUniqueId())) return;

        event.setCancelled(true);
        String message = event.getMessage().trim();

        if (message.equalsIgnoreCase("cancel")) {
            awaitingPrice.remove(player.getUniqueId());
            player.sendMessage("§7Price setting cancelled.");
            return;
        }

        double price;
        try {
            price = Double.parseDouble(message);
        } catch (NumberFormatException e) {
            player.sendMessage("§4Invalid amount. Type a number or §fcancel§4.");
            return;
        }

        if (price < 0) {
            player.sendMessage("§4Price cannot be negative.");
            return;
        }

        org.bukkit.Location loc = awaitingPrice.remove(player.getUniqueId());

        // Run on main thread since we're in async chat event
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            plugin.getTerminalManager().setPrice(loc, price);
            player.sendMessage("§fTerminal price set to §b" + plugin.getEconomyManager().format(price) + "§f.");
        });
    }
}