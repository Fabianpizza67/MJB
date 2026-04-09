package com.UserMC.MJB;

import org.bukkit.World;

import java.time.ZoneId;
import java.time.ZonedDateTime;

public class TimeSyncManager {

    private final MJB plugin;
    private static final ZoneId CET = ZoneId.of("Europe/Amsterdam");
    private static final String TARGET_WORLD = "world";

    // Minecraft day is 24000 ticks
    // In-game midnight = 18000, noon = 6000
    // Real midnight (00:00) should map to in-game midnight (18000)
    // Formula: ((realHour * 60 + realMinute) / 1440.0) * 24000 + 18000) % 24000

    public TimeSyncManager(MJB plugin) {
        this.plugin = plugin;
    }

    public void startSyncScheduler() {
        // Run every 20 ticks (once per second) — smooth but very cheap
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::syncTime, 0L, 20L);
    }

    private void syncTime() {
        World world = plugin.getServer().getWorld(TARGET_WORLD);
        if (world == null) return;

        ZonedDateTime now = ZonedDateTime.now(CET);
        int hour = now.getHour();
        int minute = now.getMinute();
        int second = now.getSecond();

        // Total seconds into the day
        int totalSeconds = hour * 3600 + minute * 60 + second;
        // Total seconds in a day
        double secondsInDay = 86400.0;

        // Map to Minecraft ticks
        // Real 00:00 = MC 18000 (midnight), real 06:00 = MC 0 (dawn), real 12:00 = MC 6000 (noon)
        long mcTime = (long) ((totalSeconds / secondsInDay) * 24000 + 18000) % 24000;

        world.setTime(mcTime);
    }
}