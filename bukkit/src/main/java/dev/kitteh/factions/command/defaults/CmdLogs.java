package dev.kitteh.factions.command.defaults;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import dev.kitteh.factions.FPlayer;
import dev.kitteh.factions.Faction;
import dev.kitteh.factions.audit.AuditEntry;
import dev.kitteh.factions.command.Cloudy;
import dev.kitteh.factions.command.Cmd;
import dev.kitteh.factions.command.Sender;
import dev.kitteh.factions.permissible.PermissibleActions;
import dev.kitteh.factions.permissible.Role;
import dev.kitteh.factions.util.Permission;
import dev.kitteh.factions.util.TriConsumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * /f logs — GUI-based faction activity log with category tabs.
 */
public class CmdLogs implements Cmd {

    private static final SimpleDateFormat TS = new SimpleDateFormat("MM/dd HH:mm");
    private static final int LINES_PER_PAGE = 36; // 4 rows × 9 columns of papers in the log view

    enum Category {
        ALL("All Activity", Material.WRITTEN_BOOK, e -> true),
        MEMBERS("Members", Material.PLAYER_HEAD,
                e -> set(e.type, "invite", "deinvite", "join", "leave", "promote", "demote", "leader")),
        BANK("Bank", Material.GOLD_INGOT,
                e -> set(e.type, "bank-deposit", "bank-withdraw")),
        TNT_BANK("TNT Bank", Material.TNT,
                e -> set(e.type, "tnt-deposit", "tnt-withdraw")),
        LAND("Land", Material.GRASS_BLOCK,
                e -> set(e.type, "claim", "unclaim")),
        WARPS("Warps", Material.ENDER_PEARL,
                e -> set(e.type, "warp-set", "warp-delete")),
        HOME("Home", Material.RED_BED,
                e -> set(e.type, "sethome")),
        FACTION("Faction", Material.OAK_SIGN,
                e -> set(e.type, "rename", "relation")),
        SPAWNERS("Spawners", Material.SPAWNER,
                e -> set(e.type, "spawner-place", "spawner-break"));

        final String label;
        final Material icon;
        final Predicate<AuditEntry> match;

        Category(String label, Material icon, Predicate<AuditEntry> match) {
            this.label = label;
            this.icon = icon;
            this.match = match;
        }

