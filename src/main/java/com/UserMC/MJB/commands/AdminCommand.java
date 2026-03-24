package com.UserMC.MJB.commands;

import com.UserMC.MJB.CompanyManager;
import com.UserMC.MJB.CrimeManager;
import com.UserMC.MJB.MJB;
import com.UserMC.MJB.listeners.*;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Material;

import java.util.List;

public class AdminCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!player.hasPermission("mjb.admin")) {
            player.sendMessage("§4You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "setbankteller" -> {
                if (args.length != 2) {
                    player.sendMessage("§4Usage: /mjbadmin setbankteller <npc_id>");
                    return true;
                }
                NPC npc = getNPC(player, args[1]);
                if (npc == null) return true;
                npc.data().setPersistent(BankNPCListener.BANK_TELLER_TAG, true);
                player.sendMessage("§fNPC §b" + npc.getName() + " §f(ID: " + npc.getId() + ") is now a bank teller!");
            }

            case "removebankteller" -> {
                if (args.length != 2) {
                    player.sendMessage("§4Usage: /mjbadmin removebankteller <npc_id>");
                    return true;
                }
                NPC npc = getNPC(player, args[1]);
                if (npc == null) return true;
                npc.data().remove(BankNPCListener.BANK_TELLER_TAG);
                player.sendMessage("§fNPC §b" + npc.getName() + " §f(ID: " + npc.getId() + ") is no longer a bank teller.");
            }

            case "listbanktellers" -> {
                player.sendMessage("§b§l--- Bank Teller NPCs ---");
                boolean found = false;
                for (NPC npc : CitizensAPI.getNPCRegistry()) {
                    if (npc.data().has(BankNPCListener.BANK_TELLER_TAG)) {
                        player.sendMessage("§f- " + npc.getName() + " §7(ID: " + npc.getId() + ")");
                        found = true;
                    }
                }
                if (!found) player.sendMessage("§7No bank teller NPCs found.");
            }

            case "setnpc" -> {
                if (args.length != 3) {
                    player.sendMessage("§4Usage: /mjbadmin setnpc <npc_id> <type>");
                    player.sendMessage("§7Types: §fbankteller§7, §fhousing");
                    return true;
                }
                NPC npc = getNPC(player, args[1]);
                if (npc == null) return true;
                switch (args[2].toLowerCase()) {
                    case "bankteller" -> {
                        npc.data().setPersistent(BankNPCListener.BANK_TELLER_TAG, true);
                        player.sendMessage("§fNPC §b" + npc.getName() + " §fis now a bank teller!");
                    }
                    case "housing" -> {
                        npc.data().setPersistent(HousingNPCListener.HOUSING_NPC_TAG, true);
                        player.sendMessage("§fNPC §b" + npc.getName() + " §fis now a housing office NPC!");
                    }
                    case "government" -> {
                        npc.data().setPersistent(GovernmentNPCListener.GOV_NPC_TAG, true);
                        player.sendMessage("§fNPC §b" + npc.getName() + " §fis now a government office NPC!");
                    }
                    case "realestate" -> {
                        npc.data().setPersistent(RealEstateNPCListener.REALESTATE_NPC_TAG, true);
                        player.sendMessage("§fNPC §b" + npc.getName() + " §fis now a real estate NPC!");
                    }
                    case "blackmarket" -> {
                        npc.data().setPersistent(BlackMarketListener.BLACK_MARKET_TAG, true);
                        player.sendMessage("§fNPC §b" + npc.getName() + " §fis now the black market dealer!");
                    }
                    case "policestation" -> {
                        npc.data().setPersistent(PoliceStationListener.STATION_NPC_TAG, true);
                        player.sendMessage("§fNPC §b" + npc.getName() + " §fis now a police station terminal!");
                    }
                    default -> player.sendMessage("§4Unknown type. Use: bankteller, housing, government, realestate, blackmarket, policestation");
                }
            }

