package org.aetherspigot;

import net.aether.spigot.AetherCore;
import net.aether.spigot.knockback.KnockbackMath;
import net.aether.spigot.knockback.KnockbackService;
import net.minecraft.server.DamageSource;
import net.minecraft.server.EnchantmentManager;
import net.minecraft.server.Entity;
import net.minecraft.server.EntityArrow;
import net.minecraft.server.EntityDamageSource;
import net.minecraft.server.EntityDamageSourceIndirect;
import net.minecraft.server.EntityFishingHook;
import net.minecraft.server.EntityHuman;
import net.minecraft.server.EntityLiving;
import net.minecraft.server.EntityPlayer;
import org.bukkit.entity.Player;

/**
 * Points d'entrée du code NMS vers le practice. Ils sont appelés là où Minecraft calcule le coup,
 * à la place du calcul d'origine. Quand le practice ne gère pas le cas, le code NachoSpigot s'exécute
 * tel quel.
 */
public final class AetherHooks {

    private AetherHooks() {
    }

    private static KnockbackService knockback() {
        AetherCore core = AetherCore.get();
        return core == null || !core.isEnabled() ? null : core.knockback();
    }

    private static Player bukkit(Entity entity) {
        return entity instanceof EntityPlayer ? ((EntityPlayer) entity).getBukkitEntity() : null;
    }

    private static EntityPlayer meleeAttacker(DamageSource source) {
        if (!(source instanceof EntityDamageSource) || source instanceof EntityDamageSourceIndirect) {
            return null;
        }
        Entity entity = source.getEntity();
        return entity instanceof EntityPlayer ? (EntityPlayer) entity : null;
    }

    private static Entity projectile(DamageSource source) {
        return source instanceof EntityDamageSourceIndirect ? ((EntityDamageSourceIndirect) source).getProximateDamageSource() : null;
    }

    /**
     * Début de EntityLiving.damageEntity, avant les événements et les dégâts. Règle l'invulnérabilité
     * du profil et retourne false si le coup arrive pendant le hit delay.
     */
    public static boolean hitAllowed(EntityLiving victim, DamageSource source) {
        KnockbackService service = knockback();
        if (service == null || !(victim instanceof EntityPlayer)) {
            return true;
        }
        Player from;
        KnockbackService.Kind kind;
        EntityPlayer attacker = meleeAttacker(source);
        if (attacker != null) {
            from = attacker.getBukkitEntity();
            kind = KnockbackService.Kind.MELEE;
        } else if (projectile(source) instanceof EntityArrow) {
            from = bukkit(source.getEntity());
            kind = KnockbackService.Kind.ARROW;
        } else {
            return true;
        }
        int ticks = service.hitDelay(from, ((EntityPlayer) victim).getBukkitEntity(), kind);
        if (ticks < 0) {
            return false;
        }
        victim.maxNoDamageTicks = ticks;
        return true;
    }

    /**
     * EntityLiving.a(x, z, source) : calcule le knockback practice à la place de la formule NachoSpigot.
     * Retourne true si le vecteur est posé.
     */
    public static boolean knockback(EntityLiving victim, DamageSource source) {
        KnockbackService service = knockback();
        if (service == null || !(victim instanceof EntityPlayer)) {
            return false;
        }
        Player target = ((EntityPlayer) victim).getBukkitEntity();
        KnockbackMath.Vec vec = null;
        EntityPlayer attacker = meleeAttacker(source);
        Entity projectile = projectile(source);
        if (attacker != null && service.overrides(KnockbackService.Kind.MELEE)) {
            vec = service.melee(attacker.getBukkitEntity(), target, victim.motX, victim.motY, victim.motZ,
                    victim.onGround, attacker.isExtraKnockback(), EnchantmentManager.a((EntityLiving) attacker));
        } else if (projectile instanceof EntityArrow && service.overrides(KnockbackService.Kind.ARROW)) {
            EntityArrow arrow = (EntityArrow) projectile;
            vec = service.arrow(bukkit(source.getEntity()), target, arrow.motX, arrow.motY, arrow.motZ, arrow.knockbackStrength);
        } else if (projectile instanceof EntityFishingHook && service.overrides(KnockbackService.Kind.ROD)) {
            vec = service.rod(bukkit(source.getEntity()), victim.locX - projectile.locX, victim.locZ - projectile.locZ);
        }
        if (vec == null) {
            return false;
        }
        victim.motX = vec.x;
        victim.motY = vec.y;
        victim.motZ = vec.z;
        victim.velocityChanged = true;
        return true;
    }

