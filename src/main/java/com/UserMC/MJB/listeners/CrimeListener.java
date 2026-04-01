package com.UserMC.MJB.listeners;

import com.UserMC.MJB.MJB;
import com.UserMC.MJB.CrimeManager;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public class CrimeListener implements Listener {

    private final MJB plugin;
    private static final double WITNESS_RANGE = 20.0;

    public CrimeListener(MJB plugin) {
        this.plugin = plugin;
    }

    // ---- Shooting / punching a player ----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        // ---- Shot by weapon ----
        if (event.getDamager() instanceof Arrow arrow &&
                arrow.getShooter() instanceof Player shooter) {
            if (plugin.getPoliceManager().isOfficer(shooter.getUniqueId())) return;
            // Only our weapon arrows
            if (!arrow.getPersistentDataContainer().has(
                    plugin.getWeaponManager().IS_WEAPON_PROJECTILE_KEY,
                    org.bukkit.persistence.PersistentDataType.BOOLEAN)) return;
            return;
        }

        // ---- Fist punch (self-defense allowed, still a crime if witnessed) ----
        if (event.getDamager() instanceof Player attacker) {
            // If it's a weapon melee (knife) handle separately
            if (plugin.getWeaponManager().isWeapon(
                    attacker.getInventory().getItemInMainHand())) return;
            if (plugin.getPoliceManager().isOfficer(attacker.getUniqueId())) return;

            // Only flag unprovoked punches — self-defense window means they were attacked first
            if (plugin.getWeaponManager().isInSelfDefense(attacker.getUniqueId()));
        }
    }

    // ---- Killing a player ----

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (!(victim.getKiller() instanceof Player killer)) return;
        if (plugin.getPoliceManager().isOfficer(killer.getUniqueId()));
    }
}