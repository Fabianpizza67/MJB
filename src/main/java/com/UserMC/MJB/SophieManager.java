package com.UserMC.MJB;

import com.google.gson.*;
import org.bukkit.entity.Player;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class SophieManager {

    private final MJB plugin;
    private static final String SOPHIE = "§b§lSophie §8(City Guide)§r";
    private static final String MODEL  = "gemini-3.1-flash-lite-preview";
    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/" +
                    MODEL + ":generateContent?key=";

    public SophieManager(MJB plugin) {
        this.plugin = plugin;
    }

    public void ask(Player player, String question) {
        String apiKey = plugin.getConfig().getString("ai.gemini_api_key", "");
        if (apiKey.isEmpty() || apiKey.equals("YOUR_GEMINI_API_KEY_HERE")) {
            player.sendMessage(SOPHIE + " §7» §cSophie is not configured yet. " +
                    "Ask an admin to set the API key in config.yml.");
            return;
        }

        // Build the system prompt with live server data
        String systemPrompt = buildSystemPrompt(player);

        // Run async so the server doesn't freeze during the HTTP call
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String response = callGemini(apiKey, systemPrompt, question);
            // Send result back on the main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.sendMessage("");
                player.sendMessage(SOPHIE + " §7»");
                // Split response into lines for clean chat display
                for (String line : response.split("\n")) {
                    if (!line.isBlank()) {
                        player.sendMessage("§f" + line.trim());
                    }
                }
                player.sendMessage("");
            });
        });
    }

    private String buildSystemPrompt(Player player) {
        // Gather live server data to include in the prompt
        StringBuilder sb = new StringBuilder();
        sb.append("You are Sophie, a friendly and helpful city guide NPC on a Minecraft ");
        sb.append("city roleplay server called UserMC. ");
        sb.append("You speak in a warm, approachable tone. Keep answers short (max 5 lines). ");
        sb.append("Never break character. If you genuinely don't know, say so politely.\n\n");

        sb.append("=== LIVE SERVER INFO ===\n");

        // Player's own info
        sb.append("Player asking: ").append(player.getName()).append("\n");
        String phone = plugin.getPhoneManager().getPhoneNumber(player.getUniqueId());
        sb.append("Their phone number: ").append(phone != null ? phone : "none").append("\n");
        boolean hasID = plugin.getIDCardManager().playerHasValidIDCard(player);
        sb.append("Has valid ID card: ").append(hasID).append("\n");

        // Active laws
        List<GovernmentManager.Law> laws = plugin.getGovernmentManager().getActiveLaws();
        if (laws.isEmpty()) {
            sb.append("Active laws: none currently\n");
        } else {
            sb.append("Active laws:\n");
            for (GovernmentManager.Law law : laws) {
                sb.append("  - ").append(law.title).append("\n");
            }
        }

        // Mayor
        java.util.UUID mayorUuid = plugin.getGovernmentManager().getMayorUuid();
        String mayorName = mayorUuid != null
                ? plugin.getServer().getOfflinePlayer(mayorUuid).getName()
                : "none";
        sb.append("Current mayor: ").append(mayorName).append("\n");

        // Guns legal
        sb.append("Guns currently legal: ")
                .append(plugin.getGovernmentManager().areGunsLegal()).append("\n");

        // Tax rate
        sb.append("Tax rate: ").append(plugin.getGovernmentManager().getTaxRate())
                .append("%\n");

        sb.append("\n=== SERVER SYSTEMS ===\n");
        sb.append("- Bank: /deposit, /withdraw at bank teller NPC. /buycard $25 for debit card.\n");
        sb.append("- Phone: right-click phone item. /buyphone $100 for replacement.\n");
        sb.append("- ID card: required by law. Free on join, $300 replacement at Gov NPC.\n");
        sb.append("- Hospital: doctors treat downed players. Call /911 for help.\n");
        sb.append("- Police: can arrest with handcuffs, search with badge. /911 for emergencies.\n");
        sb.append("- Housing: claim free starter apartment at Housing NPC near spawn.\n");
        sb.append("- Jobs: get hired with /acceptjob, or register a company at Gov NPC.\n");
        sb.append("- Licenses: buy at Government Office NPC. Expire after 30 days.\n");
        sb.append("- Radio: hold radio item and use /radio <message> to transmit.\n");
        sb.append("- Jail: judge can sentence players. Timer runs even offline.\n");
        sb.append("- Gangs: /gang create, /gang list. Social only, no game effects.\n");
        sb.append("- Supply orders: order stock from computer terminal (green terracotta).\n");
        sb.append("- Tutorial: /tutorial to see getting started checklist.\n");

        return sb.toString();
    }

    private String callGemini(String apiKey, String systemPrompt, String question) {
        try {
            URL url = new URL(API_URL + apiKey);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);

            // Build JSON body
            JsonObject body = new JsonObject();

            // System instruction
            JsonObject systemInstruction = new JsonObject();
            JsonObject systemPart = new JsonObject();
            systemPart.addProperty("text", systemPrompt);
            JsonArray systemParts = new JsonArray();
            systemParts.add(systemPart);
            systemInstruction.add("parts", systemParts);
            body.add("systemInstruction", systemInstruction);

            // User message
            JsonObject userPart = new JsonObject();
            userPart.addProperty("text", question);
            JsonArray userParts = new JsonArray();
            userParts.add(userPart);
            JsonObject content = new JsonObject();
            content.addProperty("role", "user");
            content.add("parts", userParts);
            JsonArray contents = new JsonArray();
            contents.add(content);
            body.add("contents", contents);

            // Generation config — keep responses short
            JsonObject genConfig = new JsonObject();
            genConfig.addProperty("maxOutputTokens", 200);
            genConfig.addProperty("temperature", 0.7);
            body.add("generationConfig", genConfig);

            // Send request
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();

            // Handle rate limit / quota exceeded
            if (status == 429) {
                plugin.getLogger().warning("[Sophie] Gemini API rate limit hit (429).");
                return "I'm a little overwhelmed right now — too many questions! " +
                        "Try again in a minute, or use /tutorial for the basics.";
            }

            // Handle other errors
            if (status != 200) {
                plugin.getLogger().warning("[Sophie] Gemini API returned status " + status);
                return handleFallback(question);
            }

            // Read response
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            // Parse response
            JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
            JsonArray candidates = json.getAsJsonArray("candidates");
            if (candidates == null || candidates.isEmpty()) {
                return handleFallback(question);
            }
            JsonObject candidate = candidates.get(0).getAsJsonObject();
            JsonObject contentObj = candidate.getAsJsonObject("content");
            JsonArray parts = contentObj.getAsJsonArray("parts");
            if (parts == null || parts.isEmpty()) {
                return handleFallback(question);
            }
            return parts.get(0).getAsJsonObject().get("text").getAsString().trim();

        } catch (java.net.SocketTimeoutException e) {
            plugin.getLogger().warning("[Sophie] Gemini API timed out.");
            return "Sorry, I'm taking too long to respond right now! " +
                    "Try /tutorial or ask another player for help.";
        } catch (Exception e) {
            plugin.getLogger().warning("[Sophie] Gemini API error: " + e.getMessage());
            return handleFallback(question);
        }
    }

    // Fallback for when the API is unavailable — basic keyword responses
    private String handleFallback(String question) {
        String q = question.toLowerCase();
        if (has(q, "bank", "deposit", "withdraw", "money", "cash"))
            return "Head to a Bank Teller NPC to manage your money! " +
                    "Use /deposit and /withdraw. Buy a debit card with /buycard.";
        if (has(q, "phone", "call", "message"))
            return "Right-click your phone item to open it! " +
                    "Lost it? Use /buyphone for a $100 replacement.";
        if (has(q, "hospital", "doctor", "hurt", "downed"))
            return "Call /911 if you're in trouble! " +
                    "Doctors can treat you at the hospital.";
        if (has(q, "police", "arrest", "crime", "wanted"))
            return "The police enforce the law. Use /911 in emergencies. " +
                    "Always carry your ID card!";
        if (has(q, "law", "legal", "rule"))
            return "Use /laws to see all currently active laws in the city!";
        return "Hmm, I can't reach my notes right now! " +
                "Try /tutorial or ask another player for help.";
    }

    private boolean has(String text, String... keywords) {
        for (String kw : keywords) if (text.contains(kw)) return true;
        return false;
    }
}