package com.UserMC.MJB.listeners;

import com.UserMC.MJB.MJB;
import com.UserMC.MJB.PhoneManager;
import com.UserMC.MJB.PhoneManager.*;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.text.SimpleDateFormat;
import java.util.*;

public class PhoneListener implements Listener {

    private final MJB plugin;

    // GUI titles
    private static final String HOME_GUI      = "§f§l📱 Phone";
    private static final String MESSAGES_GUI  = "§f§l💬 Messages";
    private static final String CHAT_GUI      = "§f§l📨 Chat";
    private static final String CONTACTS_GUI  = "§f§l📒 Contacts";
    private static final String NEW_MSG_GUI   = "§f§l✉ New Message";
    private static final String GROUPS_GUI    = "§f§l👥 Groups";

    private final NamespacedKey ACTION_KEY;
    private final NamespacedKey CONV_ID_KEY;
    private final NamespacedKey NUMBER_KEY;

    // Chat input sessions: uuid -> session type + data
    private final Map<UUID, PhoneSession> chatSessions = new HashMap<>();

    private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm dd/MM");

    public PhoneListener(MJB plugin) {
        this.plugin = plugin;
        ACTION_KEY  = new NamespacedKey(plugin, "phone_action");
        CONV_ID_KEY = new NamespacedKey(plugin, "conv_id");
        NUMBER_KEY  = new NamespacedKey(plugin, "contact_number");
    }

