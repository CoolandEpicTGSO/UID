package dev.kitteh.factions.level;

import dev.kitteh.factions.Faction;
import dev.kitteh.factions.FactionsPlugin;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

public final class FactionEffectManager {

    private FactionEffectManager() {}

    /**
     * Returns all potion effect types unlocked for the faction's current level.
     */
    public static List<PotionEffectType> unlockedEffects(Faction faction) {
        FactionLevelsConfig cfg = FactionsPlugin.instance().configManager().factionLevelsConfig();
        List<PotionEffectType> effects = new ArrayList<>();
        for (int lvl = 1; lvl <= faction.factionLevel(); lvl++) {
            for (String name : cfg.effectUnlocks(lvl)) {
                PotionEffectType type = byKey(name);
                if (type != null) effects.add(type);
            }
        }
        return effects;
    }

    /**
     * Legacy: activates an unlocked effect using the old global cooldown + fixed duration.
     * Kept for backwards compatibility; prefer {@link #applyEffect(Faction, PotionEffectType, int)}.
     */
    public static boolean activateEffect(Faction faction, PotionEffectType type) {
        if (faction.effectOnCooldown()) return false;
        if (!unlockedEffects(faction).contains(type)) return false;
        FactionLevelsConfig cfg = FactionsPlugin.instance().configManager().factionLevelsConfig();
        applyEffect(faction, type, cfg.getEffectDurationSeconds());
        long cooldownMs = (long) cfg.getEffectCooldownSeconds() * 1000L;
        faction.effectCooldownEnd(System.currentTimeMillis() + cooldownMs);
        return true;
    }

    /**
     * Apply a potion effect to all online faction members for a given duration,
     * using the vanilla-max amplifier for that effect (e.g. Speed II = amp 1).
     * Does NOT check unlock state, coin cost, or cooldown — that's the caller's job.
     */
    public static void applyEffect(Faction faction, PotionEffectType type, int durationSeconds) {
        int durationTicks = Math.max(1, durationSeconds) * 20;
        int amplifier = vanillaMaxAmplifier(type);
        PotionEffect effect = new PotionEffect(type, durationTicks, amplifier, false, true, true);

        faction.membersOnline(true).forEach(fp -> {
            if (fp.asPlayer() != null) {
                fp.asPlayer().addPotionEffect(effect);
            }
        });

        faction.sendRichMessage("<green>✦ F Effect activated: <yellow>" + displayName(type)
                + "</yellow> for " + formatDuration(durationSeconds) + "!");
    }

    private static String formatDuration(int seconds) {
        if (seconds >= 3600) return (seconds / 3600) + "h";
        if (seconds >= 60) return (seconds / 60) + "m";
        return seconds + "s";
    }

    /** Returns the remaining cooldown in seconds, or 0 if ready. */
    public static long cooldownRemainingSeconds(Faction faction) {
        long remaining = faction.effectCooldownEnd() - System.currentTimeMillis();
        return remaining > 0 ? remaining / 1000L : 0L;
    }

    /** Formats a PotionEffectType name to a readable string. (Legacy — prefer {@link #displayName}.) */
    public static String formatEffectName(PotionEffectType type) {
        return displayName(type);
    }

    // ── Per-effect metadata: friendly name, icon, vanilla-max amplifier ──────

    /**
     * Friendly display name (e.g. "Strength Potion", "Speed Potion") for a given effect.
     * Uses the canonical key for lookup so HASTE / FAST_DIGGING / etc. all resolve correctly.
     */
    public static String displayName(PotionEffectType type) {
        if (type == null) return "Unknown";
        String key = canonicalKey(type);
        return switch (key) {
            case "speed" -> "Speed Potion";
            case "haste" -> "Haste Potion";
            case "regeneration" -> "Regeneration Potion";
            case "strength", "increase_damage" -> "Strength Potion";
            case "jump_boost" -> "Jump Boost Potion";
            case "fire_resistance" -> "Fire Resistance Potion";
            case "night_vision" -> "Night Vision Potion";
            case "saturation" -> "Saturation Potion";
            default -> {
                // Fallback: capitalize the key segments
                String raw = key.replace("_", " ");
                StringBuilder sb = new StringBuilder();
                for (String word : raw.split(" ")) {
                    if (!word.isEmpty()) {
                        sb.append(Character.toUpperCase(word.charAt(0)))
                          .append(word.substring(1).toLowerCase())
                          .append(" ");
                    }
                }
                yield sb.toString().trim() + " Potion";
            }
        };
    }

