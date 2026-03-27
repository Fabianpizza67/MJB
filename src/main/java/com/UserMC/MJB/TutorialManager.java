package com.UserMC.MJB;

import org.bukkit.entity.Player;

import java.sql.*;
import java.util.UUID;

public class TutorialManager {

    private final MJB plugin;

    // Guide name — friendly and approachable
    private static final String GUIDE = "§b§lSophie §8(City Guide)§r";
    private static final String PREFIX = GUIDE + " §7» §f";

    public TutorialManager(MJB plugin) {
        this.plugin = plugin;
    }

    // ---- Progress tracking ----

    public void initPlayer(UUID uuid) {
        String sql = "INSERT IGNORE INTO tutorial_progress (player_uuid) VALUES (?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error initialising tutorial: " + e.getMessage());
        }
    }

    public TutorialProgress getProgress(UUID uuid) {
        String sql = "SELECT * FROM tutorial_progress WHERE player_uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new TutorialProgress(
                        rs.getBoolean("visited_bank"),
                        rs.getBoolean("claimed_apartment"),
                        rs.getBoolean("checked_phone"),
                        rs.getBoolean("visited_gov"),
                        rs.getBoolean("visited_realestate"),
                        rs.getBoolean("made_choice"),
                        rs.getBoolean("completed")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting tutorial progress: " + e.getMessage());
        }
        return new TutorialProgress(false, false, false, false, false, false, false);
    }

    public void markStep(UUID uuid, String column) {
        String sql = "UPDATE tutorial_progress SET " + column + " = TRUE WHERE player_uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error marking tutorial step: " + e.getMessage());
        }
        checkCompletion(uuid);
    }

    private void checkCompletion(UUID uuid) {
        TutorialProgress p = getProgress(uuid);
        if (p.completed) return;
        if (p.visitedBank && p.claimedApartment && p.checkedPhone
                && p.visitedGov && p.visitedRealestate && p.madeChoice) {
            String sql = "UPDATE tutorial_progress SET completed = TRUE WHERE player_uuid = ?";
            try (PreparedStatement stmt = plugin.getDatabaseManager()
                    .getConnection().prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error completing tutorial: " + e.getMessage());
            }
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) sendCompletion(player);
        }
    }

    // ---- Message sending ----

    public void sendWelcome(Player player) {
        player.sendMessage("");
        player.sendMessage(GUIDE + " §aHey there, " + player.getName() + "! Welcome to §bUserMC§a!");
        player.sendMessage(PREFIX + "My name is Sophie and I'll help you get settled in the city.");
        player.sendMessage(PREFIX + "You can always check your progress with §f/tutorial§7.");
        player.sendMessage(PREFIX + "Let's start — you have §f$100 §fin your pocket.");
        player.sendMessage(PREFIX + "Head to the §fbank §7to deposit it safely — cash drops on death!");
        player.sendMessage("");
    }

    public void sendOverview(Player player) {
        TutorialProgress p = getProgress(player.getUniqueId());
        player.sendMessage("");
        player.sendMessage("§b§l--- Your City Guide --- §7(Sophie)");
        player.sendMessage(step(p.visitedBank,      "Visit the bank and check your balance"));
        player.sendMessage(step(p.claimedApartment, "Claim your starter apartment from the Housing NPC"));
        player.sendMessage(step(p.checkedPhone,      "Open your phone and check your number"));
        player.sendMessage(step(p.visitedRealestate, "Visit the Real Estate office"));
        player.sendMessage(step(p.visitedGov,        "Visit the Government Office"));
        player.sendMessage(step(p.madeChoice,        "Choose your path: start a company or find a job"));
        player.sendMessage("");
        player.sendMessage("§7§o  Tip: visit a furniture store to make your apartment feel like home!");
        player.sendMessage("§7§o  Tip: use §f/laws §7§oto see what's legal in the city.");
        player.sendMessage("§7§o  Tip: police can arrest you — stay out of trouble!");
        player.sendMessage("");

        if (p.completed) {
            player.sendMessage("§a§lAll steps complete! Enjoy the city, " + player.getName() + "!");
        }
    }

    private String step(boolean done, String text) {
        return (done ? "§a✔ §7" : "§c✘ §f") + text;
    }

    // ---- Step-specific hints (called when player does relevant action) ----

    public void onVisitedBank(Player player) {
        TutorialProgress p = getProgress(player.getUniqueId());
        if (p.visitedBank) return;
        markStep(player.getUniqueId(), "visited_bank");
        player.sendMessage("");
        player.sendMessage(PREFIX + "Great, you found the bank! You can §f/deposit §7and §f/withdraw §7cash here.");
        player.sendMessage(PREFIX + "You can also buy a §fdebit card §7here with §f/buycard §7— useful for paying at shops.");
        player.sendMessage(PREFIX + "Next up: find the §fHousing NPC §7to claim your free starter apartment!");
        player.sendMessage("");
    }

    public void onClaimedApartment(Player player) {
        TutorialProgress p = getProgress(player.getUniqueId());
        if (p.claimedApartment) return;
        markStep(player.getUniqueId(), "claimed_apartment");
        player.sendMessage("");
        player.sendMessage(PREFIX + "You have a place to call home — nice!");
        player.sendMessage(PREFIX + "Your apartment is yours to build in freely.");
        player.sendMessage(PREFIX + "§7§oWant furniture? Visit a player-run furniture store in the city.");
        player.sendMessage(PREFIX + "§7§oYou'll lose this apartment if you buy a bigger place later — that's normal!");
        player.sendMessage(PREFIX + "Don't forget to check your §fphone §7— right-click it to open!");
        player.sendMessage("");
    }

    public void onCheckedPhone(Player player) {
        TutorialProgress p = getProgress(player.getUniqueId());
        if (p.checkedPhone) return;
        markStep(player.getUniqueId(), "checked_phone");
        player.sendMessage("");
        player.sendMessage(PREFIX + "Your phone is how you stay connected with other players!");
        player.sendMessage(PREFIX + "You can message, call, and add contacts — all from your phone.");
        player.sendMessage(PREFIX + "In an emergency, use §f/911 §7to reach the police.");
        player.sendMessage(PREFIX + "Head to the §fReal Estate Office §7to see what properties are for sale in the city.");
        player.sendMessage("");
    }

    public void onVisitedRealestate(Player player) {
        TutorialProgress p = getProgress(player.getUniqueId());
        if (p.visitedRealestate) return;
        markStep(player.getUniqueId(), "visited_realestate");
        player.sendMessage("");
        player.sendMessage(PREFIX + "Properties can be apartments, houses or stores — all player-owned!");
        player.sendMessage(PREFIX + "To list your own property you'll need a §fReal Estate License§7.");
        player.sendMessage(PREFIX + "Now head to the §fGovernment Office §7— it's the heart of the city.");
        player.sendMessage("");
    }

    public void onVisitedGov(Player player) {
        TutorialProgress p = getProgress(player.getUniqueId());
        if (p.visitedGov) return;
        markStep(player.getUniqueId(), "visited_gov");
// Replace the choice lines in onVisitedGov:
        player.sendMessage("§e§l  Click one to choose your path:");

        net.kyori.adventure.text.Component companyBtn = net.kyori.adventure.text.Component
                .text("  » ")
                .color(net.kyori.adventure.text.format.NamedTextColor.GRAY)
                .append(net.kyori.adventure.text.Component.text("Start my own company")
                        .color(net.kyori.adventure.text.format.NamedTextColor.GREEN)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/tutorial company"))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                net.kyori.adventure.text.Component.text("Register a business at the Government Office"))));

        net.kyori.adventure.text.Component jobBtn = net.kyori.adventure.text.Component
                .text("  » ")
                .color(net.kyori.adventure.text.format.NamedTextColor.GRAY)
                .append(net.kyori.adventure.text.Component.text("Find a job first")
                        .color(net.kyori.adventure.text.format.NamedTextColor.AQUA)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/tutorial job"))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                net.kyori.adventure.text.Component.text("Get hired by an existing company"))));

        net.kyori.adventure.text.Component skipBtn = net.kyori.adventure.text.Component
                .text("  » ")
                .color(net.kyori.adventure.text.format.NamedTextColor.GRAY)
                .append(net.kyori.adventure.text.Component.text("I'll figure it out myself")
                        .color(net.kyori.adventure.text.format.NamedTextColor.GRAY)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/tutorial skip"))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                net.kyori.adventure.text.Component.text("Skip this step"))));

        player.sendMessage(companyBtn);
        player.sendMessage(jobBtn);
        player.sendMessage(skipBtn);
    }

    public void onMadeChoice(Player player, String choice) {
        TutorialProgress p = getProgress(player.getUniqueId());
        if (p.madeChoice) return;
        markStep(player.getUniqueId(), "made_choice");

        player.sendMessage("");
        switch (choice) {
            case "company" -> {
                player.sendMessage(PREFIX + "Ambitious! Here's how to get started:");
                player.sendMessage(PREFIX + "§f1. §7Go to the §fGovernment Office NPC §7and register your company.");
                player.sendMessage(PREFIX + "§f2. §7Place a §fGreen Glazed Terracotta §7block — that's your computer.");
                player.sendMessage(PREFIX + "§f3. §7Right-click it to manage employees, bank, and orders.");
                player.sendMessage(PREFIX + "§f4. §7Buy the licenses you need to operate legally.");
            }
            case "job" -> {
                player.sendMessage(PREFIX + "Smart move to start! Here's how to find work:");
                player.sendMessage(PREFIX + "§f1. §7Talk to other players — ask around in chat.");
                player.sendMessage(PREFIX + "§f2. §7A company owner can invite you — you'll get §f/acceptjob§7.");
                player.sendMessage(PREFIX + "§f3. §7Your salary is paid daily from the company bank.");
                player.sendMessage(PREFIX + "§f4. §7You can always start your own company later!");
            }
            case "skip" -> {
                player.sendMessage(PREFIX + "No worries — you can always use §f/tutorial §7to come back to this.");
                player.sendMessage(PREFIX + "Good luck out there!");
            }
        }
        player.sendMessage("");
    }

    private void sendCompletion(Player player) {
        player.sendMessage("");
        player.sendMessage("§b§m----------------------------------------");
        player.sendMessage("§b§l        Welcome to UserMC!");
        player.sendMessage("§b§m----------------------------------------");
        player.sendMessage(PREFIX + "You've completed the introduction — the city is yours!");
        player.sendMessage(PREFIX + "Remember: §f/tutorial §7anytime if you need a reminder.");
        player.sendMessage(PREFIX + "Stay safe out there, " + player.getName() + ". Good luck!");
        player.sendMessage("§b§m----------------------------------------");
        player.sendMessage("");
    }

    private String clickable(String command, String display, String hoverText) {
        return display;
    }

    // ---- Data class ----

    public static class TutorialProgress {
        public final boolean visitedBank;
        public final boolean claimedApartment;
        public final boolean checkedPhone;
        public final boolean visitedGov;
        public final boolean visitedRealestate;
        public final boolean madeChoice;
        public final boolean completed;

        public TutorialProgress(boolean visitedBank, boolean claimedApartment,
                                boolean checkedPhone, boolean visitedGov,
                                boolean visitedRealestate, boolean madeChoice,
                                boolean completed) {
            this.visitedBank = visitedBank;
            this.claimedApartment = claimedApartment;
            this.checkedPhone = checkedPhone;
            this.visitedGov = visitedGov;
            this.visitedRealestate = visitedRealestate;
            this.madeChoice = madeChoice;
            this.completed = completed;
        }
    }
}