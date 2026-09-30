package dev.kitteh.factions.command.defaults.money;

import dev.kitteh.factions.FPlayer;
import dev.kitteh.factions.Faction;
import dev.kitteh.factions.FactionsPlugin;
import dev.kitteh.factions.command.Cloudy;
import dev.kitteh.factions.command.Cmd;
import dev.kitteh.factions.command.FactionParser;
import dev.kitteh.factions.command.Sender;
import dev.kitteh.factions.integration.Econ;
import dev.kitteh.factions.permissible.PermissibleActions;
import dev.kitteh.factions.plugin.AbstractFactionsPlugin;
import dev.kitteh.factions.util.Permission;
import dev.kitteh.factions.util.TL;
import dev.kitteh.factions.util.TextUtil;
import org.bukkit.ChatColor;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.DoubleParser;

import dev.kitteh.factions.util.TriConsumer;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;

public class CmdMoneyDeposit implements Cmd {
    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        // Hidden from /f help — the top-level /f deposit shortcut (CmdDeposit) is the listed command.
        return (manager, builder, help) -> manager.command(
                builder.literal("deposit")
                        .meta(HIDE_IN_HELP, true)
                        .permission(builder.commandPermission().and(Cloudy.hasPermission(Permission.MONEY_DEPOSIT)).and(Cloudy.isPlayer()))
                        .required("amount", DoubleParser.doubleParser(0))
                        .flag(manager.flagBuilder("faction").withComponent(FactionParser.of(FactionParser.Include.SELF)))
                        .handler(this::handle)
        );
    }

    public void handle(CommandContext<Sender> context) {
        double amount = context.get("amount");
        amount = Math.abs(amount);
        if (amount == 0D) {
            return;
        }

        FPlayer sender = ((Sender.Player) context.sender()).fPlayer();

        Faction faction;

        if (context.flags().get("faction") instanceof Faction f) {
            faction = f;
        } else {
            faction = sender.faction();
        }

        if (!faction.isNormal()) {
            return;
        }

        if (faction == sender.faction() && !faction.hasAccess(sender, PermissibleActions.BANKDEPOSIT, sender.lastStoodAt())) {
            sender.sendRichMessage("<red>You do not have permission to deposit to the faction bank.");
            return;
        }

        boolean success = Econ.transferMoney(sender, sender, faction, amount);

        if (success) {
            faction.addBankDeposit(sender.uniqueId(), amount);
            faction.recordFactionLog(dev.kitteh.factions.audit.AuditEntry.generic(
                    sender.name(), sender.uniqueId(), "bank-deposit",
                    sender.name() + " deposited " + Econ.moneyString(amount)
            ));
            if (FactionsPlugin.instance().conf().logging().isMoneyTransactions()) {
                AbstractFactionsPlugin.instance().log(ChatColor.stripColor(TextUtil.parse(TL.COMMAND_MONEYDEPOSIT_DEPOSITED.toString(), sender.name(), Econ.moneyString(amount), faction.describeToLegacy(null))));
            }
        }
    }
}
