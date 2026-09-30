package dev.kitteh.factions.mission;

/**
 * What a mission tracks. Listener feeds increments based on these types.
 *
 * <p>For types that take a target key, the key is matched case-insensitively
 * against the mission's {@code targetKey}. Empty key on the mission means
 * "match any" (totals).</p>
 */
public enum MissionType {
    PLAYER_KILLS,         // no key
    BLOCK_MINED,          // key = Material name
    FACTION_PLAYTIME_MINUTES, // no key
    HEADS_SOLD,           // key = EntityType (e.g. "ZOMBIE"); empty = any head
    CROPS_SOLD,           // key = Material name (e.g. "WHEAT"); empty = any crop
    OUTPOST_CAPTURES,     // no key
    OUTPOST_HOLD_MINUTES, // no key
    MOB_KILLED,           // key = EntityType name; empty = any mob
    FISH_CAUGHT,          // key = Material name (COD/SALMON/PUFFERFISH/TROPICAL_FISH); empty = any
    HH_SPAWNERS_BOUGHT    // key = "BASIC" / "ADVANCED" / "EXTREME" (mystery) or "SHOP" / ""; empty = any
}
