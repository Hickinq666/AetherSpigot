package net.aether.spigot.combat;

import net.aether.spigot.AetherCore;
import net.aether.spigot.config.Engine;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Dégâts practice : crit, chute, armure et protection. Appelé par EntityHuman et EntityLiving
 * pendant le calcul des dégâts, à la place des formules vanilla.
 */
public final class CombatService {

    /** Valeur renvoyée quand le calcul vanilla doit s'appliquer. */
    public static final float VANILLA = -1.0F;

    private final AetherCore plugin;

    public CombatService(AetherCore plugin) {
        this.plugin = plugin;
    }

    public float critMultiplier() {
        Engine engine = plugin.engine();
        return engine.customDamage ? (float) engine.critModifier : 1.5F;
    }

    public float fallMultiplier() {
        return (float) plugin.engine().fallModifier;
    }

    /** Dégâts restants après l'armure, pour un coup ou un projectile entre joueurs. */
    public float armor(float damage, int armorPoints) {
        Engine engine = plugin.engine();
        if (!engine.customDamage) {
            return VANILLA;
        }
        return (float) (damage * DamageMath.remainingAfterArmor(armorPoints, engine.armorDivision));
    }

    /** Dégâts restants après Protection, sans l'aléatoire vanilla. cause : nom de DamageCause Bukkit. */
    public float protection(Player victim, String cause, float damage) {
        Engine engine = plugin.engine();
        if (!engine.customDamage || engine.protRandom) {
            return VANILLA;
        }
        double fraction = DamageMath.protectionFraction(epf(victim, cause), engine.protectionModifier);
        return (float) Math.max(0.0D, damage * (1.0D - fraction));
    }

    static int epf(Player player, String cause) {
        int protection = 0;
        int fire = 0;
        int feather = 0;
        int blast = 0;
        int projectile = 0;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece == null) {
                continue;
            }
            protection += piece.getEnchantmentLevel(Enchantment.PROTECTION_ENVIRONMENTAL);
            fire += piece.getEnchantmentLevel(Enchantment.PROTECTION_FIRE);
            feather += piece.getEnchantmentLevel(Enchantment.PROTECTION_FALL);
            blast += piece.getEnchantmentLevel(Enchantment.PROTECTION_EXPLOSIONS);
            projectile += piece.getEnchantmentLevel(Enchantment.PROTECTION_PROJECTILE);
        }
        return DamageMath.enchantProtection(protection, fire, feather, blast, projectile, cause);
    }
}
