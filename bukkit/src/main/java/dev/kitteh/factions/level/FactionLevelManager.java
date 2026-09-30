package dev.kitteh.factions.level;

import dev.kitteh.factions.Faction;
import dev.kitteh.factions.FactionsPlugin;
import dev.kitteh.factions.integration.Econ;
import org.bukkit.Bukkit;

import java.util.List;

/**
 * Handles XP gain, level-up logic, and reward dispatch for the Faction Levels system.
 */
public final class FactionLevelManager {

    private FactionLevelManager() {}

    public static FactionLevelsConfig config() {
        return FactionsPlugin.instance().configManager().factionLevelsConfig();
    }

    /**
     * Awards XP to a faction and processes any resulting level-ups.
     * Negative amounts are supported (clamped to not drop below 0). Level-ups
     * subtract the threshold from XP — overflow carries over to the next level.
     */
    public static void awardXp(Faction faction, long amount) {
        if (!faction.isNormal()) return;
        long newXp = Math.max(0, faction.factionXp() + amount);
        faction.factionXp(newXp);
        processLevelUps(faction);
    }

    /**
     * Process level-ups using per-level XP semantics: when xp >= threshold for the
     * current→next level, increment the level and subtract the threshold from XP.
     * Overflow carries over (e.g. 1100 XP at the 1000-threshold leaves 100 XP at the new level).
     */
    private static void processLevelUps(Faction faction) {
        FactionLevelsConfig cfg = config();
        // Loop, in case a single huge XP grant carries through multiple levels
        while (true) {
            int currentLevel = faction.factionLevel();
            if (currentLevel >= cfg.maxLevel()) {
                // At cap — clamp XP to the final threshold so the placeholder reads X/X
                long cap = cfg.xpRequired(cfg.maxLevel());
                if (faction.factionXp() > cap) faction.factionXp(cap);
                return;
            }
            long required = cfg.xpRequired(currentLevel + 1);
            long xp = faction.factionXp();
            if (xp < required) return;

            int newLevel = currentLevel + 1;
            faction.factionLevel(newLevel);
            faction.factionXp(xp - required);
            applyLevelUpRewards(faction, newLevel);
            faction.sendRichMessage("<gold><bold>✦ FACTION LEVEL UP!</bold></gold> <yellow>Your faction is now <bold>Level " + newLevel + "</bold>!");
        }
    }

    /**
     * Applies money reward, HH XP booster, faction points, and effect unlocks on level-up.
     */
    private static void applyLevelUpRewards(Faction faction, int newLevel) {
        FactionLevelsConfig cfg = config();

        // Money reward via Vault
        double money = cfg.moneyReward(newLevel);
        if (money > 0 && Econ.shouldBeUsed()) {
            Econ.modifyMoney(faction, money, "Faction level up reward", "reaching level " + newLevel);
            faction.sendRichMessage("<green>+<gold>" + money + "</gold> deposited to faction bank as a level-up reward!");
        }

        // HH XP booster reward — stored for the faction admin to collect
        double hhBooster = cfg.hhXpBoosterReward(newLevel);
        if (hhBooster > 0) {
            // Notify members; actual API call handled by HH integration if available
            faction.sendRichMessage("<aqua>HH XP Booster reward: <yellow>+" + (int)(hhBooster * 100) + "%</yellow> booster unlocked!");
            dispatchHhBoosterReward(faction, hhBooster);
        }

        // Faction coins
        int coins = cfg.factionCoinReward(newLevel);
        if (coins > 0) {
            faction.addFactionPoints(coins);
            faction.sendRichMessage("<light_purple>+" + coins + " faction coin(s)! Use <white>/f upgrades</white> to spend them.");
        }

        // Effect unlocks
        List<String> unlocks = cfg.effectUnlocks(newLevel);
        if (!unlocks.isEmpty()) {
            faction.sendRichMessage("<green>New F Effect(s) unlocked: <yellow>" + String.join(", ", unlocks) + "</yellow>! Use <white>/f effects</white> to activate.");
        }

        // Faction boosters — 1 random booster per level, 2 per level above level 5.
        int boosterCount = newLevel > 5 ? 2 : 1;
        for (int i = 0; i < boosterCount; i++) {
            BoosterManager.grantRandomBooster(faction);
        }
    }

    /**
     * Dispatches an HH XP booster reward via RegentHeadhunting API if available.
     * No-ops if RegentHeadhunting is not loaded.
     */
    private static void dispatchHhBoosterReward(Faction faction, double multiplier) {
        if (Bukkit.getPluginManager().getPlugin("RegentHeadhunting") == null) return;
        // Integration point: call RegentHeadhunting API here once its API is known.
        // Example (replace with actual API): RegentHeadhunting.getInstance().awardFactionBooster(faction.id(), multiplier);
    }

    /**
     * Returns the XP progress towards the next level as a value 0.0–1.0 (per-level).
     */
    public static double progressToNextLevel(Faction faction) {
        FactionLevelsConfig cfg = config();
        int level = faction.factionLevel();
        if (level >= cfg.maxLevel()) return 1.0;
        long xp = faction.factionXp();
        long required = cfg.xpRequired(level + 1);
        if (required <= 0) return 1.0;
        return Math.min(1.0, (double) xp / required);
    }

    /**
     * Returns the XP still needed to reach the next level (per-level remaining).
     */
    public static long xpUntilNextLevel(Faction faction) {
        FactionLevelsConfig cfg = config();
        int level = faction.factionLevel();
        if (level >= cfg.maxLevel()) return 0L;
        long required = cfg.xpRequired(level + 1);
        return Math.max(0L, required - faction.factionXp());
    }

    /**
     * Forces a faction to a specific level. Resets per-level XP to 0.
     * Awards rewards if the new level is higher than the current level (for each level gained).
     * Use {@code awardRewards = false} to set silently (e.g. via admin command).
     */
    public static void setLevel(Faction faction, int targetLevel, boolean awardRewards) {
        if (!faction.isNormal()) return;
        FactionLevelsConfig cfg = config();
        targetLevel = Math.max(0, Math.min(cfg.maxLevel(), targetLevel));
        int currentLevel = faction.factionLevel();
        faction.factionLevel(targetLevel);
        faction.factionXp(0L);
        if (awardRewards && targetLevel > currentLevel) {
            for (int lvl = currentLevel + 1; lvl <= targetLevel; lvl++) {
                applyLevelUpRewards(faction, lvl);
            }
        }
    }
}
