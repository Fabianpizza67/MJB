package com.UserMC.MJB.listeners;

import com.UserMC.MJB.MJB;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class HousingNPCListener implements Listener {

    private final MJB plugin;
    public static final String HOUSING_NPC_TAG = "housing_office";

    public HousingNPCListener(MJB plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onNPCRightClick(NPCRightClickEvent event) {
        NPC npc = event.getNPC();
        if (!npc.data().has(HOUSING_NPC_TAG)) return;

        Player player = event.getClicker();

        // Check if already claimed a starter
        if (plugin.getPlotManager().hasClaimedStarter(player.getUniqueId())) {
            player.sendMessage("§eYou have already claimed your starter apartment.");
            player.sendMessage("§7You need to purchase a new property to upgrade.");
            return;
        }

        // Try to claim a random starter apartment
        String regionId = plugin.getPlotManager().claimStarterApartment(player);

        if (regionId == null) {
            player.sendMessage("§cSorry, there are no starter apartments available right now.");
            player.sendMessage("§7Please wait for an admin to add more units.");
            return;
        }

        player.sendMessage("§8§m-----------------------------");
        player.sendMessage("§6§lWelcome to your new home!");
        player.sendMessage("§8§m-----------------------------");
        player.sendMessage("§eYou have been assigned apartment: §f" + regionId);
        player.sendMessage("§eThis is your starter apartment.");
        player.sendMessage("§7You can place and break blocks inside it freely.");
        player.sendMessage("§c§lRemember: §7You will lose this apartment");
        player.sendMessage("§7once your balance exceeds the threshold!");
        player.sendMessage("§8§m-----------------------------");
    }
}