package com.UserMC.MJB;

import org.bukkit.entity.Player;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.Map;

public class CompanyManager {

    private final MJB plugin;

    public static final double REGISTRATION_FEE = 0.0;

    public CompanyManager(MJB plugin) {
        this.plugin = plugin;
    }
    private final Map<UUID, CompanyInvite> pendingInvites = new HashMap<>();

    // ---- Company Registration ----

    public boolean nameExists(String name) {
        String sql = "SELECT 1 FROM companies WHERE LOWER(name) = LOWER(?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, name);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking company name: " + e.getMessage());
            return false;
        }
    }

    public boolean ownsCompany(UUID uuid) {
        String sql = "SELECT 1 FROM companies WHERE owner_uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking company ownership: " + e.getMessage());
            return false;
        }
    }

    /**
     * Registers a new company. Deducts fee from player's bank.
     * Returns company ID or -1 on failure.
     */
    public int registerCompany(Player owner, String name, String type, String description) {
        double bank = plugin.getEconomyManager().getBankBalance(owner.getUniqueId());
        if (bank < REGISTRATION_FEE) return -2; // not enough money

        if (nameExists(name)) return -3; // name taken
        if (ownsCompany(owner.getUniqueId())) return -4; // already owns one

        // Deduct fee
        String deductSql = "UPDATE players SET bank_balance = bank_balance - ? WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(deductSql)) {
            stmt.setDouble(1, REGISTRATION_FEE);
            stmt.setString(2, owner.getUniqueId().toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error deducting registration fee: " + e.getMessage());
            return -1;
        }

        // Insert company
        String sql = "INSERT INTO companies (name, type, description, owner_uuid) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setString(2, type.toLowerCase());
            stmt.setString(3, description);
            stmt.setString(4, owner.getUniqueId().toString());
            stmt.executeUpdate();

            ResultSet keys = stmt.getGeneratedKeys();
            if (!keys.next()) return -1;
            int companyId = keys.getInt(1);

            // Add owner as member with owner role
            addMember(companyId, owner.getUniqueId(), "owner", 0);
            // Create default roles
            createDefaultRoles(companyId);

            return companyId;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error registering company: " + e.getMessage());
            return -1;
        }
    }

    private void createDefaultRoles(int companyId) {
        String sql = "INSERT IGNORE INTO company_roles (company_id, role_name, can_hire_fire, can_set_prices, can_access_bank) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            // Manager role
            stmt.setInt(1, companyId); stmt.setString(2, "manager");
            stmt.setBoolean(3, true); stmt.setBoolean(4, true); stmt.setBoolean(5, true);
            stmt.addBatch();
            // Employee role
            stmt.setInt(1, companyId); stmt.setString(2, "employee");
            stmt.setBoolean(3, false); stmt.setBoolean(4, false); stmt.setBoolean(5, false);
            stmt.addBatch();
            stmt.executeBatch();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error creating default roles: " + e.getMessage());
        }
    }

    // ---- Members ----

    public boolean addMember(int companyId, UUID uuid, String roleName, double salary) {
        String sql = "INSERT IGNORE INTO company_members (company_id, player_uuid, role_name, salary) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, companyId);
            stmt.setString(2, uuid.toString());
            stmt.setString(3, roleName);
            stmt.setDouble(4, salary);
            stmt.executeUpdate();
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) plugin.getNameTagManager().refresh(p);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error adding member: " + e.getMessage());
            return false;
        }
    }

    public boolean removeMember(int companyId, UUID uuid) {
        // Can't fire the owner
        CompanyInfo info = getCompanyById(companyId);
        if (info != null && info.ownerUuid.equals(uuid)) return false;

        String sql = "DELETE FROM company_members WHERE company_id = ? AND player_uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, companyId);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) plugin.getNameTagManager().refresh(p);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error removing member: " + e.getMessage());
            return false;
        }
    }

    public boolean isMember(int companyId, UUID uuid) {
        String sql = "SELECT 1 FROM company_members WHERE company_id = ? AND player_uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, companyId);
            stmt.setString(2, uuid.toString());
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean setSalary(int companyId, UUID memberUuid, double salary) {
        String sql = "UPDATE company_members SET salary = ? WHERE company_id = ? AND player_uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setDouble(1, salary);
            stmt.setInt(2, companyId);
            stmt.setString(3, memberUuid.toString());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error setting salary: " + e.getMessage());
            return false;
        }
    }

    public boolean setMemberRole(int companyId, UUID memberUuid, String roleName) {
        String sql = "UPDATE company_members SET role_name = ? WHERE company_id = ? AND player_uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, roleName);
            stmt.setInt(2, companyId);
            stmt.setString(3, memberUuid.toString());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error setting member role: " + e.getMessage());
            return false;
        }
    }

    public List<CompanyMember> getMembers(int companyId) {
        List<CompanyMember> members = new ArrayList<>();
        String sql = "SELECT player_uuid, role_name, salary, joined_at FROM company_members WHERE company_id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, companyId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                members.add(new CompanyMember(
                        UUID.fromString(rs.getString("player_uuid")),
                        companyId,
                        rs.getString("role_name"),
                        rs.getDouble("salary"),
                        rs.getTimestamp("joined_at")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching members: " + e.getMessage());
        }
        return members;
    }

    public CompanyMember getMember(int companyId, UUID uuid) {
        String sql = "SELECT player_uuid, role_name, salary, joined_at FROM company_members WHERE company_id = ? AND player_uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, companyId);
            stmt.setString(2, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new CompanyMember(
                        UUID.fromString(rs.getString("player_uuid")),
                        companyId,
                        rs.getString("role_name"),
                        rs.getDouble("salary"),
                        rs.getTimestamp("joined_at")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching member: " + e.getMessage());
        }
        return null;
    }

    // ---- Permissions ----

    public boolean hasPermission(int companyId, UUID uuid, String permission) {
        CompanyInfo info = getCompanyById(companyId);
        if (info == null) return false;
        if (info.ownerUuid.equals(uuid)) return true; // owner has all perms

        CompanyMember member = getMember(companyId, uuid);
        if (member == null) return false;

        String roleName = member.roleName;

        // Check custom role table
        String sql = "SELECT can_hire_fire, can_set_prices, can_access_bank FROM company_roles " +
                "WHERE company_id = ? AND LOWER(role_name) = LOWER(?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, companyId);
            stmt.setString(2, roleName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return switch (permission) {
                    case "hire_fire" -> rs.getBoolean("can_hire_fire");
                    case "set_prices" -> rs.getBoolean("can_set_prices");
                    case "access_bank" -> rs.getBoolean("can_access_bank");
                    default -> false;
                };
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking permission: " + e.getMessage());
        }
        return false;
    }

    // ---- Roles ----

    public boolean createRole(int companyId, String roleName, boolean canHireFire, boolean canSetPrices, boolean canAccessBank) {
        String sql = "INSERT INTO company_roles (company_id, role_name, can_hire_fire, can_set_prices, can_access_bank) " +
                "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE can_hire_fire = ?, can_set_prices = ?, can_access_bank = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, companyId);
            stmt.setString(2, roleName.toLowerCase());
            stmt.setBoolean(3, canHireFire);
            stmt.setBoolean(4, canSetPrices);
            stmt.setBoolean(5, canAccessBank);
            stmt.setBoolean(6, canHireFire);
            stmt.setBoolean(7, canSetPrices);
            stmt.setBoolean(8, canAccessBank);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error creating role: " + e.getMessage());
            return false;
        }
    }

    public List<CompanyRole> getRoles(int companyId) {
        List<CompanyRole> roles = new ArrayList<>();
        String sql = "SELECT role_name, can_hire_fire, can_set_prices, can_access_bank FROM company_roles WHERE company_id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, companyId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                roles.add(new CompanyRole(
                        companyId,
                        rs.getString("role_name"),
                        rs.getBoolean("can_hire_fire"),
                        rs.getBoolean("can_set_prices"),
                        rs.getBoolean("can_access_bank")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching roles: " + e.getMessage());
        }
        return roles;
    }

    // ---- Company Bank ----

    public double getCompanyBalance(int companyId) {
        String sql = "SELECT bank_balance FROM companies WHERE id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, companyId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getDouble("bank_balance");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting company balance: " + e.getMessage());
        }
        return 0;
    }

    public boolean depositToCompany(int companyId, double amount) {
        String sql = "UPDATE companies SET bank_balance = bank_balance + ? WHERE id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setDouble(1, amount);
            stmt.setInt(2, companyId);
            stmt.executeUpdate();
            // Un-bankrupt if positive again
            updateBankruptStatus(companyId);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error depositing to company: " + e.getMessage());
            return false;
        }
    }

    public boolean withdrawFromCompany(int companyId, UUID requesterUuid, double amount) {
        if (!hasPermission(companyId, requesterUuid, "access_bank")) return false;
        if (getCompanyBalance(companyId) < amount) return false;

        String sql = "UPDATE companies SET bank_balance = bank_balance - ? WHERE id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setDouble(1, amount);
            stmt.setInt(2, companyId);
            stmt.executeUpdate();
            updateBankruptStatus(companyId);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error withdrawing from company: " + e.getMessage());
            return false;
        }
    }

    /**
     * Pay from company bank to a player's personal bank (for terminal payments).
     * Does NOT check permissions — used internally.
     */
    public boolean payFromCompanyToPlayer(int companyId, UUID recipientUuid, double amount) {
        String deductSql = "UPDATE companies SET bank_balance = bank_balance - ? WHERE id = ?";
        String addSql = "UPDATE players SET bank_balance = bank_balance + ? WHERE uuid = ?";
        try (PreparedStatement s1 = plugin.getDatabaseManager().getConnection().prepareStatement(deductSql);
             PreparedStatement s2 = plugin.getDatabaseManager().getConnection().prepareStatement(addSql)) {
            s1.setDouble(1, amount); s1.setInt(2, companyId); s1.executeUpdate();
            s2.setDouble(1, amount); s2.setString(2, recipientUuid.toString()); s2.executeUpdate();
            updateBankruptStatus(companyId);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error paying from company to player: " + e.getMessage());
            return false;
        }
    }

    /**
     * Pay from a player's bank into the company bank (e.g. terminal payment).
     */
    public boolean payFromPlayerToCompany(UUID payerUuid, int companyId, double amount) {
        double balance = plugin.getEconomyManager().getBankBalance(payerUuid);
        if (balance < amount) return false;

        String deductSql = "UPDATE players SET bank_balance = bank_balance - ? WHERE uuid = ?";
        String addSql = "UPDATE companies SET bank_balance = bank_balance + ? WHERE id = ?";
        try (PreparedStatement s1 = plugin.getDatabaseManager().getConnection().prepareStatement(deductSql);
             PreparedStatement s2 = plugin.getDatabaseManager().getConnection().prepareStatement(addSql)) {
            s1.setDouble(1, amount); s1.setString(2, payerUuid.toString()); s1.executeUpdate();
            s2.setDouble(1, amount); s2.setInt(2, companyId); s2.executeUpdate();
            updateBankruptStatus(companyId);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error paying from player to company: " + e.getMessage());
            return false;
        }
    }

    private void updateBankruptStatus(int companyId) {
        String sql = "UPDATE companies SET is_bankrupt = (bank_balance < 0) WHERE id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, companyId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error updating bankrupt status: " + e.getMessage());
        }
    }

    // ---- Salary Payout ----

    /**
     * Runs daily — pays all employees from company banks.
     * If company can't afford payroll, marks bankrupt and skips.
     */
    public void processDailySalaries() {
        String sql = "SELECT id, bank_balance, is_bankrupt FROM companies";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int companyId = rs.getInt("id");
                boolean isBankrupt = rs.getBoolean("is_bankrupt");

                if (isBankrupt) continue;

                List<CompanyMember> members = getMembers(companyId);
                double totalPayroll = members.stream()
                        .filter(m -> !m.roleName.equals("owner"))
                        .mapToDouble(m -> m.salary)
                        .sum();

                if (totalPayroll <= 0) continue;

                double balance = getCompanyBalance(companyId);
                if (balance < totalPayroll) {
                    // Mark bankrupt, notify owner
                    String bankruptSql = "UPDATE companies SET is_bankrupt = TRUE WHERE id = ?";
                    try (PreparedStatement bs = plugin.getDatabaseManager().getConnection().prepareStatement(bankruptSql)) {
                        bs.setInt(1, companyId);
                        bs.executeUpdate();
                    }
                    CompanyInfo info = getCompanyById(companyId);
                    if (info != null) {
                        Player owner = plugin.getServer().getPlayer(info.ownerUuid);
                        if (owner != null) {
                            owner.sendMessage("§4§l[Company] §4Your company §f" + info.name + " §4cannot afford payroll and has gone bankrupt!");
                            owner.sendMessage("§7Deposit funds into the company account to restore operations.");
                        }
                    }
                    continue;
                }

                // Pay each member
                String deductSql = "UPDATE companies SET bank_balance = bank_balance - ? WHERE id = ?";
                String addSql = "UPDATE players SET bank_balance = bank_balance + ? WHERE uuid = ?";
                for (CompanyMember member : members) {
                    if (member.roleName.equals("owner") || member.salary <= 0) continue;
                    try (PreparedStatement ds = plugin.getDatabaseManager().getConnection().prepareStatement(deductSql);
                         PreparedStatement as = plugin.getDatabaseManager().getConnection().prepareStatement(addSql)) {
                        ds.setDouble(1, member.salary); ds.setInt(2, companyId); ds.executeUpdate();
                        as.setDouble(1, member.salary); as.setString(2, member.playerUuid.toString()); as.executeUpdate();
                    }
                    Player memberPlayer = plugin.getServer().getPlayer(member.playerUuid);
                    if (memberPlayer != null) {
                        CompanyInfo info = getCompanyById(companyId);
                        String companyName = info != null ? info.name : "your employer";
                        memberPlayer.sendMessage("§b§l[Salary] §fYou received §b" +
                                plugin.getEconomyManager().format(member.salary) +
                                " §ffrom §b" + companyName + "§f.");
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error processing salaries: " + e.getMessage());
        }
    }

    public void startSalaryScheduler() {
        long oneDayTicks = 20L * 60 * 60 * 24;
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            plugin.getServer().getScheduler().runTask(plugin, this::processDailySalaries);
        }, oneDayTicks, oneDayTicks);
    }

    // ---- Company Plots ----

    public boolean assignPlotToCompany(int companyId, String regionId, String world) {
        String sql = "INSERT INTO company_plots (company_id, region_id, world) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE company_id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, companyId);
            stmt.setString(2, regionId);
            stmt.setString(3, world);
            stmt.setInt(4, companyId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error assigning plot to company: " + e.getMessage());
            return false;
        }
    }

    public boolean removePlotFromCompany(String regionId, String world) {
        String sql = "DELETE FROM company_plots WHERE region_id = ? AND world = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, regionId);
            stmt.setString(2, world);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * If a region is a company plot, returns the company ID. Otherwise -1.
     */
    public int getCompanyForPlot(String regionId, String world) {
        String sql = "SELECT company_id FROM company_plots WHERE region_id = ? AND world = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, regionId);
            stmt.setString(2, world);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("company_id");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching company plot: " + e.getMessage());
        }
        return -1;
    }

    // ---- Company Sale ----

    public boolean sellCompany(int companyId, UUID newOwnerUuid, double salePrice) {
        CompanyInfo info = getCompanyById(companyId);
        if (info == null) return false;
        if (ownsCompany(newOwnerUuid)) return false; // buyer already owns one

        double buyerBalance = plugin.getEconomyManager().getBankBalance(newOwnerUuid);
        if (buyerBalance < salePrice) return false;

        // Transfer money
        String deductBuyer = "UPDATE players SET bank_balance = bank_balance - ? WHERE uuid = ?";
        String addSeller = "UPDATE players SET bank_balance = bank_balance + ? WHERE uuid = ?";
        String updateOwner = "UPDATE companies SET owner_uuid = ? WHERE id = ?";
        String updateMemberOld = "UPDATE company_members SET role_name = 'employee' WHERE company_id = ? AND player_uuid = ? AND role_name = 'owner'";
        String updateMemberNew = "INSERT INTO company_members (company_id, player_uuid, role_name, salary) VALUES (?, ?, 'owner', 0) " +
                "ON DUPLICATE KEY UPDATE role_name = 'owner'";

        try (PreparedStatement s1 = plugin.getDatabaseManager().getConnection().prepareStatement(deductBuyer);
             PreparedStatement s2 = plugin.getDatabaseManager().getConnection().prepareStatement(addSeller);
             PreparedStatement s3 = plugin.getDatabaseManager().getConnection().prepareStatement(updateOwner);
             PreparedStatement s4 = plugin.getDatabaseManager().getConnection().prepareStatement(updateMemberOld);
             PreparedStatement s5 = plugin.getDatabaseManager().getConnection().prepareStatement(updateMemberNew)) {

            s1.setDouble(1, salePrice); s1.setString(2, newOwnerUuid.toString()); s1.executeUpdate();
            s2.setDouble(1, salePrice); s2.setString(2, info.ownerUuid.toString()); s2.executeUpdate();
            s3.setString(1, newOwnerUuid.toString()); s3.setInt(2, companyId); s3.executeUpdate();
            s4.setInt(1, companyId); s4.setString(2, info.ownerUuid.toString()); s4.executeUpdate();
            s5.setInt(1, companyId); s5.setString(2, newOwnerUuid.toString()); s5.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error selling company: " + e.getMessage());
            return false;
        }
    }

    // ---- Lookups ----

    public CompanyInfo getCompanyById(int id) {
        String sql = "SELECT * FROM companies WHERE id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return companyFromResultSet(rs);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching company: " + e.getMessage());
        }
        return null;
    }

    public CompanyInfo getCompanyByOwner(UUID ownerUuid) {
        String sql = "SELECT * FROM companies WHERE owner_uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, ownerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return companyFromResultSet(rs);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching company by owner: " + e.getMessage());
        }
        return null;
    }

    public CompanyInfo getCompanyByName(String name) {
        String sql = "SELECT * FROM companies WHERE LOWER(name) = LOWER(?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return companyFromResultSet(rs);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching company by name: " + e.getMessage());
        }
        return null;
    }

    /**
     * Returns the company a player is a member of (any role).
     * Returns null if not in any company.
     */
    public CompanyInfo getCompanyForPlayer(UUID uuid) {
        String sql = "SELECT c.* FROM companies c " +
                "JOIN company_members m ON c.id = m.company_id " +
                "WHERE m.player_uuid = ? LIMIT 1";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return companyFromResultSet(rs);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching company for player: " + e.getMessage());
        }
        return null;
    }

    private CompanyInfo companyFromResultSet(ResultSet rs) throws SQLException {
        return new CompanyInfo(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("type"),
                rs.getString("description"),
                UUID.fromString(rs.getString("owner_uuid")),
                rs.getDouble("bank_balance"),
                rs.getBoolean("is_bankrupt"),
                rs.getTimestamp("created_at")
        );
    }

    // ---- Data Classes ----

    public static class CompanyInfo {
        public final int id;
        public final String name;
        public final String type;
        public final String description;
        public final UUID ownerUuid;
        public final double bankBalance;
        public final boolean isBankrupt;
        public final Timestamp createdAt;

        public CompanyInfo(int id, String name, String type, String description,
                           UUID ownerUuid, double bankBalance, boolean isBankrupt, Timestamp createdAt) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.description = description;
            this.ownerUuid = ownerUuid;
            this.bankBalance = bankBalance;
            this.isBankrupt = isBankrupt;
            this.createdAt = createdAt;
        }
    }

    public static class CompanyMember {
        public final UUID playerUuid;
        public final int companyId;
        public final String roleName;
        public final double salary;
        public final Timestamp joinedAt;

        public CompanyMember(UUID playerUuid, int companyId, String roleName, double salary, Timestamp joinedAt) {
            this.playerUuid = playerUuid;
            this.companyId = companyId;
            this.roleName = roleName;
            this.salary = salary;
            this.joinedAt = joinedAt;
        }
    }

    public static class CompanyRole {
        public final int companyId;
        public final String roleName;
        public final boolean canHireFire;
        public final boolean canSetPrices;
        public final boolean canAccessBank;

        public CompanyRole(int companyId, String roleName, boolean canHireFire, boolean canSetPrices, boolean canAccessBank) {
            this.companyId = companyId;
            this.roleName = roleName;
            this.canHireFire = canHireFire;
            this.canSetPrices = canSetPrices;
            this.canAccessBank = canAccessBank;
        }
    }
    public void sendInvite(int companyId, UUID inviterUuid, UUID targetUuid) {
        pendingInvites.put(targetUuid, new CompanyInvite(companyId, inviterUuid,
                System.currentTimeMillis() + 5 * 60 * 1000L));
    }

    public CompanyInvite getInvite(UUID targetUuid) {
        CompanyInvite invite = pendingInvites.get(targetUuid);
        if (invite == null) return null;
        if (System.currentTimeMillis() > invite.expiresAt) {
            pendingInvites.remove(targetUuid);
            return null;
        }
        return invite;
    }

    public void removeInvite(UUID targetUuid) {
        pendingInvites.remove(targetUuid);
    }

    // Add inner class:
    public static class CompanyInvite {
        public final int companyId;
        public final UUID inviterUuid;
        public final long expiresAt;

        public CompanyInvite(int companyId, UUID inviterUuid, long expiresAt) {
            this.companyId = companyId;
            this.inviterUuid = inviterUuid;
            this.expiresAt = expiresAt;
        }
    }

}