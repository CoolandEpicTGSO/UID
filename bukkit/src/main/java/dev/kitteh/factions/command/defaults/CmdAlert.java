package dev.kitteh.factions.command.defaults;

import dev.kitteh.factions.FPlayer;
import dev.kitteh.factions.Faction;
import dev.kitteh.factions.command.Cloudy;
import dev.kitteh.factions.command.Cmd;
import dev.kitteh.factions.command.Sender;
import dev.kitteh.factions.permissible.Role;
import dev.kitteh.factions.util.ComponentDispatcher;
import dev.kitteh.factions.util.Mini;
import dev.kitteh.factions.util.Permission;
import dev.kitteh.factions.util.TriConsumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;
import org.incendo.cloud.parser.standard.StringParser;

/**
 * /f alert &lt;message&gt; — Leader-only. Pops a title on every online faction member's screen.
 */
public class CmdAlert implements Cmd {

    private static final int FADE_IN_TICKS = 10;     // 0.5s
    private static final int STAY_TICKS = 60;        // 3s
    private static final int FADE_OUT_TICKS = 20;    // 1s

    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        return (manager, builder, help) -> manager.command(
                builder.literal("alert")
                        .commandDescription(Cloudy.desc("Send a faction-wide alert title (leader only)"))
                        .permission(builder.commandPermission()
                                .and(Cloudy.hasPermission(Permission.ALERT))
                                .and(Cloudy.hasFaction())
                                .and(Cloudy.isAtLeastRole(Role.ADMIN)))
                        .required("message", StringParser.greedyStringParser())
                        .handler(this::handle)
        );
    }

    private void handle(CommandContext<Sender> context) {
        FPlayer sender = ((Sender.Player) context.sender()).fPlayer();
        Faction faction = sender.faction();
        String raw = context.<String>get("message").trim();

        if (raw.isEmpty()) {
            sender.sendRichMessage("<red>Alert message cannot be empty.");
            return;
        }
        if (raw.length() > 80) {
            sender.sendRichMessage("<red>Alert message is too long (max 80 chars).");
            return;
        }

        Component title = Component.text("⚠ Faction Alert", NamedTextColor.RED);
        // Parse the message through MiniMessage-limited so leaders can colour their alerts
        Component messageComponent = Mini.parseLimited(raw);
        // Subtitle: "<IGN>: <message>"
        Component subtitle = Component.text(sender.name() + ": ", NamedTextColor.YELLOW)
                .append(messageComponent);

        // Send the title + a chat copy + a small ding to every online member
        for (FPlayer member : faction.membersOnline(true)) {
            Player p = member.asPlayer();
            if (p == null) continue;
            ComponentDispatcher.sendTitle(p, title, subtitle, FADE_IN_TICKS, STAY_TICKS, FADE_OUT_TICKS);
            ComponentDispatcher.send(p, Component.text("⚠ ", NamedTextColor.RED)
                    .append(Component.text("[" + faction.tag() + " alert] ", NamedTextColor.GOLD))
                    .append(Component.text(sender.name() + ": ", NamedTextColor.YELLOW))
                    .append(messageComponent));
            try {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.2f);
            } catch (Throwable ignored) {}
        }

        // Audit log
        faction.recordFactionLog(dev.kitteh.factions.audit.AuditEntry.generic(
                sender.name(), sender.uniqueId(), "alert",
                sender.name() + " sent a faction alert: " + raw
        ));

        sender.sendRichMessage("<green>Alert sent to your faction.");
    }
}
