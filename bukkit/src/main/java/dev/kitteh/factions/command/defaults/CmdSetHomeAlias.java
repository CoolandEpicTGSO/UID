package dev.kitteh.factions.command.defaults;

import dev.kitteh.factions.FactionsPlugin;
import dev.kitteh.factions.command.Cloudy;
import dev.kitteh.factions.command.Cmd;
import dev.kitteh.factions.command.Sender;
import dev.kitteh.factions.command.defaults.set.CmdSetHome;
import dev.kitteh.factions.permissible.PermissibleActions;
import dev.kitteh.factions.util.Permission;
import dev.kitteh.factions.util.TL;
import dev.kitteh.factions.util.TriConsumer;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;

public class CmdSetHomeAlias implements Cmd {
    private final CmdSetHome delegate = new CmdSetHome();

    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        return (manager, builder, help) -> manager.command(
                builder.literal("sethome")
                        .commandDescription(Cloudy.desc(TL.COMMAND_SETHOME_DESCRIPTION))
                        .permission(builder.commandPermission().and(
                                Cloudy.predicate(s -> FactionsPlugin.instance().conf().factions().homes().isEnabled())
                                        .and(Cloudy.hasPermission(Permission.SETHOME))
                                        .and(Cloudy.hasSelfFactionPerms(PermissibleActions.SETHOME))
                        ))
                        .flag(
                                manager.flagBuilder("delete")
                                        .withPermission(
                                                Cloudy.predicate(s -> FactionsPlugin.instance().conf().factions().homes().isEnabled())
                                                        .and(Cloudy.hasPermission(Permission.DELHOME))
                                                        .and(Cloudy.hasSelfFactionPerms(PermissibleActions.SETHOME))
                                        )
                        )
                        .handler(delegate::handle)
        );
    }
}