    /**
     * EntityHuman.attack, après un coup réussi. Si le practice a posé le knockback, applique le
     * ralentissement de l'attaquant et l'arrêt du sprint du profil, et retourne true pour sauter
     * le bonus de knockback NachoSpigot.
     */
    public static boolean meleeHandled(EntityHuman attacker, Entity victim, boolean sprinting, int knockbackLevel) {
        KnockbackService service = knockback();
        if (service == null || !(attacker instanceof EntityPlayer) || !(victim instanceof EntityPlayer)
                || !service.overrides(KnockbackService.Kind.MELEE)) {
            return false;
        }
        if (sprinting || knockbackLevel > 0) {
            attacker.motX *= 0.6D;
            attacker.motZ *= 0.6D;
            if (service.cancelSprint(((EntityPlayer) attacker).getBukkitEntity())) {
                attacker.setExtraKnockback(false);
            }
        }
        return true;
    }

    private static net.aether.spigot.combat.CombatService combat() {
        AetherCore core = AetherCore.get();
        return core == null || !core.isEnabled() ? null : core.combat();
    }

    /** Nom de DamageCause Bukkit pour un coup entre joueurs, une flèche ou une chute ; null sinon. */
    private static String combatCause(DamageSource source) {
        if (source == DamageSource.FALL) {
            return "FALL";
        }
        if (meleeAttacker(source) != null) {
            return "ENTITY_ATTACK";
        }
        if (projectile(source) != null && source.getEntity() instanceof EntityPlayer) {
            return "PROJECTILE";
        }
        return null;
    }

    /** Multiplicateur de coup critique (EntityHuman.attack). */
    public static float critMultiplier() {
        net.aether.spigot.combat.CombatService service = combat();
        return service == null ? 1.5F : service.critMultiplier();
    }

    /** Dégâts de chute (EntityLiving.e). */
    public static float fallDamage(float damage) {
        net.aether.spigot.combat.CombatService service = combat();
        return service == null ? damage : damage * service.fallMultiplier();
    }

    /** EntityLiving.applyArmorModifier : armure practice pour les coups et flèches entre joueurs, sinon -1. */
    public static float armor(EntityLiving victim, DamageSource source, float damage, int armorPoints) {
        net.aether.spigot.combat.CombatService service = combat();
        String cause = combatCause(source);
        if (service == null || !(victim instanceof EntityPlayer) || cause == null || "FALL".equals(cause)) {
            return net.aether.spigot.combat.CombatService.VANILLA;
        }
        return service.armor(damage, armorPoints);
    }

    /** EntityLiving.applyMagicModifier : Protection sans aléatoire, sinon -1. */
    public static float protection(EntityLiving victim, DamageSource source, float damage) {
        net.aether.spigot.combat.CombatService service = combat();
        String cause = combatCause(source);
        if (service == null || !(victim instanceof EntityPlayer) || cause == null) {
            return net.aether.spigot.combat.CombatService.VANILLA;
        }
        return service.protection(((EntityPlayer) victim).getBukkitEntity(), cause, damage);
    }

    /** Le bonus Punch est déjà dans le knockback de flèche du profil. */
    public static boolean arrowHandled(Entity victim) {
        KnockbackService service = knockback();
        return victim instanceof EntityPlayer && service != null && service.overrides(KnockbackService.Kind.ARROW);
    }
}
