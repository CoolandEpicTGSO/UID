package dev.kitteh.factions.command.defaults;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import dev.kitteh.factions.FPlayer;
import dev.kitteh.factions.Faction;
import dev.kitteh.factions.FactionsPlugin;
import dev.kitteh.factions.command.Cloudy;
import dev.kitteh.factions.command.Cmd;
import dev.kitteh.factions.command.Sender;
import dev.kitteh.factions.util.Permission;
import dev.kitteh.factions.level.FactionEffectManager;
import dev.kitteh.factions.level.FactionLevelsConfig;
import dev.kitteh.factions.util.TriConsumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;

import java.util.ArrayList;
import java.util.List;

public class CmdEffects implements Cmd {

    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        return (manager, builder, help) -> manager.command(
                builder.literal("effects", "effect", "fx")
                        .permission(builder.commandPermission().and(Cloudy.hasPermission(Permission.EFFECTS).and(Cloudy.hasFaction())))
                        .commandDescription(Cloudy.desc("View and activate faction effects"))
                        .handler(this::handle)
        );
    }

    private void handle(CommandContext<Sender> context) {
        FPlayer sender = ((Sender.Player) context.sender()).fPlayer();
        Player player = ((Sender.Player) context.sender()).player();

        // F Effects must first be unlocked via /f upgrades
        if (sender.faction().customUpgradeTier(CmdUpgrades.KEY_EFFECTS) < 1) {
            sender.sendRichMessage("<red>Your faction hasn't unlocked F Effects yet! Use <gold>/f upgrades</gold> to purchase it.");
            return;
        }

        if (!sender.faction().hasAccess(sender, dev.kitteh.factions.permissible.PermissibleActions.EFFECTS, sender.lastStoodAt())) {
            sender.sendRichMessage("<red>You do not have permission to use faction effects.");
            return;
        }
        openMainMenu(player, sender);
    }

    // ── Main selector: choose Effects or Boosters ─────────────────────────────

    private void openMainMenu(Player player, FPlayer sender) {
        Component title = Component.text("✦ Faction Effects", NamedTextColor.LIGHT_PURPLE);
        ChestGui gui = new ChestGui(3, ComponentHolder.of(title));
        gui.setOnGlobalDrag(e -> e.setCancelled(true));
        gui.setOnGlobalClick(e -> e.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 3);
        ItemStack filler = makeFiller(Material.PURPLE_STAINED_GLASS_PANE);
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 3; y++) {
                pane.addItem(new GuiItem(filler), x, y);
            }
        }

        Faction faction = sender.faction();

        // Total saved-effect count
        int savedTotal = faction.effectInventory().values().stream().mapToInt(Integer::intValue).sum();

        // Potion Effects button
        ItemStack effectsBtn = new ItemStack(Material.SPLASH_POTION);
        ItemMeta em = effectsBtn.getItemMeta();
        em.setDisplayName(ChatColor.LIGHT_PURPLE + "✦ Potion Effects");
        List<String> el = new ArrayList<>();
        el.add(ChatColor.GRAY + "View and activate your saved");
        el.add(ChatColor.GRAY + "faction effects.");
        el.add(" ");
        el.add(ChatColor.GRAY + "Saved effects: " + ChatColor.YELLOW + savedTotal);
        em.setLore(el);
        effectsBtn.setItemMeta(em);
        GuiItem effectsBtnItem = new GuiItem(effectsBtn);
        effectsBtnItem.setAction(e -> {
            e.setCancelled(true);
            openEffectsMenu(player, sender);
        });
        pane.addItem(effectsBtnItem, 2, 1);

        // Op-only: Admin Commands cheatsheet tile (middle slot)
        if (player.isOp()) {
            ItemStack adminBtn = new ItemStack(Material.COMMAND_BLOCK);
            ItemMeta abm = adminBtn.getItemMeta();
            abm.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "✦ Admin Commands");
            List<String> abl = new ArrayList<>();
            abl.add(ChatColor.GRAY + "F Effects admin commands:");
            abl.add(" ");
            abl.add(ChatColor.YELLOW + "/f admin geteffect " + ChatColor.GRAY + "<player> <effect> [seconds]");
            abl.add(ChatColor.DARK_GRAY + "  Saves an effect into the player's");
            abl.add(ChatColor.DARK_GRAY + "  faction's effect inventory.");
            abl.add(" ");
            abl.add(ChatColor.YELLOW + "/f admin xp " + ChatColor.GRAY + "<faction> <amount>");
            abl.add(ChatColor.DARK_GRAY + "  Award (or remove) faction XP.");
            abl.add(" ");
            abl.add(ChatColor.YELLOW + "/f admin coins " + ChatColor.GRAY + "<faction> <amount>");
            abl.add(ChatColor.DARK_GRAY + "  Give or take faction coins.");
            abl.add(" ");
            abl.add(ChatColor.YELLOW + "/f admin setlevel " + ChatColor.GRAY + "<faction> <level>");
            abl.add(ChatColor.DARK_GRAY + "  Set a faction's level (resets XP).");
            abl.add(" ");
            abl.add(ChatColor.DARK_GRAY + "" + ChatColor.ITALIC + "Visible only to opped players.");
            abm.setLore(abl);
            adminBtn.setItemMeta(abm);
            pane.addItem(new GuiItem(adminBtn, e -> e.setCancelled(true)), 4, 1);
        }

        // Boosters button — opens the faction's stored booster inventory
        int boosterTotal = faction.boosterInventory().values().stream().mapToInt(Integer::intValue).sum();
        ItemStack boostersBtn = new ItemStack(Material.BEACON);
        ItemMeta bm = boostersBtn.getItemMeta();
        bm.setDisplayName(ChatColor.AQUA + "✦ Boosters");
        List<String> bl = new ArrayList<>();
        bl.add(ChatColor.GRAY + "View and activate your saved");
        bl.add(ChatColor.GRAY + "faction boosters.");
        bl.add(" ");
        bl.add(ChatColor.GRAY + "Saved boosters: " + ChatColor.YELLOW + boosterTotal);
        bm.setLore(bl);
        boostersBtn.setItemMeta(bm);
        GuiItem boostersBtnItem = new GuiItem(boostersBtn);
        boostersBtnItem.setAction(e -> {
            e.setCancelled(true);
            openBoostersMenu(player, sender);
        });
        pane.addItem(boostersBtnItem, 6, 1);

        gui.addPane(pane);
        gui.show(player);
    }

    // ── Potion effects panel ──────────────────────────────────────────────────

    /**
     * Saved-inventory view: shows the faction's stored effect uses. Click an entry to consume one and apply it.
     * A "Shop" button opens the purchase flow.
     */
    private void openEffectsMenu(Player player, FPlayer sender) {
        Faction faction = sender.faction();

        int rows = 6;
        Component title = Component.text("✦ Saved F Effects", NamedTextColor.LIGHT_PURPLE);
        ChestGui gui = new ChestGui(rows, ComponentHolder.of(title));
        gui.setOnGlobalDrag(e -> e.setCancelled(true));
        gui.setOnGlobalClick(e -> e.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, rows);
        ItemStack filler = makeFiller(Material.PURPLE_STAINED_GLASS_PANE);
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < rows; y++) {
                pane.addItem(new GuiItem(filler), x, y);
            }
        }

        java.util.Map<String, Integer> inventory = faction.effectInventory();
        if (inventory.isEmpty()) {
            ItemStack empty = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta em = empty.getItemMeta();
            em.setDisplayName(ChatColor.GRAY + "No saved effects yet");
            List<String> el = new ArrayList<>();
            el.add(ChatColor.GRAY + "Buy effects from the Shop below.");
            em.setLore(el);
            empty.setItemMeta(em);
            pane.addItem(new GuiItem(empty), 4, 2);
        } else {
            // Sort entries deterministically so the GUI doesn't shuffle each open
            java.util.List<java.util.Map.Entry<String, Integer>> entries = new java.util.ArrayList<>(inventory.entrySet());
            entries.sort(java.util.Map.Entry.comparingByKey());

            // Pack contiguously, slot 0 onward. Skip the footer row.
            int slotsPerRow = 9;
            int contentRows = rows - 1;
            int maxSlots = slotsPerRow * contentRows;

            int slotIdx = 0;
            for (java.util.Map.Entry<String, Integer> entry : entries) {
                if (slotIdx >= maxSlots) break;

                String[] parts = entry.getKey().split(":");
                if (parts.length != 2) continue;
                String effectName = parts[0];
                int duration;
                try {
                    duration = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    continue;
                }
                PotionEffectType type = FactionEffectManager.byKey(effectName);
                if (type == null) continue;
                int count = entry.getValue();

                ItemStack item = FactionEffectManager.buildPotionIcon(type, duration);
                item.setAmount(Math.max(1, Math.min(64, count)));

                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + FactionEffectManager.displayName(type)
                        + ChatColor.GRAY + " (" + formatDurationLabel(duration) + ")");
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Saved uses: " + ChatColor.YELLOW + count);
                lore.add(ChatColor.GRAY + "Duration: " + ChatColor.WHITE + formatDurationLabel(duration));
                lore.add(" ");
                lore.add(ChatColor.GREEN + "▶ Click to activate (consumes 1)");
                meta.setLore(lore);
                // Hide the auto-generated potion-effect tooltip lines since we render our own
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
                        org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
                item.setItemMeta(meta);

                final PotionEffectType finalType = type;
                final int finalDuration = duration;
                final String finalStoredKey = effectName;
                GuiItem gi = new GuiItem(item);
                gi.setAction(e -> {
                    e.setCancelled(true);
                    if (!faction.consumeEffectFromInventory(finalStoredKey, finalDuration)) {
                        sender.sendRichMessage("<red>That effect isn't in your inventory anymore.");
                        openEffectsMenu(player, sender);
                        return;
                    }
                    FactionEffectManager.applyEffect(faction, finalType, finalDuration);
                    openEffectsMenu(player, sender);
                });

                int col = slotIdx % slotsPerRow;
                int row = slotIdx / slotsPerRow;
                pane.addItem(gi, col, row);
                slotIdx++;
            }
        }

        // Footer (row rows-1): Back, Coins display, Shop button
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName(ChatColor.GRAY + "← Back");
        back.setItemMeta(bm);
        GuiItem backItem = new GuiItem(back);
        backItem.setAction(e -> {
            e.setCancelled(true);
            openMainMenu(player, sender);
        });
        pane.addItem(backItem, 0, rows - 1);

        ItemStack status = new ItemStack(Material.SUNFLOWER);
        ItemMeta sm = status.getItemMeta();
        sm.setDisplayName(ChatColor.GOLD + "Faction Coins: " + ChatColor.YELLOW + faction.factionPoints());
        List<String> sl = new ArrayList<>();
        sl.add(ChatColor.GRAY + "Earn more by levelling up your faction.");
        sm.setLore(sl);
        status.setItemMeta(sm);
        pane.addItem(new GuiItem(status), 4, rows - 1);

        ItemStack shopBtn = new ItemStack(Material.EMERALD);
        ItemMeta shm = shopBtn.getItemMeta();
        shm.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "✦ Effects Shop");
        List<String> shl = new ArrayList<>();
        shl.add(ChatColor.GRAY + "Buy more effects with faction coins.");
        shm.setLore(shl);
        shopBtn.setItemMeta(shm);
        GuiItem shopItem = new GuiItem(shopBtn);
        shopItem.setAction(e -> {
            e.setCancelled(true);
            openEffectsShop(player, sender);
        });
        pane.addItem(shopItem, 8, rows - 1);

        gui.addPane(pane);
        gui.show(player);
    }

    /**
     * Shop view: lists every effect type from the configured unlock list (no per-level gate).
     * Click an effect → duration picker → buy → adds to inventory.
     */
    private void openEffectsShop(Player player, FPlayer sender) {
        Faction faction = sender.faction();
        FactionLevelsConfig cfg = FactionsPlugin.instance().configManager().factionLevelsConfig();

        int rows = 6;
        Component title = Component.text("✦ F Effects Shop — " + faction.factionPoints() + " coins", NamedTextColor.LIGHT_PURPLE);
        ChestGui gui = new ChestGui(rows, ComponentHolder.of(title));
        gui.setOnGlobalDrag(e -> e.setCancelled(true));
        gui.setOnGlobalClick(e -> e.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, rows);
        ItemStack filler = makeFiller(Material.PURPLE_STAINED_GLASS_PANE);
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < rows; y++) {
                pane.addItem(new GuiItem(filler), x, y);
            }
        }

        // List EVERY effect from the unlock list — no per-level gate (already gated by F Effects upgrade)
        int col = 1;
        int row = 1;
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        for (int lvl = 1; lvl <= cfg.maxLevel(); lvl++) {
            for (String effectName : cfg.effectUnlocks(lvl)) {
                PotionEffectType type = FactionEffectManager.byKey(effectName);
                if (type == null) continue;
                String canonicalKey = FactionEffectManager.canonicalKey(type);
                if (!seen.add(canonicalKey)) continue; // de-dupe by canonical key

                // Use a real coloured potion icon (drinkable variant for the shop tile)
                ItemStack item = FactionEffectManager.buildPotionIcon(type, 0);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(ChatColor.LIGHT_PURPLE + FactionEffectManager.displayName(type));
                List<String> lore = new ArrayList<>();
                int amp = FactionEffectManager.vanillaMaxAmplifier(type);
                if (amp > 0) {
                    lore.add(ChatColor.DARK_GRAY + "Vanilla max: " + ChatColor.GRAY + "Level " + (amp + 1));
                }
                lore.add(ChatColor.GRAY + "Tap to choose duration & buy");
                lore.add(" ");
                lore.add(ChatColor.DARK_GRAY + "Tiers: "
                        + cfg.effectCostForTier(0) + "c / "
                        + cfg.effectCostForTier(1) + "c / "
                        + cfg.effectCostForTier(2) + "c");
                meta.setLore(lore);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
                        org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
                item.setItemMeta(meta);

                final PotionEffectType finalType = type;
                GuiItem gi = new GuiItem(item);
                gi.setAction(e -> {
                    e.setCancelled(true);
                    openDurationPicker(player, sender, finalType);
                });
                pane.addItem(gi, col, row);

                col += 2;
                if (col > 7) {
                    col = 1;
                    row++;
                    if (row >= rows - 1) break;
                }
            }
            if (row >= rows - 1) break;
        }

        // Footer
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName(ChatColor.GRAY + "← Back");
        back.setItemMeta(bm);
        GuiItem backItem = new GuiItem(back);
        backItem.setAction(e -> {
            e.setCancelled(true);
            openEffectsMenu(player, sender);
        });
        pane.addItem(backItem, 0, rows - 1);

        ItemStack status = new ItemStack(Material.SUNFLOWER);
        ItemMeta sm = status.getItemMeta();
        sm.setDisplayName(ChatColor.GOLD + "Faction Coins: " + ChatColor.YELLOW + faction.factionPoints());
        status.setItemMeta(sm);
        pane.addItem(new GuiItem(status), 4, rows - 1);

        gui.addPane(pane);
        gui.show(player);
    }

    // ── Duration picker / shop submenu ────────────────────────────────────────

    private void openDurationPicker(Player player, FPlayer sender, PotionEffectType type) {
        Faction faction = sender.faction();
        FactionLevelsConfig cfg = FactionsPlugin.instance().configManager().factionLevelsConfig();

        Component title = Component.text(FactionEffectManager.displayName(type) + " — " + faction.factionPoints() + " coins", NamedTextColor.LIGHT_PURPLE);
        ChestGui gui = new ChestGui(3, ComponentHolder.of(title));
        gui.setOnGlobalDrag(e -> e.setCancelled(true));
        gui.setOnGlobalClick(e -> e.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 3);
        ItemStack filler = makeFiller(Material.PURPLE_STAINED_GLASS_PANE);
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 3; y++) {
                pane.addItem(new GuiItem(filler), x, y);
            }
        }

        // 3 duration tiers at cols 2, 4, 6 of row 1
        int[] tiers = {0, 1, 2};
        int[] cols = {2, 4, 6};
        for (int i = 0; i < 3; i++) {
            int tier = tiers[i];
            int duration = cfg.effectDurationForTier(tier);
            int cost = cfg.effectCostForTier(tier);
            boolean canAfford = faction.factionPoints() >= cost;

            // Real coloured potion icon — drinkable / splash / lingering chosen by duration
            ItemStack item = FactionEffectManager.buildPotionIcon(type, duration);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName((canAfford ? ChatColor.GREEN : ChatColor.RED) + ChatColor.BOLD.toString()
                    + FactionEffectManager.displayName(type) + " " + formatDurationLabel(duration));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Duration: " + ChatColor.WHITE + formatDurationLabel(duration));
            lore.add(ChatColor.GRAY + "Cost: " + ChatColor.GOLD + cost + " coin(s)");
            lore.add(" ");
            if (canAfford) {
                lore.add(ChatColor.GREEN + "✔ Click to purchase (adds to inventory)");
            } else {
                lore.add(ChatColor.RED + "✘ Need " + (cost - faction.factionPoints()) + " more coin(s)");
            }
            meta.setLore(lore);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
                    org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            item.setItemMeta(meta);

            GuiItem gi = new GuiItem(item);
            gi.setAction(e -> {
                e.setCancelled(true);
                if (faction.factionPoints() < cost) {
                    sender.sendRichMessage("<red>Your faction doesn't have enough coins. Need " + cost + ", have " + faction.factionPoints() + ".");
                    return;
                }
                if (!faction.spendFactionPoints(cost)) {
                    sender.sendRichMessage("<red>Failed to spend coins.");
                    return;
                }
                faction.addEffectToInventory(FactionEffectManager.canonicalKey(type), duration, 1);
                sender.sendRichMessage("<green>Bought <yellow>" + FactionEffectManager.displayName(type)
                        + "</yellow> (" + formatDurationLabel(duration) + ") for <gold>" + cost + "</gold> coin(s). Saved to your faction's effect inventory.");
                openEffectsShop(player, sender);
            });
            pane.addItem(gi, cols[i], 1);
        }

        // Back returns to the shop list, not the saved-effects view
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName(ChatColor.GRAY + "← Back");
        back.setItemMeta(bm);
        GuiItem backItem = new GuiItem(back);
        backItem.setAction(e -> {
            e.setCancelled(true);
            openEffectsShop(player, sender);
        });
        pane.addItem(backItem, 0, 2);

        gui.addPane(pane);
        gui.show(player);
    }

    private static String formatDurationLabel(int seconds) {
        if (seconds >= 3600) {
            int h = seconds / 3600;
            int rem = seconds % 3600;
            if (rem == 0) return h + "h";
            return h + "h " + (rem / 60) + "m";
        }
        if (seconds >= 60) return (seconds / 60) + "m";
        return seconds + "s";
    }

    // ── Boosters panel — the faction's stored booster inventory ───────────────

    /**
     * Saved-booster view: shows the faction's stored boosters (earned via level-up / missions).
     * Click an entry to consume it and dispatch the RegentHeadHunting booster command.
     */
    private void openBoostersMenu(Player player, FPlayer sender) {
        Faction faction = sender.faction();

        int rows = 6;
        Component title = Component.text("✦ Saved Boosters", NamedTextColor.AQUA);
        ChestGui gui = new ChestGui(rows, ComponentHolder.of(title));
        gui.setOnGlobalDrag(e -> e.setCancelled(true));
        gui.setOnGlobalClick(e -> e.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, rows);
        ItemStack filler = makeFiller(Material.CYAN_STAINED_GLASS_PANE);
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < rows; y++) {
                pane.addItem(new GuiItem(filler), x, y);
            }
        }

        java.util.Map<String, Integer> inventory = faction.boosterInventory();
        if (inventory.isEmpty()) {
            ItemStack empty = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta em = empty.getItemMeta();
            em.setDisplayName(ChatColor.GRAY + "No saved boosters yet");
            List<String> el = new ArrayList<>();
            el.add(ChatColor.GRAY + "Earn boosters by levelling up your faction");
            el.add(ChatColor.GRAY + "and completing hard faction missions.");
            em.setLore(el);
            empty.setItemMeta(em);
            pane.addItem(new GuiItem(empty), 4, 2);
        } else {
            // Sort deterministically so the GUI doesn't shuffle each open
            java.util.List<java.util.Map.Entry<String, Integer>> entries =
                    new java.util.ArrayList<>(inventory.entrySet());
            entries.sort(java.util.Map.Entry.comparingByKey());

            int slotsPerRow = 9;
            int contentRows = rows - 1;
            int maxSlots = slotsPerRow * contentRows;

            int slotIdx = 0;
            for (java.util.Map.Entry<String, Integer> entry : entries) {
                if (slotIdx >= maxSlots) break;

                // key format: TYPE:MULT_X100:MINUTES
                String[] parts = entry.getKey().split(":");
                if (parts.length != 3) continue;
                dev.kitteh.factions.level.BoosterType type =
                        dev.kitteh.factions.level.BoosterType.byName(parts[0]);
                if (type == null) continue;
                int multX100;
                int minutes;
                try {
                    multX100 = Integer.parseInt(parts[1]);
                    minutes = Integer.parseInt(parts[2]);
                } catch (NumberFormatException ex) {
                    continue;
                }
                int count = entry.getValue();
                String multLabel = dev.kitteh.factions.level.BoosterManager.formatMultiplier(multX100);

                ItemStack item = new ItemStack(type.icon());
                item.setAmount(Math.max(1, Math.min(64, count)));
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + type.displayName() + " Booster");
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Multiplier: " + ChatColor.YELLOW + multLabel);
                lore.add(ChatColor.GRAY + "Duration: " + ChatColor.WHITE + minutes + " minutes");
                lore.add(ChatColor.GRAY + "Saved: " + ChatColor.YELLOW + count);
                lore.add(" ");
                lore.add(ChatColor.GREEN + "▶ Click to activate (consumes 1)");
                meta.setLore(lore);
                item.setItemMeta(meta);

                final dev.kitteh.factions.level.BoosterType finalType = type;
                final int finalMult = multX100;
                final int finalMinutes = minutes;
                GuiItem gi = new GuiItem(item);
                gi.setAction(e -> {
                    e.setCancelled(true);
                    if (!dev.kitteh.factions.level.BoosterManager.activateBooster(
                            faction, finalType, finalMult, finalMinutes)) {
                        sender.sendRichMessage("<red>That booster isn't in your inventory anymore.");
                    }
                    openBoostersMenu(player, sender);
                });

                int col = slotIdx % slotsPerRow;
                int row = slotIdx / slotsPerRow;
                pane.addItem(gi, col, row);
                slotIdx++;
            }
        }

        // Footer: Back + info
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName(ChatColor.GRAY + "← Back");
        back.setItemMeta(bm);
        GuiItem backItem = new GuiItem(back);
        backItem.setAction(e -> {
            e.setCancelled(true);
            openMainMenu(player, sender);
        });
        pane.addItem(backItem, 0, rows - 1);

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName(ChatColor.AQUA + "Faction Boosters");
        List<String> il = new ArrayList<>();
        il.add(ChatColor.GRAY + "Earned from level-ups (1/level, 2/level past 5)");
        il.add(ChatColor.GRAY + "and hard faction missions.");
        il.add(ChatColor.GRAY + "Activating one applies it to your whole faction.");
        im.setLore(il);
        info.setItemMeta(im);
        pane.addItem(new GuiItem(info), 4, rows - 1);

        gui.addPane(pane);
        gui.show(player);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ItemStack makeFiller(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(" ");
        item.setItemMeta(m);
        return item;
    }
}
