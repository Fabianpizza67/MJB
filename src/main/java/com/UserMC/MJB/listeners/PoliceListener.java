package com.UserMC.MJB.listeners;

import com.UserMC.MJB.MJB;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public class PoliceListener implements Listener {

    private final MJB plugin;

    public PoliceListener(MJB plugin) {
        this.plugin = plugin;
    }

    // ---- Right-click player with handcuffs to cuff/uncuff ----

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Player target)) return;

        Player officer = event.getPlayer();
        ItemStack held = officer.getInventory().getItemInMainHand();

        // ---- Handcuffs: cuff or uncuff ----
        if (plugin.getPoliceManager().isHandcuffs(held)) {
            event.setCancelled(true);

            if (!plugin.getPoliceManager().isOfficer(officer.getUniqueId())) {
                officer.sendMessage("§4You are not a police officer.");
                return;
            }

            if (plugin.getPoliceManager().isCuffed(target.getUniqueId())) {
                // Already cuffed — uncuff
                plugin.getPoliceManager().uncuff(target.getUniqueId());
                officer.sendMessage("§f§l[Police] §fYou released §b" + target.getName() + "§f.");
                target.sendMessage("§f§l[Police] §fYou have been released by §b" + officer.getName() + "§f.");
                target.setWalkSpeed(0.2f);
            } else {
                // Cuff them
                plugin.getPoliceManager().cuff(target.getUniqueId(), officer.getUniqueId());
                officer.sendMessage("§f§l[Police] §fYou arrested §b" + target.getName() + "§f.");
                target.sendMessage("§c§l[Police] §cYou have been arrested by §f" + officer.getName() + "§c!");
                target.sendMessage("§7Do not resist. You will be escorted to the station.");

                // Notify nearby players
                officer.getWorld().getNearbyPlayers(officer.getLocation(), 20).forEach(nearby -> {
                    if (!nearby.equals(officer) && !nearby.equals(target)) {
                        nearby.sendMessage("§8[Police] §7" + officer.getName() +
                                " has arrested §f" + target.getName() + "§7.");
                    }
                });
            }
            return;
        }

        // ---- Drug test ----
        if (plugin.getPoliceBudgetManager().isDrugTest(held)) {
            event.setCancelled(true);

            if (!plugin.getPoliceManager().isOfficer(officer.getUniqueId())) {
                officer.sendMessage("§4You are not a police officer.");
                return;
            }

            if (pendingDrugTests.containsKey(target.getUniqueId())) {
                officer.sendMessage("§4That player already has a pending drug test request.");
                return;
            }

            // Send GUI to target
            pendingDrugTests.put(target.getUniqueId(), officer.getUniqueId());

            org.bukkit.inventory.Inventory gui = plugin.getServer()
                    .createInventory(null, 27, DRUG_TEST_GUI);

            org.bukkit.inventory.ItemStack info = new org.bukkit.inventory.ItemStack(
                    org.bukkit.Material.AMETHYST_SHARD);
            org.bukkit.inventory.meta.ItemMeta infoMeta = info.getItemMeta();
            infoMeta.setDisplayName("§5§lDrug Test");
            infoMeta.setLore(java.util.List.of(
                    "§7Officer §b" + officer.getName() +
                            " §7wants to administer a drug test.",
                    "§7This will reveal any drugs used",
                    "§7in the last §f2.5 minutes§7.",
                    "",
                    "§aAccept §7or §cDecline§7?"
            ));
            info.setItemMeta(infoMeta);
            gui.setItem(4, info);

            org.bukkit.inventory.ItemStack accept =
                    new org.bukkit.inventory.ItemStack(org.bukkit.Material.LIME_WOOL);
            org.bukkit.inventory.meta.ItemMeta acceptMeta = accept.getItemMeta();
            acceptMeta.setDisplayName("§a§lAccept");
            acceptMeta.setLore(java.util.List.of("§7Consent to the drug test."));
            accept.setItemMeta(acceptMeta);
            gui.setItem(11, accept);

            org.bukkit.inventory.ItemStack decline =
                    new org.bukkit.inventory.ItemStack(org.bukkit.Material.RED_WOOL);
            org.bukkit.inventory.meta.ItemMeta declineMeta = decline.getItemMeta();
            declineMeta.setDisplayName("§c§lDecline");
            declineMeta.setLore(java.util.List.of(
                    "§7Refuse the drug test.",
                    "§7The officer will be notified."
            ));
            decline.setItemMeta(declineMeta);
            gui.setItem(15, decline);

            target.openInventory(gui);
            officer.sendMessage("§5§l[Drug Test] §fRequest sent to §b" +
                    target.getName() + "§f. Waiting for response...");

            // Auto-expire after 30 seconds if no response
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (pendingDrugTests.containsKey(target.getUniqueId())) {
                    pendingDrugTests.remove(target.getUniqueId());
                    officer.sendMessage("§5§l[Drug Test] §7" + target.getName() +
                            " §7did not respond. Request expired.");
                    if (target.isOnline() &&
                            DRUG_TEST_GUI.equals(target.getOpenInventory().getTitle())) {
                        target.closeInventory();
                    }
                }
            }, 20L * 30);
            return;
        }

        // ---- Badge: search cuffed player ----
