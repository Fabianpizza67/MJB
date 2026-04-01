package com.UserMC.MJB.listeners;

import com.UserMC.MJB.MJB;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public class PoliceListener implements Listener {

    private final MJB plugin;

    public PoliceListener(MJB plugin) {
        this.plugin = plugin;
    }

    // ---- Right-click player with handcuffs to cuff/uncuff ----

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Player target)) return;

        Player officer = event.getPlayer();
        ItemStack held = officer.getInventory().getItemInMainHand();

        // ---- Handcuffs: cuff or uncuff ----
        if (plugin.getPoliceManager().isHandcuffs(held)) {
            event.setCancelled(true);

            if (!plugin.getPoliceManager().isOfficer(officer.getUniqueId())) {
                officer.sendMessage("§4You are not a police officer.");
                return;
            }

            if (plugin.getPoliceManager().isCuffed(target.getUniqueId())) {
                // Already cuffed — uncuff
                plugin.getPoliceManager().uncuff(target.getUniqueId());
                officer.sendMessage("§f§l[Police] §fYou released §b" + target.getName() + "§f.");
                target.sendMessage("§f§l[Police] §fYou have been released by §b" + officer.getName() + "§f.");
                target.setWalkSpeed(0.2f);
            } else {
                // Cuff them
                plugin.getPoliceManager().cuff(target.getUniqueId(), officer.getUniqueId());
                officer.sendMessage("§f§l[Police] §fYou arrested §b" + target.getName() + "§f.");
                target.sendMessage("§c§l[Police] §cYou have been arrested by §f" + officer.getName() + "§c!");
                target.sendMessage("§7Do not resist. You will be escorted to the station.");

                // Notify nearby players
                officer.getWorld().getNearbyPlayers(officer.getLocation(), 20).forEach(nearby -> {
                    if (!nearby.equals(officer) && !nearby.equals(target)) {
                        nearby.sendMessage("§8[Police] §7" + officer.getName() +
                                " has arrested §f" + target.getName() + "§7.");
                    }
                });
            }
            return;
        }

        // ---- Badge: search cuffed player ----
// ---- Badge: search cuffed player ----
        if (plugin.getPoliceManager().isBadge(held)) {
            event.setCancelled(true);

            if (!plugin.getPoliceManager().isOfficer(officer.getUniqueId())) {
                officer.sendMessage("§4You are not a police officer.");
                return;
            }

            if (!plugin.getPoliceManager().isCuffed(target.getUniqueId())) {
                officer.sendMessage("§4" + target.getName() + " is not cuffed. Arrest them first.");
                return;
            }

            List<ItemStack> seized = plugin.getPoliceManager().searchAndSeize(target);

            if (seized.isEmpty()) {
                officer.sendMessage("§f§l[Police] §fSearch of §b" + target.getName() +
                        " §fcomplete. §aNo illegal items found.");
                target.sendMessage("§f§l[Police] §fYou were searched by §b" + officer.getName() +
                        "§f. No illegal items found.");
            } else {
                ItemStack evidence = plugin.getPoliceManager()
                        .createEvidenceBag(officer.getName(), seized);
                officer.getInventory().addItem(evidence);
                officer.sendMessage("§f§l[Police] §fSearch of §b" + target.getName() +
                        " §fcomplete. §c" + seized.size() + " illegal item(s) seized.");
                target.sendMessage("§c§l[Police] §c" + officer.getName() +
                        " seized §f" + seized.size() + " §cillegal item(s) from you!");
                seized.forEach(item -> officer.sendMessage("§7  - §f" + item.getAmount() + "x " +
                        formatMaterial(item.getType().name())));
                plugin.getCrimeManager().addOffence(
                        target.getUniqueId(),
                        "Possession of illegal items (" + seized.size() +
                                " item(s) seized by " + officer.getName() + ")",
                        officer.getUniqueId()
                );
            }

            // Auto-check ID card if the no_id_card law is active
            if (plugin.getGovernmentManager().isNoIDCardIllegal()) {
                boolean hasValidID = false;
                for (ItemStack invItem : target.getInventory().getContents()) {
                    if (!plugin.getIDCardManager().isIDCard(invItem)) continue;
                    UUID cardOwner = plugin.getIDCardManager().getCardOwner(invItem);
                    if (cardOwner != null && cardOwner.equals(target.getUniqueId())
                            && plugin.getIDCardManager().isCardValid(invItem)) {
                        hasValidID = true;
                        break;
                    }
                }
                if (!hasValidID) {
                    plugin.getCrimeManager().addOffence(
                            target.getUniqueId(),
                            "No valid ID card found during search",
                            officer.getUniqueId()
                    );
                    officer.sendMessage("§c§l[Police] §c" + target.getName() +
                            " §cdoes not carry a valid ID card! Offence added.");
                    target.sendMessage("§c§l[Police] §cYou were found without a valid ID card. " +
                            "An offence has been added to your record.");
                }
            }
        }
    }

    // ---- Block cuffed players from using items ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemUse(PlayerInteractEvent event) {
        if (!plugin.getPoliceManager().isCuffed(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage("§c§l[Police] §cYou are handcuffed!");
    }

    // ---- Block cuffed players from opening inventories ----

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!plugin.getPoliceManager().isCuffed(player.getUniqueId())) return;
        event.setCancelled(true);
        player.sendMessage("§c§l[Police] §cYou are handcuffed!");
    }

    // ---- Block cuffed players from dropping items ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!plugin.getPoliceManager().isCuffed(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
    }

    // ---- Block cuffed players from sprinting ----

    @EventHandler
    public void onToggleSprint(PlayerToggleSprintEvent event) {
        if (!plugin.getPoliceManager().isCuffed(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
    }

    // ---- Cuffed players can't attack ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!plugin.getPoliceManager().isCuffed(attacker.getUniqueId())) return;
        event.setCancelled(true);
        attacker.sendMessage("§c§l[Police] §cYou are handcuffed!");
    }

    // ---- Uncuff on disconnect (officer) — clean up victims ----

    @EventHandler
    public void onOfficerQuit(PlayerQuitEvent event) {
        UUID officerUuid = event.getPlayer().getUniqueId();
        // Don't instantly uncuff — the 10-min scheduler in PoliceManager handles it
        // Just notify victims that officer disconnected
        for (UUID cuffed : plugin.getPoliceManager().getCuffedPlayers()) {
            if (officerUuid.equals(plugin.getPoliceManager().getCuffingOfficer(cuffed))) {
                Player victim = plugin.getServer().getPlayer(cuffed);
                if (victim != null) {
                    victim.sendMessage("§7§l[Police] §7Your arresting officer disconnected.");
                    victim.sendMessage("§7You will be automatically released in §f10 minutes§7.");
                }
            }
        }
    }

    // ---- Uncuff if victim disconnects ----

    @EventHandler
    public void onVictimQuit(PlayerQuitEvent event) {
        if (plugin.getPoliceManager().isCuffed(event.getPlayer().getUniqueId())) {
            plugin.getPoliceManager().uncuff(event.getPlayer().getUniqueId());
        }
    }

    // ---- Helper ----

    private String formatMaterial(String material) {
        String[] words = material.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words)
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
        return sb.toString().trim();
    }
}