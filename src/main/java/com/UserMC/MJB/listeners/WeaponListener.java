package com.UserMC.MJB.listeners;

import com.UserMC.MJB.MJB;
import com.UserMC.MJB.WeaponManager;
import com.UserMC.MJB.WeaponManager.WeaponType;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WeaponListener implements Listener {

    private final MJB plugin;

    private record ShotInfo(UUID shooterUuid, WeaponType weaponType, double damage) {}

    private final Map<UUID, ShotInfo> activeProjectiles = new ConcurrentHashMap<>();

    private final Map<UUID, Boolean> semiAutoLocked = new ConcurrentHashMap<>();

    private final Map<UUID, BukkitTask> rifleTask = new ConcurrentHashMap<>();

    private final Map<UUID, Long> lastRifleClick = new ConcurrentHashMap<>();

    public WeaponListener(MJB plugin) {
        this.plugin = plugin;
    }


    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!plugin.getWeaponManager().isWeapon(held)) return;

        WeaponType type = plugin.getWeaponManager().getWeaponType(held);
        if (type == null || type == WeaponType.KNIFE) return;

        event.setCancelled(true);
        handleFireInput(player, held, type);
    }


    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!plugin.getWeaponManager().isWeapon(held)) return;

        WeaponType type = plugin.getWeaponManager().getWeaponType(held);
        if (type == null || type == WeaponType.KNIFE) return;

        event.setCancelled(true);
        handleFireInput(player, held, type);
    }


    private void handleFireInput(Player player, ItemStack held, WeaponType type) {
        if (plugin.getWeaponManager().isReloading(player.getUniqueId())) {
            player.sendMessage("§e§l[!] §eStill reloading...");
            return;
        }

        switch (type) {
            case PISTOL, SHOTGUN -> handleSemiAuto(player, held, type);
            case RIFLE            -> handleAutoFire(player, type);
        }
    }


    private void handleSemiAuto(Player player, ItemStack held, WeaponType type) {
        UUID uuid = player.getUniqueId();

        if (semiAutoLocked.getOrDefault(uuid, false)) return;

        int ammo = plugin.getWeaponManager().getCurrentAmmo(held);
        if (ammo <= 0) {
            plugin.getWeaponManager().startReload(player, type);
            return;
        }

        shoot(player, held, type);

        long cooldownMs = (type == WeaponType.PISTOL) ? 500L : 3000L;
        semiAutoLocked.put(uuid, true);
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> semiAutoLocked.remove(uuid),
                cooldownMs / 50L);
    }


    private void handleAutoFire(Player player, WeaponType type) {
        UUID uuid = player.getUniqueId();
        lastRifleClick.put(uuid, System.currentTimeMillis());

        if (rifleTask.containsKey(uuid)) return;

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) { stopRifleTask(uuid); return; }

            long last = lastRifleClick.getOrDefault(uuid, 0L);
            if (System.currentTimeMillis() - last > 300L) { stopRifleTask(uuid); return; }

            if (plugin.getWeaponManager().isReloading(uuid)) return;

            ItemStack current = player.getInventory().getItemInMainHand();
            if (!plugin.getWeaponManager().isWeapon(current)
                    || plugin.getWeaponManager().getWeaponType(current) != WeaponType.RIFLE) {
                stopRifleTask(uuid);
                return;
            }

            int ammo = plugin.getWeaponManager().getCurrentAmmo(current);
            if (ammo <= 0) {
                stopRifleTask(uuid);
                plugin.getWeaponManager().startReload(player, WeaponType.RIFLE);
                return;
            }

            shoot(player, current, WeaponType.RIFLE);

        }, 0L, 4L);

        rifleTask.put(uuid, task);
    }

    private void stopRifleTask(UUID uuid) {
        BukkitTask task = rifleTask.remove(uuid);
        if (task != null) task.cancel();
        lastRifleClick.remove(uuid);
    }


    private void shoot(Player player, ItemStack weapon, WeaponType type) {
        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection().normalize();

        int newAmmo = plugin.getWeaponManager().getCurrentAmmo(weapon) - 1;
        plugin.getWeaponManager().setCurrentAmmo(weapon, newAmmo);
        player.getInventory().setItemInMainHand(weapon);

        broadcastGunshot(player.getLocation(), type);

        if (type == WeaponType.SHOTGUN) {
            for (int i = 0; i < 3; i++) {
                Vector spread = direction.clone().add(new Vector(
                        (Math.random() - 0.5) * 0.18,
                        (Math.random() - 0.5) * 0.18,
                        (Math.random() - 0.5) * 0.18
                )).normalize();
                spawnProjectile(player, eyeLoc, spread, type);
            }
        } else {
            spawnProjectile(player, eyeLoc, direction, type);
        }

        String bar = newAmmo > 0
                ? "§f" + type.displayName + " §7" + newAmmo + "/" + type.magSize
                : "§c§lEMPTY";
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(bar));

        if (newAmmo <= 0) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                ItemStack current = player.getInventory().getItemInMainHand();
                if (plugin.getWeaponManager().isWeapon(current)
                        && plugin.getWeaponManager().getWeaponType(current) == type) {
                    plugin.getWeaponManager().startReload(player, type);
                }
            }, 5L);
        }
    }


    private void spawnProjectile(Player shooter, Location origin, Vector direction, WeaponType type) {
        Location spawnLoc = origin.clone().add(direction.clone().multiply(1.0));

        Arrow arrow = shooter.getWorld().spawnArrow(spawnLoc, direction.clone().normalize(),
                type.projectileSpeed, 0.0f);
        arrow.setShooter(shooter);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setPierceLevel(0);
        arrow.setDamage(0);
        arrow.setGravity(type != WeaponType.RIFLE);

        arrow.getPersistentDataContainer().set(
                plugin.getWeaponManager().IS_WEAPON_PROJECTILE_KEY, PersistentDataType.BOOLEAN, true);
        arrow.getPersistentDataContainer().set(
                plugin.getWeaponManager().PROJECTILE_SHOOTER_KEY, PersistentDataType.STRING,
                shooter.getUniqueId().toString());
        arrow.getPersistentDataContainer().set(
                plugin.getWeaponManager().PROJECTILE_WEAPON_TYPE_KEY, PersistentDataType.STRING, type.id);
        arrow.getPersistentDataContainer().set(
                plugin.getWeaponManager().PROJECTILE_DAMAGE_KEY, PersistentDataType.DOUBLE, type.damage);

        activeProjectiles.put(arrow.getUniqueId(),
                new ShotInfo(shooter.getUniqueId(), type, type.damage));

        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> activeProjectiles.remove(arrow.getUniqueId()), 200L);
    }


    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) return;
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> activeProjectiles.remove(arrow.getUniqueId()), 2L);
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {

        if (event.getDamager() instanceof Arrow arrow) {
            ShotInfo shot = activeProjectiles.get(arrow.getUniqueId());
            if (shot == null) {
                if (arrow.getShooter() instanceof Player && event.getEntity() instanceof Player) {
                    event.setCancelled(true);
                }
                return;
            }
            event.setDamage(shot.damage());
            if (event.getEntity() instanceof Player victim) {
                plugin.getWeaponManager().recordAttacked(victim.getUniqueId());
                Player shooter = plugin.getServer().getPlayer(shot.shooterUuid());
                if (shooter != null) {
                    victim.sendMessage("§4§l[!] §4You've been shot by §f" + shooter.getName() + "§4!");
                }
            }
            return;
        }

        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        ItemStack held = attacker.getInventory().getItemInMainHand();

        if (plugin.getWeaponManager().isWeapon(held)) {
            WeaponType type = plugin.getWeaponManager().getWeaponType(held);
            if (type == WeaponType.KNIFE) {
                event.setDamage(type.damage);
                plugin.getWeaponManager().recordAttacked(victim.getUniqueId());
                return;
            }
            event.setCancelled(true);
            return;
        }

        if (plugin.getWeaponManager().isInSelfDefense(attacker.getUniqueId())) {
            event.setDamage(Math.min(event.getDamage(), 2.0));
            return;
        }

        event.setCancelled(true);
        attacker.sendMessage("§4You can't attack unarmed.");
    }


    private void broadcastGunshot(Location loc, WeaponType type) {
        Sound sound = switch (type) {
            case PISTOL  -> Sound.ENTITY_FIREWORK_ROCKET_BLAST;
            case RIFLE   -> Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST;
            case SHOTGUN -> Sound.ENTITY_GENERIC_EXPLODE;
            default      -> Sound.ENTITY_FIREWORK_ROCKET_BLAST;
        };
        float volume = (type == WeaponType.RIFLE) ? 2.0f : 1.5f;
        loc.getWorld().playSound(loc, sound, volume, 1.2f);
    }
}