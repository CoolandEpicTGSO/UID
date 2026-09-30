package dev.kitteh.factions.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Reflective lookup for a player's RegentHeadHunting level.
 * Returns -1 if RH isn't loaded, the player has no PlayerData, or the API call fails.
 * Compiled without RH on the classpath so the Factions plugin still builds without it.
 */
public final class HHLevelLookup {

    private HHLevelLookup() {}

    /**
     * Best-effort lookup of the given player's HH level. Returns -1 if unavailable.
     */
    public static int levelOf(Player player) {
        if (player == null) return -1;
        return levelOf(player.getUniqueId());
    }

    public static int levelOf(UUID uuid) {
        org.bukkit.plugin.Plugin rh = Bukkit.getPluginManager().getPlugin("RegentHeadHunting");
        if (rh == null) return -1;
        try {
            // RH path: rh.getStorageManager().getPlayerData(uuid).getLevel()
            Method getStorageManager = rh.getClass().getMethod("getStorageManager");
            Object storage = getStorageManager.invoke(rh);
            if (storage == null) return -1;

            Method getPlayerData = storage.getClass().getMethod("getPlayerData", UUID.class);
            Object data = getPlayerData.invoke(storage, uuid);
            if (data == null) return -1;

            Method getLevel = data.getClass().getMethod("getLevel");
            Object lvl = getLevel.invoke(data);
            return lvl instanceof Integer i ? i : -1;
        } catch (Exception e) {
            return -1;
        }
    }
}
