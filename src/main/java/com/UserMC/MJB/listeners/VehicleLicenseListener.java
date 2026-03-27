package com.UserMC.MJB.listeners;

import com.UserMC.MJB.MJB;
import nl.mtvehicles.core.events.VehicleEnterEvent;
import nl.mtvehicles.core.infrastructure.enums.VehicleType;
import nl.mtvehicles.core.infrastructure.vehicle.VehicleData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class VehicleLicenseListener implements Listener {

    private final MJB plugin;
    private static final double WITNESS_RANGE = 50.0;

    public VehicleLicenseListener(MJB plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVehicleEnter(VehicleEnterEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        // Police officers are exempt
        if (plugin.getPoliceManager().isOfficer(player.getUniqueId())) return;

        // If government disabled the law, no crime
        if (!plugin.getVehicleManager().isLicenseRequired()) return;

        // Get vehicle type from the static VehicleData map using license plate
        String licensePlate = event.getLicensePlate();
        if (licensePlate == null) return;

        VehicleType vehicleType = VehicleData.type.get(licensePlate);
        if (vehicleType == null) return;

        String requiredLicense = plugin.getVehicleManager().getRequiredLicense(vehicleType);
        if (requiredLicense == null) return; // vehicle needs no license

        if (!plugin.getVehicleManager().hasLicense(player.getUniqueId(), vehicleType)) {
            Player witness = plugin.getCrimeManager()
                    .getNearbyOfficer(player.getLocation(), WITNESS_RANGE);
            if (witness != null) {
                String vehicleName = formatVehicleType(vehicleType);
                plugin.getCrimeManager().addOffence(
                        player.getUniqueId(),
                        "Driving without a " + vehicleName + " license",
                        witness.getUniqueId()
                );
                witness.sendMessage("§c§l[Crime] §cYou witnessed §f" + player.getName() +
                        " §center a §f" + vehicleName +
                        " §cwithout a valid license! They are now wanted.");
                player.sendMessage("§c§l[Wanted] §cA police officer witnessed you entering a " +
                        vehicleName + " without a license. You are now wanted!");
            }
        }
    }

    // Used by /police impound — finds nearest MTVehicles main seat armor stand
    public ArmorStand findNearbyVehicle(Player officer, double range) {
        for (Entity entity : officer.getNearbyEntities(range, range, range)) {
            if (!(entity instanceof ArmorStand stand)) continue;
            // MTVehicles stores the license plate in the armor stand's custom name
            String name = stand.getCustomName();
            if (name == null) continue;
            if (VehicleData.autostand.containsValue(stand)) return stand;
        }
        return null;
    }

    // Get the player riding a vehicle armor stand
    public Player getRidingPlayer(ArmorStand stand) {
        for (Entity passenger : stand.getPassengers()) {
            if (passenger instanceof Player p) return p;
        }
        return null;
    }

    // Get the license plate from an armor stand
    public String getLicensePlate(ArmorStand stand) {
        for (java.util.Map.Entry<String, org.bukkit.entity.ArmorStand> entry
                : VehicleData.autostand.entrySet()) {
            if (entry.getValue().equals(stand)) return entry.getKey();
        }
        return null;
    }

    private String formatVehicleType(VehicleType type) {
        return switch (type) {
            case CAR        -> "car";
            case HOVER      -> "hover vehicle";
            case HELICOPTER -> "helicopter";
            case AIRPLANE   -> "airplane";
            case BOAT       -> "boat";
            default         -> "vehicle";
        };
    }
}