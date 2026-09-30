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
 * Optional integration with HytopOutpost. When a faction captures an outpost via
 * {@code OutpostCaptureEvent}, increments the {@code OUTPOST_CAPTURES} faction
 * mission progress by 1.
 *
 * <p>Wired via reflection so the Factions plugin compiles cleanly without HytopOutpost
 * on the classpath. If the plugin isn't installed (or is too old to expose the event),
 * this initialiser silently no-ops.</p>
 */
public final class ListenOutpostCapture {

    private static final String EVENT_CLASS = "me.tgsocodes.hytopOutpost.events.OutpostCaptureEvent";

    private ListenOutpostCapture() {}

    public static void registerIfAvailable(AbstractFactionsPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("HytopOutpost") == null) return;

        Class<?> eventClass;
        Method getFactionId;
        try {
            eventClass = Class.forName(EVENT_CLASS);
            getFactionId = eventClass.getMethod("getFactionId");
        } catch (Exception e) {
            plugin.getLogger().info("HytopOutpost is loaded but OutpostCaptureEvent is unavailable — OUTPOST_CAPTURES mission progress won't increment. Update HytopOutpost to enable this integration.");
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

                MissionManager.increment(faction, MissionType.OUTPOST_CAPTURES, 1);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to increment OUTPOST_CAPTURES mission progress", e);
            }
        };

        Bukkit.getPluginManager().registerEvent(eventClassTyped, listener, EventPriority.MONITOR, executor, plugin);
        plugin.getLogger().info("HytopOutpost OUTPOST_CAPTURES mission integration enabled.");
    }
}
