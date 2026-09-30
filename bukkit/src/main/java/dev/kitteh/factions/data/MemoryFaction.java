package dev.kitteh.factions.data;

import dev.kitteh.factions.FLocation;
import dev.kitteh.factions.FPlayer;
import dev.kitteh.factions.FPlayers;
import dev.kitteh.factions.Faction;
import dev.kitteh.factions.Factions;
import dev.kitteh.factions.FactionsPlugin;
import dev.kitteh.factions.Participator;
import dev.kitteh.factions.Universe;
import dev.kitteh.factions.config.file.PermissionsConfig;
import dev.kitteh.factions.event.FactionAutoDisbandEvent;
import dev.kitteh.factions.event.FactionNewAdminEvent;
import dev.kitteh.factions.integration.Econ;
import dev.kitteh.factions.landraidcontrol.DTRControl;
import dev.kitteh.factions.landraidcontrol.LandRaidControl;
import dev.kitteh.factions.permissible.PermSelector;
import dev.kitteh.factions.permissible.PermState;
import dev.kitteh.factions.permissible.PermissibleAction;
import dev.kitteh.factions.permissible.PermissibleActions;
import dev.kitteh.factions.permissible.Relation;
import dev.kitteh.factions.permissible.Role;
import dev.kitteh.factions.permissible.Selectable;
import dev.kitteh.factions.plugin.AbstractFactionsPlugin;
import dev.kitteh.factions.plugin.Instances;
import dev.kitteh.factions.upgrade.Upgrade;
import dev.kitteh.factions.upgrade.UpgradeSettings;
import dev.kitteh.factions.upgrade.Upgrades;
import dev.kitteh.factions.util.BanInfo;
import dev.kitteh.factions.util.LazyLocation;
import dev.kitteh.factions.util.Mini;
import dev.kitteh.factions.util.TL;
import dev.kitteh.factions.util.TextUtil;
import dev.kitteh.factions.util.WorldTracker;
import dev.kitteh.factions.util.WorldUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApiStatus.Internal
@NullMarked
public abstract class MemoryFaction implements Faction {
    public static class Permissions implements Faction.Permissions {
        public static class SelectorPerms implements Faction.Permissions.SelectorPerms {
            private Map<String, Boolean> perms;

            public SelectorPerms() {
                this.perms = new HashMap<>();
            }

            public SelectorPerms(Map<String, Boolean> perms) {
                this.perms = new HashMap<>(perms);
            }

            public Map<String, Boolean> getPerms() {
                return this.perms;
            }

            @Override
            public PermState get(String action) {
                return PermState.of(this.perms.get(action.toUpperCase()));
            }

            @Override
            public void set(String action, PermState state) {
                action = action.toUpperCase();
                if (Objects.requireNonNull(state) == PermState.UNSET) {
                    this.perms.remove(action);
                } else {
                    this.perms.put(action, state == PermState.ALLOW);
                }
            }

            @Override
            public Collection<String> actions() {
                return this.perms.keySet().stream().sorted().toList();
            }
        }

        private List<PermSelector> selectorOrder = new ArrayList<>();
        private Map<PermSelector, SelectorPerms> perms = new HashMap<>();

        @Override
        public List<PermSelector> selectors() {
            return Collections.unmodifiableList(this.selectorOrder);
        }

        @Override
        public SelectorPerms get(PermSelector selector) {
            SelectorPerms perm = this.perms.get(selector);
            if (perm == null) {
                throw new IllegalArgumentException("Selector " + selector + " is not present");
            }
            return perm;
        }

        @Override
        public boolean has(PermSelector selector) {
            return this.selectorOrder.contains(selector);
        }

        @Override
        public SelectorPerms add(PermSelector selector) {
            if (!this.selectorOrder.contains(selector)) {
                this.selectorOrder.add(selector);
            }
            return this.perms.computeIfAbsent(selector, k -> new SelectorPerms());
        }

        @Override
        public void remove(PermSelector selector) {
            this.selectorOrder.remove(selector);
            this.perms.remove(selector);
        }

        @Override
        public void moveSelectorUp(PermSelector selector) {
            int index = this.selectorOrder.indexOf(selector);
            if (index <= 0) { // Not present or already top
                return;
            }
            this.selectorOrder.remove(index);
            index--;
            this.selectorOrder.add(index, selector);
        }

        @Override
        public void moveSelectorDown(PermSelector selector) {
            int index = this.selectorOrder.indexOf(selector);
            if (index == -1 || index == this.selectorOrder.size() - 1) { // Not present or already bottom
                return;
            }
            this.selectorOrder.remove(index);
            index++;
            this.selectorOrder.add(index, selector);
        }

        @Override
        public void clear() {
            this.perms.clear();
            this.selectorOrder.clear();
        }
    }

    protected static class Zone implements Faction.Zone {
        private int id;
        private String name;
        @Nullable
        private String greeting;
        @Nullable
        private transient Component greetingComponent;
        private Permissions perms = new Permissions();
        private transient MemoryFaction faction;

        private Zone(int id, String name, MemoryFaction faction) {
            this.id = id;
            this.name = name;
            this.faction = faction;
        }

        @Override
        public int id() {
            return this.id;
        }

        @Override
        public String name() {
            return this.name;
        }

        @Override
        public void name(String name) {
            this.name = Objects.requireNonNull(name);
        }

        @Override
        public Component greeting() {
            if (this.greetingComponent == null) {
                if (this.greeting == null) {
                    this.greetingComponent = this.faction.zones.main.greetingComponent;
                    if (this.greetingComponent == null) {
                        this.greetingComponent = Component.text("");
                    }
                } else {
                    this.greetingComponent = Mini.parseLimited(this.greeting, Placeholder.unparsed("tag", this.faction.tag));
                }
            }
            return this.greetingComponent;
        }

        @Override
        public @Nullable String greetingString() {
            return this.greeting;
        }

        @Override
        public void greeting(@Nullable String greeting) {
            if (this.id == 0) {
                this.greeting = Objects.requireNonNull(greeting);
            } else {
                this.greeting = greeting;
            }
        }

        @Override
        public Faction.Permissions permissions() {
            return this.id == 0 ? this.faction.perms : this.perms;
        }

        @Override
        public boolean canPlayerManage(FPlayer fPlayer) {
            //noinspection ConstantValue
            if (fPlayer == null) {
                return false; // Fail in a safe way because people are foolish
            }
            if (fPlayer.faction() == this.faction && (fPlayer.role() == Role.ADMIN)) {
                return true;
            }
            if (this.faction.hasOverrideAccess(fPlayer, PermissibleActions.ZONE) instanceof Boolean bool) {
                return bool;
            }
            if (this.faction.hasAccess(fPlayer, PermissibleActions.ZONE, this.perms) instanceof Boolean bool) {
                return bool;
            }
            return false;
        }
    }

