package dev.kitteh.factions.listener;

import dev.kitteh.factions.Board;
import dev.kitteh.factions.FLocation;
import dev.kitteh.factions.Faction;
import dev.kitteh.factions.FactionsPlugin;
import dev.kitteh.factions.command.defaults.CmdUpgrades;
import dev.kitteh.factions.plugin.AbstractFactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Crop Growth upgrade — when a crop in faction territory naturally grows, roll the upgrade's
 * bonus percent; if it hits, advance the crop one extra age stage on the next tick.
 */
public class ListenCropGrowth implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        Block block = event.getBlock();
        Faction faction = Board.board().factionAt(new FLocation(block.getLocation()));
        if (faction == null || !faction.isNormal()) return;

        int tier = faction.customUpgradeTier(CmdUpgrades.KEY_CROP_GROWTH);
        if (tier <= 0) return;

        int bonusPercent = FactionsPlugin.instance().configManager().factionLevelsConfig().cropGrowthPercentForTier(tier);
        if (bonusPercent <= 0) return;

        if (ThreadLocalRandom.current().nextInt(100) >= bonusPercent) return;

        // Schedule a bonus growth tick on the next server tick — bumps the crop's age by 1 if not maxed.
        Bukkit.getScheduler().runTask(AbstractFactionsPlugin.instance(), () -> {
            if (!block.getChunk().isLoaded()) return;
            BlockState state = block.getState();
            if (state.getBlockData() instanceof Ageable ageable) {
                int max = ageable.getMaximumAge();
                int age = ageable.getAge();
                if (age < max) {
                    ageable.setAge(Math.min(max, age + 1));
                    state.setBlockData(ageable);
                    state.update(true, false);
                }
            }
        });
    }
}
