package dev.kitteh.factions.command.defaults;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import dev.kitteh.factions.FPlayer;
import dev.kitteh.factions.Faction;
import dev.kitteh.factions.FactionsPlugin;
import dev.kitteh.factions.command.Cloudy;
import dev.kitteh.factions.command.Cmd;
import dev.kitteh.factions.command.Sender;
import dev.kitteh.factions.integration.Econ;
import dev.kitteh.factions.level.FactionLevelManager;
import dev.kitteh.factions.level.FactionLevelsConfig;
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
import org.bukkit.inventory.meta.SkullMeta;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /f info — Faction info hub. Shows level/XP front-and-center, claim count, bank balance,
 * top depositor / top withdrawer, and quick-access tiles for roster, PV, missions, effects.
 */
public class CmdInfo implements Cmd {

    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        return (manager, builder, help) -> manager.command(
                builder.literal("info")
                        .commandDescription(Cloudy.desc("Open the faction info GUI"))
                        .permission(builder.commandPermission().and(Cloudy.hasFaction()))
                        .handler(this::handle)
        );
    }

    private void handle(CommandContext<Sender> context) {
        if (!(context.sender() instanceof Sender.Player playerSender)) {
            context.sender().sendRichMessage("<red>This command can only be run by a player.");
            return;
        }
        openInfoGui(playerSender.player(), playerSender.fPlayer());
    }

    private void openInfoGui(Player player, FPlayer fPlayer) {
        Faction faction = fPlayer.faction();
        FactionLevelsConfig cfg = FactionsPlugin.instance().configManager().factionLevelsConfig();

        Component title = Component.text("✦ " + faction.tag() + " — Info", NamedTextColor.GOLD);
        ChestGui gui = new ChestGui(6, ComponentHolder.of(title));
        gui.setOnGlobalDrag(e -> e.setCancelled(true));
        gui.setOnGlobalClick(e -> e.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 6);
        ItemStack borderPane = makeFiller(Material.MAGENTA_STAINED_GLASS_PANE);
        ItemStack innerPane = makeFiller(Material.PURPLE_STAINED_GLASS_PANE);
        // Layout (organised in clean lines, like /f upgrades):
        //   Row 0 — border
        //   Row 1 — merged faction header + level/XP tile, dead-center
        //   Row 2 — STATS line: Top Depositor · Land · Members · Bank · TNT · Top Withdrawer
        //   Row 3 — border separator
        //   Row 4 — FEATURES line: Roster · PV · Missions · Effects · Logs · Perms · Upgrades
        //   Row 5 — border, Close dead-center
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 6; y++) {
                boolean border = (x == 0 || x == 8 || y == 0 || y == 3 || y == 5);
                pane.addItem(new GuiItem(border ? borderPane : innerPane), x, y);
            }
        }

        // ── Row 1 (center): merged faction header + level/XP showcase ────
        pane.addItem(buildFactionShowcase(faction, cfg), 4, 1);

        // ── Row 2: STATS line — TopDep · Land · Members · Bank · TNT · TopWdr ──
        pane.addItem(buildTopContributorItem(faction, true), 1, 2);
        pane.addItem(buildClaimsItem(player, faction), 2, 2);
        pane.addItem(buildMembersItem(player, faction), 3, 2);
        pane.addItem(buildBankLinkItem(player, faction, cfg), 5, 2);
        pane.addItem(buildTntLinkItem(player, faction), 6, 2);
        pane.addItem(buildTopContributorItem(faction, false), 7, 2);

        // ── Row 4: FEATURES line — Roster · PV · Missions · Effects · Logs · Perms · Upgrades ──
        pane.addItem(buildLink(player, "Roster", Material.PLAYER_HEAD,
                "View and manage the faction roster.", "f roster"), 1, 4);
        pane.addItem(buildLink(player, "Faction PV", Material.ENDER_CHEST,
                "Open the faction private vault.", "f pv"), 2, 4);
        pane.addItem(buildLink(player, "Missions", Material.SUNFLOWER,
                "View daily, weekly & map missions.", "f missions"), 3, 4);
        pane.addItem(buildLink(player, "F Effects", Material.SPLASH_POTION,
                "Faction-wide potion effects shop & inventory.", "f effects"), 4, 4);
        pane.addItem(buildLink(player, "F Logs", Material.WRITTEN_BOOK,
                "View recent faction activity.", "f logs"), 5, 4);
        pane.addItem(buildLink(player, "F Permissions", Material.WRITABLE_BOOK,
                "Manage role & relation permissions.", "f perms"), 6, 4);
        pane.addItem(buildLink(player, "F Upgrades", Material.DIAMOND,
                "Spend faction coins on upgrades.", "f upgrades"), 7, 4);

        // ── Row 5 (center): close button ─────────────────────────────────
        ItemStack closeIcon = new ItemStack(Material.BARRIER);
        ItemMeta cm = closeIcon.getItemMeta();
        cm.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Close");
        closeIcon.setItemMeta(cm);
        GuiItem closeItem = new GuiItem(closeIcon);
        closeItem.setAction(e -> {
            e.setCancelled(true);
            player.closeInventory();
        });
        pane.addItem(closeItem, 4, 5);

        gui.addPane(pane);
        gui.show(player);
    }

    // ── Item builders ────────────────────────────────────────────────────

    /**
     * Single merged showcase tile — combines the old OAK_SIGN faction header
     * (tag, leader, description) with the EXPERIENCE_BOTTLE level/XP panel into
     * one clean centerpiece.
     */
    private GuiItem buildFactionShowcase(Faction faction, FactionLevelsConfig cfg) {
        int level = faction.factionLevel();
        long xp = faction.factionXp();
        int max = cfg.maxLevel();
        long required = level >= max ? cfg.xpRequired(max) : cfg.xpRequired(level + 1);

        ItemStack item = new ItemStack(level >= max ? Material.NETHER_STAR : Material.EXPERIENCE_BOTTLE);
        item.setAmount(Math.max(1, Math.min(64, level)));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + faction.tag()
                + ChatColor.GRAY + " — " + ChatColor.LIGHT_PURPLE + "Level " + level + ChatColor.GRAY + "/" + max);

        List<String> lore = new ArrayList<>();
        // ── Faction identity (from the old sign tile) ──
        lore.add(ChatColor.GRAY + "Leader: " + ChatColor.WHITE
                + (faction.admin() != null ? faction.admin().name() : "(none)"));
        if (faction.description() != null && !faction.description().isEmpty()) {
            lore.add(ChatColor.GRAY + "" + ChatColor.ITALIC + "\"" + faction.description() + "\"");
        }
        lore.add(" ");
        // ── Level / XP (from the old XP-bottle tile) ──
        if (level >= max) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "✓ MAX LEVEL");
            lore.add(ChatColor.GRAY + "Total XP this level: " + ChatColor.WHITE + xp);
        } else {
            double progress = FactionLevelManager.progressToNextLevel(faction);
            int barLen = 20;
            int filled = (int) Math.round(progress * barLen);
            StringBuilder bar = new StringBuilder(ChatColor.GREEN + "");
            for (int i = 0; i < filled; i++) bar.append("█");
            bar.append(ChatColor.DARK_GRAY);
            for (int i = filled; i < barLen; i++) bar.append("█");
            lore.add(ChatColor.GRAY + "XP: " + ChatColor.WHITE + xp + ChatColor.GRAY + "/" + ChatColor.WHITE + required);
            lore.add(bar.toString() + " " + ChatColor.YELLOW + String.format("%.1f%%", progress * 100));
            lore.add(ChatColor.GRAY + "To next level: " + ChatColor.AQUA + FactionLevelManager.xpUntilNextLevel(faction));
        }
        lore.add(" ");
        lore.add(ChatColor.GRAY + "Faction Coins: " + ChatColor.LIGHT_PURPLE + faction.factionPoints()
                + ChatColor.DARK_GRAY + " (earn by levelling up)");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return new GuiItem(item, e -> e.setCancelled(true));
    }

    /** Land tile — clickable shortcut to /f logs (claim/unclaim history). */
    private GuiItem buildClaimsItem(Player player, Faction faction) {
        ItemStack item = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "Land");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Claims: " + ChatColor.WHITE + faction.claimCount());
        lore.add(ChatColor.GRAY + "Power: " + ChatColor.WHITE + (long) faction.power()
                + ChatColor.GRAY + " / " + ChatColor.WHITE + (long) faction.powerMax());
        lore.add(" ");
        lore.add(ChatColor.GREEN + "▶ Click to view the Claims log");
        meta.setLore(lore);
        item.setItemMeta(meta);

        GuiItem gi = new GuiItem(item);
        gi.setAction(e -> {
            e.setCancelled(true);
            player.closeInventory();
            org.bukkit.Bukkit.getScheduler().runTask(
                    dev.kitteh.factions.plugin.AbstractFactionsPlugin.instance(),
                    () -> player.performCommand("f logs")
            );
        });
        return gi;
    }

    private GuiItem buildMembersItem(Player player, Faction faction) {
        int total = faction.members().size();
        int online = faction.membersOnline(true).size();
        ItemStack item = new ItemStack(Material.IRON_HELMET);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Members");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Total: " + ChatColor.WHITE + total);
        lore.add(ChatColor.GRAY + "Online: " + ChatColor.GREEN + online);
        lore.add(" ");
        lore.add(ChatColor.GREEN + "▶ Click to open the Roster");
        meta.setLore(lore);
        item.setItemMeta(meta);

        GuiItem gi = new GuiItem(item);
        gi.setAction(e -> {
            e.setCancelled(true);
            player.closeInventory();
            org.bukkit.Bukkit.getScheduler().runTask(
                    dev.kitteh.factions.plugin.AbstractFactionsPlugin.instance(),
                    () -> player.performCommand("f roster")
            );
        });
        return gi;
    }

    /** Bank tile that ALSO acts as a clickable shortcut to /f bank. Shows balance + cap + click hint. */
    private GuiItem buildBankLinkItem(Player player, Faction faction, FactionLevelsConfig cfg) {
        double balance = Econ.shouldBeUsed() ? Econ.getBalance(faction) : 0;
        int tier = faction.customUpgradeTier(CmdUpgrades.KEY_BANK_CAP);
        double cap = cfg.bankCapForTier(tier);

        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Faction Bank");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Balance: " + ChatColor.GREEN + Econ.moneyString(balance));
        lore.add(ChatColor.GRAY + "Capacity: " + ChatColor.YELLOW + Econ.moneyString(cap));
        lore.add(" ");
        lore.add(ChatColor.GREEN + "▶ Click to open Bank GUI");
        meta.setLore(lore);
        item.setItemMeta(meta);

        GuiItem gi = new GuiItem(item);
        gi.setAction(e -> {
            e.setCancelled(true);
            player.closeInventory();
            org.bukkit.Bukkit.getScheduler().runTask(
                    dev.kitteh.factions.plugin.AbstractFactionsPlugin.instance(),
                    () -> player.performCommand("f bank")
            );
        });
        return gi;
    }

    /** TNT Bank tile + clickable shortcut to /f tnt. */
    private GuiItem buildTntLinkItem(Player player, Faction faction) {
        ItemStack item = new ItemStack(Material.TNT);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "TNT Bank");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Stored: " + ChatColor.WHITE + faction.tntBank()
                + ChatColor.GRAY + " / " + ChatColor.WHITE + faction.tntBankMax());
        lore.add(" ");
        lore.add(ChatColor.GREEN + "▶ Click to open TNT Bank");
        meta.setLore(lore);
        item.setItemMeta(meta);

        GuiItem gi = new GuiItem(item);
        gi.setAction(e -> {
            e.setCancelled(true);
            player.closeInventory();
            org.bukkit.Bukkit.getScheduler().runTask(
                    dev.kitteh.factions.plugin.AbstractFactionsPlugin.instance(),
                    () -> player.performCommand("f tnt")
            );
        });
        return gi;
    }

    private GuiItem buildTopContributorItem(Faction faction, boolean deposits) {
        Map<UUID, Double> source = deposits ? faction.bankDepositLeaderboard() : faction.bankWithdrawLeaderboard();

        UUID topUuid = null;
        double topAmount = 0;
        for (Map.Entry<UUID, Double> entry : source.entrySet()) {
            if (entry.getValue() > topAmount) {
                topAmount = entry.getValue();
                topUuid = entry.getKey();
            }
        }

        Material mat = topUuid != null ? Material.PLAYER_HEAD : Material.GRAY_DYE;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        String label = deposits ? "Top Depositor" : "Top Withdrawer";
        ChatColor color = deposits ? ChatColor.GREEN : ChatColor.RED;
        meta.setDisplayName(color + "" + ChatColor.BOLD + label);
        List<String> lore = new ArrayList<>();
        if (topUuid == null) {
            lore.add(ChatColor.GRAY + "(no " + (deposits ? "deposits" : "withdrawals") + " yet)");
        } else {
            OfflinePlayer op = Bukkit.getOfflinePlayer(topUuid);
            String name = op.getName() != null ? op.getName() : topUuid.toString().substring(0, 8);
            lore.add(ChatColor.GRAY + "Player: " + ChatColor.WHITE + name);
            lore.add(ChatColor.GRAY + (deposits ? "Total deposited: " : "Total withdrawn: ")
                    + color + Econ.moneyString(topAmount));
            lore.add(" ");
            lore.add(ChatColor.GRAY + "Rank list: " + ChatColor.WHITE + "/f bank");
            if (meta instanceof SkullMeta sm) {
                sm.setOwningPlayer(op);
            }

            // Sort remaining and show top 3
            List<Map.Entry<UUID, Double>> sorted = new ArrayList<>(source.entrySet());
            sorted.sort(Comparator.<Map.Entry<UUID, Double>>comparingDouble(Map.Entry::getValue).reversed());
            if (sorted.size() > 1) {
                lore.add(" ");
                lore.add(ChatColor.GRAY + "Top 3:");
                int shown = 0;
                for (Map.Entry<UUID, Double> e : sorted) {
                    if (shown >= 3) break;
                    OfflinePlayer p = Bukkit.getOfflinePlayer(e.getKey());
                    String n = p.getName() != null ? p.getName() : e.getKey().toString().substring(0, 8);
                    lore.add(ChatColor.DARK_GRAY + "#" + (shown + 1) + " " + ChatColor.GRAY + n
                            + ChatColor.DARK_GRAY + " — " + color + Econ.moneyString(e.getValue()));
                    shown++;
                }
            }
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return new GuiItem(item, e -> e.setCancelled(true));
    }

    private GuiItem buildLink(Player player, String label, Material icon, String description, String runCommand) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + label);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + description);
        lore.add(" ");
        lore.add(ChatColor.GREEN + "▶ Click to open");
        meta.setLore(lore);
        item.setItemMeta(meta);

        GuiItem gi = new GuiItem(item);
        gi.setAction(e -> {
            e.setCancelled(true);
            player.closeInventory();
            // Run the command via the player so all permission/role checks fire normally
            org.bukkit.Bukkit.getScheduler().runTask(
                    dev.kitteh.factions.plugin.AbstractFactionsPlugin.instance(),
                    () -> player.performCommand(runCommand)
            );
        });
        return gi;
    }

    private static ItemStack makeFiller(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(" ");
        item.setItemMeta(m);
        return item;
    }
}
