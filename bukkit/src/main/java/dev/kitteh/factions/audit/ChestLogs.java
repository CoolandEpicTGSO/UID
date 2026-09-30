package dev.kitteh.factions.audit;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.kitteh.factions.data.json.JsonSaver;
import dev.kitteh.factions.plugin.AbstractFactionsPlugin;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Faction chest (/f pv) item history, kept in its own file — {@code data/chestlogs.json} — rather than
 * on the faction, because it is staff-only data with its own retention policy and no member ever reads
 * it. Entries older than {@link #RETENTION_DAYS} days are dropped on load and on every save.
 * <p>
 * Only ever read through {@code /f chestlogs}, which is op-gated.
 */
@NullMarked
public final class ChestLogs {

    /** Chest history is kept for this many days, then deleted. */
    public static final int RETENTION_DAYS = 3;

    private static final long RETENTION_MS = TimeUnit.DAYS.toMillis(RETENTION_DAYS);

    /** faction id -> that faction's chest history, oldest first. */
    private static final Map<Integer, List<ChestLogEntry>> logs = new HashMap<>();

    private static boolean dirty = false;

    private ChestLogs() {
    }

    /**
     * One add/remove of a single item type by one player, as observed by diffing the vault.
     */
    public static final class ChestLogEntry {
        public long timestamp;
        public String actorName;
        public UUID actorUuid;
        /** true = items put into the vault, false = items taken out. */
        public boolean added;
        /** Bukkit {@code Material} name. */
        public String material;
        public int amount;
        /** Item display name, when it had one — so staff can tell a named/enchanted item apart. */
        public String itemName;

        public ChestLogEntry() {
        } // for Gson

        public ChestLogEntry(long timestamp, String actorName, UUID actorUuid, boolean added,
                             String material, int amount, String itemName) {
            this.timestamp = timestamp;
            this.actorName = actorName;
            this.actorUuid = actorUuid;
            this.added = added;
            this.material = material;
            this.amount = amount;
            this.itemName = itemName;
        }
    }

    public static void record(int factionId, ChestLogEntry entry) {
        logs.computeIfAbsent(factionId, id -> new ArrayList<>()).add(entry);
        dirty = true;
    }

    /**
     * @return the faction's chest history, oldest first. Never null.
     */
    public static List<ChestLogEntry> forFaction(int factionId) {
        List<ChestLogEntry> list = logs.get(factionId);
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }

    /**
     * Drops entries past the retention window. Returns how many were removed.
     */
    private static int prune() {
        long cutoff = System.currentTimeMillis() - RETENTION_MS;
        int removed = 0;
        for (List<ChestLogEntry> list : logs.values()) {
            // Entries are appended in chronological order, so the expired ones are a prefix.
            int keepFrom = 0;
            while (keepFrom < list.size() && list.get(keepFrom).timestamp < cutoff) {
                keepFrom++;
            }
            if (keepFrom > 0) {
                list.subList(0, keepFrom).clear();
                removed += keepFrom;
            }
        }
        logs.entrySet().removeIf(e -> e.getValue().isEmpty());
        return removed;
    }

    private static Path path() {
        return AbstractFactionsPlugin.instance().getDataFolder().toPath().resolve("data/chestlogs.json");
    }

    public static void load() {
        Path path = path();
        if (!Files.exists(path)) {
            return;
        }
        try {
            String content = Files.readString(path);
            Gson gson = AbstractFactionsPlugin.instance().gson();
            Map<Integer, List<ChestLogEntry>> data = gson.fromJson(content,
                    new TypeToken<Map<Integer, List<ChestLogEntry>>>() {
                    }.getType());
            if (data != null) {
                logs.clear();
                data.forEach((id, list) -> logs.put(id, new ArrayList<>(list)));
            }
        } catch (IOException | RuntimeException e) {
            AbstractFactionsPlugin.instance().getLogger().log(Level.SEVERE, "Failed to load chestlogs.json", e);
            return;
        }

        int expired = prune();
        if (expired > 0) {
            dirty = true;
            AbstractFactionsPlugin.instance().getLogger().info("Dropped " + expired
                    + " chest log entr" + (expired == 1 ? "y" : "ies") + " older than " + RETENTION_DAYS + " days.");
        }
    }

    /**
     * Prunes and writes the log to disk. No-op when nothing has changed since the last save.
     *
     * @param sync write on the calling thread (use during shutdown)
     */
    public static void save(boolean sync) {
        boolean pruned = prune() > 0;
        if (!dirty && !pruned) {
            return;
        }
        dirty = false;

        // Snapshot on the calling (main) thread; JsonSaver may serialize on an async one.
        Map<Integer, List<ChestLogEntry>> snapshot = new HashMap<>();
        logs.forEach((id, list) -> snapshot.put(id, new ArrayList<>(list)));

        Path path = path();
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            AbstractFactionsPlugin.instance().getLogger().log(Level.SEVERE, "Failed to create data directory", e);
            return;
        }
        JsonSaver.write(path, () -> AbstractFactionsPlugin.instance().gson().toJson(snapshot), sync);
    }
}
