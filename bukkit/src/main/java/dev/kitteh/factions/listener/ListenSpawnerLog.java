package dev.kitteh.factions.listener;

import dev.kitteh.factions.Board;
import dev.kitteh.factions.FLocation;
import dev.kitteh.factions.Faction;
import dev.kitteh.factions.audit.AuditEntry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Records spawner place/break events inside faction territory into the faction's
 * activity log (/f logs → Spawners tab).
 *
 * <p>Tracks every {@link Material#SPAWNER} block — vanilla and RegentHeadHunting
 * custom spawners alike (RH custom spawners are still SPAWNER blocks in the world).</p>
 *
 * <p>The logged "placed on" block is the face the player clicked when placing
 * ({@code BlockPlaceEvent#getBlockAgainst()}); for breaks there is no click face,
 * so the block directly below the spawner is recorded instead.</p>
 */
public class ListenSpawnerLog implements Listener {

    private static Faction ownerOf(Location loc) {
        Faction faction = Board.board().factionAt(new FLocation(loc));
        if (faction == null || !faction.isNormal()) return null;
        return faction;
    }

    private static void log(Faction faction, Player player, String type, String message, Location loc) {
        AuditEntry entry = AuditEntry.block(
                player.getName(),
                player.getUniqueId(),
                type,
                message,
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                loc.getWorld() == null ? "?" : loc.getWorld().getName()
        );
        faction.recordFactionLog(entry);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block placed = event.getBlock();
        if (placed.getType() != Material.SPAWNER) return;

        Faction faction = ownerOf(placed.getLocation());
        if (faction == null) return;

        // The face the player clicked to place the spawner
        Block against = event.getBlockAgainst();
        String surface = against == null ? "AIR" : against.getType().name();

        log(faction, event.getPlayer(), "spawner-place",
                "+spawner placed on " + surface,
                placed.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block broken = event.getBlock();
        if (broken.getType() != Material.SPAWNER) return;

        Faction faction = ownerOf(broken.getLocation());
        if (faction == null) return;

        // No click face on break — use the block directly beneath the spawner
        Block below = broken.getRelative(BlockFace.DOWN);
        String surface = below.getType().name();

        log(faction, event.getPlayer(), "spawner-break",
                "-spawner broken (on " + surface + ")",
                broken.getLocation());
    }
}
