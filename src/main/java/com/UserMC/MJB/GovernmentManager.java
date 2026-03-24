package com.UserMC.MJB;

import org.bukkit.entity.Player;

import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class GovernmentManager {

    private final MJB plugin;

    // Active election ID (-1 = none)
    private int activeElectionId = -1;
    // Active council session ID (-1 = none)
    private int activeSessionId = -1;
    // Currently active mayor UUID
    private UUID mayorUuid = null;
    private final Map<UUID, Integer> pendingPartyInvites = new java.util.concurrent.ConcurrentHashMap<>();

    // Law keys
    public static final String LAW_GUNS_LEGAL       = "guns_legal";
    public static final String LAW_TAX_RATE         = "tax_rate";
    public static final String LAW_PROPERTY_PRICE   = "property_price_modifier";
    public static final String LAW_POLICE_DEFUNDED  = "police_defunded";
    public static final String LAW_PARDON      = "pardon";
    public static final String LAW_REPEAL      = "repeal_law";
    public static final String LAW_CUSTOM           = "custom";
    public static final String LAW_POLICE_FUND = "police_fund";
    public static final String LAW_POLICE_WEEKLY = "police_weekly_contribution";

    private static final ZoneId CET = ZoneId.of("Europe/Amsterdam");

    public GovernmentManager(MJB plugin) {
        this.plugin = plugin;
    }
    public void sendPartyInvite(UUID targetUuid, int partyId) {
        pendingPartyInvites.put(targetUuid, partyId);
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> pendingPartyInvites.remove(targetUuid), 20L * 60 * 5);
    }
    public Integer getPendingInvite(UUID uuid) {
        return pendingPartyInvites.get(uuid);
    }

    public void removePendingInvite(UUID uuid) {
        pendingPartyInvites.remove(uuid);
    }

    // ---- Initialisation ----

    public void init() {
        // Load active election
        String elecSql = "SELECT id FROM elections WHERE status = 'active' LIMIT 1";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(elecSql)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) activeElectionId = rs.getInt("id");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading active election: " + e.getMessage());
        }

        // Load active session
        String sessSql = "SELECT id FROM council_sessions WHERE status = 'active' LIMIT 1";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sessSql)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) activeSessionId = rs.getInt("id");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading active session: " + e.getMessage());
        }

        if (activeElectionId != -1) {
            String endSql = "SELECT ends_at FROM elections WHERE id = ?";
            try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(endSql)) {
                stmt.setInt(1, activeElectionId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    long endsAt = rs.getTimestamp("ends_at").getTime();
                    long remainingMs = endsAt - System.currentTimeMillis();
                    if (remainingMs <= 0) {
                        // Already overdue — close immediately on next tick
                        plugin.getServer().getScheduler().runTaskLater(plugin, this::closeElection, 1L);
                    } else {
                        plugin.getServer().getScheduler().runTaskLater(plugin,
                                this::closeElection, remainingMs / 50L);
                    }
                    plugin.getLogger().info("[Government] Recovered active election, closes in " +
                            (remainingMs / 1000 / 60) + " minutes.");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error recovering election: " + e.getMessage());
            }
        }

        if (activeSessionId != -1) {
            String endSql = "SELECT ends_at FROM council_sessions WHERE id = ?";
            try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(endSql)) {
                stmt.setInt(1, activeSessionId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    long endsAt = rs.getTimestamp("ends_at").getTime();
                    long remainingMs = endsAt - System.currentTimeMillis();
                    if (remainingMs <= 0) {
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            evaluateProposals(activeSessionId);
                            closeSession();
                        }, 1L);
                    } else {
                        final int sessionIdToClose = activeSessionId;
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            evaluateProposals(sessionIdToClose);
                            closeSession();
                        }, remainingMs / 50L);
                    }
                    plugin.getLogger().info("[Government] Recovered active council session.");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error recovering session: " + e.getMessage());
            }
        }

        // Load current mayor
        String mayorSql = "SELECT mayor_uuid FROM government_state WHERE id = 1";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(mayorSql)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String uuidStr = rs.getString("mayor_uuid");
                if (uuidStr != null) mayorUuid = UUID.fromString(uuidStr);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading mayor: " + e.getMessage());
        }
    }

    // ---- Party system ----

    public boolean partyNameExists(String name) {
        String sql = "SELECT 1 FROM parties WHERE LOWER(name) = LOWER(?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, name);
            return stmt.executeQuery().next();
        } catch (SQLException e) { return false; }
    }

    public int createParty(UUID leaderUuid, String name, String description) {
        if (partyNameExists(name)) return -2;
        if (getPartyByMember(leaderUuid) != null) return -3; // already in a party

        String sql = "INSERT INTO parties (name, leader_uuid, description) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setString(2, leaderUuid.toString());
            stmt.setString(3, description);
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            if (!keys.next()) return -1;
            int id = keys.getInt(1);
            addPartyMember(id, leaderUuid);
            return id;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error creating party: " + e.getMessage());
            return -1;
        }
    }

    public boolean disbandParty(int partyId, UUID requesterUuid) {
        PartyInfo info = getPartyById(partyId);
        if (info == null || !info.leaderUuid.equals(requesterUuid)) return false;
        String sql = "DELETE FROM parties WHERE id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, partyId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) { return false; }
    }

    public boolean addPartyMember(int partyId, UUID uuid) {
        String sql = "INSERT IGNORE INTO party_members (party_id, player_uuid) VALUES (?, ?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, partyId);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) { return false; }
    }

    public boolean removePartyMember(int partyId, UUID uuid) {
        PartyInfo info = getPartyById(partyId);
        if (info != null && info.leaderUuid.equals(uuid)) return false; // can't remove leader
        String sql = "DELETE FROM party_members WHERE party_id = ? AND player_uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, partyId);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) { return false; }
    }

    public PartyInfo getPartyById(int id) {
        String sql = "SELECT * FROM parties WHERE id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return partyFromRs(rs);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting party: " + e.getMessage());
        }
        return null;
    }

    public PartyInfo getPartyByName(String name) {
        String sql = "SELECT * FROM parties WHERE LOWER(name) = LOWER(?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return partyFromRs(rs);
        } catch (SQLException e) { }
        return null;
    }

    public PartyInfo getPartyByMember(UUID uuid) {
        String sql = "SELECT p.* FROM parties p JOIN party_members m ON p.id = m.party_id WHERE m.player_uuid = ? LIMIT 1";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return partyFromRs(rs);
        } catch (SQLException e) { }
        return null;
    }

    public List<PartyInfo> getAllParties() {
        List<PartyInfo> parties = new ArrayList<>();
        String sql = "SELECT * FROM parties ORDER BY name";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) parties.add(partyFromRs(rs));
        } catch (SQLException e) { }
        return parties;
    }

    public List<UUID> getPartyMembers(int partyId) {
        List<UUID> members = new ArrayList<>();
        String sql = "SELECT player_uuid FROM party_members WHERE party_id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, partyId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) members.add(UUID.fromString(rs.getString("player_uuid")));
        } catch (SQLException e) { }
        return members;
    }

    public int getPartyMemberCount(int partyId) {
        return getPartyMembers(partyId).size();
    }

    // ---- Seats (from last election results) ----

    public int getSeatsForParty(int partyId) {
        String sql = "SELECT seats FROM election_results WHERE party_id = ? ORDER BY election_id DESC LIMIT 1";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, partyId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("seats");
        } catch (SQLException e) { }
        return 0;
    }

    public int getTotalSeats() {
        String sql = "SELECT SUM(seats) FROM election_results er " +
                "WHERE er.election_id = (SELECT MAX(id) FROM elections WHERE status = 'closed')";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { }
        return 0;
    }

    // How many seats a specific player represents (their own seat + unfilled party seats if leader)
    public int getVotingPower(UUID playerUuid) {
        PartyInfo party = getPartyByMember(playerUuid);
        if (party == null) return 0;
        int partySeats = getSeatsForParty(party.id);
        if (partySeats == 0) return 0;

        if (party.leaderUuid.equals(playerUuid)) {
            int memberCount = getPartyMemberCount(party.id);
            // Leader gets unfilled seats + their own seat
            int filledByOthers = Math.min(memberCount - 1, partySeats - 1);
            return partySeats - filledByOthers;
        } else {
            // Regular member gets 1 seat
            return 1;
        }
    }

    public boolean hasSeat(UUID playerUuid) {
        return getVotingPower(playerUuid) > 0;
    }

    // ---- Elections ----

    public boolean isElectionActive() { return activeElectionId != -1; }
    public int getActiveElectionId() { return activeElectionId; }

    public int startElection() {
        if (isElectionActive()) return activeElectionId;

        // Close any lingering active elections
        String closeSql = "UPDATE elections SET status = 'closed' WHERE status = 'active'";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(closeSql)) {
            stmt.executeUpdate();
        } catch (SQLException e) { }

        String sql = "INSERT INTO elections (status, ends_at) VALUES ('active', DATE_ADD(NOW(), INTERVAL 24 HOUR))";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            if (!keys.next()) return -1;
            activeElectionId = keys.getInt(1);

            // Broadcast
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                p.sendMessage("§6§l[Election] §eAn election has begun! Visit the voting booth to cast your vote.");
                p.sendMessage("§7Voting closes in §f24 hours§7. You cannot vote for your own party!");
            }

            // Schedule close after 24 hours
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    this::closeElection, 20L * 60 * 60 * 24);

            return activeElectionId;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error starting election: " + e.getMessage());
            return -1;
        }
    }

    public boolean castVote(UUID voterUuid, int partyId) {
        if (!isElectionActive()) return false;

        // Can't vote for own party
        PartyInfo ownParty = getPartyByMember(voterUuid);
        if (ownParty != null && ownParty.id == partyId) return false;

        // Already voted?
        String checkSql = "SELECT 1 FROM election_votes WHERE election_id = ? AND voter_uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(checkSql)) {
            stmt.setInt(1, activeElectionId);
            stmt.setString(2, voterUuid.toString());
            if (stmt.executeQuery().next()) return false;
        } catch (SQLException e) { return false; }

        String sql = "INSERT INTO election_votes (election_id, voter_uuid, party_id) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, activeElectionId);
            stmt.setString(2, voterUuid.toString());
            stmt.setInt(3, partyId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) { return false; }
    }

    public boolean hasVoted(UUID voterUuid) {
        if (!isElectionActive()) return false;
        String sql = "SELECT 1 FROM election_votes WHERE election_id = ? AND voter_uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, activeElectionId);
            stmt.setString(2, voterUuid.toString());
            return stmt.executeQuery().next();
        } catch (SQLException e) { return false; }
    }

    public void closeElection() {
        if (!isElectionActive()) return;

        int electionId = activeElectionId;
        activeElectionId = -1;

        // Count votes per party
        String sql = "SELECT party_id, COUNT(*) as votes FROM election_votes WHERE election_id = ? GROUP BY party_id ORDER BY votes DESC";
        Map<Integer, Integer> voteCounts = new LinkedHashMap<>();
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, electionId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) voteCounts.put(rs.getInt("party_id"), rs.getInt("votes"));
        } catch (SQLException e) {
            plugin.getLogger().severe("Error counting votes: " + e.getMessage());
            return;
        }

        if (voteCounts.isEmpty()) {
            // No votes cast
            String closeSql = "UPDATE elections SET status = 'closed' WHERE id = ?";
            try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(closeSql)) {
                stmt.setInt(1, electionId);
                stmt.executeUpdate();
            } catch (SQLException e) { }

            for (Player p : plugin.getServer().getOnlinePlayers()) {
                p.sendMessage("§6§l[Election] §eElection closed with no votes. Government unchanged.");
            }
            return;
        }

        // Store results (1 vote = 1 seat)
        int winnerPartyId = -1;
        int maxSeats = 0;
        String insertResult = "INSERT INTO election_results (election_id, party_id, seats, is_winner) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(insertResult)) {
            boolean first = true;
            for (Map.Entry<Integer, Integer> entry : voteCounts.entrySet()) {
                int partyId = entry.getKey();
                int seats = entry.getValue();
                boolean isWinner = first;
                if (first) { winnerPartyId = partyId; maxSeats = seats; first = false; }
                stmt.setInt(1, electionId);
                stmt.setInt(2, partyId);
                stmt.setInt(3, seats);
                stmt.setBoolean(4, isWinner);
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error storing results: " + e.getMessage());
        }

        // Close election
        String closeSql = "UPDATE elections SET status = 'closed' WHERE id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(closeSql)) {
            stmt.setInt(1, electionId);
            stmt.executeUpdate();
        } catch (SQLException e) { }

        // Set new mayor
        if (winnerPartyId != -1) {
            PartyInfo winner = getPartyById(winnerPartyId);
            if (winner != null) {
                mayorUuid = winner.leaderUuid;
                setMayorInDb(mayorUuid);

                // Announce results
                final PartyInfo finalWinner = winner;
                final Map<Integer, Integer> finalCounts = voteCounts;
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    for (Player p : plugin.getServer().getOnlinePlayers()) {
                        p.sendMessage("§6§l╔═══ ELECTION RESULTS ═══╗");
                        for (Map.Entry<Integer, Integer> e : finalCounts.entrySet()) {
                            PartyInfo party = getPartyById(e.getKey());
                            if (party == null) continue;
                            String leader = plugin.getServer().getOfflinePlayer(party.leaderUuid).getName();
                            p.sendMessage("§6§l║ §f" + party.name + " §7(" + leader + "): §e" + e.getValue() + " seats");
                        }
                        p.sendMessage("§6§l╠═══════════════════════╣");
                        String newMayorName = plugin.getServer().getOfflinePlayer(finalWinner.leaderUuid).getName();
                        p.sendMessage("§6§l║ §aWinner: §f" + finalWinner.name);
                        p.sendMessage("§6§l║ §aNew Mayor: §f" + newMayorName);
                        p.sendMessage("§6§l╚═══════════════════════╝");
                    }
                });
            }
        }
    }

    private void setMayorInDb(UUID uuid) {
        String sql = "UPDATE government_state SET mayor_uuid = ? WHERE id = 1";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid != null ? uuid.toString() : null);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error setting mayor: " + e.getMessage());
        }
    }

    public UUID getMayorUuid() { return mayorUuid; }
    public boolean isMayor(UUID uuid) { return mayorUuid != null && mayorUuid.equals(uuid); }

    // ---- Council sessions ----

    public boolean isSessionActive() { return activeSessionId != -1; }
    public int getActiveSessionId() { return activeSessionId; }

    public int openSession() {
        if (isSessionActive()) return activeSessionId;
        String sql = "INSERT INTO council_sessions (status, ends_at) VALUES ('active', DATE_ADD(NOW(), INTERVAL 2 HOUR))";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            if (!keys.next()) return -1;
            activeSessionId = keys.getInt(1);

            for (Player p : plugin.getServer().getOnlinePlayers()) {
                p.sendMessage("§9§l[Council] §bA council session is now open!");
                p.sendMessage("§7Council members may use §f/propose <text> §7in the council chamber.");
                p.sendMessage("§7Session lasts §f2 hours§7.");
            }
            // Auto-close after 2 hours
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    this::closeSession, 20L * 60 * 60 * 2);
            return activeSessionId;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error opening session: " + e.getMessage());
            return -1;
        }
    }

    public void closeSession() {
        if (!isSessionActive()) return;
        String sql = "UPDATE council_sessions SET status = 'closed' WHERE id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, activeSessionId);
            stmt.executeUpdate();
        } catch (SQLException e) { }
        activeSessionId = -1;

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.sendMessage("§9§l[Council] §7The council session has ended.");
        }
    }

    // ---- Proposals ----

    public int submitProposal(UUID proposerUuid, String text, String lawType, String lawValue) {
        if (!isSessionActive()) return -1;
        String sql = "INSERT INTO proposals (session_id, proposer_uuid, text, law_type, law_value) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, activeSessionId);
            stmt.setString(2, proposerUuid.toString());
            stmt.setString(3, text);
            stmt.setString(4, lawType);
            stmt.setString(5, lawValue);
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            if (!keys.next()) return -1;
            return keys.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error submitting proposal: " + e.getMessage());
            return -1;
        }
    }

    public boolean voteOnProposal(int proposalId, UUID voterUuid, boolean vote) {
        // Check already voted
        String checkSql = "SELECT 1 FROM proposal_votes WHERE proposal_id = ? AND voter_uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(checkSql)) {
            stmt.setInt(1, proposalId);
            stmt.setString(2, voterUuid.toString());
            if (stmt.executeQuery().next()) return false;
        } catch (SQLException e) { return false; }

        int power = getVotingPower(voterUuid);
        if (power == 0) return false;

        String insertSql = "INSERT INTO proposal_votes (proposal_id, voter_uuid, vote, seats_used) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(insertSql)) {
            stmt.setInt(1, proposalId);
            stmt.setString(2, voterUuid.toString());
            stmt.setBoolean(3, vote);
            stmt.setInt(4, power);
            stmt.executeUpdate();
        } catch (SQLException e) { return false; }

        // Update proposal totals
        String updateSql = vote
                ? "UPDATE proposals SET yes_votes = yes_votes + ? WHERE id = ?"
                : "UPDATE proposals SET no_votes = no_votes + ? WHERE id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(updateSql)) {
            stmt.setInt(1, power);
            stmt.setInt(2, proposalId);
            stmt.executeUpdate();
        } catch (SQLException e) { }

        // Check if session ends — evaluate proposal after all votes
        // (we close at end of session, not mid-session)
        return true;
    }

    public boolean hasVotedOnProposal(UUID voterUuid, int proposalId) {
        String sql = "SELECT 1 FROM proposal_votes WHERE proposal_id = ? AND voter_uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, proposalId);
            stmt.setString(2, voterUuid.toString());
            return stmt.executeQuery().next();
        } catch (SQLException e) { return false; }
    }

    public void evaluateProposals(int sessionId) {
        int totalSeats = getTotalSeats();
        String sql = "SELECT * FROM proposals WHERE session_id = ? AND status = 'open'";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, sessionId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int id = rs.getInt("id");
                int yes = rs.getInt("yes_votes");
                int no = rs.getInt("no_votes");
                String text = rs.getString("text");
                String lawType = rs.getString("law_type");
                String lawValue = rs.getString("law_value");
                int total = yes + no;
                if (total == 0) continue;

                boolean passed = yes > no && yes > (totalSeats / 2.0);

                String updateSql = "UPDATE proposals SET status = ? WHERE id = ?";
                try (PreparedStatement us = plugin.getDatabaseManager().getConnection().prepareStatement(updateSql)) {
                    us.setString(1, passed ? "passed" : "rejected");
                    us.setInt(2, id);
                    us.executeUpdate();
                }

                if (passed) {
                    enactLaw(text, lawType, lawValue, id);
                    for (Player p : plugin.getServer().getOnlinePlayers()) {
                        p.sendMessage("§a§l[Council] §aProposal passed: §f" + text);
                        p.sendMessage("§7Yes: §a" + yes + " §7| No: §c" + no);
                    }
                } else {
                    for (Player p : plugin.getServer().getOnlinePlayers()) {
                        p.sendMessage("§c§l[Council] §cProposal rejected: §f" + text);
                        p.sendMessage("§7Yes: §a" + yes + " §7| No: §c" + no);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error evaluating proposals: " + e.getMessage());
        }
    }

    public List<Proposal> getOpenProposals() {
        List<Proposal> proposals = new ArrayList<>();
        if (!isSessionActive()) return proposals;
        String sql = "SELECT * FROM proposals WHERE session_id = ? AND status = 'open' ORDER BY proposed_at";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, activeSessionId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) proposals.add(proposalFromRs(rs));
        } catch (SQLException e) { }
        return proposals;
    }

    // ---- Laws ----

    public void enactLaw(String title, String lawType, String lawValue, int proposalId) {
        // Deactivate existing law of same type if not custom
        if (!lawType.equals(LAW_CUSTOM)) {
            String deactivateSql = "UPDATE laws SET is_active = FALSE WHERE law_type = ?";
            try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(deactivateSql)) {
                stmt.setString(1, lawType);
                stmt.executeUpdate();
            } catch (SQLException e) { }
        }

        String sql = "INSERT INTO laws (title, law_type, law_value, passed_by_proposal_id, is_active) VALUES (?, ?, ?, ?, TRUE)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, title);
            stmt.setString(2, lawType);
            stmt.setString(3, lawValue);
            stmt.setInt(4, proposalId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error enacting law: " + e.getMessage());
        }

        // Apply immediate game effect
        applyLawEffect(lawType, lawValue);
    }

    private void applyLawEffect(String lawType, String lawValue) {
        switch (lawType) {
            case LAW_TAX_RATE -> {
                try {
                    double rate = Double.parseDouble(lawValue);
                    setGovernmentSetting("tax_rate", String.valueOf(Math.max(0, Math.min(100, rate))));
                } catch (NumberFormatException ignored) { }
            }
            case LAW_GUNS_LEGAL -> setGovernmentSetting("guns_legal", lawValue);
            case LAW_POLICE_DEFUNDED -> {
                if ("true".equals(lawValue)) {
                    plugin.getPoliceBudgetManager().setPoliceContribution(0);
                }
            }
            case LAW_PROPERTY_PRICE -> {
                try {
                    double modifier = Double.parseDouble(lawValue);
                    setGovernmentSetting("property_price_modifier", String.valueOf(modifier));
                } catch (NumberFormatException ignored) { }
            }
            case LAW_PARDON -> {
                // lawValue = player name
                org.bukkit.OfflinePlayer target = plugin.getServer().getOfflinePlayer(lawValue);
                if (target != null) {
                    plugin.getCrimeManager().clearCrimeRecord(target.getUniqueId());
                    // Notify if online
                    org.bukkit.entity.Player online = plugin.getServer().getPlayer(target.getUniqueId());
                    if (online != null) {
                        online.sendMessage("§a§l[Government] §aThe council has voted to pardon you.");
                        online.sendMessage("§aYour criminal record has been cleared.");
                    }
                    // Broadcast
                    for (org.bukkit.entity.Player p : plugin.getServer().getOnlinePlayers()) {
                        p.sendMessage("§6§l[Government] §fThe council has pardoned §b" + lawValue + "§f.");
                    }
                }
            }

            case LAW_POLICE_FUND -> {
                try {
                    double amount = Double.parseDouble(lawValue);
                    plugin.getPoliceBudgetManager().addToBudget(amount);
                    for (Player p : plugin.getServer().getOnlinePlayers()) {
                        p.sendMessage("§b§l[Police] §fThe city has contributed §b" +
                                plugin.getEconomyManager().format(amount) +
                                " §fto the police budget.");
                    }
                } catch (NumberFormatException ignored) { }
            }

            case LAW_POLICE_WEEKLY -> {
                try {
                    double amount = Double.parseDouble(lawValue);
                    setGovernmentSetting("police_weekly_contribution", String.valueOf(amount));
                    for (Player p : plugin.getServer().getOnlinePlayers()) {
                        p.sendMessage("§b§l[Police] §fThe city will now contribute §b" +
                                plugin.getEconomyManager().format(amount) +
                                " §fto the police budget every week.");
                    }
                } catch (NumberFormatException ignored) { }
            }

            case LAW_REPEAL -> {
                // lawValue = law ID as string
                try {
                    int lawId = Integer.parseInt(lawValue);
                    String fetchSql = "SELECT law_type, law_value FROM laws WHERE id = ? AND is_active = TRUE";
                    try (java.sql.PreparedStatement stmt = plugin.getDatabaseManager()
                            .getConnection().prepareStatement(fetchSql)) {
                        stmt.setInt(1, lawId);
                        java.sql.ResultSet rs = stmt.executeQuery();
                        if (rs.next()) {
                            String repealedType  = rs.getString("law_type");
                            String repealedValue = rs.getString("law_value");
                            repealLaw(lawId);
                            undoLawEffect(repealedType, repealedValue);
                            for (org.bukkit.entity.Player p : plugin.getServer().getOnlinePlayers()) {
                                p.sendMessage("§6§l[Government] §fLaw #" + lawId + " has been repealed by council vote.");
                            }
                        } else {
                            plugin.getLogger().warning("[Gov] Repeal voted for law #" + lawId + " but law not found or already inactive.");
                        }
                    }
                } catch (NumberFormatException | java.sql.SQLException e) {
                    plugin.getLogger().severe("Error applying repeal: " + e.getMessage());
                }
            }
        }
    }

    private void undoLawEffect(String lawType, String lawValue) {
        switch (lawType) {
            case LAW_GUNS_LEGAL ->
                    setGovernmentSetting("guns_legal", "false");

            case LAW_TAX_RATE ->
                    setGovernmentSetting("tax_rate", "0");

            case LAW_POLICE_DEFUNDED ->
                    setGovernmentSetting("police_contribution", "0");

            case LAW_PROPERTY_PRICE ->
                    setGovernmentSetting("property_price_modifier", "1.0");

            case LAW_POLICE_WEEKLY ->
                    setGovernmentSetting("police_weekly_contribution", "0");

            default -> { }
        }
    }


    public List<Law> getActiveLaws() {
        List<Law> laws = new ArrayList<>();
        String sql = "SELECT * FROM laws WHERE is_active = TRUE ORDER BY passed_at DESC";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) laws.add(lawFromRs(rs));
        } catch (SQLException e) { }
        return laws;
    }

    public boolean repealLaw(int lawId) {
        String sql = "UPDATE laws SET is_active = FALSE WHERE id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, lawId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) { return false; }
    }

    // ---- Government settings (tax rate, guns legal, etc.) ----

    public String getGovernmentSetting(String key, String defaultValue) {
        String sql = "SELECT value FROM government_settings WHERE setting_key = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, key);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("value");
        } catch (SQLException e) { }
        return defaultValue;
    }

    public void setGovernmentSetting(String key, String value) {
        String sql = "INSERT INTO government_settings (setting_key, value) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE value = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, key);
            stmt.setString(2, value);
            stmt.setString(3, value);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error setting government setting: " + e.getMessage());
        }
    }

    public double getTaxRate() {
        try { return Double.parseDouble(getGovernmentSetting("tax_rate", "0")); }
        catch (NumberFormatException e) { return 0; }
    }

    public boolean areGunsLegal() {
        return "true".equals(getGovernmentSetting("guns_legal", "false"));
    }

    public double getPropertyPriceModifier() {
        try { return Double.parseDouble(getGovernmentSetting("property_price_modifier", "1.0")); }
        catch (NumberFormatException e) { return 1.0; }
    }

    // ---- Council region ----

    public boolean addCouncilRegion(String regionId, String world) {
        String sql = "INSERT IGNORE INTO council_regions (region_id, world) VALUES (?, ?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, regionId);
            stmt.setString(2, world);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) { return false; }
    }

    public boolean isInCouncilRegion(org.bukkit.entity.Player player) {
        String regionId = plugin.getPlotManager().getRegionAtPlayer(player);
        if (regionId == null) return false;
        String sql = "SELECT 1 FROM council_regions WHERE region_id = ? AND world = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, regionId);
            stmt.setString(2, player.getWorld().getName());
            return stmt.executeQuery().next();
        } catch (SQLException e) { return false; }
    }

    // ---- Scheduled session timer ----

    public void startSessionScheduler() {
        // Check every minute if it's time to open a session
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            ZonedDateTime now = ZonedDateTime.now(CET);
            int dayOfWeek = now.getDayOfWeek().getValue(); // 1=Mon, 3=Wed, 6=Sat, 7=Sun
            int hour = now.getHour();
            int minute = now.getMinute();

            boolean shouldOpen = false;

            // Wednesday 16:00 CET
            if (dayOfWeek == 3 && hour == 16 && minute == 0) shouldOpen = true;
            // Saturday 13:00 CET
            if (dayOfWeek == 6 && hour == 13 && minute == 0) shouldOpen = true;
            // Sunday 13:00 CET — only if no election active
            if (dayOfWeek == 7 && hour == 13 && minute == 0 && !isElectionActive()) shouldOpen = true;

            if (shouldOpen && !isSessionActive()) {
                plugin.getServer().getScheduler().runTask(plugin, this::openSession);
            }

            // Sunday election at 14:00 CET every 2 weeks
            // We'll use a simpler approach — check every 2 weeks from server start
        }, 0L, 20L * 60); // every minute
    }

    public void startElectionScheduler() {
        // Elections every 2 weeks — check on Sundays at 14:00 CET
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            ZonedDateTime now = ZonedDateTime.now(CET);
            if (now.getDayOfWeek().getValue() == 7 && now.getHour() == 0
                    && now.getMinute() == 0 && !isElectionActive()) {
                plugin.getServer().getScheduler().runTask(plugin, this::startElection);
            }
        }, 0L, 20L * 60);
    }

    // ---- Helpers / fromRs ----

    private PartyInfo partyFromRs(ResultSet rs) throws SQLException {
        return new PartyInfo(
                rs.getInt("id"),
                rs.getString("name"),
                UUID.fromString(rs.getString("leader_uuid")),
                rs.getString("description"),
                rs.getTimestamp("created_at")
        );
    }

    private Proposal proposalFromRs(ResultSet rs) throws SQLException {
        return new Proposal(
                rs.getInt("id"),
                rs.getInt("session_id"),
                UUID.fromString(rs.getString("proposer_uuid")),
                rs.getString("text"),
                rs.getString("law_type"),
                rs.getString("law_value"),
                rs.getString("status"),
                rs.getInt("yes_votes"),
                rs.getInt("no_votes"),
                rs.getTimestamp("proposed_at")
        );
    }

    private Law lawFromRs(ResultSet rs) throws SQLException {
        return new Law(
                rs.getInt("id"),
                rs.getString("title"),
                rs.getString("law_type"),
                rs.getString("law_value"),
                rs.getTimestamp("passed_at"),
                rs.getBoolean("is_active")
        );
    }

    // ---- Data classes ----

    public static class PartyInfo {
        public final int id;
        public final String name;
        public final UUID leaderUuid;
        public final String description;
        public final Timestamp createdAt;
        public PartyInfo(int id, String name, UUID leaderUuid, String description, Timestamp createdAt) {
            this.id = id; this.name = name; this.leaderUuid = leaderUuid;
            this.description = description; this.createdAt = createdAt;
        }
    }

    public static class Proposal {
        public final int id, sessionId, yesVotes, noVotes;
        public final UUID proposerUuid;
        public final String text, lawType, lawValue, status;
        public final Timestamp proposedAt;
        public Proposal(int id, int sessionId, UUID proposerUuid, String text, String lawType,
                        String lawValue, String status, int yesVotes, int noVotes, Timestamp proposedAt) {
            this.id = id; this.sessionId = sessionId; this.proposerUuid = proposerUuid;
            this.text = text; this.lawType = lawType; this.lawValue = lawValue;
            this.status = status; this.yesVotes = yesVotes; this.noVotes = noVotes;
            this.proposedAt = proposedAt;
        }
    }

    public static class Law {
        public final int id;
        public final String title, lawType, lawValue;
        public final Timestamp passedAt;
        public final boolean isActive;
        public Law(int id, String title, String lawType, String lawValue, Timestamp passedAt, boolean isActive) {
            this.id = id; this.title = title; this.lawType = lawType;
            this.lawValue = lawValue; this.passedAt = passedAt; this.isActive = isActive;
        }
    }
}