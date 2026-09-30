package dev.kitteh.factions.command.defaults;

import dev.kitteh.factions.Board;
import dev.kitteh.factions.FLocation;
import dev.kitteh.factions.FPlayer;
import dev.kitteh.factions.Faction;
import dev.kitteh.factions.audit.AuditEntry;
import dev.kitteh.factions.command.Cloudy;
import dev.kitteh.factions.command.Cmd;
import dev.kitteh.factions.command.Sender;
import dev.kitteh.factions.permissible.PermissibleActions;
import dev.kitteh.factions.permissible.Role;
import dev.kitteh.factions.plugin.AbstractFactionsPlugin;
import dev.kitteh.factions.util.Permission;
import dev.kitteh.factions.util.TriConsumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * /f inspect — toggle inspect mode, then right-click a block in your faction's territory
 * to see a CoreProtect-style chat history of who placed/broke/opened that block.
 */
public class CmdInspect implements Cmd, Listener {

    private static final SimpleDateFormat TS = new SimpleDateFormat("MM/dd HH:mm:ss");
    private static final int MAX_LINES = 12;

    /** UUIDs of players currently in inspect mode. */
    private static final Set<UUID> activeInspectors = new HashSet<>();

    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        return (manager, builder, help) -> {
            manager.command(
                    builder.literal("inspect", "i")
                            .commandDescription(Cloudy.desc("Toggle inspect mode — right-click blocks in your claims to see who modified them"))
                            .permission(builder.commandPermission().and(Cloudy.hasPermission(Permission.INSPECT)))
                            .handler(this::handle)
            );
            Bukkit.getPluginManager().registerEvents(this, AbstractFactionsPlugin.instance());
        };
    }

    private void handle(CommandContext<Sender> context) {
        if (!(context.sender() instanceof Sender.Player playerSender)) {
            context.sender().sendRichMessage("<red>This command can only be run by a player.");
            return;
        }
        FPlayer fPlayer = playerSender.fPlayer();
        Player player = playerSender.player();

        // Faction-side perm check (admins always pass)
        if (fPlayer.hasFaction()
                && fPlayer.role() != Role.ADMIN
                && !fPlayer.faction().hasAccess(fPlayer, PermissibleActions.INSPECT, fPlayer.lastStoodAt())) {
            fPlayer.sendRichMessage("<red>You do not have permission to use <white>/f inspect</white>.");
            return;
        }

        UUID uuid = player.getUniqueId();
        if (activeInspectors.remove(uuid)) {
            player.sendMessage("§7§l[§c§lInspect§7§l] §cInspect mode disabled.");
        } else {
            activeInspectors.add(uuid);
            player.sendMessage("§7§l[§c§lInspect§7§l] §aInspect mode §lENABLED§r§a — right-click any block in your claim.");
            player.sendMessage("§8Run §7/f inspect§8 again to disable.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        org.bukkit.event.block.Action action = event.getAction();
        if (action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                && action != org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (!activeInspectors.contains(player.getUniqueId())) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        event.setCancelled(true); // Don't actually break/open while inspecting

        Location loc = block.getLocation();
        FLocation flocation = new FLocation(loc);
        Faction faction = Board.board().factionAt(flocation);

        if (faction == null || !faction.isNormal()) {
            String ownerName = faction == null ? "?" :
                    faction.isWilderness() ? "Wilderness" :
                    faction.isSafeZone() ? "SafeZone" :
                    faction.isWarZone() ? "WarZone" : faction.tag();
            player.sendMessage("§7§l[§c§lInspect§7§l] §7Block at §e" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()
                    + " §7(chunk §e" + flocation.x() + ", " + flocation.z() + "§7 in §e" + flocation.worldName()
                    + "§7) is in §c" + ownerName + "§7 — no log available.");
            return;
        }

        printBlockHistory(player, faction, loc);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        activeInspectors.remove(event.getPlayer().getUniqueId());
    }

    private static void printBlockHistory(Player player, Faction faction, Location loc) {
        String world = loc.getWorld() == null ? "?" : loc.getWorld().getName();
        List<AuditEntry> hits = faction.blockLogAt(world, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

        player.sendMessage("§7§l[§c§lInspect§7§l] §6Block at §e" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()
                + " §7in §e" + faction.tag());

        if (hits.isEmpty()) {
            player.sendMessage("§8 (no recorded activity at this block)");
            return;
        }

        long now = System.currentTimeMillis();
        int shown = 0;
        for (AuditEntry e : hits) {
            if (shown++ >= MAX_LINES) {
                player.sendMessage("§8 (and " + (hits.size() - MAX_LINES) + " more — only newest " + MAX_LINES + " shown)");
                break;
            }
            String ago = formatAgo(now - e.timestamp);
            String typeColor = switch (e.type) {
                case "place" -> "§a";
                case "break" -> "§c";
                case "open" -> "§e";
                default -> "§f";
            };
            player.sendMessage("§7" + ago + " §8| §f" + e.actorName + " §7" + typeColor + e.message);
        }
    }

    private static String formatAgo(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000;
        if (s < 60) return s + "s ago";
        long m = s / 60;
        if (m < 60) return m + "m ago";
        long h = m / 60;
        if (h < 24) return h + "h ago";
        long d = h / 24;
        if (d < 30) return d + "d ago";
        // For older entries, fall back to absolute timestamp
        return TS.format(new Date(System.currentTimeMillis() - ms));
    }
}
