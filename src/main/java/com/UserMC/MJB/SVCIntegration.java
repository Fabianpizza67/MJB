package com.UserMC.MJB;

import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SVCIntegration implements VoicechatPlugin {

    private static VoicechatServerApi serverApi;
    // call group ID -> both player UUIDs so we can clean up
    private static final Map<UUID, de.maxhenkel.voicechat.api.Group> playerGroups
            = new ConcurrentHashMap<>();

    @Override
    public String getPluginId() {
        return "MJB";
    }

    @Override
    public void initialize(VoicechatApi api) {
        // Called by SVC when it loads our plugin
        serverApi = (VoicechatServerApi) api;
        MJB.getInstance().getLogger().info("[SVC] Simple Voice Chat API initialised.");
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        // No events needed for now — we only create/destroy groups
    }

    // ---- Called from PhoneManager ----

    public static boolean isAvailable() {
        return serverApi != null;
    }

    /**
     * Creates a private SVC group and puts both players in it.
     * Returns true on success.
     */
    public static boolean createCallGroup(Player caller, Player callee) {
        if (serverApi == null) return false;
        try {
            de.maxhenkel.voicechat.api.Group group = serverApi.groupBuilder()
                    .setName("📞 " + caller.getName() + " & " + callee.getName())
                    .setPersistent(false)
                    .build();

            // Must set group on both connections after a short delay
            // to ensure SVC has fully registered both connections
            MJB.getInstance().getServer().getScheduler().runTaskLater(
                    MJB.getInstance(), () -> {
                        try {
                            VoicechatConnection callerConn =
                                    serverApi.getConnectionOf(caller.getUniqueId());
                            VoicechatConnection calleeConn =
                                    serverApi.getConnectionOf(callee.getUniqueId());

                            if (callerConn != null) {
                                callerConn.setGroup(group);
                                playerGroups.put(caller.getUniqueId(), group);
                            } else {
                                caller.sendMessage("§7(Voice chat not connected for you)");
                            }
                            if (calleeConn != null) {
                                calleeConn.setGroup(group);
                                playerGroups.put(callee.getUniqueId(), group);
                            } else {
                                callee.sendMessage("§7(Voice chat not connected for you)");
                            }
                        } catch (Exception e) {
                            MJB.getInstance().getLogger().warning(
                                    "[SVC] Error setting group: " + e.getMessage());
                        }
                    }, 10L); // 10 tick delay

            return true;
        } catch (Exception e) {
            MJB.getInstance().getLogger().warning(
                    "[SVC] Failed to create call group: " + e.getMessage());
            return false;
        }
    }

    /**
     * Removes a player from their call group.
     * If both players have left, the group is automatically destroyed by SVC
     * since it was created as non-persistent.
     */
    public static void removeFromCallGroup(UUID uuid) {
        if (serverApi == null) return;
        try {
            VoicechatConnection conn = serverApi.getConnectionOf(uuid);
            if (conn != null) conn.setGroup(null);
            playerGroups.remove(uuid);
        } catch (Exception e) {
            MJB.getInstance().getLogger().warning("[SVC] Failed to remove player from group: " + e.getMessage());
        }
    }
}