package com.UserMC.MJB;

import org.bukkit.Material;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CraftingLicenseManager {

    private final MJB plugin;

    public CraftingLicenseManager(MJB plugin) {
        this.plugin = plugin;
    }

    public String getRequiredLicense(Material material) {
        String sql = "SELECT license_type FROM license_craft_rules WHERE result_material = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, material.name());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("license_type");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking craft rule: " + e.getMessage());
        }
        return null;
    }

    public boolean addRule(String licenseType, Material material) {
        String sql = "INSERT INTO license_craft_rules (license_type, result_material) " +
                "VALUES (?, ?) ON DUPLICATE KEY UPDATE license_type = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, licenseType);
            stmt.setString(2, material.name());
            stmt.setString(3, licenseType);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error adding craft rule: " + e.getMessage());
            return false;
        }
    }

    public boolean removeRule(Material material) {
        String sql = "DELETE FROM license_craft_rules WHERE result_material = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, material.name());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error removing craft rule: " + e.getMessage());
            return false;
        }
    }

    public List<CraftRule> getAllRules() {
        List<CraftRule> rules = new ArrayList<>();
        String sql = "SELECT license_type, result_material FROM license_craft_rules ORDER BY license_type, result_material";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                rules.add(new CraftRule(rs.getString("license_type"), rs.getString("result_material")));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching craft rules: " + e.getMessage());
        }
        return rules;
    }

    public List<CraftRule> getRulesForLicense(String licenseType) {
        List<CraftRule> rules = new ArrayList<>();
        String sql = "SELECT license_type, result_material FROM license_craft_rules WHERE license_type = ? ORDER BY result_material";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, licenseType);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                rules.add(new CraftRule(rs.getString("license_type"), rs.getString("result_material")));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching rules for license: " + e.getMessage());
        }
        return rules;
    }

    public static class CraftRule {
        public final String licenseType;
        public final String resultMaterial;

        public CraftRule(String licenseType, String resultMaterial) {
            this.licenseType = licenseType;
            this.resultMaterial = resultMaterial;
        }
    }
}