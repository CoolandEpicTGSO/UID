package dev.kitteh.factions.command.defaults.admin;

import dev.kitteh.factions.Faction;
import dev.kitteh.factions.command.Cloudy;
import dev.kitteh.factions.command.Cmd;
import dev.kitteh.factions.command.FactionParser;
import dev.kitteh.factions.command.Sender;
import dev.kitteh.factions.util.TriConsumer;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;
import org.incendo.cloud.parser.standard.IntegerParser;

public class CmdAdminCoins implements Cmd {

    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        return (manager, builder, help) -> {
            Command.Builder<Sender> base = builder.literal("coins")
                    .commandDescription(Cloudy.desc("Give or take faction coins"))
                    .permission(builder.commandPermission().and(Cloudy.hasPermission("factions.kit.admin")))
                    .required("faction", FactionParser.of(FactionParser.Include.SELF))
                    .required("amount", IntegerParser.integerParser());

            manager.command(base.handler(this::handle));
        };
    }

    private void handle(CommandContext<Sender> context) {
        Faction faction = context.get("faction");
        int amount = context.get("amount");
        int before = faction.factionPoints();

        if (amount >= 0) {
            faction.addFactionPoints(amount);
        } else {
            faction.spendFactionPoints(-amount);
        }

        int after = faction.factionPoints();
        context.sender().sendRichMessage(
                "<gold>Faction coins for <yellow>" + faction.tag() + "</yellow>: "
                + "<gray>" + before + " → <green>" + after
                + "<gray> (" + (amount >= 0 ? "+" : "") + amount + ")"
        );

        // Notify online faction members
        faction.sendRichMessage(
                "<light_purple>An admin " + (amount >= 0 ? "gave" : "took") + " <gold>"
                + Math.abs(amount) + " coin(s)</gold>. "
                + "Your faction now has <gold>" + after + " coin(s)</gold>. "
                + "Use <white>/f upgrades</white> to spend them."
        );
    }
}
