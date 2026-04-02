package com.UserMC.MJB;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class SophieManager {

    private final MJB plugin;
    private static final String SOPHIE = "§b§lSophie §8(City Guide)§r";

    public SophieManager(MJB plugin) {
        this.plugin = plugin;
    }

    public void ask(Player player, String question) {
        String q = question.toLowerCase();
        String response = detectAndRespond(player, q);
        player.sendMessage("");
        player.sendMessage(SOPHIE + " §7»");
        for (String line : response.split("\n")) {
            player.sendMessage("§f" + line);
        }
        player.sendMessage("");
    }

    private String detectAndRespond(Player player, String q) {

        // Bank / money
        if (has(q, "bank", "deposit", "withdraw", "balance", "money",
                "cash", "atm", "debit", "card")) {
            return "To manage your money, head to a §bBank Teller NPC§f!\n" +
                    "Use §b/deposit <amount>§f to put cash in the bank,\n" +
                    "and §b/withdraw <amount>§f to take it out.\n" +
                    "Cash drops on death, so keep it safe in the bank! 💰\n" +
                    "Buy a §bDebit Card§f with §b/buycard§f ($25) to pay at terminals.";
        }

        // Phone
        if (has(q, "phone", "number", "call", "message", "text",
                "sms", "contact", "ring")) {
            String num = plugin.getPhoneManager().getPhoneNumber(player.getUniqueId());
            String numStr = num != null ? "§b" + num : "§7not assigned yet";
            return "Your phone number is: " + numStr + "§f.\n" +
                    "Right-click your §bphone item§f to open it.\n" +
                    "You can send messages, call players, and manage contacts.\n" +
                    "Lost your phone? Get a replacement with §b/buyphone§f ($100).";
        }

        // Hospital / medical
        if (has(q, "hospital", "doctor", "medical", "hurt", "downed", "blood",
                "morphine", "bandage", "surgery", "bleed", "health", "heal",
                "iv", "drip", "injured", "ambulance")) {
            return "If you get downed, you need a §bDoctor§f to treat you!\n" +
                    "Call for help with §b/911 <message>§f.\n" +
                    "Doctors can carry you to the hospital and treat your injury.\n" +
                    "Hospital ranks: Intern → Resident → Doctor → Surgeon → Chief.\n" +
                    "Blood type matters for treatment — get tested at the hospital!";
        }

        // Police / crime
        if (has(q, "police", "arrest", "cuff", "wanted", "crime", "officer",
                "charge", "illegal", "weapon", "gun", "handcuff", "badge")) {
            return "The police enforce the law in this city!\n" +
                    "If you're wanted, an officer may arrest you with §bhandcuffs§f.\n" +
                    "Call police with §b/911 <message>§f in an emergency.\n" +
                    "Illegal items (weapons, ammo) can be seized during a search.\n" +
                    "Always carry your §bID card§f — you are required to by law.\n" +
                    "Use §b/laws§f to see what's currently legal.";
        }

        // Laws
        if (has(q, "law", "legal", "illegal", "rule", "allowed", "banned", "forbidden")) {
            List<GovernmentManager.Law> laws =
                    plugin.getGovernmentManager().getActiveLaws();
            if (laws.isEmpty()) {
                return "No special laws are in effect right now!\n" +
                        "Standard rules apply. Stay out of trouble! 😉";
            }
            StringBuilder sb = new StringBuilder("Current active laws:\n");
            for (GovernmentManager.Law law : laws) {
                sb.append("§7• §f").append(law.title).append("\n");
            }
            sb.append("Use §b/laws§f anytime for the full list!");
            return sb.toString();
        }

        // ID card
        if (has(q, "id", "identification", "identity", "passport", "id card")) {
            boolean hasId = plugin.getIDCardManager().playerHasValidIDCard(player);
            return "Your ID card is an official §bCity Hall document§f.\n" +
                    "You " + (hasId ? "§acurrently have§f" : "§cdo NOT have§f") +
                    " a valid ID card.\n" +
                    "You are required by law to carry it at all times.\n" +
                    "Lost it? Visit the §bGovernment Office NPC§f for a replacement ($300).";
        }

        // Licenses
        if (has(q, "license", "licence", "driver", "pilot", "boat",
                "vehicle", "car", "drive", "fly", "driving")) {
            return "Licenses let you operate vehicles and run businesses legally!\n" +
                    "Visit the §bGovernment Office NPC§f to browse and buy licenses.\n" +
                    "Types include: Driver's, Pilot's, Boat License, and more.\n" +
                    "Licenses expire after §b30 days§f — renew before they run out!\n" +
                    "Check yours at the Government Office NPC.";
        }

        // Company / job / work
        if (has(q, "company", "job", "work", "employ", "boss", "hire",
                "salary", "business", "register", "employee")) {
            return "Want to work? Ask a company owner to hire you!\n" +
                    "You'll get a §b/acceptjob§f invite when they send an offer.\n" +
                    "Want your own business? Visit the §bGovernment Office NPC§f\n" +
                    "to register a company. Manage it from your §bcomputer terminal§f\n" +
                    "(right-click a green glazed terracotta block).\n" +
                    "Your salary is paid daily from the company bank!";
        }

        // Housing / property
        if (has(q, "apartment", "house", "home", "property", "plot",
                "live", "room", "flat", "estate", "housing")) {
            return "New to the city? Claim your §bfree starter apartment§f\n" +
                    "from the §bHousing NPC§f near spawn!\n" +
                    "Want to upgrade? Visit the §bReal Estate Office NPC§f\n" +
                    "or browse from your §bcomputer terminal§f.\n" +
                    "You need a Real Estate License to list your own property for sale.";
        }

        // Gang
        if (has(q, "gang", "crew", "faction")) {
            return "Gangs are social groups you can form with friends!\n" +
                    "Use §b/gang create <name>§f to start one,\n" +
                    "or §b/gang list§f to see all existing gangs.\n" +
                    "Gangs are purely social — no special powers, just community! 🤝";
        }

        // Radio
        if (has(q, "radio", "frequency", "channel", "transmit", "broadcast")) {
            return "Radios let you communicate over long distances (200 blocks)!\n" +
                    "Hold your radio and use §b/radio <message>§f to transmit.\n" +
                    "Channels: §9Police§f, §aMedical§f, §6Dispatch§f, §fPublic§f.\n" +
                    "Police and Medical radios are encrypted.\n" +
                    "Request one through the police station or hospital terminal!";
        }

        // Jail / court
        if (has(q, "jail", "prison", "sentence", "convicted", "court", "judge")) {
            return "If you break the law, the §bJudge§f can sentence you to jail time!\n" +
                    "Sentences run even while you're offline.\n" +
                    "When your sentence ends, you'll be released and notified.\n" +
                    "Officers will return any confiscated items on release.";
        }

        // Tutorial / help / new player
        if (has(q, "tutorial", "start", "help", "new", "beginner",
                "guide", "how", "what do i")) {
            return "Welcome to the city! 🏙️\n" +
                    "Use §b/tutorial§f to see your getting-started checklist.\n" +
                    "It covers the bank, apartment, phone, government, and more!\n" +
                    "You can always ask me anything with §b/ask <question>§f.";
        }

        // Government / politics
        if (has(q, "government", "party", "election", "vote", "mayor",
                "council", "politic", "parliament")) {
            UUID mayorUuid = plugin.getGovernmentManager().getMayorUuid();
            String mayorStr = mayorUuid != null
                    ? "§b" + plugin.getServer().getOfflinePlayer(mayorUuid).getName()
                    : "§7nobody yet";
            return "The city is governed by elected parties!\n" +
                    "Current mayor: " + mayorStr + "§f.\n" +
                    "Use §b/party§f to create or join a political party.\n" +
                    "Elections happen every 2 weeks — vote at the §bVoting Booth NPC§f.\n" +
                    "Use §b/government§f to see current status.";
        }

        // Supply orders
        if (has(q, "supply", "order", "stock", "delivery", "warehouse")) {
            return "Companies can order supplies through their §bcomputer terminal§f!\n" +
                    "Right-click a green glazed terracotta block to access it.\n" +
                    "You need the right licenses to order certain items.\n" +
                    "Orders take time to arrive — check §bMy Orders§f on the computer.";
        }

        // Fallback
        return "Hmm, I'm not sure about that one! 🤔\n" +
                "Try asking something more specific, or use §b/tutorial§f\n" +
                "for a full guide to the city. Other players might know too!";
    }

    private boolean has(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }
}