            case "assignplot" -> {
                if (args.length < 3) {
                    player.sendMessage("§4Usage: /mjbadmin assignplot <player> <regionId> <type>");
                    player.sendMessage("§7Types: §fapartment§7, §fhouse§7, §fstore");
                    return true;
                }
                Player target = getTarget(player, args[1]);
                if (target == null) return true;
                String regionId = args[2].toLowerCase();
                String plotType = args.length >= 4 ? args[3].toLowerCase() : "apartment";
                if (!plotType.equals("apartment") && !plotType.equals("house") && !plotType.equals("store")) {
                    player.sendMessage("§4Invalid plot type. Use: apartment, house, store");
                    return true;
                }
                boolean success = MJB.getInstance().getPlotManager()
                        .assignPlot(target, player.getWorld(), regionId, plotType);
                if (success) {
                    player.sendMessage("§fAssigned §b" + plotType + " §b" + regionId + " §fto §b" + target.getName() + "§f.");
                    target.sendMessage("§fYou have been assigned a §b" + plotType + "§f: §b" + regionId + "§f!");
                } else {
                    player.sendMessage("§4Failed — region doesn't exist or already has an owner.");
                }
            }

            case "removeplot" -> {
                if (args.length != 3) {
                    player.sendMessage("§4Usage: /mjbadmin removeplot <player> <regionId>");
                    return true;
                }
                Player target = getTarget(player, args[1]);
                if (target == null) return true;
                String regionId = args[2].toLowerCase();
                boolean success = MJB.getInstance().getPlotManager()
                        .removePlot(target, player.getWorld(), regionId);
                if (success) {
                    player.sendMessage("§fRemoved §b" + target.getName() + " §ffrom region §b" + regionId + "§f.");
                    target.sendMessage("§4Your plot §b" + regionId + " §4has been reclaimed.");
                } else {
                    player.sendMessage("§4Failed — region doesn't exist.");
                }
            }

            case "addmember" -> {
                if (args.length != 3) {
                    player.sendMessage("§4Usage: /mjbadmin addmember <player> <regionId>");
                    return true;
                }
                Player target = getTarget(player, args[1]);
                if (target == null) return true;
                String regionId = args[2].toLowerCase();
                boolean success = MJB.getInstance().getPlotManager()
                        .addMember(target.getUniqueId(), player.getWorld(), regionId);
                if (success) {
                    player.sendMessage("§fAdded §b" + target.getName() + " §fas member of §b" + regionId + "§f.");
                    target.sendMessage("§fYou have been added as a member of plot: §b" + regionId + "§f!");
                } else {
                    player.sendMessage("§4Failed — region doesn't exist.");
                }
            }

            case "addstarterapt" -> {
                if (args.length != 2) {
                    player.sendMessage("§4Usage: /mjbadmin addstarterapt <regionId>");
                    return true;
                }
                String regionId = args[1].toLowerCase();
                boolean success = MJB.getInstance().getPlotManager()
                        .registerStarterApartment(regionId, player.getWorld());
                if (success) {
                    player.sendMessage("§fRegion §b" + regionId + " §fadded to starter apartment pool!");
                } else {
                    player.sendMessage("§4Failed — region doesn't exist in this world.");
                }
            }

            case "removestarterapt" -> {
                if (args.length != 2) {
                    player.sendMessage("§4Usage: /mjbadmin removestarterapt <regionId>");
                    return true;
                }
                boolean success = MJB.getInstance().getPlotManager()
                        .unregisterStarterApartment(args[1].toLowerCase());
                if (success) {
                    player.sendMessage("§fRegion §b" + args[1] + " §fremoved from starter apartment pool.");
                } else {
                    player.sendMessage("§4Failed to remove region.");
                }
            }

            case "liststarterapts" -> {
                player.sendMessage("§b§l--- Available Starter Apartments ---");
                List<String> apts = MJB.getInstance().getPlotManager().getAvailableStarterApartments();
                if (apts.isEmpty()) {
                    player.sendMessage("§7No available starter apartments.");
                } else {
                    apts.forEach(apt -> player.sendMessage("§f- §b" + apt));
                }
            }

            case "unclaimpstarter", "unclaimstarter" -> {
                if (args.length != 2) {
                    player.sendMessage("§4Usage: /mjbadmin unclaimstarter <regionId>");
                    return true;
                }
                String regionId = args[1].toLowerCase();
                boolean success = MJB.getInstance().getPlotManager()
                        .unclaimStarterApartment(regionId);
                if (success) {
                    player.sendMessage("§fStarter apartment §b" + regionId + " §fhas been unclaimed and is now available again.");
                } else {
                    player.sendMessage("§4Failed — region not found or not a starter apartment.");
                }
            }
            case "addcomputer" -> {
                org.bukkit.block.Block target = player.getTargetBlockExact(5);
                if (target == null || target.getType() != Material.GREEN_GLAZED_TERRACOTTA) {
                    player.sendMessage("§4You must be looking at a Green Glazed Terracotta block.");
                    return true;
                }
                boolean success = MJB.getInstance().getSupplyOrderManager()
                        .registerComputer(target.getLocation(), player.getUniqueId());
                if (success) {
                    player.sendMessage("§fComputer registered!");
                } else {
                    player.sendMessage("§4Failed to register computer.");
                }
            }

