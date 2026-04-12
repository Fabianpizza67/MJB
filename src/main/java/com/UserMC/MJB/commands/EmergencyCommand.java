package com.UserMC.MJB.commands;

import com.UserMC.MJB.MJB;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class EmergencyCommand implements CommandExecutor {

    private final MJB plugin;

    // ---- Keyword lists ----
    private static final String[] POLICE_KEYWORDS = {
            "shot", "shooting", "gunfire", "gun", "armed", "robbery", "robbed",
            "stolen", "steal", "fight", "attack", "attacked", "assault",
            "murder", "kill", "killed", "weapon", "hostage", "threat",
            "breaking in", "break in", "stabbed", "knife"
    };
    private static final String[] MEDICAL_KEYWORDS = {
            "hurt", "injured", "injury", "bleeding", "unconscious", "downed",
            "overdose", "sick", "collapsed", "collapse", "heart", "breathing",
            "ambulance", "medic", "doctor", "hospital", "broken", "burn",
            "burned", "poisoned"
    };
    private static final String[] BOTH_KEYWORDS = {
            "fire", "burning", "explosion", "explode", "crash", "accident"
    };

    public EmergencyCommand(MJB plugin) {
        this.plugin = plugin;
    }

    private String[] triageWithAI(String apiKey, String callerName,
                                  String world, int x, int z, String message) {
        String prompt = "You are an emergency dispatch AI for a city roleplay server. " +
                "A player called '" + callerName + "' sent this 911 message: \"" + message + "\". " +
                "Their location: world=" + world + " X=" + x + " Z=" + z + ". " +
                "Decide who to dispatch. Reply with ONLY a JSON object like this: " +
                "{\"dispatch\":[\"POLICE\",\"MEDICAL\"],\"reason\":\"brief reason\"}. " +
                "dispatch can contain POLICE, MEDICAL, both, or be empty if unclear. " +
                "Consider that someone could be both a crime victim AND need medical help. " +
                "Keep reason under 10 words.";

        try {
            java.net.URL url = new java.net.URL(
                    "https://generativelanguage.googleapis.com/v1beta/models/" +
                            "gemini-3.1-flash-lite-preview:generateContent?key=" + apiKey);
            java.net.HttpURLConnection conn =
                    (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(8000);

            com.google.gson.JsonObject body    = new com.google.gson.JsonObject();
            com.google.gson.JsonObject part    = new com.google.gson.JsonObject();
            part.addProperty("text", prompt);
            com.google.gson.JsonArray parts    = new com.google.gson.JsonArray();
            parts.add(part);
            com.google.gson.JsonObject content = new com.google.gson.JsonObject();
            content.addProperty("role", "user");
            content.add("parts", parts);
            com.google.gson.JsonArray contents = new com.google.gson.JsonArray();
            contents.add(content);
            body.add("contents", contents);

            com.google.gson.JsonObject genConfig = new com.google.gson.JsonObject();
            genConfig.addProperty("maxOutputTokens", 80);
            genConfig.addProperty("temperature", 0.2);
            body.add("generationConfig", genConfig);

            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            if (conn.getResponseCode() != 200) {
                plugin.getLogger().warning("[911 AI] API returned " +
                        conn.getResponseCode());
                return new String[]{"", "API error"};
            }

            StringBuilder response = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(
                            conn.getInputStream(),
                            java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
            }

            com.google.gson.JsonObject json =
                    com.google.gson.JsonParser.parseString(response.toString())
                            .getAsJsonObject();
            String text = json.getAsJsonArray("candidates")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts")
                    .get(0).getAsJsonObject()
                    .get("text").getAsString().trim();

            // Strip markdown code fences if Gemini wraps in ```json
            text = text.replaceAll("```json|```", "").trim();

            com.google.gson.JsonObject parsed =
                    com.google.gson.JsonParser.parseString(text).getAsJsonObject();
            com.google.gson.JsonArray dispatch = parsed.getAsJsonArray("dispatch");
            String reason = parsed.has("reason")
                    ? parsed.get("reason").getAsString() : "unknown";

            StringBuilder dispatchStr = new StringBuilder();
            for (com.google.gson.JsonElement el : dispatch) {
                if (dispatchStr.length() > 0) dispatchStr.append("+");
                dispatchStr.append(el.getAsString().toUpperCase());
            }

            return new String[]{dispatchStr.toString(), reason};

        } catch (Exception e) {
            plugin.getLogger().warning("[911 AI] Triage error: " + e.getMessage());
            return new String[]{"", "error"};
        }
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§4Usage: /" + label + " <message>");
            player.sendMessage("§7Example: §f/" + label + " shots fired near Main Street!");
            return true;
        }



        StringBuilder sb = new StringBuilder();
        for (String arg : args) sb.append(arg).append(" ");
        String msg = sb.toString().trim();
        String lower = msg.toLowerCase();

        // ---- Triage ----
        boolean needPolice  = false;
        boolean needMedical = false;

        for (String kw : POLICE_KEYWORDS)  if (lower.contains(kw)) { needPolice  = true; break; }
        for (String kw : MEDICAL_KEYWORDS) if (lower.contains(kw)) { needMedical = true; break; }
        for (String kw : BOTH_KEYWORDS)    if (lower.contains(kw)) { needPolice  = true; needMedical = true; break; }

        // Confirm to caller
        player.sendMessage("§c§l[911] §7Your call has been dispatched.");
        sendDispatcherResponse(player, msg, needPolice, needMedical);

        List<String> categories = new ArrayList<>();
        if (needPolice)  categories.add("§9POLICE§7");
        if (needMedical) categories.add("§aMEDICAL§7");
        if (!categories.isEmpty()) {
            player.sendMessage("§7Dispatched to: " + String.join(" + ", categories));
        }

        // ---- Build relay messages ----
        String baseInfo = "§7Caller: §f" + player.getName() +
                " §7near §f" + player.getWorld().getName() +
                " §f" + player.getLocation().getBlockX() +
                ", " + player.getLocation().getBlockZ();

        if (needPolice || needMedical) {
            if (needPolice) {
                String policeMsg = "§c§l[911][POLICE] §c" + player.getName() +
                        "§c: §f" + msg;
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    if (plugin.getPoliceManager().isOfficer(p.getUniqueId())) {
                        p.sendMessage(policeMsg);
                        p.sendMessage(baseInfo);
                    }
                }
            }
            if (needMedical) {
                String medMsg = "§a§l[911][MEDICAL] §a" + player.getName() +
                        "§a: §f" + msg;
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    if (plugin.getHospitalManager().isDoctor(p.getUniqueId())) {
                        p.sendMessage(medMsg);
                        p.sendMessage(baseInfo);
                    }
                }
            }
        } else {
            // No keywords matched — ask Gemini to triage
            String apiKey = plugin.getConfig().getString("ai.gemini_api_key", "");
            if (apiKey.isEmpty() || apiKey.equals("YOUR_GEMINI_API_KEY_HERE")) {
                // No API key — fall back to relaying to all officers
                String fallbackMsg = "§c§l[911] §c" + player.getName() + "§c: §f" + msg;
                int count = 0;
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    if (plugin.getPoliceManager().isOfficer(p.getUniqueId())) {
                        p.sendMessage(fallbackMsg);
                        p.sendMessage(baseInfo);
                        count++;
                    }
                }
                if (count == 0) {
                    player.sendMessage("§4§l[911] §4No officers are online. You're on your own!");
                }
                plugin.getLogger().info("[911] " + player.getName() +
                        " | Triage: UNKNOWN (no API key) | Message: " + msg);
            } else {
                // Tell the caller dispatch is working on it
                player.sendMessage("§7Dispatch is assessing your call...");

                final String finalMsg  = msg;
                final String finalInfo = baseInfo;

                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    String[] result = triageWithAI(apiKey, player.getName(),
                            player.getWorld().getName(),
                            player.getLocation().getBlockX(),
                            player.getLocation().getBlockZ(),
                            finalMsg);

                    boolean aiPolice  = result[0].contains("POLICE");
                    boolean aiMedical = result[0].contains("MEDICAL");
                    String reasoning  = result[1];

                    plugin.getLogger().info("[911] " + player.getName() +
                            " | AI Triage: " + result[0] +
                            " | Reasoning: " + reasoning +
                            " | Message: " + finalMsg);

                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (!aiPolice && !aiMedical) {
                            // AI couldn't determine — relay to all officers without tag
                            String fallbackMsg = "§c§l[911] §c" + player.getName() +
                                    "§c: §f" + finalMsg;
                            int count = 0;
                            for (Player p : plugin.getServer().getOnlinePlayers()) {
                                if (plugin.getPoliceManager().isOfficer(p.getUniqueId())) {
                                    p.sendMessage(fallbackMsg);
                                    p.sendMessage(finalInfo);
                                    count++;
                                }
                            }
                            if (count == 0) {
                                player.sendMessage("§4§l[911] §4No officers are online. " +
                                        "You're on your own!");
                            }
                            return;
                        }

                        // Relay with AI-determined tags
                        if (aiPolice) {
                            String policeMsg = "§c§l[911][POLICE] §c" + player.getName() +
                                    "§c: §f" + finalMsg;
                            for (Player p : plugin.getServer().getOnlinePlayers()) {
                                if (plugin.getPoliceManager().isOfficer(p.getUniqueId())) {
                                    p.sendMessage(policeMsg);
                                    p.sendMessage(finalInfo);
                                }
                            }
                        }
                        if (aiMedical) {
                            String medMsg = "§a§l[911][MEDICAL] §a" + player.getName() +
                                    "§a: §f" + finalMsg;
                            for (Player p : plugin.getServer().getOnlinePlayers()) {
                                if (plugin.getHospitalManager().isDoctor(p.getUniqueId())) {
                                    p.sendMessage(medMsg);
                                    p.sendMessage(finalInfo);
                                }
                            }
                        }
                        sendDispatcherResponse(player, finalMsg, aiPolice, aiMedical);

                        // Update caller
                        if (aiPolice || aiMedical) {
                            player.sendMessage("§7Dispatch confirmed — routing to " +
                                    (aiPolice && aiMedical ? "§9Police §7+ §aMedical"
                                            : aiPolice ? "§9Police" : "§aMedical") + "§7.");
                        }
                    });
                });
            }
        }



