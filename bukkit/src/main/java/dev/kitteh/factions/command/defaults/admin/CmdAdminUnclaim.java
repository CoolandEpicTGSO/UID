package dev.kitteh.factions.command.defaults.admin;

import dev.kitteh.factions.Board;
import dev.kitteh.factions.FLocation;
import dev.kitteh.factions.Faction;
import dev.kitteh.factions.FactionsPlugin;
import dev.kitteh.factions.command.Cloudy;
import dev.kitteh.factions.command.Cmd;
import dev.kitteh.factions.command.FactionParser;
import dev.kitteh.factions.command.Sender;
import dev.kitteh.factions.event.LandUnclaimAllEvent;
import dev.kitteh.factions.event.LandUnclaimEvent;
import dev.kitteh.factions.plugin.AbstractFactionsPlugin;
import dev.kitteh.factions.util.SpiralTask;
import dev.kitteh.factions.util.TriConsumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;
import org.incendo.cloud.parser.standard.IntegerParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin unclaim commands under {@code /fa unclaim}:
 *
 * <ul>
 *   <li>{@code /fa unclaim <faction> <radius>} — unclaim chunks belonging to {@code <faction>}
 *       within a square radius around the admin. Works on warzone / safezone too.</li>
 *   <li>{@code /fa unclaim <faction> all} — unclaim every chunk that faction owns, map-wide.</li>
 * </ul>
 *
 * <p>Both forms bypass ownership / territory permission checks because they run under the
 * admin root. Wilderness chunks in the radius are silently skipped.</p>
 */
public class CmdAdminUnclaim implements Cmd {

    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        return (manager, builder, help) -> {
            // Form 1: /fa unclaim <faction> <radius>
            manager.command(
                    builder.literal("unclaim")
                            .commandDescription(Cloudy.desc("Force-unclaim a radius of chunks from a faction (admin)"))
                            .permission(builder.commandPermission().and(Cloudy.isPlayer()))
                            .required("faction", FactionParser.of(
                                    FactionParser.Include.SELF,
                                    FactionParser.Include.WAR,
                                    FactionParser.Include.SAFE))
                            .required("radius", IntegerParser.integerParser(1))
                            .handler(this::handleRadius)
            );

            // Form 2: /fa unclaim <faction> all  (literal "all" instead of an int radius)
            manager.command(
                    builder.literal("unclaim")
                            .commandDescription(Cloudy.desc("Force-unclaim every chunk owned by a faction (admin)"))
                            .permission(builder.commandPermission().and(Cloudy.isPlayer()))
                            .required("faction", FactionParser.of(
                                    FactionParser.Include.SELF,
                                    FactionParser.Include.WAR,
                                    FactionParser.Include.SAFE))
                            .literal("all")
                            .handler(this::handleAll)
            );
        };
    }

    private void handleRadius(CommandContext<Sender> context) {
        Sender.Player playerSender = (Sender.Player) context.sender();
        Player player = playerSender.player();
        Faction target = context.get("faction");
        int radius = context.get("radius");

        if (target.isWilderness()) {
            playerSender.sendRichMessage("<red>Cannot unclaim wilderness.");
            return;
        }

        FLocation start = new FLocation(player);
        int[] unclaimed = {0};

        new SpiralTask(start, radius) {
            @Override
            public boolean work() {
                FLocation here = this.currentFLocation();
                Faction at = Board.board().factionAt(here);
                if (at == null || !at.equals(target)) return true; // skip — not target's chunk

                LandUnclaimEvent event = new LandUnclaimEvent(here, target, playerSender.fPlayer());
                Bukkit.getPluginManager().callEvent(event);
                if (event.isCancelled()) return true;

                Board.board().unclaim(here);
                unclaimed[0]++;

                if (FactionsPlugin.instance().conf().logging().isLandUnclaims()) {
                    AbstractFactionsPlugin.instance().log(
                            "ADMIN " + player.getName() + " unclaimed " + here.asCoordString() + " from " + target.tag()
                    );
                }
                return true;
            }
        };

        int side = radius * 2 + 1;
        // Strip legacy section-sign codes — warzone/safezone tags carry baked-in §-codes
        // that crash MiniMessage when embedded in a <yellow>...</yellow> block.
        String safeTag = org.bukkit.ChatColor.stripColor(target.tag());
        playerSender.sendRichMessage("<gold>Unclaiming up to <yellow>" + (side * side)
                + "</yellow> chunks from <yellow>" + safeTag + "</yellow>.");
    }

    private void handleAll(CommandContext<Sender> context) {
        Sender.Player playerSender = (Sender.Player) context.sender();
        Player player = playerSender.player();
        Faction target = context.get("faction");

        if (target.isWilderness()) {
            playerSender.sendRichMessage("<red>Cannot unclaim wilderness.");
            return;
        }

        // Snapshot the chunks first — modifying claims while iterating Board's view is risky
        List<FLocation> chunks = new ArrayList<>(target.claims());
        String safeTag = org.bukkit.ChatColor.stripColor(target.tag());
        if (chunks.isEmpty()) {
            playerSender.sendRichMessage("<gold>" + safeTag + " has no claims to unclaim.");
            return;
        }

        LandUnclaimAllEvent allEvent = new LandUnclaimAllEvent(target, playerSender.fPlayer());
        Bukkit.getPluginManager().callEvent(allEvent);
        if (allEvent.isCancelled()) {
            playerSender.sendRichMessage("<red>Unclaim-all event was cancelled.");
            return;
        }

        int unclaimed = 0;
        for (FLocation here : chunks) {
            LandUnclaimEvent event = new LandUnclaimEvent(here, target, playerSender.fPlayer());
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) continue;
            Board.board().unclaim(here);
            unclaimed++;
        }

        if (FactionsPlugin.instance().conf().logging().isLandUnclaims()) {
            AbstractFactionsPlugin.instance().log(
                    "ADMIN " + player.getName() + " unclaimed ALL " + unclaimed + " chunks from " + target.tag()
            );
        }

        playerSender.sendRichMessage("<green>Unclaimed <yellow>" + unclaimed
                + "</yellow> chunks from <yellow>" + safeTag + "</yellow>.");
    }
}
