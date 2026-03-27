package com.UserMC.MJB;

import nl.mtvehicles.core.infrastructure.enums.VehicleType;

public class VehicleManager {

    private final MJB plugin;

    // Law key for government control
    public static final String LAW_VEHICLE_LICENSE = "vehicle_license_required";

    public VehicleManager(MJB plugin) {
        this.plugin = plugin;
        registerDefaultLicenseTypes();
    }

    // Map MTVehicles vehicle type to MJB license type name
    public String getRequiredLicense(VehicleType vehicleType) {
        if (vehicleType == null) return null;
        return switch (vehicleType) {
            case CAR   -> "drivers_license";
            case HOVER -> "drivers_license";
            case HELICOPTER -> "pilot_license";
            case AIRPLANE   -> "pilot_license";
            case BOAT  -> "boat_license";
            default    -> null; // TANK — no license required
        };
    }

    // Is the vehicle license law currently active?
    public boolean isLicenseRequired() {
        return !"false".equals(plugin.getGovernmentManager()
                .getGovernmentSetting(LAW_VEHICLE_LICENSE, "true"));
    }

    // Check if a player has the right license for a given vehicle type
    public boolean hasLicense(java.util.UUID playerUuid, VehicleType vehicleType) {
        String licenseType = getRequiredLicense(vehicleType);
        if (licenseType == null) return true; // no license needed for this vehicle
        return plugin.getLicenseManager().hasActiveLicense(playerUuid, licenseType);
    }

    // Seed the three license types into the DB on startup if not already there
    private void registerDefaultLicenseTypes() {
        plugin.getLicenseManager().registerLicenseType(
                "drivers_license",
                "Driver's License",
                2000.0,
                500.0,
                "Required to operate cars and hover vehicles."
        );
        plugin.getLicenseManager().registerLicenseType(
                "pilot_license",
                "Pilot's License",
                20000.0,
                4800.0,
                "Required to operate helicopters and airplanes."
        );
        plugin.getLicenseManager().registerLicenseType(
                "boat_license",
                "Boat License",
                4000.0,
                1500.0,
                "Required to operate boats."
        );
    }
}