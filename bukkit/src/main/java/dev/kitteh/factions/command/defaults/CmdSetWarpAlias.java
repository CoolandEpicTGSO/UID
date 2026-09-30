package dev.kitteh.factions.command.defaults;

import dev.kitteh.factions.command.Cloudy;
import dev.kitteh.factions.command.Cmd;
import dev.kitteh.factions.command.Sender;
import dev.kitteh.factions.command.defaults.set.CmdSetWarp;
import dev.kitteh.factions.permissible.PermissibleActions;
import dev.kitteh.factions.util.Permission;
import dev.kitteh.factions.util.TL;
import dev.kitteh.factions.util.TriConsumer;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;
import org.incendo.cloud.parser.standard.StringParser;

/**
 * Top-level shortcut for /f set warp. Delegates to CmdSetWarp.
 */
public class CmdSetWarpAlias implements Cmd {
    private final CmdSetWarp delegate = new CmdSetWarp();

    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        return (manager, builder, help) -> manager.command(
                builder.literal("setwarp")
                        .commandDescription(Cloudy.desc(TL.COMMAND_SETFWARP_DESCRIPTION))
                        .permission(builder.commandPermission()
                                .and(Cloudy.hasPermission(Permission.SETWARP))
                                .and(Cloudy.hasSelfFactionPerms(PermissibleActions.SETWARP)))
                        .required("name", StringParser.stringParser())
                        .flag(manager.flagBuilder("password").withComponent(StringParser.stringParser()))
                        .flag(manager.flagBuilder("delete"))
                        .handler(delegate::handle)
        );
    }
}
