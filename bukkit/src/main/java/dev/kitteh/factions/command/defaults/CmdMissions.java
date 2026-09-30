package dev.kitteh.factions.command.defaults;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import dev.kitteh.factions.FPlayer;
import dev.kitteh.factions.FPlayers;
import dev.kitteh.factions.Faction;
import dev.kitteh.factions.command.Cloudy;
import dev.kitteh.factions.command.Cmd;
import dev.kitteh.factions.command.Sender;
import dev.kitteh.factions.mission.MissionCategory;
import dev.kitteh.factions.mission.MissionDefinition;
import dev.kitteh.factions.mission.MissionManager;
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

/**
 * /f missions — Faction missions GUI. Category picker → mission list per category.
 */
public class CmdMissions implements Cmd {

    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        return (manager, builder, help) -> manager.command(
                builder.literal("missions", "mission", "m")
                        .commandDescription(Cloudy.desc("View your faction's missions"))
                        .permission(builder.commandPermission().and(Cloudy.hasFaction()))
                        .handler(this::handle)
        );
    }

    private void handle(CommandContext<Sender> context) {
        if (!(context.sender() instanceof Sender.Player playerSender)) {
            context.sender().sendRichMessage("<red>This command can only be run by a player.");
            return;
        }
        openMainMenu(playerSender.player(), playerSender.fPlayer());
    }

    private void openMainMenu(Player player, FPlayer fPlayer) {
        Faction faction = fPlayer.faction();
        Component title = Component.text("✦ Faction Missions", NamedTextColor.DARK_AQUA);
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

        // Category tiles laid out at columns 1, 3, 5, 7
        pane.addItem(makeCategoryTile(faction, MissionCategory.DAILY, Material.SUNFLOWER,
                ChatColor.YELLOW + "Daily Missions",
                ChatColor.GRAY + "Resets every 24h",
                ChatColor.GRAY + "Resets in: " + ChatColor.WHITE + MissionManager.timeUntilReset(faction, MissionCategory.DAILY)
        ), 1, 1);
        pane.addItem(makeCategoryTile(faction, MissionCategory.BI_DAILY, Material.GLOWSTONE_DUST,
                ChatColor.GOLD + "Bi-Daily Missions",
                ChatColor.GRAY + "Resets every 48h",
                ChatColor.GRAY + "Resets in: " + ChatColor.WHITE + MissionManager.timeUntilReset(faction, MissionCategory.BI_DAILY)
        ), 3, 1);
        pane.addItem(makeCategoryTile(faction, MissionCategory.WEEKLY, Material.BLAZE_POWDER,
                ChatColor.RED + "Weekly Missions",
                ChatColor.GRAY + "Resets every 7d",
                ChatColor.GRAY + "Resets in: " + ChatColor.WHITE + MissionManager.timeUntilReset(faction, MissionCategory.WEEKLY)
        ), 5, 1);
        pane.addItem(makeCategoryTile(faction, MissionCategory.MAP, Material.DRAGON_EGG,
                ChatColor.DARK_PURPLE + "Map Missions",
                ChatColor.GRAY + "Permanent — never reset"
        ), 7, 1);

        gui.addPane(pane);
        gui.show(player);
    }

    private GuiItem makeCategoryTile(Faction faction, MissionCategory category, Material mat, String... loreLines) {
        List<MissionDefinition> missions = MissionManager.activeMissions(faction, category);
        long completed = missions.stream().filter(m -> MissionManager.isCompleted(faction, m)).count();

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(loreLines[0]);
        List<String> lore = new ArrayList<>();
        for (int i = 1; i < loreLines.length; i++) lore.add(loreLines[i]);
        lore.add(" ");
        lore.add(ChatColor.GRAY + "Completed: " + ChatColor.GREEN + completed + "/" + missions.size());
        meta.setLore(lore);
        item.setItemMeta(meta);

        GuiItem gi = new GuiItem(item);
        gi.setAction(e -> {
            e.setCancelled(true);
            Player player = (Player) e.getWhoClicked();
            FPlayer fp = FPlayers.fPlayers().get(player);
            openCategoryView(player, fp, category);
        });
        return gi;
    }

    private void openCategoryView(Player player, FPlayer fPlayer, MissionCategory category) {
        Faction faction = fPlayer.faction();
        List<MissionDefinition> missions = MissionManager.activeMissions(faction, category);

        String catName = switch (category) {
            case DAILY -> ChatColor.YELLOW + "Daily Missions";
            case BI_DAILY -> ChatColor.GOLD + "Bi-Daily Missions";
            case WEEKLY -> ChatColor.RED + "Weekly Missions";
            case MAP -> ChatColor.DARK_PURPLE + "Map Missions";
        };

        int rows = Math.max(3, Math.min(6, (int) Math.ceil(missions.size() / 7.0) + 2));
        ChestGui gui = new ChestGui(rows, catName);
        gui.setOnGlobalDrag(e -> e.setCancelled(true));
        gui.setOnGlobalClick(e -> e.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, rows);
        ItemStack filler = makeFiller();
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < rows; y++) {
                pane.addItem(new GuiItem(filler), x, y);
            }
        }

        // Place mission tiles in a grid (cols 1,3,5,7 starting at row 1)
        int col = 1;
        int row = 1;
        for (MissionDefinition mission : missions) {
            pane.addItem(makeMissionItem(faction, mission), col, row);
            col += 2;
            if (col > 7) {
                col = 1;
                row++;
                if (row >= rows - 1) break;
            }
        }

        // Footer: back button + reset timer info
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName(ChatColor.GRAY + "← Back");
        back.setItemMeta(bm);
        GuiItem backItem = new GuiItem(back);
        backItem.setAction(e -> {
            e.setCancelled(true);
            openMainMenu(player, fPlayer);
        });
        pane.addItem(backItem, 0, rows - 1);

        ItemStack timer = new ItemStack(Material.CLOCK);
        ItemMeta tm = timer.getItemMeta();
        tm.setDisplayName(ChatColor.AQUA + "Resets in: " + ChatColor.WHITE
                + MissionManager.timeUntilReset(faction, category));
        timer.setItemMeta(tm);
        pane.addItem(new GuiItem(timer), 4, rows - 1);

        gui.addPane(pane);
        gui.show(player);
    }

    private GuiItem makeMissionItem(Faction faction, MissionDefinition mission) {
        long progress = MissionManager.progress(faction, mission);
        boolean done = progress >= mission.requiredAmount();

        Material mat = done ? Material.LIME_STAINED_GLASS_PANE : progressMaterial(mission.difficulty());

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        String status = done ? ChatColor.GREEN + "✔ COMPLETE" : ChatColor.YELLOW + "In Progress";
        meta.setDisplayName(ChatColor.WHITE + "" + ChatColor.BOLD + mission.displayName() + "  " + status);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + mission.description());
        lore.add(" ");
        int barLength = 20;
        long pct = mission.requiredAmount() == 0 ? barLength
                : (long) ((double) progress / mission.requiredAmount() * barLength);
        StringBuilder bar = new StringBuilder();
        bar.append(ChatColor.GREEN);
        for (int i = 0; i < pct; i++) bar.append("█");
        bar.append(ChatColor.DARK_GRAY);
        for (int i = 0; i < barLength - pct; i++) bar.append("█");
        lore.add(bar.toString() + " " + ChatColor.WHITE + progress + "/" + mission.requiredAmount());
        lore.add(" ");
        lore.add(ChatColor.GRAY + "Difficulty: " + difficultyColor(mission.difficulty()) + mission.difficultyLabel());
        lore.add(ChatColor.GRAY + "XP Reward: " + ChatColor.AQUA + mission.xpReward());
        if (mission.moneyReward() > 0) {
            lore.add(ChatColor.GRAY + "Money Reward: " + ChatColor.GREEN + "$" + (long) mission.moneyReward());
        }
        if (mission.coinReward() > 0) {
            lore.add(ChatColor.GRAY + "Coin Reward: " + ChatColor.LIGHT_PURPLE + mission.coinReward() + " ✦");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);

        return new GuiItem(item, e -> e.setCancelled(true));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Material progressMaterial(int difficulty) {
        return switch (difficulty) {
            case 0 -> Material.PAPER;
            case 1 -> Material.BOOK;
            case 2 -> Material.ENCHANTED_BOOK;
            default -> Material.PAPER;
        };
    }

    private static String difficultyColor(int difficulty) {
        return switch (difficulty) {
            case 0 -> ChatColor.GREEN.toString();
            case 1 -> ChatColor.YELLOW.toString();
            case 2 -> ChatColor.RED.toString();
            default -> ChatColor.WHITE.toString();
        };
    }

    private static ItemStack makeFiller() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(" ");
        item.setItemMeta(m);
        return item;
    }
}
