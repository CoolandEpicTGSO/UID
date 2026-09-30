package dev.kitteh.factions.command.defaults;

import dev.kitteh.factions.FPlayer;
import dev.kitteh.factions.FPlayers;
import dev.kitteh.factions.Faction;
import dev.kitteh.factions.Factions;
import dev.kitteh.factions.FactionsPlugin;
import dev.kitteh.factions.plugin.AbstractFactionsPlugin;
import dev.kitteh.factions.command.Cloudy;
import dev.kitteh.factions.command.Cmd;
import dev.kitteh.factions.command.Sender;
import dev.kitteh.factions.util.ComponentDispatcher;
import dev.kitteh.factions.util.Permission;
import dev.kitteh.factions.util.TeleportCountdown;
import dev.kitteh.factions.util.TriConsumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.scheduler.BukkitTask;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;
import org.incendo.cloud.parser.standard.IntegerParser;

import java.util.HashMap;
import java.util.Map;

public class CmdBanner implements Cmd, Listener {

    private static final int BAR_LENGTH = 20;
    private static final int BANNER_TP_SECONDS = 5;

    private static final Map<Integer, BannerEntry> activeBanners = new HashMap<>();
    /** factionId -> epoch millis the faction last deployed a banner (for the cooldown). */
    private static final Map<Integer, Long> lastDeploy = new HashMap<>();

    private record BannerEntry(
            Location location,
            ArmorStand nameStand,
            ArmorStand barStand,
            BukkitTask tickTask,
            BukkitTask expireTask,
            long expiresAt
    ) {}

