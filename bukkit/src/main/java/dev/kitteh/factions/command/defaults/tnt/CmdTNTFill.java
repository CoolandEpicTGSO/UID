package dev.kitteh.factions.command.defaults.tnt;

import dev.kitteh.factions.Board;
import dev.kitteh.factions.FLocation;
import dev.kitteh.factions.FPlayer;
import dev.kitteh.factions.Faction;
import dev.kitteh.factions.FactionsPlugin;
import dev.kitteh.factions.command.Cloudy;
import dev.kitteh.factions.command.Cmd;
import dev.kitteh.factions.command.Sender;
import dev.kitteh.factions.permissible.PermissibleActions;
import dev.kitteh.factions.util.Pair;
import dev.kitteh.factions.util.Permission;
import dev.kitteh.factions.util.TL;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Dispenser;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.IntegerParser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import dev.kitteh.factions.util.TriConsumer;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;
import java.util.stream.Collectors;

public class CmdTNTFill implements Cmd {
    @Override
    public TriConsumer<CommandManager<Sender>, Command.Builder<Sender>, MinecraftHelp<Sender>> consumer() {
        return (manager, builder, help) -> manager.command(
                builder.literal("fill")
                        .commandDescription(Cloudy.desc(TL.COMMAND_TNT_FILL_DESCRIPTION))
                        .permission(
                                builder.commandPermission()
                                        .and(Cloudy.hasPermission(Permission.TNT_FILL))
                                        .and(Cloudy.hasSelfFactionPerms(PermissibleActions.TNTWITHDRAW))
                        )
                        .required("radius", IntegerParser.integerParser(1))
                        .required("amount", IntegerParser.integerParser(1))
                        .handler(this::handle)
        );
    }

    private void handle(CommandContext<Sender> context) {
        FPlayer sender = ((Sender.Player) context.sender()).fPlayer();
        Player player = ((Sender.Player) context.sender()).player();
        Faction faction = sender.faction();

        if (!faction.equals(Board.board().factionAt(new FLocation(player.getLocation())))) {
            sender.msgLegacy(TL.COMMAND_TNT_TERRITORYONLY);
            return;
        }
        final int radius = context.get("radius");
        // 'perDispenser' is now the amount of TNT to put into EACH dispenser, not a shared total.
        final int perDispenser = context.get("amount");

        if (radius <= 0 || perDispenser <= 0) {
            sender.msgLegacy(TL.COMMAND_TNT_FILL_FAIL_POSITIVE);
            return;
        }

        if (radius > FactionsPlugin.instance().conf().commands().tnt().getMaxRadius()) {
            sender.msgLegacy(TL.COMMAND_TNT_FILL_FAIL_MAXRADIUS, radius, FactionsPlugin.instance().conf().commands().tnt().getMaxRadius());
            return;
        }

        List<Dispenser> list = getDispensers(player.getLocation(), radius, faction);
        if (list.isEmpty()) {
            sender.msgLegacy(TL.COMMAND_TNT_FILL_MESSAGE, 0, 0, faction.tntBank());
            return;
        }

        // Each dispenser wants up to 'perDispenser' TNT — total demand is that times the count.
        long demand = (long) perDispenser * list.size();
        if (faction.tntBank() <= 0) {
            sender.msgLegacy(TL.COMMAND_TNT_FILL_FAIL_NOTENOUGH, perDispenser);
            return;
        }
        // Cap total spend at what the bank actually holds.
        int budget = (int) Math.min(demand, faction.tntBank());

        int deposited = 0;
        int dispenserCount = 0;
        for (Dispenser dispenser : list) {
            if (budget <= 0) break;
            int give = Math.min(perDispenser, budget);
            int left = getCount(dispenser.getInventory().addItem(getStacks(give)).values());
            int placed = give - left;
            if (placed > 0) {
                deposited += placed;
                dispenserCount++;
                budget -= placed;
            }
        }

        faction.tntBank(faction.tntBank() - deposited);

        sender.msgLegacy(TL.COMMAND_TNT_FILL_MESSAGE, deposited, dispenserCount, faction.tntBank());
    }

    static ItemStack[] getStacks(int count) {
        if (count < 65) {
            return new ItemStack[]{new ItemStack(Material.TNT, count)};
        } else {
            List<ItemStack> stack = new ArrayList<>();
            while (count > 0) {
                stack.add(new ItemStack(Material.TNT, Math.min(64, count)));
                count -= Math.min(64, count);
            }
            return stack.toArray(new ItemStack[0]);
        }
    }

    static List<Dispenser> getDispensers(Location location, int radius, Faction faction) {
        List<Pair<Dispenser, Double>> list = new ArrayList<>();
        for (int x = location.getBlockX() - radius; x <= location.getBlockX() + radius; x++) {
            for (int y = location.getBlockY() - radius; y <= location.getBlockY() + radius; y++) {
                for (int z = location.getBlockZ() - radius; z <= location.getBlockZ() + radius; z++) {
                    Block block = location.getWorld().getBlockAt(x, y, z);
                    if (Board.board().factionAt(new FLocation(block)) != faction) {
                        continue;
                    }
                    if (block.getType() == Material.DISPENSER) {
                        list.add(Pair.of((Dispenser) block.getState(), Math.sqrt(((location.getBlockX() - x) ^ 2) + ((location.getBlockY() - y) ^ 2) + ((location.getBlockZ() - z) ^ 2))));
                    }
                }
            }
        }
        list.sort(Comparator.comparing(Pair::getRight));
        return list.stream().map(Pair::getLeft).collect(Collectors.toCollection(ArrayList::new));
    }

    static int getCount(Collection<? extends ItemStack> items) {
        int count = 0;
        for (ItemStack stack : items) {
            count += stack.getAmount();
        }
        return count;
    }
}