    protected static class Zones implements Faction.Zones {
        private Object2ObjectOpenHashMap<String, WorldTracker> worldTrackers = new Object2ObjectOpenHashMap<>();
        private Int2ObjectOpenHashMap<Zone> zones = new Int2ObjectOpenHashMap<>();
        private int nextId = 1;
        private transient Zone main;
        private transient MemoryFaction faction;

        private Zones(MemoryFaction faction) {
            this.cleanupDeserialization(faction);
        }

        @SuppressWarnings("ConstantValue")
        private void cleanupDeserialization(MemoryFaction faction) {
            this.faction = faction;
            if (this.main == null) {
                this.main = this.zones.get(0);
                if (this.main == null) {
                    this.main = new Zone(0, "main", this.faction);
                    main.greeting = FactionsPlugin.instance().conf().factions().other().getDefaultGreeting();
                    this.zones.put(0, this.main);
                }
                this.zones.defaultReturnValue(this.main);
            }
            for (Zone zone : this.zones.values()) {
                zone.faction = faction;
            }
        }

        @Override
        public Zone main() {
            return this.main;
        }

        @Override
        public Zone create(String name) {
            if (this.get(name) != null) {
                throw new IllegalArgumentException("The name '" + name + "' already exists");
            }
            Zone zone = new Zone(this.nextId++, name, this.faction);
            this.zones.put(zone.id(), zone);
            return zone;
        }

        @Override
        public void delete(String name) {
            this.zones.values().stream().filter(zone -> zone.name().equals(name)).findFirst().ifPresent(zone -> this.zones.remove(zone.id));
        }

        @Override
        public Zone get(FLocation fLocation) {
            WorldTracker tracker = this.worldTrackers.get(fLocation.worldName());
            int id;
            //noinspection ConstantValue
            if (tracker == null || (id = tracker.idAt(fLocation)) == WorldTracker.NO_MATCH) {
                return this.main();
            }

            return this.zones.get(id);
        }

        @Override
        public Faction.@Nullable Zone get(String name) {
            return this.zones.values().stream().filter(zone -> zone.name().equals(name)).findFirst().orElse(null);
        }

        @Override
        public List<Faction.Zone> get() {
            List<Faction.Zone> zones = new ArrayList<>();
            this.zones.values().stream()
                    .sorted(Comparator.comparing(zone -> zone.id))
                    .forEach(zones::add);
            return Collections.unmodifiableList(zones);
        }

        @Override
        public void set(Faction.Zone zone, FLocation fLocation) {
            if (fLocation.faction() != this.faction) {
                throw new IllegalArgumentException("Cannot assign non-owned territory");
            }
            if (this.zones.get(zone.id()) != zone) {
                throw new IllegalArgumentException("Invalid zone for this faction");
            }
            if (zone == this.main) {
                WorldTracker tracker = this.worldTrackers.get(fLocation.worldName());
                //noinspection ConstantValue
                if (tracker != null) {
                    tracker.removeClaim(fLocation);
                    if (tracker.countClaims() == 0) {
                        this.worldTrackers.remove(fLocation.worldName());
                    }
                }
                return;
            }
            this.getAndCreate(fLocation.worldName()).addClaim(zone.id(), fLocation);
        }

        protected WorldTracker getAndCreate(String world) {
            return this.worldTrackers.computeIfAbsent(world, k -> new WorldTracker(world));
        }
    }

    protected int id;
    protected boolean peacefulExplosionsEnabled;
    protected boolean permanent;
    protected String tag;
    protected String description;
    protected @Nullable String link;
    protected boolean open;
    protected boolean peaceful;
    protected @Nullable Integer permanentPower;
    protected @Nullable LazyLocation home;
    protected long foundedDate;
    protected double powerBoost;
    protected ConcurrentHashMap<Integer, Relation> relationWish = new ConcurrentHashMap<>();
    protected Set<UUID> invites = new HashSet<>();
    // Legacy field — replaced by rosterRoles map. Kept for migration; cleaned in cleanupDeserialization.
    protected Set<UUID> roster = null;
    /** Roster: UUID → Role. Players on here can /f join without an invite and join with the assigned role. */
    protected HashMap<UUID, Role> rosterRoles = new HashMap<>();
    /** Trusted-owners list — a player on this list can /f owner to take leadership when the current leader is offline. */
    protected HashSet<UUID> owners = new HashSet<>();
    protected HashMap<UUID, List<String>> announcements = new HashMap<>();
    protected ConcurrentHashMap<String, LazyLocation> warps = new ConcurrentHashMap<>();
    protected ConcurrentHashMap<String, String> warpPasswords = new ConcurrentHashMap<>();
    // Per-warp role-name overrides for the warp GUI's right-click permissions feature.
    // Map<warpName, Set<roleName>>; absent or empty entry means "use default WARP perm".
    protected ConcurrentHashMap<String, HashSet<String>> warpAllowedRoles = new ConcurrentHashMap<>();
    protected long lastDeath;
    protected int maxVaults;
    protected Role defaultRole;
    protected @Nullable LinkedHashMap<PermSelector, Map<String, Boolean>> permissions; // legacy importing, lazier this way
    protected Set<BanInfo> bans = new HashSet<>();
    protected double dtr;
    protected long lastDTRUpdateTime;
    protected long frozenDTRUntilTime;
    protected int tntBank;
    protected long shieldEnd;
    protected long shieldCooldownEnd;
    protected Object2IntOpenHashMap<String> upgrades = new Object2IntOpenHashMap<>();
    protected MemoryFaction.Permissions perms = new MemoryFaction.Permissions();
    protected Zones zones = new Zones(this);

    // Faction Levels
    protected long factionXp = 0L;
    protected int factionLevel = 0;
    protected int factionPoints = 0;

    // Custom upgrade tiers (pv, warps, warp_time, hh_xp_boost, tnt_bank, banner)
    protected HashMap<String, Integer> customUpgradeTiers = new HashMap<>();

    // Faction PV contents — base64-encoded serialized ItemStacks, null entries = empty slot
    protected String[] pvContents = new String[0];

    // Faction Effects
    protected long effectCooldownEnd = 0L;
    // Inventory of purchased / granted effects. Key format: "EFFECT_NAME:DURATION_SECONDS", value: remaining uses.
    protected HashMap<String, Integer> effectInventory = new HashMap<>();

