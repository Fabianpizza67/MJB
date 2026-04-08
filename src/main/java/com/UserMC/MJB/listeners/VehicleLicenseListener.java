package com.UserMC.MJB.listeners;

import com.UserMC.MJB.MJB;
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



    // Get the player riding a vehicle armor stand
    public Player getRidingPlayer(ArmorStand stand) {
        for (Entity passenger : stand.getPassengers()) {
            if (passenger instanceof Player p) return p;
        }
        return null;
    }


}