        private static boolean set(String value, String... candidates) {
            for (String c : candidates) {
                if (c.equals(value)) return true;
            }
            return false;
        }
    }

    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        return (manager, builder, help) -> manager.command(
                builder.literal("logs", "log")
                        .commandDescription(Cloudy.desc("View your faction's activity log"))
                        .permission(builder.commandPermission()
                                .and(Cloudy.hasPermission(Permission.LOGS))
                                .and(Cloudy.hasFaction()))
                        .handler(this::handle)
        );
    }

    private void handle(CommandContext<Sender> context) {
        if (!(context.sender() instanceof Sender.Player playerSender)) {
            context.sender().sendRichMessage("<red>This command can only be run by a player.");
            return;
        }
        FPlayer fPlayer = playerSender.fPlayer();
        Player player = playerSender.player();
        Faction faction = fPlayer.faction();

        if (fPlayer.role() != Role.ADMIN
                && !faction.hasAccess(fPlayer, PermissibleActions.LOGS, fPlayer.lastStoodAt())) {
            fPlayer.sendRichMessage("<red>You do not have permission to view <white>/f logs</white>.");
            return;
        }

        openCategoryMenu(player, fPlayer);
    }

    // ── Main menu: pick a category ────────────────────────────────────────────

    private void openCategoryMenu(Player player, FPlayer fPlayer) {
        Faction faction = fPlayer.faction();
        Component title = Component.text("✦ Faction Logs", NamedTextColor.AQUA);
        ChestGui gui = new ChestGui(3, ComponentHolder.of(title));
        gui.setOnGlobalDrag(e -> e.setCancelled(true));
        gui.setOnGlobalClick(e -> e.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 3);
        ItemStack filler = filler();
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 3; y++) {
                pane.addItem(new GuiItem(filler), x, y);
            }
        }

        List<AuditEntry> all = faction.factionLog();
        Category[] cats = Category.values();
        // Layout: row 0/2 background, row 1 — categories. Spacing: 1, 2, 3, 4, 5, 6, 7 (7 cats fit, we have 8).
        // We'll use 8 slots: cols 0-7 of row 1.
        for (int i = 0; i < cats.length && i < 9; i++) {
            pane.addItem(buildCategoryItem(player, fPlayer, cats[i], all), i, 1);
        }

        gui.addPane(pane);
        gui.show(player);
    }

    private GuiItem buildCategoryItem(Player player, FPlayer fPlayer, Category category, List<AuditEntry> all) {
        long count = all.stream().filter(category.match).count();
        ItemStack item = new ItemStack(category.icon);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + category.label);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Entries: " + ChatColor.YELLOW + count);
        lore.add(" ");
        lore.add(ChatColor.YELLOW + "▶ Click to view");
        meta.setLore(lore);
        item.setItemMeta(meta);

        GuiItem gi = new GuiItem(item);
        gi.setAction(e -> {
            e.setCancelled(true);
            openCategoryView(player, fPlayer, category, 1);
        });
        return gi;
    }

    // ── Category view: paginated list of entries ──────────────────────────────

    private void openCategoryView(Player player, FPlayer fPlayer, Category category, int page) {
        Faction faction = fPlayer.faction();
        // Filter and sort newest-first
        List<AuditEntry> filtered = new ArrayList<>();
        List<AuditEntry> source = faction.factionLog();
        for (int i = source.size() - 1; i >= 0; i--) {
            AuditEntry e = source.get(i);
            if (category.match.test(e)) filtered.add(e);
        }

        int total = filtered.size();
        int totalPages = Math.max(1, (total + LINES_PER_PAGE - 1) / LINES_PER_PAGE);
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        Component title = Component.text("Logs: " + category.label + " (" + page + "/" + totalPages + ")", NamedTextColor.AQUA);
        ChestGui gui = new ChestGui(6, ComponentHolder.of(title));
        gui.setOnGlobalDrag(e -> e.setCancelled(true));
        gui.setOnGlobalClick(e -> e.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 6);
        ItemStack filler = filler();
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 6; y++) {
                pane.addItem(new GuiItem(filler), x, y);
            }
        }

        // Entries: rows 0-3 (4 rows × 9 = 36 entries / page)
        int start = (page - 1) * LINES_PER_PAGE;
        int end = Math.min(start + LINES_PER_PAGE, total);
        long now = System.currentTimeMillis();
        for (int i = start; i < end; i++) {
            int relative = i - start;
            int col = relative % 9;
            int row = relative / 9;
            pane.addItem(buildEntryItem(filtered.get(i), now), col, row);
        }

        // Footer (row 5): back, prev, info, next
        // Back to category menu
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName(ChatColor.GRAY + "← Back");
        back.setItemMeta(bm);
        GuiItem backItem = new GuiItem(back);
        backItem.setAction(e -> {
            e.setCancelled(true);
            openCategoryMenu(player, fPlayer);
        });
        pane.addItem(backItem, 0, 5);

        // Prev page
        if (page > 1) {
            ItemStack prev = new ItemStack(Material.PAPER);
            ItemMeta pm = prev.getItemMeta();
            pm.setDisplayName(ChatColor.YELLOW + "← Page " + (page - 1));
            prev.setItemMeta(pm);
            GuiItem prevItem = new GuiItem(prev);
            int targetPage = page - 1;
            prevItem.setAction(e -> {
                e.setCancelled(true);
                openCategoryView(player, fPlayer, category, targetPage);
            });
            pane.addItem(prevItem, 3, 5);
        }

        // Info
        ItemStack info = new ItemStack(category.icon);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName(ChatColor.AQUA + category.label);
        List<String> il = new ArrayList<>();
        il.add(ChatColor.GRAY + "Page " + ChatColor.YELLOW + page + ChatColor.GRAY + " of " + ChatColor.YELLOW + totalPages);
        il.add(ChatColor.GRAY + "Total entries: " + ChatColor.YELLOW + total);
        im.setLore(il);
        info.setItemMeta(im);
        pane.addItem(new GuiItem(info), 4, 5);

        // Next page
        if (page < totalPages) {
            ItemStack next = new ItemStack(Material.PAPER);
            ItemMeta nm = next.getItemMeta();
            nm.setDisplayName(ChatColor.YELLOW + "Page " + (page + 1) + " →");
            next.setItemMeta(nm);
            GuiItem nextItem = new GuiItem(next);
            int targetPage = page + 1;
            nextItem.setAction(e -> {
                e.setCancelled(true);
                openCategoryView(player, fPlayer, category, targetPage);
            });
            pane.addItem(nextItem, 5, 5);
        }

        if (total == 0) {
            ItemStack empty = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta em = empty.getItemMeta();
            em.setDisplayName(ChatColor.GRAY + "No entries in this category");
            empty.setItemMeta(em);
            pane.addItem(new GuiItem(empty), 4, 2);
        }

        gui.addPane(pane);
        gui.show(player);
    }

    private GuiItem buildEntryItem(AuditEntry entry, long now) {
        ItemStack item = new ItemStack(iconForType(entry.type));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(colorChatFor(entry.type) + entry.message);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Type: " + ChatColor.YELLOW + entry.type);
        lore.add(ChatColor.GRAY + "By: " + ChatColor.YELLOW + entry.actorName);
        // Block-tied entries (claim, place, break, spawner-*) carry exact coords — show them
        if (entry.world != null && (entry.x != -1 || entry.y != -1 || entry.z != -1)) {
            lore.add(ChatColor.GRAY + "Location: " + ChatColor.AQUA
                    + entry.x + ", " + entry.y + ", " + entry.z
                    + ChatColor.GRAY + " (" + entry.world + ")");
        }
        lore.add(ChatColor.GRAY + "When: " + ChatColor.YELLOW + formatAgo(now - entry.timestamp));
        lore.add(ChatColor.DARK_GRAY + TS.format(new Date(entry.timestamp)));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return new GuiItem(item, e -> e.setCancelled(true));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Material iconForType(String type) {
        return switch (type) {
            case "claim" -> Material.GREEN_WOOL;
            case "unclaim" -> Material.RED_WOOL;
            case "promote" -> Material.GOLDEN_HELMET;
            case "demote" -> Material.LEATHER_HELMET;
            case "leader" -> Material.GOLDEN_HELMET;
            case "invite" -> Material.PAPER;
            case "deinvite" -> Material.MAP;
            case "join" -> Material.LIME_DYE;
            case "leave" -> Material.GRAY_DYE;
            case "bank-deposit" -> Material.GOLD_INGOT;
            case "bank-withdraw" -> Material.GOLD_NUGGET;
            case "tnt-deposit" -> Material.TNT;
            case "tnt-withdraw" -> Material.GUNPOWDER;
            case "warp-set" -> Material.ENDER_EYE;
            case "warp-delete" -> Material.ENDER_PEARL;
            case "sethome" -> Material.RED_BED;
            case "rename" -> Material.NAME_TAG;
            case "relation" -> Material.OAK_SIGN;
            case "spawner-place" -> Material.SPAWNER;
            case "spawner-break" -> Material.BUDDING_AMETHYST;
            default -> Material.PAPER;
        };
    }

    private static String colorChatFor(String type) {
        return switch (type) {
            case "claim", "warp-set", "sethome", "join", "bank-deposit", "tnt-deposit", "spawner-place" -> ChatColor.GREEN.toString();
            case "unclaim", "warp-delete", "leave", "spawner-break" -> ChatColor.RED.toString();
            case "promote", "leader" -> ChatColor.AQUA.toString();
            case "demote" -> ChatColor.YELLOW.toString();
            case "bank-withdraw", "tnt-withdraw" -> ChatColor.GOLD.toString();
            case "invite", "rename" -> ChatColor.LIGHT_PURPLE.toString();
            case "deinvite" -> ChatColor.YELLOW.toString();
            case "relation" -> ChatColor.DARK_PURPLE.toString();
            default -> ChatColor.WHITE.toString();
        };
    }

    private static String formatAgo(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000;
        if (s < 60) return s + "s ago";
        long m = s / 60;
        if (m < 60) return m + "m ago";
        long h = m / 60;
        if (h < 24) return h + "h ago";
        long d = h / 24;
        return d + "d ago";
    }

    private static ItemStack filler() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(" ");
        item.setItemMeta(m);
        return item;
    }
}
