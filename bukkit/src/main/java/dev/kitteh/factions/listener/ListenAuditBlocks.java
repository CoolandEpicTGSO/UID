package dev.kitteh.factions.listener;

import dev.kitteh.factions.Board;
import dev.kitteh.factions.FLocation;
import dev.kitteh.factions.Faction;
import dev.kitteh.factions.audit.AuditEntry;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Listener that records per-block actions inside faction territory for /f inspect.
 * Only normal player factions get block logs; wilderness/safezone/warzone are skipped.
 */
public class ListenAuditBlocks implements Listener {

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
        faction.recordBlockLog(entry);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Faction faction = ownerOf(event.getBlock().getLocation());
        if (faction == null) return;
        log(faction, event.getPlayer(), "place",
                "+placed " + event.getBlock().getType().name(),
                event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Faction faction = ownerOf(event.getBlock().getLocation());
        if (faction == null) return;
        log(faction, event.getPlayer(), "break",
                "-broke " + event.getBlock().getType().name(),
                event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onContainerOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof org.bukkit.block.BlockState bs)) return;
        Block block = bs.getBlock();
        Faction faction = ownerOf(block.getLocation());
        if (faction == null) return;
        log(faction, player, "open",
                "opened " + block.getType().name(),
                block.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Faction faction = ownerOf(event.getBlock().getLocation());
        if (faction == null) return;
        log(faction, event.getPlayer(), "place",
                "+poured " + event.getBucket().name(),
                event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Faction faction = ownerOf(event.getBlock().getLocation());
        if (faction == null) return;
        log(faction, event.getPlayer(), "break",
                "-collected " + event.getBlock().getType().name(),
                event.getBlock().getLocation());
    }
}
