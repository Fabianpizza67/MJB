package com.UserMC.MJB.listeners;

import com.UserMC.MJB.MJB;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final MJB plugin;

    public PlayerJoinListener(MJB plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getEconomyManager().initPlayer(
                event.getPlayer().getUniqueId(),
                event.getPlayer().getName()
        );
    }
}