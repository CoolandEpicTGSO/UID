package dev.kitteh.factions.listener;

import dev.kitteh.factions.FPlayer;
import dev.kitteh.factions.FPlayers;
import dev.kitteh.factions.Faction;
import dev.kitteh.factions.mission.MissionManager;
import dev.kitteh.factions.mission.MissionType;
import dev.kitteh.factions.plugin.AbstractFactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Optional integration with RegentHeadHunting. When a player sells heads via RH's
 * {@code HHHeadSellEvent}, increments the {@code HEADS_SOLD} faction mission progress
 * by the number of heads sold. If the event reports a single mob type, also feeds
 * that as a target key so per-mob-type missions can match.
 *
 * <p>Wired via reflection so the Factions plugin compiles cleanly without RH on the
 * classpath. If RH isn't installed (or is too old to expose the event), this initialiser
 * silently no-ops.</p>
 */
public final class ListenHHHeadSell {

    private static final String EVENT_CLASS = "me.tgsocodes.regentHeadHunting.events.HHHeadSellEvent";

    private ListenHHHeadSell() {}

    public static void registerIfAvailable(AbstractFactionsPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("RegentHeadHunting") == null) return;

        Class<?> eventClass;
        Method getPlayer;
        Method getHeadsSold;
        Method getMobType; // may be null on older RH builds
        try {
            eventClass = Class.forName(EVENT_CLASS);
            getPlayer = eventClass.getMethod("getPlayer");
            getHeadsSold = eventClass.getMethod("getHeadsSold");
        } catch (Exception e) {
            plugin.getLogger().info("RegentHeadHunting is loaded but HHHeadSellEvent is unavailable — HEADS_SOLD mission progress won't increment. Update RH to enable this integration.");
            return;
        }
        Method maybeGetMobType;
        try {
            maybeGetMobType = eventClass.getMethod("getMobType");
        } catch (NoSuchMethodException nsme) {
            maybeGetMobType = null;
        }
        getMobType = maybeGetMobType;

        @SuppressWarnings("unchecked")
        Class<? extends Event> eventClassTyped = (Class<? extends Event>) eventClass;

        Listener listener = new Listener() {};

        Method getMobTypeFinal = getMobType;
        EventExecutor executor = (l, event) -> {
            if (!eventClassTyped.isInstance(event)) return;
            try {
                Object playerObj = getPlayer.invoke(event);
                if (!(playerObj instanceof Player player)) return;

                int headsSold = (Integer) getHeadsSold.invoke(event);
                if (headsSold <= 0) return;

                FPlayer fPlayer = FPlayers.fPlayers().get(player);
                if (!fPlayer.hasFaction()) return;
                Faction faction = fPlayer.faction();
                if (!faction.isNormal()) return;

                // Single increment call — pass the mob type as key when available so
                // both type-specific and totals missions get credited (totals match
                // because they have empty targetKey). Fall back to empty key for
                // mixed sells where the type isn't known.
                String key = "";
                if (getMobTypeFinal != null) {
                    Object mobObj = getMobTypeFinal.invoke(event);
                    if (mobObj != null) {
                        try {
                            Method nameMethod = mobObj.getClass().getMethod("name");
                            Object name = nameMethod.invoke(mobObj);
                            if (name instanceof String s) key = s;
                        } catch (Exception ignored) {}
                    }
                }
                MissionManager.increment(faction, MissionType.HEADS_SOLD, key, headsSold);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to increment HEADS_SOLD mission progress", e);
            }
        };

        Bukkit.getPluginManager().registerEvent(eventClassTyped, listener, EventPriority.MONITOR, executor, plugin);
        plugin.getLogger().info("RegentHeadHunting HEADS_SOLD mission integration enabled.");
    }
}
