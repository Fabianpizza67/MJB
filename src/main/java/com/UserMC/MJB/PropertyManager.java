package com.UserMC.MJB;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PropertyManager {

    private final MJB plugin;

    public PropertyManager(MJB plugin) {
        this.plugin = plugin;
    }

    // ---- City listings (admin registered) ----

    /**
     * Admin registers a plot as available for purchase by the city.
     * Returns false if region doesn't exist or is already listed.
     */
    public boolean registerListing(String regionId, String world, String plotType,
                                   String district, double price) {
        // Verify region exists in WorldGuard
        World w = plugin.getServer().getWorld(world);
        if (w == null) return false;
        RegionManager rm = WorldGuard.getInstance().getPlatform()
                .getRegionContainer().get(BukkitAdapter.adapt(w));
        if (rm == null || rm.getRegion(regionId) == null) return false;

        String sql = "INSERT INTO property_listings (region_id, world, plot_type, district, price, is_available, listed_by) " +
                "VALUES (?, ?, ?, ?, ?, TRUE, NULL) " +
                "ON DUPLICATE KEY UPDATE plot_type=?, district=?, price=?, is_available=TRUE, listed_by=NULL";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, regionId);
            stmt.setString(2, world);
            stmt.setString(3, plotType);
            stmt.setString(4, district);
            stmt.setDouble(5, price);
            stmt.setString(6, plotType);
            stmt.setString(7, district);
            stmt.setDouble(8, price);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error registering listing: " + e.getMessage());
            return false;
        }
    }

    public boolean unregisterListing(String regionId, String world) {
        String sql = "DELETE FROM property_listings WHERE region_id = ? AND world = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, regionId);
            stmt.setString(2, world);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error unregistering listing: " + e.getMessage());
            return false;
        }
    }

    // ---- Player resale listings ----

    public static final double RESALE_LISTING_FEE = 50.0;

    /**
     * Player lists their own plot for resale. Requires Real Estate license.
     * Deducts flat listing fee upfront.
     */
    public ListingResult listForResale(Player seller, String regionId, String world, double askingPrice) {
        // Must own the plot
        if (!plugin.getPlotManager().isPlotOwner(seller.getUniqueId(), regionId)) {
            return ListingResult.NOT_OWNER;
        }

        // Must have Real Estate license
        if (!plugin.getLicenseManager().hasActiveLicense(seller.getUniqueId(), "real_estate")) {
            return ListingResult.NO_LICENSE;
        }

        // Check balance for listing fee
        double balance = plugin.getEconomyManager().getBankBalance(seller.getUniqueId());
        if (balance < RESALE_LISTING_FEE) return ListingResult.INSUFFICIENT_FUNDS;

        // Already listed?
        if (isListed(regionId, world)) return ListingResult.ALREADY_LISTED;

        // Deduct listing fee
        String deductSql = "UPDATE players SET bank_balance = bank_balance - ? WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(deductSql)) {
            stmt.setDouble(1, RESALE_LISTING_FEE);
            stmt.setString(2, seller.getUniqueId().toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error deducting listing fee: " + e.getMessage());
            return ListingResult.ERROR;
        }

        // Get plot type from plots table
        String plotType = getPlotType(regionId, world);
        String district = "unknown"; // TODO: could derive from nearest district

        String sql = "INSERT INTO property_listings (region_id, world, plot_type, district, price, is_available, listed_by) " +
                "VALUES (?, ?, ?, ?, ?, TRUE, ?)";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, regionId);
            stmt.setString(2, world);
            stmt.setString(3, plotType != null ? plotType : "property");
            stmt.setString(4, district);
            stmt.setDouble(5, askingPrice);
            stmt.setString(6, seller.getUniqueId().toString());
            stmt.executeUpdate();
            return ListingResult.SUCCESS;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error listing for resale: " + e.getMessage());
            return ListingResult.ERROR;
        }
    }

    public boolean cancelResaleListing(UUID playerUuid, String regionId, String world) {
        // Must be the seller
        String sql = "DELETE FROM property_listings WHERE region_id = ? AND world = ? AND listed_by = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, regionId);
            stmt.setString(2, world);
            stmt.setString(3, playerUuid.toString());
            int rows = stmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error cancelling listing: " + e.getMessage());
            return false;
        }
    }

    // ---- Purchase ----

    /**
     * Handles both city and resale purchases.
     * Deducts from buyer's bank, transfers WorldGuard ownership, updates DB.
     */
    public PurchaseResult purchase(Player buyer, String regionId, String world) {
        PropertyListing listing = getListing(regionId, world);
        if (listing == null) return PurchaseResult.NOT_LISTED;
        if (!listing.isAvailable) return PurchaseResult.NOT_AVAILABLE;

        // Can't buy your own listing
        if (listing.listedBy != null && listing.listedBy.equals(buyer.getUniqueId())) {
            return PurchaseResult.OWN_LISTING;
        }

        double balance = plugin.getEconomyManager().getBankBalance(buyer.getUniqueId());
        if (balance < listing.price * plugin.getMoneyModifier())
            return PurchaseResult.INSUFFICIENT_FUNDS;

        World w = plugin.getServer().getWorld(world);
        if (w == null) return PurchaseResult.ERROR;

        RegionManager rm = WorldGuard.getInstance().getPlatform()
                .getRegionContainer().get(BukkitAdapter.adapt(w));
        if (rm == null) return PurchaseResult.ERROR;

        ProtectedRegion region = rm.getRegion(regionId);
        if (region == null) return PurchaseResult.ERROR;

        // Deduct from buyer
        String deductSql = "UPDATE players SET bank_balance = bank_balance - ? WHERE uuid = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(deductSql)) {
            double adjustedPrice = listing.price * plugin.getMoneyModifier();
            stmt.setDouble(1, adjustedPrice);
            stmt.setString(2, buyer.getUniqueId().toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error deducting from buyer: " + e.getMessage());
            return PurchaseResult.ERROR;
        }

        // Pay seller (if player resale) or city treasury (if city listing)
        if (listing.listedBy != null) {
            // Player resale — pay seller
            String paySql = "UPDATE players SET bank_balance = bank_balance + ? WHERE uuid = ?";
            try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(paySql)) {
                stmt.setDouble(1, listing.price);
                stmt.setString(2, listing.listedBy.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error paying seller: " + e.getMessage());
            }
            // Notify seller
            Player seller = plugin.getServer().getPlayer(listing.listedBy);
            if (seller != null) {
                seller.sendMessage("§b§l[Property] §b" + buyer.getName() + " §fbought your listing §b" +
                        regionId + " §ffor §b" + plugin.getEconomyManager().format(listing.price) + "§f!");
            }

            // Remove old owner from WorldGuard
            region.getOwners().getUniqueIds().forEach(region.getOwners()::removePlayer);
            region.getMembers().getUniqueIds().forEach(region.getMembers()::removePlayer);

            // Update plots table
            String updatePlot = "UPDATE plots SET owner_uuid = ?, purchased_at = CURRENT_TIMESTAMP WHERE region_id = ? AND world = ?";
            try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(updatePlot)) {
                stmt.setString(1, buyer.getUniqueId().toString());
                stmt.setString(2, regionId);
                stmt.setString(3, world);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error updating plot owner: " + e.getMessage());
            }
        } else {
            // City listing — pay into treasury
            String treasurySql = "UPDATE city_treasury SET balance = balance + ?";
            try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(treasurySql)) {
                stmt.setDouble(1, listing.price);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error adding to treasury: " + e.getMessage());
            }

            // Insert new plot ownership
            String insertPlot = "INSERT INTO plots (region_id, world, owner_uuid, plot_type, purchased_at) " +
                    "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP) " +
                    "ON DUPLICATE KEY UPDATE owner_uuid = ?, plot_type = ?, purchased_at = CURRENT_TIMESTAMP";
            try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(insertPlot)) {
                stmt.setString(1, regionId);
                stmt.setString(2, world);
                stmt.setString(3, buyer.getUniqueId().toString());
                stmt.setString(4, listing.plotType);
                stmt.setString(5, buyer.getUniqueId().toString());
                stmt.setString(6, listing.plotType);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error inserting plot: " + e.getMessage());
                return PurchaseResult.ERROR;
            }
        }

        // Transfer WorldGuard ownership to buyer
        region.getOwners().getUniqueIds().forEach(region.getOwners()::removePlayer);
        region.getOwners().addPlayer(buyer.getUniqueId());

        // Mark listing as sold
        String soldSql = "UPDATE property_listings SET is_available = FALSE WHERE region_id = ? AND world = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(soldSql)) {
            stmt.setString(1, regionId);
            stmt.setString(2, world);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error marking listing sold: " + e.getMessage());
        }

        return PurchaseResult.SUCCESS;
    }

    // ---- Queries ----

    public List<PropertyListing> getAvailableListings() {
        List<PropertyListing> listings = new ArrayList<>();
        String sql = "SELECT * FROM property_listings WHERE is_available = TRUE ORDER BY plot_type, price";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) listings.add(listingFromResultSet(rs));
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching listings: " + e.getMessage());
        }
        return listings;
    }

    public List<PropertyListing> getAvailableListingsByType(String plotType) {
        List<PropertyListing> listings = new ArrayList<>();
        String sql = "SELECT * FROM property_listings WHERE is_available = TRUE AND plot_type = ? ORDER BY price";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, plotType);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) listings.add(listingFromResultSet(rs));
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching listings by type: " + e.getMessage());
        }
        return listings;
    }

    public List<PropertyListing> getPlayerListings(UUID playerUuid) {
        List<PropertyListing> listings = new ArrayList<>();
        String sql = "SELECT * FROM property_listings WHERE listed_by = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) listings.add(listingFromResultSet(rs));
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching player listings: " + e.getMessage());
        }
        return listings;
    }

    public PropertyListing getListing(String regionId, String world) {
        String sql = "SELECT * FROM property_listings WHERE region_id = ? AND world = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, regionId);
            stmt.setString(2, world);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return listingFromResultSet(rs);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching listing: " + e.getMessage());
        }
        return null;
    }

    public boolean isListed(String regionId, String world) {
        String sql = "SELECT 1 FROM property_listings WHERE region_id = ? AND world = ? AND is_available = TRUE";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, regionId);
            stmt.setString(2, world);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }

    // ---- Treasury ----

    public double getTreasuryBalance() {
        String sql = "SELECT balance FROM city_treasury LIMIT 1";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getDouble("balance");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error fetching treasury: " + e.getMessage());
        }
        return 0;
    }

    // ---- Helpers ----

    private String getPlotType(String regionId, String world) {
        String sql = "SELECT plot_type FROM plots WHERE region_id = ? AND world = ?";
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            stmt.setString(1, regionId);
            stmt.setString(2, world);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("plot_type");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting plot type: " + e.getMessage());
        }
        return null;
    }

    private PropertyListing listingFromResultSet(ResultSet rs) throws SQLException {
        String listedByStr = rs.getString("listed_by");
        return new PropertyListing(
                rs.getString("region_id"),
                rs.getString("world"),
                rs.getString("plot_type"),
                rs.getString("district"),
                rs.getDouble("price"),
                rs.getBoolean("is_available"),
                listedByStr != null ? UUID.fromString(listedByStr) : null,
                rs.getTimestamp("listed_at")
        );
    }

    // ---- Result enums ----

    public enum PurchaseResult {
        SUCCESS, NOT_LISTED, NOT_AVAILABLE, INSUFFICIENT_FUNDS, OWN_LISTING, ERROR
    }

    public enum ListingResult {
        SUCCESS, NOT_OWNER, NO_LICENSE, INSUFFICIENT_FUNDS, ALREADY_LISTED, ERROR
    }

    // ---- Data class ----

    public static class PropertyListing {
        public final String regionId;
        public final String world;
        public final String plotType;
        public final String district;
        public final double price;
        public final boolean isAvailable;
        public final UUID listedBy; // null = city listing
        public final Timestamp listedAt;

        public PropertyListing(String regionId, String world, String plotType, String district,
                               double price, boolean isAvailable, UUID listedBy, Timestamp listedAt) {
            this.regionId = regionId;
            this.world = world;
            this.plotType = plotType;
            this.district = district;
            this.price = price;
            this.isAvailable = isAvailable;
            this.listedBy = listedBy;
            this.listedAt = listedAt;
        }

        public boolean isCityListing() {
            return listedBy == null;
        }
    }
}