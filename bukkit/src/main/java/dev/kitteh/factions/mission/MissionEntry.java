package dev.kitteh.factions.mission;

/**
 * One mission as it appears in {@code missions.conf}.
 *
 * <p>This is NOT loaded directly by the reflective Configurate Loader (it can't handle nested
 * complex objects in collections). Instead, {@link MissionsConfig} keeps a {@code Map<String,
 * Object>} that Configurate populates with raw {@code Map<String, Object>} values, and we
 * construct {@code MissionEntry} instances from those raw maps at build time.</p>
 */
public class MissionEntry {
    public String displayName = "";
    public String description = "";
    public String category = "DAILY";          // DAILY / BI_DAILY / WEEKLY / MAP
    public String type = "PLAYER_KILLS";       // see MissionType
    public String targetKey = "";              // empty = "totals"
    public long requiredAmount = 1L;
    public long xpReward = 0L;
    public double moneyReward = 0.0;
    public int coinReward = 0;
    public int difficulty = 0;                 // 0=Easy, 1=Medium, 2=Hard
}
