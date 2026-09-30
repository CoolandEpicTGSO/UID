package dev.kitteh.factions.command.defaults.admin;

import dev.kitteh.factions.FPlayer;
import dev.kitteh.factions.FPlayers;
import dev.kitteh.factions.Faction;
import dev.kitteh.factions.command.Cloudy;
import dev.kitteh.factions.command.Cmd;
import dev.kitteh.factions.command.FPlayerParser;
import dev.kitteh.factions.command.Sender;
import dev.kitteh.factions.level.FactionEffectManager;
import dev.kitteh.factions.util.TriConsumer;
import org.bukkit.potion.PotionEffectType;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;

/**
 * Admin command: /f admin geteffect &lt;player&gt; &lt;effect&gt; [duration_seconds]
 * Looks up the target player's faction and adds one use of the given effect+duration
 * to that faction's saved effect inventory. No coin cost, no cooldown.
 */
public class CmdAdminGetEffect implements Cmd {

    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        return (manager, builder, help) -> manager.command(
                builder.literal("geteffect")
                        .commandDescription(Cloudy.desc("Give a faction-wide potion effect via a faction member"))
                        .permission(builder.commandPermission().and(Cloudy.hasPermission("factions.kit.admin")))
                        .required("player", FPlayerParser.of(FPlayerParser.Include.SAME_FACTION, FPlayerParser.Include.OTHER_FACTION))
                        .required("effect", StringParser.stringParser())
                        .optional("seconds", IntegerParser.integerParser(1))
                        .handler(this::handle)
        );
    }

    private void handle(CommandContext<Sender> context) {
        FPlayer target = context.get("player");
        String effectName = context.get("effect");
        int seconds = context.getOrDefault("seconds", 1800);

        if (!target.hasFaction()) {
            context.sender().sendRichMessage("<red>" + target.name() + " is not in a faction.");
            return;
        }

        Faction faction = target.faction();
        if (!faction.isNormal()) {
            context.sender().sendRichMessage("<red>" + target.name() + "'s faction is not a normal player faction.");
            return;
        }

        // Tolerate "speed", "SPEED", "Increase damage", etc.
        PotionEffectType type = FactionEffectManager.byKey(effectName.toLowerCase().replace(' ', '_'));
        if (type == null) {
            context.sender().sendRichMessage("<red>Unknown effect '" + effectName + "'. Use a vanilla effect key (e.g. speed, strength, haste).");
            return;
        }

        faction.addEffectToInventory(FactionEffectManager.canonicalKey(type), seconds, 1);

        context.sender().sendRichMessage(
                "<gold>Saved <yellow>" + FactionEffectManager.displayName(type) + "</yellow>"
                + " (" + formatDuration(seconds) + ") to <yellow>" + faction.tag() + "</yellow>'s effect inventory"
                + " (via " + target.name() + ")."
        );

        // Notify the faction
        faction.sendRichMessage(
                "<light_purple>An admin saved <yellow>" + FactionEffectManager.displayName(type)
                + "</yellow> (" + formatDuration(seconds) + ") to your faction's effects inventory. Use <white>/f effects</white> to activate."
        );
    }

    private static String formatDuration(int seconds) {
        if (seconds >= 3600) return (seconds / 3600) + "h" + (seconds % 3600 == 0 ? "" : " " + ((seconds % 3600) / 60) + "m");
        if (seconds >= 60) return (seconds / 60) + "m";
        return seconds + "s";
    }
}