// ---- Badge: search cuffed player ----
        if (plugin.getPoliceManager().isBadge(held)) {
            event.setCancelled(true);

            if (!plugin.getPoliceManager().isOfficer(officer.getUniqueId())) {
                officer.sendMessage("§4You are not a police officer.");
                return;
            }

            if (!plugin.getPoliceManager().isCuffed(target.getUniqueId())) {
                officer.sendMessage("§4" + target.getName() + " is not cuffed. Arrest them first.");
                return;
            }

            List<ItemStack> seized = plugin.getPoliceManager().searchAndSeize(target);

            if (seized.isEmpty()) {
                officer.sendMessage("§f§l[Police] §fSearch of §b" + target.getName() +
                        " §fcomplete. §aNo illegal items found.");
                target.sendMessage("§f§l[Police] §fYou were searched by §b" + officer.getName() +
                        "§f. No illegal items found.");
            } else {
                ItemStack evidence = plugin.getPoliceManager()
                        .createEvidenceBag(officer.getName(), seized);
                officer.getInventory().addItem(evidence);
                officer.sendMessage("§f§l[Police] §fSearch of §b" + target.getName() +
                        " §fcomplete. §c" + seized.size() + " illegal item(s) seized.");
                target.sendMessage("§c§l[Police] §c" + officer.getName() +
                        " seized §f" + seized.size() + " §cillegal item(s) from you!");
                seized.forEach(item -> officer.sendMessage("§7  - §f" + item.getAmount() + "x " +
                        formatMaterial(item.getType().name())));
                plugin.getCrimeManager().addOffence(
                        target.getUniqueId(),
                        "Possession of illegal items (" + seized.size() +
                                " item(s) seized by " + officer.getName() + ")",
                        officer.getUniqueId()
                );
            }

            // Auto-check ID card if the no_id_card law is active
            if (plugin.getGovernmentManager().isNoIDCardIllegal()) {
                boolean hasValidID = false;
                for (ItemStack invItem : target.getInventory().getContents()) {
                    if (!plugin.getIDCardManager().isIDCard(invItem)) continue;
                    UUID cardOwner = plugin.getIDCardManager().getCardOwner(invItem);
                    if (cardOwner != null && cardOwner.equals(target.getUniqueId())
                            && plugin.getIDCardManager().isCardValid(invItem)) {
                        hasValidID = true;
                        break;
                    }
                }
                if (!hasValidID) {
                    plugin.getCrimeManager().addOffence(
                            target.getUniqueId(),
                            "No valid ID card found during search",
                            officer.getUniqueId()
                    );
                    officer.sendMessage("§c§l[Police] §c" + target.getName() +
                            " §cdoes not carry a valid ID card! Offence added.");
                    target.sendMessage("§c§l[Police] §cYou were found without a valid ID card. " +
                            "An offence has been added to your record.");
                }
            }
        }
    }


    // ---- Block cuffed players from using items ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemUse(PlayerInteractEvent event) {
        if (!plugin.getPoliceManager().isCuffed(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage("§c§l[Police] §cYou are handcuffed!");
    }

    // ---- Block cuffed players from opening inventories ----

    @EventHandler
    public void onDrugTestResponse(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player target)) return;
        if (!event.getView().getTitle().equals(DRUG_TEST_GUI)) return;

        event.setCancelled(true);

        org.bukkit.inventory.ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == org.bukkit.Material.AIR) return;

        java.util.UUID officerUuid = pendingDrugTests.remove(target.getUniqueId());
        if (officerUuid == null) {
            target.closeInventory();
            return;
        }

        Player officer = plugin.getServer().getPlayer(officerUuid);
        target.closeInventory();

        if (clicked.getType() == org.bukkit.Material.RED_WOOL) {
            // Declined
            target.sendMessage("§5§l[Drug Test] §7You declined the drug test.");
            if (officer != null) {
                officer.sendMessage("§5§l[Drug Test] §b" + target.getName() +
                        " §cdeclined §fyour drug test.");

                // Refusing can be a crime if the law is active
                // (officer decides manually whether to charge)
            }
            return;
        }

        if (clicked.getType() == org.bukkit.Material.LIME_WOOL) {
            // Accepted — check drug use in last 150 seconds
            target.sendMessage("§5§l[Drug Test] §7Test administered...");

            String sql = "SELECT drug_type, COUNT(*) as uses FROM drug_usage " +
                    "WHERE player_uuid = ? " +
                    "AND used_at > NOW() - INTERVAL 150 SECOND " +
                    "GROUP BY drug_type";
            try (java.sql.PreparedStatement stmt = plugin.getDatabaseManager()
                    .getConnection().prepareStatement(sql)) {
                stmt.setString(1, target.getUniqueId().toString());
                java.sql.ResultSet rs = stmt.executeQuery();

                java.util.Map<String, Integer> results = new java.util.LinkedHashMap<>();
                while (rs.next()) {
                    results.put(rs.getString("drug_type"), rs.getInt("uses"));
                }

                if (results.isEmpty()) {
                    target.sendMessage("§a§l[Drug Test] §aTest came back clean.");
                    if (officer != null) {
                        officer.sendMessage("§5§l[Drug Test] §b" + target.getName() +
                                " §atested clean. §7No drugs detected in the last 2.5 minutes.");
                    }
                } else {
                    target.sendMessage("§c§l[Drug Test] §cTest came back positive.");
                    if (officer != null) {
                        officer.sendMessage("§5§l[Drug Test] §b" + target.getName() +
                                " §ctested positive§f:");
                        for (var entry : results.entrySet()) {
                            com.UserMC.MJB.DrugManager.DrugType type =
                                    com.UserMC.MJB.DrugManager.DrugType.fromId(entry.getKey());
                            String displayName = type != null
                                    ? type.displayName : entry.getKey();
                            officer.sendMessage("§7  - §f" + displayName +
                                    " §7(§f" + entry.getValue() + "x §7in last 2.5min)");
                        }
                        // Auto-add offence if drugs are illegal
                        for (String drugId : results.keySet()) {
                            com.UserMC.MJB.DrugManager.DrugType type =
                                    com.UserMC.MJB.DrugManager.DrugType.fromId(drugId);
                            if (type != null && !plugin.getDrugManager().isLegal(type)) {
                                plugin.getCrimeManager().addOffence(
                                        target.getUniqueId(),
                                        "Tested positive for " +
                                                (type.displayName) + " (drug test by " +
                                                officer.getName() + ")",
                                        officer.getUniqueId()
                                );
                            }
                        }
                        officer.sendMessage("§7Charges automatically added for illegal substances.");
                    }
                }
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("Drug test query error: " + e.getMessage());
                if (officer != null) {
                    officer.sendMessage("§4Drug test failed due to a server error.");
                }
            }

            // Consume one drug test from officer's inventory
            if (officer != null) {
                for (int i = 0; i < officer.getInventory().getSize(); i++) {
                    org.bukkit.inventory.ItemStack inv =
                            officer.getInventory().getItem(i);
                    if (plugin.getPoliceBudgetManager().isDrugTest(inv)) {
                        if (inv.getAmount() > 1) inv.setAmount(inv.getAmount() - 1);
                        else officer.getInventory().setItem(i, null);
                        break;
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!plugin.getPoliceManager().isCuffed(player.getUniqueId())) return;
        event.setCancelled(true);
        player.sendMessage("§c§l[Police] §cYou are handcuffed!");
    }

    // ---- Block cuffed players from dropping items ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!plugin.getPoliceManager().isCuffed(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
    }

    // ---- Block cuffed players from sprinting ----

    @EventHandler
    public void onToggleSprint(PlayerToggleSprintEvent event) {
        if (!plugin.getPoliceManager().isCuffed(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
    }

    // ---- Cuffed players can't attack ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!plugin.getPoliceManager().isCuffed(attacker.getUniqueId())) return;
        event.setCancelled(true);
        attacker.sendMessage("§c§l[Police] §cYou are handcuffed!");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCuffedDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.getPoliceManager().isCuffed(player.getUniqueId())) return;
        // Block suffocation damage — cuffed players get pushed into blocks by the escort
        if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.SUFFOCATION ||
                event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.CRAMMING) {
            event.setCancelled(true);
        }
    }

    // ---- Uncuff on disconnect (officer) — clean up victims ----

    @EventHandler
    public void onOfficerQuit(PlayerQuitEvent event) {
        UUID officerUuid = event.getPlayer().getUniqueId();
        // Don't instantly uncuff — the 10-min scheduler in PoliceManager handles it
        // Just notify victims that officer disconnected
        for (UUID cuffed : plugin.getPoliceManager().getCuffedPlayers()) {
            if (officerUuid.equals(plugin.getPoliceManager().getCuffingOfficer(cuffed))) {
                Player victim = plugin.getServer().getPlayer(cuffed);
                if (victim != null) {
                    victim.sendMessage("§7§l[Police] §7Your arresting officer disconnected.");
                    victim.sendMessage("§7You will be automatically released in §f10 minutes§7.");
                }
            }
        }
    }

    // Pending drug tests: target UUID → officer UUID
    private final java.util.Map<java.util.UUID, java.util.UUID> pendingDrugTests =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static final String DRUG_TEST_GUI = "§c§lDrug Test Request";

    // ---- Uncuff if victim disconnects ----

    @EventHandler
    public void onVictimQuit(PlayerQuitEvent event) {
        if (plugin.getPoliceManager().isCuffed(event.getPlayer().getUniqueId())) {
            plugin.getPoliceManager().uncuff(event.getPlayer().getUniqueId());
        }
    }

    // ---- Helper ----

    private String formatMaterial(String material) {
        String[] words = material.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words)
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
        return sb.toString().trim();
    }
}