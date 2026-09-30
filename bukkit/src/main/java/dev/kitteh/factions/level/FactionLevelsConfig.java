package dev.kitteh.factions.level;

import dev.kitteh.factions.config.annotation.Comment;

import java.util.ArrayList;
import java.util.List;

public class FactionLevelsConfig {

    @Comment("Per-level XP needed for each level-up. Index 0 = XP needed to go from level 0→1, " +
            "index 1 = XP for level 1→2, etc. After a level-up, the faction's XP resets to 0 (any " +
            "overflow carries over). Add or remove entries to change the max level.")
    private List<Long> xpRequired = new ArrayList<>(List.of(
        1000L,    // 0 → 1
        5000L,    // 1 → 2
        10000L,   // 2 → 3
        15000L,   // 3 → 4
        20000L,   // 4 → 5
        25000L,   // 5 → 6
        30000L,   // 6 → 7
        30000L,   // 7 → 8
        30000L,   // 8 → 9
        30000L    // 9 → 10
    ));

    @Comment("Money reward (via Vault) given to the faction bank on level-up. Index 0 = reward for reaching level 1.")
    private List<Double> moneyRewards = new ArrayList<>(List.of(
        500.0,
        1000.0,
        2000.0,
        3500.0,
        5000.0,
        7500.0,
        10000.0,
        15000.0,
        20000.0,
        30000.0
    ));

    @Comment("HH XP booster multiplier given as a reward on level-up. 0.0 = no booster reward.")
    private List<Double> hhXpBoosterRewards = new ArrayList<>(List.of(
        0.0,
        0.1,
        0.0,
        0.15,
        0.0,
        0.2,
        0.0,
        0.25,
        0.0,
        0.5
    ));

    @Comment("Faction coins awarded on level-up to spend on upgrades. Index 0 = coins for reaching level 1.")
    private List<Integer> factionCoinRewards = new ArrayList<>(List.of(
        1, 1, 1, 2, 2, 2, 3, 3, 3, 4
    ));

    @Comment("Total faction coins available at max level (sum of factionCoinRewards). Upgrades should cost more collectively than this to enforce the skill-tree.")
    private int totalCoinsAtMaxLevel = 22;

    @Comment("Potion effects unlocked at each level. Use Bukkit PotionEffectType names. Empty list = no unlock at that level.\n" +
             "Note: with the F Effects upgrade flow, effects are no longer per-level locked — every entry here is buyable from the shop.")
    private List<List<String>> effectUnlocks = new ArrayList<>(List.of(
        List.of(),
        List.of("SPEED"),
        List.of("HASTE"),
        List.of("REGENERATION"),
        List.of("STRENGTH"),
        List.of(),
        List.of("JUMP_BOOST"),
        List.of("FIRE_RESISTANCE"),
        List.of("NIGHT_VISION"),
        List.of("SATURATION")
    ));

    @Comment("Duration in seconds of faction potion effects when activated. (Legacy — used by older one-click flow only.)")
    private int effectDurationSeconds = 300;

    @Comment("Cooldown in seconds between faction effect uses. (Legacy — coins are the gate now.)")
    private int effectCooldownSeconds = 3600;

    @Comment("Coin cost per duration tier when buying an F Effect: index 0 = 10min, 1 = 30min, 2 = 1hr. Set the same effect to different costs for each duration.")
    private List<Integer> effectCosts = new ArrayList<>(List.of(1, 2, 4));

    @Comment("Duration in seconds for each effect-purchase tier. index 0 = short, 1 = medium, 2 = long. Defaults: 10min, 30min, 1hr.")
    private List<Integer> effectDurationsSeconds = new ArrayList<>(List.of(600, 1800, 3600));

    @Comment("Faction PV (private vault) upgrade — each tier unlocks 9 more slots. Tier count and coin costs are configurable.")
    private UpgradeTierConfig pvUpgrade = new UpgradeTierConfig(6,
        new ArrayList<>(List.of(2, 2, 3, 5, 6, 7)));

    @Comment("Faction Warps upgrade — each tier increases max warp count. Tier count and coin costs are configurable.")
    private UpgradeTierConfig warpsUpgrade = new UpgradeTierConfig(4,
        new ArrayList<>(List.of(2, 2, 3, 5)));

    @Comment("Warp Time Reduction upgrade — each tier reduces /f warp teleport delay by 2 seconds. Tier count and coin costs are configurable.")
    private UpgradeTierConfig warpTimeUpgrade = new UpgradeTierConfig(3,
        new ArrayList<>(List.of(1, 2, 2)));