            case "setpickupnpc" -> {
                if (args.length != 3) {
                    player.sendMessage("§4Usage: /mjbadmin setpickupnpc <npc_id> <district>");
                    return true;
                }
                int id;
                try {
                    id = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§4Invalid NPC ID.");
                    return true;
                }
                boolean success = MJB.getInstance().getSupplyOrderManager()
                        .registerPickupNPC(id, args[2].toLowerCase());
                if (success) {
                    player.sendMessage("§fNPC §b" + id + " §fset as pickup NPC for district §b" + args[2] + "§f.");
                } else {
                    player.sendMessage("§4Failed to set pickup NPC.");
                }
            }

            case "addsupplyitem" -> {
                if (args.length < 5) {
                    player.sendMessage("§4Usage: /mjbadmin addsupplyitem <material> <license> <price> <delivery_seconds>");
                    return true;
                }
                double price;
                int delivery;
                try {
                    price = Double.parseDouble(args[3]);
                    delivery = Integer.parseInt(args[4]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§4Invalid price or delivery time.");
                    return true;
                }
                boolean success = MJB.getInstance().getSupplyOrderManager()
                        .registerSupplyItem(args[1].toUpperCase(), args[2].toLowerCase(), price, delivery);
                if (success) {
                    player.sendMessage("§fSupply item §b" + args[1] + " §fregistered!");
                } else {
                    player.sendMessage("§4Failed to register supply item.");
                }
            }
            case "assigncompanyplot" -> {
                if (args.length != 3) {
                    player.sendMessage("§4Usage: /mjbadmin assigncompanyplot <company_name> <regionId>");
                    return true;
                }
                CompanyManager.CompanyInfo info = MJB.getInstance().getCompanyManager().getCompanyByName(args[1]);
                if (info == null) {
                    player.sendMessage("§4Company not found.");
                    return true;
                }
                boolean ok = MJB.getInstance().getCompanyManager().assignPlotToCompany(info.id, args[2].toLowerCase(), player.getWorld().getName());
                player.sendMessage(ok ? "§fPlot §b" + args[2] + " §fassigned to §b" + info.name + "§f." : "§4Failed.");
            }
            case "removecompanyplot" -> {
                if (args.length != 2) {
                    player.sendMessage("§4Usage: /mjbadmin removecompanyplot <regionId>");
                    return true;
                }
                MJB.getInstance().getCompanyManager().removePlotFromCompany(args[1].toLowerCase(), player.getWorld().getName());
                player.sendMessage("§fCompany plot §b" + args[1] + " §fremoved.");
            }
            case "listproperty" -> {
                // /mjbadmin listproperty <regionId> <plotType> <district> <price>
                if (args.length < 5) {
                    player.sendMessage("§4Usage: /mjbadmin listproperty <region> <type> <district> <price>");
                    player.sendMessage("§7Types: apartment, house, store");
                    return true;
                }
                double price;
                try { price = Double.parseDouble(args[4]); }
                catch (NumberFormatException e) { player.sendMessage("§4Invalid price."); return true; }

                boolean ok = MJB.getInstance().getPropertyManager()
                        .registerListing(args[1].toLowerCase(), player.getWorld().getName(),
                                args[2].toLowerCase(), args[3].toLowerCase(), price);
                player.sendMessage(ok
                        ? "§fProperty §b" + args[1] + " §flisted for §b" + MJB.getInstance().getEconomyManager().format(price) + "§f."
                        : "§4Failed — region may not exist in WorldGuard.");
            }

            case "unlistproperty" -> {
                // /mjbadmin unlistproperty <regionId>
                if (args.length < 2) { player.sendMessage("§4Usage: /mjbadmin unlistproperty <region>"); return true; }
                MJB.getInstance().getPropertyManager()
                        .unregisterListing(args[1].toLowerCase(), player.getWorld().getName());
                player.sendMessage("§fListing §b" + args[1] + " §fremoved.");
            }

            case "treasury" -> {
                double bal = MJB.getInstance().getPropertyManager().getTreasuryBalance();
                player.sendMessage("§b§lCity Treasury: §f" + MJB.getInstance().getEconomyManager().format(bal));
            }

            case "issuelicense" -> {
                // /mjbadmin issuelicense <player> <license_type>
                if (args.length != 3) { player.sendMessage("§4Usage: /mjbadmin issuelicense <player> <license_type>"); return true; }
                Player target = getTarget(player, args[1]);
                if (target == null) return true;
                // Admin bypass — issue without cost check
                String sql = "INSERT INTO licenses (player_uuid, license_type, expires_at) " +
                        "VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 30 DAY)) " +
                        "ON DUPLICATE KEY UPDATE expires_at = DATE_ADD(NOW(), INTERVAL 30 DAY), is_revoked = FALSE, revoked_by = NULL";
                try (java.sql.PreparedStatement stmt2 = MJB.getInstance().getDatabaseManager().getConnection().prepareStatement(sql)) {
                    stmt2.setString(1, target.getUniqueId().toString());
                    stmt2.setString(2, args[2].toLowerCase());
                    stmt2.executeUpdate();
                    player.sendMessage("§fIssued §b" + args[2] + " §flicense to §b" + target.getName() + "§f.");
                    target.sendMessage("§b§l[License] §fAn admin has issued you the §b" + args[2] + " §flicense.");
                } catch (java.sql.SQLException e) {
                    player.sendMessage("§4Failed — check that the license type exists.");
                }
            }

            case "revokelicense" -> {
                // /mjbadmin revokelicense <player> <license_type>
                if (args.length != 3) { player.sendMessage("§4Usage: /mjbadmin revokelicense <player> <license_type>"); return true; }
                Player target = getTarget(player, args[1]);
                if (target == null) return true;
                boolean ok = MJB.getInstance().getLicenseManager().revokeLicense(
                        target.getUniqueId(), args[2].toLowerCase(), player.getUniqueId());
                if (ok) {
                    player.sendMessage("§fRevoked §b" + args[2] + " §flicense from §b" + target.getName() + "§f.");
                    target.sendMessage("§4§l[License] §4Your §f" + args[2] + " §4license has been revoked by an admin.");
                } else {
                    player.sendMessage("§4Failed to revoke — player may not have that license.");
                }
            }

            case "addlicensetype" -> {
                // /mjbadmin addlicensetype <type_name> <display_name> <cost> <renewal_cost> <description...>
                if (args.length < 6) {
                    player.sendMessage("§4Usage: /mjbadmin addlicensetype <type_name> <display_name> <cost> <renewal_cost> <description>");
                    return true;
                }
                double cost, renewal;
                try { cost = Double.parseDouble(args[3]); renewal = Double.parseDouble(args[4]); }
                catch (NumberFormatException e) { player.sendMessage("§4Invalid cost or renewal cost."); return true; }
                // Join remaining args as description
                StringBuilder desc = new StringBuilder();
                for (int i = 5; i < args.length; i++) desc.append(args[i]).append(" ");
                boolean ok = MJB.getInstance().getLicenseManager().registerLicenseType(
                        args[1].toLowerCase(), args[2], cost, renewal, desc.toString().trim());
                player.sendMessage(ok ? "§fLicense type §b" + args[1] + " §fregistered." : "§4Failed.");
            }

            case "addcraftrule" -> {
                // /mjbadmin addcraftrule <license_type> <MATERIAL>
                if (args.length != 3) {
                    player.sendMessage("§4Usage: /mjbadmin addcraftrule <license_type> <MATERIAL>");
                    return true;
                }
                org.bukkit.Material mat;
                try { mat = org.bukkit.Material.valueOf(args[2].toUpperCase()); }
                catch (IllegalArgumentException e) { player.sendMessage("§4Unknown material: §f" + args[2]); return true; }
                boolean ok = MJB.getInstance().getCraftingLicenseManager().addRule(args[1].toLowerCase(), mat);
                player.sendMessage(ok ? "§fCraft rule added: §b" + mat.name() + " §frequires §b" + args[1] + "§f." : "§4Failed.");
            }

            case "removecraftrule" -> {
                // /mjbadmin removecraftrule <MATERIAL>
                if (args.length != 2) { player.sendMessage("§4Usage: /mjbadmin removecraftrule <MATERIAL>"); return true; }
                org.bukkit.Material mat;
                try { mat = org.bukkit.Material.valueOf(args[1].toUpperCase()); }
                catch (IllegalArgumentException e) { player.sendMessage("§4Unknown material: §f" + args[1]); return true; }
                boolean ok = MJB.getInstance().getCraftingLicenseManager().removeRule(mat);
                player.sendMessage(ok ? "§fCraft rule for §b" + mat.name() + " §fremoved." : "§4Failed.");
            }

            case "listcraftrules" -> {
                // /mjbadmin listcraftrules [license_type]
                java.util.List<com.UserMC.MJB.CraftingLicenseManager.CraftRule> rules = args.length >= 2
                        ? MJB.getInstance().getCraftingLicenseManager().getRulesForLicense(args[1].toLowerCase())
                        : MJB.getInstance().getCraftingLicenseManager().getAllRules();
                if (rules.isEmpty()) { player.sendMessage("§7No craft rules found."); return true; }
                player.sendMessage("§b§l--- Craft Rules ---");
                String currentLicense = null;
                for (var rule : rules) {
                    if (!rule.licenseType.equals(currentLicense)) {
                        currentLicense = rule.licenseType;
                        player.sendMessage("§b" + currentLicense + "§7:");
                    }
                    player.sendMessage("§7  - §f" + rule.resultMaterial);
                }
            }

            case "addblackmarket" -> {
                // /mjbadmin addblackmarket — stand at the location
                boolean ok = MJB.getInstance().getWeaponManager()
                        .addBlackMarketLocation(player.getLocation());
                player.sendMessage(ok
                        ? "§fBlack market location added at §b" + player.getLocation().getBlockX() +
                        ", " + player.getLocation().getBlockY() +
                        ", " + player.getLocation().getBlockZ() + "§f."
                        : "§4Failed to add location.");
            }

            case "removeblackmarket" -> {
                if (args.length != 2) { player.sendMessage("§4Usage: /mjbadmin removeblackmarket <id>"); return true; }
                int id;
                try { id = Integer.parseInt(args[1]); }
                catch (NumberFormatException e) { player.sendMessage("§4Invalid ID."); return true; }
                boolean ok = MJB.getInstance().getWeaponManager().removeBlackMarketLocation(id);
                player.sendMessage(ok ? "§fLocation §b" + id + " §fremoved." : "§4Failed.");
            }

            case "listblackmarket" -> {
                var locs = MJB.getInstance().getWeaponManager().listBlackMarketLocationsRaw();
                if (locs.isEmpty()) { player.sendMessage("§7No black market locations registered."); return true; }
                player.sendMessage("§4§l--- Black Market Locations ---");
                for (var loc : locs) {
                    player.sendMessage("§7ID §b" + loc.id + "§7: §f" + loc.worldName +
                            " " + loc.x + ", " + loc.y + ", " + loc.z);
                }
            }

            case "moveblackmarket" -> {
                MJB.getInstance().getBlackMarketListener().teleportNow();
                player.sendMessage("§fBlack market NPC teleported to a random location.");
            }

            case "giveweapon" -> {
                if (args.length != 3) {
                    player.sendMessage("§4Usage: /mjbadmin giveweapon <player> <pistol|rifle|shotgun|knife>");
                    return true;
                }
                Player target = getTarget(player, args[1]);
                if (target == null) return true;
                com.UserMC.MJB.WeaponManager.WeaponType wType =
                        com.UserMC.MJB.WeaponManager.WeaponType.fromId(args[2].toLowerCase());
                if (wType == null) {
                    player.sendMessage("§4Unknown weapon type. Use: pistol, rifle, shotgun, knife");
                    return true;
                }
                target.getInventory().addItem(MJB.getInstance().getWeaponManager().createWeapon(wType));
                player.sendMessage("§fGave §b" + target.getName() + " §fa §b" + wType.displayName + "§f.");
                target.sendMessage("§b§l[Police] §fYou have been issued a §b" + wType.displayName + "§f.");
            }

            case "giveammo" -> {
                if (args.length != 3) {
                    player.sendMessage("§4Usage: /mjbadmin giveammo <player> <pistol|rifle|shotgun>");
                    return true;
                }
                Player target = getTarget(player, args[1]);
                if (target == null) return true;
                com.UserMC.MJB.WeaponManager.WeaponType wType =
                        com.UserMC.MJB.WeaponManager.WeaponType.fromId(args[2].toLowerCase());
                if (wType == null || wType == com.UserMC.MJB.WeaponManager.WeaponType.KNIFE) {
                    player.sendMessage("§4Unknown ammo type. Use: pistol, rifle, shotgun");
                    return true;
                }
                org.bukkit.inventory.ItemStack ammo =
                        MJB.getInstance().getWeaponManager().createAmmo(wType);
                if (ammo != null) target.getInventory().addItem(ammo);
                player.sendMessage("§fGave §b" + target.getName() + " §f" + wType.displayName + " ammo.");
                target.sendMessage("§b§l[Police] §fYou have been issued §b" + wType.displayName + " ammo§f.");
            }

            case "setpolice" -> {
                if (args.length != 2) {
                    player.sendMessage("§4Usage: /mjbadmin setpolice <player>");
                    return true;
                }
                Player target = getTarget(player, args[1]);
                if (target == null) return true;
                if (MJB.getInstance().getPoliceManager().isOfficer(target.getUniqueId())) {
                    player.sendMessage("§4" + target.getName() + " is already an officer.");
                    return true;
                }
                boolean ok = MJB.getInstance().getPoliceManager()
                        .addOfficer(target.getUniqueId(), player.getUniqueId());
                if (ok) {
                    player.sendMessage("§f" + target.getName() + " §fis now a police officer.");
                    target.sendMessage("§b§l[Police] §fYou have been appointed as a police officer by §b"
                            + player.getName() + "§f.");
                    target.sendMessage("§7Use §f/mjbadmin giveweapon §7to get your equipment.");
                } else {
                    player.sendMessage("§4Failed.");
                }
            }

            case "removepolice" -> {
                if (args.length != 2) {
                    player.sendMessage("§4Usage: /mjbadmin removepolice <player>");
                    return true;
                }
                Player target = getTarget(player, args[1]);
                if (target == null) return true;
                boolean ok = MJB.getInstance().getPoliceManager()
                        .removeOfficer(target.getUniqueId());
                player.sendMessage(ok
                        ? "§f" + target.getName() + " §fhas been removed from the police force."
                        : "§4Failed — player may not be an officer.");
                if (ok && target.isOnline()) {
                    target.sendMessage("§4§l[Police] §4You have been removed from the police force.");
                }
            }

            case "listpolice" -> {
                var officers = MJB.getInstance().getPoliceManager().getAllOfficers();
                if (officers.isEmpty()) {
                    player.sendMessage("§7No officers on the force yet.");
                    return true;
                }
                player.sendMessage("§b§l--- Police Officers ---");
                for (var o : officers) {
                    String name = MJB.getInstance().getServer().getOfflinePlayer(o.uuid).getName();
                    player.sendMessage("§f- §b" + (name != null ? name : o.uuid.toString()));
                }
            }

            case "givehandcuffs" -> {
                if (args.length != 2) {
                    player.sendMessage("§4Usage: /mjbadmin givehandcuffs <player>");
                    return true;
                }
                Player target = getTarget(player, args[1]);
                if (target == null) return true;
                target.getInventory().addItem(MJB.getInstance().getPoliceManager().createHandcuffs());
                player.sendMessage("§fGave handcuffs to §b" + target.getName() + "§f.");
                target.sendMessage("§b§l[Police] §fYou have been issued §fHandcuffs§f.");
            }

            case "givebadge" -> {
                if (args.length != 2) {
                    player.sendMessage("§4Usage: /mjbadmin givebadge <player>");
                    return true;
                }
                Player target = getTarget(player, args[1]);
                if (target == null) return true;
                target.getInventory().addItem(
                        MJB.getInstance().getPoliceManager().createBadge(target.getName()));
                player.sendMessage("§fGave badge to §b" + target.getName() + "§f.");
                target.sendMessage("§b§l[Police] §fYou have been issued your §6Police Badge§f.");
            }

            case "uncuff" -> {
                if (args.length != 2) {
                    player.sendMessage("§4Usage: /mjbadmin uncuff <player>");
                    return true;
                }
                Player target = getTarget(player, args[1]);
                if (target == null) return true;
                if (!MJB.getInstance().getPoliceManager().isCuffed(target.getUniqueId())) {
                    player.sendMessage("§4" + target.getName() + " is not cuffed.");
                    return true;
                }
                MJB.getInstance().getPoliceManager().uncuff(target.getUniqueId());
                player.sendMessage("§f" + target.getName() + " §fhas been uncuffed.");
                target.sendMessage("§7An admin has released your handcuffs.");
            }

            case "setpolicerank" -> {
                if (args.length != 3) {
                    player.sendMessage("§4Usage: /mjbadmin setpolicerank <player> <officer|detective|sergeant>");
                    return true;
                }
                Player target = getTarget(player, args[1]);
                if (target == null) return true;
                if (!MJB.getInstance().getPoliceManager().isOfficer(target.getUniqueId())) {
                    player.sendMessage("§4" + target.getName() + " is not on the force. Use /mjbadmin setpolice first.");
                    return true;
                }
                CrimeManager.PoliceRank rank = CrimeManager.PoliceRank.fromString(args[2]);
                if (rank == null) {
                    player.sendMessage("§4Unknown rank. Use: officer, detective, sergeant");
                    return true;
                }
                MJB.getInstance().getCrimeManager().setRank(target.getUniqueId(), rank);
                player.sendMessage("§f" + target.getName() + " §fis now a §b" + rank.displayName + "§f.");
                target.sendMessage("§b§l[Police] §fAn admin has set your rank to §b" + rank.displayName + "§f.");
            }

            case "setpolicestation" -> {
                if (args.length != 2) {
                    player.sendMessage("§4Usage: /mjbadmin setpolicestation <npc_id>");
                    return true;
                }
                NPC npc = getNPC(player, args[1]);
                if (npc == null) return true;
                npc.data().setPersistent(
                        com.UserMC.MJB.listeners.PoliceStationListener.STATION_NPC_TAG, true);
                player.sendMessage("§fNPC §b" + npc.getName() + " §fis now a police station terminal!");
            }

            case "setvotingbooth" -> {
                // /mjbadmin setvotingbooth <npc_id>
                if (args.length != 2) { player.sendMessage("§4Usage: /mjbadmin setvotingbooth <npc_id>"); return true; }
                NPC npc = getNPC(player, args[1]);
                if (npc == null) return true;
                npc.data().setPersistent(
                        com.UserMC.MJB.listeners.ElectionListener.VOTING_BOOTH_TAG, true);
                player.sendMessage("§fNPC §b" + npc.getName() + " §fis now a voting booth!");
            }

            case "setcouncilregion" -> {
                // /mjbadmin setcouncilregion — stand in the region
                String regionId = MJB.getInstance().getPlotManager().getRegionAtPlayer(player);
                if (regionId == null) { player.sendMessage("§4You are not in any WorldGuard region."); return true; }
                boolean ok = MJB.getInstance().getGovernmentManager()
                        .addCouncilRegion(regionId, player.getWorld().getName());
                player.sendMessage(ok ? "§fRegion §b" + regionId + " §fset as council chamber." : "§4Failed.");
            }

            case "startelection" -> {
                // /mjbadmin startelection — force start an election
                int id = MJB.getInstance().getGovernmentManager().startElection();
                player.sendMessage(id != -1 ? "§fElection started (ID: " + id + ")." : "§4Election already active.");
            }

            case "closeelection" -> {
                MJB.getInstance().getGovernmentManager().closeElection();
                player.sendMessage("§fElection closed and results announced.");
            }

            case "opensession" -> {
                int id = MJB.getInstance().getGovernmentManager().openSession();
                player.sendMessage(id != -1 ? "§fCouncil session opened." : "§4Session already active.");
            }

            case "closesession" -> {
                int sessionId = MJB.getInstance().getGovernmentManager().getActiveSessionId();
                MJB.getInstance().getGovernmentManager().evaluateProposals(sessionId);
                MJB.getInstance().getGovernmentManager().closeSession();
                player.sendMessage("§fCouncil session closed and proposals evaluated.");
            }

            default -> sendHelp(player);
        }

