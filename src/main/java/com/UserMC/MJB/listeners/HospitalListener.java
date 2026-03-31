package com.UserMC.MJB.listeners;

import com.UserMC.MJB.HospitalManager;
import com.UserMC.MJB.MJB;
import com.UserMC.MJB.MedicalRecordManager;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class HospitalListener implements Listener {

    private final MJB plugin;

    public HospitalListener(MJB plugin) {
        this.plugin = plugin;
    }

    // Fires LAST — after WeaponListener has set real damage at HIGHEST
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Already downed — cancel everything
        if (plugin.getHospitalManager().isDowned(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // Not lethal — ignore
        if (player.getHealth() - event.getFinalDamage() > 0) return;

        // Determine if this was one of our weapon arrows
        boolean wasShot = false;
        if (event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Arrow arrow) {
            wasShot = arrow.getPersistentDataContainer().has(
                    plugin.getWeaponManager().IS_WEAPON_PROJECTILE_KEY,
                    PersistentDataType.BOOLEAN);
        }

        // Cancel vanilla death and go downed instead
        event.setCancelled(true);

        HospitalManager.InjuryType injury = determineInjury(event.getCause(), wasShot);
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                plugin.getHospitalManager().goDown(player, injury), 1L);
    }

    private HospitalManager.InjuryType determineInjury(
            EntityDamageEvent.DamageCause cause, boolean shot) {
        if (shot) return HospitalManager.InjuryType.GUNSHOT_WOUND;
        return switch (cause) {
            case FALL                              -> HospitalManager.InjuryType.BROKEN_BONE;
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR -> HospitalManager.InjuryType.BURNS;
            default                                -> HospitalManager.InjuryType.BLEEDING;
        };
    }

    // ---- Carry mechanic: right-click downed player with empty hand ----

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Player target)) return;

        Player carrier = event.getPlayer();
        ItemStack held = carrier.getInventory().getItemInMainHand();
        if (held.getType() != org.bukkit.Material.AIR) return;
        if (!plugin.getHospitalManager().isDowned(target.getUniqueId())) return;
        if (plugin.getPoliceManager().isHandcuffs(held)) return;
        if (plugin.getPoliceManager().isBadge(held)) return;

        event.setCancelled(true);

        if (plugin.getHospitalManager().isBeingCarried(target.getUniqueId())
                && carrier.getUniqueId().equals(
                plugin.getHospitalManager().getCarrier(target.getUniqueId()))) {
            plugin.getHospitalManager().stopCarrying(target.getUniqueId());
        } else {
            plugin.getHospitalManager().startCarrying(
                    carrier.getUniqueId(), target.getUniqueId());
        }
    }

    // ---- Block downed players from most actions ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDownedInteract(PlayerInteractEvent event) {
        if (!plugin.getHospitalManager().isDowned(event.getPlayer().getUniqueId())) return;
        ItemStack held = event.getPlayer().getInventory().getItemInMainHand();
        if (plugin.getPhoneManager().isPhone(held)) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage("§4You are downed and cannot do that.");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDownedInventory(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!plugin.getHospitalManager().isDowned(player.getUniqueId())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDownedDrop(PlayerDropItemEvent event) {
        if (!plugin.getHospitalManager().isDowned(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDownedSprint(PlayerToggleSprintEvent event) {
        if (!plugin.getHospitalManager().isDowned(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
    }

    // Block jumping while downed
    @EventHandler(priority = EventPriority.HIGH)
    public void onDownedMove(PlayerMoveEvent event) {
        if (!plugin.getHospitalManager().isDowned(event.getPlayer().getUniqueId())) return;
        if (event.getTo().getY() > event.getFrom().getY() + 0.05) {
            org.bukkit.Location to = event.getTo().clone();
            to.setY(event.getFrom().getY());
            event.setTo(to);
        }
    }

    // Clean up on quit
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (plugin.getHospitalManager().isDowned(uuid)) {
            plugin.getHospitalManager().actuallyDie(event.getPlayer());
        }
        plugin.getHospitalManager().stopCarrying(uuid);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMedicalItemUse(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Player target)) return;

        Player doctor = event.getPlayer();
        ItemStack held = doctor.getInventory().getItemInMainHand();

        if (!plugin.getHospitalManager().isMedicalItem(held)) return;
        if (!plugin.getHospitalManager().isMedicalItemValid(held)) {
            doctor.sendMessage("§4This medical item has expired!");
            return;
        }

        String type = held.getItemMeta().getPersistentDataContainer()
                .get(plugin.getHospitalManager().MEDICAL_TYPE_KEY,
                        org.bukkit.persistence.PersistentDataType.STRING);
        if (type == null) return;

        event.setCancelled(true);

        switch (type) {
            case "iv_drip" -> {
                if (!plugin.getHospitalManager().isDowned(target.getUniqueId())) {
                    doctor.sendMessage("§4IV drip can only be applied to downed patients.");
                    return;
                }
                if (plugin.getMedicalRecordManager().hasIVDrip(target.getUniqueId())) {
                    doctor.sendMessage("§4This patient already has an IV drip.");
                    return;
                }
                plugin.getMedicalRecordManager().applyIVDrip(target.getUniqueId());
                consumeOne(doctor);
                doctor.sendMessage("§b§l[Hospital] §fIV drip applied to §f" +
                        target.getName() + "§f. Their bleed-out time has been doubled!");
                target.sendMessage("§b§l[Hospital] §fAn IV drip has been applied. " +
                        "You have more time — hang in there!");
            }
            case "blood_test" -> {
                MedicalRecordManager.BloodType blood =
                        plugin.getMedicalRecordManager().getBloodType(target.getUniqueId());
                if (blood == null) {
                    // First test — assign blood type
                    plugin.getMedicalRecordManager().assignBloodType(target.getUniqueId());
                    blood = plugin.getMedicalRecordManager().getBloodType(target.getUniqueId());
                }
                consumeOne(doctor);
                doctor.sendMessage("§b§l[Hospital] §fBlood type of §f" +
                        target.getName() + "§f: §b" + blood.name());
                target.sendMessage("§b§l[Hospital] §fYour blood type has been tested: §b" +
                        blood.name());
            }
        }
    }

    private void consumeOne(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getAmount() > 1) held.setAmount(held.getAmount() - 1);
        else player.getInventory().setItemInMainHand(null);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBandageUse(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Player target)) return;

        Player doctor = event.getPlayer();
        ItemStack held = doctor.getInventory().getItemInMainHand();

        if (!plugin.getHospitalManager().isBandage(held)) return;
        if (!plugin.getHospitalManager().isDoctor(doctor.getUniqueId())) {
            doctor.sendMessage("§4Only hospital staff can use medical supplies.");
            return;
        }

        // Check expiry
        if (!plugin.getHospitalManager().isMedicalItemValid(held)) {
            doctor.sendMessage("§4This bandage has expired!");
            return;
        }

        // Can't bandage a downed player — they need proper treatment
        if (plugin.getHospitalManager().isDowned(target.getUniqueId())) {
            doctor.sendMessage("§4This patient needs proper treatment, not just a bandage.");
            return;
        }

        event.setCancelled(true);

        // Heal 2 hearts (4 health points), cap at max health
        double newHealth = Math.min(target.getMaxHealth(), target.getHealth() + 4.0);
        target.setHealth(newHealth);

        // Consume one bandage
        if (held.getAmount() > 1) {
            held.setAmount(held.getAmount() - 1);
        } else {
            doctor.getInventory().setItemInMainHand(null);
        }

        doctor.sendMessage("§a§l[Hospital] §fYou applied a bandage to §b" +
                target.getName() + "§f. (+" + plugin.getEconomyManager().format(2) + " hearts)");
        target.sendMessage("§a§l[Hospital] §f" + doctor.getName() +
                " §fapplied a bandage. You feel a little better.");
        target.playSound(target.getLocation(),
                org.bukkit.Sound.ENTITY_PLAYER_BURP, 0.5f, 1.2f);
    }
}