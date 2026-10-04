package net.aether.spigot.combat;

/**
 * Armure et protection du practice HCF.
 * Un diviseur d'armure plus haut laisse passer plus de dégâts (12 en teamfight).
 * Un protectionModifier plus bas laisse aussi passer plus de dégâts.
 */
public final class DamageMath {

    private DamageMath() {
    }

    public static double remainingAfterArmor(double armorPoints, double divisor) {
        double armor = Math.max(0.0D, armorPoints);
        double safe = divisor <= 0.0D ? 25.0D : divisor;
        return safe / (safe + armor);
    }

    public static double protectionFraction(int enchantProtectionFactor, double protectionModifier) {
        double capped = Math.min(20.0D, Math.max(0.0D, enchantProtectionFactor));
        double vanilla = capped / 25.0D;
        double scale = protectionModifier / 25.0D;
        double fraction = vanilla * scale;
        if (fraction < 0.0D) {
            return 0.0D;
        }
        if (fraction > 0.8D) {
            return 0.8D;
        }
        return fraction;
    }

    public static double rescaleCrit(double damageIncludingCrit, double vanillaMultiplier, double wantedMultiplier) {
        if (vanillaMultiplier == 0.0D) {
            return damageIncludingCrit;
        }
        return damageIncludingCrit / vanillaMultiplier * wantedMultiplier;
    }

    public static int enchantProtection(int protection, int fire, int feather, int blast, int projectile, String cause) {
        int total = protection;
        if (cause == null) {
            return Math.min(20, total);
        }
        if ("FIRE".equals(cause) || "FIRE_TICK".equals(cause) || "LAVA".equals(cause)) {
            total += fire * 2;
        } else if ("FALL".equals(cause)) {
            total += feather * 3;
        } else if ("BLOCK_EXPLOSION".equals(cause) || "ENTITY_EXPLOSION".equals(cause)) {
            total += blast * 2;
        } else if ("PROJECTILE".equals(cause)) {
            total += projectile * 2;
        }
        return Math.min(20, Math.max(0, total));
    }
}
