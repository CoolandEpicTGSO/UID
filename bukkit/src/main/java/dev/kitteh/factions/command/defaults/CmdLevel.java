package dev.kitteh.factions.command.defaults;

import dev.kitteh.factions.FPlayer;
import dev.kitteh.factions.Faction;
import dev.kitteh.factions.FactionsPlugin;
import dev.kitteh.factions.command.Cloudy;
import dev.kitteh.factions.command.Cmd;
import dev.kitteh.factions.command.Sender;
import dev.kitteh.factions.util.Permission;
import dev.kitteh.factions.level.FactionLevelManager;
import dev.kitteh.factions.level.FactionLevelsConfig;
import dev.kitteh.factions.util.TriConsumer;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;

public class CmdLevel implements Cmd {

    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        return (manager, builder, help) -> manager.command(
                builder.literal("level", "lvl", "xp")
                        .permission(builder.commandPermission().and(Cloudy.hasPermission(Permission.LEVEL).and(Cloudy.hasFaction())))
                        .commandDescription(Cloudy.desc("View your faction level and XP progress"))
                        .handler(this::handle)
        );
    }

    private void handle(CommandContext<Sender> context) {
        FPlayer sender = ((Sender.Player) context.sender()).fPlayer();
        Faction faction = sender.faction();
        FactionLevelsConfig cfg = FactionsPlugin.instance().configManager().factionLevelsConfig();

        int level = faction.factionLevel();
        long xp = faction.factionXp();
        int points = faction.factionPoints();
        int maxLevel = cfg.maxLevel();

        sender.sendRichMessage("<aqua><bold>✦ Faction Level</bold></aqua>");
        sender.sendRichMessage("<gray>Faction: <white>" + faction.tag());
        sender.sendRichMessage("<gray>Level: <yellow>" + level + "/" + maxLevel);

        if (level < maxLevel) {
            long needed = cfg.xpRequired(level + 1);
            long remaining = FactionLevelManager.xpUntilNextLevel(faction);
            double progress = FactionLevelManager.progressToNextLevel(faction);

            // Progress bar
            int barLen = 20;
            int filled = (int) (progress * barLen);
            String bar = "<green>" + "█".repeat(filled) + "<dark_gray>" + "█".repeat(barLen - filled);

            sender.sendRichMessage("<gray>XP: <white>" + xp + "<gray>/<white>" + needed);
            sender.sendRichMessage(bar + " <yellow>" + String.format("%.1f", progress * 100) + "%");
            sender.sendRichMessage("<gray>XP until next level: <aqua>" + remaining);
        } else {
            sender.sendRichMessage("<green>✔ Maximum level reached!");
            sender.sendRichMessage("<gray>Total XP: <white>" + xp);
        }

        sender.sendRichMessage("<gray>Faction Points: <yellow>" + points);

        if (points > 0) {
            sender.sendRichMessage("<gray>Use <white>/f upgrades</white> to spend points on the skill tree.");
        }

        // Show next level's effect unlocks if not max
        if (level < maxLevel) {
            java.util.List<String> nextUnlocks = cfg.effectUnlocks(level + 1);
            if (!nextUnlocks.isEmpty()) {
                sender.sendRichMessage("<gray>Next level unlocks: <light_purple>" + String.join(", ", nextUnlocks));
            }
        }
    }
}