    @Comment("HH XP Boost upgrade — increases headhunting XP multiplier. Tier count and coin costs are configurable.")
    private UpgradeTierConfig hhXpBoostUpgrade = new UpgradeTierConfig(4,
        new ArrayList<>(List.of(2, 2, 3, 5)));

    @Comment("Bigger TNT Bank upgrade — increases maximum TNT bank capacity. Tier count and coin costs are configurable.")
    private UpgradeTierConfig tntBankUpgrade = new UpgradeTierConfig(4,
        new ArrayList<>(List.of(2, 3, 3, 5)));

    @Comment("Faction Banner upgrade — unlocks /f banner command. Single tier.")
    private UpgradeTierConfig bannerUpgrade = new UpgradeTierConfig(1,
        new ArrayList<>(List.of(10)));

    @Comment("F Effects upgrade — unlocks /f effects. Single tier. Requires the faction to be at faction level effectsRequiredHhLevel (despite the legacy field name).")
    private UpgradeTierConfig effectsUpgrade = new UpgradeTierConfig(1,
        new ArrayList<>(List.of(10)));

    @Comment("Required FACTION LEVEL the faction must reach before F Effects can be unlocked. Set to 0 to disable the prerequisite. (Field name kept as-is for backwards compatibility with old configs.)")
    private int effectsRequiredHhLevel = 5;

    @Comment("Crop Growth upgrade — adds a chance to fast-tick crops in faction territory. Tier count and coin costs are configurable.")
    private UpgradeTierConfig cropGrowthUpgrade = new UpgradeTierConfig(5,
        new ArrayList<>(List.of(2, 2, 3, 5, 6)));

    @Comment("Bonus crop-growth chance per tier as a percent (0-100). Index 0 = tier 1.")
    private List<Integer> cropGrowthPerTierPercent = new ArrayList<>(List.of(10, 20, 30, 40, 50));

    @Comment("Bank Capacity upgrade — increases the maximum money a faction bank can hold. Tier count and coin costs are configurable.")
    private UpgradeTierConfig bankCapUpgrade = new UpgradeTierConfig(9,
        new ArrayList<>(List.of(2, 2, 2, 5, 6, 7, 8, 9, 10)));

    @Comment("Base bank capacity (tier 0, no upgrade). In whatever currency unit Vault uses.")
    private double bankCapBaseAmount = 1_000_000.0;

    @Comment("Bank capacity at each tier. Index 0 = tier 1, index 1 = tier 2, etc. Defaults span $1M (tier 0) → $10M (tier 9).")
    private List<Double> bankCapPerTierList = new ArrayList<>(List.of(
        2_000_000.0, 3_000_000.0, 4_000_000.0, 5_000_000.0,
        6_000_000.0, 7_000_000.0, 8_000_000.0, 9_000_000.0, 10_000_000.0
    ));

    @Comment("Base warp teleport delay in seconds before any warp-time-reduction upgrades.")
    private int baseWarpDelaySeconds = 11;

    @Comment("How many seconds each warp-time-reduction upgrade tier reduces the warp delay by.")
    private int warpDelayReductionPerTier = 2;

    @Comment("Base slots for the faction PV (0 means no PV until first tier is purchased).")
    private int pvBaseSlots = 0;

    @Comment("How many inventory slots each PV upgrade tier adds.")
    private int pvSlotsPerTier = 9;

    @Comment("Base max warp count before upgrades.")
    private int baseMaxWarps = 3;

    @Comment("How many additional warps each warp upgrade tier grants.")
    private int warpsPerTier = 2;

    @Comment("Faction banner auto-expire time in seconds.")
    private int bannerExpireSeconds = 30;

    @Comment("Cooldown in seconds between /f banner deployments per faction. 0 disables the cooldown.")
    private int bannerCooldownSeconds = 300;

    @Comment("Cooldown in seconds between successful /f home teleports. 0 disables the cooldown.")
    private int homeCooldownSeconds = 30;

    @Comment("Maximum number of players that can be on a faction's roster (pre-approved join list). " +
             "The actual member cap is set via main.conf -> factions.other.factionMemberLimit; the roster lets " +
             "Officer+ pre-approve up to this many people who can /f join without re-invite when slots free up.")
    private int rosterMaxSize = 30;

    @Comment("If false, /f open is disabled (open factions can't be toggled). Set to true to allow /f open again.")
    private boolean allowOpenFactionToggle = false;

