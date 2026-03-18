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

            Player witness = plugin.getCrimeManager().getNearbyOfficer(
                    shooter.getLocation(), WITNESS_RANGE);
            if (witness != null) {
                plugin.getCrimeManager().addOffence(
                        shooter.getUniqueId(),
                        "Assault with a firearm (victim: " + victim.getName() + ")",
                        witness.getUniqueId()
                );
                witness.sendMessage("§c§l[Crime] §cYou witnessed §f" + shooter.getName() +
                        " §cshoot §f" + victim.getName() + "§c! They are now wanted.");
                shooter.sendMessage("§c§l[Wanted] §cA police officer witnessed you shoot " +
                        victim.getName() + ". You are now wanted!");
            }
            return;
        }

        // ---- Fist punch (self-defense allowed, still a crime if witnessed) ----
        if (event.getDamager() instanceof Player attacker) {
            // If it's a weapon melee (knife) handle separately
            if (plugin.getWeaponManager().isWeapon(
                    attacker.getInventory().getItemInMainHand())) return;
            if (plugin.getPoliceManager().isOfficer(attacker.getUniqueId())) return;

            // Only flag unprovoked punches — self-defense window means they were attacked first
            if (plugin.getWeaponManager().isInSelfDefense(attacker.getUniqueId())) return;

            Player witness = plugin.getCrimeManager().getNearbyOfficer(
                    attacker.getLocation(), WITNESS_RANGE);
            if (witness != null) {
                plugin.getCrimeManager().addOffence(
                        attacker.getUniqueId(),
                        "Assault (victim: " + victim.getName() + ")",
                        witness.getUniqueId()
                );
                witness.sendMessage("§c§l[Crime] §cYou witnessed §f" + attacker.getName() +
                        " §cassault §f" + victim.getName() + "§c! They are now wanted.");
                attacker.sendMessage("§c§l[Wanted] §cA police officer witnessed your assault. You are now wanted!");
            }
        }
    }

    // ---- Killing a player ----

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (!(victim.getKiller() instanceof Player killer)) return;
        if (plugin.getPoliceManager().isOfficer(killer.getUniqueId())) return;

        Player witness = plugin.getCrimeManager().getNearbyOfficer(
                killer.getLocation(), WITNESS_RANGE);
        if (witness != null) {
            plugin.getCrimeManager().addOffence(
                    killer.getUniqueId(),
                    "Murder (victim: " + victim.getName() + ")",
                    witness.getUniqueId()
            );
            witness.sendMessage("§c§l[Crime] §cYou witnessed §f" + killer.getName() +
                    " §ckill §f" + victim.getName() + "§c! They are now wanted.");
            killer.sendMessage("§c§l[Wanted] §cA police officer witnessed you kill " +
                    victim.getName() + ". You are now wanted for murder!");
        }

        // Alert all online officers regardless of witness
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (plugin.getPoliceManager().isOfficer(p.getUniqueId()) && !p.equals(witness)) {
                p.sendMessage("§c§l[Dispatch] §cA player has been killed. Victim: §f" +
                        victim.getName() + "§c. Suspect: §f" +
                        (killer.getName()) + "§c.");
            }
        }
    }

    // ---- Illegal items found during search (called from PoliceListener) ----
    // This is triggered externally via CrimeManager.addOffence() in PoliceListener
    // when items are seized — no separate event needed.
}