    // Faction booster inventory: "TYPE:MULT_X100:MINUTES" -> count
    protected HashMap<String, Integer> boosterInventory = new HashMap<>();

    // Faction Missions: missionId -> progress value
    protected HashMap<String, Long> missionProgress = new HashMap<>();
    // category -> last reset epoch millis
    protected HashMap<String, Long> missionLastReset = new HashMap<>();
    // category -> active mission ids picked from the pool for this window
    protected HashMap<String, java.util.ArrayList<String>> missionActiveIds = new HashMap<>();

    // Audit logs
    protected java.util.ArrayList<dev.kitteh.factions.audit.AuditEntry> factionLog = new java.util.ArrayList<>();
    protected java.util.ArrayList<dev.kitteh.factions.audit.AuditEntry> blockLog = new java.util.ArrayList<>();

    // Per-member bank contribution leaderboards (lifetime totals, never decay)
    protected HashMap<UUID, Double> bankDeposits = new HashMap<>();
    protected HashMap<UUID, Double> bankWithdraws = new HashMap<>();

    protected transient long lastPlayerLoggedOffTime;
    protected transient Set<FPlayer> fplayers = new HashSet<>();
    protected transient @Nullable OfflinePlayer offlinePlayer;
    private transient boolean removed = false;

    @SuppressWarnings("ConstantValue")
    public void cleanupDeserialization() {
        this.fplayers = new HashSet<>();
        this.offlinePlayer = null;
        this.asOfflinePlayer();
        if (this.upgrades == null) {
            this.upgrades = new Object2IntOpenHashMap<>();
        }
        if (this.permissions != null) {
            this.perms = new MemoryFaction.Permissions();
            for (Map.Entry<PermSelector, Map<String, Boolean>> entry : this.permissions.entrySet()) {
                this.perms.selectorOrder.add(entry.getKey());
                this.perms.perms.put(entry.getKey(), new Permissions.SelectorPerms(entry.getValue()));
            }
            this.permissions = null;
        }
        if (this.perms == null || !this.isNormal()) {
            this.resetPerms();
        }
        if (this.zones == null) {
            this.zones = new Zones(this);
        }
        this.zones.cleanupDeserialization(this); // Sets the transient helper main value.
        if (this.missionProgress == null) this.missionProgress = new HashMap<>();
        if (this.missionLastReset == null) this.missionLastReset = new HashMap<>();
        if (this.missionActiveIds == null) this.missionActiveIds = new HashMap<>();
        if (this.customUpgradeTiers == null) this.customUpgradeTiers = new HashMap<>();
        if (this.factionLog == null) this.factionLog = new java.util.ArrayList<>();
        if (this.blockLog == null) this.blockLog = new java.util.ArrayList<>();
        if (this.bankDeposits == null) this.bankDeposits = new HashMap<>();
        if (this.bankWithdraws == null) this.bankWithdraws = new HashMap<>();
        if (this.warpAllowedRoles == null) this.warpAllowedRoles = new ConcurrentHashMap<>();
        if (this.effectInventory == null) this.effectInventory = new HashMap<>();
        if (this.boosterInventory == null) this.boosterInventory = new HashMap<>();
        if (this.rosterRoles == null) this.rosterRoles = new HashMap<>();
        if (this.owners == null) this.owners = new HashSet<>();
        // Migrate legacy roster (Set of UUID) -> rosterRoles (UUID -> Role) defaulting to NORMAL
        if (this.roster != null) {
            for (UUID id : this.roster) {
                if (!this.rosterRoles.containsKey(id)) {
                    this.rosterRoles.put(id, Role.NORMAL);
                }
            }
            this.roster = null;
        }
    }

    @Override
    public void addAnnouncement(FPlayer fPlayer, String msg) {
        announcements.computeIfAbsent(fPlayer.uniqueId(), k -> new ArrayList<>()).add(msg);
    }

    @Override
    public void sendUnreadAnnouncements(FPlayer fPlayer) {
        List<String> ann = announcements.remove(fPlayer.uniqueId());
        if (ann == null) {
            return;
        }
        fPlayer.msgLegacy(TL.FACTIONS_ANNOUNCEMENT_TOP);
        for (String s : ann) {
            fPlayer.sendMessageLegacy(s);
        }
        fPlayer.msgLegacy(TL.FACTIONS_ANNOUNCEMENT_BOTTOM);
    }

    @Override
    public ConcurrentHashMap<String, LazyLocation> warps() {
        return this.warps;
    }

    @Override
    public LazyLocation warp(String name) {
        return this.warps.get(name);
    }

    @Override
    public void createWarp(String name, LazyLocation loc) {
        this.warps.put(name, loc);
    }

    @Override
    public boolean isWarp(String name) {
        return this.warps.containsKey(name);
    }

    @Override
    public boolean removeWarp(String name) {
        warpPasswords.remove(name); // remove password no matter what.
        if (this.warpAllowedRoles != null) this.warpAllowedRoles.remove(name.toLowerCase());
        return warps.remove(name) != null;
    }

    @Override
    public java.util.Set<String> warpAllowedRoles(String warpName) {
        if (this.warpAllowedRoles == null) return java.util.Set.of();
        HashSet<String> set = this.warpAllowedRoles.get(warpName.toLowerCase());
        return set == null ? java.util.Set.of() : java.util.Collections.unmodifiableSet(set);
    }

    @Override
    public void warpAllowedRoles(String warpName, java.util.Set<String> roleNames) {
        if (this.warpAllowedRoles == null) this.warpAllowedRoles = new ConcurrentHashMap<>();
        if (roleNames == null || roleNames.isEmpty()) {
            this.warpAllowedRoles.remove(warpName.toLowerCase());
        } else {
            // store lowercase for case-insensitive comparison
            HashSet<String> normalized = new HashSet<>();
            for (String r : roleNames) normalized.add(r.toLowerCase());
            this.warpAllowedRoles.put(warpName.toLowerCase(), normalized);
        }
    }

    @Override
    public boolean isWarpPassword(String warp, String password) {
        return hasWarpPassword(warp) && warpPasswords.get(warp.toLowerCase()).equals(password);
    }

    @Override
    public boolean hasWarpPassword(String warp) {
        return warpPasswords.containsKey(warp.toLowerCase());
    }

    @Override
    public void setWarpPassword(String warp, String password) {
        warpPasswords.put(warp.toLowerCase(), password);
    }

    @Override
    public void removeWarpPassword(String warp) {
        warpPasswords.remove(warp.toLowerCase());
    }

    @Override
    public void clearWarps() {
        warps.clear();
    }

    @Override
    public int maxVaults() {
        return this.maxVaults;
    }

