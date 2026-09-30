package dev.kitteh.factions.command.defaults;

import dev.kitteh.factions.command.Cloudy;
import dev.kitteh.factions.command.Cmd;
import dev.kitteh.factions.command.FactionParser;
import dev.kitteh.factions.command.Sender;
import dev.kitteh.factions.command.defaults.money.CmdMoneyWithdraw;
import dev.kitteh.factions.integration.Econ;
import dev.kitteh.factions.util.Permission;
import dev.kitteh.factions.util.TriConsumer;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;
import org.incendo.cloud.parser.standard.DoubleParser;

/**
 * Top-level shortcut for /f money withdraw. Delegates to CmdMoneyWithdraw.
 */
public class CmdWithdraw implements Cmd {
    private final CmdMoneyWithdraw delegate = new CmdMoneyWithdraw();

    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        return (manager, builder, help) -> manager.command(
                builder.literal("withdraw")
                        .commandDescription(Cloudy.desc("Withdraw money from your faction bank"))
                        .permission(builder.commandPermission()
                                .and(Cloudy.predicate(s -> Econ.shouldBeUsedWithBanks()))
                                .and(Cloudy.hasPermission(Permission.MONEY_WITHDRAW))
                                .and(Cloudy.isPlayer()))
                        .required("amount", DoubleParser.doubleParser(0))
                        .flag(
                                manager.flagBuilder("faction")
                                        .withComponent(FactionParser.of(FactionParser.Include.SELF))
                                        .withPermission(Cloudy.hasPermission(Permission.MONEY_WITHDRAW_ANY))
                        )
                        .handler(delegate::handle)
        );
    }
}
