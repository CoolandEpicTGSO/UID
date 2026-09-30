package dev.kitteh.factions.mission;

import dev.kitteh.factions.Faction;
import dev.kitteh.factions.FactionsPlugin;
import dev.kitteh.factions.integration.Econ;
import dev.kitteh.factions.level.FactionLevelManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public final class MissionManager {

    private MissionManager() {}

    public static List<MissionDefinition> allMissions() {
        return FactionsPlugin.instance().configManager().missionsConfig().buildDefinitions();
    }

    /** Full pool for a category — used for rotation picks and admin views, not the per-faction GUI. */
    public static List<MissionDefinition> missionsFor(MissionCategory category) {
        return allMissions().stream()
                .filter(m -> m.category() == category)
                .collect(Collectors.toList());
    }

    /**
     * Returns the active mission rotation for a faction in a given category. Picks
     * {@link MissionCategory#pickCount()} missions from the pool, deterministically
     * seeded by faction id + last reset epoch so the picks stay stable for the
     * whole window (and across server restarts).
     *
     * <p>If the pool is smaller than the pick count, returns the entire pool.</p>
     */
    public static List<MissionDefinition> activeMissions(Faction faction, MissionCategory category) {
        // Ensure window is fresh and active list is rolled if needed
        maybeReset(faction, category);
        rollIfMissing(faction, category);

        List<String> activeIds = faction.missionActiveIds(category.id());
        if (activeIds.isEmpty()) return Collections.emptyList();

        Map<String, MissionDefinition> byId = new HashMap<>();
        for (MissionDefinition m : allMissions()) byId.put(m.id(), m);

        List<MissionDefinition> result = new ArrayList<>();
        for (String id : activeIds) {
            MissionDefinition def = byId.get(id);
            if (def != null) result.add(def);
        }
        return result;
    }

    /**
     * Returns a faction's progress on a specific mission, resetting stale progress first.
     */
    public static long progress(Faction faction, MissionDefinition mission) {
        maybeReset(faction, mission.category());
        return faction.missionProgress(mission.id());
    }

    /**
     * Has the faction met the required amount?
     */
    public static boolean isCompleted(Faction faction, MissionDefinition mission) {
        return progress(faction, mission) >= mission.requiredAmount();
    }

    /**
     * Increment progress on every <em>active</em> mission of the given type for this faction.
     * Only missions in the faction's current rotation pick are credited — the pool of
     * inactive missions is ignored. Auto-fires completion rewards when crossing the
     * threshold.
     */
    public static void increment(Faction faction, MissionType type, String key, long amount) {
        if (!faction.isNormal()) return;
        for (MissionCategory category : MissionCategory.values()) {
            for (MissionDefinition mission : activeMissions(faction, category)) {
                if (mission.type() != type) continue;
                if (!mission.targetKey().isEmpty() && !mission.targetKey().equalsIgnoreCase(key)) continue;

                long current = faction.missionProgress(mission.id());
                if (current >= mission.requiredAmount()) continue; // already done

                long newVal = Math.min(current + amount, mission.requiredAmount());
                faction.missionProgress(mission.id(), newVal);

                if (newVal >= mission.requiredAmount()) {
                    completeMission(faction, mission);
                }
            }
        }
    }

    /** Convenience overload with no key filter. */
    public static void increment(Faction faction, MissionType type, long amount) {
        increment(faction, type, "", amount);
    }

    private static void completeMission(Faction faction, MissionDefinition mission) {
        StringBuilder rewards = new StringBuilder();
        rewards.append(" <gray>| +").append(mission.xpReward()).append(" XP");
        if (mission.moneyReward() > 0) {
            rewards.append(" | +$").append((long) mission.moneyReward());
        }
        if (mission.coinReward() > 0) {
            rewards.append(" | +").append(mission.coinReward()).append(" coins");
        }
        if (mission.boosterReward()) {
            rewards.append(" | +Booster");
        }
        rewards.append("</gray>");

        faction.sendRichMessage(
                "<gold><bold>✦ FACTION MISSION COMPLETE!</bold></gold> <yellow>" + mission.displayName()
                        + "</yellow>" + rewards
        );

        if (mission.xpReward() > 0) {
            FactionLevelManager.awardXp(faction, mission.xpReward());
        }
        if (mission.moneyReward() > 0 && Econ.shouldBeUsed()) {
            Econ.modifyMoney(faction, mission.moneyReward(),
                    "Mission reward: " + mission.displayName(), "completing mission");
        }
        if (mission.coinReward() > 0) {
            faction.addFactionPoints(mission.coinReward());
        }
        if (mission.boosterReward()) {
            // Grant (store) a random booster — does NOT auto-activate.
            dev.kitteh.factions.level.BoosterManager.grantRandomBooster(faction);
        }

        faction.recordFactionLog(dev.kitteh.factions.audit.AuditEntry.generic(
                "(server)", null, "mission-complete",
                "Completed mission: " + mission.displayName()
        ));
    }

    /**
     * Reset progress + reroll active picks for a category if the reset interval has
     * elapsed since last reset. MAP missions never auto-reset (their picks stay locked
     * for the whole map cycle, only refreshed by {@link #forceResetAll(Faction)}).
     */
    public static void maybeReset(Faction faction, MissionCategory category) {
        long interval = category.resetIntervalMs();
        if (interval < 0) return; // MAP — only force-reset on map cycle

        long lastReset = faction.missionLastReset(category.id());
        long now = System.currentTimeMillis();

        if (now - lastReset >= interval) {
            for (MissionDefinition mission : missionsFor(category)) {
                faction.missionProgress(mission.id(), 0L);
            }
            faction.missionLastReset(category.id(), now);
            // Reroll the active pick when the window rolls
            faction.missionActiveIds(category.id(), pickRotation(faction, category, now));
        }
    }

    /**
     * If the faction has no active mission list for a category yet (first access ever
     * or right after data load), roll one now. Uses last-reset (or current) as the seed.
     */
    private static void rollIfMissing(Faction faction, MissionCategory category) {
        if (!faction.missionActiveIds(category.id()).isEmpty()) return;
        long seedTime = faction.missionLastReset(category.id());
        if (seedTime <= 0) {
            seedTime = System.currentTimeMillis();
            faction.missionLastReset(category.id(), seedTime);
        }
        faction.missionActiveIds(category.id(), pickRotation(faction, category, seedTime));
    }

    /**
     * Deterministically pick {@code pickCount} missions from the category pool seeded by
     * faction id + window epoch so the same picks stay stable for the whole window even
     * across server restarts.
     */
    private static List<String> pickRotation(Faction faction, MissionCategory category, long seedEpoch) {
        List<MissionDefinition> pool = missionsFor(category);
        if (pool.isEmpty()) return Collections.emptyList();

        int pickCount = Math.min(category.pickCount(), pool.size());
        long seed = ((long) faction.id() * 31L) ^ seedEpoch ^ category.ordinal();
        Random rng = new Random(seed);

        // Fisher-Yates partial shuffle for the first pickCount entries
        List<MissionDefinition> shuffled = new ArrayList<>(pool);
        for (int i = 0; i < pickCount; i++) {
            int j = i + rng.nextInt(shuffled.size() - i);
            MissionDefinition tmp = shuffled.get(i);
            shuffled.set(i, shuffled.get(j));
            shuffled.set(j, tmp);
        }
        List<String> ids = new ArrayList<>(pickCount);
        for (int i = 0; i < pickCount; i++) ids.add(shuffled.get(i).id());
        return ids;
    }

    /** Force-reset every category (e.g. on a new map). Re-rolls all active picks. */
    public static void forceResetAll(Faction faction) {
        long now = System.currentTimeMillis();
        for (MissionCategory cat : MissionCategory.values()) {
            for (MissionDefinition mission : missionsFor(cat)) {
                faction.missionProgress(mission.id(), 0L);
            }
            faction.missionLastReset(cat.id(), now);
            faction.missionActiveIds(cat.id(), pickRotation(faction, cat, now));
        }
    }

    /** Human-readable time-until-next-reset string. */
    public static String timeUntilReset(Faction faction, MissionCategory category) {
        long interval = category.resetIntervalMs();
        if (interval < 0) return "Never (Map)";
        long lastReset = faction.missionLastReset(category.id());
        long remaining = interval - (System.currentTimeMillis() - lastReset);
        if (remaining <= 0) return "Resetting soon";
        long hours = remaining / 3_600_000;
        long mins = (remaining % 3_600_000) / 60_000;
        if (hours > 0) return hours + "h " + mins + "m";
        return mins + "m";
    }
}
