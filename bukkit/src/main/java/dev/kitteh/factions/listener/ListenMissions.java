package dev.kitteh.factions.listener;

import dev.kitteh.factions.FPlayer;
import dev.kitteh.factions.FPlayers;
import dev.kitteh.factions.Faction;
import dev.kitteh.factions.mission.MissionManager;
import dev.kitteh.factions.mission.MissionType;
import dev.kitteh.factions.plugin.AbstractFactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Feeds runtime increments to MissionManager based on Bukkit events plus a
 * per-minute scheduled tick for playtime.
 *
 * <ul>
 *   <li>{@code PlayerDeathEvent} → PLAYER_KILLS for the killer's faction.</li>
 *   <li>{@code BlockBreakEvent} → BLOCK_MINED keyed by Material name.</li>
 *   <li>Scheduled minute tick → FACTION_PLAYTIME_MINUTES, +1 per online faction
 *       member's faction (deduplicated so a faction with N online members gains N
 *       per minute, matching real player-minutes contributed).</li>
 * </ul>
 *
 * Heads-sold, crops-sold, outpost-captures, outpost-hold are wired up by external integrations
 * (RegentHeadHunting / shop / outpost plugin) calling {@code MissionManager.increment(...)} directly.
 */
public class ListenMissions implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        FPlayer killerFp = FPlayers.fPlayers().get(killer);
        if (!killerFp.hasFaction()) return;

        Faction killerFaction = killerFp.faction();
        if (!killerFaction.isNormal()) return;

        MissionManager.increment(killerFaction, MissionType.PLAYER_KILLS, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        FPlayer fp = FPlayers.fPlayers().get(event.getPlayer());
        if (!fp.hasFaction()) return;

        Faction faction = fp.faction();
        if (!faction.isNormal()) return;

        Block block = event.getBlock();
        String key = block.getType().name();
        MissionManager.increment(faction, MissionType.BLOCK_MINED, key, 1);
    }

    /**
     * Mob kills (non-player). Player kills are already handled by {@link #onPlayerDeath}.
     * Credits MOB_KILLED keyed by EntityType name (e.g. "ZOMBIE").
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead instanceof Player) return; // covered by PlayerDeathEvent

        Player killer = dead.getKiller();
        if (killer == null) return;

        FPlayer fp = FPlayers.fPlayers().get(killer);
        if (!fp.hasFaction()) return;
        Faction faction = fp.faction();
        if (!faction.isNormal()) return;

        String key = dead.getType().name();
        MissionManager.increment(faction, MissionType.MOB_KILLED, key, 1);
    }

    /**
     * Fishing. Credits FISH_CAUGHT only when the catch is an actual fish item
     * (not junk). The key is the fish material name (COD/SALMON/PUFFERFISH/TROPICAL_FISH).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Entity caught = event.getCaught();
        if (caught == null) return;

        // CAUGHT_FISH is fired with an Item entity; check its stack type
        if (!(caught instanceof org.bukkit.entity.Item itemEntity)) return;
        ItemStack stack = itemEntity.getItemStack();
        Material mat = stack.getType();
        if (mat != Material.COD && mat != Material.SALMON
                && mat != Material.PUFFERFISH && mat != Material.TROPICAL_FISH) {
            return;
        }

        FPlayer fp = FPlayers.fPlayers().get(event.getPlayer());
        if (!fp.hasFaction()) return;
        Faction faction = fp.faction();
        if (!faction.isNormal()) return;

        MissionManager.increment(faction, MissionType.FISH_CAUGHT, mat.name(), stack.getAmount());
    }

    /**
     * Starts the per-minute playtime tick. Each online player adds 1 minute of
     * faction playtime to their faction every minute, so a faction with 3 online
     * members gains 3 minutes per real-time minute (matches "total faction playtime").
     */
    public static void startPlaytimeTicker(AbstractFactionsPlugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Map<Faction, Long> contributions = new HashMap<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                FPlayer fp = FPlayers.fPlayers().get(player);
                if (!fp.hasFaction()) continue;
                Faction faction = fp.faction();
                if (!faction.isNormal()) continue;
                contributions.merge(faction, 1L, Long::sum);
            }
            for (Map.Entry<Faction, Long> entry : contributions.entrySet()) {
                MissionManager.increment(entry.getKey(), MissionType.FACTION_PLAYTIME_MINUTES, entry.getValue());
            }
        }, 60L * 20L, 60L * 20L);
    }
}
