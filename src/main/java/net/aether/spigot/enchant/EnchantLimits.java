package net.aether.spigot.enchant;

import net.aether.spigot.config.YamlDoc;

import java.util.LinkedHashMap;
import java.util.Map;

public final class EnchantLimits {

    private final Map<String, Integer> caps;

    public EnchantLimits(Map<String, Integer> caps) {
        this.caps = caps;
    }

    public static EnchantLimits from(YamlDoc doc) {
        Map<String, Integer> caps = new LinkedHashMap<String, Integer>();
        put(caps, doc, "DURABILITY", "enchant-limits.unbreaking", 3);
        put(caps, doc, "PROTECTION_ENVIRONMENTAL", "enchant-limits.armor.protectionAll", 4);
        put(caps, doc, "PROTECTION_FIRE", "enchant-limits.armor.protectionFire", 4);
        put(caps, doc, "PROTECTION_FALL", "enchant-limits.armor.protectionFall", 4);
        put(caps, doc, "PROTECTION_EXPLOSIONS", "enchant-limits.armor.protectionExplosion", 4);
        put(caps, doc, "PROTECTION_PROJECTILE", "enchant-limits.armor.protectionProjectile", 4);
        put(caps, doc, "THORNS", "enchant-limits.armor.thorns", 3);
        put(caps, doc, "WATER_WORKER", "enchant-limits.armor.aquaAffinity", 1);
        put(caps, doc, "OXYGEN", "enchant-limits.armor.waterBreathing", 3);
        put(caps, doc, "DEPTH_STRIDER", "enchant-limits.armor.depthStrider", 3);
        put(caps, doc, "DIG_SPEED", "enchant-limits.tools.efficiency", 5);
        put(caps, doc, "SILK_TOUCH", "enchant-limits.tools.silkTouch", 1);
        put(caps, doc, "LOOT_BONUS_BLOCKS", "enchant-limits.tools.fortune", 3);
        put(caps, doc, "DAMAGE_ALL", "enchant-limits.swords.sharpness", 5);
        put(caps, doc, "DAMAGE_ARTHROPODS", "enchant-limits.swords.baneOfArthropods", 5);
        put(caps, doc, "DAMAGE_UNDEAD", "enchant-limits.swords.smite", 5);
        put(caps, doc, "FIRE_ASPECT", "enchant-limits.swords.fireAspect", 2);
        put(caps, doc, "KNOCKBACK", "enchant-limits.swords.knockback", 2);
        put(caps, doc, "LOOT_BONUS_MOBS", "enchant-limits.swords.looting", 3);
        put(caps, doc, "ARROW_DAMAGE", "enchant-limits.bows.power", 5);
        put(caps, doc, "ARROW_KNOCKBACK", "enchant-limits.bows.punch", 2);
        put(caps, doc, "ARROW_FIRE", "enchant-limits.bows.flame", 1);
        put(caps, doc, "ARROW_INFINITE", "enchant-limits.bows.infinity", 1);
        put(caps, doc, "LURE", "enchant-limits.rods.lure", 3);
        put(caps, doc, "LUCK", "enchant-limits.rods.luckOfTheSea", 3);
        return new EnchantLimits(caps);
    }

    private static void put(Map<String, Integer> caps, YamlDoc doc, String enchant, String path, int fallback) {
        caps.put(enchant, Integer.valueOf(doc.integer(path, fallback)));
    }

    public int cap(String enchantName, int level) {
        Integer max = caps.get(enchantName);
        if (max == null) {
            return level;
        }
        return Math.min(level, max.intValue());
    }

    public int max(String enchantName) {
        Integer max = caps.get(enchantName);
        return max == null ? Integer.MAX_VALUE : max.intValue();
    }

    public boolean clamp(Map<String, Integer> levels) {
        boolean changed = false;
        String[] keys = levels.keySet().toArray(new String[0]);
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            int level = levels.get(key).intValue();
            int capped = cap(key, level);
            if (capped <= 0) {
                levels.remove(key);
                changed = true;
            } else if (capped != level) {
                levels.put(key, Integer.valueOf(capped));
                changed = true;
            }
        }
        return changed;
    }
}
