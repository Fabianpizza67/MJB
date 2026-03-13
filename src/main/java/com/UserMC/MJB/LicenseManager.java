package com.UserMC.MJB;

import org.bukkit.entity.Player;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LicenseManager {

    private final MJB plugin;

    // Warning period in days before license is actually cut off
    public static final int WARNING_DAYS = 3;
    // License duration in days
    public static final int LICENSE_DURATION_DAYS = 30;

    public LicenseManager(MJB plugin) {
        this.plugin = plugin;
    }

    // ---- License Types ----

    public boolean registerLicenseType(String typeName, String displayName, double cost, double renewalCost, String description) {
        String sql = "INSERT INTO license_types (type_name, display_name, cost, renewal_cost, description) " +
                "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE display_name=?, cost=?, renewal_cost=?, description=?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, typeName);
            stmt.setString(2, displayName);
            stmt.setDouble(3, cost);
            stmt.setDouble(4, renewalCost);
            stmt.setString(5, description);
            stmt.setString(6, displayName);
            stmt.setDouble(7, cost);
            stmt.setDouble(8, renewalCost);
            stmt.setString(9, description);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error registering license type: " + e.getMessage());
            return false;
        }
    }

    public List<LicenseType> getAllLicenseTypes() {
        List<LicenseType> types = new ArrayList<>();
        String sql = "SELECT * FROM license_types ORDER BY display_name";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                types.add(new LicenseType(
                        rs.getString("type_name"),
                        rs.getString("display_name"),
                        rs.getDouble("cost"),
                        rs.getDouble("renewal_cost"),
                        rs.getString("description")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching license types: " + e.getMessage());
        }
        return types;
    }

    public LicenseType getLicenseType(String typeName) {
        String sql = "SELECT * FROM license_types WHERE type_name = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, typeName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new LicenseType(
                        rs.getString("type_name"),
                        rs.getString("display_name"),
                        rs.getDouble("cost"),
                        rs.getDouble("renewal_cost"),
                        rs.getString("description")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching license type: " + e.getMessage());
        }
        return null;
    }

    // ---- Player Licenses ----

    /**
     * Issues a new license to a player. Deducts cost from their bank.
     * Returns false if they already have an active license of this type.
     */
    public boolean issueLicense(Player player, String typeName) {
        LicenseType type = getLicenseType(typeName);
        if (type == null) return false;

        // Check if already has active (non-revoked) license
        PlayerLicense existing = getLicense(player.getUniqueId(), typeName);
        if (existing != null && !existing.isRevoked) {
            return false; // already has it
        }

        double balance = plugin.getEconomyManager().getBankBalance(player.getUniqueId());
        if (balance < type.cost) return false;

        // Deduct cost
        String deductSql = "UPDATE players SET bank_balance = bank_balance - ? WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(deductSql)) {
            stmt.setDouble(1, type.cost);
            stmt.setString(2, player.getUniqueId().toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error deducting license cost: " + e.getMessage());
            return false;
        }

        // Insert or replace license record
        String sql = "INSERT INTO licenses (player_uuid, license_type, expires_at, is_revoked) " +
                "VALUES (?, ?, DATE_ADD(NOW(), INTERVAL ? DAY), FALSE) " +
                "ON DUPLICATE KEY UPDATE expires_at = DATE_ADD(NOW(), INTERVAL ? DAY), is_revoked = FALSE, revoked_by = NULL";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, player.getUniqueId().toString());
            stmt.setString(2, typeName);
            stmt.setInt(3, LICENSE_DURATION_DAYS);
            stmt.setInt(4, LICENSE_DURATION_DAYS);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error issuing license: " + e.getMessage());
            return false;
        }
    }

    /**
     * Renews an existing license. Must already have the license (even if expired/in warning).
     * Deducts renewal cost from bank.
     */
    public boolean renewLicense(Player player, String typeName) {
        LicenseType type = getLicenseType(typeName);
        if (type == null) return false;

        PlayerLicense existing = getLicense(player.getUniqueId(), typeName);
        if (existing == null) return false; // never had it — must buy first
        if (existing.isRevoked) return false; // revoked — can't renew, must appeal

        double balance = plugin.getEconomyManager().getBankBalance(player.getUniqueId());
        if (balance < type.renewalCost) return false;

        String deductSql = "UPDATE players SET bank_balance = bank_balance - ? WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(deductSql)) {
            stmt.setDouble(1, type.renewalCost);
            stmt.setString(2, player.getUniqueId().toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error deducting renewal cost: " + e.getMessage());
            return false;
        }

        // Extend from NOW (not from current expiry) so late renewals don't stack
        String sql = "UPDATE licenses SET expires_at = DATE_ADD(NOW(), INTERVAL ? DAY) " +
                "WHERE player_uuid = ? AND license_type = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, LICENSE_DURATION_DAYS);
            stmt.setString(2, player.getUniqueId().toString());
            stmt.setString(3, typeName);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error renewing license: " + e.getMessage());
            return false;
        }
    }

    /**
     * Revokes a license. Can be called by admin or judge.
     * revokedBy is the UUID of the admin/judge, or null for system.
     */
    public boolean revokeLicense(UUID playerUuid, String typeName, UUID revokedBy) {
        String sql = "UPDATE licenses SET is_revoked = TRUE, revoked_by = ? " +
                "WHERE player_uuid = ? AND license_type = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, revokedBy != null ? revokedBy.toString() : null);
            stmt.setString(2, playerUuid.toString());
            stmt.setString(3, typeName);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error revoking license: " + e.getMessage());
            return false;
        }
    }

    public PlayerLicense getLicense(UUID playerUuid, String typeName) {
        String sql = "SELECT * FROM licenses WHERE player_uuid = ? AND license_type = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, typeName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return licenseFromResultSet(rs);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching license: " + e.getMessage());
        }
        return null;
    }

    public List<PlayerLicense> getPlayerLicenses(UUID playerUuid) {
        List<PlayerLicense> licenses = new ArrayList<>();
        String sql = "SELECT l.*, lt.display_name FROM licenses l " +
                "JOIN license_types lt ON l.license_type = lt.type_name " +
                "WHERE l.player_uuid = ? ORDER BY l.expires_at";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) licenses.add(licenseFromResultSet(rs));
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching player licenses: " + e.getMessage());
        }
        return licenses;
    }

    /**
     * Core check — does this player have an active, non-revoked license?
     * Returns true if license exists, not revoked, and not past the hard cutoff.
     * Warning period (days 27-30) still returns true — they can still use it, just get nagged.
     */
    public boolean hasActiveLicense(UUID playerUuid, String typeName) {
        PlayerLicense license = getLicense(playerUuid, typeName);
        if (license == null) return false;
        if (license.isRevoked) return false;

        long now = System.currentTimeMillis();
        long expiresAt = license.expiresAt.getTime();
        long warningCutoff = expiresAt + ((long) WARNING_DAYS * 24 * 60 * 60 * 1000);

        // Still active during warning period
        return now < warningCutoff;
    }

    /**
     * Is the license in the warning period (expired but within grace)?
     */
    public boolean isInWarningPeriod(UUID playerUuid, String typeName) {
        PlayerLicense license = getLicense(playerUuid, typeName);
        if (license == null) return false;
        if (license.isRevoked) return false;

        long now = System.currentTimeMillis();
        long expiresAt = license.expiresAt.getTime();
        long warningCutoff = expiresAt + ((long) WARNING_DAYS * 24 * 60 * 60 * 1000);

        return now >= expiresAt && now < warningCutoff;
    }

    /**
     * Called on player login — warns them about expiring/expired licenses.
     */
    public void checkAndWarnOnLogin(Player player) {
        List<PlayerLicense> licenses = getPlayerLicenses(player.getUniqueId());
        long now = System.currentTimeMillis();

        for (PlayerLicense license : licenses) {
            if (license.isRevoked) continue;

            long expiresAt = license.expiresAt.getTime();
            long daysUntilExpiry = (expiresAt - now) / (1000 * 60 * 60 * 24);
            long warningCutoff = expiresAt + ((long) WARNING_DAYS * 24 * 60 * 60 * 1000);

            LicenseType type = getLicenseType(license.licenseType);
            String displayName = type != null ? type.displayName : license.licenseType;

            if (now >= warningCutoff) {
                // Past grace period — should already be cut off, but warn anyway
                player.sendMessage("§4§l[License] §4Your §f" + displayName +
                        " §4license has expired and been cut off! Visit the Government Office to renew.");
            } else if (now >= expiresAt) {
                // In warning period
                long daysLeft = (warningCutoff - now) / (1000 * 60 * 60 * 24);
                player.sendMessage("§e§l[License] §eYour §f" + displayName +
                        " §elicense has expired! You have §f" + daysLeft +
                        " day(s) §eto renew before losing access. Visit the Government Office.");
            } else if (daysUntilExpiry <= 5) {
                // Expiring soon
                player.sendMessage("§e§l[License] §eYour §f" + displayName +
                        " §elicense expires in §f" + daysUntilExpiry + " day(s)§e. Consider renewing soon.");
            }
        }
    }

    private PlayerLicense licenseFromResultSet(ResultSet rs) throws SQLException {
        return new PlayerLicense(
                rs.getInt("id"),
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("license_type"),
                rs.getTimestamp("issued_at"),
                rs.getTimestamp("expires_at"),
                rs.getBoolean("is_revoked"),
                rs.getString("revoked_by") != null ? UUID.fromString(rs.getString("revoked_by")) : null
        );
    }

    // ---- Data Classes ----

    public static class LicenseType {
        public final String typeName;
        public final String displayName;
        public final double cost;
        public final double renewalCost;
        public final String description;

        public LicenseType(String typeName, String displayName, double cost, double renewalCost, String description) {
            this.typeName = typeName;
            this.displayName = displayName;
            this.cost = cost;
            this.renewalCost = renewalCost;
            this.description = description;
        }
    }

    public static class PlayerLicense {
        public final int id;
        public final UUID playerUuid;
        public final String licenseType;
        public final Timestamp issuedAt;
        public final Timestamp expiresAt;
        public final boolean isRevoked;
        public final UUID revokedBy;

        public PlayerLicense(int id, UUID playerUuid, String licenseType,
                             Timestamp issuedAt, Timestamp expiresAt,
                             boolean isRevoked, UUID revokedBy) {
            this.id = id;
            this.playerUuid = playerUuid;
            this.licenseType = licenseType;
            this.issuedAt = issuedAt;
            this.expiresAt = expiresAt;
            this.isRevoked = isRevoked;
            this.revokedBy = revokedBy;
        }
    }
}