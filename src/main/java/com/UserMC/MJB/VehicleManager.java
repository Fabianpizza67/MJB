package com.UserMC.MJB;

public class VehicleManager {

    private final MJB plugin;

    // Law key for government control
    public static final String LAW_VEHICLE_LICENSE = "vehicle_license_required";

    public VehicleManager(MJB plugin) {
        this.plugin = plugin;
        registerDefaultLicenseTypes();
    }


    // Is the vehicle license law currently active?
    public boolean isLicenseRequired() {
        return !"false".equals(plugin.getGovernmentManager()
                .getGovernmentSetting(LAW_VEHICLE_LICENSE, "true"));
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