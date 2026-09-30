package dev.kitteh.factions.audit;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * One faction audit log entry — used for both per-block history (/f inspect) and
 * faction-management activity (/f logs).
 */
@NullMarked
public class AuditEntry {

    public long timestamp;
    public String actorName;
    public @Nullable UUID actorUuid;
    public String type;
    public String message;

    /** Block coords for inspect entries; -1/-1/-1 for non-block events. */
    public int x;
    public int y;
    public int z;
    public @Nullable String world;

    public AuditEntry() {} // for Gson

    public AuditEntry(long timestamp, String actorName, @Nullable UUID actorUuid,
                      String type, String message,
                      int x, int y, int z, @Nullable String world) {
        this.timestamp = timestamp;
        this.actorName = actorName;
        this.actorUuid = actorUuid;
        this.type = type;
        this.message = message;
        this.x = x;
        this.y = y;
        this.z = z;
        this.world = world;
    }

    public static AuditEntry block(String actor, @Nullable UUID uuid, String type, String message,
                                    int x, int y, int z, String world) {
        return new AuditEntry(System.currentTimeMillis(), actor, uuid, type, message, x, y, z, world);
    }

    public static AuditEntry generic(String actor, @Nullable UUID uuid, String type, String message) {
        return new AuditEntry(System.currentTimeMillis(), actor, uuid, type, message, -1, -1, -1, null);
    }
}
