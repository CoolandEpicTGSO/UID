package dev.kitteh.factions.level;

import dev.kitteh.factions.Faction;
import dev.kitteh.factions.plugin.AbstractFactionsPlugin;
import org.bukkit.Bukkit;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Rolls, grants, and activates faction boosters.
 *
 * <p>Boosters are rolled at <em>earn time</em> (level-up or mission reward): a random
 * {@link BoosterType}, a multiplier between 1.25x and 2.0x, and a duration between
 * 20 and 60 minutes. The rolled values are stored in the faction's booster inventory
 * (see {@link Faction#boosterInventory()}) so players see exactly what they have.</p>
 *
 * <p>Activation dispatches RegentHeadHunting's admin command from console:
 * {@code hhadmin booster faction <factionTag> <type> <multiplier> <minutes>}.</p>
 */
public final class BoosterManager {

    /** Multiplier roll bounds (inclusive), stored as ×100 integers. */
    private static final int MIN_MULT_X100 = 125; // 1.25x
    private static final int MAX_MULT_X100 = 200; // 2.00x

    /** Duration roll bounds in minutes (inclusive). */
    private static final int MIN_MINUTES = 20;
    private static final int MAX_MINUTES = 60;

    private BoosterManager() {}

    /**
     * Roll one random booster and add it to the faction's inventory.
     * Multiplier is rolled in 0.05 steps for tidy values (1.25, 1.30, … 2.00).
     */
    public static void grantRandomBooster(Faction faction) {
        if (!faction.isNormal()) return;

        BoosterType[] types = BoosterType.values();
        BoosterType type = types[ThreadLocalRandom.current().nextInt(types.length)];

        // Roll multiplier in 5-point (×100) steps: 125,130,...,200 → 16 possible values
        int steps = (MAX_MULT_X100 - MIN_MULT_X100) / 5;
        int multX100 = MIN_MULT_X100 + ThreadLocalRandom.current().nextInt(steps + 1) * 5;

        int minutes = ThreadLocalRandom.current().nextInt(MIN_MINUTES, MAX_MINUTES + 1);

        faction.addBoosterToInventory(type.name(), multX100, minutes, 1);

        faction.sendRichMessage(
                "<aqua><bold>✦ FACTION BOOSTER EARNED!</bold></aqua> <yellow>" + type.displayName()
                        + "</yellow> <gray>(" + formatMultiplier(multX100) + " for " + minutes
                        + "m) <gray>— saved to <white>/f effects</white>."
        );
    }

    /**
     * Activate a stored booster: consume it from the inventory and dispatch the
     * RegentHeadHunting admin command from console. Returns true on success.
     */
    public static boolean activateBooster(Faction faction, BoosterType type, int multX100, int minutes) {
        if (!faction.consumeBoosterFromInventory(type.name(), multX100, minutes)) {
            return false;
        }

        String factionTag = faction.tag();
        String multiplier = formatMultiplierRaw(multX100);
        String command = "hhadmin booster faction " + factionTag + " "
                + type.commandKey() + " " + multiplier + " " + minutes;

        // Dispatch on the main thread from console
        Bukkit.getScheduler().runTask(AbstractFactionsPlugin.instance(), () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));

        faction.sendRichMessage(
                "<aqua><bold>✦ BOOSTER ACTIVATED!</bold></aqua> <yellow>" + type.displayName()
                        + "</yellow> <gray>" + formatMultiplier(multX100) + " for " + minutes + " minutes."
        );
        return true;
    }

    /** Pretty multiplier with an 'x' suffix, e.g. "1.5x". */
    public static String formatMultiplier(int multX100) {
        return formatMultiplierRaw(multX100) + "x";
    }

    /** Raw multiplier string with no suffix, e.g. "1.5" — for the command argument. */
    public static String formatMultiplierRaw(int multX100) {
        if (multX100 % 100 == 0) return String.valueOf(multX100 / 100);
        // Trim trailing zero: 150 -> "1.5", 125 -> "1.25"
        String s = String.format(java.util.Locale.ROOT, "%.2f", multX100 / 100.0);
        if (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s;
    }
}