    @Override
    public TriConsumer<CommandManager<Sender>, org.incendo.cloud.Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        return (manager, builder, help) -> {
            manager.command(
                    builder.literal("banner")
                            .permission(builder.commandPermission().and(Cloudy.hasPermission(Permission.BANNER).and(Cloudy.hasFaction())))
                            .commandDescription(Cloudy.desc("Deploy a faction rally banner at your location"))
                            .handler(this::handle)
            );
            // Hidden subcommand used by the chat-clickback for banner teleport
            manager.command(
                    builder.literal("banner-tp")
                            .meta(HIDE_IN_HELP, true)
                            .permission(builder.commandPermission().and(Cloudy.hasPermission(Permission.BANNER).and(Cloudy.hasFaction())))
                            .required("factionId", IntegerParser.integerParser())
                            .handler(this::handleTp)
            );
            Bukkit.getPluginManager().registerEvents(this, AbstractFactionsPlugin.instance());
        };
    }

    private void handleTp(CommandContext<Sender> context) {
        Player player = ((Sender.Player) context.sender()).player();
        int factionId = context.get("factionId");
        BannerEntry entry = activeBanners.get(factionId);
        if (entry == null) {
            player.sendMessage("§cThat banner has expired.");
            return;
        }

        FPlayer fp = ((Sender.Player) context.sender()).fPlayer();
        if (!fp.hasFaction() || fp.faction().id() != factionId) {
            player.sendMessage("§cYou can only teleport to your own faction's banner.");
            return;
        }

        Location dest = entry.location().clone().add(0.5, 1.0, 0.5);
        TeleportCountdown.start(player, BANNER_TP_SECONDS, "Rallying to faction banner", () -> {
            BannerEntry stillThere = activeBanners.get(factionId);
            if (stillThere == null) {
                player.sendMessage("§cThe banner expired before you arrived.");
                return;
            }
            AbstractFactionsPlugin.instance().teleport(player, dest);
        });
    }

    private void handle(CommandContext<Sender> context) {
        FPlayer fPlayer = ((Sender.Player) context.sender()).fPlayer();
        Player player = ((Sender.Player) context.sender()).player();
        Faction faction = fPlayer.faction();

        if (faction.customUpgradeTier(CmdUpgrades.KEY_BANNER) < 1) {
            fPlayer.sendRichMessage("<red>Your faction hasn't unlocked F Banner yet! Use <gold>/f upgrades</gold> to purchase it.");
            return;
        }

        // Per-faction cooldown between banner deployments
        int cooldownSeconds = FactionsPlugin.instance().configManager().factionLevelsConfig().getBannerCooldownSeconds();
        if (cooldownSeconds > 0) {
            Long last = lastDeploy.get(faction.id());
            if (last != null) {
                long elapsed = System.currentTimeMillis() - last;
                long remainingMs = cooldownSeconds * 1000L - elapsed;
                if (remainingMs > 0) {
                    fPlayer.sendRichMessage("<red>Your faction's banner is on cooldown — <yellow>"
                            + formatCooldown(remainingMs) + "</yellow> remaining.");
                    return;
                }
            }
        }
        lastDeploy.put(faction.id(), System.currentTimeMillis());

        removeBanner(faction.id());

        Location loc = player.getLocation().getBlock().getLocation();
        loc.getBlock().setType(Material.BLACK_BANNER);

        int expireSeconds = FactionsPlugin.instance().configManager().factionLevelsConfig().getBannerExpireSeconds();
        long expiresAt = System.currentTimeMillis() + expireSeconds * 1000L;

        // Holograms: two invisible marker ArmorStands stacked above the banner
        // Banner top is at Y+1; name line at Y+2.6, bar line at Y+2.2
        Location nameLoc = loc.clone().add(0.5, 2.6, 0.5);
        Location barLoc  = loc.clone().add(0.5, 2.2, 0.5);

        Component nameText = Component.text(faction.tag(), NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(" ⚑ Banner", NamedTextColor.RED).decoration(TextDecoration.BOLD, false));

        ArmorStand nameStand = spawnHologram(nameLoc, nameText);
        ArmorStand barStand  = spawnHologram(barLoc, buildProgressBar(expireSeconds, expireSeconds));

        // Chat announcement
        String coords = loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
        String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "world";
        String tpCmd = "/" + Cmd.rootCommand() + " banner-tp " + faction.id();

        Component msg = Component.text()
                .append(Component.text("⚑ ", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(faction.tag(), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" banner deployed at ", NamedTextColor.YELLOW))
                .append(Component.text("[" + coords + " in " + worldName + "]",
                        NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand(tpCmd))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                Component.text("Click to rally (5s warmup)", NamedTextColor.AQUA))))
                .append(Component.text(" — expires in " + expireSeconds + "s", NamedTextColor.GRAY))
                .build();

        for (FPlayer member : faction.membersOnline(true, fPlayer)) {
            Player online = Bukkit.getPlayer(member.uniqueId());
            if (online != null) ComponentDispatcher.send(online, msg);
        }
        ComponentDispatcher.send(player, msg);

        final int factionId = faction.id();

        // Update progress bar every second
        BukkitTask tickTask = Bukkit.getScheduler().runTaskTimer(AbstractFactionsPlugin.instance(), () -> {
            BannerEntry entry = activeBanners.get(factionId);
            if (entry == null) return;
            long remaining = Math.max(0, entry.expiresAt() - System.currentTimeMillis());
            int remainingSec = (int) (remaining / 1000);
            if (entry.barStand().isValid()) {
                entry.barStand().setCustomName(LegacyComponentSerializer.legacySection().serialize(buildProgressBar(remainingSec, expireSeconds)));
            }
        }, 20L, 20L);

        BukkitTask expireTask = Bukkit.getScheduler().runTaskLater(AbstractFactionsPlugin.instance(),
                () -> removeBanner(factionId), expireSeconds * 20L);

        activeBanners.put(factionId, new BannerEntry(loc, nameStand, barStand, tickTask, expireTask, expiresAt));
    }

    private static void removeBanner(int factionId) {
        BannerEntry entry = activeBanners.remove(factionId);
        if (entry == null) return;

        entry.tickTask().cancel();
        entry.expireTask().cancel();

        if (entry.nameStand().isValid()) entry.nameStand().remove();
        if (entry.barStand().isValid()) entry.barStand().remove();

        Block b = entry.location().getBlock();
        if (Tag.BANNERS.isTagged(b.getType())) b.setType(Material.AIR);

        Faction faction = Factions.factions().get(factionId);
        if (faction != null) {
            for (FPlayer member : faction.membersOnline(true, null)) {
                Player online = Bukkit.getPlayer(member.uniqueId());
                if (online != null) {
                    ComponentDispatcher.send(online,
                            Component.text("⚑ Faction banner has expired.", NamedTextColor.GRAY));
                }
            }
        }
    }

    private static boolean isBannerBlock(Block block) {
        if (!Tag.BANNERS.isTagged(block.getType())) return false;
        for (BannerEntry entry : activeBanners.values()) {
            if (entry.location().getBlock().equals(block)) return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isBannerBlock(event.getBlock())) return;
        event.setCancelled(true);
        event.setDropItems(false);

        Player breaker = event.getPlayer();
        FPlayer fp = FPlayers.fPlayers().get(breaker);
        fp.sendRichMessage("<red>Faction banners are indestructible!");
    }

    @EventHandler(ignoreCancelled = false)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        if (isBannerBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = false)
    public void onBlockBurn(BlockBurnEvent event) {
        if (isBannerBlock(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = false)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (isBannerBlock(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = false)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (isBannerBlock(event.getToBlock())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = false)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (isBannerBlock(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = false)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            if (isBannerBlock(b)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = false)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (isBannerBlock(b)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = false)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(CmdBanner::isBannerBlock);
    }

    @EventHandler(ignoreCancelled = false)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(CmdBanner::isBannerBlock);
    }

    private static ArmorStand spawnHologram(Location loc, Component name) {
        ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.setCustomNameVisible(true);
        stand.setCustomName(LegacyComponentSerializer.legacySection().serialize(name));
        stand.setPersistent(false);
        stand.setInvulnerable(true);
        return stand;
    }

    private static Component buildProgressBar(int remainingSec, int totalSec) {
        int filled = totalSec > 0 ? (int) Math.round((double) remainingSec / totalSec * BAR_LENGTH) : 0;
        filled = Math.max(0, Math.min(BAR_LENGTH, filled));
        return Component.text("█".repeat(filled), NamedTextColor.GREEN)
                .append(Component.text("█".repeat(BAR_LENGTH - filled), NamedTextColor.DARK_GRAY))
                .append(Component.text(" " + remainingSec + "s", NamedTextColor.YELLOW));
    }

    public static boolean hasBanner(int factionId) {
        return activeBanners.containsKey(factionId);
    }

    /** Render a remaining cooldown (in millis) as a short "Xm Ys" / "Ys" string. */
    private static String formatCooldown(long remainingMs) {
        long totalSec = (remainingMs + 999) / 1000; // round up
        long minutes = totalSec / 60;
        long seconds = totalSec % 60;
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }
}
