package dev.kitteh.factions;

import dev.kitteh.factions.permissible.PermSelector;
import dev.kitteh.factions.permissible.PermState;
import dev.kitteh.factions.permissible.PermissibleAction;
import dev.kitteh.factions.permissible.Relation;
import dev.kitteh.factions.permissible.Role;
import dev.kitteh.factions.permissible.Selectable;
import dev.kitteh.factions.upgrade.Upgrade;
import dev.kitteh.factions.upgrade.Upgrades;
import dev.kitteh.factions.util.BanInfo;
import dev.kitteh.factions.util.LazyLocation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@ApiStatus.AvailableSince("4.0.0")
@ApiStatus.NonExtendable
@NullMarked
public non-sealed interface Faction extends Participator, Selectable {
    interface Permissions {
        interface SelectorPerms {
            /**
             * Gets this selector's permission state for the given action.
             *
             * @param action action
             * @return perm state
             */
            default PermState get(PermissibleAction action) {
                return this.get(action.name());
            }

            /**
             * Gets this selector's permission state for the given action name.
             *
             * @param action action name, case-insensitive
             * @return perm state
             */
            PermState get(String action);

            /**
             * Sets this selector's permission state for the given action.
             *
             * @param action action
             * @param state  perm state
             */
            default void set(PermissibleAction action, PermState state) {
                this.set(action.name(), state);
            }

            /**
             * Sets this selector's permission state for the given action name.
             *
             * @param action action, case-insensitive
             * @param state  perm state
             */
            void set(String action, PermState state);

            /**
             * Gets all action names with set permission states.
             * May include names not presently registered with the plugin.
             *
             * @return actions
             */
            Collection<String> actions();
        }

        /**
         * Gets an immutable, ordered list of the selectors tracked.
         *
         * @return the selectors
         */
        List<PermSelector> selectors();

        /**
         * Moves the given selector up in the order.
         *
         * @param selector selector to move up
         */
        void moveSelectorUp(PermSelector selector);

        /**
         * Moves the given selector down in the order.
         *
         * @param selector selector to move down
         */
        void moveSelectorDown(PermSelector selector);

        /**
         * Gets a selector's perms.
         *
         * @param selector selector to get
         * @return perms
         * @throws IllegalArgumentException if this selector is not tracked
         */
        SelectorPerms get(PermSelector selector);

        /**
         * Gets if a given selector is tracked.
         *
         * @param selector selector
         * @return true if tracked
         */
        boolean has(PermSelector selector);

        /**
         * Adds a selector. If an already added selector is provided, functions the same as {@link #get(PermSelector)}.
         *
         * @param selector selector
         * @return the selector perms for immediate editing
         */
        SelectorPerms add(PermSelector selector);

        /**
         * Removes a selector.
         *
         * @param selector selector to remove
         */
        void remove(PermSelector selector);

        /**
         * Clears everything.
         */
        void clear();
    }

    /**
     * Controller for the faction's zones.
     */
    interface Zones {
        /**
         * Creates a zone.
         *
         * @param name zone name
         * @return new zone
         * @throws IllegalArgumentException if the name is already in use
         */
        Zone create(String name);

        /**
         * Deletes a zone.
         *
         * @param name named zone
         */
        void delete(String name);

        /**
         * Gets the zone at a given location.
         *
         * @param fLocation location
         * @return zone or default zone if not this faction's territory
         */
        Zone get(FLocation fLocation);

        /**
         * Gets a zone by name.
         *
         * @param name zone name
         * @return matching zone or null for no match
         */
        @Nullable
        Zone get(String name);

        /**
         * Gets an immutable list of all zones.
         *
         * @return zones
         */
        List<Zone> get();

        /**
         * Gets the main, default zone.
         *
         * @return main zone
         */
        Zone main();

        /**
         * Sets the zone for a given location
         *
         * @param zone      zone
         * @param fLocation location
         * @throws IllegalArgumentException if the zone is not an active zone for this faction or if the location is not owned by the faction
         */
        void set(Zone zone, FLocation fLocation);
    }

    /**
     * An individual zone.
     */
    interface Zone {
        /**
         * Gets the zone's internal ID.
         *
         * @return zone id
         */
        int id();

        /**
         * Gets the zone's name.
         *
         * @return zone name
         */
        String name();

        /**
         * Sets the zone's name.
         *
         * @param name new name
         */
        void name(String name);

        /**
         * Gets the zone's greeting.
         *
         * @return greeting
         */
        Component greeting();

        /**
         * Gets the zone's raw MiniMessage greeting.
         *
         * @return MiniMessage greeting or null if the zone uses the main zone's greeting
         */
        @Nullable
        String greetingString();

        /**
         * Sets the zone's greeting in MiniMessage format. Takes the 'tag' placeholder to inject the faction tag.
         * If null, will default to the main zone's greeting.
         *
         * @param greeting new greeting
         * @throws IllegalArgumentException if sending null for the main zone
         */
        void greeting(@Nullable String greeting);

        /**
         * Gets the zone's permissions.
         *
         * @return zone permissions
         */
        Permissions permissions();

        /**
         * Gets if the given player can manage this zone, meaning they have the ZONE permission for specifically this zone (if not overridden).
         *
         * @param fPlayer player
         * @return true if can manage
         */
        boolean canPlayerManage(FPlayer fPlayer);
    }

    int id();

    void addAnnouncement(FPlayer fPlayer, String msg);

    void sendUnreadAnnouncements(FPlayer fPlayer);

    Map<String, LazyLocation> warps();

    @Nullable
    LazyLocation warp(String name);

    default boolean isWarp(String name) {
        return warp(name) != null;
    }

    void createWarp(String name, LazyLocation loc);

    boolean hasWarpPassword(String warp);

    boolean isWarpPassword(String warp, String password);

    void setWarpPassword(String warp, String password);

    void removeWarpPassword(String warp);

    boolean removeWarp(String name);

    void clearWarps();

    /**
     * Per-warp role override: returns the set of role names (lowercase, e.g. "admin", "moderator")
     * allowed to use the given warp via the GUI. If the warp has no override (returns null or empty),
     * fall back to the default WARP permissible-action check.
     */
    java.util.Set<String> warpAllowedRoles(String warpName);

    /** Set the allowed roles for a warp. Pass an empty set to clear the override (default behavior resumes). */
    void warpAllowedRoles(String warpName, java.util.Set<String> roleNames);

    @ApiStatus.AvailableSince("4.2.0")
    default int maxWarps() {
        int lvl = this.upgradeLevel(Upgrades.WARPS);
        if (lvl == 0) {
            return 0;
        }
        return Universe.universe().upgradeSettings(Upgrades.WARPS).valueAt(Upgrades.Variables.COUNT, lvl).intValue();
    }

    int maxVaults();

    void maxVaults(int value);

    Set<UUID> invites();

    void invite(FPlayer fplayer);

    void deInvite(FPlayer fplayer);

    boolean hasInvite(FPlayer fplayer);

    // ── Faction Roster ──────────────────────────────────────────────────────

    /**
     * Pre-approved roster: map of UUIDs → Role. Players on this map can join without an invite,
     * and on join their role is set to the value here.
     */
    java.util.Map<UUID, dev.kitteh.factions.permissible.Role> rosterRoles();

    /** Convenience: just the UUIDs on the roster. */
    default Set<UUID> roster() {
        return rosterRoles().keySet();
    }

    /**
     * Add a player to the roster with the given role. Returns true if added or role updated.
     * Returns false if the roster is full (and the player isn't already on it).
     * Reject role == ADMIN — leader cannot be pre-rostered.
     */
    boolean rosterAdd(UUID playerId, dev.kitteh.factions.permissible.Role role);

    /** Add with default role (NORMAL / member). */
    default boolean rosterAdd(UUID playerId) {
        return rosterAdd(playerId, dev.kitteh.factions.permissible.Role.NORMAL);
    }

    /** Update an existing roster entry's role. Returns true if the player is on the roster. */
    boolean rosterSetRole(UUID playerId, dev.kitteh.factions.permissible.Role role);

    /** The role assigned to the given roster entry, or null if not on the roster. */
    dev.kitteh.factions.permissible.Role rosterRoleOf(UUID playerId);

    /** Remove a player from the roster. Returns true if they were on it. */
    boolean rosterRemove(UUID playerId);

    /** Whether the given player is on the faction's roster. */
    boolean isOnRoster(UUID playerId);

    // ── Faction Owners (leader-takeover trust list) ─────────────────────────

    /**
     * Set of UUIDs that the current leader has marked as trusted owners.
     * Persists through kicks/leaves — only explicit removal clears the entry.
     */
    Set<UUID> owners();

    /** Add a UUID to the owners list. Returns true if newly added. */
    boolean ownersAdd(UUID playerId);

    /** Remove a UUID from the owners list. Returns true if they were on it. */
    boolean ownersRemove(UUID playerId);

    /** Whether the given UUID is a trusted owner. */
    boolean isOwner(UUID playerId);

    void ban(FPlayer target, FPlayer banner);

    void unban(FPlayer player);

    boolean isBanned(FPlayer player);

    Set<BanInfo> bans();

    boolean open();

    void open(boolean isOpen);

    boolean isPeaceful();

    void peaceful(boolean isPeaceful);

    void peacefulExplosionsEnabled(boolean val);

    boolean peacefulExplosionsEnabled();

    default boolean noExplosionsInTerritory() {
        return (this.isNormal() && Universe.universe().grace()) || this.shieldActive() || (this.isPeaceful() && !this.peacefulExplosionsEnabled());
    }

    boolean isPermanent();

    void permanent(boolean isPermanent);

    String tag();

    void tag(String str);

    @Deprecated(forRemoval = true, since = "4.0.0")
    String tagLegacy(@Nullable Participator participator);

    String description();

    void description(String value);

    String link();

    void link(String value);

    void home(Location home);

    void removeHome();

    default boolean hasHome() {
        return this.home() != null;
    }

    @Nullable
    Location home();

    Instant founded();

    void founded(Instant when);

    default boolean noPvPInTerritory() {
        return isSafeZone() || (isPeaceful() && FactionsPlugin.instance().conf().factions().specialCase().isPeacefulTerritoryDisablePVP());
    }

    default boolean noMonstersInTerritory() {
        return isSafeZone() || (isPeaceful() && FactionsPlugin.instance().conf().factions().specialCase().isPeacefulTerritoryDisableMonsters());
    }

    default boolean isNormal() {
        return !(this.isWilderness() || this.isSafeZone() || this.isWarZone());
    }

    default boolean isWilderness() {
        return this.id() == Factions.ID_WILDERNESS;
    }

    default boolean isSafeZone() {
        return this.id() == Factions.ID_SAFEZONE;
    }

    default boolean isWarZone() {
        return this.id() == Factions.ID_WARZONE;
    }

    void lastDeath(Instant time);

    Instant lastDeath();

    default int kills() {
        return members().stream().mapToInt(FPlayer::kills).sum();
    }

    default int deaths() {
        return members().stream().mapToInt(FPlayer::deaths).sum();
    }

    /**
     * Get the access of a selectable for a given chunk.
     *
     * @param selectable        selectable
     * @param permissibleAction permissible
     * @param location          location
     * @return player's access
     */
    boolean hasAccess(Selectable selectable, PermissibleAction permissibleAction, @Nullable FLocation location);

    Permissions permissions();

    int tntBank();

    int tntBankMax();

    void tntBank(int amount);

    default boolean shieldActive() {
        return this.shieldRemaining().isPositive();
    }

    Duration shieldCooldownRemaining();

    Duration shieldRemaining();

    void shield(Duration duration, Duration cooldown);

    int upgradeLevel(Upgrade upgrade);

    void upgradeLevel(Upgrade upgrade, int level);

    // ── Faction Levels ──────────────────────────────────────────────────────

    long factionXp();

    void factionXp(long xp);

    int factionLevel();

    void factionLevel(int level);

    int factionPoints();

    void factionPoints(int points);

    void addFactionPoints(int amount);

    boolean spendFactionPoints(int amount);

    // ── Custom Upgrade Tiers ────────────────────────────────────────────────

    int customUpgradeTier(String upgradeKey);

    void customUpgradeTier(String upgradeKey, int tier);

    // ── Faction PV (Private Vault) ──────────────────────────────────────────

    String[] pvContents();

    void pvContents(String[] contents);

    // ── Faction Effects ─────────────────────────────────────────────────────

    long effectCooldownEnd();

    void effectCooldownEnd(long epochMillis);

    default boolean effectOnCooldown() {
        return System.currentTimeMillis() < this.effectCooldownEnd();
    }

    /**
     * Faction effect inventory — stockpile of purchased / admin-granted effect activations,
     * keyed by a string token of the form {@code EFFECT_NAME:DURATION_SECONDS}.
     * Each entry records how many uses are currently saved.
     */
    java.util.Map<String, Integer> effectInventory();

    /** Add one or more uses of a given effect-duration to the saved inventory. */
    void addEffectToInventory(String effectName, int durationSeconds, int count);

    /**
     * Consume one use of the given effect-duration. Returns true if a use was consumed.
     * Returns false if no uses are available.
     */
    boolean consumeEffectFromInventory(String effectName, int durationSeconds);

    /**
     * Faction booster inventory — stockpile of earned (level-up / mission reward) faction
     * boosters, keyed by a string token of the form {@code TYPE:MULT_X100:MINUTES}, where
     * MULT_X100 is the multiplier × 100 (e.g. 150 = 1.5x). Each entry records how many are saved.
     */
    java.util.Map<String, Integer> boosterInventory();

    /** Add one or more of a rolled booster (type + multiplier ×100 + minutes) to the inventory. */
    void addBoosterToInventory(String boosterType, int multiplierX100, int minutes, int count);

    /**
     * Consume one of the given booster. Returns true if one was consumed,
     * false if none are available.
     */
    boolean consumeBoosterFromInventory(String boosterType, int multiplierX100, int minutes);

    // ── Faction Audit Logs ──────────────────────────────────────────────────

    /**
     * Append a faction-management audit event (/f logs). Caps at an internal limit, evicting oldest.
     */
    void recordFactionLog(dev.kitteh.factions.audit.AuditEntry entry);

    /** All faction-management entries, oldest first. */
    java.util.List<dev.kitteh.factions.audit.AuditEntry> factionLog();

    /** Wipes all entries from the faction-management log. */
    void clearFactionLog();

    /** Append a per-block history event (/f inspect). Same eviction policy. */
    void recordBlockLog(dev.kitteh.factions.audit.AuditEntry entry);

    /** Block history entries newest first that match the given coords. */
    java.util.List<dev.kitteh.factions.audit.AuditEntry> blockLogAt(String world, int x, int y, int z);

    // ── Bank Contribution Tracking ──────────────────────────────────────────

    /** Add to a member's lifetime deposit total for this faction's bank. */
    void addBankDeposit(java.util.UUID member, double amount);

    /** Add to a member's lifetime withdrawal total. */
    void addBankWithdraw(java.util.UUID member, double amount);

    /** Total deposited by a member to this faction's bank, all-time. */
    double bankDepositOf(java.util.UUID member);

    /** Total withdrawn by a member from this faction's bank, all-time. */
    double bankWithdrawOf(java.util.UUID member);

    /** Snapshot of all members' deposit totals (UUID → amount). Read-only. */
    java.util.Map<java.util.UUID, Double> bankDepositLeaderboard();

    /** Snapshot of all members' withdraw totals (UUID → amount). Read-only. */
    java.util.Map<java.util.UUID, Double> bankWithdrawLeaderboard();

    // ── Faction Missions ────────────────────────────────────────────────────

    long missionProgress(String missionId);

    void missionProgress(String missionId, long value);

    long missionLastReset(String category);

    void missionLastReset(String category, long epochMillis);

    /** Active mission IDs for a category (rotation pool selection). May be empty if not yet rolled. */
    java.util.List<String> missionActiveIds(String category);

    /** Set the active mission IDs for a category. */
    void missionActiveIds(String category, java.util.List<String> ids);

    Relation relationWish(Faction otherFaction);

    void relationWish(Faction otherFaction, Relation relation);

    default int relationCount(Relation relation) {
        int count = 0;
        for (Faction faction : Factions.factions().all()) {
            if (faction.relationTo(this) == relation) {
                count++;
            }
        }
        return count;
    }

    /**
     * Gets if the faction is raidable.
     *
     * @return true if raidable
     * @see dev.kitteh.factions.landraidcontrol.LandRaidControl#isRaidable(Faction)
     */
    @ApiStatus.AvailableSince("4.3.0")
    default boolean isRaidable() {
        return FactionsPlugin.instance().landRaidControl().isRaidable(this);
    }

    double dtr();

    double dtrWithoutUpdate();

    void dtr(double dtr);

    long dtrLastUpdated();

    long dtrFrozenUntil();

    void dtrFrozenUntil(long time);

    default boolean dtrFrozen() {
        return System.currentTimeMillis() < this.dtrFrozenUntil();
    }

    /**
     * Gets the exact faction power, which is not used for claim/raidability calculations
     *
     * @return exact power
     */
    double powerExact();

    /**
     * Gets the exact faction max power
     *
     * @return exactmax power
     */
    double powerMaxExact();

    /**
     * Gets the faction power, as used for claims/raidability calculations
     *
     * @return power
     */
    default int power() {
        return (int) Math.round(this.powerExact());
    }

    default int powerMax() {
        return (int) Math.round(this.powerMaxExact());
    }

    @Nullable
    Integer permanentPower();

    void permanentPower(@Nullable Integer permanentPower);

    default boolean hasPermanentPower() {
        return this.permanentPower() != null;
    }

    double powerBoost();

    void powerBoost(double powerBoost);

    default boolean hasLandInflation() {
        return FactionsPlugin.instance().landRaidControl().hasLandInflation(this);
    }

    boolean isPowerFrozen();

    int size();

    default int memberLimit() {
        int confMax = FactionsPlugin.instance().conf().factions().other().getFactionMemberLimit();
        if (confMax < 1) {
            return Integer.MAX_VALUE;
        }
        if (Universe.universe().isUpgradeEnabled(Upgrades.MAX_MEMBERS) && this.upgradeLevel(Upgrades.MAX_MEMBERS) > 0) {
            int boost = Universe.universe().upgradeSettings(Upgrades.MAX_MEMBERS).valueAt(Upgrades.Variables.POSITIVE_INCREASE, this.upgradeLevel(Upgrades.MAX_MEMBERS)).intValue();
            return confMax + boost;
        } else {
            return confMax;
        }
    }

    Set<FPlayer> members();

    Set<FPlayer> membersOnline(boolean online);

    Set<FPlayer> membersOnline(boolean online, @Nullable FPlayer viewer);

    @Nullable
    FPlayer admin();

    List<FPlayer> members(Role role);

    List<Player> membersOnlineAsPlayers();

    boolean hasMembersOnline();

    void trackMemberLoggedOff();

    void promoteNewLeader();

    Role defaultRole();

    void defaultRole(Role role);

    @Override
    default void sendMessage(Component component) {
        for (FPlayer fplayer : this.membersOnline(true)) {
            fplayer.sendMessage(component);
        }
    }

    @Override
    default void sendRichMessage(String miniMessage, TagResolver... resolvers) {
        for (FPlayer fplayer : this.membersOnline(true)) {
            fplayer.sendRichMessage(miniMessage, resolvers);
        }
    }

    @Deprecated(forRemoval = true, since = "4.0.0")
    default void sendMessageLegacy(String message) {
        for (FPlayer fplayer : this.membersOnline(true)) {
            fplayer.sendMessageLegacy(message);
        }
    }

    default Set<FLocation> claims() {
        return Board.board().allClaims(this);
    }

    default int claimCount() {
        return Board.board().claimCount(this);
    }

    default int claimCount(World world) {
        return Board.board().claimCount(this, world);
    }

    /**
     * Gets the faction's zone controller.
     *
     * @return zones
     */
    Zones zones();
}
