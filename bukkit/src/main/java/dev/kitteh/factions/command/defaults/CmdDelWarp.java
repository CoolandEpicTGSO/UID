package dev.kitteh.factions.command.defaults;

import dev.kitteh.factions.FPlayer;
import dev.kitteh.factions.Faction;
import dev.kitteh.factions.FactionsPlugin;
import dev.kitteh.factions.command.Cloudy;
import dev.kitteh.factions.command.Cmd;
import dev.kitteh.factions.command.Sender;
import dev.kitteh.factions.permissible.PermissibleActions;
import dev.kitteh.factions.util.Permission;
import dev.kitteh.factions.util.TL;
import dev.kitteh.factions.util.TriConsumer;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;
import org.incendo.cloud.parser.standard.StringParser;

public class CmdDelWarp implements Cmd {
    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        return (manager, builder, help) -> manager.command(
                builder.literal("delwarp", "deletewarp")
                        .commandDescription(Cloudy.desc("Delete a faction warp"))
                        .permission(builder.commandPermission().and(Cloudy.hasPermission(Permission.SETWARP).and(Cloudy.hasSelfFactionPerms(PermissibleActions.SETWARP))))
                        .required("name", StringParser.stringParser())
                        .handler(this::handle)
        );
    }

    private void handle(CommandContext<Sender> context) {
        FPlayer sender = ((Sender.Player) context.sender()).fPlayer();
        Faction faction = sender.faction();
        String warp = context.get("name");

        if (!faction.isWarp(warp)) {
            sender.msgLegacy(TL.COMMAND_DELFWARP_INVALID, warp);
            return;
        }

        if (!context.sender().payForCommand(FactionsPlugin.instance().conf().economy().getCostDelWarp(), TL.COMMAND_DELFWARP_TODELETE, TL.COMMAND_DELFWARP_FORDELETE)) {
            return;
        }

        faction.removeWarp(warp);
        faction.recordFactionLog(dev.kitteh.factions.audit.AuditEntry.generic(
                sender.name(), sender.uniqueId(), "warp-delete",
                sender.name() + " deleted warp '" + warp + "'"
        ));
        sender.msgLegacy(TL.COMMAND_DELFWARP_DELETED, warp);
    }
}
