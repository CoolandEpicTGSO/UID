package dev.kitteh.factions.mission;

/**
 * Runtime mission record. Built from MissionsConfig at load time.
 * NOT a Configurate-loaded class — only used in memory.
 */
public record MissionDefinition(
        String id,
        String displayName,
        String description,
        MissionCategory category,
        MissionType type,
        String targetKey,
        long requiredAmount,
        long xpReward,
        double moneyReward,
        int coinReward,
        int difficulty,
        boolean boosterReward
) {
    public String difficultyLabel() {
        return switch (difficulty) {
            case 0 -> "Easy";
            case 1 -> "Medium";
            case 2 -> "Hard";
            default -> "?";
        };
    }
}
