package dev.kitteh.factions.command.defaults;

import dev.kitteh.factions.FPlayer;
import dev.kitteh.factions.Faction;
import dev.kitteh.factions.FactionsPlugin;
import dev.kitteh.factions.command.Cloudy;
import dev.kitteh.factions.command.Cmd;
import dev.kitteh.factions.command.Sender;
import dev.kitteh.factions.level.FactionLevelsConfig;
import dev.kitteh.factions.util.TriConsumer;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;

/**
 * /f coins — quick view of the faction's current coin balance and how to spend it.
 */
public class CmdCoins implements Cmd {
    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        return (manager, builder, help) -> manager.command(
                builder.literal("coins")
                        .commandDescription(Cloudy.desc("View your faction's coin balance"))
                        .permission(builder.commandPermission().and(Cloudy.hasFaction()))
                        .handler(this::handle)
        );
    }

    private void handle(CommandContext<Sender> context) {
        FPlayer fPlayer = ((Sender.Player) context.sender()).fPlayer();
        Faction faction = fPlayer.faction();
        FactionLevelsConfig cfg = FactionsPlugin.instance().configManager().factionLevelsConfig();

        int coins = faction.factionPoints();
        int level = faction.factionLevel();
        int maxLevel = cfg.maxLevel();

        fPlayer.sendRichMessage("<aqua><bold>✦ Faction Coins</bold></aqua>");
        fPlayer.sendRichMessage("<gray>Faction: <white>" + faction.tag());
        fPlayer.sendRichMessage("<gray>Coins: <yellow>" + coins);
        fPlayer.sendRichMessage("<gray>Level: <yellow>" + level + "<gray>/<yellow>" + maxLevel);
        if (coins > 0) {
            fPlayer.sendRichMessage("<gray>Spend with <white>/f upgrades</white>.");
        } else {
            fPlayer.sendRichMessage("<gray>Earn coins by levelling up. See <white>/f level</white>.");
        }
    }
}
