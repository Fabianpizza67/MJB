package com.UserMC.MJB.listeners;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.UserMC.MJB.MJB;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class StarterStoreNPCListener implements Listener {

    private final MJB plugin;
    public static final String STARTER_STORE_NPC_TAG = "starter_store_office";

    public StarterStoreNPCListener(MJB plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onNPCRightClick(NPCRightClickEvent event) {
        NPC npc = event.getNPC();
        if (!npc.data().has(STARTER_STORE_NPC_TAG)) return;

        Player player = event.getClicker();

        if (hasClaimedStarterStore(player)) {
            player.sendMessage("§eYou have already claimed your starter store.");
            player.sendMessage("§7To upgrade, purchase a store from the Real Estate Office.");
            return;
        }

        String regionId = claimStarterStore(player);
        if (regionId == null) {
            player.sendMessage("§cSorry, no starter stores are available right now.");
            player.sendMessage("§7Ask an admin to add more with /mjbadmin addstarterstoreplot.");
            return;
        }

        player.sendMessage("§8§m-----------------------------");
        player.sendMessage("§6§lWelcome to your new store!");
        player.sendMessage("§8§m-----------------------------");
        player.sendMessage("§eYou have been assigned store: §f" + regionId);
        player.sendMessage("§7This is your starter store — set it up and start selling!");
        player.sendMessage("§7Register a terminal with §f/terminal register §7to take payments.");
        player.sendMessage("§c§lNote: §7You will lose this store once you buy a larger property.");
        player.sendMessage("§8§m-----------------------------");

        plugin.getTutorialManager().onClaimedStarterStore(player);
    }

    private boolean hasClaimedStarterStore(Player player) {
        String sql = "SELECT 1 FROM starter_stores WHERE claimed_by = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            stmt.setString(1, player.getUniqueId().toString());
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking starter store: " + e.getMessage());
            return false;
        }
    }

    private String claimStarterStore(Player player) {
        // Find an unclaimed store
        List<String[]> available = new ArrayList<>();
        String sql = "SELECT region_id, world FROM starter_stores " +
                "WHERE is_claimed = FALSE";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                available.add(new String[]{
                        rs.getString("region_id"),
                        rs.getString("world")
                });
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching starter stores: " + e.getMessage());
            return null;
        }

        if (available.isEmpty()) return null;

        // Pick random
        String[] chosen = available.get(
                (int) (Math.random() * available.size()));
        String regionId = chosen[0];
        String worldName = chosen[1];

        World world = plugin.getServer().getWorld(worldName);
        if (world == null) return null;

        RegionManager rm = WorldGuard.getInstance().getPlatform()
                .getRegionContainer().get(BukkitAdapter.adapt(world));
        if (rm == null) return null;

        ProtectedRegion region = rm.getRegion(regionId);
        if (region == null) return null;

        // Assign WorldGuard ownership
        region.getOwners().addPlayer(player.getUniqueId());

        // Mark as claimed
        String updateSql = "UPDATE starter_stores SET is_claimed = TRUE, " +
                "claimed_by = ? WHERE region_id = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(updateSql)) {
            stmt.setString(1, player.getUniqueId().toString());
            stmt.setString(2, regionId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error claiming starter store: " + e.getMessage());
            return null;
        }

        // Add to plots table
        String plotSql = "INSERT IGNORE INTO plots " +
                "(region_id, world, owner_uuid, plot_type) VALUES (?, ?, ?, 'starter_store')";
        try (PreparedStatement stmt = plugin.getDatabaseManager()
                .getConnection().prepareStatement(plotSql)) {
            stmt.setString(1, regionId);
            stmt.setString(2, worldName);
            stmt.setString(3, player.getUniqueId().toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error adding starter store to plots: " + e.getMessage());
        }

        return regionId;
    }
}