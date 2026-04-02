package com.UserMC.MJB.listeners;

import com.UserMC.MJB.HospitalManager;
import com.UserMC.MJB.HospitalManager.InjuryType;
import com.UserMC.MJB.MJB;
import com.UserMC.MJB.MedicalRecordManager;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HospitalNPCListener implements Listener {

    private final MJB plugin;
    public static final String HOSPITAL_NPC_TAG = "hospital_npc";

    private static final String HOSPITAL_GUI  = "§b§lHospital";
    private static final String TREATMENT_GUI = "§c§lTreat Patient";
    private static final String SURGERY_GUI   = "§4§lSurgery — avoid the red!";
    private static final String PENDING_GUI   = "§b§lPending Requests";
    private static final String SALARY_GUI    = "§b§lSet Salaries";
    private static final String SUPPLY_GUI    = "§b§lRequest Supplies";

    private final NamespacedKey ACTION_KEY;
    private final NamespacedKey PATIENT_KEY;
    private final NamespacedKey REQUEST_ID_KEY;

    private final Map<UUID, SurgerySession> surgerySessions = new ConcurrentHashMap<>();
    private final Map<UUID, String> awaitingChat = new HashMap<>();

    public HospitalNPCListener(MJB plugin) {
        this.plugin = plugin;
        ACTION_KEY     = new NamespacedKey(plugin, "hospital_action");
        PATIENT_KEY    = new NamespacedKey(plugin, "hospital_patient");
        REQUEST_ID_KEY = new NamespacedKey(plugin, "request_id");
    }

    @EventHandler
    public void onNPCRightClick(NPCRightClickEvent event) {
        NPC npc = event.getNPC();
        if (!npc.data().has(HOSPITAL_NPC_TAG)) return;
        Player player = event.getClicker();
        if (!plugin.getHospitalManager().isDoctor(player.getUniqueId())) {
            player.sendMessage("§4This terminal is for hospital staff only.");
            return;
        }
        openHospitalMenu(player);
    }

    @EventHandler
    public void onInteractDowned(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Player target)) return;
        Player doctor = event.getPlayer();
        if (!plugin.getHospitalManager().isDoctor(doctor.getUniqueId())) return;
        if (!plugin.getHospitalManager().isDowned(target.getUniqueId())) return;
        event.setCancelled(true);
        if (!plugin.getHospitalManager().isAtHospital(target.getLocation())) {
            doctor.sendMessage("§4The patient must be at the hospital to be treated.");
            doctor.sendMessage("§7Carry them within range of the hospital first.");
            return;
        }
        openTreatmentMenu(doctor, target);
    }

    // ---- Menus ----

    private void openHospitalMenu(Player doctor) {
        HospitalManager.HospitalRank rank =
                plugin.getHospitalManager().getRank(doctor.getUniqueId());
        Inventory gui = plugin.getServer().createInventory(null, 27, HOSPITAL_GUI);
        double budget = plugin.getHospitalBudgetManager().getBudget();

        gui.setItem(4, item(Material.NETHER_STAR, "§b§lHospital Terminal",
                "§7Budget: §f" + plugin.getEconomyManager().format(budget),
                "§7Your rank: §b" + rank.displayName));

        if (rank.canTreatPatients()) {
            ItemStack supplyBtn = item(Material.CHEST, "§f§lRequest Supplies",
                    "§7Request medical supplies.",
                    "§7All requests require Chief approval.");
            setAction(supplyBtn, "request_supplies");
            gui.setItem(10, supplyBtn);
        }

        if (rank.canApproveSupplies()) {
            List<HospitalManager.SupplyRequest> pending =
                    plugin.getHospitalManager().getPendingSupplyRequests();
            ItemStack approveBtn = item(Material.WRITABLE_BOOK,
                    "§f§lPending Requests §7(" + pending.size() + ")",
                    "§7Approve supply requests from staff.");
            setAction(approveBtn, "open_pending");
            gui.setItem(12, approveBtn);

            ItemStack salaryBtn = item(Material.PLAYER_HEAD,
                    "§f§lSet Salaries",
                    "§7Set daily salary per doctor.");
            setAction(salaryBtn, "open_salaries");
            gui.setItem(14, salaryBtn);
        }

        gui.setItem(22, item(Material.BARRIER, "§4Close", "§7Close."));
        doctor.openInventory(gui);
    }

    private void openTreatmentMenu(Player doctor, Player patient) {
        HospitalManager.DownedPlayer dp =
                plugin.getHospitalManager().getDownedPlayer(patient.getUniqueId());
        if (dp == null) return;

        HospitalManager.HospitalRank rank =
                plugin.getHospitalManager().getRank(doctor.getUniqueId());

        if (!rank.canTreatPatients()) {
            doctor.sendMessage("§4Interns cannot treat patients. Observe and learn.");
            return;
        }
        if (dp.injury.requiresSurgery && !rank.canPerformSurgery()) {
            doctor.sendMessage("§4Only Surgeons and the Chief can perform surgery.");
            return;
        }

        InjuryType injury = dp.injury;
        long elapsed   = (System.currentTimeMillis() - dp.downerAt) / 1000;
        long remaining = injury.bleedoutSeconds - elapsed;
        boolean hasItems = plugin.getHospitalManager().hasRequiredItems(doctor, injury);

        Inventory gui = plugin.getServer().createInventory(null, 27, TREATMENT_GUI);

        gui.setItem(4, item(Material.PLAYER_HEAD,
                "§c§lPatient: §f" + patient.getName(),
                "§7Condition: §c" + injury.displayName,
                "§7Time remaining: §f" + formatTime(remaining),
                hasItems ? "§aYou have the required supplies."
                        : "§4You are missing required supplies!"));

        List<String> required = plugin.getHospitalManager().getRequiredItems(injury);
        gui.setItem(10, item(Material.BOOK, "§fRequired Items",
                required.stream().map(s -> "§7- §f" + formatMedName(s))
                        .toArray(String[]::new)));

        if (hasItems) {
            ItemStack treatBtn = item(Material.EMERALD,
                    "§a§lTreat " + patient.getName(),
                    "§7Consumes required items from your inventory.",
                    injury.requiresSurgery ? "§7§oSurgery minigame follows." : "");
            ItemMeta meta = treatBtn.getItemMeta();
            meta.getPersistentDataContainer().set(ACTION_KEY,
                    PersistentDataType.STRING, "treat");
            meta.getPersistentDataContainer().set(PATIENT_KEY,
                    PersistentDataType.STRING, patient.getUniqueId().toString());
            treatBtn.setItemMeta(meta);
            gui.setItem(13, treatBtn);
        } else {
            gui.setItem(13, item(Material.BARRIER,
                    "§4Missing Supplies",
                    "§7Request a supply kit from the hospital terminal.",
                    "§7Items expire after 1 week."));
        }

        gui.setItem(22, item(Material.ARROW, "§fBack", "§7Cancel."));
        doctor.openInventory(gui);
    }

    private void openSupplyRequestMenu(Player doctor) {
        Inventory gui = plugin.getServer().createInventory(null, 54, SUPPLY_GUI);

        gui.setItem(4, item(Material.PAPER, "§b§lRequest a Supply Kit",
                "§7ALL requests require Chief of Medicine approval.",
                "§7Items expire 1 week after delivery."));

        int slot = 9;

        // Injury treatment kits
        for (InjuryType injury : InjuryType.values()) {
            List<String> lore = new ArrayList<>();
            lore.add("§7Includes:");
            for (String med : plugin.getHospitalManager().getRequiredItems(injury))
                lore.add("§7  - §f" + formatMedName(med));
            lore.add("§7Kit cost: §b" +
                    plugin.getEconomyManager().format(injury.supplyKitCost));
            lore.add("§eClick §7to submit request.");
            ItemStack btn = item(Material.LIME_DYE,
                    "§f" + injury.displayName + " kit",
                    lore.toArray(new String[0]));
            setAction(btn, "submit_request_" + injury.name());
            gui.setItem(slot++, btn);
        }

        // Bandage pack
        ItemStack bandageBtn = item(Material.WHITE_WOOL, "§fBandage Pack §7(x5)",
                "§7Heals 2 hearts each. Any staff can use.",
                "§7Kit cost: §b$25",
                "§eClick §7to submit request.");
        setAction(bandageBtn, "submit_request_BANDAGE");
        gui.setItem(slot++, bandageBtn);

        // IV drip
        ItemStack ivBtn = item(Material.POTION, "§bIV Drip Pack §7(x3)",
                "§7Doubles bleed-out time for downed patients.",
                "§7Kit cost: §b$75",
                "§eClick §7to submit request.");
        setAction(ivBtn, "submit_request_IV_DRIP");
        gui.setItem(slot++, ivBtn);

        // Blood test kit
        ItemStack bloodBtn = item(Material.PAPER, "§fBlood Test Kit §7(x5)",
                "§7Test a player's blood type.",
                "§7Kit cost: §b$50",
                "§eClick §7to submit request.");
        setAction(bloodBtn, "submit_request_BLOOD_TEST");
        gui.setItem(slot++, bloodBtn);
        // Medical radio
        ItemStack radioBtn = item(Material.CLOCK, "§aMedical Radio §7(x1)",
                "§7Encrypted radio for medical channel.",
                "§7Kit cost: §b$500",
                "§eClick §7to submit request.");
        setAction(radioBtn, "submit_request_MEDICAL_RADIO");
        gui.setItem(slot++, radioBtn);

        gui.setItem(45, item(Material.ARROW, "§fBack", "§7Return to main menu."));
        doctor.openInventory(gui);
    }

    private void openPendingMenu(Player chief) {
        List<HospitalManager.SupplyRequest> pending =
                plugin.getHospitalManager().getPendingSupplyRequests();
        Inventory gui = plugin.getServer().createInventory(null, 54, PENDING_GUI);

        if (pending.isEmpty()) {
            gui.setItem(22, item(Material.LIME_DYE, "§aNo pending requests", "§7All clear!"));
        }

        int slot = 0;
        for (HospitalManager.SupplyRequest req : pending) {
            if (slot >= 45) break;

            String requesterName = plugin.getServer()
                    .getOfflinePlayer(req.requesterUuid).getName();

            // Display name depends on whether it's a standard injury kit or a special type
            String displayName;
            String costDisplay;
            if (req.injuryType != null) {
                displayName = req.injuryType.displayName + " kit";
                costDisplay = plugin.getEconomyManager().format(req.injuryType.supplyKitCost);
            } else {
                displayName = switch (req.injuryTypeName) {
                    case "BANDAGE"       -> "Bandage Pack (x5)";
                    case "IV_DRIP"       -> "IV Drip Pack (x3)";
                    case "BLOOD_TEST"    -> "Blood Test Kit (x5)";
                    case "MEDICAL_RADIO" -> "Medical Radio (x1)";
                    default              -> req.injuryTypeName;
                };
                costDisplay = switch (req.injuryTypeName) {
                    case "BANDAGE"       -> "$25";
                    case "IV_DRIP"       -> "$75";
                    case "BLOOD_TEST"    -> "$50";
                    case "MEDICAL_RADIO" -> "$500";
                    default              -> "unknown";
                };
            }

            ItemStack btn = item(Material.CHEST,
                    "§f" + displayName,
                    "§7Requested by: §b" + requesterName,
                    "§7Cost: §b" + costDisplay,
                    "§7Budget: §f" +
                            plugin.getEconomyManager()
                                    .format(plugin.getHospitalBudgetManager().getBudget()),
                    "",
                    "§aClick §7to approve",
                    "§cShift-click §7to dismiss");

            ItemMeta meta = btn.getItemMeta();
            meta.getPersistentDataContainer().set(REQUEST_ID_KEY,
                    PersistentDataType.INTEGER, req.id);
            btn.setItemMeta(meta);
            gui.setItem(slot++, btn);
        }

        gui.setItem(45, item(Material.ARROW, "§fBack", "§7Return to main menu."));
        chief.openInventory(gui);
    }

    private void openSalaryMenu(Player chief) {
        List<UUID> doctors = plugin.getHospitalManager().getAllDoctors();
        Inventory gui = plugin.getServer().createInventory(null, 54, SALARY_GUI);

        int slot = 0;
        for (UUID uuid : doctors) {
            if (slot >= 45) break;
            String name = plugin.getServer().getOfflinePlayer(uuid).getName();
            double salary = plugin.getHospitalManager().getSalary(uuid);
            HospitalManager.HospitalRank rank =
                    plugin.getHospitalManager().getRank(uuid);

            ItemStack btn = item(Material.PLAYER_HEAD,
                    "§b" + (name != null ? name : uuid.toString()),
                    "§7Rank: §f" + rank.displayName,
                    "§7Salary: §f" + plugin.getEconomyManager().format(salary) + " §7/day",
                    "",
                    "§eClick §7to set salary (type in chat)");
            ItemMeta meta = btn.getItemMeta();
            meta.getPersistentDataContainer().set(ACTION_KEY,
                    PersistentDataType.STRING, "set_salary_" + uuid);
            btn.setItemMeta(meta);
            gui.setItem(slot++, btn);
        }

        gui.setItem(45, item(Material.ARROW, "§fBack", "§7Return to main menu."));
        chief.openInventory(gui);
    }

    private void openSurgery(Player doctor, Player patient) {
        HospitalManager.DownedPlayer dp =
                plugin.getHospitalManager().getDownedPlayer(patient.getUniqueId());
        if (dp == null) {
            doctor.sendMessage("§4Patient is no longer in a valid state for surgery.");
            return;
        }

        Inventory gui = plugin.getServer().createInventory(null, 27, SURGERY_GUI);

        List<ItemStack> tools = List.of(
                namedItem(Material.IRON_HOE,  "§fScalpel"),
                namedItem(Material.IRON_HOE,  "§fScalpel"),
                namedItem(Material.SHEARS,    "§fSurgical Scissors"),
                namedItem(Material.SHEARS,    "§fSurgical Scissors"),
                namedItem(Material.STRING,    "§fSuture Thread"),
                namedItem(Material.STRING,    "§fSuture Thread"),
                namedItem(Material.PAPER,     "§fSurgical Sponge"),
                namedItem(Material.PAPER,     "§fSurgical Sponge")
        );

        List<ItemStack> bad = List.of(
                new ItemStack(Material.RED_CONCRETE),
                new ItemStack(Material.RED_CONCRETE),
                new ItemStack(Material.RED_STAINED_GLASS_PANE),
                new ItemStack(Material.RED_STAINED_GLASS_PANE),
                new ItemStack(Material.RED_CONCRETE)
        );

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.setDisplayName(" ");
        filler.setItemMeta(fm);
        for (int i = 0; i < 27; i++) gui.setItem(i, filler);

        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < 27; i++) slots.add(i);
        Collections.shuffle(slots);

        List<Integer> toolSlots = new ArrayList<>();
        for (int i = 0; i < tools.size(); i++) {
            int s = slots.get(i);
            gui.setItem(s, tools.get(i));
            toolSlots.add(s);
        }
        for (int i = 0; i < bad.size(); i++) {
            gui.setItem(slots.get(tools.size() + i), bad.get(i));
        }

        SurgerySession session = new SurgerySession(
                patient.getUniqueId(), doctor.getUniqueId(),
                dp.injury, new HashSet<>(toolSlots), tools.size());
        surgerySessions.put(doctor.getUniqueId(), session);

        doctor.openInventory(gui);
        doctor.sendMessage("§c§l[Surgery] §fClick all surgical tools! Avoid the red items!");
        doctor.sendMessage("§7You have §f15 seconds§7. 3 strikes and surgery fails.");

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!surgerySessions.containsKey(doctor.getUniqueId())) return;
            surgerySessions.remove(doctor.getUniqueId());
            doctor.closeInventory();
            doctor.sendMessage("§4§l[Surgery] §4Time ran out! Surgery failed.");
            HospitalManager.DownedPlayer downed =
                    plugin.getHospitalManager().getDownedPlayer(session.patientUuid);
            if (downed != null) {
                plugin.getHospitalBudgetManager().addToBudget(downed.injury.supplyKitCost / 2);
            }
        }, 20L * 15);
    }

    // ---- Click handler ----

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        // Surgery minigame
        if (title.equals(SURGERY_GUI)) {
            event.setCancelled(true);
            SurgerySession session = surgerySessions.get(player.getUniqueId());
            if (session == null) return;
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;
            if (clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

            if (clicked.getType() == Material.RED_CONCRETE
                    || clicked.getType() == Material.RED_STAINED_GLASS_PANE) {
                session.strikes++;
                event.getInventory().setItem(event.getSlot(), null);
                player.sendMessage("§c§l[Surgery] §cStrike " + session.strikes + "/3!");
                if (session.strikes >= 3) {
                    surgerySessions.remove(player.getUniqueId());
                    player.closeInventory();
                    player.sendMessage("§4§l[Surgery] §4Too many mistakes! Surgery failed.");
                    HospitalManager.DownedPlayer dp =
                            plugin.getHospitalManager().getDownedPlayer(session.patientUuid);
                    if (dp != null) {
                        plugin.getHospitalBudgetManager()
                                .addToBudget(dp.injury.supplyKitCost / 2);
                        Player patient = plugin.getServer().getPlayer(session.patientUuid);
                        if (patient != null)
                            patient.sendMessage("§4§l[Hospital] §4Surgery failed!");
                    }
                }
                return;
            }

            session.toolsClicked++;
            event.getInventory().setItem(event.getSlot(), null);
            if (session.toolsClicked >= session.totalTools) {
                surgerySessions.remove(player.getUniqueId());
                player.closeInventory();
                player.sendMessage("§a§l[Surgery] §aSurgery successful!");
                Player patient = plugin.getServer().getPlayer(session.patientUuid);
                if (patient != null) {
                    plugin.getHospitalManager().revive(patient, player);
                    double bill = MedicalRecordManager.TREATMENT_BILLS
                            .getOrDefault(session.injury, 200.0);
                    plugin.getMedicalRecordManager().chargePatient(session.patientUuid, session.injury);
                    plugin.getMedicalRecordManager().addRecord(
                            session.patientUuid, session.doctorUuid,
                            session.injury, bill, null, true, "Surgery performed");
                }
            }
            return;
        }

        // Pending requests GUI
        if (title.equals(PENDING_GUI)) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;
            if (clicked.getType() == Material.ARROW) { openHospitalMenu(player); return; }
            if (clicked.getType() == Material.LIME_DYE) return;
            if (!clicked.hasItemMeta()) return;
            if (!clicked.getItemMeta().getPersistentDataContainer()
                    .has(REQUEST_ID_KEY, PersistentDataType.INTEGER)) return;
            int reqId = clicked.getItemMeta().getPersistentDataContainer()
                    .get(REQUEST_ID_KEY, PersistentDataType.INTEGER);
            if (event.isShiftClick()) {
                String sql = "UPDATE hospital_supply_requests SET status = 'dismissed' WHERE id = ?";
                try (java.sql.PreparedStatement stmt = plugin.getDatabaseManager()
                        .getConnection().prepareStatement(sql)) {
                    stmt.setInt(1, reqId);
                    stmt.executeUpdate();
                } catch (java.sql.SQLException ignored) {}
                player.sendMessage("§7Request dismissed.");
            } else {
                plugin.getHospitalManager().approveSupplyRequest(reqId);
                player.sendMessage("§aRequest #" + reqId + " approved!");
            }
            openPendingMenu(player);
            return;
        }

        // Salary GUI
        if (title.equals(SALARY_GUI)) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;
            if (clicked.getType() == Material.ARROW) { openHospitalMenu(player); return; }
            if (!clicked.hasItemMeta()) return;
            String action = getAction(clicked);
            if (action == null || !action.startsWith("set_salary_")) return;
            UUID targetUuid = UUID.fromString(action.substring(11));
            player.closeInventory();
            player.sendMessage("§b§lSet Salary §7— Type the daily salary, or §fcancel§7:");
            awaitingChat.put(player.getUniqueId(), "set_salary_" + targetUuid);
            return;
        }

        // Main hospital GUIs
        if (!title.equals(HOSPITAL_GUI) && !title.equals(TREATMENT_GUI)
                && !title.equals(SUPPLY_GUI)) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (clicked.getType() == Material.BARRIER) { player.closeInventory(); return; }
        if (clicked.getType() == Material.ARROW) { openHospitalMenu(player); return; }

        String action = getAction(clicked);
        if (action == null) return;

        switch (action) {
            case "request_supplies" -> openSupplyRequestMenu(player);
            case "open_pending"     -> openPendingMenu(player);
            case "open_salaries"    -> openSalaryMenu(player);

            case "treat" -> {
                String patientStr = clicked.getItemMeta().getPersistentDataContainer()
                        .get(PATIENT_KEY, PersistentDataType.STRING);
                if (patientStr == null) return;
                UUID patientUuid = UUID.fromString(patientStr);
                Player patient = plugin.getServer().getPlayer(patientUuid);
                if (patient == null) {
                    player.sendMessage("§4Patient is no longer online.");
                    player.closeInventory();
                    return;
                }
                HospitalManager.DownedPlayer dp =
                        plugin.getHospitalManager().getDownedPlayer(patientUuid);
                if (dp == null) {
                    player.sendMessage("§4Patient is no longer downed.");
                    player.closeInventory();
                    return;
                }
                if (!plugin.getHospitalManager().hasRequiredItems(player, dp.injury)) {
                    player.sendMessage("§4You are missing required medical items!");
                    player.closeInventory();
                    return;
                }
                boolean usesMorphine = plugin.getHospitalManager()
                        .getRequiredItems(dp.injury).contains("morphine");
                plugin.getHospitalManager().consumeRequiredItems(player, dp.injury);
                player.closeInventory();

                if (usesMorphine) {
                    plugin.getMedicalRecordManager().recordMorphineUse(patientUuid);
                    int uses = plugin.getMedicalRecordManager()
                            .getMorphineUsesLast24h(patientUuid);
                    if (uses >= 5) {
                        player.sendMessage("§5§l[Warning] §5This patient has received morphine §f" +
                                uses + " §5times in the last 24 hours. Addiction risk!");
                    }
                }

                if (dp.injury.requiresSurgery) {
                    player.sendMessage("§7Medication administered. Perform surgery:");
                    plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                            openSurgery(player, patient), 5L);
                } else {
                    plugin.getHospitalManager().revive(patient, player);
                    double bill = MedicalRecordManager.TREATMENT_BILLS
                            .getOrDefault(dp.injury, 200.0);
                    plugin.getMedicalRecordManager().chargePatient(patientUuid, dp.injury);
                    plugin.getMedicalRecordManager().addRecord(
                            patientUuid, player.getUniqueId(),
                            dp.injury, bill, null, usesMorphine, null);
                }
            }

            default -> {
                // All supply requests — everything goes through Chief approval
                if (action.startsWith("submit_request_")) {
                    String typeName = action.substring(15);
                    int reqId;

                    // Check if it's a standard injury type or a special type
                    try {
                        InjuryType injury = InjuryType.valueOf(typeName);
                        reqId = plugin.getHospitalManager()
                                .submitSupplyRequest(player.getUniqueId(), injury);
                    } catch (IllegalArgumentException e) {
                        // Special type: BANDAGE, IV_DRIP, BLOOD_TEST
                        reqId = plugin.getHospitalManager()
                                .submitSpecialRequest(player.getUniqueId(), typeName);
                    }

                    player.closeInventory();
                    if (reqId != -1) {
                        player.sendMessage("§b§l[Hospital] §fRequest submitted for §f" +
                                typeName.replace("_", " ").toLowerCase() +
                                "§f. Waiting for Chief of Medicine approval.");
                        for (Player p : plugin.getServer().getOnlinePlayers()) {
                            if (plugin.getHospitalManager().isDoctor(p.getUniqueId()) &&
                                    plugin.getHospitalManager().getRank(p.getUniqueId())
                                            .canApproveSupplies()) {
                                p.sendMessage("§b§l[Hospital] §f" + player.getName() +
                                        " §frequested §f" +
                                        typeName.replace("_", " ").toLowerCase() +
                                        "§f. Check the terminal.");
                            }
                        }
                    } else {
                        player.sendMessage("§4Failed to submit request.");
                    }
                }
            }
        }
    }

    // ---- Chat handler ----

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!awaitingChat.containsKey(player.getUniqueId())) return;
        event.setCancelled(true);
        String input = event.getMessage().trim();
        String session = awaitingChat.remove(player.getUniqueId());
        if (input.equalsIgnoreCase("cancel")) {
            player.sendMessage("§7Cancelled.");
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            double amount;
            try { amount = Double.parseDouble(input); }
            catch (NumberFormatException e) { player.sendMessage("§4Invalid amount."); return; }
            if (amount <= 0) { player.sendMessage("§4Must be positive."); return; }
            if (session.startsWith("set_salary_")) {
                UUID targetUuid = UUID.fromString(session.substring(11));
                plugin.getHospitalManager().setSalary(targetUuid, amount);
                String name = plugin.getServer().getOfflinePlayer(targetUuid).getName();
                player.sendMessage("§fSalary for §b" + name + " §fset to §b" +
                        plugin.getEconomyManager().format(amount) + " §f/day.");
                Player target = plugin.getServer().getPlayer(targetUuid);
                if (target != null) {
                    target.sendMessage("§b§l[Hospital] §fThe Chief has set your daily salary to §b" +
                            plugin.getEconomyManager().format(amount) + "§f.");
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

    private ItemStack namedItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private void setAction(ItemStack item, String action) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(ACTION_KEY,
                PersistentDataType.STRING, action);
        item.setItemMeta(meta);
    }

    private String getAction(ItemStack item) {
        if (!item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(ACTION_KEY, PersistentDataType.STRING);
    }

    private String formatMedName(String type) {
        return switch (type) {
            case "morphine"    -> "Morphine";
            case "gauze"       -> "Gauze Bandage";
            case "naloxone"    -> "Naloxone (Narcan)";
            case "ibuprofen"   -> "Ibuprofen";
            case "amoxicillin" -> "Amoxicillin";
            case "iv_fluids"   -> "IV Fluids";
            case "splint"      -> "Splint";
            case "tourniquet"  -> "Tourniquet";
            case "burn_cream"  -> "Burn Cream";
            default            -> type;
        };
    }

    private String formatTime(long seconds) {
        if (seconds <= 0) return "0s";
        if (seconds >= 60) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }

    private static class SurgerySession {
        final UUID patientUuid;
        final UUID doctorUuid;
        final InjuryType injury;
        final Set<Integer> toolSlots;
        final int totalTools;
        int toolsClicked = 0;
        int strikes = 0;

        SurgerySession(UUID patientUuid, UUID doctorUuid, InjuryType injury,
                       Set<Integer> toolSlots, int totalTools) {
            this.patientUuid = patientUuid;
            this.doctorUuid  = doctorUuid;
            this.injury      = injury;
            this.toolSlots   = toolSlots;
            this.totalTools  = totalTools;
        }
    }
}