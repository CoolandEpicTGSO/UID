package dev.kitteh.factions.command.defaults.money;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import dev.kitteh.factions.FPlayer;
import dev.kitteh.factions.Faction;
import dev.kitteh.factions.FactionsPlugin;
import dev.kitteh.factions.command.Cloudy;
import dev.kitteh.factions.command.Cmd;
import dev.kitteh.factions.command.FactionParser;
import dev.kitteh.factions.command.Sender;
import dev.kitteh.factions.command.defaults.CmdUpgrades;
import dev.kitteh.factions.integration.Econ;
import dev.kitteh.factions.util.Permission;
import dev.kitteh.factions.util.TL;
import dev.kitteh.factions.util.TriConsumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CmdMoneyBalance implements Cmd {
    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        // Hidden from /f help — the top-level /f bank shortcut (CmdBank) is the listed command.
        return (manager, builder, help) -> manager.command(
                builder.literal("balance", "bal", "b")
                        .meta(HIDE_IN_HELP, true)
                        .commandDescription(Cloudy.desc(TL.COMMAND_TNT_DEPOSIT_DESCRIPTION))
                        .permission(builder.commandPermission().and(Cloudy.hasPermission(Permission.MONEY_BALANCE)))
                        .flag(
                                manager.flagBuilder("faction")
                                        .withComponent(FactionParser.of(FactionParser.Include.SELF))
                                        .withPermission(Cloudy.hasPermission(Permission.MONEY_BALANCE_ANY))
                        )
                        .handler(this::handle)
        );
    }

    public void handle(CommandContext<Sender> context) {
        FPlayer fPlayer = context.sender().fPlayerOrNull();

        Faction faction = null;

        if (context.flags().get("faction") instanceof Faction f) {
            faction = f;
        }
        if (faction == null && fPlayer != null) {
            faction = fPlayer.faction();
        }

        if (faction == null || !faction.isNormal()) {
            context.sender().msgLegacy(TL.GENERIC_MEMBERONLY);
            return;
        }

        // Console fallback: keep the legacy chat output
        if (!(context.sender() instanceof Sender.Player playerSender)) {
            Econ.sendBalanceInfo(context.sender().sender(), faction);
            return;
        }

        openMainGui(playerSender.player(), faction);
    }

    // ── Main GUI: balance + cap + leaderboard tabs ────────────────────────────

    private void openMainGui(Player player, Faction faction) {
        Component title = Component.text("✦ Faction Bank — " + faction.tag(), NamedTextColor.GOLD);
        ChestGui gui = new ChestGui(3, ComponentHolder.of(title));
        gui.setOnGlobalDrag(e -> e.setCancelled(true));
        gui.setOnGlobalClick(e -> e.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 3);
        ItemStack filler = makeFiller(Material.YELLOW_STAINED_GLASS_PANE);
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 3; y++) {
                pane.addItem(new GuiItem(filler), x, y);
            }
        }

        // Center: balance + cap
        pane.addItem(buildBalanceItem(faction), 4, 1);

        // Left tab: top depositors
        ItemStack depBtn = new ItemStack(Material.GOLD_INGOT);
        ItemMeta dm = depBtn.getItemMeta();
        dm.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "Top Depositors");
        List<String> dl = new ArrayList<>();
        dl.add(ChatColor.GRAY + "Lifetime totals deposited");
        dl.add(ChatColor.GRAY + "by each member.");
        dl.add(" ");
        dl.add(ChatColor.YELLOW + "▶ Click to view");
        dm.setLore(dl);
        depBtn.setItemMeta(dm);
        GuiItem depItem = new GuiItem(depBtn);
        depItem.setAction(e -> {
            e.setCancelled(true);
            openLeaderboard(player, faction, true);
        });
        pane.addItem(depItem, 1, 1);

        // Right tab: top withdrawers
        ItemStack wBtn = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta wm = wBtn.getItemMeta();
        wm.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Top Withdrawers");
        List<String> wl = new ArrayList<>();
        wl.add(ChatColor.GRAY + "Lifetime totals withdrawn");
        wl.add(ChatColor.GRAY + "by each member.");
        wl.add(" ");
        wl.add(ChatColor.YELLOW + "▶ Click to view");
        wm.setLore(wl);
        wBtn.setItemMeta(wm);
        GuiItem wItem = new GuiItem(wBtn);
        wItem.setAction(e -> {
            e.setCancelled(true);
            openLeaderboard(player, faction, false);
        });
        pane.addItem(wItem, 7, 1);

        gui.addPane(pane);
        gui.show(player);
    }

    private GuiItem buildBalanceItem(Faction faction) {
        double balance = Econ.getBalance(faction);
        int tier = faction.customUpgradeTier(CmdUpgrades.KEY_BANK_CAP);
        double cap = FactionsPlugin.instance().configManager().factionLevelsConfig().bankCapForTier(tier);
        double pct = cap <= 0 ? 0 : Math.min(1.0, balance / cap);

        ItemStack item = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Faction Bank");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Balance: " + ChatColor.GREEN + Econ.moneyString(balance));
        lore.add(ChatColor.GRAY + "Capacity: " + ChatColor.YELLOW + Econ.moneyString(cap));
        lore.add(" ");

        // Progress bar (20 cells)
        int filled = (int) Math.round(pct * 20);
        StringBuilder bar = new StringBuilder();
        bar.append(ChatColor.GREEN);
        for (int i = 0; i < filled; i++) bar.append("█");
        bar.append(ChatColor.DARK_GRAY);
        for (int i = filled; i < 20; i++) bar.append("█");
        bar.append(" ").append(ChatColor.YELLOW).append(String.format("%.1f%%", pct * 100));
        lore.add(bar.toString());
        lore.add(" ");
        lore.add(ChatColor.GRAY + "Bank Capacity tier: " + ChatColor.WHITE + tier);
        lore.add(ChatColor.GRAY + "Upgrade with " + ChatColor.WHITE + "/f upgrades");
        lore.add(" ");
        lore.add(ChatColor.GRAY + "Use " + ChatColor.WHITE + "/f money deposit <amount>");
        lore.add(ChatColor.GRAY + "Use " + ChatColor.WHITE + "/f money withdraw <amount>");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return new GuiItem(item);
    }

    // ── Leaderboard view ──────────────────────────────────────────────────────

    private void openLeaderboard(Player player, Faction faction, boolean deposits) {
        Component title = Component.text((deposits ? "Top Depositors — " : "Top Withdrawers — ") + faction.tag(),
                deposits ? NamedTextColor.GREEN : NamedTextColor.RED);
        ChestGui gui = new ChestGui(6, ComponentHolder.of(title));
        gui.setOnGlobalDrag(e -> e.setCancelled(true));
        gui.setOnGlobalClick(e -> e.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 6);
        ItemStack filler = makeFiller(deposits ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE);
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 6; y++) {
                pane.addItem(new GuiItem(filler), x, y);
            }
        }

        Map<UUID, Double> source = deposits ? faction.bankDepositLeaderboard() : faction.bankWithdrawLeaderboard();

        // Sort by amount desc
        List<Map.Entry<UUID, Double>> sorted = new ArrayList<>(source.entrySet());
        sorted.sort(Comparator.<Map.Entry<UUID, Double>>comparingDouble(Map.Entry::getValue).reversed());

        if (sorted.isEmpty()) {
            ItemStack empty = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta em = empty.getItemMeta();
            em.setDisplayName(ChatColor.GRAY + (deposits ? "No deposits yet" : "No withdrawals yet"));
            empty.setItemMeta(em);
            pane.addItem(new GuiItem(empty), 4, 2);
        } else {
            // Show up to 36 entries (4 rows × 9 cols)
            int maxEntries = Math.min(sorted.size(), 36);
            for (int i = 0; i < maxEntries; i++) {
                Map.Entry<UUID, Double> entry = sorted.get(i);
                int col = i % 9;
                int row = i / 9;
                pane.addItem(buildLeaderboardEntry(i + 1, entry.getKey(), entry.getValue(), deposits), col, row);
            }
        }

        // Footer: back button
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName(ChatColor.GRAY + "← Back");
        back.setItemMeta(bm);
        GuiItem backItem = new GuiItem(back);
        backItem.setAction(e -> {
            e.setCancelled(true);
            openMainGui(player, faction);
        });
        pane.addItem(backItem, 0, 5);

        // Footer info
        ItemStack info = new ItemStack(deposits ? Material.GOLD_INGOT : Material.GOLD_NUGGET);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName(deposits ? ChatColor.GREEN + "Top Depositors" : ChatColor.RED + "Top Withdrawers");
        List<String> il = new ArrayList<>();
        il.add(ChatColor.GRAY + "Total members tracked: " + ChatColor.WHITE + sorted.size());
        im.setLore(il);
        info.setItemMeta(im);
        pane.addItem(new GuiItem(info), 4, 5);

        gui.addPane(pane);
        gui.show(player);
    }

    private GuiItem buildLeaderboardEntry(int rank, UUID uuid, double amount, boolean deposits) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String name = offline.getName() != null ? offline.getName() : uuid.toString().substring(0, 8);

        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        // Avoid relying on SkullMeta to keep this code Spigot-portable; just use the player display name.
        String medal = switch (rank) {
            case 1 -> "§6#1 §e";
            case 2 -> "§7#2 §e";
            case 3 -> "§c#3 §e";
            default -> "§7#" + rank + " §f";
        };
        meta.setDisplayName(medal + name);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + (deposits ? "Total deposited: " : "Total withdrawn: ")
                + (deposits ? ChatColor.GREEN : ChatColor.RED) + Econ.moneyString(amount));
        meta.setLore(lore);
        // Set skull owner if SkullMeta is available (works on Paper and Spigot)
        if (meta instanceof org.bukkit.inventory.meta.SkullMeta skull) {
            skull.setOwningPlayer(offline);
        }
        item.setItemMeta(meta);
        return new GuiItem(item);
    }

    private static ItemStack makeFiller(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(" ");
        item.setItemMeta(m);
        return item;
    }
}
