package dev.kitteh.factions.command;

import dev.kitteh.factions.permissible.PermissibleAction;
import dev.kitteh.factions.permissible.Role;
import dev.kitteh.factions.util.Permission;
import dev.kitteh.factions.util.TL;
import org.bukkit.ChatColor;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.key.CloudKey;
import org.incendo.cloud.permission.PredicatePermission;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NullMarked;

import java.util.function.Predicate;

/**
 * Helper methods for cloud.
 */
@ApiStatus.AvailableSince("4.0.0")
@NullMarked
public final class Cloudy {
    public static PredicatePermission<Sender> predicate(Predicate<Sender> predicate) {
        return PredicatePermission.of(predicate);
    }

    public static PredicatePermission<Sender> hasPermission(String permission) {
        return PredicatePermission.of(s -> s.sender().hasPermission(permission));
    }

    public static PredicatePermission<Sender> hasPermission(Permission permission) {
        return PredicatePermission.of(s -> s.sender().hasPermission(permission.node));
    }

    public static PredicatePermission<Sender> isPlayer() {
        return PredicatePermission.of(Sender::isPlayer);
    }

    /**
     * Permission key used by {@link #hasFaction()}. Identified by name so the command
     * exception handler can recognise a "needs a faction" failure and show a friendly
     * "create a faction" prompt instead of the generic permission-denied message.
     */
    public static final CloudKey<Void> HAS_FACTION_KEY = CloudKey.of("factions:has_faction");

    public static PredicatePermission<Sender> hasFaction() {
        return PredicatePermission.of(HAS_FACTION_KEY, Sender::hasFaction);
    }

    public static PredicatePermission<Sender> isBypass() {
        return PredicatePermission.of(Sender::isBypass);
    }

    public static PredicatePermission<Sender> isAtLeastRole(Role role) {
        return PredicatePermission.of(s -> s.isAtLeastRole(role));
    }

    public static PredicatePermission<Sender> hasSelfFactionPerms(PermissibleAction action) {
        return PredicatePermission.of(s -> s instanceof Sender.Player player && player.faction().isNormal() && player.faction().hasAccess(player.fPlayer(), action, player.fPlayer().lastStoodAt()));
    }

    @Deprecated(forRemoval = true, since = "4.5.0")
    public static Description desc(TL tl) {
        return Description.of(ChatColor.stripColor(tl.toString()));
    }

    @ApiStatus.AvailableSince("4.5.0")
    public static Description desc(String text) {
        return Description.of(text);
    }
}
