package com.UserMC.MJB;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MedicalRecordManager {

    private final MJB plugin;
    private static final SimpleDateFormat SDF = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    // Blood type treatment bills
    public static final Map<HospitalManager.InjuryType, Double> TREATMENT_BILLS = Map.of(
            HospitalManager.InjuryType.BLEEDING,      150.0,
            HospitalManager.InjuryType.BROKEN_BONE,   300.0,
            HospitalManager.InjuryType.BURNS,          400.0,
            HospitalManager.InjuryType.GUNSHOT_WOUND,  750.0,
            HospitalManager.InjuryType.INFECTION,      500.0,
            HospitalManager.InjuryType.OVERDOSE,      1000.0
    );

    public enum BloodType {
        A, B, AB, O;

        public boolean canReceiveFrom(BloodType donor) {
            return switch (this) {
                case A  -> donor == A  || donor == O;
                case B  -> donor == B  || donor == O;
                case AB -> true;
                case O  -> donor == O;
            };
        }

        public static BloodType random() {
            BloodType[] values = values();
            return values[new Random().nextInt(values.length)];
        }

        public static BloodType fromString(String s) {
            if (s == null) return null;
            try { return valueOf(s.toUpperCase()); }
            catch (IllegalArgumentException e) { return null; }
        }
    }

    public MedicalRecordManager(MJB plugin) {
        this.plugin = plugin;
    }

    // ---- Blood type ----

    public BloodType getBloodType(UUID uuid) {
        String sql = "SELECT blood_type FROM players WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return BloodType.fromString(rs.getString("blood_type"));
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting blood type: " + e.getMessage());
        }
        return null;
    }

    public void assignBloodType(UUID uuid) {
        if (getBloodType(uuid) != null) return;
        BloodType type = BloodType.random();
        String sql = "UPDATE players SET blood_type = ? WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, type.name());
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error assigning blood type: " + e.getMessage());
        }
    }

    // ---- Medical records ----

    public void addRecord(UUID patientUuid, UUID doctorUuid,
                          HospitalManager.InjuryType injury,
                          double cost, String bloodTypeUsed,
                          boolean morphineUsed, String notes) {
        String sql = "INSERT INTO patient_medical_records " +
                "(patient_uuid, doctor_uuid, injury_type, treatment_cost, " +
                "blood_type_used, morphine_used, notes) VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, patientUuid.toString());
            stmt.setString(2, doctorUuid.toString());
            stmt.setString(3, injury.name());
            stmt.setDouble(4, cost);
            stmt.setString(5, bloodTypeUsed);
            stmt.setBoolean(6, morphineUsed);
            stmt.setString(7, notes);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error adding medical record: " + e.getMessage());
        }
    }

    public List<MedicalRecord> getRecords(UUID patientUuid) {
        List<MedicalRecord> records = new ArrayList<>();
        String sql = "SELECT * FROM patient_medical_records " +
                "WHERE patient_uuid = ? ORDER BY treated_at DESC LIMIT 20";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, patientUuid.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                records.add(new MedicalRecord(
                        rs.getInt("id"),
                        UUID.fromString(rs.getString("patient_uuid")),
                        UUID.fromString(rs.getString("doctor_uuid")),
                        HospitalManager.InjuryType.valueOf(rs.getString("injury_type")),
                        rs.getDouble("treatment_cost"),
                        rs.getString("blood_type_used"),
                        rs.getBoolean("morphine_used"),
                        rs.getString("notes"),
                        rs.getTimestamp("treated_at")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting medical records: " + e.getMessage());
        }
        return records;
    }

    public void printRecords(Player viewer, UUID patientUuid) {
        String name = plugin.getServer().getOfflinePlayer(patientUuid).getName();
        BloodType blood = getBloodType(patientUuid);
        List<MedicalRecord> records = getRecords(patientUuid);

        viewer.sendMessage("§b§m----------------------------------------");
        viewer.sendMessage("§b§lMedical Record: §f" + name);
        viewer.sendMessage("§7Blood type: §f" +
                (blood != null ? blood.name() : "§7Unknown (not tested)"));
        viewer.sendMessage("§7Addiction stage: §f" + getAddictionStage(patientUuid));
        viewer.sendMessage("§b§m----------------------------------------");

        if (records.isEmpty()) {
            viewer.sendMessage("§7No treatment history.");
        } else {
            for (MedicalRecord r : records) {
                String doctorName = plugin.getServer()
                        .getOfflinePlayer(r.doctorUuid).getName();
                viewer.sendMessage("§f[" + SDF.format(r.treatedAt) + "] §c" +
                        r.injury.displayName);
                viewer.sendMessage("§7  Doctor: §f" + doctorName +
                        " §7| Bill: §f" + plugin.getEconomyManager().format(r.treatmentCost) +
                        (r.morphineUsed ? " §7| §cMorphine used" : ""));
                if (r.notes != null && !r.notes.isEmpty())
                    viewer.sendMessage("§7  Notes: §f" + r.notes);
            }
        }
        viewer.sendMessage("§b§m----------------------------------------");
    }

    // ---- Treatment bill ----

    public void chargePatient(UUID patientUuid, HospitalManager.InjuryType injury) {
        double bill = TREATMENT_BILLS.getOrDefault(injury, 200.0);
        String sql = "UPDATE players SET bank_balance = bank_balance - ? WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setDouble(1, bill);
            stmt.setString(2, patientUuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error charging patient: " + e.getMessage());
        }

        // Pay to hospital budget
        plugin.getHospitalBudgetManager().addToBudget(bill);

        Player patient = plugin.getServer().getPlayer(patientUuid);
        if (patient != null) {
            double balance = plugin.getEconomyManager().getBankBalance(patientUuid);
            patient.sendMessage("§c§l[Hospital] §fYou have been billed §c" +
                    plugin.getEconomyManager().format(bill) +
                    " §ffor your treatment.");
            if (balance < 0) {
                patient.sendMessage("§4§l[Hospital] §4Your bank balance is negative! " +
                        "You owe §f" + plugin.getEconomyManager().format(Math.abs(balance)) +
                        "§4 to the hospital.");
            }
        }
    }

    // ---- IV Drip ----

    // Active IV drips: patient UUID → expiry time
    private final Map<UUID, Long> activeDrips = new HashMap<>();

    public boolean hasIVDrip(UUID uuid) {
        Long expiry = activeDrips.get(uuid);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            activeDrips.remove(uuid);
            return false;
        }
        return true;
    }

    public void applyIVDrip(UUID uuid) {
        // IV drip lasts 10 minutes
        activeDrips.put(uuid, System.currentTimeMillis() + 10 * 60 * 1000L);
    }

    public void removeIVDrip(UUID uuid) {
        activeDrips.remove(uuid);
    }

    // Get effective bleed-out seconds (halved if IV drip active)
    public int getEffectiveBleedoutSeconds(UUID uuid,
                                           HospitalManager.InjuryType injury) {
        int base = injury.bleedoutSeconds;
        return hasIVDrip(uuid) ? base * 2 : base; // IV drip doubles time remaining
    }

    // ---- Morphine addiction ----

    public void recordMorphineUse(UUID uuid) {
        String sql = "INSERT INTO morphine_usage (player_uuid) VALUES (?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) { }

        // Count uses in last 24 hours
        int uses = getMorphineUsesLast24h(uuid);
        updateAddictionStage(uuid, uses);
    }

    public int getMorphineUsesLast24h(UUID uuid) {
        String sql = "SELECT COUNT(*) FROM morphine_usage WHERE player_uuid = ? " +
                "AND used_at > NOW() - INTERVAL 24 HOUR";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { }
        return 0;
    }

    private void updateAddictionStage(UUID uuid, int uses) {
        int stage;
        if (uses >= 12)      stage = 3;
        else if (uses >= 8)  stage = 2;
        else if (uses >= 5)  stage = 1;
        else {
            clearAddiction(uuid);
            return;
        }

        String sql = "INSERT INTO morphine_addiction (player_uuid, stage, last_use) " +
                "VALUES (?, ?, NOW()) ON DUPLICATE KEY UPDATE stage = ?, last_use = NOW()";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setInt(2, stage);
            stmt.setInt(3, stage);
            stmt.executeUpdate();
        } catch (SQLException e) { }

        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null) applyAddictionEffects(player, stage);
    }

    public int getAddictionStage(UUID uuid) {
        String sql = "SELECT stage FROM morphine_addiction WHERE player_uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("stage");
        } catch (SQLException e) { }
        return 0;
    }

    public void clearAddiction(UUID uuid) {
        String sql = "DELETE FROM morphine_addiction WHERE player_uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) { }
        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null) {
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            player.removePotionEffect(PotionEffectType.WEAKNESS);
            player.removePotionEffect(PotionEffectType.NAUSEA);
        }
    }

    public void applyAddictionEffects(Player player, int stage) {
        int duration = 20 * 60 * 10; // 10 minutes — refreshed by scheduler
        switch (stage) {
            case 1 -> {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS, duration, 0, false, true));
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.WEAKNESS, duration, 0, false, true));
                player.sendMessage("§5§l[Addiction] §5You feel strange... " +
                        "§7Your body is craving more morphine.");
            }
            case 2 -> {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS, duration, 1, false, true));
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.WEAKNESS, duration, 1, false, true));
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.NAUSEA, duration, 0, false, true));
                player.sendMessage("§5§l[Addiction] §5Your hands are shaking. " +
                        "§7Morphine withdrawal is setting in badly.");
                player.sendMessage("§7§oVisit the hospital — they can treat your addiction.");
            }
            case 3 -> {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS, duration, 2, false, true));
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.WEAKNESS, duration, 2, false, true));
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.NAUSEA, duration, 1, false, true));
                player.sendMessage("§5§l[Addiction] §5§lYou are in critical withdrawal. " +
                        "§7Another morphine use will cause an overdose!");
                // Schedule a random OD check in 2-5 minutes
                int delay = (120 + new Random().nextInt(180)) * 20;
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (getAddictionStage(player.getUniqueId()) >= 3
                            && !plugin.getHospitalManager().isDowned(player.getUniqueId())) {
                        player.sendMessage("§5§l[OVERDOSE] §4Your body can't take it anymore!");
                        plugin.getHospitalManager().goDown(player,
                                HospitalManager.InjuryType.OVERDOSE);
                    }
                }, delay);
            }
        }
    }

    // Called every 5 minutes — refreshes addiction effects for online players
    public void startAddictionScheduler() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            String sql = "SELECT player_uuid, stage FROM morphine_addiction";
            try (PreparedStatement stmt = plugin.getDatabaseManager()
                    .getConnection().prepareStatement(sql)) {
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    int stage = rs.getInt("stage");
                    // Check if 24h has passed since last use — auto-clear
                    int uses = getMorphineUsesLast24h(uuid);
                    if (uses < 5) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            clearAddiction(uuid);
                            Player p = plugin.getServer().getPlayer(uuid);
                            if (p != null) p.sendMessage(
                                    "§a§l[Hospital] §aYour morphine withdrawal has passed.");
                        });
                        continue;
                    }
                    // Re-apply effects for online players
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        Player p = plugin.getServer().getPlayer(uuid);
                        if (p != null) applyAddictionEffects(p, stage);
                    });
                }
            } catch (SQLException e) { }
        }, 0L, 20L * 60 * 5);
    }

    // ---- Data class ----

    public static class MedicalRecord {
        public final int id;
        public final UUID patientUuid;
        public final UUID doctorUuid;
        public final HospitalManager.InjuryType injury;
        public final double treatmentCost;
        public final String bloodTypeUsed;
        public final boolean morphineUsed;
        public final String notes;
        public final Timestamp treatedAt;

        public MedicalRecord(int id, UUID patientUuid, UUID doctorUuid,
                             HospitalManager.InjuryType injury, double treatmentCost,
                             String bloodTypeUsed, boolean morphineUsed,
                             String notes, Timestamp treatedAt) {
            this.id = id;
            this.patientUuid = patientUuid;
            this.doctorUuid = doctorUuid;
            this.injury = injury;
            this.treatmentCost = treatmentCost;
            this.bloodTypeUsed = bloodTypeUsed;
            this.morphineUsed = morphineUsed;
            this.notes = notes;
            this.treatedAt = treatedAt;
        }
    }
}