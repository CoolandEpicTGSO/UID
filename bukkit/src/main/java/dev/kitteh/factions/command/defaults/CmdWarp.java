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
import dev.kitteh.factions.command.FactionParser;
import dev.kitteh.factions.command.Sender;
import dev.kitteh.factions.event.FPlayerTeleportEvent;
import dev.kitteh.factions.level.FactionLevelsConfig;
import dev.kitteh.factions.permissible.PermissibleActions;
import dev.kitteh.factions.permissible.Role;
import dev.kitteh.factions.plugin.AbstractFactionsPlugin;
import dev.kitteh.factions.tagresolver.FactionResolver;
import dev.kitteh.factions.util.LazyLocation;
import dev.kitteh.factions.util.Permission;
import dev.kitteh.factions.util.TeleportCountdown;
import dev.kitteh.factions.util.TriConsumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;
import org.incendo.cloud.parser.standard.StringParser;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

public class CmdWarp implements Cmd {
    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        return (manager, builder, help) -> {
            var tl = FactionsPlugin.instance().tl().commands().warp();
            manager.command(
                    builder.literal(tl.getFirstAlias(), tl.getSecondaryAliases())
                            .commandDescription(Cloudy.desc(tl.getDescription()))
                            .permission(builder.commandPermission().and(Cloudy.hasPermission(Permission.WARP).and(Cloudy.hasSelfFactionPerms(PermissibleActions.WARP).or(Cloudy.isBypass()))))
                            .optional("warp", StringParser.stringParser())
                            .flag(manager.flagBuilder("password").withComponent(StringParser.stringParser()))
                            .flag(manager.flagBuilder("faction").withComponent(FactionParser.of()))
                            .handler(ctx -> handle(ctx, this::menu))
            );
        };
    }

    public static void handle(CommandContext<Sender> context, BiConsumer<Sender, Faction> consumer) {
        FPlayer sender = ((Sender.Player) context.sender()).fPlayer();
        Faction faction = context.flags().get("faction") instanceof Faction fac ? fac : sender.faction();
        var tl = FactionsPlugin.instance().tl().commands().warp();

        String warpName = context.getOrDefault("warp", null);

        // Permission gate: per-warp role override (if set) takes priority over the default WARP perm.
        // Showing the menu (no warpName) still uses the default WARP perm.
        if (warpName == null) {
            if (!context.sender().isBypass() && !faction.hasAccess(sender, PermissibleActions.WARP, sender.lastStoodAt())) {
                sender.sendRichMessage(tl.getNoPermission(), FactionResolver.of(faction));
                return;
            }
        } else {
            if (!context.sender().isBypass() && !playerCanUseWarp(faction, sender, warpName)) {
                sender.sendRichMessage(tl.getNoPermission(), FactionResolver.of(faction));
                return;
            }
        }

        if (warpName == null) {
            if (faction.warps().isEmpty()) {
                context.sender().sendRichMessage(tl.getNoWarps(), FactionResolver.of( faction));
            } else {
                consumer.accept(context.sender(), faction);
            }
        } else {
            final String passwordAttempt = context.flags().get("password") instanceof String s ? s : "";

            LazyLocation destination = faction.warp(warpName);
            if (destination != null) {
                if (!sender.adminBypass() && faction.hasWarpPassword(warpName) && !faction.isWarpPassword(warpName, passwordAttempt)) {
                    sender.sendRichMessage(tl.getInvalidPassword());
                    return;
                }

                teleport(sender, faction, warpName, context.sender(), destination);
            } else {
                sender.sendRichMessage(tl.getInvalidWarp(), Placeholder.unparsed("warp", warpName));
            }
        }
    }

    public static void teleport(FPlayer sender, Faction faction, String warpName, Sender commandSender, LazyLocation destination) {
        var tl = FactionsPlugin.instance().tl().commands().warp();

        FPlayerTeleportEvent tpEvent = new FPlayerTeleportEvent(sender, destination.asLocation(), FPlayerTeleportEvent.Reason.WARP);
        Bukkit.getServer().getPluginManager().callEvent(tpEvent);
        if (tpEvent.isCancelled()) {
            return;
        }
        // Check transaction AFTER password check.
        if (!transact(sender, commandSender)) {
            return;
        }
        final FPlayer fPlayer = sender;
        final UUID uuid = sender.uniqueId();

        FactionLevelsConfig levelCfg = FactionsPlugin.instance().configManager().factionLevelsConfig();
        int warpTimeTier = sender.faction().customUpgradeTier(CmdUpgrades.KEY_WARP_TIME);
        int baseDelay = levelCfg.getBaseWarpDelaySeconds();
        int reduction = warpTimeTier * levelCfg.getWarpDelayReductionPerTier();
        int delay = Math.max(0, baseDelay - reduction);
        Player playerNow = Bukkit.getPlayer(uuid);
        if (playerNow == null) return;
        TeleportCountdown.start(playerNow, delay, "Warping to " + warpName, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (destination == faction.warp(warpName) && player != null) {
                AbstractFactionsPlugin.instance().teleport(player, destination.asLocation()).thenAccept(success -> {
                    if (success) {
                        fPlayer.sendRichMessage(tl.getWarped(), Placeholder.unparsed("warp", warpName));
                    }
                });
            }
        });
    }

    private void menu(Sender sender, Faction faction) {
        if (!(sender instanceof Sender.Player sp)) {
            sender.sendRichMessage(String.join(", ", faction.warps().keySet()));
            return;
        }
        openWarpGui(sp.player(), sp.fPlayer(), faction);
    }

    private void openWarpGui(Player player, FPlayer fPlayer, Faction faction) {
        FactionLevelsConfig cfg = FactionsPlugin.instance().configManager().factionLevelsConfig();
        int warpTimeTier = faction.customUpgradeTier(CmdUpgrades.KEY_WARP_TIME);
        int delay = Math.max(0, cfg.getBaseWarpDelaySeconds() - warpTimeTier * cfg.getWarpDelayReductionPerTier());

        List<String> warpNames = new ArrayList<>(faction.warps().keySet());
        int rows = Math.max(3, Math.min(6, (int) Math.ceil(warpNames.size() / 7.0) + 2));

        Component title = Component.text("✦ Faction Warps", NamedTextColor.DARK_PURPLE);
        ChestGui gui = new ChestGui(rows, ComponentHolder.of(title));
        gui.setOnGlobalDrag(e -> e.setCancelled(true));
        gui.setOnGlobalClick(e -> e.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, rows);

        // Background
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gm = glass.getItemMeta();
        gm.setDisplayName(" ");
        glass.setItemMeta(gm);
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < rows; y++) {
                pane.addItem(new GuiItem(glass.clone()), x, y);
            }
        }

        // Warp items in a grid: cols 1,3,5,7 from row 1
        int col = 1;
        int row = 1;
        for (String warpName : warpNames) {
            LazyLocation loc = faction.warp(warpName);
            if (loc == null) continue;

            ItemStack warpItem = new ItemStack(Material.ENDER_PEARL);
            ItemMeta meta = warpItem.getItemMeta();
            meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + warpName);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "World: " + ChatColor.WHITE + loc.worldName());
            lore.add(ChatColor.GRAY + "X: " + ChatColor.WHITE + (int) loc.x()
                    + ChatColor.GRAY + "  Y: " + ChatColor.WHITE + (int) loc.y()
                    + ChatColor.GRAY + "  Z: " + ChatColor.WHITE + (int) loc.z());
            lore.add(" ");
            if (faction.hasWarpPassword(warpName)) {
                lore.add(ChatColor.YELLOW + "🔒 Password protected");
            }
            // Show current role override, if any
            java.util.Set<String> overrideRoles = faction.warpAllowedRoles(warpName);
            if (!overrideRoles.isEmpty()) {
                lore.add(ChatColor.GRAY + "Allowed roles: " + ChatColor.WHITE + String.join(", ", overrideRoles));
            } else {
                lore.add(ChatColor.DARK_GRAY + "Uses default faction perms");
            }
            lore.add(" ");
            lore.add(ChatColor.GREEN + "▶ Left-click to teleport  (" + delay + "s warmup)");
            // Only show right-click hint if the player can manage warps
            boolean canManage = faction.hasAccess(fPlayer, PermissibleActions.SETWARP, fPlayer.lastStoodAt());
            if (canManage) {
                lore.add(ChatColor.YELLOW + "▶ Right-click to edit role permissions");
            }
            meta.setLore(lore);
            warpItem.setItemMeta(meta);

            final String finalWarpName = warpName;
            GuiItem guiItem = new GuiItem(warpItem);
            guiItem.setAction(e -> {
                e.setCancelled(true);

                if (e.isRightClick()) {
                    if (!canManage) return; // silently ignore right-click for non-managers
                    openWarpPermsEditor(player, fPlayer, faction, finalWarpName);
                    return;
                }

                if (!e.isLeftClick()) return; // ignore middle-click etc.

                // Per-warp role override: if set, the player's role must be in the allow set.
                if (!playerCanUseWarp(faction, fPlayer, finalWarpName)) {
                    fPlayer.sendRichMessage("<red>Your role isn't allowed to use this warp.");
                    return;
                }

                if (faction.hasWarpPassword(finalWarpName)) {
                    fPlayer.sendRichMessage("<yellow>This warp is password-protected. Use <white>/f warp " + finalWarpName + " --password <yourpassword></white>");
                    player.closeInventory();
                    return;
                }

                player.closeInventory();
                LazyLocation dest = faction.warp(finalWarpName);
                if (dest != null) {
                    Sender cmdSender = new Sender.Player.Impl(player, player, fPlayer, faction);
                    teleport(fPlayer, faction, finalWarpName, cmdSender, dest);
                }
            });

            pane.addItem(guiItem, col, row);
            col += 2;
            if (col > 7) {
                col = 1;
                row++;
            }
        }

        // Info bar at bottom
        ItemStack infoItem = new ItemStack(Material.CLOCK);
        ItemMeta im = infoItem.getItemMeta();
        im.setDisplayName(ChatColor.AQUA + "Warp Delay: " + ChatColor.WHITE + delay + "s");
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.GRAY + "Warp Time tier: " + ChatColor.WHITE + warpTimeTier);
        infoLore.add(ChatColor.GRAY + "Upgrade with " + ChatColor.WHITE + "/f upgrades");
        im.setLore(infoLore);
        infoItem.setItemMeta(im);
        pane.addItem(new GuiItem(infoItem), 4, rows - 1);

        gui.addPane(pane);
        gui.show(player);
    }

    private static boolean transact(FPlayer player, Sender sender) {
        return player.adminBypass() || sender.payForCommand(FactionsPlugin.instance().conf().economy().getCostWarp(), FactionsPlugin.instance().tl().economy().actions().getWarpTo(), FactionsPlugin.instance().tl().economy().actions().getWarpFor());
    }

    /**
     * Returns true if the given player is allowed to use the given warp, considering both
     * the per-warp role override (if set) and the default WARP permissible action.
     */
    private static boolean playerCanUseWarp(Faction faction, FPlayer fPlayer, String warpName) {
        if (fPlayer.adminBypass()) return true;
        if (fPlayer.role() == Role.ADMIN) return true; // leader always

        java.util.Set<String> override = faction.warpAllowedRoles(warpName);
        if (!override.isEmpty()) {
            return override.contains(fPlayer.role().name().toLowerCase());
        }
        // No override → fall back to default WARP perm check
        return faction.hasAccess(fPlayer, PermissibleActions.WARP, fPlayer.lastStoodAt());
    }

    // ── Per-warp role-permission editor ───────────────────────────────────────

    private void openWarpPermsEditor(Player player, FPlayer fPlayer, Faction faction, String warpName) {
        Component title = Component.text("Warp: " + warpName, NamedTextColor.DARK_PURPLE);
        ChestGui gui = new ChestGui(3, ComponentHolder.of(title));
        gui.setOnGlobalDrag(e -> e.setCancelled(true));
        gui.setOnGlobalClick(e -> e.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 3);
        ItemStack filler = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.setDisplayName(" ");
        filler.setItemMeta(fm);
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 3; y++) {
                pane.addItem(new GuiItem(filler), x, y);
            }
        }

        // Header
        ItemStack header = new ItemStack(Material.BOOK);
        ItemMeta hm = header.getItemMeta();
        hm.setDisplayName(ChatColor.LIGHT_PURPLE + "Permissions for warp: " + ChatColor.WHITE + warpName);
        List<String> hl = new ArrayList<>();
        hl.add(ChatColor.GRAY + "Toggle which roles can use this warp.");
        hl.add(ChatColor.GRAY + "If no role is enabled, the default");
        hl.add(ChatColor.GRAY + "faction WARP permission is used.");
        hm.setLore(hl);
        header.setItemMeta(hm);
        pane.addItem(new GuiItem(header), 4, 0);

        // 5 role toggles in row 1, cols 2..6
        Role[] roles = { Role.ADMIN, Role.COLEADER, Role.MODERATOR, Role.NORMAL, Role.RECRUIT };
        for (int i = 0; i < roles.length; i++) {
            pane.addItem(buildRoleToggle(player, fPlayer, faction, warpName, roles[i]), i + 2, 1);
        }

        // Back button
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName(ChatColor.GRAY + "← Back");
        back.setItemMeta(bm);
        GuiItem backItem = new GuiItem(back);
        backItem.setAction(e -> {
            e.setCancelled(true);
            openWarpGui(player, fPlayer, faction);
        });
        pane.addItem(backItem, 0, 2);

        // Reset button — clears all overrides for this warp
        ItemStack reset = new ItemStack(Material.BARRIER);
        ItemMeta rm = reset.getItemMeta();
        rm.setDisplayName(ChatColor.RED + "Reset to default");
        List<String> rl = new ArrayList<>();
        rl.add(ChatColor.GRAY + "Clear all role overrides.");
        rl.add(ChatColor.GRAY + "Falls back to default WARP perm.");
        rm.setLore(rl);
        reset.setItemMeta(rm);
        GuiItem resetItem = new GuiItem(reset);
        resetItem.setAction(e -> {
            e.setCancelled(true);
            faction.warpAllowedRoles(warpName, java.util.Set.of());
            openWarpPermsEditor(player, fPlayer, faction, warpName);
        });
        pane.addItem(resetItem, 8, 2);

        gui.addPane(pane);
        gui.show(player);
    }

    private GuiItem buildRoleToggle(Player player, FPlayer fPlayer, Faction faction, String warpName, Role role) {
        java.util.Set<String> current = faction.warpAllowedRoles(warpName);
        boolean allowed = current.contains(role.name().toLowerCase());

        ItemStack item = new ItemStack(allowed ? Material.LIME_WOOL : Material.RED_WOOL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + "" + ChatColor.BOLD + roleLabel(role));
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Currently: " + (allowed ? ChatColor.GREEN + "Allowed" : ChatColor.RED + "Denied"));
        lore.add(" ");
        lore.add(ChatColor.YELLOW + "▶ Click to toggle");
        meta.setLore(lore);
        item.setItemMeta(meta);

        GuiItem gi = new GuiItem(item);
        gi.setAction(e -> {
            e.setCancelled(true);
            // Mutable copy of current set
            java.util.Set<String> next = new java.util.HashSet<>(current);
            String key = role.name().toLowerCase();
            if (allowed) next.remove(key); else next.add(key);
            faction.warpAllowedRoles(warpName, next);
            openWarpPermsEditor(player, fPlayer, faction, warpName);
        });
        return gi;
    }

    private static String roleLabel(Role role) {
        return switch (role) {
            case ADMIN -> "Leader";
            case COLEADER -> "Co-Leader";
            case MODERATOR -> "Officer";
            case NORMAL -> "Member";
            case RECRUIT -> "Recruit";
        };
    }
}