    @Override
    public void maxVaults(int value) {
        this.maxVaults = value;
    }

    @Override
    public Set<UUID> invites() {
        return invites;
    }

    @Override
    public java.util.Map<UUID, Role> rosterRoles() {
        if (this.rosterRoles == null) this.rosterRoles = new HashMap<>();
        return java.util.Collections.unmodifiableMap(this.rosterRoles);
    }

    @Override
    public boolean rosterAdd(UUID playerId, Role role) {
        if (this.rosterRoles == null) this.rosterRoles = new HashMap<>();
        if (role == null || role == Role.ADMIN) return false; // Leader cannot be pre-rostered
        int max = FactionsPlugin.instance().configManager().factionLevelsConfig().getRosterMaxSize();
        if (this.rosterRoles.size() >= max && !this.rosterRoles.containsKey(playerId)) return false;
        this.rosterRoles.put(playerId, role);
        return true;
    }

    @Override
    public boolean rosterSetRole(UUID playerId, Role role) {
        if (this.rosterRoles == null) return false;
        if (role == null || role == Role.ADMIN) return false;
        if (!this.rosterRoles.containsKey(playerId)) return false;
        this.rosterRoles.put(playerId, role);
        return true;
    }

    @Override
    public Role rosterRoleOf(UUID playerId) {
        if (this.rosterRoles == null) return null;
        return this.rosterRoles.get(playerId);
    }

    @Override
    public boolean rosterRemove(UUID playerId) {
        if (this.rosterRoles == null) return false;
        return this.rosterRoles.remove(playerId) != null;
    }

    @Override
    public boolean isOnRoster(UUID playerId) {
        return this.rosterRoles != null && this.rosterRoles.containsKey(playerId);
    }

    @Override
    public Set<UUID> owners() {
        if (this.owners == null) this.owners = new HashSet<>();
        return java.util.Collections.unmodifiableSet(this.owners);
    }

    @Override
    public boolean ownersAdd(UUID playerId) {
        if (this.owners == null) this.owners = new HashSet<>();
        return this.owners.add(playerId);
    }

    @Override
    public boolean ownersRemove(UUID playerId) {
        if (this.owners == null) return false;
        return this.owners.remove(playerId);
    }

    @Override
    public boolean isOwner(UUID playerId) {
        return this.owners != null && this.owners.contains(playerId);
    }