    @Comment("How many hours to keep /f inspect block-history entries before automatic cleanup. 0 disables time-based eviction (size cap still applies).")
    private int blockLogRetentionHours = 48;

    @Comment("How many days to keep /f logs faction-activity entries before automatic cleanup. 0 disables time-based eviction (size cap still applies).")
    private int factionLogRetentionDays = 5;

    @Comment("If a faction's /f logs has had no new entries for this many days, wipe its entire faction log. 0 disables this dormant-faction sweep.")
    private int factionLogDormantWipeDays = 5;

    @Comment("Base TNT bank capacity (before any TNT Bank upgrades). Used as the floor when no per-tier capacity is configured.")
    private int tntBankBaseCapacity = 256;

    @Comment("DEPRECATED: linear bonus per tier. Used only when tntBankCapacityPerTierList is empty. Prefer the per-tier list below.")
    private int tntBankCapacityPerTier = 256;

    @Comment("Maximum TNT bank capacity at each tier. Index 0 = capacity at tier 1, index 1 = tier 2, etc. " +
            "If empty, the legacy linear formula (base + tier * tntBankCapacityPerTier) is used. " +
            "Capacity at tier 0 (no upgrade) is always tntBankBaseCapacity. Tier 4 (max) caps at 10,000.")
    private List<Integer> tntBankCapacityPerTierList = new ArrayList<>(List.of(2500, 5000, 7500, 10000));

    // ── Accessors ────────────────────────────────────────────────────────────

    public long xpRequired(int level) {
        int idx = level - 1;
        if (idx < 0 || idx >= xpRequired.size()) return Long.MAX_VALUE;
        // HOCON parses whole numbers as Integer; field is List<Long> but elements may be Integer.
        Object raw = xpRequired.get(idx);
        return raw instanceof Number n ? n.longValue() : Long.MAX_VALUE;
    }

    public double moneyReward(int level) {
        int idx = level - 1;
        if (idx < 0 || idx >= moneyRewards.size()) return 0.0;
        Object raw = moneyRewards.get(idx);
        return raw instanceof Number n ? n.doubleValue() : 0.0;
    }

    public double hhXpBoosterReward(int level) {
        int idx = level - 1;
        if (idx < 0 || idx >= hhXpBoosterRewards.size()) return 0.0;
        Object raw = hhXpBoosterRewards.get(idx);
        return raw instanceof Number n ? n.doubleValue() : 0.0;
    }

    public int factionCoinReward(int level) {
        int idx = level - 1;
        if (idx < 0 || idx >= factionCoinRewards.size()) return 0;
        return factionCoinRewards.get(idx);
    }

    public List<String> effectUnlocks(int level) {
        int idx = level - 1;
        if (idx < 0 || idx >= effectUnlocks.size()) return List.of();
        return effectUnlocks.get(idx);
    }

    public int maxLevel() {
        return xpRequired.size();
    }

    public int getEffectDurationSeconds() { return effectDurationSeconds; }
    public int getEffectCooldownSeconds() { return effectCooldownSeconds; }
    public List<Integer> getEffectCosts() { return effectCosts; }
    public List<Integer> getEffectDurationsSeconds() { return effectDurationsSeconds; }

    /** Coin cost for a given purchase tier (0 = short, 1 = medium, 2 = long). */
    public int effectCostForTier(int tier) {
        if (effectCosts == null || tier < 0 || tier >= effectCosts.size()) return 1;
        return effectCosts.get(tier);
    }

    /** Duration in seconds for a given purchase tier (0 = short, 1 = medium, 2 = long). */
    public int effectDurationForTier(int tier) {
        if (effectDurationsSeconds == null || tier < 0 || tier >= effectDurationsSeconds.size()) return 600;
        return effectDurationsSeconds.get(tier);
    }
    public int getTotalCoinsAtMaxLevel() { return totalCoinsAtMaxLevel; }
    public int getBaseWarpDelaySeconds() { return baseWarpDelaySeconds; }
    public int getWarpDelayReductionPerTier() { return warpDelayReductionPerTier; }
    public int getPvBaseSlots() { return pvBaseSlots; }
    public int getPvSlotsPerTier() { return pvSlotsPerTier; }
    public int getBaseMaxWarps() { return baseMaxWarps; }
    public int getWarpsPerTier() { return warpsPerTier; }
    public int getBannerExpireSeconds() { return bannerExpireSeconds; }
    public int getBannerCooldownSeconds() { return bannerCooldownSeconds; }
    public int getHomeCooldownSeconds() { return homeCooldownSeconds; }
    public int getRosterMaxSize() { return rosterMaxSize; }
    public boolean isAllowOpenFactionToggle() { return allowOpenFactionToggle; }
    public int getBlockLogRetentionHours() { return blockLogRetentionHours; }
    public int getFactionLogRetentionDays() { return factionLogRetentionDays; }
    public int getFactionLogDormantWipeDays() { return factionLogDormantWipeDays; }
    public int getTntBankBaseCapacity() { return tntBankBaseCapacity; }
    public int getTntBankCapacityPerTier() { return tntBankCapacityPerTier; }
    public List<Integer> getTntBankCapacityPerTierList() { return tntBankCapacityPerTierList; }

