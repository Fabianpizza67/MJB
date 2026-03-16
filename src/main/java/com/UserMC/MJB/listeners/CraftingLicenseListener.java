package com.UserMC.MJB.listeners;

import com.UserMC.MJB.MJB;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;

import java.util.Set;

public class CraftingLicenseListener implements Listener {

    private final MJB plugin;

    // Trim template materials — all smithing templates that are armor trims (not netherite upgrade)
    private static final Set<Material> TRIM_TEMPLATES = Set.of(
            Material.WARD_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.VEX_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.RIB_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.EYE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.WILD_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.HOST_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE,
            Material.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE
    );

    public CraftingLicenseListener(MJB plugin) {
        this.plugin = plugin;
    }

    // ---- Regular crafting table ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Material result = event.getRecipe().getResult().getType();
        String requiredLicense = plugin.getCraftingLicenseManager().getRequiredLicense(result);
        if (requiredLicense == null) return; // not gated

        if (!plugin.getLicenseManager().hasActiveLicense(player.getUniqueId(), requiredLicense)) {
            event.setCancelled(true);
            plugin.getLicenseManager().getLicenseType(requiredLicense);
            String displayName = plugin.getLicenseManager().getLicenseType(requiredLicense) != null
                    ? plugin.getLicenseManager().getLicenseType(requiredLicense).displayName
                    : requiredLicense;
            player.sendMessage("§4You need a §f" + displayName + " §4to craft this.");
            player.sendMessage("§7Purchase one at the §fGovernment Office§7.");
        }
    }

    // ---- Smithing table — armor trims ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSmithing(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory() instanceof SmithingInventory smithing)) return;

        // Only care about clicking the result slot (slot 3)
        if (event.getRawSlot() != 3) return;

        ItemStack result = smithing.getResult();
        if (result == null || result.getType() == Material.AIR) return;

        // Check if this is an armor trim operation (template slot = slot 0)
        ItemStack template = smithing.getItem(0);
        if (template == null || !TRIM_TEMPLATES.contains(template.getType())) return;

        // It's a trim — require tailor license
        if (!plugin.getLicenseManager().hasActiveLicense(player.getUniqueId(), "tailor")) {
            event.setCancelled(true);
            String displayName = plugin.getLicenseManager().getLicenseType("tailor") != null
                    ? plugin.getLicenseManager().getLicenseType("tailor").displayName
                    : "Tailor License";
            player.sendMessage("§4You need a §f" + displayName + " §4to apply armor trims.");
            player.sendMessage("§7Purchase one at the §fGovernment Office§7.");
        }
    }
}