// Console log for keyword-matched calls (AI calls log themselves async above)
        if (needPolice || needMedical) {
            String triageResult = needPolice && needMedical ? "POLICE+MEDICAL"
                    : needPolice ? "POLICE" : "MEDICAL";
            plugin.getLogger().info("[911] " + player.getName() +
                    " | Keyword Triage: " + triageResult + " | Message: " + msg);
        }

        // Console log with triage result
        String triageResult = needPolice && needMedical ? "POLICE+MEDICAL"
                : needPolice ? "POLICE"
                : needMedical ? "MEDICAL"
                : "UNKNOWN (no keywords)";
        plugin.getLogger().info("[911] " + player.getName() + " | Triage: " +
                triageResult + " | Message: " + msg);

        return true;
    }

    private void sendDispatcherResponse(Player caller, String message,
                                        boolean police, boolean medical) {
        String apiKey = plugin.getConfig().getString("ai.gemini_api_key", "");
        if (apiKey.isEmpty() || apiKey.equals("YOUR_GEMINI_API_KEY_HERE")) return;

        String prompt = "You are a calm, professional emergency dispatcher for a city. " +
                "A citizen called '" + caller.getName() + "' sent this 911 message: \"" + message + "\". " +
                "Dispatched units: " + (police && medical ? "Police and Medical" :
                police ? "Police" : medical ? "Medical" : "All available units") + ". " +
                "Write a short, realistic dispatcher confirmation (1-2 sentences max). " +
                "Be professional and reassuring. Don't use emojis.";

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                java.net.URL url = new java.net.URL(
                        "https://generativelanguage.googleapis.com/v1beta/models/" +
                                "gemini-3.1-flash-lite-preview:generateContent?key=" + apiKey);
                java.net.HttpURLConnection conn =
                        (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(7000);

                com.google.gson.JsonObject body    = new com.google.gson.JsonObject();
                com.google.gson.JsonObject part    = new com.google.gson.JsonObject();
                part.addProperty("text", prompt);
                com.google.gson.JsonArray parts    = new com.google.gson.JsonArray();
                parts.add(part);
                com.google.gson.JsonObject content = new com.google.gson.JsonObject();
                content.addProperty("role", "user");
                content.add("parts", parts);
                com.google.gson.JsonArray contents = new com.google.gson.JsonArray();
                contents.add(content);
                body.add("contents", contents);
                com.google.gson.JsonObject genConfig = new com.google.gson.JsonObject();
                genConfig.addProperty("maxOutputTokens", 60);
                genConfig.addProperty("temperature", 0.4);
                body.add("generationConfig", genConfig);

                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString()
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }

                if (conn.getResponseCode() != 200) return;

                StringBuilder response = new StringBuilder();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream(),
                                java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                }

                com.google.gson.JsonObject json =
                        com.google.gson.JsonParser.parseString(response.toString())
                                .getAsJsonObject();
                String text = json.getAsJsonArray("candidates")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("content")
                        .getAsJsonArray("parts")
                        .get(0).getAsJsonObject()
                        .get("text").getAsString().trim();

                plugin.getServer().getScheduler().runTask(plugin, () ->
                        caller.sendMessage("§c§l[Dispatch] §f" + text));

            } catch (Exception e) {
                // Silent fail — dispatcher response is cosmetic, not critical
                plugin.getLogger().fine("[911 Dispatch] AI response failed: "
                        + e.getMessage());
            }
        });
    }
}

