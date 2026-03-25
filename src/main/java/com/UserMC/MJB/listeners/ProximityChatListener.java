package com.UserMC.MJB.listeners;

import com.UserMC.MJB.MJB;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashSet;
import java.util.Set;

public class ProximityChatListener implements Listener {

    private final MJB plugin;
    private static final double CHAT_RANGE = 20.0;

    public ProximityChatListener(MJB plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncPlayerChatEvent event) {
        // Let other listeners that cancel the event (chat input sessions) take priority
        if (event.isCancelled()) return;

        Player sender = event.getPlayer();

        // Build the set of recipients — only players within range in the same world
        Set<Player> recipients = new HashSet<>();
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (!online.getWorld().equals(sender.getWorld())) continue;
            if (online.getLocation().distance(sender.getLocation()) <= CHAT_RANGE) {
                recipients.add(online);
            }
        }

        // Always include the sender so they see their own message
        recipients.add(sender);

        event.getRecipients().clear();
        event.getRecipients().addAll(recipients);
    }
}