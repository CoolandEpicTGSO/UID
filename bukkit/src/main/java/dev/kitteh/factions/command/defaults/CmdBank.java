package dev.kitteh.factions.command.defaults;

import dev.kitteh.factions.command.Cloudy;
import dev.kitteh.factions.command.Cmd;
import dev.kitteh.factions.command.FactionParser;
import dev.kitteh.factions.command.Sender;
import dev.kitteh.factions.command.defaults.money.CmdMoneyBalance;
import dev.kitteh.factions.integration.Econ;
import dev.kitteh.factions.util.Permission;
import dev.kitteh.factions.util.TriConsumer;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;

/**
 * Top-level shortcut: /f bank (alias /f bal) — opens the faction bank GUI.
 * Delegates to CmdMoneyBalance.
 */
public class CmdBank implements Cmd {
    private final CmdMoneyBalance delegate = new CmdMoneyBalance();

    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        return (manager, builder, help) -> manager.command(
                builder.literal("bank", "bal")
                        .commandDescription(Cloudy.desc("Open the faction bank GUI"))
                        .permission(builder.commandPermission()
                                .and(Cloudy.predicate(s -> Econ.shouldBeUsedWithBanks()))
                                .and(Cloudy.hasPermission(Permission.MONEY_BALANCE)))
                        .flag(
                                manager.flagBuilder("faction")
                                        .withComponent(FactionParser.of(FactionParser.Include.SELF))
                                        .withPermission(Cloudy.hasPermission(Permission.MONEY_BALANCE_ANY))
                        )
                        .handler(delegate::handle)
        );
    }
}