    // ---- Right-click phone to open ----

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
                event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!plugin.getPhoneManager().isPhone(held)) return;

        event.setCancelled(true);
        openHomeScreen(player);
    }

    // ---- Home screen ----

    private void openHomeScreen(Player player) {
        String number = plugin.getPhoneManager().getPhoneNumber(player.getUniqueId());
        int unread = plugin.getPhoneManager().getUnreadCount(player.getUniqueId());
        boolean inCall = plugin.getPhoneManager().isInCall(player.getUniqueId());

        Inventory gui = plugin.getServer().createInventory(null, 27, HOME_GUI);

        // Header
        gui.setItem(4, item(Material.RECOVERY_COMPASS, "§f§lYour Phone",
                "§7Number: §b" + (number != null ? number : "Unassigned"),
                "§7Unread: §f" + unread,
                inCall ? "§a📞 In a call — /endcall to hang up" : ""));

        // Messages
        ItemStack msgBtn = item(Material.WRITABLE_BOOK,
                "§f§l💬 Messages" + (unread > 0 ? " §c(" + unread + ")" : ""),
                "§7View your conversations.");
        setAction(msgBtn, "open_messages");
        gui.setItem(10, msgBtn);

        // Contacts
        ItemStack contactBtn = item(Material.PLAYER_HEAD, "§f§l📒 Contacts",
                "§7View and manage contacts.");
        setAction(contactBtn, "open_contacts");
        gui.setItem(12, contactBtn);

        // New message
        ItemStack newMsgBtn = item(Material.PAPER, "§f§l✉ New Message",
                "§7Send a message to a number.");
        setAction(newMsgBtn, "new_message");
        gui.setItem(14, newMsgBtn);

        // Groups
        ItemStack groupBtn = item(Material.CHEST, "§f§l👥 Groups",
                "§7View or create group chats.");
        setAction(groupBtn, "open_groups");
        gui.setItem(16, groupBtn);

        gui.setItem(22, item(Material.BARRIER, "§4Close", "§7Put away your phone."));
        player.openInventory(gui);
    }

    // ---- Messages list ----

    private void openMessagesScreen(Player player) {
        List<Conversation> convs = plugin.getPhoneManager()
                .getConversationsForPlayer(player.getUniqueId());

        Inventory gui = plugin.getServer().createInventory(null, 54, MESSAGES_GUI);

        int slot = 0;
        for (Conversation conv : convs) {
            if (slot >= 45) break;
            String preview = conv.lastMessage != null
                    ? (conv.lastMessage.length() > 30
                    ? conv.lastMessage.substring(0, 30) + "..." : conv.lastMessage)
                    : "§7No messages yet";
            Material mat = conv.isGroup ? Material.CHEST : Material.PAPER;

            ItemStack btn = item(mat,
                    (conv.isGroup ? "§9§l👥 " : "§f") + conv.displayName,
                    "§7" + preview,
                    "", "§eClick §7to open");
            ItemMeta meta = btn.getItemMeta();
            meta.getPersistentDataContainer().set(CONV_ID_KEY, PersistentDataType.INTEGER, conv.id);
            meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "open_chat");
            btn.setItemMeta(meta);
            gui.setItem(slot++, btn);
        }

        if (convs.isEmpty()) {
            gui.setItem(22, item(Material.BARRIER, "§7No conversations",
                    "§7Send a new message to get started."));
        }

        gui.setItem(45, item(Material.ARROW, "§fBack", "§7Return to home."));
        player.openInventory(gui);
    }

    // ---- Chat view ----

    private void openChatScreen(Player player, int convId) {
        plugin.getPhoneManager().markRead(player.getUniqueId(), convId);
        List<Message> messages = plugin.getPhoneManager().getMessages(convId, 44);

        // Check if this is a group
        boolean isGroup = plugin.getPhoneManager()
                .getConversationsForPlayer(player.getUniqueId())
                .stream().anyMatch(c -> c.id == convId && c.isGroup);

        Inventory gui = plugin.getServer().createInventory(null, 54, CHAT_GUI);

        int slot = 0;
        for (Message msg : messages) {
            if (slot >= 45) break;
            boolean isMine = msg.senderUuid.equals(player.getUniqueId());
            String senderName = plugin.getServer()
                    .getOfflinePlayer(msg.senderUuid).getName();
            Material mat = isMine ? Material.LIME_DYE : Material.LIGHT_BLUE_DYE;
            gui.setItem(slot++, item(mat,
                    (isMine ? "§aYou" : "§b" + senderName),
                    "§f" + msg.text,
                    "§7" + sdf.format(msg.sentAt)));
        }

        if (messages.isEmpty()) {
            gui.setItem(22, item(Material.GRAY_DYE, "§7No messages yet", "§7Say something!"));
        }

        // Reply button
        ItemStack replyBtn = item(Material.EMERALD, "§a§lReply",
                "§7Type your message in chat.");
        ItemMeta replyMeta = replyBtn.getItemMeta();
        replyMeta.getPersistentDataContainer().set(
                CONV_ID_KEY, PersistentDataType.INTEGER, convId);
        replyMeta.getPersistentDataContainer().set(
                ACTION_KEY, PersistentDataType.STRING, "reply");
        replyBtn.setItemMeta(replyMeta);
        gui.setItem(49, replyBtn);

        // Add member button (groups only)
        if (isGroup) {
            ItemStack addBtn = item(Material.LIME_DYE, "§a§lAdd Member",
                    "§7Invite a player to this group by number.");
            ItemMeta addMeta = addBtn.getItemMeta();
            addMeta.getPersistentDataContainer().set(
                    CONV_ID_KEY, PersistentDataType.INTEGER, convId);
            addMeta.getPersistentDataContainer().set(
                    ACTION_KEY, PersistentDataType.STRING, "add_group_member");
            addBtn.setItemMeta(addMeta);
            gui.setItem(50, addBtn);
        }

        gui.setItem(45, item(Material.ARROW, "§fBack", "§7Return to messages."));
        player.openInventory(gui);
    }


    // ---- Contacts ----

    private void openContactsScreen(Player player) {
        List<Contact> contacts = plugin.getPhoneManager()
                .getContacts(player.getUniqueId());

        Inventory gui = plugin.getServer().createInventory(null, 54, CONTACTS_GUI);

        int slot = 0;
        for (Contact contact : contacts) {
            if (slot >= 45) break;
            UUID contactUuid = plugin.getPhoneManager().getPlayerByNumber(contact.number);
            String onlineStatus = contactUuid != null &&
                    plugin.getServer().getPlayer(contactUuid) != null
                    ? "§a● Online" : "§7○ Offline";

            ItemStack btn = item(Material.PLAYER_HEAD,
                    "§f" + contact.name,
                    "§7Number: §b" + contact.number,
                    onlineStatus,
                    "",
                    "§eLeft-click §7to message",
                    "§cRight-click §7to call",
                    "§7Shift-click §7to remove");
            ItemMeta meta = btn.getItemMeta();
            meta.getPersistentDataContainer().set(NUMBER_KEY, PersistentDataType.STRING, contact.number);
            meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "contact_action");
            btn.setItemMeta(meta);
            gui.setItem(slot++, btn);
        }

        if (contacts.isEmpty()) {
            gui.setItem(22, item(Material.BARRIER, "§7No contacts", "§7Add contacts below."));
        }

        // Add contact button
        ItemStack addBtn = item(Material.LIME_DYE, "§a§lAdd Contact",
                "§7Type a number in chat to add.");
        setAction(addBtn, "add_contact");
        gui.setItem(49, addBtn);

        gui.setItem(45, item(Material.ARROW, "§fBack", "§7Return to home."));
        player.openInventory(gui);
    }

    // ---- New message ----

    private void promptNewMessage(Player player) {
        player.closeInventory();
        player.sendMessage("§f§l[📱] §fEnter the §bnumber §fto message, or §fcancel§f:");
        chatSessions.put(player.getUniqueId(),
                new PhoneSession(PhoneSessionType.NEW_MSG_NUMBER, -1));
    }

    // ---- Groups ----

    private void openGroupsScreen(Player player) {
        List<Conversation> all = plugin.getPhoneManager()
                .getConversationsForPlayer(player.getUniqueId());
        List<Conversation> groups = all.stream().filter(c -> c.isGroup).toList();

        Inventory gui = plugin.getServer().createInventory(null, 54, GROUPS_GUI);

        int slot = 0;
        for (Conversation group : groups) {
            if (slot >= 45) break;
            List<UUID> members = plugin.getPhoneManager().getGroupMembers(group.id);
            ItemStack btn = item(Material.CHEST,
                    "§9§l" + group.displayName,
                    "§7Members: §f" + members.size(),
                    "§7Last: §f" + (group.lastMessage != null ? group.lastMessage : "No messages"),
                    "", "§eClick §7to open");
            ItemMeta meta = btn.getItemMeta();
            meta.getPersistentDataContainer().set(CONV_ID_KEY, PersistentDataType.INTEGER, group.id);
            meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "open_chat");
            btn.setItemMeta(meta);
            gui.setItem(slot++, btn);
        }

        if (groups.isEmpty()) {
            gui.setItem(22, item(Material.BARRIER, "§7No groups", "§7Create one below."));
        }

        ItemStack newGrpBtn = item(Material.LIME_DYE, "§a§lCreate Group",
                "§7Type a group name in chat.");
        setAction(newGrpBtn, "create_group");
        gui.setItem(49, newGrpBtn);

        gui.setItem(45, item(Material.ARROW, "§fBack", "§7Return to home."));
        player.openInventory(gui);
    }

    // ---- Click handler ----

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        if (!title.equals(HOME_GUI) && !title.equals(MESSAGES_GUI) &&
                !title.equals(CHAT_GUI) && !title.equals(CONTACTS_GUI) &&
                !title.equals(GROUPS_GUI)) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (clicked.getType() == Material.BARRIER) { player.closeInventory(); return; }
        if (clicked.getType() == Material.ARROW) {
            if (title.equals(MESSAGES_GUI) || title.equals(CONTACTS_GUI) || title.equals(GROUPS_GUI))
                openHomeScreen(player);
            else if (title.equals(CHAT_GUI))
                openMessagesScreen(player);
            return;
        }

        if (!clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer()
                .get(ACTION_KEY, PersistentDataType.STRING);
        if (action == null) return;

        switch (action) {
            case "open_messages" -> openMessagesScreen(player);
            case "open_contacts" -> openContactsScreen(player);
            case "new_message"   -> promptNewMessage(player);
            case "open_groups"   -> openGroupsScreen(player);
            case "open_chat" -> {
                Integer convId = clicked.getItemMeta().getPersistentDataContainer()
                        .get(CONV_ID_KEY, PersistentDataType.INTEGER);
                if (convId != null) openChatScreen(player, convId);
            }
            case "reply" -> {
                Integer convId = clicked.getItemMeta().getPersistentDataContainer()
                        .get(CONV_ID_KEY, PersistentDataType.INTEGER);
                if (convId == null) return;
                player.closeInventory();
                player.sendMessage("§f§l[📱] §fType your message, or §fcancel§f:");
                chatSessions.put(player.getUniqueId(),
                        new PhoneSession(PhoneSessionType.REPLY, convId));
            }
            case "add_contact" -> {
                player.closeInventory();
                player.sendMessage("§f§l[📱] §fEnter the §bnumber §fto add as contact, or §fcancel§f:");
                chatSessions.put(player.getUniqueId(),
                        new PhoneSession(PhoneSessionType.ADD_CONTACT_NUMBER, -1));
            }
            case "create_group" -> {
                player.closeInventory();
                player.sendMessage("§f§l[📱] §fEnter a §bgroup name§f, or §fcancel§f:");
                chatSessions.put(player.getUniqueId(),
                        new PhoneSession(PhoneSessionType.CREATE_GROUP, -1));
            }

            case "add_group_member" -> {
                Integer convId2 = clicked.getItemMeta().getPersistentDataContainer()
                        .get(CONV_ID_KEY, PersistentDataType.INTEGER);
                if (convId2 == null) return;
                player.closeInventory();
                player.sendMessage("§f§l[📱] §fEnter the §bnumber §fof the player to add, or §fcancel§f:");
                PhoneSession addSession = new PhoneSession(PhoneSessionType.ADD_GROUP_MEMBER, convId2);
                chatSessions.put(player.getUniqueId(), addSession);
            }

            case "contact_action" -> {
                String number = clicked.getItemMeta().getPersistentDataContainer()
                        .get(NUMBER_KEY, PersistentDataType.STRING);
                if (number == null) return;

                if (event.isShiftClick()) {
                    // Remove contact
                    plugin.getPhoneManager().removeContact(player.getUniqueId(), number);
                    player.sendMessage("§7Contact removed.");
                    openContactsScreen(player);
                } else if (event.isRightClick()) {
                    // Call
                    UUID calleeUuid = plugin.getPhoneManager().getPlayerByNumber(number);
                    if (calleeUuid == null) { player.sendMessage("§4Player not found."); return; }
                    Player callee = plugin.getServer().getPlayer(calleeUuid);
                    if (callee == null) { player.sendMessage("§4That player is not online."); return; }
                    if (plugin.getPhoneManager().isInCall(calleeUuid)) {
                        player.sendMessage("§4That player is already in a call."); return;
                    }
                    player.closeInventory();
                    plugin.getPhoneManager().initiateCall(player.getUniqueId(), calleeUuid);
                    player.sendMessage("§f§l[📱] §fCalling §b" + callee.getName() + "§f...");
                    callee.sendMessage("§f§l[📱] §bIncoming call from §f" + player.getName() +
                            " §f(" + plugin.getPhoneManager().getPhoneNumber(player.getUniqueId()) + ")§f!");
                    callee.sendMessage("§7Type §f/answercall §7to answer or §f/declinecall §7to decline.");
                    callee.playSound(callee.getLocation(),
                            org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                }
                else {
                    // Message
                    player.closeInventory();
                    int convId = plugin.getPhoneManager().getOrCreateConversation(
                            player.getUniqueId(),
                            plugin.getPhoneManager().getPlayerByNumber(number));
                    openChatScreen(player, convId);
                }
            }
        }
    }

    // ---- Chat input handler ----

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!chatSessions.containsKey(player.getUniqueId())) return;

        event.setCancelled(true);
        String input = event.getMessage().trim();
        PhoneSession session = chatSessions.get(player.getUniqueId());

        if (input.equalsIgnoreCase("cancel")) {
            chatSessions.remove(player.getUniqueId());
            player.sendMessage("§7Cancelled.");
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            switch (session.type) {
                case REPLY -> {
                    chatSessions.remove(player.getUniqueId());
                    // Determine if DM or group
                    List<UUID> parts = plugin.getPhoneManager()
                            .getConversationParticipants(session.convId);
                    boolean isGroup = plugin.getPhoneManager().getConversationsForPlayer(
                                    player.getUniqueId()).stream()
                            .anyMatch(c -> c.id == session.convId && c.isGroup);

                    if (isGroup) {
                        plugin.getPhoneManager().sendGroupMessage(
                                session.convId, player.getUniqueId(), input);
                    } else {
                        // Find the other participant
                        UUID other = parts.stream()
                                .filter(u -> !u.equals(player.getUniqueId()))
                                .findFirst().orElse(null);
                        if (other == null) { player.sendMessage("§4Could not find recipient."); return; }
                        String otherNum = plugin.getPhoneManager().getPhoneNumber(other);
                        plugin.getPhoneManager().sendMessage(
                                player.getUniqueId(), otherNum, input);
                    }
                    player.sendMessage("§a§l[📱] §aMessage sent.");
                }

                case NEW_MSG_NUMBER -> {
                    // Input is the recipient's number
                    String number = input.trim();
                    UUID recipient = plugin.getPhoneManager().getPlayerByNumber(number);
                    if (recipient == null) {
                        player.sendMessage("§4No player found with number §f" + number + "§4.");
                        return;
                    }
                    session.pendingString = number;
                    session.type = PhoneSessionType.NEW_MSG_TEXT;
                    player.sendMessage("§f§l[📱] §fNow type your message to §b" + number + "§f:");
                }

                case NEW_MSG_TEXT -> {
                    chatSessions.remove(player.getUniqueId());
                    boolean ok = plugin.getPhoneManager().sendMessage(
                            player.getUniqueId(), session.pendingString, input);
                    player.sendMessage(ok ? "§a§l[📱] §aMessage sent!" :
                            "§4Failed to send message.");
                }

                case ADD_CONTACT_NUMBER -> {
                    String number = input.trim();
                    UUID contactUuid = plugin.getPhoneManager().getPlayerByNumber(number);
                    if (contactUuid == null) {
                        player.sendMessage("§4No player found with number §f" + number + "§4.");
                        return;
                    }
                    String defaultName = plugin.getServer().getOfflinePlayer(contactUuid).getName();
                    session.pendingString = number;
                    session.type = PhoneSessionType.ADD_CONTACT_NAME;
                    player.sendMessage("§f§l[📱] §fEnter a name for this contact (default: §b" +
                            defaultName + "§f), or press Enter:");
                }

                case ADD_CONTACT_NAME -> {
                    chatSessions.remove(player.getUniqueId());
                    String number = session.pendingString;
                    UUID contactUuid = plugin.getPhoneManager().getPlayerByNumber(number);
                    String name = input.isBlank() && contactUuid != null
                            ? plugin.getServer().getOfflinePlayer(contactUuid).getName()
                            : input;
                    boolean ok = plugin.getPhoneManager().addContact(
                            player.getUniqueId(), name, number);
                    player.sendMessage(ok ? "§a§l[📱] §a" + name + " §aadded to contacts!" :
                            "§4Failed to add contact.");
                }

                case CREATE_GROUP -> {
                    chatSessions.remove(player.getUniqueId());
                    if (input.length() < 2 || input.length() > 32) {
                        player.sendMessage("§4Group name must be 2-32 characters.");
                        return;
                    }
                    int groupId = plugin.getPhoneManager().createGroup(
                            player.getUniqueId(), input);
                    if (groupId == -1) { player.sendMessage("§4Failed to create group."); return; }
                    player.sendMessage("§a§l[📱] §aGroup §b" + input + " §acreated!");
                    player.sendMessage("§7Open the group in §f/phone §7to add members.");
                }

                case ADD_GROUP_MEMBER -> {
                    chatSessions.remove(player.getUniqueId());
                    String number = input.trim();
                    UUID targetUuid = plugin.getPhoneManager().getPlayerByNumber(number);
                    if (targetUuid == null) {
                        player.sendMessage("§4No player found with number §f" + number + "§4.");
                        return;
                    }
                    // Check not already in group
                    List<UUID> members = plugin.getPhoneManager().getGroupMembers(session.convId);
                    if (members.contains(targetUuid)) {
                        player.sendMessage("§4That player is already in this group.");
                        return;
                    }
                    plugin.getPhoneManager().addGroupMember(session.convId, targetUuid);
                    String targetName = plugin.getServer().getOfflinePlayer(targetUuid).getName();
                    player.sendMessage("§a§l[📱] §b" + targetName + " §aadded to the group!");
                    // Notify the added player if online
                    Player target = plugin.getServer().getPlayer(targetUuid);
                    if (target != null) {
                        target.sendMessage("§f§l[📱] §b" + player.getName() +
                                " §fadded you to a group chat!");
                        target.playSound(target.getLocation(),
                                org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 2.0f);
                    }
                }
            }
        });
    }

    // ---- Helpers ----

    private ItemStack item(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    private void setAction(ItemStack item, String action) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
    }

    // ---- Session ----

    private enum PhoneSessionType {
        REPLY, NEW_MSG_NUMBER, NEW_MSG_TEXT,
        ADD_CONTACT_NUMBER, ADD_CONTACT_NAME,
        CREATE_GROUP, ADD_GROUP_MEMBER
    }

    private static class PhoneSession {
        PhoneSessionType type;
        int convId;
        String pendingString;
        PhoneSession(PhoneSessionType type, int convId) {
            this.type = type; this.convId = convId;
        }
    }
}