package dev.kitteh.factions.mission;

import dev.kitteh.factions.config.annotation.Comment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mission definitions, keyed by mission ID.
 *
 * <p>Each mission lives in its own self-contained block in {@code missions.conf} so the
 * config is human-editable per-mission (add/remove/tune one mission without counting indexes
 * across parallel lists).</p>
 *
 * <p><b>Why {@code Map<String, Object>} instead of {@code Map<String, MissionEntry>}?</b><br>
 * The reflective Configurate Loader treats {@code Map} as a primitive type, so it round-trips
 * the raw nested-map shape that HOCON represents. If we typed the values as {@code MissionEntry},
 * Configurate would either fail to deserialise or silently drop fields. We accept the raw map
 * here and convert to typed {@link MissionDefinition} in {@link #buildDefinitions()}.</p>
 *
 * <p>Field reference (per-mission keys):</p>
 * <ul>
 *   <li>{@code displayName} — name shown in the GUI</li>
 *   <li>{@code description} — short description</li>
 *   <li>{@code category} — DAILY / BI_DAILY / WEEKLY / MAP</li>
 *   <li>{@code type} — PLAYER_KILLS / BLOCK_MINED / FACTION_PLAYTIME_MINUTES / HEADS_SOLD /
 *       CROPS_SOLD / OUTPOST_CAPTURES / OUTPOST_HOLD_MINUTES / MOB_KILLED / FISH_CAUGHT /
 *       HH_SPAWNERS_BOUGHT</li>
 *   <li>{@code targetKey} — Material name / EntityType name / spawner tier (BASIC / ADVANCED
 *       / EXTREME / SHOP) / empty for "totals"</li>
 *   <li>{@code requiredAmount} — long, target value to complete</li>
 *   <li>{@code xpReward} — faction XP awarded on completion</li>
 *   <li>{@code moneyReward} — Vault money awarded on completion (0 = none)</li>
 *   <li>{@code coinReward} — faction coins awarded on completion</li>
 *   <li>{@code difficulty} — 0 (Easy) / 1 (Medium) / 2 (Hard); cosmetic</li>
 * </ul>
 */
@SuppressWarnings("FieldMayBeFinal")
public class MissionsConfig {

    @Comment("Faction missions, keyed by mission ID. Each mission is a self-contained block. " +
             "Edit, add, or remove freely. The plugin will silently skip any malformed entry.\n\n" +
             "Categories: DAILY / BI_DAILY / WEEKLY / MAP\n" +
             "Types: PLAYER_KILLS, BLOCK_MINED, FACTION_PLAYTIME_MINUTES, HEADS_SOLD, " +
             "CROPS_SOLD, OUTPOST_CAPTURES, OUTPOST_HOLD_MINUTES, MOB_KILLED, FISH_CAUGHT, " +
             "HH_SPAWNERS_BOUGHT.\n" +
             "targetKey examples: 'STONE' (block), 'WHEAT' (crop/material), 'ZOMBIE' " +
             "(mob/head), 'COD' (fish), 'BASIC'/'ADVANCED'/'EXTREME'/'SHOP' (HH spawners). " +
             "Leave empty for total/any.\n" +
             "Coin reward convention: Daily=3, Bi-Daily=5, Weekly=7, Map=10.")
    private Map<String, Object> missions = defaultMissions();

    /**
     * Build the runtime mission list from the raw nested-map config. Each top-level key is the
     * mission ID; the value should be a Map of field→value. Malformed entries are silently
     * skipped so a single typo can't break the whole config.
     */
    public List<MissionDefinition> buildDefinitions() {
        List<MissionDefinition> defs = new ArrayList<>();
        if (missions == null) return defs;

        for (Map.Entry<String, Object> entry : missions.entrySet()) {
            try {
                String id = entry.getKey();
                if (id == null || id.isEmpty()) continue;
                if (!(entry.getValue() instanceof Map<?, ?> raw)) continue;

                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) raw;

                String displayName  = str(m, "displayName", id);
                String description  = str(m, "description", "");
                String catRaw       = str(m, "category", "DAILY");
                String typeRaw      = str(m, "type", null);
                String targetKey    = str(m, "targetKey", "");
                long requiredAmount = lng(m, "requiredAmount", 1L);
                long xpReward       = lng(m, "xpReward", 0L);
                double moneyReward  = dbl(m, "moneyReward", 0.0);
                int coinReward      = intg(m, "coinReward", 0);
                int difficulty      = intg(m, "difficulty", 0);
                boolean booster     = bool(m, "boosterReward", false);
                if (typeRaw == null) continue;

                MissionCategory category = MissionCategory.valueOf(catRaw);
                MissionType type = MissionType.valueOf(typeRaw);
                defs.add(new MissionDefinition(
                        id, displayName, description, category, type, targetKey,
                        requiredAmount, xpReward, moneyReward, coinReward, difficulty, booster
                ));
            } catch (Exception ignored) {
                // Bad mission row — skip it, don't break the whole config.
            }
        }
        return defs;
    }

    // ── Field readers (defensive, accept missing/wrong types) ─────────────────────

    private static String str(Map<String, Object> m, String k, String fallback) {
        Object v = m.get(k);
        return v instanceof String s ? s : fallback;
    }

    private static long lng(Map<String, Object> m, String k, long fallback) {
        Object v = m.get(k);
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) try { return Long.parseLong(s); } catch (Exception ignored) {}
        return fallback;
    }

    private static double dbl(Map<String, Object> m, String k, double fallback) {
        Object v = m.get(k);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) try { return Double.parseDouble(s); } catch (Exception ignored) {}
        return fallback;
    }

    private static int intg(Map<String, Object> m, String k, int fallback) {
        Object v = m.get(k);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) try { return Integer.parseInt(s); } catch (Exception ignored) {}
        return fallback;
    }

    private static boolean bool(Map<String, Object> m, String k, boolean fallback) {
        Object v = m.get(k);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return fallback;
    }

    // ── Defaults ──────────────────────────────────────────────────────────────────

    private static Map<String, Object> defaultMissions() {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();

        // ── DAILY pool (36 missions, +3 coins each) ──
        addD(out, "d_kills_10",        "Bounty Hunters",     "Get 10 player kills as a faction", "PLAYER_KILLS", "", 10,    150, 0,     0);
        addD(out, "d_kills_25",        "Slaughter House",    "Get 25 player kills as a faction", "PLAYER_KILLS", "", 25,    200, 7500,  1);
        addD(out, "d_mine_stone",      "Stone Breakers",     "Mine 256 stone",                   "BLOCK_MINED",  "STONE",      256, 100, 0,    0);
        addD(out, "d_mine_coal",       "Coal Diggers",       "Mine 64 coal ore",                 "BLOCK_MINED",  "COAL_ORE",   64,  120, 0,    0);
        addD(out, "d_mine_iron",       "Iron Will",          "Mine 32 iron ore",                 "BLOCK_MINED",  "IRON_ORE",   32,  140, 5000, 0);
        addD(out, "d_mine_gold",       "Gold Rush",          "Mine 16 gold ore",                 "BLOCK_MINED",  "GOLD_ORE",   16,  150, 5000, 1);
        addD(out, "d_mine_diamond",    "Diamond Touch",      "Mine 16 diamond ore",              "BLOCK_MINED",  "DIAMOND_ORE",16,  180, 7500, 1);
        addD(out, "d_mine_redstone",   "Redstone Tinkerer",  "Mine 32 redstone ore",             "BLOCK_MINED",  "REDSTONE_ORE",32, 130, 5000, 0);
        addD(out, "d_mine_lapis",      "Lapis Lazuli",       "Mine 32 lapis ore",                "BLOCK_MINED",  "LAPIS_ORE",  32,  130, 5000, 0);
        addD(out, "d_mine_emerald",    "Emerald Eye",        "Mine 8 emerald ore",               "BLOCK_MINED",  "EMERALD_ORE",8,   160, 7500, 1);
        addD(out, "d_playtime_60",     "Faction Activity",   "Accumulate 60 minutes of faction playtime", "FACTION_PLAYTIME_MINUTES", "", 60, 120, 0,    0);
        addD(out, "d_playtime_120",    "Active Garrison",    "Accumulate 120 minutes of faction playtime","FACTION_PLAYTIME_MINUTES", "", 120,150, 5000, 1);
        addD(out, "d_heads_5",         "Trophy Cabinet",     "Sell 5 heads to HH",               "HEADS_SOLD",   "",         5, 160, 7500, 1);
        addD(out, "d_heads_zombie",    "Zombie Apocalypse",  "Sell 10 zombie heads",             "HEADS_SOLD",   "ZOMBIE",   10,140, 5000, 0);
        addD(out, "d_heads_skeleton",  "Bone Collector",     "Sell 10 skeleton heads",           "HEADS_SOLD",   "SKELETON", 10,140, 5000, 0);
        addD(out, "d_heads_creeper",   "Pop Goes The Weasel","Sell 10 creeper heads",            "HEADS_SOLD",   "CREEPER",  10,140, 5000, 0);
        addD(out, "d_mob_zombie",      "Z-Day",              "Kill 30 zombies",                  "MOB_KILLED",   "ZOMBIE",   30,120, 5000, 0);
        addD(out, "d_mob_skeleton",    "Bonebreaker",        "Kill 30 skeletons",                "MOB_KILLED",   "SKELETON", 30,120, 5000, 0);
        addD(out, "d_mob_creeper",     "Boom Squad",         "Kill 20 creepers",                 "MOB_KILLED",   "CREEPER",  20,120, 5000, 0);
        addD(out, "d_mob_spider",      "Spider Squisher",    "Kill 30 spiders",                  "MOB_KILLED",   "SPIDER",   30,120, 5000, 0);
        addD(out, "d_mob_enderman",    "Voidwalker",         "Kill 10 endermen",                 "MOB_KILLED",   "ENDERMAN", 10,140, 7500, 1);
        addD(out, "d_mob_blaze",       "Nether Demon",       "Kill 10 blazes",                   "MOB_KILLED",   "BLAZE",    10,150, 7500, 1);
        addD(out, "d_mob_pillager",    "Pillage Patrol",     "Kill 20 pillagers",                "MOB_KILLED",   "PILLAGER", 20,140, 5000, 0);
        addD(out, "d_mob_drowned",     "Drowned Cleanup",    "Kill 20 drowned",                  "MOB_KILLED",   "DROWNED",  20,130, 5000, 0);
        addD(out, "d_mob_husk",        "Desert Sweep",       "Kill 20 husks",                    "MOB_KILLED",   "HUSK",     20,130, 5000, 0);
        addD(out, "d_mob_witch",       "Witch Hunt",         "Kill 10 witches",                  "MOB_KILLED",   "WITCH",    10,150, 7500, 1);
        addD(out, "d_fish_any",        "Gone Fishing",       "Catch 20 fish (any)",              "FISH_CAUGHT",  "",         20,130, 5000, 0);
        addD(out, "d_fish_cod",        "Cod Catcher",        "Catch 15 cod",                     "FISH_CAUGHT",  "COD",      15,130, 5000, 0);
        addD(out, "d_fish_salmon",     "Salmon Run",         "Catch 15 salmon",                  "FISH_CAUGHT",  "SALMON",   15,130, 5000, 0);
        addD(out, "d_spawner_any",     "Spawner Hoarder",    "Buy 1 spawner (any)",              "HH_SPAWNERS_BOUGHT", "",   1, 180, 7500, 1);
        addD(out, "d_spawner_basic",   "Basic Instinct",     "Redeem 1 BASIC mystery",           "HH_SPAWNERS_BOUGHT", "BASIC",1,180, 7500, 1);
        addD(out, "d_break_logs",      "Lumberjack",         "Mine 256 logs",                    "BLOCK_MINED",  "OAK_LOG",  256,120, 5000, 0);
        addD(out, "d_break_dirt",      "Earthmover",         "Mine 256 dirt",                    "BLOCK_MINED",  "DIRT",     256,100, 0,    0);
        addD(out, "d_break_netherrack","Nether Quarry",      "Mine 256 netherrack",              "BLOCK_MINED",  "NETHERRACK",256,110, 5000, 0);
        addD(out, "d_break_endstone",  "End Walker",         "Mine 64 endstone",                 "BLOCK_MINED",  "END_STONE",64, 140, 7500, 1);
        addD(out, "d_break_obsidian",  "Obsidian Crusher",   "Mine 16 obsidian",                 "BLOCK_MINED",  "OBSIDIAN", 16, 180, 10000,1);

        // ── BI_DAILY pool (27 missions, +5 coins each) ──
        addBD(out, "bd_kills_50",          "Blood Bath",         "Get 50 player kills as a faction",   "PLAYER_KILLS", "",            50,  350, 10000, 1);
        addBD(out, "bd_kills_100",         "Massacre",           "Get 100 player kills as a faction",  "PLAYER_KILLS", "",            100, 450, 15000, 2);
        addBD(out, "bd_mine_stone_1k",     "Stone Avalanche",    "Mine 1000 stone",                    "BLOCK_MINED",  "STONE",       1000,300, 7500,  1);
        addBD(out, "bd_mine_diamond_64",   "Diamond Heist",      "Mine 64 diamond ore",                "BLOCK_MINED",  "DIAMOND_ORE", 64,  380, 10000, 1);
        addBD(out, "bd_mine_emerald_32",   "Emerald Empire",     "Mine 32 emerald ore",                "BLOCK_MINED",  "EMERALD_ORE", 32,  400, 12500, 1);
        addBD(out, "bd_mine_ancient",      "Ancient Diggers",    "Mine 16 ancient debris",             "BLOCK_MINED",  "ANCIENT_DEBRIS",16, 420, 15000, 2);
        addBD(out, "bd_mine_netherite",    "Netherite Forge",    "Mine 8 netherite ingots-worth",      "BLOCK_MINED",  "NETHERITE_INGOT",8, 440, 15000, 2);
        addBD(out, "bd_heads_50",          "Head Collector",     "Sell 50 heads to HH",                "HEADS_SOLD",   "",            50,  350, 10000, 1);
        addBD(out, "bd_heads_zombie_50",   "Z-Hunter",           "Sell 50 zombie heads",               "HEADS_SOLD",   "ZOMBIE",      50,  380, 10000, 1);
        addBD(out, "bd_heads_skeleton_50", "Skull Stack",        "Sell 50 skeleton heads",             "HEADS_SOLD",   "SKELETON",    50,  380, 10000, 1);
        addBD(out, "bd_heads_warden",      "Warden Bounty",      "Sell 1 warden head",                 "HEADS_SOLD",   "WARDEN",      1,   500, 200,   2);
        addBD(out, "bd_mob_zombie_200",    "Z-Wave",             "Kill 200 zombies",                   "MOB_KILLED",   "ZOMBIE",      200, 320, 7500,  1);
        addBD(out, "bd_mob_blaze_100",     "Blaze Brigade",      "Kill 100 blazes",                    "MOB_KILLED",   "BLAZE",       100, 360, 10000, 1);
        addBD(out, "bd_mob_enderman_50",   "Void Reapers",       "Kill 50 endermen",                   "MOB_KILLED",   "ENDERMAN",    50,  380, 10000, 1);
        addBD(out, "bd_mob_warden",        "Warden Slayers",     "Kill 1 warden",                      "MOB_KILLED",   "WARDEN",      1,   500, 25000, 2);
        addBD(out, "bd_mob_wither_skel",   "Nether Cleanup",     "Kill 30 wither skeletons",           "MOB_KILLED",   "WITHER_SKELETON", 30, 400, 12500, 1);
        addBD(out, "bd_mob_iron_golem",    "Iron Crushers",      "Kill 5 iron golems",                 "MOB_KILLED",   "IRON_GOLEM",  5,   420, 15000, 2);
        addBD(out, "bd_fish_200",          "Fishery",            "Catch 200 fish",                     "FISH_CAUGHT",  "",            200, 330, 7500,  1);
        addBD(out, "bd_fish_pufferfish",   "Pufferfish Pro",     "Catch 30 pufferfish",                "FISH_CAUGHT",  "PUFFERFISH",  30,  380, 10000, 1);
        addBD(out, "bd_fish_tropical",     "Tropical Tropics",   "Catch 50 tropical fish",             "FISH_CAUGHT",  "TROPICAL_FISH",50, 390, 10000, 1);
        addBD(out, "bd_spawner_basic",     "Mystery Basic",      "Redeem 3 BASIC mysteries",           "HH_SPAWNERS_BOUGHT", "BASIC",   3,   400, 12500, 1);
        addBD(out, "bd_spawner_advanced",  "Mystery Advanced",   "Redeem 1 ADVANCED mystery",          "HH_SPAWNERS_BOUGHT", "ADVANCED",1,   460, 200,   2);
        addBD(out, "bd_outpost_capture",   "Outpost Push",       "Capture 1 outpost",                  "OUTPOST_CAPTURES", "",         1,   440, 15000, 1);
        addBD(out, "bd_outpost_hold_60",   "Garrison Hour",      "Hold an outpost for 60 minutes",     "OUTPOST_HOLD_MINUTES", "",     60,  400, 12500, 1);
        addBD(out, "bd_break_logs_500",    "Logging Crew",       "Mine 500 logs",                      "BLOCK_MINED",  "OAK_LOG",     500, 330, 7500,  1);
        addBD(out, "bd_playtime_240",      "Settled In",         "Accumulate 240 minutes of playtime", "FACTION_PLAYTIME_MINUTES", "", 240, 360, 7500,  1);
        addBD(out, "bd_break_obsidian_64", "Obsidian Empire",    "Mine 64 obsidian",                   "BLOCK_MINED",  "OBSIDIAN",    64,  420, 15000, 1);

        // ── WEEKLY pool (26 missions, +7 coins each) ──
        addW(out, "w_kills_300",           "Warlords",           "Get 300 player kills as a faction",  "PLAYER_KILLS", "",            300, 1000, 35000, 1);
        addW(out, "w_kills_500",           "Generals of War",    "Get 500 player kills as a faction",  "PLAYER_KILLS", "",            500, 1300, 40000, 2);
        addW(out, "w_mine_diamond_256",    "Diamond Cartel",     "Mine 256 diamond ore",               "BLOCK_MINED",  "DIAMOND_ORE", 256, 1100, 40000, 1);
        addW(out, "w_mine_emerald_128",    "Emerald Cartel",     "Mine 128 emerald ore",               "BLOCK_MINED",  "EMERALD_ORE", 128, 1200, 40000, 2);
        addW(out, "w_mine_netherite_8",    "Netherite Hoard",    "Mine 8 netherite ingots-worth",      "BLOCK_MINED",  "NETHERITE_INGOT", 8, 1500, 50000, 2);
        addW(out, "w_mine_ancient_32",     "Ancient Civilization","Mine 32 ancient debris",            "BLOCK_MINED",  "ANCIENT_DEBRIS", 32, 1300, 45000, 2);
        addW(out, "w_heads_300",           "Head Hunter Elite",  "Sell 300 heads to HH",               "HEADS_SOLD",   "",            300, 1100, 40000, 2);
        addW(out, "w_heads_warden_3",      "Warden Trio",        "Sell 3 warden heads",                "HEADS_SOLD",   "WARDEN",      3,   1500, 50000, 2);
        addW(out, "w_heads_creeper_100",   "Boom Empire",        "Sell 100 creeper heads",             "HEADS_SOLD",   "CREEPER",     100, 1200, 40000, 1);
        addW(out, "w_heads_blaze_75",      "Blaze Reaper",       "Sell 75 blaze heads",                "HEADS_SOLD",   "BLAZE",       75,  1300, 40000, 1);
        addW(out, "w_mob_warden_5",        "Warden Pack",        "Kill 5 wardens",                     "MOB_KILLED",   "WARDEN",      5,   1500, 50000, 2);
        addW(out, "w_mob_wskel_100",       "Wither Cleansing",   "Kill 100 wither skeletons",          "MOB_KILLED",   "WITHER_SKELETON",100,1300, 40000, 1);
        addW(out, "w_mob_ender_dragon",    "Dragon Slayer",      "Kill 1 ender dragon",                "MOB_KILLED",   "ENDER_DRAGON",1,   1600, 600,   2);
        addW(out, "w_mob_wither",          "Wither Boss",        "Kill 1 wither",                      "MOB_KILLED",   "WITHER",      1,   1500, 550,   2);
        addW(out, "w_mob_elder_guardian",  "Guardian Empire",    "Kill 1 elder guardian",              "MOB_KILLED",   "ELDER_GUARDIAN",1, 1400, 50000, 2);
        addW(out, "w_outpost_capture_3",   "Territory Control",  "Capture 3 outposts",                 "OUTPOST_CAPTURES", "",         3,   1100, 40000, 1);
        addW(out, "w_outpost_hold_360",    "Stronghold",         "Hold an outpost for 360 minutes",    "OUTPOST_HOLD_MINUTES", "",     360, 1100, 40000, 1);
        addW(out, "w_spawner_advanced",    "Mystery Master",     "Redeem 5 ADVANCED mysteries",        "HH_SPAWNERS_BOUGHT", "ADVANCED",5,  1300, 40000, 2);
        addW(out, "w_spawner_extreme",     "Mystery God",        "Redeem 2 EXTREME mysteries",         "HH_SPAWNERS_BOUGHT", "EXTREME", 2,  1500, 50000, 2);
        addW(out, "w_fish_750",            "Master Angler",      "Catch 750 fish",                     "FISH_CAUGHT",  "",            750, 1100, 35000, 1);
        addW(out, "w_fish_pufferfish_50",  "Pufferfish Master",  "Catch 50 pufferfish",                "FISH_CAUGHT",  "PUFFERFISH",  50,  1300, 40000, 2);
        addW(out, "w_fish_tropical_100",   "Tropical Master",    "Catch 100 tropical fish",            "FISH_CAUGHT",  "TROPICAL_FISH",100,1300, 40000, 2);
        addW(out, "w_break_logs_5000",     "Forest Razers",      "Mine 5000 logs",                     "BLOCK_MINED",  "OAK_LOG",     5000,1100, 30000, 1);
        addW(out, "w_break_obsidian_500",  "Obsidian Mountain",  "Mine 500 obsidian",                  "BLOCK_MINED",  "OBSIDIAN",    500, 1300, 40000, 1);
        addW(out, "w_playtime_1200",       "Faction Marathon",   "Accumulate 1200 minutes of playtime","FACTION_PLAYTIME_MINUTES", "",1200,1100, 35000, 1);
        addW(out, "w_kills_phantom",       "Sky Sweepers",       "Kill 30 phantoms",                   "MOB_KILLED",   "PHANTOM",     30,  1300, 40000, 1);

        // ── MAP pool (44 missions, +10 coins each) ──
        addM(out, "m_kills_2000",          "Conquerors",         "Get 2000 player kills as a faction", "PLAYER_KILLS", "",            2000, 8000, 500000, 2);
        addM(out, "m_kills_5000",          "World Eaters",       "Get 5000 player kills as a faction", "PLAYER_KILLS", "",            5000, 9500, 6000,   2);
        addM(out, "m_mine_stone_50k",      "Stone Empire",       "Mine 50000 stone",                   "BLOCK_MINED",  "STONE",       50000,7000, 3500,   1);
        addM(out, "m_mine_diamond_2000",   "Diamond Empire",     "Mine 2000 diamond ore",              "BLOCK_MINED",  "DIAMOND_ORE", 2000, 8500, 200000, 2);
        addM(out, "m_mine_emerald_1000",   "Emerald Dynasty",    "Mine 1000 emerald ore",              "BLOCK_MINED",  "EMERALD_ORE", 1000, 8000, 150000, 2);
        addM(out, "m_mine_netherite_64",   "Netherite Kingdom",  "Mine 64 netherite ingots-worth",     "BLOCK_MINED",  "NETHERITE_INGOT",64,9000, 200000, 2);
        addM(out, "m_mine_ancient_500",    "Ancient Empire",     "Mine 500 ancient debris",            "BLOCK_MINED",  "ANCIENT_DEBRIS",500,9000, 200000, 2);
        addM(out, "m_heads_2000",          "Head Empire",        "Sell 2000 heads to HH",              "HEADS_SOLD",   "",            2000, 7500, 150000, 1);
        addM(out, "m_heads_warden_25",     "Warden Empire",      "Sell 25 warden heads",               "HEADS_SOLD",   "WARDEN",      25,   9500, 6000,   2);
        addM(out, "m_heads_creeper_500",   "Boom Dynasty",       "Sell 500 creeper heads",             "HEADS_SOLD",   "CREEPER",     500,  7500, 150000, 1);
        addM(out, "m_heads_zombie_1000",   "Z-Empire",           "Sell 1000 zombie heads",             "HEADS_SOLD",   "ZOMBIE",      1000, 7000, 3500,   1);
        addM(out, "m_heads_skel_1000",     "Skeleton Empire",    "Sell 1000 skeleton heads",           "HEADS_SOLD",   "SKELETON",    1000, 7000, 3500,   1);
        addM(out, "m_heads_blaze_500",     "Blaze Empire",       "Sell 500 blaze heads",               "HEADS_SOLD",   "BLAZE",       500,  7500, 150000, 1);
        addM(out, "m_heads_pillager_500",  "Pillager Empire",    "Sell 500 pillager heads",            "HEADS_SOLD",   "PILLAGER",    500,  7500, 150000, 1);
        addM(out, "m_heads_witch_300",     "Witch Empire",       "Sell 300 witch heads",               "HEADS_SOLD",   "WITCH",       300,  7500, 150000, 1);
        addM(out, "m_mob_warden_50",       "Warden Slaughter",   "Kill 50 wardens",                    "MOB_KILLED",   "WARDEN",      50,   9000, 200000, 2);
        addM(out, "m_mob_wither_5",        "Wither Slayer Set",  "Kill 5 withers",                     "MOB_KILLED",   "WITHER",      5,    9500, 6000,   2);
        addM(out, "m_mob_dragon_3",        "Dragon Trio",        "Kill 3 ender dragons",               "MOB_KILLED",   "ENDER_DRAGON",3,    9500, 6000,   2);
        addM(out, "m_mob_eg_5",            "Guardian Trio",      "Kill 5 elder guardians",             "MOB_KILLED",   "ELDER_GUARDIAN",5,  9500, 6000,   2);
        addM(out, "m_mob_iron_golem_200",  "Iron Empire",        "Kill 200 iron golems",               "MOB_KILLED",   "IRON_GOLEM",  200,  8000, 150000, 1);
        addM(out, "m_mob_blaze_1000",      "Blaze Empire 1k",    "Kill 1000 blazes",                   "MOB_KILLED",   "BLAZE",       1000, 7500, 100000, 1);
        addM(out, "m_mob_enderman_1000",   "End Empire",         "Kill 1000 endermen",                 "MOB_KILLED",   "ENDERMAN",    1000, 7500, 100000, 1);
        addM(out, "m_mob_drowned_500",     "Drowned Empire",     "Kill 500 drowned",                   "MOB_KILLED",   "DROWNED",     500,  7500, 100000, 1);
        addM(out, "m_outpost_capture_20",  "Outpost Domination", "Capture 20 outposts total",          "OUTPOST_CAPTURES", "",         20,   7000, 150000, 1);
        addM(out, "m_outpost_hold_2000",   "Eternal Garrison",   "Hold an outpost for 2000 minutes",   "OUTPOST_HOLD_MINUTES", "",     2000, 8500, 200000, 1);
        addM(out, "m_outpost_hold_5000",   "Indomitable",        "Hold an outpost for 5000 minutes",   "OUTPOST_HOLD_MINUTES", "",     5000, 9000, 6000,   2);
        addM(out, "m_spawner_shop_50",     "Spawner Mogul",      "Buy 50 spawners (shop)",             "HH_SPAWNERS_BOUGHT", "SHOP",   50,   7500, 150000, 1);
        addM(out, "m_spawner_basic_25",    "Basic Empire",       "Redeem 25 BASIC mysteries",          "HH_SPAWNERS_BOUGHT", "BASIC",  25,   7500, 150000, 1);
        addM(out, "m_spawner_adv_15",      "Advanced Empire",    "Redeem 15 ADVANCED mysteries",       "HH_SPAWNERS_BOUGHT", "ADVANCED",15,  8000, 500000, 1);
        addM(out, "m_spawner_ext_10",      "Extreme Empire",     "Redeem 10 EXTREME mysteries",        "HH_SPAWNERS_BOUGHT", "EXTREME",10,   8500, 200000, 2);
        addM(out, "m_fish_5000",           "Master Fisherman",   "Catch 5000 fish",                    "FISH_CAUGHT",  "",            5000, 7500, 150000, 2);
        addM(out, "m_fish_pufferfish_500", "Pufferfish Empire",  "Catch 500 pufferfish",               "FISH_CAUGHT",  "PUFFERFISH",  500,  8000, 150000, 1);
        addM(out, "m_fish_tropical_500",   "Tropical Empire",    "Catch 500 tropical fish",            "FISH_CAUGHT",  "TROPICAL_FISH",500,8000, 150000, 1);
        addM(out, "m_fish_salmon_1000",    "Salmon Master",      "Catch 1000 salmon",                  "FISH_CAUGHT",  "SALMON",      1000, 7500, 100000, 1);
        addM(out, "m_fish_cod_1000",       "Cod Master",         "Catch 1000 cod",                     "FISH_CAUGHT",  "COD",         1000, 7500, 100000, 1);
        addM(out, "m_break_logs_25000",    "Forest Apocalypse",  "Mine 25000 logs",                    "BLOCK_MINED",  "OAK_LOG",     25000,8000, 150000, 2);
        addM(out, "m_break_obsidian_2000", "Obsidian Mountain X","Mine 2000 obsidian",                 "BLOCK_MINED",  "OBSIDIAN",    2000, 8500, 500000, 2);
        addM(out, "m_break_endstone_3000", "End Quarry",         "Mine 3000 endstone",                 "BLOCK_MINED",  "END_STONE",   3000, 8000, 150000, 1);
        addM(out, "m_playtime_5000",       "Marathon",           "Accumulate 5000 minutes of playtime","FACTION_PLAYTIME_MINUTES","",5000, 7000, 100000, 1);
        addM(out, "m_playtime_10000",      "Iron Marathon",      "Accumulate 10000 minutes of playtime","FACTION_PLAYTIME_MINUTES","",10000,8500, 500000, 2);
        addM(out, "m_kills_phantom_200",   "Phantom Hunters",    "Kill 200 phantoms",                  "MOB_KILLED",   "PHANTOM",     200,  7500, 150000, 1);
        addM(out, "m_break_netherrack_10k","Nether Quarry X",    "Mine 10000 netherrack",              "BLOCK_MINED",  "NETHERRACK",  10000,7500, 100000, 1);
        addM(out, "m_mine_lapis_2000",     "Lapis Empire",       "Mine 2000 lapis ore",                "BLOCK_MINED",  "LAPIS_ORE",   2000, 7500, 100000, 1);
        addM(out, "m_mine_redstone_3000",  "Redstone Empire",    "Mine 3000 redstone ore",             "BLOCK_MINED",  "REDSTONE_ORE",3000, 7500, 100000, 1);

        return out;
    }

    // ── Helpers (per-category coin convention) ────────────────────────────────────

    /** DAILY adder — coins fixed at 3. */
    private static void addD(LinkedHashMap<String, Object> out, String id, String name, String desc,
                             String type, String key, long required, long xp, double money, int diff) {
        out.put(id, missionEntry(name, desc, "DAILY", type, key, required, xp, money, 3, diff));
    }

    /** BI_DAILY adder — coins fixed at 5. */
    private static void addBD(LinkedHashMap<String, Object> out, String id, String name, String desc,
                              String type, String key, long required, long xp, double money, int diff) {
        out.put(id, missionEntry(name, desc, "BI_DAILY", type, key, required, xp, money, 5, diff));
    }

    /** WEEKLY adder — coins fixed at 7. */
    private static void addW(LinkedHashMap<String, Object> out, String id, String name, String desc,
                             String type, String key, long required, long xp, double money, int diff) {
        out.put(id, missionEntry(name, desc, "WEEKLY", type, key, required, xp, money, 7, diff));
    }

    /** MAP adder — coins fixed at 10. */
    private static void addM(LinkedHashMap<String, Object> out, String id, String name, String desc,
                             String type, String key, long required, long xp, double money, int diff) {
        out.put(id, missionEntry(name, desc, "MAP", type, key, required, xp, money, 10, diff));
    }

    private static Map<String, Object> missionEntry(String name, String desc, String category,
                                                    String type, String targetKey, long required,
                                                    long xp, double money, int coins, int diff) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("displayName",    name);
        m.put("description",    desc);
        m.put("category",       category);
        m.put("type",           type);
        m.put("targetKey",      targetKey);
        m.put("requiredAmount", required);
        m.put("xpReward",       xp);
        m.put("moneyReward",    money);
        m.put("coinReward",     coins);
        m.put("difficulty",     diff);
        // Hard missions (difficulty 2) additionally award a random faction booster on completion.
        // Set per-mission in missions.conf to override.
        m.put("boosterReward",  diff >= 2);
        return m;
    }
}
