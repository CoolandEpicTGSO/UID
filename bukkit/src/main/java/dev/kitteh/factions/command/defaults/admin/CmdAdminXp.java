package dev.kitteh.factions.command.defaults.admin;

import dev.kitteh.factions.Faction;
import dev.kitteh.factions.command.Cloudy;
import dev.kitteh.factions.command.Cmd;
import dev.kitteh.factions.command.FactionParser;
import dev.kitteh.factions.command.Sender;
import dev.kitteh.factions.level.FactionLevelManager;
import dev.kitteh.factions.util.TriConsumer;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;
import org.incendo.cloud.parser.standard.LongParser;

/**
 * Admin command: /f admin xp &lt;faction&gt; &lt;amount&gt;
 * Adds (or removes, with negative) faction XP. Triggers level-ups normally.
 */
public class CmdAdminXp implements Cmd {

    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        return (manager, builder, help) -> manager.command(
                builder.literal("xp")
                        .commandDescription(Cloudy.desc("Give or take faction XP (negative supported)"))
                        .permission(builder.commandPermission().and(Cloudy.hasPermission("factions.kit.admin")))
                        .required("faction", FactionParser.of(FactionParser.Include.SELF))
                        .required("amount", LongParser.longParser())
                        .handler(this::handle)
        );
    }

    private void handle(CommandContext<Sender> context) {
        Faction faction = context.get("faction");
        long amount = context.get("amount");

        if (!faction.isNormal()) {
            context.sender().sendRichMessage("<red>That faction is not a normal player faction.");
            return;
        }

        long beforeXp = faction.factionXp();
        int beforeLevel = faction.factionLevel();
        FactionLevelManager.awardXp(faction, amount);
        long afterXp = faction.factionXp();
        int afterLevel = faction.factionLevel();

        context.sender().sendRichMessage(
                "<gold>Faction XP for <yellow>" + faction.tag() + "</yellow>: "
                + "<gray>L" + beforeLevel + " " + beforeXp
                + " <gray>→ <green>L" + afterLevel + " " + afterXp
                + "<gray> (" + (amount >= 0 ? "+" : "") + amount + ")"
        );

        if (afterLevel != beforeLevel) {
            context.sender().sendRichMessage("<gray>(triggered " + (afterLevel - beforeLevel) + " level-up" + (afterLevel - beforeLevel == 1 ? "" : "s") + ")");
        }

        // Notify online faction members of the admin XP change
        faction.sendRichMessage(
                "<light_purple>An admin " + (amount >= 0 ? "gave your faction" : "took") + " <yellow>"
                + Math.abs(amount) + " XP</yellow>"
                + (amount >= 0 ? "." : " from your faction.")
        );
    }
}
