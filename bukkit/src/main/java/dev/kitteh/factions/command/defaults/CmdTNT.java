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
import dev.kitteh.factions.command.defaults.tnt.CmdTNTDeposit;
import dev.kitteh.factions.command.defaults.tnt.CmdTNTFill;
import dev.kitteh.factions.command.defaults.tnt.CmdTNTSiphon;
import dev.kitteh.factions.command.defaults.tnt.CmdTNTWithdraw;
import dev.kitteh.factions.level.FactionLevelsConfig;
import dev.kitteh.factions.util.Permission;
import dev.kitteh.factions.util.TL;
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

public class CmdTNT implements Cmd {
    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        return (manager, builder, help) -> {
            Command.Builder<Sender> tntBuilder = builder.literal("tnt")
                    .commandDescription(Cloudy.desc(TL.COMMAND_TNT_INFO_DESCRIPTION))
                    .permission(builder.commandPermission().and(Cloudy.hasFaction().and(Cloudy.predicate(s -> FactionsPlugin.instance().conf().commands().tnt().isEnable()))));

            manager.command(tntBuilder.permission(tntBuilder.commandPermission().and(Cloudy.hasPermission(Permission.TNT_INFO))).handler(this::handle));
            // `/f tnt info` duplicates bare `/f tnt` — kept for back-compat but hidden from help.
            manager.command(tntBuilder.literal("info").meta(HIDE_IN_HELP, true).permission(tntBuilder.commandPermission().and(Cloudy.hasPermission(Permission.TNT_INFO))).handler(this::handle));

            new CmdTNTDeposit().consumer().accept(manager, tntBuilder, help);
            new CmdTNTFill().consumer().accept(manager, tntBuilder, help);
            new CmdTNTSiphon().consumer().accept(manager, tntBuilder, help);
            new CmdTNTWithdraw().consumer().accept(manager, tntBuilder, help);
        };
    }

    private void handle(CommandContext<Sender> context) {
        if (context.sender() instanceof Sender.Player playerSender) {
            openTntGui(playerSender.player(), playerSender.fPlayer());
        } else {
            context.sender().msgLegacy(TL.COMMAND_TNT_INFO_MESSAGE, ((Sender.Player) context.sender()).faction().tntBank());
        }
    }

    private void openTntGui(Player player, FPlayer fPlayer) {
        Faction faction = fPlayer.faction();
        Component title = Component.text("✦ Faction TNT Bank", NamedTextColor.RED);
        ChestGui gui = new ChestGui(3, ComponentHolder.of(title));
        gui.setOnGlobalDrag(e -> e.setCancelled(true));
        gui.setOnGlobalClick(e -> e.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 3);
        ItemStack filler = makeFiller();
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 3; y++) {
                pane.addItem(new GuiItem(filler), x, y);
            }
        }

        pane.addItem(buildTntItem(player, fPlayer, gui), 4, 1);

        // Optional info badge for the upgrade tier
        FactionLevelsConfig cfg = FactionsPlugin.instance().configManager().factionLevelsConfig();
        int tier = faction.customUpgradeTier(CmdUpgrades.KEY_TNT_BANK);
        int maxTier = cfg.getTntBankUpgrade().getTiers();

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName(ChatColor.GOLD + "Bank Upgrade Tier: " + ChatColor.YELLOW + tier + "/" + maxTier);
        List<String> il = new ArrayList<>();
        il.add(ChatColor.GRAY + "Capacity: " + ChatColor.WHITE + faction.tntBankMax());
        il.add(ChatColor.GRAY + "Upgrade with " + ChatColor.WHITE + "/f upgrades");
        im.setLore(il);
        info.setItemMeta(im);
        pane.addItem(new GuiItem(info), 7, 1);

        gui.addPane(pane);
        gui.show(player);
    }

    private GuiItem buildTntItem(Player player, FPlayer fPlayer, ChestGui parentGui) {
        Faction faction = fPlayer.faction();
        int balance = faction.tntBank();
        int max = faction.tntBankMax();

        // Stack size shows balance up to 64 (cosmetic — clamped because Bukkit caps stack size)
        ItemStack tnt = new ItemStack(Material.TNT, Math.max(1, Math.min(64, balance)));
        ItemMeta meta = tnt.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "TNT Bank: " + ChatColor.WHITE + balance + ChatColor.GRAY + " / " + ChatColor.WHITE + max);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Stored TNT for your faction.");
        lore.add(" ");
        lore.add(ChatColor.YELLOW + "▶ Right-click " + ChatColor.GRAY + "to deposit ALL TNT from your inventory");
        lore.add(ChatColor.GRAY + "    (must be standing in your own territory)");
        lore.add(" ");
        lore.add(ChatColor.GREEN + "Commands:");
        lore.add(ChatColor.WHITE + "  /f tnt deposit [amount]");
        lore.add(ChatColor.WHITE + "  /f tnt withdraw <amount>");
        lore.add(ChatColor.WHITE + "  /f tnt fill <amount> <radius>");
        lore.add(ChatColor.WHITE + "  /f tnt siphon <radius>");
        meta.setLore(lore);
        tnt.setItemMeta(meta);

        GuiItem item = new GuiItem(tnt);
        item.setAction(event -> {
            event.setCancelled(true);
            if (!event.isRightClick()) return;

            int deposited = CmdTNTDeposit.depositTnt(fPlayer, player, Integer.MAX_VALUE);
            // Refresh GUI to show new balance
            if (deposited > 0) {
                player.closeInventory();
                openTntGui(player, fPlayer);
            }
        });

        return item;
    }

    private ItemStack makeFiller() {
        ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(" ");
        item.setItemMeta(m);
        return item;
    }
}
