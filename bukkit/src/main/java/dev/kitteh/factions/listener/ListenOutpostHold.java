package dev.kitteh.factions.listener;

import dev.kitteh.factions.Faction;
import dev.kitteh.factions.Factions;
import dev.kitteh.factions.mission.MissionManager;
import dev.kitteh.factions.mission.MissionType;
import dev.kitteh.factions.plugin.AbstractFactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Optional integration with HytopOutpost. When a faction holds an outpost for
 * another minute (one event per held outpost per minute), increments the
 * {@code OUTPOST_HOLD_MINUTES} faction mission progress by 1.
 *
 * <p>Wired via reflection so the Factions plugin compiles cleanly without HytopOutpost
 * on the classpath.</p>
 */
public final class ListenOutpostHold {

    private static final String EVENT_CLASS = "me.tgsocodes.hytopOutpost.events.OutpostHoldMinuteEvent";

    private ListenOutpostHold() {}

    public static void registerIfAvailable(AbstractFactionsPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("HytopOutpost") == null) return;

        Class<?> eventClass;
        Method getFactionId;
        try {
            eventClass = Class.forName(EVENT_CLASS);
            getFactionId = eventClass.getMethod("getFactionId");
        } catch (Exception e) {
            plugin.getLogger().info("HytopOutpost is loaded but OutpostHoldMinuteEvent is unavailable — OUTPOST_HOLD_MINUTES mission progress won't increment. Update HytopOutpost to enable this integration.");
            return;
        }

        @SuppressWarnings("unchecked")
        Class<? extends Event> eventClassTyped = (Class<? extends Event>) eventClass;

        Listener listener = new Listener() {};

        EventExecutor executor = (l, event) -> {
            if (!eventClassTyped.isInstance(event)) return;
            try {
                Object factionIdObj = getFactionId.invoke(event);
                if (!(factionIdObj instanceof String factionIdStr) || factionIdStr.isEmpty()) return;

                int factionId;
                try {
                    factionId = Integer.parseInt(factionIdStr);
                } catch (NumberFormatException nfe) {
                    return;
                }

                Faction faction = Factions.factions().get(factionId);
                if (faction == null || !faction.isNormal()) return;

                MissionManager.increment(faction, MissionType.OUTPOST_HOLD_MINUTES, 1);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to increment OUTPOST_HOLD_MINUTES mission progress", e);
            }
        };

        Bukkit.getPluginManager().registerEvent(eventClassTyped, listener, EventPriority.MONITOR, executor, plugin);
        plugin.getLogger().info("HytopOutpost OUTPOST_HOLD_MINUTES mission integration enabled.");
    }
}
