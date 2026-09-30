package dev.kitteh.factions.command.defaults.admin;

import dev.kitteh.factions.FLocation;
import dev.kitteh.factions.FPlayer;
import dev.kitteh.factions.Faction;
import dev.kitteh.factions.FactionsPlugin;
import dev.kitteh.factions.command.Cloudy;
import dev.kitteh.factions.command.Cmd;
import dev.kitteh.factions.command.FactionParser;
import dev.kitteh.factions.command.Sender;
import dev.kitteh.factions.util.SpiralTask;
import dev.kitteh.factions.util.TriConsumer;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;
import org.incendo.cloud.parser.standard.IntegerParser;

/**
 * Admin command: {@code /fa claim <faction> <radius>}
 *
 * <p>Force-claims a square radius of chunks centred on the admin's location for the
 * target faction. Bypasses ownership and territory permission checks because it runs
 * under the admin root. Radius 1 = single chunk, radius 5 = 11×11 square (121 chunks).</p>
 */
public class CmdAdminClaim implements Cmd {

    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        return (manager, builder, help) -> manager.command(
                builder.literal("claim")
                        .commandDescription(Cloudy.desc("Force-claim a radius of chunks for a faction (admin)"))
                        .permission(builder.commandPermission().and(Cloudy.isPlayer()))
                        .required("faction", FactionParser.of(FactionParser.Include.SELF, FactionParser.Include.WAR, FactionParser.Include.SAFE))
                        .required("radius", IntegerParser.integerParser(1))
                        .handler(this::handle)
        );
    }

    private void handle(CommandContext<Sender> context) {
        Sender.Player playerSender = (Sender.Player) context.sender();
        try {
            doHandle(context, playerSender);
        } catch (Throwable t) {
            // Last-ditch catch so the user gets a useful message and we get a real stack trace.
            playerSender.sendRichMessage("<red>fa claim failed: " + t.getClass().getSimpleName() + ": "
                    + (t.getMessage() == null ? "(no message)" : t.getMessage()));
            dev.kitteh.factions.plugin.AbstractFactionsPlugin.instance().getLogger().log(java.util.logging.Level.SEVERE,
                    "fa claim handler threw", t);
        }
    }

    private void doHandle(CommandContext<Sender> context, Sender.Player playerSender) {
        FPlayer fSender = playerSender.fPlayer();
        Player player = playerSender.player();
        Faction target = context.get("faction");
        int radius = context.get("radius");

        if (!target.isNormal() && !target.isSafeZone() && !target.isWarZone()) {
            playerSender.sendRichMessage("<red>Cannot claim for wilderness.");
            return;
        }

        // Warzone / safezone bypass the cost/event/permission pipeline — direct board stamps.
        final boolean rawZoneClaim = target.isSafeZone() || target.isWarZone();

        FLocation center = new FLocation(player);
        int centerX = center.x();
        int centerZ = center.z();
        String world = center.worldName();

        int success = 0;
        int skipped = 0;

        // Walk every chunk in the (2*radius+1) × (2*radius+1) square synchronously.
        // SpiralTask was unreliable for small radii (limit = (radius-1)*2 = 0 at radius 1)
        // and only ran on a delayed scheduler so the result message printed before any work.
        for (int dx = -(radius - 1); dx <= (radius - 1); dx++) {
            for (int dz = -(radius - 1); dz <= (radius - 1); dz++) {
                try {
                    FLocation here = new FLocation(world, centerX + dx, centerZ + dz);
                    if (rawZoneClaim) {
                        Faction existing = dev.kitteh.factions.Board.board().factionAt(here);
                        if (existing != null && existing.equals(target)) {
                            skipped++;
                        } else {
                            dev.kitteh.factions.Board.board().claim(here, target);
                            success++;
                        }
                    } else {
                        if (fSender.attemptClaim(target, here, true)) {
                            success++;
                        } else {
                            skipped++;
                        }
                    }
                } catch (Throwable t) {
                    skipped++;
                    dev.kitteh.factions.plugin.AbstractFactionsPlugin.instance().getLogger().log(java.util.logging.Level.WARNING,
                            "fa claim: chunk-claim failed", t);
                }
            }
        }

        int total = (2 * radius - 1) * (2 * radius - 1);
        String safeTag = org.bukkit.ChatColor.stripColor(target.tag());
        playerSender.sendRichMessage("<gold>Claimed <green>" + success
                + "</green> chunk(s) for <yellow>" + safeTag
                + "</yellow> (skipped " + skipped + " of " + total + ").");
    }
}