    /**
     * GUI icon Material for a given effect (legacy fallback when a real potion icon isn't desired).
     */
    public static Material iconFor(PotionEffectType type) {
        String key = canonicalKey(type);
        return switch (key) {
            case "speed" -> Material.SUGAR;
            case "haste" -> Material.GOLDEN_PICKAXE;
            case "regeneration" -> Material.GHAST_TEAR;
            case "strength", "increase_damage" -> Material.BLAZE_POWDER;
            case "jump_boost" -> Material.RABBIT_FOOT;
            case "fire_resistance" -> Material.MAGMA_CREAM;
            case "night_vision" -> Material.GOLDEN_CARROT;
            case "saturation" -> Material.COOKED_BEEF;
            default -> Material.SPLASH_POTION;
        };
    }

    /**
     * Build a vanilla potion ItemStack matching the given effect, with the potion variant
     * (drinkable / splash / lingering) chosen by the duration tier. The potion is coloured
     * to match the vanilla potion of that effect.
     *
     * @param type     potion effect to build for
     * @param duration duration in seconds — selects the variant: 10min → POTION, 30min → SPLASH_POTION, 1h+ → LINGERING_POTION
     */
    public static ItemStack buildPotionIcon(PotionEffectType type, int duration) {
        Material mat;
        if (duration >= 3600) mat = Material.LINGERING_POTION;
        else if (duration >= 1800) mat = Material.SPLASH_POTION;
        else mat = Material.POTION;

        ItemStack stack = new ItemStack(mat);
        if (stack.getItemMeta() instanceof PotionMeta pm) {
            // Add the actual effect — Bukkit will colour and tooltip-format the potion accordingly
            pm.addCustomEffect(new PotionEffect(type, Math.max(1, duration) * 20, vanillaMaxAmplifier(type), false, true, true), true);
            // Override the colour to match vanilla so the texture looks right even if customEffects aren't visualised
            pm.setColor(vanillaPotionColor(type));
            stack.setItemMeta(pm);
        }
        return stack;
    }

    /** Approximate vanilla potion color for an effect. */
    private static Color vanillaPotionColor(PotionEffectType type) {
        String key = canonicalKey(type);
        return switch (key) {
            case "speed" -> Color.fromRGB(124, 175, 198);
            case "haste" -> Color.fromRGB(217, 192, 67);
            case "regeneration" -> Color.fromRGB(205, 92, 171);
            case "strength", "increase_damage" -> Color.fromRGB(147, 36, 35);
            case "jump_boost" -> Color.fromRGB(34, 255, 76);
            case "fire_resistance" -> Color.fromRGB(228, 154, 58);
            case "night_vision" -> Color.fromRGB(31, 31, 161);
            case "saturation" -> Color.fromRGB(248, 36, 35);
            default -> Color.fromRGB(255, 0, 255);
        };
    }

    /**
     * Vanilla-max amplifier per effect (0-indexed; amp=1 means level II).
     * Speed/Strength/Haste/JumpBoost/Regeneration are all II in vanilla; the rest are I.
     */
    public static int vanillaMaxAmplifier(PotionEffectType type) {
        String key = canonicalKey(type);
        return switch (key) {
            case "speed", "haste", "regeneration", "strength", "increase_damage", "jump_boost" -> 1;
            default -> 0;
        };
    }

    /**
     * Canonical lowercase key for a PotionEffectType. Uses {@link PotionEffectType#getKey()}'s key,
     * which is stable across Bukkit versions (e.g. "haste", "strength", "fire_resistance").
     */
    public static String canonicalKey(PotionEffectType type) {
        if (type == null) return "";
        try {
            return type.getKey().getKey().toLowerCase();
        } catch (Throwable t) {
            // Older API fallback
            return type.getName().toLowerCase();
        }
    }

    /**
     * Resolve a PotionEffectType from a stored key string. Tries the modern Registry path first,
     * falls back to the legacy {@code getByName} for backward compatibility with existing JSON saves.
     */
    public static PotionEffectType byKey(String name) {
        if (name == null || name.isEmpty()) return null;
        // Modern path
        try {
            NamespacedKey key = NamespacedKey.minecraft(name.toLowerCase());
            PotionEffectType viaRegistry = Registry.EFFECT.get(key);
            if (viaRegistry != null) return viaRegistry;
        } catch (Throwable ignored) {}
        // Legacy fallback (still works on most versions)
        try {
            @SuppressWarnings("deprecation")
            PotionEffectType legacy = PotionEffectType.getByName(name.toUpperCase());
            if (legacy != null) return legacy;
        } catch (Throwable ignored) {}
        // Manual common-alias map for stragglers
        String lower = name.toLowerCase();
        if (lower.equals("haste") || lower.equals("fast_digging")) {
            try { return Registry.EFFECT.get(NamespacedKey.minecraft("haste")); } catch (Throwable ignored) {}
        }
        if (lower.equals("strength") || lower.equals("increase_damage")) {
            try { return Registry.EFFECT.get(NamespacedKey.minecraft("strength")); } catch (Throwable ignored) {}
        }
        return null;
    }
}
