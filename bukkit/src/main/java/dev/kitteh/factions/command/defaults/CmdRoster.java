package dev.kitteh.factions.command.defaults;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import dev.kitteh.factions.FPlayer;
import dev.kitteh.factions.FPlayers;
import dev.kitteh.factions.Faction;
import dev.kitteh.factions.FactionsPlugin;
import dev.kitteh.factions.command.Cloudy;
import dev.kitteh.factions.command.Cmd;
import dev.kitteh.factions.command.FPlayerParser;
import dev.kitteh.factions.command.Sender;
import dev.kitteh.factions.permissible.Role;
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
import org.incendo.cloud.parser.standard.EnumParser;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /f roster &lt;add|remove|list&gt; — pre-approved member roster with per-entry role.
 * - Officer+ can add/remove and change roles.
 * - Anyone in the faction can list.
 * - Capped at FactionLevelsConfig.rosterMaxSize.
 */
public class CmdRoster implements Cmd {

    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        return (manager, builder, help) -> {
            Command.Builder<Sender> base = builder.literal("roster")
                    .commandDescription(Cloudy.desc("Manage your faction's pre-approved roster"))
                    .permission(builder.commandPermission().and(Cloudy.hasFaction()));

            // /f roster add <player> [role] — Officer+. Default role NORMAL (member).
            manager.command(
                    base.literal("add")
                            .permission(base.commandPermission().and(Cloudy.isAtLeastRole(Role.MODERATOR)))
                            .required("player", FPlayerParser.of(FPlayerParser.Include.NO_FACTION, FPlayerParser.Include.OTHER_FACTION, FPlayerParser.Include.SAME_FACTION))
                            .optional("role", EnumParser.enumParser(Role.class))
                            .handler(this::handleAdd)
            );

            // /f roster remove <player> — Officer+
            manager.command(
                    base.literal("remove")
                            .permission(base.commandPermission().and(Cloudy.isAtLeastRole(Role.MODERATOR)))
                            .required("player", FPlayerParser.of(FPlayerParser.Include.NO_FACTION, FPlayerParser.Include.OTHER_FACTION, FPlayerParser.Include.SAME_FACTION))
                            .handler(this::handleRemove)
            );

            // /f roster list — anyone in the faction (opens GUI for players, chat list for console)
            manager.command(
                    base.literal("list")
                            .handler(this::handleList)
            );

            // Bare /f roster -> /f roster list
            manager.command(base.handler(this::handleList));
        };
    }

    private void handleAdd(CommandContext<Sender> context) {
        FPlayer sender = ((Sender.Player) context.sender()).fPlayer();
        FPlayer target = context.get("player");
        Faction faction = sender.faction();

        Role role = context.<Role>getOrDefault("role", Role.NORMAL);
        if (role == Role.ADMIN) {
            sender.sendRichMessage("<red>Cannot pre-roster a player as Leader. Choose Co-Leader, Officer, Member, or Recruit.");
            return;
        }

        int max = FactionsPlugin.instance().configManager().factionLevelsConfig().getRosterMaxSize();
        boolean alreadyOn = faction.isOnRoster(target.uniqueId());

        if (!alreadyOn && faction.roster().size() >= max) {
            sender.sendRichMessage("<red>The roster is full (" + faction.roster().size() + "/" + max + "). Remove someone before adding more.");
            return;
        }

        if (!faction.rosterAdd(target.uniqueId(), role)) {
            sender.sendRichMessage("<red>Failed to add " + target.name() + " to the roster.");
            return;
        }

        faction.recordFactionLog(dev.kitteh.factions.audit.AuditEntry.generic(
                sender.name(), sender.uniqueId(), alreadyOn ? "roster-update" : "roster-add",
                sender.name() + (alreadyOn ? " set " : " added ") + target.name() + " as " + roleLabel(role) + " on the roster"
        ));

        if (alreadyOn) {
            sender.sendRichMessage("<green>Updated <yellow>" + target.name() + "</yellow>'s roster role to <gold>" + roleLabel(role) + "</gold>.");
        } else {
            sender.sendRichMessage("<green>Added <yellow>" + target.name() + "</yellow> as <gold>" + roleLabel(role)
                    + "</gold> on the roster (" + faction.roster().size() + "/" + max + ").");
        }

        // Notify target if online
        Player targetPlayer = target.asPlayer();
        if (targetPlayer != null) {
            target.sendRichMessage("<gold>You've been added to <yellow>" + faction.tag()
                    + "</yellow>'s roster as <gold>" + roleLabel(role) + "</gold>. Run <white>/f join " + faction.tag()
                    + "</white> when there's a slot available.");
        }
    }

    private void handleRemove(CommandContext<Sender> context) {
        FPlayer sender = ((Sender.Player) context.sender()).fPlayer();
        FPlayer target = context.get("player");
        Faction faction = sender.faction();

        if (!faction.isOnRoster(target.uniqueId())) {
            sender.sendRichMessage("<red>" + target.name() + " is not on the roster.");
            return;
        }
        if (faction.rosterRemove(target.uniqueId())) {
            faction.recordFactionLog(dev.kitteh.factions.audit.AuditEntry.generic(
                    sender.name(), sender.uniqueId(), "roster-remove",
                    sender.name() + " removed " + target.name() + " from the roster"
            ));
            sender.sendRichMessage("<green>Removed <yellow>" + target.name() + "</yellow> from the roster.");
        }
    }

    private void handleList(CommandContext<Sender> context) {
        if (!(context.sender() instanceof Sender.Player playerSender)) {
            FPlayer fp = context.sender().fPlayerOrNull();
            if (fp == null || !fp.hasFaction()) {
                context.sender().sendRichMessage("<red>Console must specify a faction context.");
                return;
            }
            chatRoster(fp.faction(), context.sender());
            return;
        }
        openRosterGui(playerSender.player(), playerSender.fPlayer().faction(),
                playerSender.fPlayer().role().value >= Role.MODERATOR.value);
    }

    private void chatRoster(Faction faction, Sender sender) {
        int max = FactionsPlugin.instance().configManager().factionLevelsConfig().getRosterMaxSize();
        sender.sendRichMessage("<gold><bold>✦ " + faction.tag() + " Roster</bold></gold> <gray>(" + faction.roster().size() + "/" + max + ")");

        // Build entries with the leader always pinned at the top.
        java.util.LinkedHashMap<UUID, Role> rows = new java.util.LinkedHashMap<>();
        FPlayer leader = faction.admin();
        if (leader != null) {
            rows.put(leader.uniqueId(), Role.ADMIN);
        }
        for (java.util.Map.Entry<UUID, Role> entry : faction.rosterRoles().entrySet()) {
            rows.putIfAbsent(entry.getKey(), entry.getValue());
        }
        if (rows.isEmpty()) {
            sender.sendRichMessage("<gray>(empty)");
            return;
        }
        for (java.util.Map.Entry<UUID, Role> entry : rows.entrySet()) {
            UUID uuid = entry.getKey();
            Role role = entry.getValue();
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            String name = op.getName() != null ? op.getName() : uuid.toString().substring(0, 8);
            FPlayer fp = FPlayers.fPlayers().get(uuid);
            String status;
            if (fp != null && fp.hasFaction() && fp.faction() == faction) {
                status = "<green>(in faction)";
            } else if (fp != null && fp.hasFaction()) {
                status = "<red>(in another faction: " + fp.faction().tag() + ")";
            } else {
                status = "<yellow>(can join)";
            }
            sender.sendRichMessage("<gray>• <white>" + name + "</white> <gold>[" + roleLabel(role) + "]</gold> "
                    + "<gray>· playtime: <white>" + formatPlaytime(op) + "</white> " + status);
        }
    }

    // ── Roster GUI ────────────────────────────────────────────────────────────

    private void openRosterGui(Player player, Faction faction, boolean canManage) {
        int max = FactionsPlugin.instance().configManager().factionLevelsConfig().getRosterMaxSize();
        Component title = Component.text("✦ " + faction.tag() + " Roster — " + faction.roster().size() + "/" + max,
                NamedTextColor.GOLD);
        ChestGui gui = new ChestGui(6, ComponentHolder.of(title));
        gui.setOnGlobalDrag(e -> e.setCancelled(true));
        gui.setOnGlobalClick(e -> e.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 6);
        ItemStack filler = makeFiller(Material.GRAY_STAINED_GLASS_PANE);
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 6; y++) {
                pane.addItem(new GuiItem(filler), x, y);
            }
        }

        // Pack roster entries from slot (0,0)
        List<java.util.Map.Entry<UUID, Role>> entries = new ArrayList<>(faction.rosterRoles().entrySet());
        entries.sort((a, b) -> {
            FPlayer fa = FPlayers.fPlayers().get(a.getKey());
            FPlayer fb = FPlayers.fPlayers().get(b.getKey());
            boolean ainf = fa != null && fa.hasFaction() && fa.faction() == faction;
            boolean binf = fb != null && fb.hasFaction() && fb.faction() == faction;
            if (ainf != binf) return ainf ? -1 : 1;
            String an = Bukkit.getOfflinePlayer(a.getKey()).getName();
            String bn = Bukkit.getOfflinePlayer(b.getKey()).getName();
            return java.util.Objects.compare(an, bn, java.util.Comparator.nullsLast(String::compareToIgnoreCase));
        });

        // Always show the faction leader first, even if they were never /f roster add-ed.
        FPlayer leader = faction.admin();
        if (leader != null) {
            UUID leaderUuid = leader.uniqueId();
            // Avoid a duplicate if somehow the leader is also on the roster map.
            entries.removeIf(e -> e.getKey().equals(leaderUuid));
            entries.add(0, java.util.Map.entry(leaderUuid, Role.ADMIN));
        }

        int slotsPerRow = 9;
        int contentRows = 5;
        int maxSlots = slotsPerRow * contentRows;
        int slotIdx = 0;
        for (java.util.Map.Entry<UUID, Role> entry : entries) {
            if (slotIdx >= maxSlots) break;
            UUID uuid = entry.getKey();
            Role role = entry.getValue();
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            String name = op.getName() != null ? op.getName() : uuid.toString().substring(0, 8);
            FPlayer fp = FPlayers.fPlayers().get(uuid);
            boolean inThisFaction = fp != null && fp.hasFaction() && fp.faction() == faction;
            boolean inOtherFaction = fp != null && fp.hasFaction() && fp.faction() != faction;
            // The leader is shown synthetically and is not on the roster map — it can't be
            // re-roled or removed via the GUI.
            boolean isLeader = role == Role.ADMIN && !faction.isOnRoster(uuid);
            boolean manageable = canManage && !isLeader;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = head.getItemMeta();
            String prefix;
            if (inThisFaction) prefix = ChatColor.GREEN + "" + ChatColor.BOLD;
            else if (inOtherFaction) prefix = ChatColor.RED + "" + ChatColor.BOLD;
            else prefix = ChatColor.YELLOW + "" + ChatColor.BOLD;
            meta.setDisplayName(prefix + name);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GOLD + "Role: " + ChatColor.WHITE + roleLabel(role));
            lore.add(ChatColor.GRAY + "Playtime: " + ChatColor.WHITE + formatPlaytime(op));
            if (isLeader) {
                lore.add(ChatColor.GOLD + "★ Faction Leader");
            } else if (inThisFaction) {
                lore.add(ChatColor.GREEN + "✓ Currently in your faction");
            } else if (inOtherFaction) {
                lore.add(ChatColor.RED + "✗ In another faction: " + fp.faction().tag());
            } else {
                lore.add(ChatColor.YELLOW + "○ Can /f join when there's a slot");
            }
            if (manageable) {
                lore.add(" ");
                lore.add(ChatColor.GREEN + "▶ Left-click " + ChatColor.GRAY + "to change role");
                lore.add(ChatColor.RED + "▶ Right-click " + ChatColor.GRAY + "to remove from roster");
            }
            meta.setLore(lore);
            if (meta instanceof SkullMeta sm) {
                sm.setOwningPlayer(op);
            }
            head.setItemMeta(meta);

            final UUID finalUuid = uuid;
            final String finalName = name;
            GuiItem gi = new GuiItem(head);
            if (manageable) {
                gi.setAction(e -> {
                    e.setCancelled(true);
                    if (e.isRightClick()) {
                        openRemoveConfirmGui(player, faction, finalUuid, finalName);
                    } else if (e.isLeftClick()) {
                        openRolePickerGui(player, faction, finalUuid, finalName);
                    }
                });
            } else {
                gi.setAction(e -> e.setCancelled(true));
            }
            int col = slotIdx % slotsPerRow;
            int row = slotIdx / slotsPerRow;
            pane.addItem(gi, col, row);
            slotIdx++;
        }

        // Footer info
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName(ChatColor.GOLD + "Roster Info");
        List<String> il = new ArrayList<>();
        il.add(ChatColor.GRAY + "Slots: " + ChatColor.WHITE + faction.roster().size() + "/" + max);
        il.add(" ");
        il.add(ChatColor.GREEN + "✓ " + ChatColor.GRAY + "= currently in faction");
        il.add(ChatColor.YELLOW + "○ " + ChatColor.GRAY + "= can join when slot opens");
        il.add(ChatColor.RED + "✗ " + ChatColor.GRAY + "= already in another faction");
        if (canManage) {
            il.add(" ");
            il.add(ChatColor.GRAY + "Use " + ChatColor.WHITE + "/f roster add <player> [role]" + ChatColor.GRAY + " to add.");
            il.add(ChatColor.GRAY + "Left-click a head to change role.");
            il.add(ChatColor.GRAY + "Right-click a head to remove.");
        }
        im.setLore(il);
        info.setItemMeta(im);
        pane.addItem(new GuiItem(info), 4, 5);

        gui.addPane(pane);
        gui.show(player);
    }

    // ── Role picker submenu ──────────────────────────────────────────────────

    private void openRolePickerGui(Player player, Faction faction, UUID targetUuid, String targetName) {
        Component title = Component.text("Set role for " + targetName, NamedTextColor.GOLD);
        ChestGui gui = new ChestGui(3, ComponentHolder.of(title));
        gui.setOnGlobalDrag(e -> e.setCancelled(true));
        gui.setOnGlobalClick(e -> e.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 3);
        ItemStack filler = makeFiller(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 3; y++) {
                pane.addItem(new GuiItem(filler), x, y);
            }
        }

        // 4 roles: Co-Leader / Officer / Member / Recruit (no Leader)
        Role[] roles = { Role.COLEADER, Role.MODERATOR, Role.NORMAL, Role.RECRUIT };
        Material[] mats = { Material.GOLDEN_HELMET, Material.IRON_HELMET, Material.CHAINMAIL_HELMET, Material.LEATHER_HELMET };
        int[] cols = { 1, 3, 5, 7 };

        Role current = faction.rosterRoleOf(targetUuid);
        for (int i = 0; i < roles.length; i++) {
            Role role = roles[i];
            ItemStack item = new ItemStack(mats[i]);
            ItemMeta meta = item.getItemMeta();
            boolean isCurrent = role == current;
            meta.setDisplayName((isCurrent ? ChatColor.GREEN : ChatColor.WHITE) + "" + ChatColor.BOLD + roleLabel(role));
            List<String> lore = new ArrayList<>();
            if (isCurrent) lore.add(ChatColor.GREEN + "✓ Current role");
            lore.add(ChatColor.GRAY + "Click to set " + ChatColor.WHITE + targetName + ChatColor.GRAY + " as");
            lore.add(ChatColor.GRAY + "  " + ChatColor.GOLD + roleLabel(role) + ChatColor.GRAY + " on the roster.");
            meta.setLore(lore);
            item.setItemMeta(meta);

            final Role finalRole = role;
            GuiItem gi = new GuiItem(item);
            gi.setAction(e -> {
                e.setCancelled(true);
                if (faction.rosterSetRole(targetUuid, finalRole)) {
                    FPlayer fpClicker = FPlayers.fPlayers().get(player);
                    faction.recordFactionLog(dev.kitteh.factions.audit.AuditEntry.generic(
                            fpClicker.name(), fpClicker.uniqueId(), "roster-update",
                            fpClicker.name() + " set " + targetName + "'s roster role to " + roleLabel(finalRole)
                    ));
                    player.sendMessage(ChatColor.GREEN + "Set " + targetName + "'s roster role to " + roleLabel(finalRole) + ".");
                }
                openRosterGui(player, faction, true);
            });
            pane.addItem(gi, cols[i], 1);
        }

        // Back
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName(ChatColor.GRAY + "← Back");
        back.setItemMeta(bm);
        GuiItem backItem = new GuiItem(back);
        backItem.setAction(e -> {
            e.setCancelled(true);
            openRosterGui(player, faction, true);
        });
        pane.addItem(backItem, 0, 2);

        gui.addPane(pane);
        gui.show(player);
    }

    // ── Remove confirmation submenu ──────────────────────────────────────────

    private void openRemoveConfirmGui(Player player, Faction faction, UUID targetUuid, String targetName) {
        Component title = Component.text("Remove " + targetName + " from roster?", NamedTextColor.RED);
        ChestGui gui = new ChestGui(3, ComponentHolder.of(title));
        gui.setOnGlobalDrag(e -> e.setCancelled(true));
        gui.setOnGlobalClick(e -> e.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 3);
        ItemStack filler = makeFiller(Material.RED_STAINED_GLASS_PANE);
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 3; y++) {
                pane.addItem(new GuiItem(filler), x, y);
            }
        }

        // Confirm (green concrete)
        ItemStack yes = new ItemStack(Material.GREEN_CONCRETE);
        ItemMeta ym = yes.getItemMeta();
        ym.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "Yes, remove");
        List<String> yl = new ArrayList<>();
        yl.add(ChatColor.GRAY + "Removes " + ChatColor.WHITE + targetName + ChatColor.GRAY + " from the roster.");
        ym.setLore(yl);
        yes.setItemMeta(ym);
        GuiItem yesItem = new GuiItem(yes);
        yesItem.setAction(e -> {
            e.setCancelled(true);
            if (faction.rosterRemove(targetUuid)) {
                FPlayer fpClicker = FPlayers.fPlayers().get(player);
                faction.recordFactionLog(dev.kitteh.factions.audit.AuditEntry.generic(
                        fpClicker.name(), fpClicker.uniqueId(), "roster-remove",
                        fpClicker.name() + " removed " + targetName + " from the roster (via GUI)"
                ));
                player.sendMessage(ChatColor.GREEN + "Removed " + targetName + " from the roster.");
            }
            openRosterGui(player, faction, true);
        });
        pane.addItem(yesItem, 2, 1);

        // Cancel (red concrete)
        ItemStack no = new ItemStack(Material.RED_CONCRETE);
        ItemMeta nm = no.getItemMeta();
        nm.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Cancel");
        no.setItemMeta(nm);
        GuiItem noItem = new GuiItem(no);
        noItem.setAction(e -> {
            e.setCancelled(true);
            openRosterGui(player, faction, true);
        });
        pane.addItem(noItem, 6, 1);

        // Subject head in middle for clarity
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta hm = head.getItemMeta();
        hm.setDisplayName(ChatColor.WHITE + "" + ChatColor.BOLD + targetName);
        List<String> hl = new ArrayList<>();
        Role role = faction.rosterRoleOf(targetUuid);
        if (role != null) hl.add(ChatColor.GRAY + "Role: " + ChatColor.GOLD + roleLabel(role));
        hl.add(ChatColor.GRAY + "Confirm removal from roster.");
        hm.setLore(hl);
        if (hm instanceof SkullMeta sm) {
            sm.setOwningPlayer(Bukkit.getOfflinePlayer(targetUuid));
        }
        head.setItemMeta(hm);
        pane.addItem(new GuiItem(head), 4, 1);

        gui.addPane(pane);
        gui.show(player);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Format a player's total playtime as a short human-readable string.
     * Uses Bukkit's PLAY_ONE_MINUTE statistic (which is actually counted in ticks: 20 ticks = 1 second).
     * Falls back to "?" if the stat is unavailable (Spigot offline-player stats are best-effort).
     */
    private static String formatPlaytime(OfflinePlayer op) {
        try {
            int ticks = op.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE);
            long seconds = ticks / 20L;
            if (seconds <= 0) return "0m";
            long days = seconds / 86_400;
            long hours = (seconds % 86_400) / 3_600;
            long minutes = (seconds % 3_600) / 60;
            if (days > 0) return days + "d " + hours + "h";
            if (hours > 0) return hours + "h " + minutes + "m";
            return minutes + "m";
        } catch (Throwable t) {
            return "?";
        }
    }

    private static String roleLabel(Role role) {
        if (role == null) return "Member";
        return switch (role) {
            case ADMIN -> "Leader";
            case COLEADER -> "Co-Leader";
            case MODERATOR -> "Officer";
            case NORMAL -> "Member";
            case RECRUIT -> "Recruit";
        };
    }

    private static ItemStack makeFiller(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(" ");
        item.setItemMeta(m);
        return item;
    }
}
