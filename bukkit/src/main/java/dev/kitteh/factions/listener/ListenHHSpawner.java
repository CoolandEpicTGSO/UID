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
 * Optional integration with RegentHeadHunting. Listens for spawner-shop purchases
 * ({@code HHSpawnerPurchaseEvent}) and fragment redemptions ({@code HHSpawnerRedeemEvent})
 * and credits {@link MissionType#HH_SPAWNERS_BOUGHT} progress.
 *
 * <p>Key conventions:</p>
 * <ul>
 *   <li>{@code SHOP} — purchased from the regular spawner shop</li>
 *   <li>{@code BASIC} / {@code ADVANCED} / {@code EXTREME} — redeemed via mystery
 *       spawner fragments</li>
 * </ul>
 *
 * <p>Wired via reflection so the Factions plugin compiles cleanly without RH on the
 * classpath. If RH isn't installed (or is too old), this initialiser silently no-ops.</p>
 */
public final class ListenHHSpawner {

    private static final String PURCHASE_EVENT_CLASS = "me.tgsocodes.regentHeadHunting.events.HHSpawnerPurchaseEvent";
    private static final String REDEEM_EVENT_CLASS = "me.tgsocodes.regentHeadHunting.events.HHSpawnerRedeemEvent";

    private ListenHHSpawner() {}

    public static void registerIfAvailable(AbstractFactionsPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("RegentHeadHunting") == null) return;

        boolean any = false;
        any |= registerPurchase(plugin);
        any |= registerRedeem(plugin);
        if (!any) {
            plugin.getLogger().info("RegentHeadHunting is loaded but spawner events are unavailable — HH_SPAWNERS_BOUGHT mission progress won't increment. Update RH to enable this integration.");
        }
    }

    private static boolean registerPurchase(AbstractFactionsPlugin plugin) {
        Class<?> eventClass;
        Method getPlayer;
        try {
            eventClass = Class.forName(PURCHASE_EVENT_CLASS);
            getPlayer = eventClass.getMethod("getPlayer");
        } catch (Exception e) {
            return false;
        }

        @SuppressWarnings("unchecked")
        Class<? extends Event> eventClassTyped = (Class<? extends Event>) eventClass;

        Listener listener = new Listener() {};
        EventExecutor executor = (l, event) -> {
            if (!eventClassTyped.isInstance(event)) return;
            try {
                Object playerObj = getPlayer.invoke(event);
                if (!(playerObj instanceof Player player)) return;

                FPlayer fp = FPlayers.fPlayers().get(player);
                if (!fp.hasFaction()) return;
                Faction faction = fp.faction();
                if (!faction.isNormal()) return;

                MissionManager.increment(faction, MissionType.HH_SPAWNERS_BOUGHT, "SHOP", 1);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to credit HH spawner purchase mission progress", e);
            }
        };

        Bukkit.getPluginManager().registerEvent(eventClassTyped, listener, EventPriority.MONITOR, executor, plugin);
        plugin.getLogger().info("RegentHeadHunting HH_SPAWNERS_BOUGHT (shop) integration enabled.");
        return true;
    }

    private static boolean registerRedeem(AbstractFactionsPlugin plugin) {
        Class<?> eventClass;
        Method getPlayer;
        Method getTier;
        try {
            eventClass = Class.forName(REDEEM_EVENT_CLASS);
            getPlayer = eventClass.getMethod("getPlayer");
            getTier = eventClass.getMethod("getTier");
        } catch (Exception e) {
            return false;
        }

        @SuppressWarnings("unchecked")
        Class<? extends Event> eventClassTyped = (Class<? extends Event>) eventClass;

        Listener listener = new Listener() {};
        EventExecutor executor = (l, event) -> {
            if (!eventClassTyped.isInstance(event)) return;
            try {
                Object playerObj = getPlayer.invoke(event);
                if (!(playerObj instanceof Player player)) return;

                FPlayer fp = FPlayers.fPlayers().get(player);
                if (!fp.hasFaction()) return;
                Faction faction = fp.faction();
                if (!faction.isNormal()) return;

                Object tierObj = getTier.invoke(event);
                String tier = tierObj instanceof String s ? s.toUpperCase() : "";
                if (tier.isEmpty()) return;

                MissionManager.increment(faction, MissionType.HH_SPAWNERS_BOUGHT, tier, 1);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to credit HH spawner redeem mission progress", e);
            }
        };

        Bukkit.getPluginManager().registerEvent(eventClassTyped, listener, EventPriority.MONITOR, executor, plugin);
        plugin.getLogger().info("RegentHeadHunting HH_SPAWNERS_BOUGHT (mystery) integration enabled.");
        return true;
    }
}
