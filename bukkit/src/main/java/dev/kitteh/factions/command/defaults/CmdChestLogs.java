package dev.kitteh.factions.command.defaults;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import dev.kitteh.factions.Faction;
import dev.kitteh.factions.audit.ChestLogs;
import dev.kitteh.factions.command.Cloudy;
import dev.kitteh.factions.command.Cmd;
import dev.kitteh.factions.command.Sender;
import dev.kitteh.factions.command.FactionParser;
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
import java.util.List;

/**
 * /f chestlogs &lt;faction&gt; — staff-only view of who added and removed items from a faction's
 * vault. Op-gated: faction members, including the faction's own admin, cannot see this.
 *
 * @see ChestLogs
 */
public class CmdChestLogs implements Cmd {

    private static final SimpleDateFormat TS = new SimpleDateFormat("MM/dd HH:mm");
    private static final int PER_PAGE = 45; // 5 rows × 9, leaving the bottom row for navigation

    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        return (manager, builder, help) -> manager.command(
                builder.literal("chestlogs", "chestlog", "pvlogs")
                        .commandDescription(Cloudy.desc("View a faction's chest history (staff only)"))
                        .permission(builder.commandPermission()
                                .and(Cloudy.predicate(sender -> sender.sender().isOp())))
                        .required("faction", FactionParser.of())
                        .handler(this::handle)
        );
    }

    private void handle(CommandContext<Sender> context) {
        if (!(context.sender() instanceof Sender.Player playerSender)) {
            context.sender().sendRichMessage("<red>This command can only be run by a player.");
            return;
        }
        // Belt and braces: the permission predicate already gates this, but this data must never leak.
        Player player = playerSender.player();
        if (!player.isOp()) {
            playerSender.sendRichMessage("<red>You do not have permission to use this command.");
            return;
        }

        Faction faction = context.get("faction");
        open(player, faction, 1);
    }

    private void open(Player player, Faction faction, int page) {
        List<ChestLogs.ChestLogEntry> source = ChestLogs.forFaction(faction.id());

        // Newest first.
        List<ChestLogs.ChestLogEntry> entries = new ArrayList<>(source);
        entries.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));

        int total = entries.size();
        int totalPages = Math.max(1, (total + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(1, Math.min(page, totalPages));

        Component title = Component.text("Chest Logs: " + faction.tag() + " (" + page + "/" + totalPages + ")",
                NamedTextColor.LIGHT_PURPLE);
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

        int start = (page - 1) * PER_PAGE;
        int end = Math.min(start + PER_PAGE, total);
        long now = System.currentTimeMillis();
        for (int i = start; i < end; i++) {
            int rel = i - start;
            pane.addItem(buildEntryItem(entries.get(i), now), rel % 9, rel / 9);
        }

        if (page > 1) {
            int target = page - 1;
            pane.addItem(nav(Material.ARROW, ChatColor.YELLOW + "← Page " + target,
                    e -> open(player, faction, target)), 3, 5);
        }

        ItemStack info = new ItemStack(Material.CHEST);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + faction.tag() + " — Chest Log");
        List<String> il = new ArrayList<>();
        il.add(ChatColor.GRAY + "Page " + ChatColor.YELLOW + page + ChatColor.GRAY + " of " + ChatColor.YELLOW + totalPages);
        il.add(ChatColor.GRAY + "Entries: " + ChatColor.YELLOW + total);
        il.add(" ");
        il.add(ChatColor.DARK_GRAY + "History is kept for " + ChestLogs.RETENTION_DAYS + " days.");
        im.setLore(il);
        info.setItemMeta(im);
        pane.addItem(new GuiItem(info, e -> e.setCancelled(true)), 4, 5);

        if (page < totalPages) {
            int target = page + 1;
            pane.addItem(nav(Material.ARROW, ChatColor.YELLOW + "Page " + target + " →",
                    e -> open(player, faction, target)), 5, 5);
        }

        if (total == 0) {
            ItemStack empty = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta em = empty.getItemMeta();
            em.setDisplayName(ChatColor.GRAY + "No chest activity recorded");
            List<String> el = new ArrayList<>();
            el.add(ChatColor.DARK_GRAY + "Nothing in the last " + ChestLogs.RETENTION_DAYS + " days.");
            em.setLore(el);
            empty.setItemMeta(em);
            pane.addItem(new GuiItem(empty, e -> e.setCancelled(true)), 4, 2);
        }

        gui.addPane(pane);
        gui.show(player);
    }

    private GuiItem buildEntryItem(ChestLogs.ChestLogEntry entry, long now) {
        // Show the actual item where we can, so staff see the thing that moved.
        Material icon = Material.matchMaterial(entry.material);
        if (icon == null || !icon.isItem()) {
            icon = Material.PAPER;
        }

        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();

        String verb = entry.added ? ChatColor.GREEN + "+ Added" : ChatColor.RED + "- Removed";
        meta.setDisplayName(verb + " " + ChatColor.WHITE + entry.amount + "x "
                + ChatColor.YELLOW + prettify(entry.material));

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Player: " + ChatColor.AQUA + entry.actorName);
        if (entry.itemName != null && !entry.itemName.isEmpty()) {
            lore.add(ChatColor.GRAY + "Item name: " + ChatColor.WHITE + entry.itemName);
        }
        lore.add(ChatColor.GRAY + "When: " + ChatColor.YELLOW + formatAgo(now - entry.timestamp));
        lore.add(ChatColor.DARK_GRAY + TS.format(new Date(entry.timestamp)));
        meta.setLore(lore);
        item.setItemMeta(meta);

        return new GuiItem(item, e -> e.setCancelled(true));
    }

    private static GuiItem nav(Material material, String name, java.util.function.Consumer<org.bukkit.event.inventory.InventoryClickEvent> action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return new GuiItem(item, e -> {
            e.setCancelled(true);
            action.accept(e);
        });
    }

    private static String prettify(String material) {
        String[] parts = material.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private static String formatAgo(long ms) {
        if (ms < 0) {
            ms = 0;
        }
        long s = ms / 1000;
        if (s < 60) {
            return s + "s ago";
        }
        long m = s / 60;
        if (m < 60) {
            return m + "m ago";
        }
        long h = m / 60;
        if (h < 24) {
            return h + "h ago";
        }
        return (h / 24) + "d ago";
    }

    private static ItemStack filler() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(" ");
        item.setItemMeta(m);
        return item;
    }
}
