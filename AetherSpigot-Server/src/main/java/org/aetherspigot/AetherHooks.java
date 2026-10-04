package org.aetherspigot;

import net.aether.spigot.AetherCore;
import net.aether.spigot.knockback.KnockbackListener;
import net.aether.spigot.knockback.KnockbackMath;
import net.minecraft.server.DamageSource;
import net.minecraft.server.Entity;
import net.minecraft.server.EntityArrow;
import net.minecraft.server.EntityDamageSourceIndirect;
import net.minecraft.server.EntityHuman;
import net.minecraft.server.EntityLiving;
import net.minecraft.server.EntityPlayer;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;

/**
 * Points d'entrée du code NMS vers le cœur practice. Chaque méthode retourne false quand le practice
 * ne gère pas le cas, et le comportement NachoSpigot reste alors inchangé.
 */
public final class AetherHooks {

    private AetherHooks() {
    }

    private static KnockbackListener knockback() {
        AetherCore core = AetherCore.get();
        return core == null || !core.isEnabled() ? null : core.knockback();
    }

    /** Knockback au corps à corps entre deux joueurs, appliqué dans le tick du coup. */
    public static boolean melee(EntityHuman attacker, Entity victim, double motX, double motY, double motZ,
                                boolean sprinting, int knockbackLevel) {
        if (!(attacker instanceof EntityPlayer) || !(victim instanceof EntityPlayer)) {
            return false;
        }
        KnockbackListener listener = knockback();
        if (listener == null) {
            return false;
        }
        Player bukkitAttacker = ((EntityPlayer) attacker).getBukkitEntity();
        KnockbackMath.Vec vec = listener.melee(bukkitAttacker, ((EntityPlayer) victim).getBukkitEntity(),
                motX, motY, motZ, victim.onGround, sprinting, knockbackLevel);
        if (vec == null) {
            return false;
        }
        victim.motX = vec.x;
        victim.motY = vec.y;
        victim.motZ = vec.z;
        victim.velocityChanged = true;
        if (sprinting || knockbackLevel > 0) {
            attacker.motX *= 0.6D;
            attacker.motZ *= 0.6D;
            if (listener.cancelSprint(bukkitAttacker)) {
                attacker.setExtraKnockback(false);
            }
        }
        return true;
    }

    /** Knockback d'une flèche sur un joueur. Le bonus Punch est inclus dans le profil. */
    public static boolean projectile(EntityLiving victim, DamageSource source) {
        EntityArrow arrow = arrow(victim, source);
        if (arrow == null) {
            return false;
        }
        KnockbackMath.Vec vec = knockback().arrow((Arrow) arrow.getBukkitEntity(), ((EntityPlayer) victim).getBukkitEntity(),
                arrow.motX, arrow.motY, arrow.motZ);
        if (vec == null) {
            return false;
        }
        victim.motX = vec.x;
        victim.motY = vec.y;
        victim.motZ = vec.z;
        victim.velocityChanged = true;
        return true;
    }

    /** Vrai si le knockback de cette flèche est déjà géré par le profil practice (pas de Punch vanilla en plus). */
    public static boolean arrowHandled(Entity victim) {
        KnockbackListener listener = knockback();
        AetherCore core = AetherCore.get();
        return victim instanceof EntityPlayer && listener != null && core.engine().overrideArrow;
    }

    private static EntityArrow arrow(EntityLiving victim, DamageSource source) {
        if (!(victim instanceof EntityPlayer) || !(source instanceof EntityDamageSourceIndirect)) {
            return null;
        }
        Entity proximate = ((EntityDamageSourceIndirect) source).getProximateDamageSource();
        if (!(proximate instanceof EntityArrow) || knockback() == null) {
            return null;
        }
        return (EntityArrow) proximate;
    }
}
