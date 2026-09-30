package dev.kitteh.factions.listener;

import dev.kitteh.factions.FPlayer;
import dev.kitteh.factions.Faction;
import dev.kitteh.factions.audit.AuditEntry;
import dev.kitteh.factions.event.FPlayerJoinEvent;
import dev.kitteh.factions.event.FPlayerLeaveEvent;
import dev.kitteh.factions.event.FactionRelationEvent;
import dev.kitteh.factions.event.FactionRenameEvent;
import dev.kitteh.factions.event.FactionSetHomeEvent;
import dev.kitteh.factions.event.LandClaimEvent;
import dev.kitteh.factions.event.LandUnclaimAllEvent;
import dev.kitteh.factions.event.LandUnclaimEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Listens for faction-management events and writes them to /f logs.
 */
public class ListenAuditFaction implements Listener {

    private static void log(Faction faction, FPlayer actor, String type, String message) {
        if (faction == null || !faction.isNormal()) return;
        AuditEntry entry = AuditEntry.generic(
                actor != null ? actor.name() : "(server)",
                actor != null ? actor.uniqueId() : null,
                type,
                message
        );
        faction.recordFactionLog(entry);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClaim(LandClaimEvent event) {
        log(event.getFaction(), event.getFPlayer(), "claim",
                "claimed chunk " + event.getLocation().x() + ", " + event.getLocation().z());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUnclaim(LandUnclaimEvent event) {
        log(event.getFaction(), event.getFPlayer(), "unclaim",
                "unclaimed chunk " + event.getLocation().x() + ", " + event.getLocation().z());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUnclaimAll(LandUnclaimAllEvent event) {
        log(event.getFaction(), event.getFPlayer(), "unclaim",
                "unclaimed ALL faction land");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSetHome(FactionSetHomeEvent event) {
        org.bukkit.Location loc = event.getLocation();
        String coords = loc == null ? "" :
                " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()
                        + " in " + (loc.getWorld() == null ? "?" : loc.getWorld().getName()) + ")";
        log(event.getFaction(), event.getFPlayer(), "sethome", "set faction home" + coords);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRename(FactionRenameEvent event) {
        log(event.getFaction(), event.getFPlayer(), "rename", "renamed faction to " + event.getFactionTag());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(FPlayerJoinEvent event) {
        log(event.getFaction(), event.getFPlayer(), "join",
                event.getFPlayer().name() + " joined the faction");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLeave(FPlayerLeaveEvent event) {
        log(event.getFaction(), event.getFPlayer(), "leave",
                event.getFPlayer().name() + " left (" + event.getReason() + ")");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRelation(FactionRelationEvent event) {
        log(event.getFaction(), null, "relation",
                "relation with " + event.getTargetFaction().tag() + " is now " + event.getNewRelation().name());
        // Also mirror to the other faction's log so both sides see it
        log(event.getTargetFaction(), null, "relation",
                "relation with " + event.getFaction().tag() + " is now " + event.getNewRelation().name());
    }
}
