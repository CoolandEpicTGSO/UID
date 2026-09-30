package dev.kitteh.factions.command.defaults;

import dev.kitteh.factions.FPlayer;
import dev.kitteh.factions.Faction;
import dev.kitteh.factions.event.FactionNewAdminEvent;
import dev.kitteh.factions.command.Cloudy;
import dev.kitteh.factions.command.Cmd;
import dev.kitteh.factions.command.Sender;
import dev.kitteh.factions.permissible.Role;
import dev.kitteh.factions.util.TriConsumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;

/**
 * /f owner — A trusted owner can take leadership when the current leader is offline.
 * Requirements: runner is in the faction, on the owners list, current leader is offline.
 * Old leader → COLEADER. Runner → ADMIN.
 */
public class CmdOwner implements Cmd {

    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        return (manager, builder, help) -> manager.command(
                builder.literal("owner")
                        .commandDescription(Cloudy.desc("Take faction leadership while the current leader is offline"))
                        .permission(builder.commandPermission().and(Cloudy.hasFaction()))
                        .handler(this::handle)
        );
    }

    private void handle(CommandContext<Sender> context) {
        if (!(context.sender() instanceof Sender.Player playerSender)) {
            context.sender().sendRichMessage("<red>This command can only be run by a player.");
            return;
        }
        FPlayer sender = playerSender.fPlayer();
        Faction faction = sender.faction();

        if (!faction.isNormal()) {
            sender.sendRichMessage("<red>You must be in a normal faction.");
            return;
        }

        // Already the leader? No-op.
        if (sender.role() == Role.ADMIN) {
            sender.sendRichMessage("<red>You are already the leader.");
            return;
        }

        // Must be on the owners list
        if (!faction.isOwner(sender.uniqueId())) {
            sender.sendRichMessage("<red>You aren't on this faction's owners list. The leader can add you with <white>/f owners add</white>.");
            return;
        }

        FPlayer currentLeader = faction.admin();
        if (currentLeader == null) {
            // No leader at all — claim it freely
            promoteSelf(faction, sender, null, playerSender.player());
            return;
        }

        // Don't let someone takeover from themselves (shouldn't happen due to ADMIN check above)
        if (currentLeader.uniqueId().equals(sender.uniqueId())) {
            sender.sendRichMessage("<red>You're already the leader.");
            return;
        }

        // Leader must be offline
        Player onlineLeader = Bukkit.getPlayer(currentLeader.uniqueId());
        if (onlineLeader != null && onlineLeader.isOnline()) {
            sender.sendRichMessage("<red>The current leader (<yellow>" + currentLeader.name() + "</yellow>) is online. You can only take leadership while they are offline.");
            return;
        }

        promoteSelf(faction, sender, currentLeader, playerSender.player());
    }

    private void promoteSelf(Faction faction, FPlayer sender, FPlayer oldLeader, Player playerHandle) {
        // Fire the FactionNewAdminEvent so other plugins/listeners react properly
        Bukkit.getServer().getPluginManager().callEvent(new FactionNewAdminEvent(sender, faction));

        if (oldLeader != null) {
            oldLeader.role(Role.COLEADER);
        }
        sender.role(Role.ADMIN);

        // Refresh the player's command suggestions
        playerHandle.updateCommands();

        faction.recordFactionLog(dev.kitteh.factions.audit.AuditEntry.generic(
                sender.name(), sender.uniqueId(), "owner-takeover",
                sender.name() + " took faction leadership"
                        + (oldLeader != null ? " from " + oldLeader.name() + " (offline)" : "")
        ));

        // Broadcast to faction
        faction.sendRichMessage("<gold><bold>✦ <yellow>" + sender.name() + "</yellow> has taken faction leadership"
                + (oldLeader != null ? " from <yellow>" + oldLeader.name() + "</yellow>" : "") + "!");
    }
}
