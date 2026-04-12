package com.UserMC.MJB.listeners;

import com.UserMC.MJB.DrugManager;
import com.UserMC.MJB.MJB;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

public class DrugListener implements Listener {

    private final MJB plugin;

    // Blocks that drug plants grow on
    private static final Set<Material> PLANTABLE_GROUND = Set.of(
            Material.GRASS_BLOCK,
            Material.DIRT,
            Material.PODZOL,
            Material.COARSE_DIRT,
            Material.ROOTED_DIRT
    );

    // Drug plant blocks (must be registered in DB to be harvestable)
    private static final Set<Material> DRUG_PLANT_BLOCKS = Set.of(
            Material.SHORT_GRASS,
            Material.FERN,
            Material.SWEET_BERRY_BUSH
    );

    public DrugListener(MJB plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        Action action = event.getAction();

        // ---- Seed planting — right-click ground with seed ----
        if ((action == Action.RIGHT_CLICK_BLOCK) &&
                plugin.getDrugManager().isSeed(held)) {
            Block clicked = event.getClickedBlock();
            if (clicked == null) return;
            if (!PLANTABLE_GROUND.contains(clicked.getType())) return;

            DrugManager.DrugType type = plugin.getDrugManager().getSeedDrugType(held);
            if (type == null) return;
            String regionId = plugin.getPlotManager()
                    .getRegionAtLocation(clicked.getLocation());
            if (regionId == null || plugin.getPlotManager().getPlotInfo(regionId, player.getWorld()).members.contains(player.getUniqueId())) {
                player.sendMessage("§4You can only plant on a plot where you have permission to build.");
                event.setCancelled(true);
                return;
            }

            Material plantMat = plugin.getDrugManager().getPlantMaterial(type);
            if (plantMat == null) return;

            Block above = clicked.getRelative(BlockFace.UP);
            if (above.getType() != Material.AIR) {
                player.sendMessage("§4There's no space above to plant here.");
                return;
            }

            event.setCancelled(true);

            above.setType(plantMat);
            plugin.getDrugManager().registerPlant(above.getLocation(), type);

            // Consume one seed
            if (held.getAmount() > 1) held.setAmount(held.getAmount() - 1);
            else player.getInventory().setItemInMainHand(null);

            player.sendMessage("§aYou planted §f" + type.displayName +
                    " seeds§a. Come back in §f4 hours §ato harvest.");
            return;
        }

        // ---- Drug use — right-click air or block with drug item ----
        if ((action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) &&
                plugin.getDrugManager().isDrug(held)) {

            // Don't trigger drug use when clicking a harvestable plant
            if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null &&
                    DRUG_PLANT_BLOCKS.contains(event.getClickedBlock().getType())) {
                // Fall through to harvest handler below
            } else {
                event.setCancelled(true);
                plugin.getDrugManager().useDrug(player, held);
                return;
            }
        }

        // ---- Plant harvesting — right-click a drug plant block ----
        if (action != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!DRUG_PLANT_BLOCKS.contains(block.getType())) return;

        event.setCancelled(true);
        plugin.getDrugManager().handleHarvest(player, block);
    }

    // Unregister from DB if a plant block is broken
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!DRUG_PLANT_BLOCKS.contains(block.getType())) return;
        if (!plugin.getDrugManager().isRegisteredPlant(block.getLocation())) return;

        plugin.getDrugManager().unregisterPlant(block.getLocation());
        event.getPlayer().sendMessage("§7Your drug plant has been removed.");
    }
}