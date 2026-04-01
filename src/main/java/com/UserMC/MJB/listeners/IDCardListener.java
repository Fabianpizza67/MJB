package com.UserMC.MJB.listeners;

import com.UserMC.MJB.IDCardManager;
import com.UserMC.MJB.MJB;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class IDCardListener implements Listener {

    private final MJB plugin;

    public IDCardListener(MJB plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
                event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!plugin.getIDCardManager().isIDCard(held)) return;

        event.setCancelled(true);

        UUID ownerUuid = plugin.getIDCardManager().getCardOwner(held);
        if (ownerUuid == null) {
            player.sendMessage("§4This ID card is invalid.");
            return;
        }

        if (!plugin.getIDCardManager().isCardValid(held)) {
            player.sendMessage("§4§l[ID] §4This ID card has been reported as lost and is no longer valid.");
            return;
        }

        plugin.getIDCardManager().openIDCardGUI(player, ownerUuid);
    }

    // Close button in the ID card GUI
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!event.getView().getTitle().equals(IDCardManager.ID_CARD_GUI_TITLE)) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked != null && clicked.getType() == Material.BARRIER) {
            event.getWhoClicked().closeInventory();
        }
    }
}