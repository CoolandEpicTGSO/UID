package dev.kitteh.factions.util;

import dev.kitteh.factions.plugin.AbstractFactionsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NullMarked
public final class TeleportCountdown {

    private static final double MOVE_TOLERANCE_SQ = 0.25 * 0.25;

    private static final Map<UUID, BukkitTask> active = new HashMap<>();

    private TeleportCountdown() {}

    public static void start(Player player, int seconds, String label, Runnable onComplete) {
        cancel(player.getUniqueId());

        if (seconds <= 0) {
            onComplete.run();
            return;
        }

        Location startLoc = player.getLocation().clone();
        UUID uuid = player.getUniqueId();

        showTick(player, label, seconds);

        BukkitTask task = new org.bukkit.scheduler.BukkitRunnable() {
            int remaining = seconds;

            @Override
            public void run() {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline()) {
                    cancel();
                    active.remove(uuid);
                    return;
                }
                if (p.getLocation().distanceSquared(startLoc) > MOVE_TOLERANCE_SQ
                        || !p.getWorld().equals(startLoc.getWorld())) {
                    ComponentDispatcher.sendTitle(p,
                            Component.text("✘ Cancelled", NamedTextColor.RED),
                            Component.text("You moved!", NamedTextColor.GRAY),
                            0, 30, 10);
                    cancel();
                    active.remove(uuid);
                    return;
                }

                remaining--;
                if (remaining <= 0) {
                    cancel();
                    active.remove(uuid);
                    ComponentDispatcher.sendTitle(p,
                            Component.text("✦ Teleported!", NamedTextColor.GREEN),
                            Component.empty(),
                            0, 20, 10);
                    onComplete.run();
                } else {
                    showTick(p, label, remaining);
                }
            }
        }.runTaskTimer(AbstractFactionsPlugin.instance(), 20L, 20L);

        active.put(uuid, task);
    }

    public static void cancel(UUID uuid) {
        BukkitTask task = active.remove(uuid);
        if (task != null) task.cancel();
    }

    private static void showTick(Player player, String label, int remaining) {
        Component title = Component.text(String.valueOf(remaining), NamedTextColor.AQUA);
        Component subtitle = Component.text(label, NamedTextColor.GRAY);
        ComponentDispatcher.sendTitle(player, title, subtitle, 0, 25, 5);
    }
}
