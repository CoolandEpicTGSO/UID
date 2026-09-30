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
import dev.kitteh.factions.level.FactionLevelsConfig;
import dev.kitteh.factions.permissible.PermissibleActions;
import dev.kitteh.factions.util.Permission;
import dev.kitteh.factions.util.TriConsumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;

import java.util.ArrayList;
import java.util.List;

public class CmdUpgrades implements Cmd {

    public static final String KEY_PV = "pv";
    public static final String KEY_WARPS = "warps";
    public static final String KEY_WARP_TIME = "warp_time";
    public static final String KEY_HH_XP_BOOST = "hh_xp_boost";
    public static final String KEY_TNT_BANK = "tnt_bank";
    public static final String KEY_BANNER = "banner";
    public static final String KEY_CROP_GROWTH = "crop_growth";
    public static final String KEY_BANK_CAP = "bank_cap";
    public static final String KEY_EFFECTS = "effects";

    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        return (manager, builder, help) -> {
            var tl = FactionsPlugin.instance().tl().commands().upgrades();
            manager.command(
                    builder.literal(tl.getFirstAlias(), tl.getSecondaryAliases())
                            .permission(builder.commandPermission().and(Cloudy.hasPermission(Permission.UPGRADES).and(Cloudy.hasFaction())))
                            .commandDescription(Cloudy.desc(tl.getDescription()))
                            .handler(this::handle)
            );
        };
    }

    private void handle(CommandContext<Sender> context) {
        FPlayer sender = ((Sender.Player) context.sender()).fPlayer();
        Player player = ((Sender.Player) context.sender()).player();
        openMainGui(player, sender);
    }

    void openMainGui(Player player, FPlayer fPlayer) {
        Faction faction = fPlayer.faction();
        FactionLevelsConfig cfg = FactionsPlugin.instance().configManager().factionLevelsConfig();

        Component title = Component.text("✦ F Upgrades  ", NamedTextColor.DARK_PURPLE)
                .append(Component.text(faction.factionPoints() + " coins", NamedTextColor.GOLD));

        // 5 rows: row 0 glass header, row 1 = first 7 upgrades (glass at ends),
        // row 2 = remaining 2 upgrades (glass start), row 3 glass, row 4 footer.
        ChestGui gui = new ChestGui(5, ComponentHolder.of(title));
        gui.setOnGlobalDrag(e -> e.setCancelled(true));
        gui.setOnGlobalClick(e -> e.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 5);
        fillBackground(pane, 9, 5);

        // Feature line 1 (y=1): glass at x=0, seven upgrades x=1..7, glass at x=8
        String[] line1 = {KEY_PV, KEY_WARPS, KEY_WARP_TIME, KEY_HH_XP_BOOST,
                          KEY_TNT_BANK, KEY_BANNER, KEY_CROP_GROWTH};
        for (int i = 0; i < line1.length; i++) {
            pane.addItem(buildClickableItem(player, fPlayer, line1[i], cfg, gui), i + 1, 1);
        }

        // Feature line 2 (y=2): glass at x=0, remaining upgrades from x=1
        String[] line2 = {KEY_BANK_CAP, KEY_EFFECTS};
        for (int i = 0; i < line2.length; i++) {
            pane.addItem(buildClickableItem(player, fPlayer, line2[i], cfg, gui), i + 1, 2);
        }

        // Footer (y=4): close button centred (x=4), coins display bottom-right (x=8)
        pane.addItem(buildCloseButton(player), 4, 4);
        pane.addItem(buildCoinsDisplay(faction, cfg), 8, 4);

        gui.addPane(pane);
        gui.show(player);
    }

    private GuiItem buildClickableItem(Player player, FPlayer fPlayer, String key,
                                        FactionLevelsConfig cfg, ChestGui parentGui) {
        Faction faction = fPlayer.faction();
        FactionLevelsConfig.UpgradeTierConfig tierCfg = getTierConfig(cfg, key);
        int currentTier = faction.customUpgradeTier(key);
        int maxTier = tierCfg.getTiers();
        boolean maxed = currentTier >= maxTier;
        int nextCost = maxed ? 0 : tierCfg.costForTier(currentTier + 1);
        boolean canAfford = !maxed && faction.factionPoints() >= nextCost;

        ItemStack stack = buildItemStack(faction, key, cfg, currentTier, maxTier, maxed, nextCost, canAfford);
        GuiItem item = new GuiItem(stack);

        item.setAction(e -> {
            e.setCancelled(true);
            if (maxed) {
                fPlayer.sendRichMessage("<gold>This upgrade is already at max tier!");
                return;
            }
            if (!faction.hasAccess(fPlayer, PermissibleActions.UPGRADE, null)) {
                fPlayer.sendRichMessage("<red>You don't have permission to purchase upgrades.");
                return;
            }
            openConfirmGui(player, fPlayer, key, cfg, parentGui);
        });

        return item;
    }

    private void openConfirmGui(Player player, FPlayer fPlayer, String key,
                                 FactionLevelsConfig cfg, ChestGui returnGui) {
        Faction faction = fPlayer.faction();
        FactionLevelsConfig.UpgradeTierConfig tierCfg = getTierConfig(cfg, key);
        int currentTier = faction.customUpgradeTier(key);
        int nextTier = currentTier + 1;
        int cost = tierCfg.costForTier(nextTier);

        ChestGui buyGui = new ChestGui(3, ChatColor.DARK_PURPLE + "Purchase " + upgradeDisplayName(key) + " T" + nextTier + "?");
        buyGui.setOnGlobalDrag(ee -> ee.setCancelled(true));
        buyGui.setOnGlobalClick(ee -> ee.setCancelled(true));

        StaticPane buyPane = new StaticPane(0, 0, 9, 3);
        fillBackground(buyPane, 9, 3);

        // Info display item
        FactionLevelsConfig.UpgradeTierConfig tc = getTierConfig(cfg, key);
        boolean maxed = currentTier >= tc.getTiers();
        int nCost = maxed ? 0 : tc.costForTier(currentTier + 1);
        boolean canAff = !maxed && faction.factionPoints() >= nCost;
        buyPane.addItem(new GuiItem(buildItemStack(faction, key, cfg, currentTier, tc.getTiers(), maxed, nCost, canAff)), 4, 1);

        // Buy button
        boolean canAfford = faction.factionPoints() >= cost;
        ItemStack buyStack = new ItemStack(canAfford ? Material.GREEN_CONCRETE : Material.RED_CONCRETE);
        ItemMeta buyMeta = buyStack.getItemMeta();
        if (canAfford) {
            buyMeta.setDisplayName(ChatColor.GREEN + "Buy Tier " + nextTier + " for " + cost + " coin(s)");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Your coins: " + ChatColor.GOLD + faction.factionPoints());
            lore.add(ChatColor.GRAY + "After purchase: " + ChatColor.GOLD + (faction.factionPoints() - cost));
            lore.add(ChatColor.GRAY + "Unlocks: " + ChatColor.WHITE + upgradeNextValue(key, cfg, nextTier));
            buyMeta.setLore(lore);
        } else {
            buyMeta.setDisplayName(ChatColor.RED + "Not enough coins!");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Cost: " + ChatColor.GOLD + cost + " coins");
            lore.add(ChatColor.GRAY + "You have: " + ChatColor.RED + faction.factionPoints() + " coins");
            lore.add(ChatColor.GRAY + "Need: " + ChatColor.RED + (cost - faction.factionPoints()) + " more");
            buyMeta.setLore(lore);
        }
        buyStack.setItemMeta(buyMeta);

        GuiItem buyItem = new GuiItem(buyStack);
        buyItem.setAction(ee -> {
            ee.setCancelled(true);
            if (!canAfford) {
                fPlayer.sendRichMessage("<red>Not enough faction coins. Earn more by levelling up!");
                return;
            }
            // Faction-level prerequisite for the F Effects unlock
            if (KEY_EFFECTS.equals(key)) {
                int required = cfg.getEffectsRequiredHhLevel();
                if (required > 0) {
                    int factionLevel = faction.factionLevel();
                    if (factionLevel < required) {
                        fPlayer.sendRichMessage("<red>Your faction must be <yellow>Level " + required + "</yellow> to unlock F Effects. (Currently: " + factionLevel + ")");
                        return;
                    }
                }
            }
            if (faction.spendFactionPoints(cost)) {
                faction.customUpgradeTier(key, nextTier);
                fPlayer.sendRichMessage("<green>Upgraded <gold>" + upgradeDisplayName(key) + "</gold> to Tier " + nextTier + "! <gray>(-" + cost + " coins)");
                openMainGui(player, fPlayer);
            }
        });
        buyPane.addItem(buyItem, 2, 1);

        // Cancel button
        ItemStack cancelStack = new ItemStack(Material.BARRIER);
        ItemMeta cancelMeta = cancelStack.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.RED + "Cancel");
        cancelStack.setItemMeta(cancelMeta);
        GuiItem cancelItem = new GuiItem(cancelStack);
        cancelItem.setAction(ee -> {
            ee.setCancelled(true);
            returnGui.show(player);
        });
        buyPane.addItem(cancelItem, 6, 1);

        buyGui.addPane(buyPane);
        buyGui.show(player);
    }

    // ── Item builders ────────────────────────────────────────────────────────

    private static Material upgradeBaseMaterial(String key) {
        return switch (key) {
            case KEY_PV -> Material.CHEST;
            case KEY_WARPS -> Material.ENDER_EYE;
            case KEY_WARP_TIME -> Material.SUGAR;
            case KEY_HH_XP_BOOST -> Material.CLOCK;
            case KEY_TNT_BANK -> Material.TNT;
            case KEY_BANNER -> Material.BLACK_BANNER;
            case KEY_CROP_GROWTH -> Material.WHEAT;
            case KEY_BANK_CAP -> Material.GOLD_INGOT;
            case KEY_EFFECTS -> Material.SPLASH_POTION;
            default -> Material.PAPER;
        };
    }

    private ItemStack buildItemStack(Faction faction, String key, FactionLevelsConfig cfg,
                                      int currentTier, int maxTier, boolean maxed,
                                      int nextCost, boolean canAfford) {
        // Use the thematic material; overlay emerald/gold border via name color instead
        Material mat = upgradeBaseMaterial(key);

        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();

        String tierDisplay = maxed ? ChatColor.GREEN + "MAX" : ChatColor.YELLOW + "" + currentTier + "/" + maxTier;
        meta.setDisplayName(ChatColor.WHITE + "" + ChatColor.BOLD + upgradeDisplayName(key) + "  " + tierDisplay);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + upgradeDescription(key));
        lore.add(" ");
        lore.add(ChatColor.AQUA + "Current: " + ChatColor.WHITE + upgradeCurrentValue(faction, key, cfg));

        if (!maxed) {
            lore.add(ChatColor.AQUA + "Next tier: " + ChatColor.WHITE + upgradeNextValue(key, cfg, currentTier + 1));
            lore.add(" ");
            lore.add(ChatColor.GRAY + "Cost: " + ChatColor.GOLD + nextCost + " coin(s)");
            if (canAfford) {
                lore.add(ChatColor.GREEN + "✔ Click to purchase");
            } else {
                lore.add(ChatColor.RED + "✘ Need " + (nextCost - faction.factionPoints()) + " more coins");
            }
        } else {
            lore.add(" ");
            lore.add(ChatColor.GREEN + "✔ Max tier reached!");
        }

        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private GuiItem buildCoinsDisplay(Faction faction, FactionLevelsConfig cfg) {
        ItemStack stack = new ItemStack(Material.SUNFLOWER);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "✦ Faction Coins: " + ChatColor.YELLOW + faction.factionPoints());
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Level: " + ChatColor.WHITE + faction.factionLevel() + " / " + cfg.maxLevel());
        lore.add(ChatColor.GRAY + "Earn coins by levelling up your faction.");
        lore.add(ChatColor.GRAY + "Use " + ChatColor.WHITE + "/f level" + ChatColor.GRAY + " to view XP progress.");
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return new GuiItem(stack, e -> e.setCancelled(true));
    }

    private GuiItem buildCloseButton(Player player) {
        ItemStack stack = new ItemStack(Material.BARRIER);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Close");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Click to close this menu.");
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return new GuiItem(stack, e -> {
            e.setCancelled(true);
            player.closeInventory();
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void fillBackground(StaticPane pane, int cols, int rows) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gm = glass.getItemMeta();
        gm.setDisplayName(" ");
        glass.setItemMeta(gm);
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                pane.addItem(new GuiItem(glass.clone()), x, y);
            }
        }
    }

    private FactionLevelsConfig.UpgradeTierConfig getTierConfig(FactionLevelsConfig cfg, String key) {
        return switch (key) {
            case KEY_PV -> cfg.getPvUpgrade();
            case KEY_WARPS -> cfg.getWarpsUpgrade();
            case KEY_WARP_TIME -> cfg.getWarpTimeUpgrade();
            case KEY_HH_XP_BOOST -> cfg.getHhXpBoostUpgrade();
            case KEY_TNT_BANK -> cfg.getTntBankUpgrade();
            case KEY_BANNER -> cfg.getBannerUpgrade();
            case KEY_CROP_GROWTH -> cfg.getCropGrowthUpgrade();
            case KEY_BANK_CAP -> cfg.getBankCapUpgrade();
            case KEY_EFFECTS -> cfg.getEffectsUpgrade();
            default -> cfg.getPvUpgrade();
        };
    }

    private String upgradeDisplayName(String key) {
        return switch (key) {
            case KEY_PV -> "Faction PV";
            case KEY_WARPS -> "Faction Warps";
            case KEY_WARP_TIME -> "Warp Speed";
            case KEY_HH_XP_BOOST -> "HH XP Boost";
            case KEY_TNT_BANK -> "TNT Bank";
            case KEY_BANNER -> "F Banner";
            case KEY_CROP_GROWTH -> "Crop Growth";
            case KEY_BANK_CAP -> "Bank Capacity";
            case KEY_EFFECTS -> "F Effects";
            default -> key;
        };
    }

    private String upgradeDescription(String key) {
        return switch (key) {
            case KEY_PV -> "Faction shared private vault. Each tier adds 9 slots.";
            case KEY_WARPS -> "Increase max faction warps. Each tier adds more warps.";
            case KEY_WARP_TIME -> "Reduce /f warp teleport delay by 2s per tier.";
            case KEY_HH_XP_BOOST -> "Boost headhunting XP earned by faction members.";
            case KEY_TNT_BANK -> "Increase the maximum TNT bank capacity.";
            case KEY_BANNER -> "Unlock /f banner — deploy a 30s faction rally point.";
            case KEY_CROP_GROWTH -> "Bonus chance for crops in your claims to grow each tick.";
            case KEY_BANK_CAP -> "Increase the maximum money your faction bank can hold.";
            case KEY_EFFECTS -> "Unlock /f effects — faction-wide potion effects. Requires Faction Level "
                    + FactionsPlugin.instance().configManager().factionLevelsConfig().getEffectsRequiredHhLevel() + " to purchase.";
            default -> "";
        };
    }

    private static String formatMoney(double amount) {
        if (amount >= 1_000_000) return "$" + (long) (amount / 1_000_000) + "M";
        if (amount >= 1_000) return "$" + (long) (amount / 1_000) + "K";
        return "$" + (long) amount;
    }

    private String upgradeCurrentValue(Faction faction, String key, FactionLevelsConfig cfg) {
        int tier = faction.customUpgradeTier(key);
        return switch (key) {
            case KEY_PV -> {
                int slots = cfg.getPvBaseSlots() + tier * cfg.getPvSlotsPerTier();
                yield slots == 0 ? "No PV" : slots + " slots";
            }
            case KEY_WARPS -> {
                int warps = cfg.getBaseMaxWarps() + tier * cfg.getWarpsPerTier();
                yield warps + " warps";
            }
            case KEY_WARP_TIME -> {
                int delay = Math.max(0, cfg.getBaseWarpDelaySeconds() - tier * cfg.getWarpDelayReductionPerTier());
                yield delay + "s delay";
            }
            case KEY_HH_XP_BOOST -> tier == 0 ? "No boost" : "Tier " + tier + " boost";
            case KEY_TNT_BANK -> cfg.tntBankCapacityForTier(tier) + " TNT";
            case KEY_BANNER -> tier == 0 ? "Locked" : "Unlocked";
            case KEY_CROP_GROWTH -> "+" + cfg.cropGrowthPercentForTier(tier) + "% growth chance";
            case KEY_BANK_CAP -> formatMoney(cfg.bankCapForTier(tier)) + " cap";
            case KEY_EFFECTS -> tier == 0 ? "Locked" : "Unlocked";
            default -> "Tier " + tier;
        };
    }

    private String upgradeNextValue(String key, FactionLevelsConfig cfg, int nextTier) {
        return switch (key) {
            case KEY_PV -> {
                int slots = cfg.getPvBaseSlots() + nextTier * cfg.getPvSlotsPerTier();
                yield slots + " slots";
            }
            case KEY_WARPS -> {
                int warps = cfg.getBaseMaxWarps() + nextTier * cfg.getWarpsPerTier();
                yield warps + " warps";
            }
            case KEY_WARP_TIME -> {
                int delay = Math.max(0, cfg.getBaseWarpDelaySeconds() - nextTier * cfg.getWarpDelayReductionPerTier());
                yield delay + "s delay";
            }
            case KEY_HH_XP_BOOST -> "Tier " + nextTier + " boost";
            case KEY_TNT_BANK -> cfg.tntBankCapacityForTier(nextTier) + " TNT";
            case KEY_BANNER -> "Unlocked";
            case KEY_CROP_GROWTH -> "+" + cfg.cropGrowthPercentForTier(nextTier) + "% growth chance";
            case KEY_BANK_CAP -> formatMoney(cfg.bankCapForTier(nextTier)) + " cap";
            case KEY_EFFECTS -> "Unlocked";
            default -> "Tier " + nextTier;
        };
    }
}
