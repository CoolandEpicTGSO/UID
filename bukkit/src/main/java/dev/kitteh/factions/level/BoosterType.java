package dev.kitteh.factions.level;

import org.bukkit.Material;

/**
 * The four faction booster types provided by RegentHeadHunting's
 * {@code hhadmin booster faction <faction> <type> <multiplier> <minutes>} command.
 *
 * <p>The {@code commandKey} is what gets passed to the hhadmin command. The display
 * fields drive the GUI rendering.</p>
 */
public enum BoosterType {
    FISHING_CAST_TIME("fishing_cast_time", "Fishing Cast Time", Material.FISHING_ROD),
    FISHING_TREASURE("fishing_treasure", "Fishing Treasure",   Material.HEART_OF_THE_SEA),
    MONEY("money", "Money", Material.GOLD_INGOT),
    XP("xp", "XP", Material.EXPERIENCE_BOTTLE);

    private final String commandKey;
    private final String displayName;
    private final Material icon;

    BoosterType(String commandKey, String displayName, Material icon) {
        this.commandKey = commandKey;
        this.displayName = displayName;
        this.icon = icon;
    }

    /** Token passed to the hhadmin booster command. */
    public String commandKey() {
        return commandKey;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    /** Resolve a stored enum-name token back to a BoosterType, or null if unknown. */
    public static BoosterType byName(String name) {
        if (name == null) return null;
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
