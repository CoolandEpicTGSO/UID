package dev.kitteh.factions.listener;

import dev.kitteh.factions.FPlayer;
import dev.kitteh.factions.FPlayers;
import dev.kitteh.factions.Faction;
import dev.kitteh.factions.FactionsPlugin;
import dev.kitteh.factions.command.defaults.CmdUpgrades;
import dev.kitteh.factions.plugin.AbstractFactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/**
 * Optional integration with RegentHeadHunting. When that plugin's
 * {@code HHXpAwardEvent} fires, applies a per-tier XP multiplier from the
 * faction's HH XP Boost upgrade (KEY_HH_XP_BOOST).
 *
 * Wired via reflection so the Factions plugin compiles cleanly without RH
 * on the classpath. If RH isn't installed (or is too old to expose the
 * event), this initializer silently no-ops.
 */
public final class ListenHHXpBoost {

    private static final String EVENT_CLASS = "me.tgsocodes.regentHeadHunting.events.HHXpAwardEvent";

    // ── Diagnostic counters (used by /f admin hhxp) ───────────────────────────
    private static volatile boolean registered = false;
    private static volatile String registrationFailureReason = "(integration not yet attempted)";
    private static final AtomicInteger eventsSeen = new AtomicInteger();
    private static final AtomicInteger skippedNoFaction = new AtomicInteger();
    private static final AtomicInteger skippedTierZero = new AtomicInteger();
    private static final AtomicInteger skippedRoundedToZero = new AtomicInteger();
    private static final AtomicInteger applied = new AtomicInteger();
    private static final AtomicReference<String> lastAppliedSummary = new AtomicReference<>("(none yet)");

    public static boolean isRegistered() { return registered; }
    public static String registrationStatus() { return registered ? "registered" : registrationFailureReason; }
    public static int eventsSeen() { return eventsSeen.get(); }
    public static int skippedNoFaction() { return skippedNoFaction.get(); }
    public static int skippedTierZero() { return skippedTierZero.get(); }
    public static int skippedRoundedToZero() { return skippedRoundedToZero.get(); }
    public static int applied() { return applied.get(); }
    public static String lastApplied() { return lastAppliedSummary.get(); }

    private ListenHHXpBoost() {}

    public static void registerIfAvailable(AbstractFactionsPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("RegentHeadHunting") == null) {
            registrationFailureReason = "RegentHeadHunting plugin not loaded";
            return;
        }

        Class<?> eventClass;
        Method getPlayer;
        Method getAmount;
        Method setAmount;
        Method isCancelled;
        try {
            eventClass = Class.forName(EVENT_CLASS);
            getPlayer = eventClass.getMethod("getPlayer");
            getAmount = eventClass.getMethod("getAmount");
            setAmount = eventClass.getMethod("setAmount", long.class);
            isCancelled = eventClass.getMethod("isCancelled");
        } catch (Exception e) {
            registrationFailureReason = "HHXpAwardEvent class missing on RH — needs newer RH build (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")";
            plugin.getLogger().info("RegentHeadHunting is loaded but HHXpAwardEvent is unavailable — faction HH XP Boost upgrade will not apply. Update RH to enable this integration.");
            return;
        }

        @SuppressWarnings("unchecked")
        Class<? extends Event> eventClassTyped = (Class<? extends Event>) eventClass;

        Listener listener = new Listener() {};

        EventExecutor executor = (l, event) -> {
            if (!eventClassTyped.isInstance(event)) return;
            try {
                if ((Boolean) isCancelled.invoke(event)) return;

                eventsSeen.incrementAndGet();

                Object playerObj = getPlayer.invoke(event);
                if (!(playerObj instanceof Player player)) return;

                FPlayer fPlayer = FPlayers.fPlayers().get(player);
                if (!fPlayer.hasFaction()) {
                    skippedNoFaction.incrementAndGet();
                    return;
                }

                Faction faction = fPlayer.faction();
                if (!faction.isNormal()) {
                    skippedNoFaction.incrementAndGet();
                    return;
                }

                int tier = faction.customUpgradeTier(CmdUpgrades.KEY_HH_XP_BOOST);
                long current = (Long) getAmount.invoke(event);
                if (tier <= 0) {
                    skippedTierZero.incrementAndGet();
                    lastAppliedSummary.set(player.getName() + " (" + faction.tag() + " tier 0): "
                            + current + " XP, no boost applied");
                    return;
                }

                double bonus = bonusMultiplierForTier(tier);
                if (bonus <= 0) {
                    skippedTierZero.incrementAndGet();
                    return;
                }

                long bonusAmount = Math.round(current * bonus);
                if (bonusAmount <= 0) {
                    skippedRoundedToZero.incrementAndGet();
                    lastAppliedSummary.set(player.getName() + " (" + faction.tag() + " tier " + tier + "): "
                            + current + " XP × " + bonus + " rounded to 0 bonus");
                    return;
                }
                long total = current + bonusAmount;
                setAmount.invoke(event, total);
                applied.incrementAndGet();
                lastAppliedSummary.set(player.getName() + " (" + faction.tag() + " tier " + tier + "): "
                        + current + " → " + total + " XP (+" + bonusAmount + ", " + (int)(bonus*100) + "%)");
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to apply faction HH XP Boost", e);
            }
        };

        Bukkit.getPluginManager().registerEvent(eventClassTyped, listener, EventPriority.NORMAL, executor, plugin);
        registered = true;
        registrationFailureReason = "(registered)";
        plugin.getLogger().info("RegentHeadHunting HH XP Boost integration enabled.");
    }

    /**
     * Bonus multiplier for a given upgrade tier. Tier 1 = +10%, tier 2 = +20%, ...
     * Adjust here (or wire to a config field) if you want a different progression.
     */
    private static double bonusMultiplierForTier(int tier) {
        // 4-tier default: +10%, +20%, +30%, +50%
        return switch (tier) {
            case 1 -> 0.10;
            case 2 -> 0.20;
            case 3 -> 0.30;
            case 4 -> 0.50;
            default -> Math.min(1.0, tier * 0.10);
        };
    }
}
