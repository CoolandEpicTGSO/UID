package dev.kitteh.factions.mission;

/**
 * Mission category — drives the reset cadence and rotation pick count.
 *
 * <p>Each category has a *pool* of all defined missions (via MissionsConfig) and a
 * *pick count* — only that many are randomly chosen as active per faction per reset
 * window. The selection is seeded by faction ID + reset timestamp so it stays stable
 * across server restarts in the same window.</p>
 */
public enum MissionCategory {
    DAILY("Daily", 24L * 3600L * 1000L, 4),
    BI_DAILY("Bi-Daily", 48L * 3600L * 1000L, 3),
    WEEKLY("Weekly", 7L * 24L * 3600L * 1000L, 3),
    /** Map missions never auto-reset. */
    MAP("Map", -1L, 5);

    private final String label;
    private final long resetIntervalMs;
    private final int pickCount;

    MissionCategory(String label, long resetIntervalMs, int pickCount) {
        this.label = label;
        this.resetIntervalMs = resetIntervalMs;
        this.pickCount = pickCount;
    }

    public String label() {
        return label;
    }

    public long resetIntervalMs() {
        return resetIntervalMs;
    }

    /** How many missions are randomly active per faction per reset window. */
    public int pickCount() {
        return pickCount;
    }

    public String id() {
        return this.name();
    }
}
