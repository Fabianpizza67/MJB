package com.UserMC.MJB.listeners;

import com.UserMC.MJB.MJB;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class BankNPCListener implements Listener {

    private final MJB plugin;
    public static final String BANK_TELLER_TAG = "bank_teller";
    private static final double BANK_RADIUS = 5.0;

    public BankNPCListener(MJB plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onNPCRightClick(NPCRightClickEvent event) {
        NPC npc = event.getNPC();
        if (!npc.data().has(BANK_TELLER_TAG)) return;

        Player player = event.getClicker();
        double bank = plugin.getEconomyManager().getBankBalance(player.getUniqueId());
        double cash = plugin.getEconomyManager().getCashBalance(player.getUniqueId());

        player.sendMessage("§b§m-----------------------------");
        player.sendMessage("§b§l  " + npc.getName());
        player.sendMessage("§b§m-----------------------------");
        player.sendMessage("§fGood day, §b" + player.getName() + "§f!");
        player.sendMessage("§fHere is your current balance:");
        player.sendMessage("§7  Cash on hand: §f" + plugin.getEconomyManager().format(cash));
        player.sendMessage("§7  Bank balance: §f" + plugin.getEconomyManager().format(bank));
        player.sendMessage("§b§m-----------------------------");
        player.sendMessage("§7Use §f/deposit <amount> §7or §f/withdraw <amount>");

        if (!plugin.getDebitCardManager().playerHasValidCard(player)) {
            player.sendMessage("§7Buy a debit card: §f/buycard");
        }

        player.sendMessage("§b§m-----------------------------");
    }

    // Checks if player is within range of a bank teller NPC
    public boolean isNearBankTeller(Player player) {
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (!npc.data().has(BANK_TELLER_TAG)) continue;
            if (!npc.isSpawned()) continue;
            Entity entity = npc.getEntity();
            if (entity == null) continue;
            if (!entity.getWorld().equals(player.getWorld())) continue;
            if (entity.getLocation().distance(player.getLocation()) <= BANK_RADIUS) {
                return true;
            }
        }
        return false;
    }
}