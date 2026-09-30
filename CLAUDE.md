# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
./gradlew build                        # Build all modules
./gradlew :bukkit:shadowJar            # Spigot/Bukkit deployable JAR
./gradlew :paper:shadowJar             # Paper deployable JAR (factionsuuid.jar)

# Test
./gradlew test                         # Run all tests
./gradlew test --tests dev.kitteh.factions.config.file.ConfigTest.testMainConfig  # Single test

# Run a live Paper test server (paper module only)
./gradlew :paper:runServer
```

There is no lint task. Only one test class exists (`ConfigTest`), which round-trips each config file
through the loader; if you add a config, add it there.

Both shadow JARs relocate shaded deps under `moss.factions.shade.*`. `:paper:shadowJar` bundles
`:bukkit`, so the Paper JAR is the real deployable.

## Repository context

This is a **fork of upstream FactionsUUID** (`FactionsU/UID`) with a large set of server-specific
features layered on top: faction levels/XP, faction coins, missions, audit logs, potion effects,
boosters, and a coin-purchased upgrade shop. Upstream code and fork code sit side by side, so when
in doubt check `git log`/`git diff` to tell which is which — the fork additions are mostly
uncommitted or recent.

The fork also integrates with private/companion plugins not on Maven: **RegentHeadHunting** (`reference/`
holds a jar for reference) and **HytopOutpost**. Both are wired via reflection so the plugin still
compiles and runs without them on the classpath.

`libs/` is an unrelated Rust project, not part of the Gradle build.

## Architecture

Multi-module Gradle project (Java 21). All source lives under `dev.kitteh.factions`.

- **`bukkit/`** — Core implementation and effectively all of the fork's features. Works on Spigot and Paper.
- **`paper/`** — Paper-only enhancements: native command system, action bars/titles, scoreboards.
- **`example-plugin/`** — API usage examples.

### Core data model

- **`Faction`** — a group of players; owns territory, members/roles, permissions, economy, TNT bank,
  upgrades, bans, warps, and (fork) level/XP/coins/missions/audit logs/effect + booster inventories.
- **`FPlayer`** — a player's faction membership and state.
- **`Board`** — maps chunk coordinates (`FLocation`) to `Faction`. The territory system.

`Faction` and `FPlayer` both implement the sealed **`Participator`** interface (messaging, relation
lookup, color resolution). In-memory impls live in `data/` (`MemoryFaction`, `MemoryFPlayer`,
`MemoryBoard`, `MemoryUniverse`); `data/json/` holds the persistence counterparts.

**Persistence is implicit and this matters.** `JSONFaction` is an empty subclass of `MemoryFaction`, and
Gson reflects over `MemoryFaction`'s fields. **Any field you add to `MemoryFaction` is automatically
saved to `factions.json` with no extra plumbing** — that is how every fork feature stores its state.
Expose it as an accessor pair on the `Faction` interface and you are done. `SaveTask` runs async
periodic saves.

### Permissions

`PermissibleActions` (BUILD, DESTROY, KICK, CLAIM, FLY, UPGRADE, …) × `Role` (ADMIN > COLEADER >
MODERATOR > NORMAL > RECRUIT) × `Relation` (ALLY, TRUCE, ENEMY, …) drive all protection and access
control. Selectors in `permissible/selector/` map role+relation combinations to a `PermState`.
Bukkit permission nodes are the separate `util/Permission` enum (auto-prefixed `factions.`).

### Command system

Commands use [Incendo Cloud](https://github.com/Incendo/cloud). Roots are `/f` and `/fa` (admin).
Every command implements the `Cmd` functional interface, which supplies a
`TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>>`.

To add a command:

1. Create `command/defaults/CmdFoo.java` (or `command/defaults/admin/`) implementing `Cmd`. In the
   consumer, build off the passed-in `builder`, then `manager.command(...)`.
2. **Always chain onto the parent permission** — `builder.commandPermission().and(Cloudy.hasFaction())` —
   never replace it. The root builder already carries the world-enabled check, and the `/fa` root
   carries `factions.kit.halfmod`. `Cloudy` supplies the predicate permissions (`isPlayer`,
   `hasFaction`, `isBypass`, `isAtLeastRole`, `hasSelfFactionPerms`, `hasPermission`) and
   `Cloudy.desc(String)` for descriptions.
3. Register it in `CommandsRoot.register()` via `registerInternal("foo", new CmdFoo())` /
   `registerAdminInternal(...)`. External plugins use the public `CommandsRoot.register(plugin, …)`.
4. Add the node to `util/Permission` **and** declare it in `paper/src/main/resources/paper-plugin.yml`,
   including membership in the right `factions.kit.*` parent.

Casting `context.sender()` to `Sender.Player` is only safe when the command is gated by
`Cloudy.isPlayer()`/`hasFaction()`. Costs go through `sender.payForCommand(...)`; in-faction action
checks use `faction.hasAccess(fPlayer, PermissibleActions.X, loc)` or the declarative
`Cloudy.hasSelfFactionPerms(...)`.

**Messages:** the legacy `TL` enum + `msgLegacy(TL, args)` is still used for commands with
configurable aliases (`show`, `set`, `warp`). **New code should use MiniMessage** —
`sendRichMessage("<gray>…")` on `Sender`/`FPlayer`/`Faction`, or `Mini.parse(...)` with tag resolvers
(see `tagresolver/`) for richer output. `Cloudy.desc(TL)` and the `TL` overloads of
`payForCommand`/`msg` are deprecated.

GUI-driven commands (`CmdUpgrades`, `CmdMissions`, `CmdEffects`, `CmdLogs`, `CmdPerms`, `CmdRoster`,
`CmdInfo`, `CmdTNT`) use the shaded InventoryFramework (`com.github.stefvanschie.inventoryframework`).

### Configuration

HOCON files loaded via Configurate through a custom reflective `Loader`, registered in `ConfigManager`:

| File | Class | Contents |
| --- | --- | --- |
| `main.conf` | `MainConfig` | Land, economy, power, DTR, upgrades |
| `permissions.conf` | `PermissionsConfig` | Default selectors per role/relation |
| `translations.conf` | `TranslationsConfig` | Player-facing strings (`TL` enum) |
| `dynmap.conf` | `DynmapConfig` | Dynmap integration |
| `factionlevels.conf` | `FactionLevelsConfig` | **Fork:** levels, rewards, and *all* coin-shop upgrade tiers |
| `missions.conf` | `MissionsConfig` | **Fork:** mission definitions |

`@Comment`, `@ConfigName`, `@DefinedType`, `@WipeOnReload` control serialization; `Transitioner`
handles cross-version migrations.

Two gotchas in fork configs:
- HOCON parses whole numbers as `Integer` even in a `List<Long>`/`List<Double>` field, so accessors
  read defensively via `raw instanceof Number n`.
- The reflective loader treats `Map` as a primitive, so `MissionsConfig.missions` is a
  `Map<String, Object>` of raw nested maps that `buildDefinitions()` hand-parses (malformed entries
  are silently skipped) rather than a typed `Map<String, MissionEntry>`. `MissionEntry` exists only
  as field-shape documentation and is never constructed.

### Fork subsystems

All of these keep their state on `MemoryFaction` (see persistence note above); their managers are
static and stateless.

**`level/` — levels, effects, boosters.** `FactionLevelManager.awardXp()` handles level-ups with XP
carryover and pays rewards (money, coins, potion-effect unlocks, random boosters). `FactionEffectManager`
applies potion effects to online members. `BoosterManager` rolls a random `BoosterType` at *earn* time
and, on activation, dispatches a console command to RegentHeadHunting (`hhadmin booster faction …`) —
its output is a command string, not an API call. Note `FactionLevelsConfig` is misleadingly named: it
is the catch-all config for the whole fork feature set.

**`mission/` — rotating faction missions.** `MissionType` (trackable events) × `MissionCategory`
(DAILY/BI_DAILY/WEEKLY/MAP, each with a reset interval and a pick count). Each faction gets a
deterministic subset of the pool per window — `pickRotation()` seeds a partial Fisher-Yates from the
faction id, so picks survive restarts. All progress flows through
`MissionManager.increment(faction, type, key, amount)`, fed by `ListenMissions` (first-party Bukkit
events) plus reflection-wired optional listeners (`ListenHH*`, `ListenOutpost*`) that no-op when the
companion plugin is absent. Adding a mission for an existing `MissionType` is **config-only**; a new
kind of tracked event needs a `MissionType` constant plus a listener calling `increment`.

**`audit/` — faction and block logs.** `AuditEntry` is a Gson POJO with a free-form `type` string; no
registry or enum. Two capped, time-evicted lists on the faction back `/f logs` and `/f inspect`. To log
something, just call `faction.recordFactionLog(AuditEntry.generic(...))` or `recordBlockLog(AuditEntry.block(...))`;
for event-driven logging add a handler to `ListenAuditFaction`/`ListenAuditBlocks`.

**Two unrelated upgrade systems — do not confuse them.**
- `upgrade/` (`Upgrade`, `Upgrades`, `UpgradeRegistry`, …) is the **upstream, money-purchased** upgrade
  API, keyed by `faction.upgrade(Upgrade)`. Unchanged by the fork.
- The **coin shop** is the fork's skill tree and does not touch `upgrade/` at all. Its currency is
  `faction.factionPoints()` (earned from levels and missions), its state is the generic
  `faction.customUpgradeTier(key)` map, and its keys are `KEY_*` string constants declared on
  `CmdUpgrades`, with tier counts/costs in `FactionLevelsConfig.UpgradeTierConfig`. To add one: add the
  key + GUI slot and the `getTierConfig`/display switches in `CmdUpgrades`, add the tier config field,
  then read `customUpgradeTier(KEY_X)` wherever the perk applies. Nothing to register.

### Integrations

`integration/` wires optional third-party plugins (Vault, LuckPerms, WorldGuard, PlaceholderAPI,
Dynmap, Essentials, Citizens, Depenizen, PlayerVaults, Graves, Duels, YardWatch, and the fork's
RegentHeadHunting/HytopOutpost) via `IntegrationManager`. Every integration is guarded by an
availability check so the plugin works without any of them. Optional deps must be declared in
`paper-plugin.yml` under `dependencies:` with `required: false`.

### Custom events

~19 Bukkit events in `event/` (`FactionCreateEvent`, `LandClaimEvent`, `PowerLossEvent`,
`DTRLossEvent`, …), all extending `FactionEvent` or `FactionPlayerEvent`, let external plugins hook the
faction lifecycle. The fork's own audit listeners consume these at `MONITOR` priority.
