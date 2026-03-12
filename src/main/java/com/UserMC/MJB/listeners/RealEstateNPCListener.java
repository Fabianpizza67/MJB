package com.UserMC.MJB.listeners;

import com.UserMC.MJB.MJB;
import com.UserMC.MJB.PropertyManager;
import com.UserMC.MJB.PropertyManager.PropertyListing;
import com.UserMC.MJB.PropertyManager.PurchaseResult;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RealEstateNPCListener implements Listener {

    private final MJB plugin;
    public static final String REALESTATE_NPC_TAG = "real_estate";

    private static final String BROWSE_GUI_TITLE  = "§b§lReal Estate Office";
    private static final String LISTINGS_GUI_TITLE = "§b§lAvailable Properties";
    private static final String CONFIRM_GUI_TITLE  = "§b§lConfirm Purchase";

    public RealEstateNPCListener(MJB plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onNPCRightClick(NPCRightClickEvent event) {
        NPC npc = event.getNPC();
        if (!npc.data().has(REALESTATE_NPC_TAG)) return;
        openBrowseMenu(event.getClicker());
    }

    // ---- Main browse menu ----

    private void openBrowseMenu(Player player) {
        Inventory gui = plugin.getServer().createInventory(null, 27, BROWSE_GUI_TITLE);

        double bank = plugin.getEconomyManager().getBankBalance(player.getUniqueId());

        gui.setItem(4, createItem(Material.GOLD_BLOCK, "§b§lReal Estate Office",
                "§7Your bank balance: §f" + plugin.getEconomyManager().format(bank),
                "§7Browse available properties below."));

        gui.setItem(10, createItem(Material.OAK_DOOR, "§f§lApartments",
                "§7View available apartments."));
        gui.setItem(12, createItem(Material.BRICKS, "§f§lHouses",
                "§7View available houses."));
        gui.setItem(14, createItem(Material.CHEST, "§f§lStores",
                "§7View available stores."));
        gui.setItem(16, createItem(Material.PAPER, "§f§lAll Listings",
                "§7Browse all available properties."));

        gui.setItem(22, createItem(Material.BARRIER, "§4Close", "§7Close this menu."));

        player.openInventory(gui);
    }

    // ---- Property listings menu ----

    private void openListingsMenu(Player player, String filterType) {
        List<PropertyListing> listings = filterType.equals("all")
                ? plugin.getPropertyManager().getAvailableListings()
                : plugin.getPropertyManager().getAvailableListingsByType(filterType);

        Inventory gui = plugin.getServer().createInventory(null, 54, LISTINGS_GUI_TITLE);

        if (listings.isEmpty()) {
            gui.setItem(22, createItem(Material.BARRIER, "§4No listings available",
                    "§7Check back later or ask an admin."));
        }

        int slot = 0;
        for (PropertyListing listing : listings) {
            if (slot >= 45) break;

            Material icon = getIconForType(listing.plotType);
            String typeLabel = formatType(listing.plotType);
            String sellerLabel = listing.isCityListing() ? "§7City" :
                    "§7" + plugin.getServer().getOfflinePlayer(listing.listedBy).getName();

            List<String> lore = new ArrayList<>();
            lore.add("§7Type: §f" + typeLabel);
            lore.add("§7District: §f" + capitalize(listing.district));
            lore.add("§7Region: §f" + listing.regionId);
            lore.add("§7Seller: " + sellerLabel);
            lore.add("§7Price: §b" + plugin.getEconomyManager().format(listing.price));
            lore.add("");
            lore.add("§eClick §7to purchase");

            ItemStack item = createItem(icon, "§f§l" + typeLabel + " §7— §b" + listing.regionId,
                    lore.toArray(new String[0]));

            // Store region + world in NBT
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "listing_region"),
                    PersistentDataType.STRING, listing.regionId);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "listing_world"),
                    PersistentDataType.STRING, listing.world);
            item.setItemMeta(meta);

            gui.setItem(slot++, item);
        }

        gui.setItem(45, createItem(Material.ARROW, "§fBack", "§7Return to main menu."));
        player.openInventory(gui);
    }

    // ---- Confirm purchase menu ----

    private void openConfirmMenu(Player player, String regionId, String world) {
        PropertyListing listing = plugin.getPropertyManager().getListing(regionId, world);
        if (listing == null) {
            player.sendMessage("§4This listing no longer exists.");
            return;
        }

        double bank = plugin.getEconomyManager().getBankBalance(player.getUniqueId());
        boolean canAfford = bank >= listing.price;

        Inventory gui = plugin.getServer().createInventory(null, 27, CONFIRM_GUI_TITLE);

        gui.setItem(4, createItem(getIconForType(listing.plotType),
                "§f§l" + formatType(listing.plotType) + " — §b" + regionId,
                "§7District: §f" + capitalize(listing.district),
                "§7Price: §b" + plugin.getEconomyManager().format(listing.price),
                "§7Your balance: §f" + plugin.getEconomyManager().format(bank),
                canAfford ? "§aYou can afford this!" : "§4Insufficient funds!"
        ));

        if (canAfford) {
            ItemStack confirm = createItem(Material.EMERALD, "§a§lConfirm Purchase",
                    "§7This will deduct §f" + plugin.getEconomyManager().format(listing.price),
                    "§7from your bank account.",
                    "§7The plot will be yours immediately.");
            ItemMeta meta = confirm.getItemMeta();
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "listing_region"),
                    PersistentDataType.STRING, regionId);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "listing_world"),
                    PersistentDataType.STRING, world);
            confirm.setItemMeta(meta);
            gui.setItem(11, confirm);
        }

        gui.setItem(15, createItem(Material.BARRIER, "§4Cancel", "§7Go back to listings."));
        player.openInventory(gui);
    }

    // ---- Click handler ----

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        if (!title.equals(BROWSE_GUI_TITLE) && !title.equals(LISTINGS_GUI_TITLE)
                && !title.equals(CONFIRM_GUI_TITLE)) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (title.equals(BROWSE_GUI_TITLE)) {
            switch (clicked.getType()) {
                case OAK_DOOR  -> openListingsMenu(player, "apartment");
                case BRICKS    -> openListingsMenu(player, "house");
                case CHEST     -> openListingsMenu(player, "store");
                case PAPER     -> openListingsMenu(player, "all");
                case BARRIER   -> player.closeInventory();
            }
            return;
        }

        if (title.equals(LISTINGS_GUI_TITLE)) {
            if (clicked.getType() == Material.ARROW) { openBrowseMenu(player); return; }

            String regionId = getStringNBT(clicked, "listing_region");
            String world = getStringNBT(clicked, "listing_world");
            if (regionId == null || world == null) return;

            openConfirmMenu(player, regionId, world);
            return;
        }

        if (title.equals(CONFIRM_GUI_TITLE)) {
            if (clicked.getType() == Material.BARRIER) {
                openListingsMenu(player, "all");
                return;
            }

            if (clicked.getType() == Material.EMERALD) {
                String regionId = getStringNBT(clicked, "listing_region");
                String world = getStringNBT(clicked, "listing_world");
                if (regionId == null || world == null) return;

                player.closeInventory();
                handlePurchase(player, regionId, world);
            }
        }
    }

    // ---- Purchase handler ----

    private void handlePurchase(Player player, String regionId, String world) {
        PurchaseResult result = plugin.getPropertyManager().purchase(player, regionId, world);

        switch (result) {
            case SUCCESS -> {
                PropertyListing listing = plugin.getPropertyManager().getListing(regionId, world);
                player.sendMessage("§b§m-----------------------------");
                player.sendMessage("§b§l  Property Purchased!");
                player.sendMessage("§b§m-----------------------------");
                player.sendMessage("§fYou are now the owner of §b" + regionId + "§f.");
                player.sendMessage("§7You can build and modify it freely.");
                player.sendMessage("§b§m-----------------------------");
            }
            case INSUFFICIENT_FUNDS ->
                    player.sendMessage("§4You don't have enough bank balance for this property.");
            case NOT_AVAILABLE ->
                    player.sendMessage("§4This property was just sold. Please browse other listings.");
            case NOT_LISTED ->
                    player.sendMessage("§4This property is no longer listed.");
            case OWN_LISTING ->
                    player.sendMessage("§4You can't buy your own listing.");
            case ERROR ->
                    player.sendMessage("§4An error occurred. Please try again.");
        }
    }

    // ---- Helpers ----

    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    private String getStringNBT(ItemStack item, String key) {
        if (!item.hasItemMeta()) return null;
        NamespacedKey nk = new NamespacedKey(plugin, key);
        if (!item.getItemMeta().getPersistentDataContainer().has(nk, PersistentDataType.STRING)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(nk, PersistentDataType.STRING);
    }

    private Material getIconForType(String type) {
        return switch (type.toLowerCase()) {
            case "house"             -> Material.BRICKS;
            case "store"             -> Material.CHEST;
            case "starter_apartment" -> Material.OAK_DOOR;
            default                  -> Material.OAK_DOOR;
        };
    }

    private String formatType(String type) {
        return switch (type.toLowerCase()) {
            case "starter_apartment" -> "Starter Apartment";
            case "apartment"         -> "Apartment";
            case "house"             -> "House";
            case "store"             -> "Store";
            default                  -> capitalize(type);
        };
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);

    }
    public void openBrowseMenuPublic(Player player) { openBrowseMenu(player); }
}