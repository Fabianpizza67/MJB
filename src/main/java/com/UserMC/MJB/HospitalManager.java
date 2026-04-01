package com.UserMC.MJB;

import com.UserMC.MJB.listeners.HospitalNPCListener;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HospitalManager {

    private final MJB plugin;

    public final NamespacedKey IS_MEDICAL_KEY;
    public final NamespacedKey MEDICAL_TYPE_KEY;
    public final NamespacedKey EXPIRY_KEY;
    public final NamespacedKey IS_BANDAGE_KEY;

    private static final long ITEM_EXPIRY_MS = 7L * 24 * 60 * 60 * 1000;

    private final Map<UUID, DownedPlayer> downedPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> carriedBy = new ConcurrentHashMap<>();

    public HospitalManager(MJB plugin) {
        this.plugin = plugin;
        IS_MEDICAL_KEY = new NamespacedKey(plugin, "is_medical_item");
        MEDICAL_TYPE_KEY = new NamespacedKey(plugin, "medical_type");
        EXPIRY_KEY = new NamespacedKey(plugin, "medical_expiry");
        IS_BANDAGE_KEY = new NamespacedKey(plugin, "is_bandage");
    }

    public int submitBandageRequest(UUID requesterUuid) {
        String sql = "INSERT INTO hospital_supply_requests (requester_uuid, injury_type) VALUES (?, 'BANDAGE')";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, requesterUuid.toString());
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error submitting bandage request: " + e.getMessage());
        }
        return -1;
    }

    // ---- Ranks ----

    public enum HospitalRank {
        INTERN("Intern"),
        RESIDENT("Resident"),
        DOCTOR("Doctor"),
        SURGEON("Surgeon"),
        CHIEF("Chief of Medicine");

        public final String displayName;

        HospitalRank(String displayName) {
            this.displayName = displayName;
        }

        public static HospitalRank fromString(String s) {
            if (s == null) return INTERN;
            for (HospitalRank r : values()) {
                if (r.name().equalsIgnoreCase(s)) return r;
            }
            return INTERN;
        }

        public boolean canTreatPatients() {
            return this != INTERN;
        }

        public boolean canPerformSurgery() {
            return this == SURGEON || this == CHIEF;
        }

        public boolean canApproveSupplies() {
            return this == CHIEF;
        }

        public boolean canSetSalaries() {
            return this == CHIEF;
        }
    }

    public HospitalRank getRank(UUID uuid) {
        String sql = "SELECT rank FROM hospital_doctors WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return HospitalRank.fromString(rs.getString("rank"));
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting hospital rank: " + e.getMessage());
        }
        return HospitalRank.INTERN;
    }

    public boolean setRank(UUID uuid, HospitalRank rank) {
        String sql = "UPDATE hospital_doctors SET rank = ? WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, rank.name());
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    // ---- Injury types ----

    public enum InjuryType {
        GUNSHOT_WOUND("Gunshot Wound", 3 * 60, 250.0, true),
        BROKEN_BONE("Broken Bone", 6 * 60, 100.0, false),
        BLEEDING("Bleeding", 4 * 60, 75.0, false),
        INFECTION("Infection", 10 * 60, 150.0, false),
        OVERDOSE("Overdose", 8 * 60, 300.0, false),
        BURNS("Burns", 5 * 60, 125.0, false);

        public final String displayName;
        public final int bleedoutSeconds;
        public final double supplyKitCost;
        public final boolean requiresSurgery;

        InjuryType(String displayName, int bleedoutSeconds, double supplyKitCost, boolean requiresSurgery) {
            this.displayName = displayName;
            this.bleedoutSeconds = bleedoutSeconds;
            this.supplyKitCost = supplyKitCost;
            this.requiresSurgery = requiresSurgery;
        }
    }

    // ---- Downed state ----

    public void goDown(Player player, InjuryType injury) {
        if (isDowned(player.getUniqueId())) return;

        downedPlayers.put(player.getUniqueId(),
                new DownedPlayer(player.getUniqueId(), injury, System.currentTimeMillis()));

        // Drop all items except phone
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            if (plugin.getPhoneManager().isPhone(item)) continue;
            player.getWorld().dropItemNaturally(player.getLocation(), item);
            player.getInventory().setItem(i, null);
        }

        player.setHealth(1.0);
        player.setWalkSpeed(0.03f);

        player.sendMessage("§4§l[!] §4You are downed! §c" + injury.displayName + "§4.");
        player.sendMessage("§7You have §f" + (injury.bleedoutSeconds / 60) +
                " minutes §7before you die. Call for help with §f/911§7!");
        player.sendMessage("§7Anyone nearby can carry you to the §fhospital§7.");

        for (Player nearby : plugin.getServer().getOnlinePlayers()) {
            if (nearby.equals(player)) continue;
            if (!nearby.getWorld().equals(player.getWorld())) continue;
        }
    }

    public void revive(Player patient, Player doctor) {
        if (!isDowned(patient.getUniqueId())) return;
        carriedBy.remove(patient.getUniqueId());
        downedPlayers.remove(patient.getUniqueId());

        patient.setHealth(10.0);
        patient.setWalkSpeed(0.2f);
        patient.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);

        patient.sendMessage("§a§l[Hospital] §aYou have been treated by §f" +
                doctor.getName() + "§a! You're alive!");
        patient.sendMessage("§7Your items were dropped when you went down — check the scene.");
        doctor.sendMessage("§a§l[Hospital] §aYou successfully treated §f" +
                patient.getName() + "§a.");
    }

    public void actuallyDie(Player player) {
        if (!isDowned(player.getUniqueId())) return;
        carriedBy.remove(player.getUniqueId());
        downedPlayers.remove(player.getUniqueId());
        player.setWalkSpeed(0.2f);

        String playerName = player.getName();
        UUID uuid = player.getUniqueId();

        player.sendMessage("§4§l[!] §4You have died. No one came in time.");
        player.sendMessage("§4You are banned for §f1 hour§4.");

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            java.util.Date expires = new java.util.Date(System.currentTimeMillis() + 60 * 60 * 1000L);
            plugin.getServer().getBanList(org.bukkit.BanList.Type.NAME)
                    .addBan(playerName,
                            "§4You died. You will be able to rejoin in 1 hour.",
                            expires, "Server");
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) {
                p.kickPlayer("§4You died.\n§7You are banned for §f1 hour§7.\n§7Come back later.");
            }
        }, 2L);
    }

    public boolean isDowned(UUID uuid) {
        return downedPlayers.containsKey(uuid);
    }

    public DownedPlayer getDownedPlayer(UUID uuid) {
        return downedPlayers.get(uuid);
    }

    public Map<UUID, DownedPlayer> getAllDownedPlayers() {
        return Collections.unmodifiableMap(downedPlayers);
    }

    // ---- Carry system ----

    public void startCarrying(UUID carrierUuid, UUID downedUuid) {
        carriedBy.put(downedUuid, carrierUuid);
        Player carrier = plugin.getServer().getPlayer(carrierUuid);
        Player downed = plugin.getServer().getPlayer(downedUuid);

        if (carrier != null) {
            carrier.sendMessage("§7You are carrying §f" +
                    (downed != null ? downed.getName() : "a downed player") +
                    "§7. Right-click again to drop.");
        }

        if (downed != null && carrier != null) {
            downed.sendMessage("§7§f" + carrier.getName() +
                    " §7is carrying you to the hospital.");
        }
    }

    public void stopCarrying(UUID downedUuid) {
        UUID carrierUuid = carriedBy.remove(downedUuid);
        if (carrierUuid != null) {
            Player carrier = plugin.getServer().getPlayer(carrierUuid);
            if (carrier != null) carrier.sendMessage("§7You set the patient down.");
        }
    }

    public boolean isBeingCarried(UUID downedUuid) {
        return carriedBy.containsKey(downedUuid);
    }

    public UUID getCarrier(UUID downedUuid) {
        return carriedBy.get(downedUuid);
    }

    public void startCarryScheduler() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, UUID> entry : new HashMap<>(carriedBy).entrySet()) {
                Player downed = plugin.getServer().getPlayer(entry.getKey());
                Player carrier = plugin.getServer().getPlayer(entry.getValue());

                if (downed == null || carrier == null || !carrier.isOnline()) {
                    carriedBy.remove(entry.getKey());
                    continue;
                }

                org.bukkit.Location behind = carrier.getLocation().clone()
                        .subtract(carrier.getLocation().getDirection().multiply(1.5));
                behind.setY(carrier.getLocation().getY());
                downed.teleport(behind);
            }
        }, 2L, 2L);
    }

    // ---- Bleed-out scheduler ----

    public void startBleedoutScheduler() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, DownedPlayer> entry : new HashMap<>(downedPlayers).entrySet()) {
                Player player = plugin.getServer().getPlayer(entry.getKey());

                if (player == null) {
                    downedPlayers.remove(entry.getKey());
                    carriedBy.remove(entry.getKey());
                    continue;
                }

                DownedPlayer dp = entry.getValue();
                long baseSeconds = dp.injury.bleedoutSeconds;
                long elapsed = (System.currentTimeMillis() - dp.downerAt) / 1000;
                boolean hasDrip = plugin.getMedicalRecordManager().hasIVDrip(entry.getKey());
                long effectiveTotal = hasDrip ? baseSeconds * 2 : baseSeconds;
                long remaining = effectiveTotal - elapsed;


                if (remaining <= 0) {
                    actuallyDie(player);
                    continue;
                }

                String bar = "§c§l" + dp.injury.displayName + " §r§7— §f" +
                        formatTime(remaining) + " §7remaining";
                player.sendActionBar(
                        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                .legacySection().deserialize(bar));

                if (remaining == 60 || remaining == 30) {
                    player.sendMessage("§c§l[!] §c" + remaining +
                            " seconds left before you die!");
                }
            }
        }, 20L, 20L);
    }

    private String formatTime(long seconds) {
        if (seconds >= 60) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }

    // ---- Hospital location check ----

    public boolean isAtHospital(org.bukkit.Location loc) {
        for (net.citizensnpcs.api.npc.NPC npc :
                net.citizensnpcs.api.CitizensAPI.getNPCRegistry()) {
            if (!npc.data().has(HospitalNPCListener.HOSPITAL_NPC_TAG)) continue;
            if (!npc.isSpawned()) continue;
            org.bukkit.entity.Entity entity = npc.getEntity();
            if (entity == null) continue;
            if (!entity.getWorld().equals(loc.getWorld())) continue;
            if (entity.getLocation().distance(loc) <= 15.0) return true;
        }
        return false;
    }

    // ---- Doctor management ----

    public boolean isDoctor(UUID uuid) {
        String sql = "SELECT 1 FROM hospital_doctors WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean addDoctor(UUID uuid, UUID appointedBy) {
        String sql = "INSERT IGNORE INTO hospital_doctors (uuid, appointed_by, rank) VALUES (?, ?, 'INTERN')";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, appointedBy != null ? appointedBy.toString() : null);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean removeDoctor(UUID uuid) {
        String sql = "DELETE FROM hospital_doctors WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public List<UUID> getAllDoctors() {
        List<UUID> doctors = new ArrayList<>();
        String sql = "SELECT uuid FROM hospital_doctors";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) doctors.add(UUID.fromString(rs.getString("uuid")));
        } catch (SQLException ignored) {
        }
        return doctors;
    }

    // ---- Salary ----

    public double getSalary(UUID uuid) {
        String sql = "SELECT salary FROM hospital_doctors WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getDouble("salary");
        } catch (SQLException ignored) {
        }
        return 0;
    }

    public boolean setSalary(UUID uuid, double salary) {
        String sql = "UPDATE hospital_doctors SET salary = ? WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setDouble(1, salary);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    // ---- Medical items ----

    public ItemStack createMedicalItem(String type) {
        return switch (type) {
            case "morphine" -> medItem(Material.GLASS_BOTTLE, "§fMorphine",
                    "§7Opioid painkiller.", type);
            case "gauze" -> medItem(Material.WHITE_WOOL, "§fGauze Bandage",
                    "§7Stops external bleeding.", type);
            case "naloxone" -> medItem(Material.GLASS_BOTTLE, "§cNaloxone (Narcan)",
                    "§7Reverses opioid overdose.", type);
            case "ibuprofen" -> medItem(Material.SUGAR, "§fIbuprofen",
                    "§7Anti-inflammatory painkiller.", type);
            case "amoxicillin" -> medItem(Material.SUGAR, "§fAmoxicillin",
                    "§7Antibiotic for infections.", type);
            case "iv_fluids" -> medItem(Material.POTION, "§fIV Fluids",
                    "§7Rehydrates the patient.", type);
            case "splint" -> medItem(Material.STICK, "§fSplint",
                    "§7Immobilises broken bones.", type);
            case "tourniquet" -> medItem(Material.STRING, "§fTourniquet",
                    "§7Stops severe bleeding.", type);
            case "burn_cream" -> medItem(Material.HONEY_BOTTLE, "§fBurn Cream",
                    "§7Treats burn wounds.", type);
            case "iv_drip" -> medItem(Material.POTION, "§bIV Drip",
                    "§7Slows bleed out, apply to downed players.", type);
            case "blood_test" -> medItem(Material.PAPER, "§fBlood Type Test Kit",
                    "§7Right-click a player to test their blood type.", type);
            default -> null;
        };
    }

    public ItemStack createBandage() {
        ItemStack item = new ItemStack(Material.WHITE_WOOL);
        ItemMeta meta = item.getItemMeta();
        long expiry = System.currentTimeMillis() + ITEM_EXPIRY_MS;

        meta.setDisplayName("§fBandage");
        meta.setLore(List.of(
                "§7Restores §a2 hearts§7.",
                "§7Any hospital staff can apply this.",
                "§7Expires: §f" + new java.text.SimpleDateFormat("dd/MM/yyyy")
                        .format(new java.util.Date(expiry)),
                "§b§lMedical Supply"
        ));
        meta.getPersistentDataContainer().set(IS_BANDAGE_KEY, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(EXPIRY_KEY, PersistentDataType.LONG, expiry);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isBandage(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(IS_BANDAGE_KEY, PersistentDataType.BOOLEAN);
    }

    private ItemStack medItem(Material mat, String name, String desc, String type) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        long expiry = System.currentTimeMillis() + ITEM_EXPIRY_MS;

        meta.setDisplayName(name);
        meta.setLore(List.of(
                desc,
                "§7Expires: §f" + new java.text.SimpleDateFormat("dd/MM/yyyy")
                        .format(new java.util.Date(expiry)),
                "§b§lMedical Supply"
        ));
        meta.getPersistentDataContainer().set(IS_MEDICAL_KEY, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(MEDICAL_TYPE_KEY, PersistentDataType.STRING, type);
        meta.getPersistentDataContainer().set(EXPIRY_KEY, PersistentDataType.LONG, expiry);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isMedicalItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(IS_MEDICAL_KEY, PersistentDataType.BOOLEAN);
    }

    public boolean isMedicalItemValid(ItemStack item) {
        if (!isMedicalItem(item)) return false;
        if (!item.getItemMeta().getPersistentDataContainer()
                .has(EXPIRY_KEY, PersistentDataType.LONG)) return false;
        long expiry = item.getItemMeta().getPersistentDataContainer()
                .get(EXPIRY_KEY, PersistentDataType.LONG);
        return System.currentTimeMillis() < expiry;
    }

    public List<String> getRequiredItems(InjuryType injury) {
        return switch (injury) {
            case GUNSHOT_WOUND -> List.of("morphine", "gauze");
            case BROKEN_BONE -> List.of("splint", "ibuprofen");
            case BLEEDING -> List.of("gauze", "tourniquet");
            case INFECTION -> List.of("amoxicillin", "iv_fluids");
            case OVERDOSE -> List.of("naloxone", "naloxone", "iv_fluids");
            case BURNS -> List.of("burn_cream", "gauze");
        };
    }

    public boolean hasRequiredItems(Player doctor, InjuryType injury) {
        Map<String, Integer> needed = new HashMap<>();
        for (String item : getRequiredItems(injury)) {
            needed.merge(item, 1, Integer::sum);
        }

        Map<String, Integer> found = new HashMap<>();
        for (ItemStack item : doctor.getInventory().getContents()) {
            if (!isMedicalItemValid(item)) continue;
            String type = item.getItemMeta().getPersistentDataContainer()
                    .get(MEDICAL_TYPE_KEY, PersistentDataType.STRING);
            if (type != null && needed.containsKey(type)) {
                found.merge(type, item.getAmount(), Integer::sum);
            }
        }

        for (Map.Entry<String, Integer> entry : needed.entrySet()) {
            if (found.getOrDefault(entry.getKey(), 0) < entry.getValue()) return false;
        }
        return true;
    }

    public void consumeRequiredItems(Player doctor, InjuryType injury) {
        Map<String, Integer> needed = new HashMap<>();
        for (String item : getRequiredItems(injury)) {
            needed.merge(item, 1, Integer::sum);
        }

        ItemStack[] contents = doctor.getInventory().getContents();
        for (Map.Entry<String, Integer> entry : needed.entrySet()) {
            int toConsume = entry.getValue();

            for (int i = 0; i < contents.length && toConsume > 0; i++) {
                ItemStack item = contents[i];
                if (!isMedicalItemValid(item)) continue;

                String type = item.getItemMeta().getPersistentDataContainer()
                        .get(MEDICAL_TYPE_KEY, PersistentDataType.STRING);

                if (entry.getKey().equals(type)) {
                    if (item.getAmount() > toConsume) {
                        item.setAmount(item.getAmount() - toConsume);
                        toConsume = 0;
                    } else {
                        toConsume -= item.getAmount();
                        doctor.getInventory().setItem(i, null);
                    }
                }
            }
        }

        doctor.updateInventory();
    }

    // ---- Supply requests ----

    public int submitSupplyRequest(UUID requesterUuid, InjuryType injuryType) {
        String sql = "INSERT INTO hospital_supply_requests (requester_uuid, injury_type) VALUES (?, ?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, requesterUuid.toString());
            stmt.setString(2, injuryType.name());
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error submitting supply request: " + e.getMessage());
        }
        return -1;
    }

    public int submitSpecialRequest(UUID requesterUuid, String typeName) {
        String sql = "INSERT INTO hospital_supply_requests (requester_uuid, injury_type) VALUES (?, ?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, requesterUuid.toString());
            stmt.setString(2, typeName);
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error submitting special request: " + e.getMessage());
        }
        return -1;
    }

    public List<SupplyRequest> getPendingSupplyRequests() {
        List<SupplyRequest> list = new ArrayList<>();
        String sql = "SELECT * FROM hospital_supply_requests WHERE status = 'pending' ORDER BY requested_at";

        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                list.add(new SupplyRequest(
                        rs.getInt("id"),
                        UUID.fromString(rs.getString("requester_uuid")),
                        rs.getString("injury_type"),
                        rs.getString("status"),
                        rs.getTimestamp("requested_at")
                ));
            }
        } catch (SQLException ignored) {
        }

        return list;
    }

    public void approveSupplyRequest(int id) {
        SupplyRequest req = getSupplyRequest(id);
        if (req == null) return;

        // Determine cost based on type name
        double cost;
        if (req.injuryType != null) {
            cost = req.injuryType.supplyKitCost;
        } else {
            cost = switch (req.injuryTypeName) {
                case "BANDAGE"    -> 25.0;
                case "IV_DRIP"    -> 75.0;
                case "BLOOD_TEST" -> 50.0;
                default           -> 50.0;
            };
        }

        if (!plugin.getHospitalBudgetManager().deductFromBudget(cost)) {
            notifyChiefs("§4§l[Hospital] §4Insufficient budget to approve request #" + id + "!");
            return;
        }

        markApproved(id);

        Player requester = plugin.getServer().getPlayer(req.requesterUuid);

        if (req.injuryType != null) {
            // Normal injury kit
            if (requester != null) {
                deliverSupplyKit(requester, req.injuryType);
            } else {
                storePendingDelivery(req.requesterUuid, req.injuryTypeName);
            }
        } else {
            // Special items — BANDAGE, IV_DRIP, BLOOD_TEST
            int qty = switch (req.injuryTypeName) {
                case "BANDAGE"    -> 5;
                case "IV_DRIP"    -> 3;
                case "BLOOD_TEST" -> 5;
                default           -> 1;
            };
            String medType = switch (req.injuryTypeName) {
                case "IV_DRIP"    -> "iv_drip";
                case "BLOOD_TEST" -> "blood_test";
                default           -> null;
            };

            if (requester != null) {
                if ("BANDAGE".equals(req.injuryTypeName)) {
                    for (int i = 0; i < qty; i++)
                        requester.getInventory().addItem(createBandage());
                } else if (medType != null) {
                    for (int i = 0; i < qty; i++) {
                        ItemStack item = createMedicalItem(medType);
                        if (item != null) requester.getInventory().addItem(item);
                    }
                }
                requester.sendMessage("§b§l[Hospital] §fYour approved supply request has arrived: §f" +
                        qty + "x §f" + req.injuryTypeName.replace("_", " ").toLowerCase());
            } else {
                storePendingDelivery(req.requesterUuid, req.injuryTypeName);
            }
        }
    }

    private void markApproved(int id) {
        String sql = "UPDATE hospital_supply_requests SET status = 'approved', approved_at = NOW() WHERE id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    private void notifyChiefs(String message) {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (isDoctor(p.getUniqueId()) && getRank(p.getUniqueId()).canApproveSupplies()) {
                p.sendMessage(message);
            }
        }
    }

    private void storePendingDelivery(UUID doctorUuid, String injuryTypeName) {
        String sql = "INSERT INTO hospital_pending_deliveries (doctor_uuid, injury_type) VALUES (?, ?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, doctorUuid.toString());
            stmt.setString(2, injuryTypeName);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error storing pending delivery: " + e.getMessage());
        }
    }

    private SupplyRequest getSupplyRequest(int id) {
        String sql = "SELECT * FROM hospital_supply_requests WHERE id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new SupplyRequest(
                        rs.getInt("id"),
                        UUID.fromString(rs.getString("requester_uuid")),
                        rs.getString("injury_type"),
                        rs.getString("status"),
                        rs.getTimestamp("requested_at")
                );
            }
        } catch (SQLException ignored) {
        }

        return null;
    }

    public void deliverSupplyKit(Player doctor, InjuryType injuryType) {
        for (String med : getRequiredItems(injuryType)) {
            ItemStack item = createMedicalItem(med);
            if (item != null) doctor.getInventory().addItem(item);
        }

        doctor.sendMessage("§b§l[Hospital] §fYou received a §f" +
                injuryType.displayName + " §fsupply kit. Valid for 1 week.");
    }

    public void deliverPendingSupplies(Player doctor) {
        String sql = "SELECT id, injury_type FROM hospital_pending_deliveries WHERE doctor_uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, doctor.getUniqueId().toString());
            ResultSet rs = stmt.executeQuery();
            boolean hadItems = false;
            while (rs.next()) {
                int id = rs.getInt("id");
                String typeName = rs.getString("injury_type");

                // Deliver based on type name
                if ("BANDAGE".equals(typeName)) {
                    for (int i = 0; i < 5; i++) doctor.getInventory().addItem(createBandage());
                    hadItems = true;
                } else if ("IV_DRIP".equals(typeName)) {
                    for (int i = 0; i < 3; i++) {
                        ItemStack item = createMedicalItem("iv_drip");
                        if (item != null) doctor.getInventory().addItem(item);
                    }
                    hadItems = true;
                } else if ("BLOOD_TEST".equals(typeName)) {
                    for (int i = 0; i < 5; i++) {
                        ItemStack item = createMedicalItem("blood_test");
                        if (item != null) doctor.getInventory().addItem(item);
                    }
                    hadItems = true;
                } else {
                    try {
                        InjuryType injury = InjuryType.valueOf(typeName);
                        deliverSupplyKit(doctor, injury);
                        hadItems = true;
                    } catch (IllegalArgumentException ignored) {}
                }

                String del = "DELETE FROM hospital_pending_deliveries WHERE id = ?";
                try (PreparedStatement ds = plugin.getDatabaseManager()
                        .getConnection().prepareStatement(del)) {
                    ds.setInt(1, id);
                    ds.executeUpdate();
                }
            }
            if (hadItems) {
                doctor.sendMessage("§b§l[Hospital] §fYou received supply kits that were approved while offline.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error delivering pending supplies: " + e.getMessage());
        }
    }

    // ---- Data classes ----

    public static class DownedPlayer {
        public final UUID uuid;
        public final InjuryType injury;
        public final long downerAt;

        public DownedPlayer(UUID uuid, InjuryType injury, long downerAt) {
            this.uuid = uuid;
            this.injury = injury;
            this.downerAt = downerAt;
        }
    }

    public static class SupplyRequest {
        public final int id;
        public final UUID requesterUuid;
        public final String injuryTypeName; // "GUNSHOT_WOUND", "BANDAGE", etc.
        public final InjuryType injuryType; // null for BANDAGE
        public final String status;
        public final Timestamp requestedAt;

        public SupplyRequest(int id, UUID requesterUuid, String injuryTypeName,
                             String status, Timestamp requestedAt) {
            this.id = id;
            this.requesterUuid = requesterUuid;
            this.injuryTypeName = injuryTypeName;
            this.status = status;
            this.requestedAt = requestedAt;

            InjuryType parsed = null;
            try {
                parsed = InjuryType.valueOf(injuryTypeName);
            } catch (IllegalArgumentException ignored) {
            }
            this.injuryType = parsed;
        }
    }
}