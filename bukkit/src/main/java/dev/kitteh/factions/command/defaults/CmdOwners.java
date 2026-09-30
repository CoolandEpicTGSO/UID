package dev.kitteh.factions.command.defaults;

import dev.kitteh.factions.FPlayer;
import dev.kitteh.factions.FPlayers;
import dev.kitteh.factions.Faction;
import dev.kitteh.factions.command.Cloudy;
import dev.kitteh.factions.command.Cmd;
import dev.kitteh.factions.command.FPlayerParser;
import dev.kitteh.factions.command.Sender;
import dev.kitteh.factions.permissible.Role;
import dev.kitteh.factions.util.TriConsumer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;

import java.util.UUID;

/**
 * /f owners add|remove|list — leader-managed list of trusted players who can run /f owner
 * to take leadership when the current leader is offline.
 */
public class CmdOwners implements Cmd {

    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        return (manager, builder, help) -> {
            Command.Builder<Sender> base = builder.literal("owners")
                    .commandDescription(Cloudy.desc("Manage your faction's trusted-owners list (leader only)"))
                    .permission(builder.commandPermission().and(Cloudy.hasFaction()));

            // Leader-only: add/remove
            manager.command(
                    base.literal("add")
                            .permission(base.commandPermission().and(Cloudy.isAtLeastRole(Role.ADMIN)))
                            .required("player", FPlayerParser.of(FPlayerParser.Include.NO_FACTION, FPlayerParser.Include.OTHER_FACTION, FPlayerParser.Include.SAME_FACTION))
                            .handler(this::handleAdd)
            );
            manager.command(
                    base.literal("remove")
                            .permission(base.commandPermission().and(Cloudy.isAtLeastRole(Role.ADMIN)))
                            .required("player", FPlayerParser.of(FPlayerParser.Include.NO_FACTION, FPlayerParser.Include.OTHER_FACTION, FPlayerParser.Include.SAME_FACTION))
                            .handler(this::handleRemove)
            );
            // Anyone in the faction can list
            manager.command(base.literal("list").handler(this::handleList));
            // Bare /f owners → list
            manager.command(base.handler(this::handleList));
        };
    }

    private void handleAdd(CommandContext<Sender> context) {
        FPlayer sender = ((Sender.Player) context.sender()).fPlayer();
        FPlayer target = context.get("player");
        Faction faction = sender.faction();

        if (target.uniqueId().equals(sender.uniqueId())) {
            sender.sendRichMessage("<red>You can't add yourself — you're already the leader.");
            return;
        }
        if (faction.isOwner(target.uniqueId())) {
            sender.sendRichMessage("<red>" + target.name() + " is already on the owners list.");
            return;
        }
        if (faction.ownersAdd(target.uniqueId())) {
            faction.recordFactionLog(dev.kitteh.factions.audit.AuditEntry.generic(
                    sender.name(), sender.uniqueId(), "owners-add",
                    sender.name() + " added " + target.name() + " to the owners list"
            ));
            sender.sendRichMessage("<green>Added <yellow>" + target.name() + "</yellow> to the owners list. They can run <white>/f owner</white> to take leadership while you're offline.");
            if (target.asPlayer() != null) {
                target.sendRichMessage("<gold>You've been added to <yellow>" + faction.tag()
                        + "</yellow>'s owners list. While the leader is offline you can run <white>/f owner</white> to take leadership.");
            }
        }
    }

    private void handleRemove(CommandContext<Sender> context) {
        FPlayer sender = ((Sender.Player) context.sender()).fPlayer();
        FPlayer target = context.get("player");
        Faction faction = sender.faction();

        if (!faction.isOwner(target.uniqueId())) {
            sender.sendRichMessage("<red>" + target.name() + " is not on the owners list.");
            return;
        }
        if (faction.ownersRemove(target.uniqueId())) {
            faction.recordFactionLog(dev.kitteh.factions.audit.AuditEntry.generic(
                    sender.name(), sender.uniqueId(), "owners-remove",
                    sender.name() + " removed " + target.name() + " from the owners list"
            ));
            sender.sendRichMessage("<green>Removed <yellow>" + target.name() + "</yellow> from the owners list.");
        }
    }

    private void handleList(CommandContext<Sender> context) {
        FPlayer fp = context.sender().fPlayerOrNull();
        if (fp == null || !fp.hasFaction()) {
            context.sender().sendRichMessage("<red>You must be in a faction.");
            return;
        }
        Faction faction = fp.faction();
        java.util.Set<UUID> ids = faction.owners();
        context.sender().sendRichMessage("<gold><bold>✦ " + faction.tag() + " Owners</bold></gold> <gray>(" + ids.size() + ")");
        if (ids.isEmpty()) {
            context.sender().sendRichMessage("<gray>(empty — leader can add with <white>/f owners add \\<player></white>)");
            return;
        }
        for (UUID uuid : ids) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            String name = op.getName() != null ? op.getName() : uuid.toString().substring(0, 8);
            FPlayer member = FPlayers.fPlayers().get(uuid);
            String status;
            if (member != null && member.hasFaction() && member.faction() == faction) {
                status = "<green>(in faction)";
            } else if (member != null && member.hasFaction()) {
                status = "<red>(in another faction: " + member.faction().tag() + ")";
            } else {
                status = "<yellow>(no faction)";
            }
            context.sender().sendRichMessage("<gray>• <white>" + name + "</white> " + status);
        }
    }
}
