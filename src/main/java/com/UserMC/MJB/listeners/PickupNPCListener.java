package com.UserMC.MJB.listeners;

import com.UserMC.MJB.MJB;
import com.UserMC.MJB.SupplyOrderManager.Order;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;

import java.util.Arrays;
import java.util.List;

public class PickupNPCListener implements Listener {

    private final MJB plugin;
    private static final String PICKUP_GUI_TITLE = "§b§lPickup Orders";

    public PickupNPCListener(MJB plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onNPCRightClick(NPCRightClickEvent event) {
        NPC npc = event.getNPC();
        String district = plugin.getSupplyOrderManager().getDistrictForNPC(npc.getId());
        if (district == null) return;

        Player player = event.getClicker();
        openPickupMenu(player, district);
    }

    private void openPickupMenu(Player player, String district) {
        List<Order> readyOrders = plugin.getSupplyOrderManager().getReadyOrdersForDistrict(district);

        // Filter to orders the player is authorized to collect
        List<Order> authorized = readyOrders.stream()
                .filter(o -> plugin.getSupplyOrderManager().isAuthorized(o.id, player.getUniqueId()))
                .toList();

        if (authorized.isEmpty()) {
            player.sendMessage("§7No orders ready for pickup in §b" + district + "§7.");
            return;
        }

        Inventory gui = plugin.getServer().createInventory(null, 54, PICKUP_GUI_TITLE);

        int slot = 0;
        for (Order order : authorized) {
            if (slot >= 54) break;
            List<com.UserMC.MJB.SupplyOrderManager.OrderLine> lines =
                    plugin.getSupplyOrderManager().getOrderLines(order.id);

            String[] lore = new String[lines.size() + 3];
            lore[0] = "§7Order by: §b" + plugin.getServer().getOfflinePlayer(order.ownerUuid).getName();
            lore[1] = "§7Total: §f" + plugin.getEconomyManager().format(order.totalCost);
            lore[2] = "§7Contents:";
            for (int i = 0; i < lines.size(); i++) {
                lore[i + 3] = "§f  " + lines.get(i).quantity + "x " + lines.get(i).material;
            }

            ItemStack guiItem = createGuiItem(Material.CHEST,
                    "§fOrder §b#" + order.id, lore);
            guiItem.setAmount(1);

            // Store order ID in item NBT
            ItemMeta meta = guiItem.getItemMeta();
            meta.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(plugin, "order_id"),
                    org.bukkit.persistence.PersistentDataType.INTEGER,
                    order.id
            );
            guiItem.setItemMeta(meta);
            gui.setItem(slot++, guiItem);
        }

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(PICKUP_GUI_TITLE)) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Get order ID from NBT
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "order_id");
        if (!clicked.getItemMeta().getPersistentDataContainer().has(key, org.bukkit.persistence.PersistentDataType.INTEGER)) return;

        int orderId = clicked.getItemMeta().getPersistentDataContainer()
                .get(key, org.bukkit.persistence.PersistentDataType.INTEGER);

        player.closeInventory();
        boolean success = plugin.getSupplyOrderManager().collectOrder(orderId, player);

        if (success) {
            Order order = plugin.getSupplyOrderManager().getOrder(orderId);
            player.sendMessage("§b§m-----------------------------");
            player.sendMessage("§b§l  Order Collected!");
            player.sendMessage("§b§m-----------------------------");
            player.sendMessage("§fOrder §b#" + orderId + " §fhas been collected.");
            player.sendMessage("§7Remember: §fstock items §7cannot be personally consumed.");
            player.sendMessage("§b§m-----------------------------");

            // Notify owner if someone else collected it
            if (order != null && !order.ownerUuid.equals(player.getUniqueId())) {
                Player owner = plugin.getServer().getPlayer(order.ownerUuid);
                if (owner != null) {
                    owner.sendMessage("§b§l[Supply] §b" + player.getName() +
                            " §fcollected your order §b#" + orderId + "§f.");
                }
            }
        } else {
            player.sendMessage("§4Failed to collect order. Make sure you have enough inventory space!");
        }
    }

    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }
}