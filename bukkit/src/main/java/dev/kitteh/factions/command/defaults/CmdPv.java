package dev.kitteh.factions.command.defaults;

import dev.kitteh.factions.FPlayer;
import dev.kitteh.factions.Faction;
import dev.kitteh.factions.Factions;
import dev.kitteh.factions.FactionsPlugin;
import dev.kitteh.factions.audit.AuditEntry;
import dev.kitteh.factions.audit.ChestLogs;
import dev.kitteh.factions.event.FactionAutoDisbandEvent;
import dev.kitteh.factions.event.FactionDisbandEvent;
import dev.kitteh.factions.event.FPlayerLeaveEvent;
import dev.kitteh.factions.plugin.AbstractFactionsPlugin;
import dev.kitteh.factions.command.Cloudy;
import dev.kitteh.factions.command.Cmd;
import dev.kitteh.factions.command.Sender;
import dev.kitteh.factions.level.FactionLevelsConfig;
import dev.kitteh.factions.permissible.PermissibleActions;
import dev.kitteh.factions.util.Permission;
import dev.kitteh.factions.util.TriConsumer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.Nullable;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Faction private vault.
 * <p>
 * A faction's vault is one live {@link Inventory} shared by every viewer, like a real chest. It is
 * deserialized from {@link Faction#pvContents()} on first open and written back on a timer, when the
 * last viewer leaves, and on shutdown. While a vault is live the inventory is the only copy of the
 * items, so there is nothing for a suppressed close packet to desync from.
 */
public class CmdPv implements Cmd, Listener {

    // faction id -> that faction's one live vault inventory
    private static final Map<Integer, Inventory> liveVaults = new HashMap<>();

    // player UUID -> item counts in the vault when they opened it, for chest-log diffing
    private static final Map<UUID, Map<ItemKey, Integer>> openSnapshots = new HashMap<>();

    private static final long FLUSH_INTERVAL_TICKS = 20L * 30L;

    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        return (manager, builder, help) -> {
            manager.command(
                    builder.literal("pv", "chest")
                            .permission(builder.commandPermission().and(Cloudy.hasPermission(Permission.PV).and(Cloudy.hasFaction())))
                            .commandDescription(Cloudy.desc("Open your faction's private vault"))
                            .handler(this::handle)
            );
            AbstractFactionsPlugin plugin = AbstractFactionsPlugin.instance();
            Bukkit.getPluginManager().registerEvents(this, plugin);
            Bukkit.getScheduler().runTaskTimer(plugin, CmdPv::flushAll, FLUSH_INTERVAL_TICKS, FLUSH_INTERVAL_TICKS);
        };
    }

    private void handle(CommandContext<Sender> context) {
        FPlayer fPlayer = ((Sender.Player) context.sender()).fPlayer();
        Player player = ((Sender.Player) context.sender()).player();
        Faction faction = fPlayer.faction();

        if (!faction.hasAccess(fPlayer, PermissibleActions.PV, fPlayer.lastStoodAt())) {
            fPlayer.sendRichMessage("<red>You do not have permission to use the faction PV.");
            return;
        }

        int slots = slotsFor(faction);
        if (slots <= 0) {
            fPlayer.sendRichMessage("<red>Your faction hasn't unlocked the Faction PV yet! Use <gold>/f upgrades</gold> to purchase it.");
            return;
        }

        Inventory inv = liveVaults.computeIfAbsent(faction.id(), id -> load(faction, slots));

        faction.recordFactionLog(AuditEntry.generic(player.getName(), player.getUniqueId(),
                "pv-open", player.getName() + " opened the faction vault"));

        // Snapshot for the chest log: whatever this player leaves behind is diffed against this.
        openSnapshots.put(player.getUniqueId(), tally(inv));

        player.openInventory(inv);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof PvHolder holder)) {
            return;
        }

        // Diff against what was there when this player opened it, whatever happens to the faction.
        recordChestChanges(holder.factionId(), event.getPlayer(), event.getInventory());

        Faction faction = Factions.factions().get(holder.factionId());
        if (faction == null) {
            // The faction was disbanded out from under an open vault. The items on screen are real and
            // have nowhere to be saved to, so hand them back rather than letting the viewer keep a copy
            // the storage never debited.
            liveVaults.remove(holder.factionId());
            refundToViewer(event.getPlayer(), event.getInventory());
            return;
        }

        if (event.getPlayer() instanceof Player player) {
            faction.recordFactionLog(AuditEntry.generic(player.getName(), player.getUniqueId(),
                    "pv-close", player.getName() + " closed the faction vault"));
        }

        save(faction, event.getInventory());

        // The closing viewer is still listed as a viewer during this event, so discount them.
        List<HumanEntity> remaining = new ArrayList<>(event.getInventory().getViewers());
        remaining.remove(event.getPlayer());
        if (remaining.isEmpty()) {
            liveVaults.remove(holder.factionId());
        }
    }

    /**
     * Flushes and closes a faction's vault before the faction stops existing. Runs at
     * {@link EventPriority#MONITOR} on the disband events, which all fire while the faction is still
     * live and therefore still saveable; without this a viewer could empty an open vault into their
     * own inventory while the close handler had no faction left to debit.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDisband(FactionDisbandEvent event) {
        closeVault(event.getFaction());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAutoDisband(FactionAutoDisbandEvent event) {
        closeVault(event.getFaction());
    }

    /**
     * Boots a departing player out of the vault. Access is only checked when the vault is opened, so
     * without this a kicked or departed member keeps a live, fully usable vault on screen.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLeave(FPlayerLeaveEvent event) {
        Inventory inv = liveVaults.get(event.getFaction().id());
        if (inv == null) {
            return;
        }
        Player player = event.getFPlayer().asPlayer();
        if (player != null && inv.getViewers().contains(player)) {
            player.closeInventory();
        }
    }

    // ── Chest logs (/f chestlogs, staff-only) ─────────────────────────────────

    /**
     * Identity of a stack for counting purposes: same material and same metadata (name, enchants,
     * components) collapse into one tally, so 12 plain diamonds are one entry rather than twelve.
     */
    private record ItemKey(Material material, String meta, String displayName) {
    }

    private static Map<ItemKey, Integer> tally(Inventory inv) {
        Map<ItemKey, Integer> counts = new HashMap<>();
        for (ItemStack item : inv.getContents()) {
            if (item == null || !item.getType().isItem()) {
                continue;
            }
            counts.merge(keyOf(item), item.getAmount(), Integer::sum);
        }
        return counts;
    }

    private static ItemKey keyOf(ItemStack item) {
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        String display = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : "";
        // ItemMeta's toString captures enchants/components, so two visually identical but distinct
        // items don't collapse together.
        return new ItemKey(item.getType(), meta == null ? "" : meta.toString(), display);
    }

    /**
     * Diffs the vault against the snapshot taken when this player opened it and writes the net changes
     * to the chest log. Re-baselines every other viewer afterwards, so a change is attributed to
     * exactly one player rather than to everyone who happens to be looking.
     */
    private static void recordChestChanges(int factionId, HumanEntity viewer, Inventory inv) {
        if (!(viewer instanceof Player player)) {
            return;
        }

        Map<ItemKey, Integer> before = openSnapshots.remove(player.getUniqueId());
        if (before == null) {
            return;
        }

        Map<ItemKey, Integer> after = tally(inv);
        long now = System.currentTimeMillis();

        Set<ItemKey> touched = new HashSet<>(before.keySet());
        touched.addAll(after.keySet());

        boolean changed = false;
        for (ItemKey key : touched) {
            int delta = after.getOrDefault(key, 0) - before.getOrDefault(key, 0);
            if (delta == 0) {
                continue;
            }
            changed = true;
            ChestLogs.record(factionId, new ChestLogs.ChestLogEntry(
                    now, player.getName(), player.getUniqueId(),
                    delta > 0, key.material().name(), Math.abs(delta), key.displayName()));
        }

        if (!changed) {
            return;
        }

        // Someone else still has it open: reset their baseline to the current contents so this player's
        // changes aren't attributed to them again when they close.
        Map<ItemKey, Integer> rebased = null;
        for (HumanEntity other : inv.getViewers()) {
            if (other.equals(player) || !(other instanceof Player op)) {
                continue;
            }
            if (openSnapshots.containsKey(op.getUniqueId())) {
                if (rebased == null) {
                    rebased = after;
                }
                openSnapshots.put(op.getUniqueId(), rebased);
            }
        }
    }

    /**
     * Saves a faction's vault and force-closes every viewer, evicting it from the live map.
     */
    private static void closeVault(Faction faction) {
        Inventory inv = liveVaults.remove(faction.id());
        if (inv == null) {
            return;
        }
        save(faction, inv);
        for (HumanEntity viewer : new ArrayList<>(inv.getViewers())) {
            viewer.closeInventory();
        }
    }

    /**
     * Last-resort return of a dead vault's contents to whoever was looking at it: anything that doesn't
     * fit in their inventory drops at their feet. Only reachable if a faction vanished while its vault
     * was open without the disband hooks firing.
     */
    private static void refundToViewer(HumanEntity viewer, Inventory inv) {
        if (!(viewer instanceof Player player)) {
            return;
        }
        for (ItemStack item : inv.getContents()) {
            if (item == null || !item.getType().isItem()) {
                continue;
            }
            for (ItemStack leftover : player.getInventory().addItem(item).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        inv.clear();
        AbstractFactionsPlugin.instance().getLogger().warning("Faction vault closed with no owning faction; returned contents to "
                + player.getName() + ".");
    }

    /**
     * Writes every live vault back to its faction, dropping entries whose faction no longer exists.
     */
    public static void flushAll() {
        liveVaults.entrySet().removeIf(entry -> {
            Faction faction = Factions.factions().get(entry.getKey());
            if (faction == null) {
                return true;
            }
            save(faction, entry.getValue());
            return false;
        });
        ChestLogs.save(false);
    }

    /**
     * Flushes and force-closes every open vault. Must run during {@code onDisable} <em>before</em>
     * faction data is force-saved, or a restart with a vault open strands the items in memory and
     * leaves the pre-open copy on disk to be duped.
     */
    public static void shutdown() {
        // Close first: this diffs each viewer's changes into the chest log and saves the vault, so the
        // synchronous ChestLogs.save below captures them.
        for (Inventory inv : new ArrayList<>(liveVaults.values())) {
            for (HumanEntity viewer : new ArrayList<>(inv.getViewers())) {
                viewer.closeInventory();
            }
        }
        flushAll();
        liveVaults.clear();
        openSnapshots.clear();
        ChestLogs.save(true); // sync: an async write scheduled during onDisable would never run
    }

    private static int slotsFor(Faction faction) {
        FactionLevelsConfig cfg = FactionsPlugin.instance().configManager().factionLevelsConfig();
        int tier = faction.customUpgradeTier(CmdUpgrades.KEY_PV);
        int slots = cfg.getPvBaseSlots() + tier * cfg.getPvSlotsPerTier();
        if (slots <= 0) {
            return 0;
        }
        return Math.min(54, Math.max(9, (slots / 9) * 9));
    }

    private static Inventory load(Faction faction, int slots) {
        Inventory inv = Bukkit.createInventory(new PvHolder(faction.id()), slots,
                "§5§lFaction Vault §7[" + slots + " slots]");

        String[] saved = faction.pvContents();
        int stranded = 0;
        for (int i = 0; i < saved.length; i++) {
            if (saved[i] == null || saved[i].isEmpty()) {
                continue;
            }
            ItemStack item = deserializeItem(saved[i]);
            if (item == null) {
                continue;
            }
            if (i < slots) {
                inv.setItem(i, item);
            } else if (inv.firstEmpty() != -1) {
                // Vault shrank (tier lowered, config changed). Pull the items forward into free slots
                // instead of leaving them unreachable.
                inv.addItem(item);
            } else {
                stranded++;
            }
        }

        if (stranded > 0) {
            AbstractFactionsPlugin.instance().getLogger().warning("Faction " + faction.tag() + " (" + faction.id()
                    + ") has " + stranded + " vault item(s) past its " + slots + " unlocked slots. They are kept in"
                    + " storage but cannot be shown; restore the PV upgrade tier to recover them.");
        }

        return inv;
    }

    private static void save(Faction faction, Inventory inv) {
        ItemStack[] contents = inv.getContents();
        String[] stored = faction.pvContents();

        // Never shrink the stored array: a faction whose vault shrank has items parked past the end of
        // the live inventory, and truncating would delete them.
        String[] encoded = new String[Math.max(contents.length, stored.length)];
        for (int i = 0; i < encoded.length; i++) {
            if (i >= contents.length) {
                encoded[i] = stored[i] == null ? "" : stored[i];
                continue;
            }

            ItemStack item = contents[i];
            if (item == null || !item.getType().isItem()) {
                encoded[i] = "";
                continue;
            }

            String serialized = serializeItem(item);
            if (serialized == null) {
                // Serialization failed — most likely a malformed/hostile stack. Writing "" here would
                // erase it from storage while the player still holds a live copy, so keep whatever was
                // stored for this slot and make noise instead.
                encoded[i] = (i < stored.length && stored[i] != null) ? stored[i] : "";
                AbstractFactionsPlugin.instance().getLogger().warning("Failed to serialize " + item.getType()
                        + " in slot " + i + " of faction " + faction.tag() + " (" + faction.id()
                        + ")'s vault; slot left as previously stored.");
            } else {
                encoded[i] = serialized;
            }
        }

        faction.pvContents(encoded);
    }

    /**
     * @return the encoded item, or null if it could not be serialized. Never returns "" for a failure —
     * callers must be able to tell "empty slot" apart from "this item did not survive encoding".
     */
    private static @Nullable String serializeItem(ItemStack item) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream boos = new BukkitObjectOutputStream(baos)) {
            boos.writeObject(item);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }

    private static ItemStack deserializeItem(String data) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream bois = new BukkitObjectInputStream(bais)) {
            return (ItemStack) bois.readObject();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Marks an inventory as a faction vault and records which faction owns it, so close handling never
     * has to infer it from the inventory type or title.
     */
    private record PvHolder(int factionId) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            throw new UnsupportedOperationException();
        }
    }
}