    @Override
    public int id() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
        this.offlinePlayer = null;
    }

    @Override
    public void invite(FPlayer fplayer) {
        this.invites.add(fplayer.uniqueId());
    }

    @Override
    public void deInvite(FPlayer fplayer) {
        this.invites.remove(fplayer.uniqueId());
    }

    @Override
    public boolean hasInvite(FPlayer fplayer) {
        return this.invites.contains(fplayer.uniqueId());
    }

    @Override
    public void ban(FPlayer target, FPlayer banner) {
        BanInfo info = new BanInfo(banner.uniqueId(), target.uniqueId(), System.currentTimeMillis());
        this.bans.add(info);
    }

    @Override
    public void unban(FPlayer player) {
        bans.removeIf(banInfo -> banInfo.banned().equals(player.uniqueId()));
    }

    @Override
    public boolean isBanned(FPlayer player) {
        for (BanInfo info : bans) {
            if (info.banned().equals(player.uniqueId())) {
                return true;
            }
        }

        return false;
    }

    @Override
    public Set<BanInfo> bans() {
        return this.bans;
    }

    @Override
    public boolean open() {
        return open;
    }

    @Override
    public void open(boolean isOpen) {
        open = isOpen;
    }

    @Override
    public boolean isPeaceful() {
        return this.peaceful;
    }

    @Override
    public void peaceful(boolean isPeaceful) {
        this.peaceful = isPeaceful;
    }

    @Override
    public void peacefulExplosionsEnabled(boolean val) {
        peacefulExplosionsEnabled = val;
    }

    @Override
    public boolean peacefulExplosionsEnabled() {
        return this.peacefulExplosionsEnabled;
    }

    @Override
    public boolean isPermanent() {
        return permanent || !this.isNormal();
    }

    @Override
    public void permanent(boolean isPermanent) {
        permanent = isPermanent;
    }

    @Override
    public String tag() {
        return this.tag;
    }

    @Override
    public String tagLegacy(@Nullable Participator other) {
        if (other == null) {
            return tag();
        }
        return this.colorLegacyStringTo(other) + this.tag();
    }

    @Override
    public void tag(String str) {
        if (FactionsPlugin.instance().conf().factions().other().isTagForceUpperCase()) {
            str = str.toUpperCase();
        }

        // Wipe processed greetings for tag placeholder
        this.zones.zones.values().forEach(zone -> zone.greetingComponent = null);
        this.tag = str;
    }

    @Override
    public String description() {
        return this.description;
    }

    @Override
    public void description(String value) {
        this.description = value;
    }

    @Override
    public String link() {
        if (this.link == null) {
            this.link = FactionsPlugin.instance().conf().commands().link().getDefaultURL();
        }
        return this.link;
    }

    @Override
    public void link(String value) {
        this.link = value;
    }

    @Override
    public void home(Location home) {
        this.home = new LazyLocation(home);
    }

    @Override
    public void removeHome() {
        this.home = null;
    }

    @Override
    public @Nullable Location home() {
        confirmValidHome();
        return (this.home != null) ? this.home.asLocation() : null;
    }

    public void confirmValidHome() {
        if ((!FactionsPlugin.instance().conf().factions().homes().isMustBeInClaimedTerritory()) || (this.home == null) || (new FLocation(this.home).faction() == this)) {
            return;
        }

        msgLegacy(TL.FACTION_HOME_UNSET);
        this.home = null;
    }

    @Override
    public Instant founded() {
        if (this.foundedDate == 0) {
            founded(Instant.now());
        }
        return Instant.ofEpochMilli(this.foundedDate);
    }

    @Override
    public void founded(Instant when) {
        this.foundedDate = when.toEpochMilli();
    }

    @Override
    public OfflinePlayer asOfflinePlayer() {
        if (this.offlinePlayer == null) {
            this.offlinePlayer = AbstractFactionsPlugin.instance().factionOfflinePlayer("faction-" + this.id);
        }
        return this.offlinePlayer;
    }

    @Override
    public void lastDeath(Instant time) {
        this.lastDeath = time.toEpochMilli();
    }

    @Override
    public Instant lastDeath() {
        return Instant.ofEpochMilli(this.lastDeath);
    }

    @Override
    public int kills() {
        return members().stream().mapToInt(FPlayer::kills).sum();
    }

    @Override
    public int deaths() {
        int deaths = 0;
        for (FPlayer fp : members()) {
            deaths += fp.deaths();
        }

        return deaths;
    }

    @Override
    public boolean hasAccess(Selectable selectable, PermissibleAction permissibleAction, @Nullable FLocation location) {
        //noinspection ConstantValue
        if (selectable == null || permissibleAction == null) {
            return false; // Fail in a safe way because people are foolish
        }

        if (permissibleAction.prerequisite() instanceof Upgrade prereq && (!Universe.universe().isUpgradeEnabled(prereq) || this.upgradeLevel(prereq) == 0)) {
            return false;
        }

        if (selectable == Role.ADMIN || (selectable instanceof FPlayer fp && fp.faction() == this && fp.role() == Role.ADMIN)) {
            return true;
        }

        if (this.hasOverrideAccess(selectable, permissibleAction) instanceof Boolean bool) {
            return bool;
        }

        if (location != null) {
            Zone zone = this.zones.get(location);
            if (zone.id != 0 && this.hasAccess(selectable, permissibleAction, zone.perms) instanceof Boolean bool) {
                return bool;
            }
        }

        if (this.hasAccess(selectable, permissibleAction, this.perms) instanceof Boolean bool) {
            return bool;
        }

        return false;
    }

    private @Nullable Boolean hasOverrideAccess(Selectable selectable, PermissibleAction permissibleAction) {
        PermissionsConfig permConf = FactionsPlugin.instance().configManager().permissionsConfig();
        List<PermSelector> priority = permConf.getOverridePermissionsOrder().stream().filter(s -> s.test(selectable, this)).toList();
        for (PermSelector selector : priority) {
            Boolean bool = permConf.getOverridePermissions().get(selector).get(permissibleAction.name());
            if (bool != null) {
                return bool;
            }
        }
        return null;
    }

    private @Nullable Boolean hasAccess(Selectable selectable, PermissibleAction permissibleAction, Permissions permissions) {
        for (PermSelector selector : permissions.selectorOrder) {
            if (selector.test(selectable, this)) {
                Permissions.SelectorPerms perm = permissions.perms.get(selector);
                if (perm.perms.get(permissibleAction.name()) instanceof Boolean bool) {
                    return bool;
                }
            }
        }
        return null;
    }

    @Override
    public Permissions permissions() {
        return this.perms;
    }

    public void resetPerms() {
        this.perms.clear();

        if (!this.isNormal()) {
            return;
        }

        PermissionsConfig permConf = FactionsPlugin.instance().configManager().permissionsConfig();
        for (PermSelector selector : permConf.getDefaultPermissionsOrder()) {
            this.perms.selectorOrder.add(selector);
            this.perms.perms.put(selector, new Permissions.SelectorPerms(new HashMap<>(permConf.getDefaultPermissions().get(selector))));
        }
    }

    @Override
    public Role defaultRole() {
        return this.defaultRole;
    }

    @Override
    public void defaultRole(Role role) {
        this.defaultRole = role;
    }

    public MemoryFaction(int id, String tag) {
        this.id = id;
        this.open = FactionsPlugin.instance().conf().factions().other().isNewFactionsDefaultOpen();
        this.tag = tag;
        this.description = TL.GENERIC_DEFAULTDESCRIPTION.toString();
        this.lastPlayerLoggedOffTime = 0;
        this.peaceful = FactionsPlugin.instance().conf().factions().other().isNewFactionsDefaultPeaceful();
        this.peacefulExplosionsEnabled = false;
        this.permanent = false;
        this.powerBoost = 0.0;
        this.foundedDate = System.currentTimeMillis();
        this.maxVaults = FactionsPlugin.instance().conf().plugins().playerVaults().getDefaultMaxVaults();
        this.defaultRole = FactionsPlugin.instance().conf().factions().other().getDefaultRole();
        this.dtr = FactionsPlugin.instance().conf().factions().landRaidControl().dtr().getStartingDTR();
        this.resetPerms();
    }

    @Override
    public Relation relationWish(Faction otherFaction) {
        if (this.relationWish.containsKey(otherFaction.id())) {
            return this.relationWish.get(otherFaction.id());
        }
        return FactionsPlugin.instance().conf().factions().other().getDefaultRelation();
    }

    @Override
    public void relationWish(Faction otherFaction, Relation relation) {
        if (this.relationWish.containsKey(otherFaction.id()) && relation.equals(Relation.NEUTRAL)) {
            this.relationWish.remove(otherFaction.id());
        } else {
            this.relationWish.put(otherFaction.id(), relation);
        }
    }

    @Override
    public double dtr() {
        LandRaidControl lrc = FactionsPlugin.instance().landRaidControl();
        if (lrc instanceof DTRControl dtrControl) {
            dtrControl.updateDTR(this);
        }
        return this.dtr;
    }

    @Override
    public double dtrWithoutUpdate() {
        return this.dtr;
    }

    @Override
    public void dtr(double dtr) {
        double start = this.dtr;
        this.dtr = dtr;
        this.lastDTRUpdateTime = System.currentTimeMillis();
        if (start != this.dtr && FactionsPlugin.instance().landRaidControl() instanceof DTRControl dtrControl) {
            dtrControl.onDTRChange(this, start, this.dtr);
        }
    }

    @Override
    public long dtrLastUpdated() {
        return this.lastDTRUpdateTime;
    }

    @Override
    public long dtrFrozenUntil() {
        return this.frozenDTRUntilTime;
    }

    @Override
    public void dtrFrozenUntil(long time) {
        this.frozenDTRUntilTime = time;
    }

    @Override
    public boolean dtrFrozen() {
        return System.currentTimeMillis() < this.frozenDTRUntilTime;
    }

    @Override
    public double powerExact() {
        if (this.permanentPower != null) {
            return this.permanentPower;
        }

        double ret = 0;
        for (FPlayer fplayer : fplayers) {
            ret += fplayer.power();
        }
        if (FactionsPlugin.instance().conf().factions().landRaidControl().power().getFactionMax() > 0 && ret > FactionsPlugin.instance().conf().factions().landRaidControl().power().getFactionMax()) {
            ret = FactionsPlugin.instance().conf().factions().landRaidControl().power().getFactionMax();
        }
        return ret + this.powerBoost;
    }

    @Override
    public double powerMaxExact() {
        if (this.permanentPower != null) {
            return this.permanentPower;
        }

        double ret = 0;
        for (FPlayer fplayer : fplayers) {
            ret += fplayer.powerMax();
        }
        double confMax = FactionsPlugin.instance().conf().factions().landRaidControl().power().getFactionMax();
        if (confMax > 0) {
            double limit = confMax;
            int lvl = this.upgradeLevel(Upgrades.POWER_MAX);
            if (lvl > 0) {
                limit += Universe.universe().upgradeSettings(Upgrades.POWER_MAX).valueAt(Upgrades.Variables.POSITIVE_INCREASE, lvl).doubleValue();
            }
            ret = Math.min(ret, limit);
        }
        return ret + this.powerBoost;
    }

    @Override
    public @Nullable Integer permanentPower() {
        return this.permanentPower;
    }

    @Override
    public void permanentPower(@Nullable Integer permanentPower) {
        this.permanentPower = permanentPower;
    }

    @Override
    public double powerBoost() {
        return this.powerBoost;
    }

    @Override
    public void powerBoost(double powerBoost) {
        this.powerBoost = powerBoost;
    }

    @Override
    public boolean isPowerFrozen() {
        int freezeSeconds = FactionsPlugin.instance().conf().factions().landRaidControl().power().getPowerFreeze();
        return freezeSeconds != 0 && System.currentTimeMillis() - lastDeath < freezeSeconds * 1000L;
    }

    @Override
    public int tntBank() {
        return this.tntBank;
    }

    @Override
    public int tntBankMax() {
        var cfg = FactionsPlugin.instance().configManager().factionLevelsConfig();
        // CmdUpgrades.KEY_TNT_BANK — string is duplicated to avoid a command-package dependency
        int tier = this.customUpgradeTier("tnt_bank");
        int upgradeMax = cfg.tntBankCapacityForTier(tier);

        int configMax = FactionsPlugin.instance().conf().commands().tnt().getMaxStorage();
        if (configMax < 0) return upgradeMax;
        return Math.min(configMax, upgradeMax);
    }

    @Override
    public void tntBank(int amount) {
        this.tntBank = amount;
    }

    @Override
    public Duration shieldCooldownRemaining() {
        long currentTime = System.currentTimeMillis();
        if (this.shieldCooldownEnd < currentTime) {
            this.shieldCooldownEnd = 0L;
        }
        return this.shieldCooldownEnd == 0L ? Duration.ZERO : Duration.ofMillis(this.shieldCooldownEnd - currentTime);
    }

    @Override
    public Duration shieldRemaining() {
        long currentTime = System.currentTimeMillis();
        if (this.shieldEnd < currentTime) {
            this.shieldEnd = 0L;
        }
        return this.shieldEnd == 0L ? Duration.ZERO : Duration.ofMillis(this.shieldEnd - currentTime);
    }

    @Override
    public void shield(Duration duration, Duration cooldown) {
        this.shieldEnd = System.currentTimeMillis() + duration.toMillis();
        this.shieldCooldownEnd = this.shieldEnd + cooldown.toMillis();
    }

    @Override
    public int upgradeLevel(Upgrade upgrade) {
        if (!this.isNormal() || !Universe.universe().isUpgradeEnabled(upgrade)) {
            return 0;
        }
        UpgradeSettings settings = Universe.universe().upgradeSettings(upgrade);
        return Math.min(settings.maxLevel(), this.upgrades.getOrDefault(upgrade.name(), settings.startingLevel()));
    }

    @Override
    public void upgradeLevel(Upgrade upgrade, int level) {
        int newLevel = Math.min(upgrade.maxLevel(), level);
        int oldLevel = this.upgradeLevel(upgrade);
        this.upgrades.put(upgrade.name(), newLevel);
        this.sendRichMessage("<green>Upgraded <upgrade> to level " + newLevel + "!", Placeholder.component("upgrade", upgrade.nameComponent()));
        upgrade.onChange(this, oldLevel, newLevel);
    }

    public void addMember(FPlayer fPlayer) {
        if (this.isNormal()) {
            fplayers.add(fPlayer);
        }
    }

    public void removeMember(FPlayer fPlayer) {
        this.announcements.remove(fPlayer.uniqueId());
        this.fplayers.remove(fPlayer);
    }

    @Override
    public int size() {
        return fplayers.size();
    }

    @Override
    public Set<FPlayer> members() {
        return Set.copyOf(fplayers);
    }

    @Override
    public Set<FPlayer> membersOnline(boolean online) {
        Set<FPlayer> ret = new HashSet<>();
        if (!this.isNormal()) {
            return ret;
        }

        for (FPlayer fplayer : fplayers) {
            if (fplayer.isOnline() == online) {
                ret.add(fplayer);
            }
        }

        return ret;
    }

    @Override
    public Set<FPlayer> membersOnline(boolean online, @Nullable FPlayer viewer) {
        if (viewer == null) {
            return membersOnline(online);
        }
        Set<FPlayer> ret = new HashSet<>();
        if (!this.isNormal()) {
            return ret;
        }

        Player viewerPlayer = viewer.asPlayer();

        for (FPlayer viewed : fplayers) {
            // Add if their online status is what we want
            boolean on = viewed.asPlayer() instanceof Player viewedPlayer && WorldUtil.isEnabled(viewedPlayer) && (viewerPlayer == null || viewerPlayer.canSee(viewedPlayer));
            if (on == online) {
                ret.add(viewed);
            }
        }

        return ret;
    }

    @Override
    public @Nullable FPlayer admin() {
        if (!this.isNormal()) {
            return null;
        }

        for (FPlayer fplayer : fplayers) {
            if (fplayer.role() == Role.ADMIN) {
                return fplayer;
            }
        }
        return null;
    }

    @Override
    public ArrayList<FPlayer> members(Role role) {
        ArrayList<FPlayer> ret = new ArrayList<>();
        if (!this.isNormal()) {
            return ret;
        }

        for (FPlayer fplayer : fplayers) {
            if (fplayer.role() == role) {
                ret.add(fplayer);
            }
        }

        return ret;
    }

    @Override
    public ArrayList<Player> membersOnlineAsPlayers() {
        ArrayList<Player> ret = new ArrayList<>();
        if (this.fplayers.isEmpty()) {
            return ret;
        }

        for (Player player : AbstractFactionsPlugin.instance().getServer().getOnlinePlayers()) {
            FPlayer fplayer = FPlayers.fPlayers().get(player);
            if (fplayer.faction() == this) {
                ret.add(player);
            }
        }

        return ret;
    }

    // slightly faster check than getOnlinePlayers() if you just want to see if
    // there are any players online
    @Override
    public boolean hasMembersOnline() {
        // only real factions can have players online, not safe zone / war zone
        if (this.fplayers.isEmpty()) {
            return false;
        }

        for (Player player : AbstractFactionsPlugin.instance().getServer().getOnlinePlayers()) {
            FPlayer fplayer = FPlayers.fPlayers().get(player);
            if (fplayer.faction() == this) {
                return true;
            }
        }

        // even if all players are technically logged off, maybe someone was on
        // recently enough to not consider them officially offline yet
        return FactionsPlugin.instance().conf().factions().other().getConsiderFactionsReallyOfflineAfterXMinutes() > 0 && System.currentTimeMillis() < lastPlayerLoggedOffTime + (FactionsPlugin.instance().conf().factions().other().getConsiderFactionsReallyOfflineAfterXMinutes() * 60000);
    }

    @Override
    public void trackMemberLoggedOff() {
        if (this.isNormal()) {
            lastPlayerLoggedOffTime = System.currentTimeMillis();
        }
    }

    // used when current leader is about to be removed from the faction;
    // promotes new leader, or disbands faction if no other members left
    @Override
    public void promoteNewLeader() {
        if (!this.isNormal()) {
            return;
        }
        if (this.isPermanent() && FactionsPlugin.instance().conf().factions().specialCase().isPermanentFactionsDisableLeaderPromotion()) {
            return;
        }

        FPlayer oldLeader = this.admin();

        // get list of coleaders, or mods, or list of normal members if there are no moderators
        ArrayList<FPlayer> replacements = this.members(Role.COLEADER);
        if (replacements.isEmpty()) {
            replacements = this.members(Role.MODERATOR);
        }

        if (replacements.isEmpty()) {
            replacements = this.members(Role.NORMAL);
        }

        if (replacements.isEmpty()) { // faction admin  is the only  member; one-man  faction
            if (this.isPermanent()) {
                if (oldLeader != null) {
                    oldLeader.role(Role.NORMAL);
                }
                return;
            }

            // no members left and faction isn't permanent, so disband it
            if (FactionsPlugin.instance().conf().logging().isFactionDisband()) {
                AbstractFactionsPlugin.instance().log("The faction " + this.tag() + " (" + this.id() + ") has been disbanded since it has no members left.");
            }

            for (FPlayer fplayer : FPlayers.fPlayers().online()) {
                fplayer.msgLegacy(TL.LEAVE_DISBANDED, this.tagLegacy(fplayer));
            }

            AbstractFactionsPlugin.instance().getServer().getPluginManager().callEvent(new FactionAutoDisbandEvent(this));

            Factions.factions().remove(this);
        } else { // promote new faction admin
            Bukkit.getServer().getPluginManager().callEvent(new FactionNewAdminEvent(replacements.getFirst(), this));

            if (oldLeader != null) {
                oldLeader.role(Role.COLEADER);
            }
            replacements.getFirst().role(Role.ADMIN);
            this.msgLegacy(TL.FACTION_NEWLEADER, oldLeader == null ? "" : oldLeader.name(), replacements.getFirst().name());
            AbstractFactionsPlugin.instance().log("Faction " + this.tag() + " (" + this.id() + ") admin was removed. Replacement admin: " + replacements.getFirst().name());
        }
    }

    @Override
    public void msgLegacy(String message, Object... args) {
        message = TextUtil.parse(message, args);

        for (FPlayer fplayer : this.membersOnline(true)) {
            fplayer.sendMessageLegacy(message);
        }
    }

    // ── Faction Levels ──────────────────────────────────────────────────────

    @Override
    public long factionXp() {
        return this.factionXp;
    }

    @Override
    public void factionXp(long xp) {
        this.factionXp = xp;
    }

    @Override
    public int factionLevel() {
        return this.factionLevel;
    }

    @Override
    public void factionLevel(int level) {
        this.factionLevel = level;
    }

    @Override
    public int factionPoints() {
        return this.factionPoints;
    }

    @Override
    public void factionPoints(int points) {
        this.factionPoints = Math.max(0, points);
    }

    @Override
    public void addFactionPoints(int amount) {
        this.factionPoints += amount;
    }

    @Override
    public boolean spendFactionPoints(int amount) {
        if (this.factionPoints < amount) return false;
        this.factionPoints -= amount;
        return true;
    }

    // ── Custom Upgrade Tiers ────────────────────────────────────────────────

    @Override
    public int customUpgradeTier(String upgradeKey) {
        if (this.customUpgradeTiers == null) return 0;
        return this.customUpgradeTiers.getOrDefault(upgradeKey, 0);
    }

    @Override
    public void customUpgradeTier(String upgradeKey, int tier) {
        if (this.customUpgradeTiers == null) this.customUpgradeTiers = new HashMap<>();
        this.customUpgradeTiers.put(upgradeKey, tier);
    }

    // ── Faction PV ─────────────────────────────────────────────────────────

    @Override
    public String[] pvContents() {
        if (this.pvContents == null) return new String[0];
        return this.pvContents;
    }

    @Override
    public void pvContents(String[] contents) {
        this.pvContents = contents;
    }

    // ── Faction Effects ─────────────────────────────────────────────────────

    @Override
    public long effectCooldownEnd() {
        return this.effectCooldownEnd;
    }

    @Override
    public void effectCooldownEnd(long epochMillis) {
        this.effectCooldownEnd = epochMillis;
    }

    @Override
    public java.util.Map<String, Integer> effectInventory() {
        if (this.effectInventory == null) return java.util.Map.of();
        return java.util.Collections.unmodifiableMap(this.effectInventory);
    }

    @Override
    public void addEffectToInventory(String effectName, int durationSeconds, int count) {
        if (count <= 0) return;
        if (this.effectInventory == null) this.effectInventory = new HashMap<>();
        String key = effectName.toUpperCase() + ":" + durationSeconds;
        this.effectInventory.merge(key, count, Integer::sum);
    }

    @Override
    public boolean consumeEffectFromInventory(String effectName, int durationSeconds) {
        if (this.effectInventory == null) return false;
        String key = effectName.toUpperCase() + ":" + durationSeconds;
        Integer remaining = this.effectInventory.get(key);
        if (remaining == null || remaining <= 0) return false;
        if (remaining == 1) {
            this.effectInventory.remove(key);
        } else {
            this.effectInventory.put(key, remaining - 1);
        }
        return true;
    }

    @Override
    public java.util.Map<String, Integer> boosterInventory() {
        if (this.boosterInventory == null) return java.util.Map.of();
        return java.util.Collections.unmodifiableMap(this.boosterInventory);
    }

    @Override
    public void addBoosterToInventory(String boosterType, int multiplierX100, int minutes, int count) {
        if (count <= 0) return;
        if (this.boosterInventory == null) this.boosterInventory = new HashMap<>();
        String key = boosterType.toUpperCase() + ":" + multiplierX100 + ":" + minutes;
        this.boosterInventory.merge(key, count, Integer::sum);
    }

    @Override
    public boolean consumeBoosterFromInventory(String boosterType, int multiplierX100, int minutes) {
        if (this.boosterInventory == null) return false;
        String key = boosterType.toUpperCase() + ":" + multiplierX100 + ":" + minutes;
        Integer remaining = this.boosterInventory.get(key);
        if (remaining == null || remaining <= 0) return false;
        if (remaining == 1) {
            this.boosterInventory.remove(key);
        } else {
            this.boosterInventory.put(key, remaining - 1);
        }
        return true;
    }

    // ── Faction Missions ────────────────────────────────────────────────────

    @Override
    public long missionProgress(String missionId) {
        if (this.missionProgress == null) return 0L;
        return this.missionProgress.getOrDefault(missionId, 0L);
    }

    @Override
    public void missionProgress(String missionId, long value) {
        if (this.missionProgress == null) this.missionProgress = new HashMap<>();
        this.missionProgress.put(missionId, value);
    }

    @Override
    public long missionLastReset(String category) {
        if (this.missionLastReset == null) return 0L;
        return this.missionLastReset.getOrDefault(category, 0L);
    }

    @Override
    public void missionLastReset(String category, long epochMillis) {
        if (this.missionLastReset == null) this.missionLastReset = new HashMap<>();
        this.missionLastReset.put(category, epochMillis);
    }

    @Override
    public java.util.List<String> missionActiveIds(String category) {
        if (this.missionActiveIds == null) return java.util.Collections.emptyList();
        java.util.ArrayList<String> ids = this.missionActiveIds.get(category);
        return ids == null ? java.util.Collections.emptyList() : new java.util.ArrayList<>(ids);
    }

    @Override
    public void missionActiveIds(String category, java.util.List<String> ids) {
        if (this.missionActiveIds == null) this.missionActiveIds = new HashMap<>();
        this.missionActiveIds.put(category, new java.util.ArrayList<>(ids));
    }

    @Override
    public void recordFactionLog(dev.kitteh.factions.audit.AuditEntry entry) {
        if (this.factionLog == null) this.factionLog = new java.util.ArrayList<>();
        this.factionLog.add(entry);
        int days = FactionsPlugin.instance().configManager().factionLevelsConfig().getFactionLogRetentionDays();
        if (days > 0) {
            evictOlderThan(this.factionLog, System.currentTimeMillis() - (long) days * 86_400_000L);
        }
        while (this.factionLog.size() > 5000) {
            this.factionLog.remove(0);
        }
    }

    @Override
    public java.util.List<dev.kitteh.factions.audit.AuditEntry> factionLog() {
        if (this.factionLog == null) return java.util.List.of();
        return java.util.Collections.unmodifiableList(this.factionLog);
    }

    @Override
    public void clearFactionLog() {
        if (this.factionLog != null) this.factionLog.clear();
    }

    @Override
    public void recordBlockLog(dev.kitteh.factions.audit.AuditEntry entry) {
        if (this.blockLog == null) this.blockLog = new java.util.ArrayList<>();
        this.blockLog.add(entry);
        int hours = FactionsPlugin.instance().configManager().factionLevelsConfig().getBlockLogRetentionHours();
        if (hours > 0) {
            evictOlderThan(this.blockLog, System.currentTimeMillis() - (long) hours * 3_600_000L);
        }
        while (this.blockLog.size() > 20000) {
            this.blockLog.remove(0);
        }
    }

    /**
     * Drops entries older than {@code cutoffMillis} from the front of the list.
     * Assumes the list is roughly chronologically ordered (which it is — entries are
     * appended with {@code System.currentTimeMillis()}). Cheap because we stop at the
     * first surviving entry rather than scanning the whole list.
     */
    private static void evictOlderThan(java.util.ArrayList<dev.kitteh.factions.audit.AuditEntry> log, long cutoffMillis) {
        int firstSurvivor = 0;
        while (firstSurvivor < log.size() && log.get(firstSurvivor).timestamp < cutoffMillis) {
            firstSurvivor++;
        }
        if (firstSurvivor > 0) {
            log.subList(0, firstSurvivor).clear();
        }
    }

    @Override
    public void addBankDeposit(UUID member, double amount) {
        if (this.bankDeposits == null) this.bankDeposits = new HashMap<>();
        this.bankDeposits.merge(member, amount, Double::sum);
    }

    @Override
    public void addBankWithdraw(UUID member, double amount) {
        if (this.bankWithdraws == null) this.bankWithdraws = new HashMap<>();
        this.bankWithdraws.merge(member, amount, Double::sum);
    }

    @Override
    public double bankDepositOf(UUID member) {
        if (this.bankDeposits == null) return 0;
        return this.bankDeposits.getOrDefault(member, 0.0);
    }

    @Override
    public double bankWithdrawOf(UUID member) {
        if (this.bankWithdraws == null) return 0;
        return this.bankWithdraws.getOrDefault(member, 0.0);
    }

    @Override
    public java.util.Map<UUID, Double> bankDepositLeaderboard() {
        if (this.bankDeposits == null) return java.util.Map.of();
        return java.util.Collections.unmodifiableMap(this.bankDeposits);
    }

    @Override
    public java.util.Map<UUID, Double> bankWithdrawLeaderboard() {
        if (this.bankWithdraws == null) return java.util.Map.of();
        return java.util.Collections.unmodifiableMap(this.bankWithdraws);
    }

    @Override
    public java.util.List<dev.kitteh.factions.audit.AuditEntry> blockLogAt(String world, int x, int y, int z) {
        if (this.blockLog == null) return java.util.List.of();
        java.util.ArrayList<dev.kitteh.factions.audit.AuditEntry> hits = new java.util.ArrayList<>();
        for (int i = this.blockLog.size() - 1; i >= 0; i--) {
            dev.kitteh.factions.audit.AuditEntry e = this.blockLog.get(i);
            if (e.x == x && e.y == y && e.z == z && world.equals(e.world)) {
                hits.add(e);
            }
        }
        return hits;
    }

    public void remove() {
        if (this.removed) {
            return;
        }

        if (Econ.shouldBeUsedWithBanks()) {
            Econ.setBalance(this, 0);
        }

        // Clean the board
        Instances.BOARD.clean(this);

        for (FPlayer fPlayer : fplayers) {
            fPlayer.resetFactionData(true);
        }

        this.removed = true;
        Factions.factions().remove(this);
    }

    @Override
    public Faction.Zones zones() {
        return this.zones;
    }
}
