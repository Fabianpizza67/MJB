package com.UserMC.MJB.listeners;

import com.UserMC.MJB.GovernmentManager;
import com.UserMC.MJB.GovernmentManager.PartyInfo;
import com.UserMC.MJB.MJB;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;

public class ElectionListener implements Listener {

    private final MJB plugin;
    public static final String VOTING_BOOTH_TAG = "voting_booth";
    private static final String VOTE_GUI = "§6§lCast Your Vote";
    private final NamespacedKey PARTY_ID_KEY;

    public ElectionListener(MJB plugin) {
        this.plugin = plugin;
        PARTY_ID_KEY = new NamespacedKey(plugin, "vote_party_id");
    }

    @EventHandler
    public void onNPCRightClick(NPCRightClickEvent event) {
        NPC npc = event.getNPC();
        if (!npc.data().has(VOTING_BOOTH_TAG)) return;
        Player player = event.getClicker();

        if (!plugin.getGovernmentManager().isElectionActive()) {
            player.sendMessage("§7There is no active election right now.");
            player.sendMessage("§7Elections are held every 2 weeks on Sunday.");
            return;
        }

        if (plugin.getGovernmentManager().hasVoted(player.getUniqueId())) {
            player.sendMessage("§7You have already cast your vote in this election.");
            return;
        }

        openVoteMenu(player);
    }

    private void openVoteMenu(Player player) {
        List<PartyInfo> parties = plugin.getGovernmentManager().getAllParties();
        PartyInfo ownParty = plugin.getGovernmentManager().getPartyByMember(player.getUniqueId());

        Inventory gui = plugin.getServer().createInventory(null, 54, VOTE_GUI);

        gui.setItem(4, item(Material.PAPER, "§6§lElection",
                "§7Vote for a party to give them a seat.",
                "§7You cannot vote for your own party.",
                "§7Your vote is anonymous."));

        int slot = 9;
        for (PartyInfo party : parties) {
            if (slot >= 45) break;
            boolean isOwn = ownParty != null && ownParty.id == party.id;
            int currentSeats = plugin.getGovernmentManager().getSeatsForParty(party.id);
            int memberCount = plugin.getGovernmentManager().getPartyMemberCount(party.id);
            String leaderName = plugin.getServer().getOfflinePlayer(party.leaderUuid).getName();

            Material mat = isOwn ? Material.RED_DYE : Material.LIME_DYE;
            String name = isOwn ? "§c§l" + party.name + " §7(Your party)" : "§a§l" + party.name;

            ItemStack btn = item(mat, name,
                    "§7Leader: §f" + leaderName,
                    "§7Members: §f" + memberCount,
                    "§7Current seats: §f" + currentSeats,
                    "§7About: §f" + party.description,
                    "",
                    isOwn ? "§cYou cannot vote for your own party." : "§eClick §7to vote for this party.");

            if (!isOwn) {
                ItemMeta meta = btn.getItemMeta();
                meta.getPersistentDataContainer().set(PARTY_ID_KEY, PersistentDataType.INTEGER, party.id);
                btn.setItemMeta(meta);
            }
            gui.setItem(slot++, btn);
        }

        if (parties.isEmpty()) {
            gui.setItem(22, item(Material.BARRIER, "§4No parties registered",
                    "§7No parties have been founded yet."));
        }

        gui.setItem(49, item(Material.BARRIER, "§4Cancel", "§7Close without voting."));
        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(VOTE_GUI)) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        if (clicked.getType() == Material.BARRIER) { player.closeInventory(); return; }

        if (!clicked.getItemMeta().getPersistentDataContainer()
                .has(PARTY_ID_KEY, PersistentDataType.INTEGER)) return;

        int partyId = clicked.getItemMeta().getPersistentDataContainer()
                .get(PARTY_ID_KEY, PersistentDataType.INTEGER);

        boolean success = plugin.getGovernmentManager().castVote(player.getUniqueId(), partyId);
        player.closeInventory();

        if (success) {
            PartyInfo party = plugin.getGovernmentManager().getPartyById(partyId);
            player.sendMessage("§6§l[Election] §eYour vote has been cast for §f" +
                    (party != null ? party.name : "the party") + "§e.");
            player.sendMessage("§7Thank you for participating in democracy!");
        } else {
            player.sendMessage("§4Failed to cast vote. You may have already voted or the election ended.");
        }
    }

    private ItemStack item(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }
}