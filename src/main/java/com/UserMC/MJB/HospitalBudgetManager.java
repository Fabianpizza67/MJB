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
        String selectSql = "SELECT uuid, salary FROM hospital_doctors WHERE salary > 0";
        String updatePlayerSql = "UPDATE players SET bank_balance = bank_balance + ? WHERE uuid = ?";
        String deductBudgetSql = "UPDATE hospital_budget SET balance = balance - ? WHERE id = 1 AND balance >= ?";

        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement selectStmt = conn.prepareStatement(selectSql);
                 PreparedStatement updatePlayerStmt = conn.prepareStatement(updatePlayerSql);
                 PreparedStatement deductBudgetStmt = conn.prepareStatement(deductBudgetSql);
                 ResultSet rs = selectStmt.executeQuery()) {

                while (rs.next()) {
                    String uuidStr = rs.getString("uuid");
                    double salary = rs.getDouble("salary");

                    deductBudgetStmt.setDouble(1, salary);
                    deductBudgetStmt.setDouble(2, salary);

                    if (deductBudgetStmt.executeUpdate() == 0) {
                        notifyChiefs("§4§l[Hospital Budget] §4Insufficient funds for " + uuidStr);
                        continue;
                    }

                    updatePlayerStmt.setDouble(1, salary);
                    updatePlayerStmt.setString(2, uuidStr);
                    updatePlayerStmt.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Hospital salary error: " + e.getMessage());
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
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(java.time.ZoneId.of("Europe/Amsterdam"));
        java.time.ZonedDateTime nextMidday = now.withHour(12).withMinute(0).withSecond(0).withNano(0);
        if (now.isAfter(nextMidday)) nextMidday = nextMidday.plusDays(1);

        long delayTicks = java.time.Duration.between(now, nextMidday).getSeconds() * 20L;
        long oneDayTicks = 20L * 60 * 60 * 24;
        long oneWeekTicks = oneDayTicks * 7;

        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin,
                this::processDailySalaries, delayTicks, oneDayTicks);

        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
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
        }, delayTicks, oneWeekTicks);
    }
}