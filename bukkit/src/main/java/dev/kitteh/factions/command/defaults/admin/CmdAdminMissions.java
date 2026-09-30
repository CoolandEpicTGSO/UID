package dev.kitteh.factions.command.defaults.admin;

import dev.kitteh.factions.Faction;
import dev.kitteh.factions.Factions;
import dev.kitteh.factions.FactionsPlugin;
import dev.kitteh.factions.command.Cloudy;
import dev.kitteh.factions.command.Cmd;
import dev.kitteh.factions.command.Sender;
import dev.kitteh.factions.mission.MissionManager;
import dev.kitteh.factions.util.TriConsumer;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;

/**
 * Admin command: {@code /fa missions reload}
 *
 * <p>Reloads {@code missions.conf} from disk so config edits take effect without a server
 * restart, then forces a full mission reset for every normal faction: progress on every
 * mission is zeroed, every category re-rolls its active picks, and reset timestamps update
 * to "now" so the next reset cycle starts fresh from this moment.</p>
 */
public class CmdAdminMissions implements Cmd {

    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        return (manager, builder, help) -> manager.command(
                builder.literal("missions", "mission")
                        .literal("reload", "reset")
                        .commandDescription(Cloudy.desc("Reload missions config and reset every faction's mission progress"))
                        .handler(this::handle)
        );
    }

    private void handle(CommandContext<Sender> context) {
        try {
            FactionsPlugin.instance().configManager().loadConfigs();
        } catch (Throwable t) {
            context.sender().sendRichMessage("<red>Failed to reload missions.conf — see server console for details.");
            dev.kitteh.factions.plugin.AbstractFactionsPlugin.instance().getLogger()
                    .log(java.util.logging.Level.SEVERE, "fa missions reload: config load failed", t);
            return;
        }

        int factionsTouched = 0;
        for (Faction faction : Factions.factions().all()) {
            if (!faction.isNormal()) continue;
            try {
                MissionManager.forceResetAll(faction);
                factionsTouched++;
            } catch (Throwable t) {
                dev.kitteh.factions.plugin.AbstractFactionsPlugin.instance().getLogger()
                        .log(java.util.logging.Level.WARNING,
                                "fa missions reload: force-reset failed for faction " + faction.tag(), t);
            }
        }

        context.sender().sendRichMessage("<aqua>Missions reloaded. <yellow>"
                + factionsTouched + "</yellow> faction(s) had their mission progress and active picks reset.");
    }
}
