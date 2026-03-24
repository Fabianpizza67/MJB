package com.UserMC.MJB.listeners;

import com.UserMC.MJB.GovernmentManager;
import com.UserMC.MJB.GovernmentManager.Proposal;
import com.UserMC.MJB.MJB;
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

import java.util.*;

public class CouncilListener implements Listener {

    private final MJB plugin;
    private static final String VOTE_GUI = "§9§lCouncil Vote";
    private final NamespacedKey PROPOSAL_KEY;
    private final NamespacedKey VOTE_KEY;

    public CouncilListener(MJB plugin) {
        this.plugin = plugin;
        PROPOSAL_KEY = new NamespacedKey(plugin, "proposal_id");
        VOTE_KEY     = new NamespacedKey(plugin, "vote_value");
    }

    // Called from GovernmentCommand when /propose is used
    public void broadcastProposal(Player proposer, Proposal proposal) {
        // Send voting GUI to all online seat holders
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (!plugin.getGovernmentManager().hasSeat(p.getUniqueId())) continue;
            if (plugin.getGovernmentManager().hasVotedOnProposal(p.getUniqueId(), proposal.id)) continue;

            p.sendMessage("§9§l[Council] §b" + proposer.getName() + " §fhas proposed a new law:");
            p.sendMessage("§f  \"" + proposal.text + "\"");
            p.sendMessage("§7Your voting power: §b" +
                    plugin.getGovernmentManager().getVotingPower(p.getUniqueId()) + " seat(s)");
            p.sendMessage("§7Use §f/vote §7or a voting GUI will open automatically.");

            openVoteGui(p, proposal);
        }
    }

    public void openVoteGui(Player player, Proposal proposal) {
        if (plugin.getGovernmentManager().hasVotedOnProposal(player.getUniqueId(), proposal.id)) {
            player.sendMessage("§7You have already voted on this proposal.");
            return;
        }

        int power = plugin.getGovernmentManager().getVotingPower(player.getUniqueId());
        if (power == 0) {
            player.sendMessage("§4You do not hold any council seats.");
            return;
        }

        Inventory gui = plugin.getServer().createInventory(null, 27, VOTE_GUI);

        gui.setItem(4, item(Material.PAPER, "§9§lProposal #" + proposal.id,
                "§f" + proposal.text,
                "",
                "§7Your voting power: §b" + power + " seat(s)",
                "§7Yes: §a" + proposal.yesVotes + " §7| No: §c" + proposal.noVotes));

        ItemStack yesBtn = item(Material.LIME_DYE, "§a§lYES",
                "§7Vote YES with all your seats (" + power + ").",
                "§7This cannot be undone.");
        ItemMeta yesMeta = yesBtn.getItemMeta();
        yesMeta.getPersistentDataContainer().set(PROPOSAL_KEY, PersistentDataType.INTEGER, proposal.id);
        yesMeta.getPersistentDataContainer().set(VOTE_KEY, PersistentDataType.BOOLEAN, true);
        yesBtn.setItemMeta(yesMeta);
        gui.setItem(11, yesBtn);

        ItemStack noBtn = item(Material.RED_DYE, "§c§lNO",
                "§7Vote NO with all your seats (" + power + ").",
                "§7This cannot be undone.");
        ItemMeta noMeta = noBtn.getItemMeta();
        noMeta.getPersistentDataContainer().set(PROPOSAL_KEY, PersistentDataType.INTEGER, proposal.id);
        noMeta.getPersistentDataContainer().set(VOTE_KEY, PersistentDataType.BOOLEAN, false);
        noBtn.setItemMeta(noMeta);
        gui.setItem(15, noBtn);

        gui.setItem(22, item(Material.BARRIER, "§7Abstain", "§7Close without voting."));
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

        var pdc = clicked.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(PROPOSAL_KEY, PersistentDataType.INTEGER)) return;

        int proposalId = pdc.get(PROPOSAL_KEY, PersistentDataType.INTEGER);
        boolean vote   = pdc.get(VOTE_KEY, PersistentDataType.BOOLEAN);

        boolean success = plugin.getGovernmentManager().voteOnProposal(proposalId, player.getUniqueId(), vote);
        player.closeInventory();

        if (success) {
            int power = plugin.getGovernmentManager().getVotingPower(player.getUniqueId());
            player.sendMessage("§9§l[Council] §fYou voted §" + (vote ? "a§lYES" : "c§lNO") +
                    " §fwith §b" + power + " §fseat(s).");

            // Broadcast updated tally to all online seat holders
            List<Proposal> open = plugin.getGovernmentManager().getOpenProposals();
            for (Proposal p : open) {
                if (p.id == proposalId) {
                    for (Player seat : plugin.getServer().getOnlinePlayers()) {
                        if (!plugin.getGovernmentManager().hasSeat(seat.getUniqueId())) continue;
                        seat.sendMessage("§9[Council] §fProposal #" + proposalId + " tally — " +
                                "§aYes: " + p.yesVotes + " §7| §cNo: " + p.noVotes);
                    }
                    break;
                }
            }
        } else {
            player.sendMessage("§4Could not cast vote — you may have already voted.");
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