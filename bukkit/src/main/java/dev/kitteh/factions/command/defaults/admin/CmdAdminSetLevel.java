package dev.kitteh.factions.command.defaults.admin;

import dev.kitteh.factions.Faction;
import dev.kitteh.factions.FactionsPlugin;
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
import org.incendo.cloud.parser.standard.IntegerParser;

/**
 * Admin command: /f admin setlevel &lt;faction&gt; &lt;level&gt;
 * Forcibly sets a faction's level. Resets per-level XP to 0.
 * If raising the level, applies level-up rewards for each level gained.
 * If lowering the level, no rewards are reversed (consistent with most "give"-style admin commands).
 */
public class CmdAdminSetLevel implements Cmd {

    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        return (manager, builder, help) -> manager.command(
                builder.literal("setlevel")
                        .commandDescription(Cloudy.desc("Set a faction's level (resets XP to 0)"))
                        .permission(builder.commandPermission().and(Cloudy.hasPermission("factions.kit.admin")))
                        .required("faction", FactionParser.of(FactionParser.Include.SELF))
                        .required("level", IntegerParser.integerParser(0))
                        .handler(this::handle)
        );
    }

    private void handle(CommandContext<Sender> context) {
        Faction faction = context.get("faction");
        int level = context.get("level");

        if (!faction.isNormal()) {
            context.sender().sendRichMessage("<red>That faction is not a normal player faction.");
            return;
        }

        int max = FactionsPlugin.instance().configManager().factionLevelsConfig().maxLevel();
        if (level > max) {
            context.sender().sendRichMessage("<red>Level " + level + " is above max level (" + max + ").");
            return;
        }

        int before = faction.factionLevel();
        FactionLevelManager.setLevel(faction, level, true);

        context.sender().sendRichMessage(
                "<gold>Faction level for <yellow>" + faction.tag() + "</yellow>: "
                + "<gray>" + before + " <gray>→ <green>" + level
                + "<gray> (XP reset to 0)"
        );

        // Notify members
        faction.sendRichMessage(
                "<light_purple>An admin set your faction level to <gold>" + level + "</gold>."
        );
    }
}
