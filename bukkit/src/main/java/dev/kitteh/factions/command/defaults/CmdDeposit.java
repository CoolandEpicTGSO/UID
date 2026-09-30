package dev.kitteh.factions.command.defaults;

import dev.kitteh.factions.command.Cloudy;
import dev.kitteh.factions.command.Cmd;
import dev.kitteh.factions.command.FactionParser;
import dev.kitteh.factions.command.Sender;
import dev.kitteh.factions.command.defaults.money.CmdMoneyDeposit;
import dev.kitteh.factions.integration.Econ;
import dev.kitteh.factions.util.Permission;
import dev.kitteh.factions.util.TriConsumer;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;
import org.incendo.cloud.parser.standard.DoubleParser;

/**
 * Top-level shortcut for /f money deposit. Delegates to CmdMoneyDeposit.
 */
public class CmdDeposit implements Cmd {
    private final CmdMoneyDeposit delegate = new CmdMoneyDeposit();

    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        return (manager, builder, help) -> manager.command(
                builder.literal("deposit")
                        .commandDescription(Cloudy.desc("Deposit money into your faction bank"))
                        .permission(builder.commandPermission()
                                .and(Cloudy.predicate(s -> Econ.shouldBeUsedWithBanks()))
                                .and(Cloudy.hasPermission(Permission.MONEY_DEPOSIT))
                                .and(Cloudy.isPlayer()))
                        .required("amount", DoubleParser.doubleParser(0))
                        .flag(manager.flagBuilder("faction").withComponent(FactionParser.of(FactionParser.Include.SELF)))
                        .handler(delegate::handle)
        );
    }
}
