package com.UserMC.MJB;

import org.bukkit.entity.Player;

import java.sql.*;
import java.util.UUID;

public class HospitalBudgetManager {

    private final MJB plugin;

    public HospitalBudgetManager(MJB plugin) {
        this.plugin = plugin;
    }

    public double getBudget() {
        String sql = "SELECT balance FROM hospital_budget WHERE id = 1";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getDouble("balance");
        } catch (SQLException e) { }
        return 0;
    }

    public void addToBudget(double amount) {
        String sql = "UPDATE hospital_budget SET balance = balance + ? WHERE id = 1";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setDouble(1, amount);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error adding to hospital budget: " + e.getMessage());
        }
    }

    public boolean deductFromBudget(double amount) {
        String sql = "UPDATE hospital_budget SET balance = balance - ? " +
                "WHERE id = 1 AND balance >= ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setDouble(1, amount);
            stmt.setDouble(2, amount);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error deducting from hospital budget: " + e.getMessage());
            return false;
        }
    }

    public void processDailySalaries() {
        String sql = "SELECT uuid, salary FROM hospital_doctors WHERE salary > 0";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                double salary = rs.getDouble("salary");
                if (!deductFromBudget(salary)) {
                    notifyChiefs("§4§l[Hospital Budget] §4Insufficient funds to pay salaries!");
                    return;
                }
                String addSql = "UPDATE players SET bank_balance = bank_balance + ? WHERE uuid = ?";
                try (PreparedStatement addStmt = plugin.getDatabaseManager()
                        .getConnection().prepareStatement(addSql)) {
                    addStmt.setDouble(1, salary);
                    addStmt.setString(2, uuid.toString());
                    addStmt.executeUpdate();
                }
                Player doctor = plugin.getServer().getPlayer(uuid);
                if (doctor != null) {
                    doctor.sendMessage("§b§l[Hospital] §fYou received your daily salary of §b" +
                            plugin.getEconomyManager().format(salary) + "§f.");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error processing hospital salaries: " + e.getMessage());
        }
    }

    private void notifyChiefs(String message) {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (plugin.getHospitalManager().isDoctor(p.getUniqueId()) &&
                    plugin.getHospitalManager().getRank(p.getUniqueId()).canSetSalaries()) {
                p.sendMessage(message);
            }
        }
    }

    public void startSchedulers() {
        long oneDayTicks  = 20L * 60 * 60 * 24;
        long oneWeekTicks = oneDayTicks * 7;

        // Daily salaries
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () ->
                        plugin.getServer().getScheduler().runTask(plugin,
                                this::processDailySalaries),
                oneDayTicks, oneDayTicks);

        // Weekly city contribution
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    try {
                        double contribution = Double.parseDouble(
                                plugin.getGovernmentManager().getGovernmentSetting(
                                        "hospital_weekly_contribution", "0"));
                        if (contribution > 0) {
                            String sql = "UPDATE city_treasury SET balance = balance - ? " +
                                    "WHERE id = 1 AND balance >= ?";
                            try (PreparedStatement stmt = plugin.getDatabaseManager()
                                    .getConnection().prepareStatement(sql)) {
                                stmt.setDouble(1, contribution);
                                stmt.setDouble(2, contribution);
                                if (stmt.executeUpdate() > 0) {
                                    addToBudget(contribution);
                                    notifyChiefs("§b§l[Hospital] §fThe city contributed §b" +
                                            plugin.getEconomyManager().format(contribution) +
                                            " §fto the hospital budget this week.");
                                } else {
                                    notifyChiefs("§4§l[Hospital] §4Weekly contribution failed — " +
                                            "city treasury has insufficient funds!");
                                }
                            }
                        }
                    } catch (Exception ignored) { }
                }), oneWeekTicks, oneWeekTicks);
    }
}