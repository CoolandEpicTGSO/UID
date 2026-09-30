package dev.kitteh.factions.command.defaults;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import dev.kitteh.factions.FPlayer;
import dev.kitteh.factions.Faction;
import dev.kitteh.factions.command.Cloudy;
import dev.kitteh.factions.command.Cmd;
import dev.kitteh.factions.command.Sender;
import dev.kitteh.factions.permissible.PermSelector;
import dev.kitteh.factions.permissible.PermState;
import dev.kitteh.factions.permissible.PermissibleAction;
import dev.kitteh.factions.permissible.PermissibleActions;
import dev.kitteh.factions.permissible.Relation;
import dev.kitteh.factions.permissible.Role;
import dev.kitteh.factions.permissible.selector.RelationSingleSelector;
import dev.kitteh.factions.permissible.selector.RoleSingleSelector;
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

import java.util.ArrayList;
import java.util.List;

public class CmdPerms implements Cmd {

    /**
     * Ordered list of actions shown in the GUI. Matches the user's spec.
     */
    private static final List<PermissibleAction> SHOWN_ACTIONS = List.of(
            PermissibleActions.BUILD,
            PermissibleActions.DESTROY,
            PermissibleActions.DOOR,
            PermissibleActions.BUTTON,
            PermissibleActions.LEVER,
            PermissibleActions.CONTAINER,
            PermissibleActions.ITEM,
            PermissibleActions.TAG,
            PermissibleActions.DESCRIPTION,
            PermissibleActions.MOTD,
            PermissibleActions.STATUS,
            PermissibleActions.INVITE,
            PermissibleActions.KICK,
            PermissibleActions.BAN,
            PermissibleActions.PROMOTE,
            PermissibleActions.RELATION,
            PermissibleActions.TERRITORY,
            PermissibleActions.SETHOME,
            PermissibleActions.HOME,
            PermissibleActions.TITLE,
            PermissibleActions.PV,
            PermissibleActions.EFFECTS,
            PermissibleActions.SETWARP,
            PermissibleActions.WARP,
            PermissibleActions.UPGRADE,
            PermissibleActions.CHUNKITEMS,
            PermissibleActions.INSPECT,
            PermissibleActions.LOGS,
            PermissibleActions.ECONOMY,
            PermissibleActions.BANKDEPOSIT,
            PermissibleActions.TNTDEPOSIT,
            PermissibleActions.TNTWITHDRAW,
            PermissibleActions.MANAGEPERMS,
            PermissibleActions.FLY,
            PermissibleActions.SHIELD,
            PermissibleActions.ZONE
    );

    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        return (manager, builder, help) -> manager.command(
                builder.literal("perms", "permissions")
                        .commandDescription(Cloudy.desc("Open the faction permissions GUI"))
                        .permission(builder.commandPermission()
                                .and(Cloudy.hasPermission(Permission.MANAGE_PERMS))
                                .and(Cloudy.hasFaction()))
                        .handler(this::handle)
        );
    }

    private void handle(CommandContext<Sender> context) {
        FPlayer fPlayer = ((Sender.Player) context.sender()).fPlayer();
        Player player = ((Sender.Player) context.sender()).player();
        Faction faction = fPlayer.faction();

        // Only members with MANAGEPERMS permission within their own faction (admin always allowed via hasAccess)
        if (!faction.hasAccess(fPlayer, PermissibleActions.MANAGEPERMS, fPlayer.lastStoodAt())) {
            fPlayer.sendRichMessage("<red>You do not have permission to manage faction perms.");
            return;
        }

        openMainGrid(player, fPlayer);
    }

    // ── Main grid: all actions ────────────────────────────────────────────────

    private void openMainGrid(Player player, FPlayer fPlayer) {
        Faction faction = fPlayer.faction();
        Component title = Component.text("✦ Faction Perms", NamedTextColor.AQUA);
        ChestGui gui = new ChestGui(6, ComponentHolder.of(title));
        gui.setOnGlobalDrag(e -> e.setCancelled(true));
        gui.setOnGlobalClick(e -> e.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 6);

        // Glass-fill the whole pane first; action icons and the footer overwrite their slots.
        ItemStack glass = makeFiller();
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 6; y++) {
                pane.addItem(new GuiItem(glass.clone()), x, y);
            }
        }

        int col = 0;
        int row = 0;
        for (PermissibleAction action : SHOWN_ACTIONS) {
            pane.addItem(buildActionIcon(player, fPlayer, action), col, row);
            col++;
            if (col >= 9) {
                col = 0;
                row++;
            }
            if (row >= 5) break; // Last row reserved for footer
        }

        // Footer: info / close
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName(ChatColor.AQUA + "Faction Perms");
        List<String> il = new ArrayList<>();
        il.add(ChatColor.GRAY + "Click any icon to edit per-role / per-relation perms.");
        il.add(ChatColor.GRAY + "Faction: " + ChatColor.WHITE + faction.tag());
        im.setLore(il);
        info.setItemMeta(im);
        pane.addItem(new GuiItem(info), 4, 5);

        gui.addPane(pane);
        gui.show(player);
    }

    private GuiItem buildActionIcon(Player player, FPlayer fPlayer, PermissibleAction action) {
        Material mat = iconFor(action);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + niceName(action));

        Faction faction = fPlayer.faction();
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "" + ChatColor.ITALIC + "\"" + action.description() + "\"");
        lore.add(" ");
        lore.add(ChatColor.AQUA + "Permissions:");
        // Show effective (resolved) state — what actually applies — not the raw explicit state
        for (Role role : roleDisplayOrder()) {
            boolean allowed = effectiveStateForRole(faction, role, action);
            lore.add("  " + ChatColor.AQUA + "* " + roleLabel(role) + ": " + colorBool(allowed));
        }
        for (Relation rel : List.of(Relation.ALLY, Relation.TRUCE, Relation.NEUTRAL, Relation.ENEMY)) {
            boolean allowed = effectiveStateForRelation(faction, rel, action);
            lore.add("  " + ChatColor.AQUA + "* " + capitalize(rel.name()) + ": " + colorBool(allowed));
        }
        lore.add(" ");
        lore.add(ChatColor.AQUA + "(!) Click to modify " + niceName(action) + "!");
        meta.setLore(lore);
        item.setItemMeta(meta);

        GuiItem gi = new GuiItem(item);
        gi.setAction(e -> {
            e.setCancelled(true);
            openActionEditor(player, fPlayer, action);
        });
        return gi;
    }

    // ── Per-action editor: roles + relations ──────────────────────────────────

    private void openActionEditor(Player player, FPlayer fPlayer, PermissibleAction action) {
        Component title = Component.text("Edit: " + niceName(action), NamedTextColor.AQUA);
        ChestGui gui = new ChestGui(4, ComponentHolder.of(title));
        gui.setOnGlobalDrag(e -> e.setCancelled(true));
        gui.setOnGlobalClick(e -> e.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 4);
        ItemStack filler = makeFiller();
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 4; y++) {
                pane.addItem(new GuiItem(filler), x, y);
            }
        }

        // Header
        ItemStack header = new ItemStack(iconFor(action));
        ItemMeta hm = header.getItemMeta();
        hm.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + niceName(action));
        List<String> hl = new ArrayList<>();
        hl.add(ChatColor.GRAY + action.description());
        hl.add(" ");
        hl.add(ChatColor.YELLOW + "Click a role/relation to cycle ALLOW → DENY → UNSET.");
        hm.setLore(hl);
        header.setItemMeta(hm);
        pane.addItem(new GuiItem(header), 4, 0);

        // Roles row 1: cols 0-4 (5 roles)
        Role[] roles = roleDisplayOrder();
        for (int i = 0; i < roles.length; i++) {
            pane.addItem(buildRoleToggle(player, fPlayer, action, roles[i]), i + 2, 1);
        }

        // Relations row 2: cols 2-5 (4 relations)
        Relation[] rels = { Relation.ALLY, Relation.TRUCE, Relation.NEUTRAL, Relation.ENEMY };
        for (int i = 0; i < rels.length; i++) {
            pane.addItem(buildRelationToggle(player, fPlayer, action, rels[i]), i + 2, 2);
        }

        // Back
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName(ChatColor.GRAY + "← Back");
        back.setItemMeta(bm);
        GuiItem backItem = new GuiItem(back);
        backItem.setAction(e -> {
            e.setCancelled(true);
            openMainGrid(player, fPlayer);
        });
        pane.addItem(backItem, 0, 3);

        gui.addPane(pane);
        gui.show(player);
    }

    private GuiItem buildRoleToggle(Player player, FPlayer fPlayer, PermissibleAction action, Role role) {
        Faction faction = fPlayer.faction();
        boolean allowed = effectiveStateForRole(faction, role, action);

        ItemStack item = new ItemStack(allowed ? Material.LIME_WOOL : Material.RED_WOOL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + "" + ChatColor.BOLD + roleLabel(role));
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Current: " + colorBool(allowed));
        lore.add(" ");
        lore.add(ChatColor.GREEN + "▶ Left-click: " + ChatColor.WHITE + "Allow");
        lore.add(ChatColor.RED + "▶ Right-click: " + ChatColor.WHITE + "Deny");
        lore.add(ChatColor.GRAY + "▶ Shift-click: " + ChatColor.WHITE + "Reset to default");
        meta.setLore(lore);
        item.setItemMeta(meta);

        GuiItem gi = new GuiItem(item);
        gi.setAction(e -> {
            e.setCancelled(true);
            PermState target;
            if (e.isShiftClick()) {
                target = PermState.UNSET;
            } else if (e.isRightClick()) {
                target = PermState.DENY;
            } else {
                target = PermState.ALLOW;
            }
            applyRolePerm(faction, role, action, target);
            openActionEditor(player, fPlayer, action);
        });
        return gi;
    }

    private GuiItem buildRelationToggle(Player player, FPlayer fPlayer, PermissibleAction action, Relation relation) {
        Faction faction = fPlayer.faction();
        boolean allowed = effectiveStateForRelation(faction, relation, action);

        ItemStack item = new ItemStack(allowed ? Material.LIME_WOOL : Material.RED_WOOL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + "" + ChatColor.BOLD + capitalize(relation.name()));
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Current: " + colorBool(allowed));
        lore.add(" ");
        lore.add(ChatColor.GREEN + "▶ Left-click: " + ChatColor.WHITE + "Allow");
        lore.add(ChatColor.RED + "▶ Right-click: " + ChatColor.WHITE + "Deny");
        lore.add(ChatColor.GRAY + "▶ Shift-click: " + ChatColor.WHITE + "Reset to default");
        meta.setLore(lore);
        item.setItemMeta(meta);

        GuiItem gi = new GuiItem(item);
        gi.setAction(e -> {
            e.setCancelled(true);
            PermState target;
            if (e.isShiftClick()) {
                target = PermState.UNSET;
            } else if (e.isRightClick()) {
                target = PermState.DENY;
            } else {
                target = PermState.ALLOW;
            }
            applyRelationPerm(faction, relation, action, target);
            openActionEditor(player, fPlayer, action);
        });
        return gi;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Computes the effective allow/deny state for a given role under a given action,
     * by simulating Faction#hasAccess's selector walk. Falls back to false if no selector matches.
     */
    private static boolean effectiveStateForRole(Faction faction, Role role, PermissibleAction action) {
        // Leader (ADMIN) always has access — short-circuit matches Faction.hasAccess behavior
        if (role == Role.ADMIN) return true;
        for (PermSelector selector : faction.permissions().selectors()) {
            if (selectorMatchesRole(selector, role, faction)) {
                PermState state = faction.permissions().get(selector).get(action);
                if (state == PermState.ALLOW) return true;
                if (state == PermState.DENY) return false;
            }
        }
        return false;
    }

    private static boolean effectiveStateForRelation(Faction faction, Relation relation, PermissibleAction action) {
        for (PermSelector selector : faction.permissions().selectors()) {
            if (selectorMatchesRelation(selector, relation, faction)) {
                PermState state = faction.permissions().get(selector).get(action);
                if (state == PermState.ALLOW) return true;
                if (state == PermState.DENY) return false;
            }
        }
        return false;
    }

    private static boolean selectorMatchesRole(PermSelector selector, Role role, Faction faction) {
        // Use the selector's own test() against the role to support Single + AtLeast + AtMost variants
        try {
            return selector.test(role, faction);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean selectorMatchesRelation(PermSelector selector, Relation relation, Faction faction) {
        try {
            return selector.test(relation, faction);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Apply a permission state for a single role. To override broader RoleAtLeast defaults,
     * the new RoleSingleSelector is moved to the FRONT of the priority order.
     */
    private static void applyRolePerm(Faction faction, Role role, PermissibleAction action, PermState target) {
        PermSelector selector = new RoleSingleSelector(role);
        if (target == PermState.UNSET) {
            // Reset: remove the explicit single-selector entry entirely
            if (faction.permissions().has(selector)) {
                faction.permissions().get(selector).set(action, PermState.UNSET);
                // If no other actions remain on this selector, drop it from the list
                if (isSelectorEmpty(faction, selector)) {
                    faction.permissions().remove(selector);
                }
            }
            return;
        }
        Faction.Permissions.SelectorPerms perms = faction.permissions().add(selector);
        perms.set(action, target);
        moveSelectorToTop(faction, selector);
    }

    private static void applyRelationPerm(Faction faction, Relation relation, PermissibleAction action, PermState target) {
        PermSelector selector = new RelationSingleSelector(relation);
        if (target == PermState.UNSET) {
            if (faction.permissions().has(selector)) {
                faction.permissions().get(selector).set(action, PermState.UNSET);
                if (isSelectorEmpty(faction, selector)) {
                    faction.permissions().remove(selector);
                }
            }
            return;
        }
        Faction.Permissions.SelectorPerms perms = faction.permissions().add(selector);
        perms.set(action, target);
        moveSelectorToTop(faction, selector);
    }

    private static boolean isSelectorEmpty(Faction faction, PermSelector selector) {
        if (!faction.permissions().has(selector)) return true;
        Faction.Permissions.SelectorPerms perms = faction.permissions().get(selector);
        for (String action : perms.actions()) {
            if (perms.get(action) != PermState.UNSET) return false;
        }
        return true;
    }

    /** Move a selector to the front of the priority order so it overrides broader defaults. */
    private static void moveSelectorToTop(Faction faction, PermSelector selector) {
        // permissions().selectors() returns an immutable snapshot; moveSelectorUp moves one position at a time
        for (int safety = 0; safety < 64; safety++) {
            int idx = faction.permissions().selectors().indexOf(selector);
            if (idx <= 0) return;
            faction.permissions().moveSelectorUp(selector);
        }
    }

    private static Role[] roleDisplayOrder() {
        // Leader → Recruit
        return new Role[] { Role.ADMIN, Role.COLEADER, Role.MODERATOR, Role.NORMAL, Role.RECRUIT };
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

    private static String capitalize(String s) {
        if (s.isEmpty()) return s;
        return s.charAt(0) + s.substring(1).toLowerCase();
    }

    private static String colorBool(boolean allowed) {
        return allowed ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No";
    }

    private static String niceName(PermissibleAction action) {
        // Capitalize the action name e.g. "Build", "Tnt deposit"
        String n = action.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    private static Material iconFor(PermissibleAction action) {
        // Best-effort visual mapping. Falls back to PAPER.
        if (!(action instanceof PermissibleActions a)) return Material.PAPER;
        return switch (a) {
            case BUILD -> Material.BRICKS;
            case DESTROY -> Material.IRON_PICKAXE;
            case DOOR -> Material.OAK_DOOR;
            case BUTTON -> Material.STONE_BUTTON;
            case LEVER -> Material.LEVER;
            case CONTAINER -> Material.CHEST;
            case ITEM -> Material.ITEM_FRAME;
            case TAG -> Material.NAME_TAG;
            case DESCRIPTION -> Material.WRITABLE_BOOK;
            case MOTD -> Material.PAPER;
            case STATUS -> Material.OBSERVER;
            case INVITE -> Material.PAPER;
            case KICK -> Material.IRON_BOOTS;
            case BAN -> Material.BARRIER;
            case PROMOTE -> Material.GOLDEN_HELMET;
            case RELATION -> Material.GOLD_INGOT;
            case TERRITORY -> Material.GRASS_BLOCK;
            case SETHOME -> Material.RED_BED;
            case HOME -> Material.WHITE_BED;
            case TITLE -> Material.YELLOW_BANNER;
            case PV -> Material.ENDER_CHEST;
            case EFFECTS -> Material.SPLASH_POTION;
            case SETWARP -> Material.ENDER_EYE;
            case WARP -> Material.ENDER_PEARL;
            case UPGRADE -> Material.DIAMOND;
            case CHUNKITEMS -> Material.TNT_MINECART;
            case INSPECT -> Material.SPYGLASS;
            case LOGS -> Material.WRITTEN_BOOK;
            case ECONOMY -> Material.GOLD_NUGGET;
            case BANKDEPOSIT -> Material.GOLD_INGOT;
            case TNTDEPOSIT -> Material.TNT;
            case TNTWITHDRAW -> Material.TNT;
            case MANAGEPERMS -> Material.WRITABLE_BOOK;
            case FLY -> Material.FEATHER;
            case SHIELD -> Material.SHIELD;
            case ZONE -> Material.PURPLE_CONCRETE;
            default -> Material.PAPER;
        };
    }

    private static ItemStack makeFiller() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(" ");
        item.setItemMeta(m);
        return item;
    }
}
