package net.aether.spigot.knockback;

import net.aether.spigot.config.YamlDoc;

/**
 * Profil de knockback. Les clés plates et le format imbriqué
 * (knockback-simple / knockback-advanced) sont lus tous les deux.
 */
public final class KnockbackProfile {

    public String name = "Default";
    public String type = "ADVANCED";
    public double friction = 2.5D;
    public double horizontal = 0.45D;
    public double horizontalOnGround = 1.1D;
    public double horizontalSprinting = 1.0D;
    public boolean horizontalInherit = true;
    public double horizontalFriction = 0.35D;
    public double horizontalKnockbackDeduction = 0.8D;
    public double extraHorizontal = 0.25D;
    public double extraVertical = 0.01D;
    public double vertical = 0.35D;
    public double verticalOnGround = 1.1D;
    public double verticalInAir = 1.0D;
    public double verticalSprinting = 1.0D;
    public boolean verticalInherit = false;
    public double verticalFriction = 0.35D;
    public double verticalLimit = 0.4D;
    public boolean enableVerticalLimit = false;
    public boolean enableOnePointSeven = true;
    public double onePointSevenHorizontal = 0.37D;
    public double onePointSevenMultiplier = 0.4D;
    public double slowdown = 0.6D;
    public boolean cancelSprint = true;
    public double rodHorizontal = 1.1D;
    public double rodVertical = 0.4D;
    public double rodSpeed = 1.0D;
    public boolean bowBoostDirectional = false;
    public boolean bowBoostKbOnlyShooter = false;
    public double bowHorizontal = 0.9D;
    public double bowVertical = 0.35D;
    public double bowPunchMultiplier = 1.1D;
    public double potionFall = 0.05D;
    public double potionSpeed = 0.5D;
    public double potionVerticalOffset = -10.0D;
    public boolean potionFast = true;
    public int hitDelay = 20;
    public int hitDelayArrow = 20;
    public boolean comboMode = false;
    public double comboHeight = 2.3D;
    public int comboTicks = 10;
    public double comboVelocity = -0.05D;

    public static KnockbackProfile from(String name, YamlDoc doc) {
        KnockbackProfile profile = new KnockbackProfile();
        profile.name = name;
        profile.type = upper(doc.text("type.knockbackType", doc.text("knockbackType", "ADVANCED")));
        String nested = "SIMPLE".equals(profile.type) ? "knockback-simple" : "knockback-advanced";
        boolean useNested = doc.at(nested + ".horizontal") != null || doc.at(nested + ".friction") != null;
        String prefix = useNested ? nested + "." : "";
        profile.friction = num(doc, prefix, "friction", profile.friction);
        profile.horizontal = num(doc, prefix, "horizontal", profile.horizontal);
        profile.horizontalOnGround = num(doc, prefix, "horizontalOnGround", profile.horizontalOnGround);
        profile.horizontalSprinting = num(doc, prefix, "horizontalSprinting", profile.horizontalSprinting);
        profile.horizontalInherit = flag(doc, prefix, "horizontalInherit", profile.horizontalInherit);
        profile.horizontalFriction = num(doc, prefix, "horizontalFriction", profile.horizontalFriction);
        profile.horizontalKnockbackDeduction = num(doc, prefix, "horizontalKnockbackDeduction", profile.horizontalKnockbackDeduction);
        profile.extraHorizontal = num(doc, prefix, "extraHorizontal", profile.extraHorizontal);
        profile.extraVertical = num(doc, prefix, "extraVertical", profile.extraVertical);
        profile.vertical = num(doc, prefix, "vertical", profile.vertical);
        profile.verticalOnGround = num(doc, prefix, "verticalOnGround", profile.verticalOnGround);
        profile.verticalInAir = num(doc, prefix, "verticalInAir", profile.verticalInAir);
        profile.verticalSprinting = num(doc, prefix, "verticalSprinting", profile.verticalSprinting);
        profile.verticalInherit = flag(doc, prefix, "verticalInherit", profile.verticalInherit);
        profile.verticalFriction = num(doc, prefix, "verticalFriction", profile.verticalFriction);
        profile.verticalLimit = num(doc, prefix, "verticalLimit", profile.verticalLimit);
        profile.enableVerticalLimit = flag(doc, prefix, "enableVerticalLimit", profile.enableVerticalLimit);
        profile.enableOnePointSeven = flag(doc, prefix, "enableOnePointSeven", profile.enableOnePointSeven);
        profile.onePointSevenHorizontal = num(doc, prefix, "onePointSevenHorizontal", profile.onePointSevenHorizontal);
        profile.onePointSevenMultiplier = num(doc, prefix, "onePointSevenMultiplier", profile.onePointSevenMultiplier);
        profile.slowdown = num(doc, prefix, "slowdown", profile.slowdown);
        profile.cancelSprint = flag(doc, prefix, "cancelSprint", profile.cancelSprint);
        profile.rodHorizontal = num(doc, prefix, "rodHorizontal", profile.rodHorizontal);
        profile.rodVertical = num(doc, prefix, "rodVertical", profile.rodVertical);
        profile.rodSpeed = num(doc, prefix, "rodSpeed", profile.rodSpeed);
        profile.bowBoostDirectional = flag(doc, prefix, "bowBoostDirectional", profile.bowBoostDirectional);
        profile.bowBoostKbOnlyShooter = flag(doc, prefix, "bowBoostKbOnlyShooter", profile.bowBoostKbOnlyShooter);
        profile.bowHorizontal = num(doc, prefix, "bowHorizontal", profile.bowHorizontal);
        profile.bowVertical = num(doc, prefix, "bowVertical", profile.bowVertical);
        profile.bowPunchMultiplier = num(doc, prefix, "bowPunchMultiplier", profile.bowPunchMultiplier);
        profile.potionFall = num(doc, prefix, "potionFall", profile.potionFall);
        profile.potionSpeed = num(doc, prefix, "potionSpeed", profile.potionSpeed);
        profile.potionVerticalOffset = num(doc, prefix, "potionVerticalOffset", profile.potionVerticalOffset);
        profile.potionFast = flag(doc, prefix, "potionFast", profile.potionFast);
        profile.hitDelay = (int) Math.round(num(doc, prefix, "hitDelay", profile.hitDelay));
        profile.hitDelayArrow = (int) Math.round(num(doc, prefix, "hitDelayArrow", profile.hitDelayArrow));
        profile.comboMode = flag(doc, prefix, "comboMode", profile.comboMode);
        profile.comboHeight = num(doc, prefix, "comboHeight", profile.comboHeight);
        profile.comboTicks = (int) Math.round(num(doc, prefix, "comboTicks", profile.comboTicks));
        profile.comboVelocity = num(doc, prefix, "comboVelocity", profile.comboVelocity);
        return profile;
    }

    private static double num(YamlDoc doc, String prefix, String key, double fallback) {
        if (!prefix.isEmpty() && doc.at(prefix + key) != null) {
            return doc.decimal(prefix + key, fallback);
        }
        if (doc.at(key) != null) {
            return doc.decimal(key, fallback);
        }
        return fallback;
    }

    private static boolean flag(YamlDoc doc, String prefix, String key, boolean fallback) {
        if (!prefix.isEmpty() && doc.at(prefix + key) != null) {
            return doc.bool(prefix + key, fallback);
        }
        if (doc.at(key) != null) {
            return doc.bool(key, fallback);
        }
        return fallback;
    }

    private static String upper(String value) {
        return value == null ? "ADVANCED" : value.trim().toUpperCase();
    }
}