    /**
     * Resolve the TNT bank capacity for a given upgrade tier.
     * Tier 0 = base capacity. Tier N (1-indexed) reads index N-1 from the per-tier list, falling back to the linear formula
     * if the list is empty or doesn't define that tier.
     */
    public int tntBankCapacityForTier(int tier) {
        if (tier <= 0) return tntBankBaseCapacity;
        if (tntBankCapacityPerTierList != null && !tntBankCapacityPerTierList.isEmpty()) {
            int idx = Math.min(tier, tntBankCapacityPerTierList.size()) - 1;
            return tntBankCapacityPerTierList.get(idx);
        }
        return tntBankBaseCapacity + tier * tntBankCapacityPerTier;
    }

    public UpgradeTierConfig getPvUpgrade() { return pvUpgrade; }
    public UpgradeTierConfig getWarpsUpgrade() { return warpsUpgrade; }
    public UpgradeTierConfig getWarpTimeUpgrade() { return warpTimeUpgrade; }
    public UpgradeTierConfig getHhXpBoostUpgrade() { return hhXpBoostUpgrade; }
    public UpgradeTierConfig getTntBankUpgrade() { return tntBankUpgrade; }
    public UpgradeTierConfig getBannerUpgrade() { return bannerUpgrade; }
    public UpgradeTierConfig getEffectsUpgrade() { return effectsUpgrade; }
    public int getEffectsRequiredHhLevel() { return effectsRequiredHhLevel; }
    public UpgradeTierConfig getCropGrowthUpgrade() { return cropGrowthUpgrade; }
    public UpgradeTierConfig getBankCapUpgrade() { return bankCapUpgrade; }
    public double getBankCapBaseAmount() { return bankCapBaseAmount; }

    /** Bonus crop-growth chance percent for a given upgrade tier. Tier 0 = 0%. */
    public int cropGrowthPercentForTier(int tier) {
        if (tier <= 0) return 0;
        if (cropGrowthPerTierPercent != null && !cropGrowthPerTierPercent.isEmpty()) {
            int idx = Math.min(tier, cropGrowthPerTierPercent.size()) - 1;
            return cropGrowthPerTierPercent.get(idx);
        }
        return Math.min(100, tier * 10);
    }

    /** Bank capacity for a given upgrade tier. Tier 0 = base amount. */
    public double bankCapForTier(int tier) {
        if (tier <= 0) return bankCapBaseAmount;
        if (bankCapPerTierList != null && !bankCapPerTierList.isEmpty()) {
            int idx = Math.min(tier, bankCapPerTierList.size()) - 1;
            // HOCON parses whole numbers as Integer; the list is declared List<Double> but at
            // runtime the elements may be Integer. Read via Number to avoid a ClassCastException.
            Object raw = bankCapPerTierList.get(idx);
            if (raw instanceof Number n) return n.doubleValue();
            return bankCapBaseAmount;
        }
        return bankCapBaseAmount * (1 + tier);
    }

    public static class UpgradeTierConfig {
        @Comment("Number of tiers for this upgrade.")
        private int tiers;
        @Comment("Faction coin cost per tier. Length must match the tiers value.")
        private List<Integer> tierCosts;

        public UpgradeTierConfig() {
            this.tiers = 1;
            this.tierCosts = new ArrayList<>(List.of(2));
        }

        public UpgradeTierConfig(int tiers, List<Integer> tierCosts) {
            this.tiers = tiers;
            this.tierCosts = tierCosts;
        }

        public int getTiers() { return tiers; }
        public List<Integer> getTierCosts() { return tierCosts; }

        public int costForTier(int tier) {
            int idx = tier - 1;
            if (idx < 0 || idx >= tierCosts.size()) return 2;
            return tierCosts.get(idx);
        }
    }
}
