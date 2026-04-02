package com.UserMC.MJB;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class RadioManager {

    private final MJB plugin;

    public final NamespacedKey IS_RADIO_KEY;
    public final NamespacedKey RADIO_CHANNEL_KEY;

    public static final String CHANNEL_POLICE   = "police";
    public static final String CHANNEL_MEDICAL  = "medical";
    public static final String CHANNEL_DISPATCH = "dispatch";
    public static final String CHANNEL_PUBLIC   = "public";

    public static final double RANGE = 200.0;

    public RadioManager(MJB plugin) {
        this.plugin = plugin;
        IS_RADIO_KEY      = new NamespacedKey(plugin, "is_radio");
        RADIO_CHANNEL_KEY = new NamespacedKey(plugin, "radio_channel");
    }

    // ---- Item creation ----

    public ItemStack createRadio(String type) {
        return switch (type) {
            case CHANNEL_POLICE  -> makeRadio("§9§lPolice Radio",
                    "§7Channels: §9Police §7+ §6Dispatch §7+ §fPublic",
                    "§8[Encrypted — Police frequency]",
                    CHANNEL_POLICE);
            case CHANNEL_MEDICAL -> makeRadio("§a§lMedical Radio",
                    "§7Channels: §aMedical §7+ §6Dispatch §7+ §fPublic",
                    "§8[Encrypted — Medical frequency]",
                    CHANNEL_MEDICAL);
            default              -> makeRadio("§f§lPublic Radio",
                    "§7Channels: §fPublic only",
                    "§8[Unencrypted — anyone can listen]",
                    CHANNEL_PUBLIC);
        };
    }

    private ItemStack makeRadio(String name, String channelLine,
                                String encryptLine, String channel) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(
                channelLine,
                encryptLine,
                "§7Use §f/radio [frequency] <message> §7to transmit.",
                "§7Range: §f200 blocks"
        ));
        meta.getPersistentDataContainer().set(IS_RADIO_KEY,
                PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(RADIO_CHANNEL_KEY,
                PersistentDataType.STRING, channel);
        item.setItemMeta(meta);
        return item;
    }

    // ---- Checks ----

    public boolean isRadio(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(IS_RADIO_KEY, PersistentDataType.BOOLEAN);
    }

    public String getRadioChannel(ItemStack item) {
        if (!isRadio(item)) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(RADIO_CHANNEL_KEY, PersistentDataType.STRING);
    }

    // Returns the radio a player is holding (main hand first, then off hand)
    public ItemStack getHeldRadio(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (isRadio(main)) return main;
        ItemStack off = player.getInventory().getItemInOffHand();
        if (isRadio(off)) return off;
        return null;
    }

    // Whether a radio of this base channel type can transmit/receive on the target frequency
    public boolean canUseFrequency(String baseChannel, String targetFrequency) {
        if (baseChannel == null) return false;
        return switch (baseChannel) {
            case CHANNEL_POLICE  -> targetFrequency.equals(CHANNEL_POLICE)
                    || targetFrequency.equals(CHANNEL_DISPATCH)
                    || targetFrequency.equals(CHANNEL_PUBLIC);
            case CHANNEL_MEDICAL -> targetFrequency.equals(CHANNEL_MEDICAL)
                    || targetFrequency.equals(CHANNEL_DISPATCH)
                    || targetFrequency.equals(CHANNEL_PUBLIC);
            case CHANNEL_PUBLIC  -> targetFrequency.equals(CHANNEL_PUBLIC);
            default              -> false;
        };
    }

    // Default transmit frequency for this radio type
    public String getDefaultFrequency(String baseChannel) {
        if (baseChannel == null) return CHANNEL_PUBLIC;
        return switch (baseChannel) {
            case CHANNEL_POLICE  -> CHANNEL_POLICE;
            case CHANNEL_MEDICAL -> CHANNEL_MEDICAL;
            default              -> CHANNEL_PUBLIC;
        };
    }

    // Chat colour for a channel name
    public String channelColor(String channel) {
        return switch (channel) {
            case CHANNEL_POLICE   -> "§9";
            case CHANNEL_MEDICAL  -> "§a";
            case CHANNEL_DISPATCH -> "§6";
            default               -> "§f";
        };
    }

    // ---- Broadcast ----

    public void broadcast(Player sender, String frequency, String message) {
        String color     = channelColor(frequency);
        String freqLabel = frequency.substring(0, 1).toUpperCase() + frequency.substring(1);
        String formatted = "§8[§bRadio §7| " + color + freqLabel + "§8] §f"
                + sender.getName() + "§7: §f" + message;

        int recipients = 0;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (!p.getWorld().equals(sender.getWorld())) continue;
            if (p.getLocation().distance(sender.getLocation()) > RANGE) continue;

            ItemStack radio = getHeldRadio(p);
            if (radio == null) continue;

            String baseChannel = getRadioChannel(radio);
            if (!canUseFrequency(baseChannel, frequency)) continue;

            // Don't double-send to the sender (handled below)
            if (!p.equals(sender)) {
                p.sendMessage(formatted);
                recipients++;
            }
        }

        // Always show to sender
        sender.sendMessage(formatted);

        plugin.getLogger().info("[Radio][" + freqLabel + "] " +
                sender.getName() + ": " + message +
                " (" + recipients + " other recipient(s) in range)");
    }
}