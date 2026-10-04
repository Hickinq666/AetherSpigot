package org.aetherspigot;

import net.minecraft.server.Entity;
import net.minecraft.server.EntityArrow;
import net.minecraft.server.EntityFishingHook;
import net.minecraft.server.EntityItem;
import net.minecraft.server.EntityPlayer;
import net.minecraft.server.EntityProjectile;
import net.minecraft.server.EntityTNTPrimed;
import net.minecraft.server.MinecraftServer;

/**
 * Isolation des joueurs cachés ({@code Player#hidePlayer}). Un joueur qui ne voit pas X ne reçoit rien
 * de X ni de ce qui lui appartient : flèches, perles, potions, bouchon de canne, items jetés, TNT, sons,
 * effets de monde. Les projectiles de X ne le touchent pas non plus, et inversement.
 *
 * <p>La source d'un son ou d'un effet émis par coordonnées est l'entité en cours de tick, ou le joueur
 * dont le paquet est en cours de traitement. Tout se passe sur le thread principal.
 */
public final class Isolation {

    public static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("aether.isolation"));

    private static Entity current;

    private Isolation() {
    }

    public static Entity push(Entity source) {
        Entity previous = current;
        current = source;
        return previous;
    }

    public static void pop(Entity previous) {
        current = previous;
    }

    /** Joueur responsable de cette entité, ou null. */
    public static EntityPlayer owner(Entity entity) {
        if (entity instanceof EntityPlayer) {
            return (EntityPlayer) entity;
        }
        Entity owner = null;
        if (entity instanceof EntityArrow) {
            owner = ((EntityArrow) entity).shooter;
        } else if (entity instanceof EntityProjectile) {
            owner = ((EntityProjectile) entity).getShooter();
        } else if (entity instanceof EntityFishingHook) {
            owner = ((EntityFishingHook) entity).owner;
        } else if (entity instanceof EntityTNTPrimed) {
            owner = ((EntityTNTPrimed) entity).getSource();
        } else if (entity instanceof EntityItem) {
            String thrower = ((EntityItem) entity).n();
            if (thrower != null) {
                owner = MinecraftServer.getServer().getPlayerList().getPlayer(thrower);
            }
        }
        return owner instanceof EntityPlayer ? (EntityPlayer) owner : null;
    }

    /** Vrai si {@code viewer} ne doit rien recevoir de {@code entity}. */
    public static boolean hidden(EntityPlayer viewer, Entity entity) {
        if (!ENABLED || entity == null) {
            return false;
        }
        EntityPlayer owner = owner(entity);
        return owner != null && owner != viewer && !viewer.getBukkitEntity().canSee(owner.getBukkitEntity());
    }

    /** Vrai si le son ou l'effet en cours ne doit pas partir vers {@code viewer}. */
    public static boolean hiddenFromCurrent(EntityPlayer viewer) {
        return current != null && hidden(viewer, current);
    }

    /** Vrai si {@code source} (un projectile, une potion) ne doit pas toucher {@code target}. */
    public static boolean blocked(Entity source, Entity target) {
        if (!ENABLED || !(target instanceof EntityPlayer)) {
            return false;
        }
        EntityPlayer owner = owner(source);
        if (owner == null || owner == target) {
            return false;
        }
        EntityPlayer player = (EntityPlayer) target;
        return !player.getBukkitEntity().canSee(owner.getBukkitEntity())
                || !owner.getBukkitEntity().canSee(player.getBukkitEntity());
    }
}
