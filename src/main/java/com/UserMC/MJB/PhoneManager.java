package com.UserMC.MJB;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PhoneManager {

    private final MJB plugin;

    public final NamespacedKey IS_PHONE_KEY;
    public final NamespacedKey PHONE_NUMBER_KEY;

    // Active call sessions: player UUID -> group ID string
    private final Map<UUID, String> activeCalls = new ConcurrentHashMap<>();
    // Pending call invites: callee UUID -> caller UUID
    private final Map<UUID, UUID> pendingCalls = new ConcurrentHashMap<>();

    public PhoneManager(MJB plugin) {
        this.plugin = plugin;
        IS_PHONE_KEY     = new NamespacedKey(plugin, "is_phone");
        PHONE_NUMBER_KEY = new NamespacedKey(plugin, "phone_number");
    }

    // ---- Phone number assignment ----

    public String assignPhoneNumber(UUID uuid) {
        // Check if already has one
        String existing = getPhoneNumber(uuid);
        if (existing != null) return existing;

        // Generate unique 06-XXXXXXXX
        String number;
        int attempts = 0;
        do {
            int rand = 10000000 + (int)(Math.random() * 90000000);
            number = "06-" + rand;
            attempts++;
            if (attempts > 100) break;
        } while (numberExists(number));

        String sql = "INSERT INTO phone_numbers (player_uuid, phone_number) VALUES (?, ?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, number);
            stmt.executeUpdate();
            return number;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error assigning phone number: " + e.getMessage());
            return null;
        }
    }

    public String getPhoneNumber(UUID uuid) {
        String sql = "SELECT phone_number FROM phone_numbers WHERE player_uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("phone_number");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting phone number: " + e.getMessage());
        }
        return null;
    }

    public UUID getPlayerByNumber(String number) {
        String sql = "SELECT player_uuid FROM phone_numbers WHERE phone_number = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, number);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return UUID.fromString(rs.getString("player_uuid"));
        } catch (SQLException e) {
            plugin.getLogger().severe("Error looking up number: " + e.getMessage());
        }
        return null;
    }

    private boolean numberExists(String number) {
        String sql = "SELECT 1 FROM phone_numbers WHERE phone_number = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, number);
            return stmt.executeQuery().next();
        } catch (SQLException e) { return false; }
    }

    // ---- Phone item ----

    public ItemStack createPhone(String number) {
        ItemStack item = new ItemStack(Material.RECOVERY_COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§f§lPhone");
        meta.setLore(List.of(
                "§7Number: §b" + number,
                "§7Right-click to open.",
                "§7Keep it charged!"
        ));
        meta.getPersistentDataContainer().set(IS_PHONE_KEY, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(PHONE_NUMBER_KEY, PersistentDataType.STRING, number);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isPhone(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(IS_PHONE_KEY, PersistentDataType.BOOLEAN);
    }

    public boolean playerHasPhone(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isPhone(item)) return true;
        }
        return false;
    }

    // ---- Contacts ----

    public boolean addContact(UUID ownerUuid, String name, String number) {
        // Verify number exists
        if (getPlayerByNumber(number) == null) return false;
        String sql = "INSERT INTO contacts (owner_uuid, contact_name, phone_number) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE contact_name = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, ownerUuid.toString());
            stmt.setString(2, name);
            stmt.setString(3, number);
            stmt.setString(4, name);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error adding contact: " + e.getMessage());
            return false;
        }
    }

    public boolean removeContact(UUID ownerUuid, String number) {
        String sql = "DELETE FROM contacts WHERE owner_uuid = ? AND phone_number = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, ownerUuid.toString());
            stmt.setString(2, number);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) { return false; }
    }

    public List<Contact> getContacts(UUID ownerUuid) {
        List<Contact> contacts = new ArrayList<>();
        String sql = "SELECT contact_name, phone_number FROM contacts WHERE owner_uuid = ? ORDER BY contact_name";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, ownerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) contacts.add(new Contact(rs.getString("contact_name"), rs.getString("phone_number")));
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching contacts: " + e.getMessage());
        }
        return contacts;
    }

    // ---- Direct messages ----

    public boolean sendMessage(UUID senderUuid, String recipientNumber, String text) {
        UUID recipientUuid = getPlayerByNumber(recipientNumber);
        if (recipientUuid == null) return false;

        // Get or create DM conversation
        int convId = getOrCreateConversation(senderUuid, recipientUuid);
        if (convId == -1) return false;

        return insertMessage(convId, senderUuid, text);
    }

    public int getOrCreateConversation(UUID a, UUID b) {
        // Conversations are stored with smaller UUID first for consistency
        String ua = a.compareTo(b) < 0 ? a.toString() : b.toString();
        String ub = a.compareTo(b) < 0 ? b.toString() : a.toString();

        String checkSql = "SELECT id FROM conversations WHERE participant_a = ? AND participant_b = ? AND is_group = FALSE";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(checkSql)) {
            stmt.setString(1, ua);
            stmt.setString(2, ub);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("id");
        } catch (SQLException e) { plugin.getLogger().severe("Error finding conversation: " + e.getMessage()); return -1; }

        String createSql = "INSERT INTO conversations (participant_a, participant_b, is_group) VALUES (?, ?, FALSE)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection()
                .prepareStatement(createSql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, ua);
            stmt.setString(2, ub);
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        } catch (SQLException e) { plugin.getLogger().severe("Error creating conversation: " + e.getMessage()); }
        return -1;
    }

    // ---- Group chats ----

    public int createGroup(UUID creatorUuid, String name) {
        String sql = "INSERT INTO conversations (group_name, participant_a, is_group) VALUES (?, ?, TRUE)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setString(2, creatorUuid.toString());
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            if (!keys.next()) return -1;
            int id = keys.getInt(1);
            addGroupMember(id, creatorUuid);
            return id;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error creating group: " + e.getMessage());
            return -1;
        }
    }

    public boolean addGroupMember(int groupId, UUID uuid) {
        String sql = "INSERT IGNORE INTO group_members (conversation_id, player_uuid) VALUES (?, ?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, groupId);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) { return false; }
    }

    public boolean removeGroupMember(int groupId, UUID uuid) {
        String sql = "DELETE FROM group_members WHERE conversation_id = ? AND player_uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, groupId);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) { return false; }
    }

    public boolean sendGroupMessage(int groupId, UUID senderUuid, String text) {
        return insertMessage(groupId, senderUuid, text);
    }

    public List<UUID> getGroupMembers(int groupId) {
        List<UUID> members = new ArrayList<>();
        String sql = "SELECT player_uuid FROM group_members WHERE conversation_id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, groupId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) members.add(UUID.fromString(rs.getString("player_uuid")));
        } catch (SQLException e) { }
        return members;
    }

    // ---- Messages ----

    private boolean insertMessage(int conversationId, UUID senderUuid, String text) {
        String sql = "INSERT INTO messages (conversation_id, sender_uuid, text) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, conversationId);
            stmt.setString(2, senderUuid.toString());
            stmt.setString(3, text);
            stmt.executeUpdate();

            // Deliver to online participants
            deliverMessage(conversationId, senderUuid, text);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error inserting message: " + e.getMessage());
            return false;
        }
    }

    private void deliverMessage(int conversationId, UUID senderUuid, String text) {
        List<UUID> recipients = getConversationParticipants(conversationId);
        String senderName = plugin.getServer().getOfflinePlayer(senderUuid).getName();
        String senderNumber = getPhoneNumber(senderUuid);

        for (UUID uuid : recipients) {
            if (uuid.equals(senderUuid)) continue;
            Player online = plugin.getServer().getPlayer(uuid);
            if (online != null) {
                online.sendMessage("§f§l[📱] §b" + senderName + " §7(" + senderNumber + ")§f: " + text);
                online.playSound(online.getLocation(),
                        org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 2.0f);
            }
        }
    }

    public List<UUID> getConversationParticipants(int convId) {
        // Check if group
        String checkSql = "SELECT is_group, participant_a, participant_b FROM conversations WHERE id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(checkSql)) {
            stmt.setInt(1, convId);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) return List.of();
            boolean isGroup = rs.getBoolean("is_group");
            if (isGroup) return getGroupMembers(convId);
            List<UUID> parts = new ArrayList<>();
            String a = rs.getString("participant_a");
            String b = rs.getString("participant_b");
            if (a != null) parts.add(UUID.fromString(a));
            if (b != null) parts.add(UUID.fromString(b));
            return parts;
        } catch (SQLException e) { return List.of(); }
    }

    public List<Message> getMessages(int conversationId, int limit) {
        List<Message> messages = new ArrayList<>();
        String sql = "SELECT * FROM messages WHERE conversation_id = ? ORDER BY sent_at DESC LIMIT ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, conversationId);
            stmt.setInt(2, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                messages.add(new Message(
                        rs.getInt("id"),
                        rs.getInt("conversation_id"),
                        UUID.fromString(rs.getString("sender_uuid")),
                        rs.getString("text"),
                        rs.getTimestamp("sent_at")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching messages: " + e.getMessage());
        }
        Collections.reverse(messages); // oldest first
        return messages;
    }

    public List<Conversation> getConversationsForPlayer(UUID uuid) {
        List<Conversation> convs = new ArrayList<>();
        // DMs
        String dmSql = "SELECT c.*, m.text as last_msg, m.sent_at as last_at " +
                "FROM conversations c LEFT JOIN messages m ON m.id = (" +
                "  SELECT id FROM messages WHERE conversation_id = c.id ORDER BY sent_at DESC LIMIT 1) " +
                "WHERE (c.participant_a = ? OR c.participant_b = ?) AND c.is_group = FALSE " +
                "ORDER BY last_at DESC";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(dmSql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) convs.add(convFromRs(rs, uuid));
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching DM conversations: " + e.getMessage());
        }
        String grpSql = "SELECT c.*, m.text as last_msg, m.sent_at as last_at " +
                "FROM conversations c " +
                "JOIN group_members gm ON gm.conversation_id = c.id " +
                "LEFT JOIN messages m ON m.id = (" +
                "  SELECT id FROM messages WHERE conversation_id = c.id ORDER BY sent_at DESC LIMIT 1) " +
                "WHERE gm.player_uuid = ? AND c.is_group = TRUE " +
                "ORDER BY last_at DESC";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(grpSql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) convs.add(convFromRs(rs, uuid));
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching group conversations: " + e.getMessage());
        }
        return convs;
    }

    private Conversation convFromRs(ResultSet rs, UUID viewer) throws SQLException {
        boolean isGroup = rs.getBoolean("is_group");
        int id = rs.getInt("id");
        String name;
        if (isGroup) {
            name = rs.getString("group_name");
        } else {
            // Show the other participant's name
            String a = rs.getString("participant_a");
            String b = rs.getString("participant_b");
            UUID other = viewer.toString().equals(a)
                    ? (b != null ? UUID.fromString(b) : viewer)
                    : UUID.fromString(a);
            String oName = plugin.getServer().getOfflinePlayer(other).getName();
            String oNum = getPhoneNumber(other);
            name = (oName != null ? oName : "Unknown") + " §7(" + oNum + ")";
        }
        String lastMsg = rs.getString("last_msg");
        return new Conversation(id, name, isGroup, lastMsg);
    }

    // ---- Calls (Simple Voice Chat) ----

    public boolean isInCall(UUID uuid) { return activeCalls.containsKey(uuid); }
    public boolean hasPendingCall(UUID uuid) { return pendingCalls.containsKey(uuid); }
    public UUID getPendingCaller(UUID calleeUuid) { return pendingCalls.get(calleeUuid); }

    public void initiateCall(UUID callerUuid, UUID calleeUuid) {
        pendingCalls.put(calleeUuid, callerUuid);

        // Auto-expire after 30 seconds
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (pendingCalls.containsKey(calleeUuid) &&
                    callerUuid.equals(pendingCalls.get(calleeUuid))) {
                pendingCalls.remove(calleeUuid);
                Player caller = plugin.getServer().getPlayer(callerUuid);
                if (caller != null) caller.sendMessage("§7Call to §b" +
                        plugin.getServer().getOfflinePlayer(calleeUuid).getName() +
                        " §7was not answered.");
            }
        }, 20L * 30);
    }

    public void acceptCall(UUID calleeUuid) {
        UUID callerUuid = pendingCalls.remove(calleeUuid);
        if (callerUuid == null) return;

        Player caller = plugin.getServer().getPlayer(callerUuid);
        Player callee = plugin.getServer().getPlayer(calleeUuid);
        if (caller == null || callee == null) return;

        // Create SVC group for the call
        tryCreateVoiceGroup(caller, callee);
    }

    public void declineCall(UUID calleeUuid) {
        UUID callerUuid = pendingCalls.remove(calleeUuid);
        if (callerUuid == null) return;
        Player caller = plugin.getServer().getPlayer(callerUuid);
        if (caller != null) {
            caller.sendMessage("§c§l[📱] §c" +
                    plugin.getServer().getOfflinePlayer(calleeUuid).getName() +
                    " §cdeclined your call.");
        }
    }

    public void endCall(UUID uuid) {
        String callType = activeCalls.remove(uuid);
        if ("svc".equals(callType)) {
            SVCIntegration.removeFromCallGroup(uuid);
        }
    }

    private void tryCreateVoiceGroup(Player caller, Player callee) {
        boolean svcSuccess = SVCIntegration.isAvailable() &&
                SVCIntegration.createCallGroup(caller, callee);

        if (svcSuccess) {
            activeCalls.put(caller.getUniqueId(), "svc");
            activeCalls.put(callee.getUniqueId(), "svc");
            caller.sendMessage("§a§l[📱] §aCall connected with §b" + callee.getName() +
                    "§a! §7Use §f/endcall §7to hang up.");
            callee.sendMessage("§a§l[📱] §aCall connected with §b" + caller.getName() +
                    "§a! §7Use §f/endcall §7to hang up.");
        } else {
            // SVC not available or player not connected to voice chat
            activeCalls.put(caller.getUniqueId(), "text_fallback");
            activeCalls.put(callee.getUniqueId(), "text_fallback");
            caller.sendMessage("§a§l[📱] §aCall connected with §b" + callee.getName() + "§a.");
            callee.sendMessage("§a§l[📱] §aCall connected with §b" + caller.getName() + "§a.");

            if (SVCIntegration.isAvailable()) {
                // SVC is installed but player isn't connected to it
                caller.sendMessage("§7Make sure Simple Voice Chat is connected to use voice.");
                callee.sendMessage("§7Make sure Simple Voice Chat is connected to use voice.");
            }
        }
    }

    // ---- Unread count ----

    public int getUnreadCount(UUID uuid) {
        String sql = "SELECT COUNT(*) FROM messages m " +
                "JOIN conversations c ON m.conversation_id = c.id " +
                "LEFT JOIN message_reads r ON r.message_id = m.id AND r.reader_uuid = ? " +
                "WHERE r.message_id IS NULL AND m.sender_uuid != ? " +
                "AND (c.participant_a = ? OR c.participant_b = ? OR c.id IN (" +
                "  SELECT conversation_id FROM group_members WHERE player_uuid = ?))";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            String uuidStr = uuid.toString();
            stmt.setString(1, uuidStr);
            stmt.setString(2, uuidStr);
            stmt.setString(3, uuidStr);
            stmt.setString(4, uuidStr);
            stmt.setString(5, uuidStr);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { }
        return 0;
    }

    public void markRead(UUID uuid, int conversationId) {
        // Mark all messages in this conversation as read for this user
        String sql = "INSERT IGNORE INTO message_reads (message_id, reader_uuid) " +
                "SELECT id, ? FROM messages WHERE conversation_id = ? AND sender_uuid != ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setInt(2, conversationId);
            stmt.setString(3, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) { }
    }

    // ---- Data classes ----

    public static class Contact {
        public final String name, number;
        public Contact(String name, String number) { this.name = name; this.number = number; }
    }

    public static class Message {
        public final int id, conversationId;
        public final UUID senderUuid;
        public final String text;
        public final Timestamp sentAt;
        public Message(int id, int conversationId, UUID senderUuid, String text, Timestamp sentAt) {
            this.id = id; this.conversationId = conversationId;
            this.senderUuid = senderUuid; this.text = text; this.sentAt = sentAt;
        }
    }

    public static class Conversation {
        public final int id;
        public final String displayName;
        public final boolean isGroup;
        public final String lastMessage;
        public Conversation(int id, String displayName, boolean isGroup, String lastMessage) {
            this.id = id; this.displayName = displayName;
            this.isGroup = isGroup; this.lastMessage = lastMessage;
        }
    }
}