        return true;
    }

    private NPC getNPC(Player player, String idStr) {
        int id;
        try {
            id = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            player.sendMessage("§4Invalid NPC ID.");
            return null;
        }
        NPC npc = CitizensAPI.getNPCRegistry().getById(id);
        if (npc == null) player.sendMessage("§4No NPC found with ID " + id + ".");
        return npc;
    }

    private Player getTarget(Player player, String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) player.sendMessage("§4Player not found or not online.");
        return target;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§b§l--- MJB Admin Commands ---");
        player.sendMessage("§f/mjbadmin setnpc <id> <type> §7- Set NPC type (bankteller, housing, government)");
        player.sendMessage("§f/mjbadmin setbankteller <id> §7- Mark NPC as bank teller");
        player.sendMessage("§f/mjbadmin removebankteller <id> §7- Unmark bank teller NPC");
        player.sendMessage("§f/mjbadmin listbanktellers §7- List all bank teller NPCs");
        player.sendMessage("§f/mjbadmin assignplot <player> <region> <type> §7- Assign a plot (apartment/house/store)");
        player.sendMessage("§f/mjbadmin removeplot <player> <region> §7- Remove a player from a plot");
        player.sendMessage("§f/mjbadmin addmember <player> <region> §7- Add a member to a plot");
        player.sendMessage("§f/mjbadmin addstarterapt <region> §7- Add region to starter apartment pool");
        player.sendMessage("§f/mjbadmin removestarterapt <region> §7- Remove region from starter pool");
        player.sendMessage("§f/mjbadmin liststarterapts §7- List available starter apartments");
        player.sendMessage("§f/mjbadmin unclaimstarter <region> §7- Unclaim a starter apartment");
        player.sendMessage("§f/mjbadmin assigncompanyplot <company> <region> §7- Assign a plot to a company");
        player.sendMessage("§f/mjbadmin removecompanyplot <region> §7- Remove a company plot assignment");
        player.sendMessage("§f/mjbadmin listproperty <region> <type> <district> <price> §7- List a property for sale");
        player.sendMessage("§f/mjbadmin unlistproperty <region> §7- Remove a property listing");
        player.sendMessage("§f/mjbadmin treasury §7- Check city treasury balance");
        player.sendMessage("§f/mjbadmin addlicensetype <type> <name> <cost> <renewal> <desc> §7- Add license type");
        player.sendMessage("§f/mjbadmin issuelicense <player> <type> §7- Issue license to player (free, admin only)");
        player.sendMessage("§f/mjbadmin revokelicense <player> <type> §7- Revoke a player's license");
        player.sendMessage("§f/mjbadmin addblackmarket §7- Add black market spawn location (stand at spot)");
        player.sendMessage("§f/mjbadmin removeblackmarket <id> §7- Remove a spawn location");
        player.sendMessage("§f/mjbadmin listblackmarket §7- List all spawn locations");
        player.sendMessage("§f/mjbadmin moveblackmarket §7- Force move NPC");
        player.sendMessage("§f/mjbadmin giveweapon <player> <type> §7- Give a police-issued weapon");
        player.sendMessage("§f/mjbadmin giveammo <player> <type> §7- Give police ammo");
        player.sendMessage("§f/mjbadmin setpolice <player> §7- Appoint a player as police officer");
        player.sendMessage("§f/mjbadmin removepolice <player> §7- Remove police rank");
        player.sendMessage("§f/mjbadmin listpolice §7- List all officers");
        player.sendMessage("§f/mjbadmin givehandcuffs <player> §7- Give handcuffs");
        player.sendMessage("§f/mjbadmin givebadge <player> §7- Give police badge");
        player.sendMessage("§f/mjbadmin uncuff <player> §7- Emergency uncuff");
        player.sendMessage("§f/mjbadmin setpolicerank <player> <rank> §7- Set officer rank (officer/detective/sergeant)");
        player.sendMessage("§f/mjbadmin setpolicestation <npc_id> §7- Set police station terminal NPC");
        player.sendMessage("§f/mjbadmin setvotingbooth <npc_id> §7- Set voting booth NPC");
        player.sendMessage("§f/mjbadmin setcouncilregion §7- Set current region as council chamber");
        player.sendMessage("§f/mjbadmin startelection §7- Force start election");
        player.sendMessage("§f/mjbadmin closeelection §7- Force close election + announce results");
        player.sendMessage("§f/mjbadmin opensession §7- Force open council session");
        player.sendMessage("§f/mjbadmin closesession §7- Force close session + evaluate proposals");
    }
}