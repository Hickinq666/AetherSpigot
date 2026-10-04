package net.aether.spigot.knockback;

import net.aether.spigot.AetherCore;
import net.aether.spigot.config.Engine;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Knockback et hit delay practice. Appelé par le code NMS (via AetherHooks) au moment du coup,
 * à la place du calcul NachoSpigot : rien n'est corrigé après coup.
 */
public final class KnockbackService {

    public enum Kind { MELEE, ARROW, ROD }

    private final AetherCore plugin;
    private final Map<UUID, Integer> combos = new ConcurrentHashMap<UUID, Integer>();
    private final Map<UUID, Long> comboAt = new ConcurrentHashMap<UUID, Long>();

    public KnockbackService(AetherCore plugin) {
        this.plugin = plugin;
    }

    public boolean overrides(Kind kind) {
        Engine engine = plugin.engine();
        switch (kind) {
            case MELEE: return engine.overrideKnockback;
            case ARROW: return engine.overrideArrow;
            default: return engine.overrideRod;
        }
    }

    /**
     * Invulnérabilité posée sur la victime (maxNoDamageTicks), comme en vanilla : un coup passe
     * quand il reste moins de la moitié de ce délai.
     */
    public int hitDelay(Player source, Kind kind) {
        KnockbackProfile profile = plugin.profileFor(source);
        return Math.max(0, kind == Kind.ARROW ? profile.hitDelayArrow : profile.hitDelay);
    }

    /**
     * Pendant l'invulnérabilité, vanilla laisse passer un coup plus fort que le précédent (double hit).
     * Avec le patch, ce coup est refusé.
     */
    public boolean blocksDoubleHit(Kind kind) {
        Engine engine = plugin.engine();
        return kind == Kind.ARROW ? engine.patchArrowBounce : engine.patchDoubleHit;
    }

    public KnockbackMath.Vec melee(Player attacker, Player victim, double motX, double motY, double motZ,
                                   boolean onGround, boolean sprinting, int knockbackLevel) {
        KnockbackProfile profile = plugin.profileFor(attacker);
        KnockbackMath.Input input = new KnockbackMath.Input();
        input.motX = motX;
        input.motY = motY;
        input.motZ = motZ;
        input.dirX = victim.getLocation().getX() - attacker.getLocation().getX();
        input.dirZ = victim.getLocation().getZ() - attacker.getLocation().getZ();
        input.onGround = onGround;
        input.sprinting = sprinting;
        input.backHit = behind(attacker, victim);
        input.knockbackLevel = knockbackLevel;
        input.comboTicks = combo(victim.getUniqueId(), System.currentTimeMillis());
        return KnockbackMath.apply(profile, input);
    }

    public boolean cancelSprint(Player attacker) {
        return plugin.profileFor(attacker).cancelSprint;
    }

    public KnockbackMath.Vec arrow(Player shooter, Player victim, double velX, double velY, double velZ, int punch) {
        KnockbackProfile profile = plugin.profileFor(shooter);
        Vector direction = new Vector(velX, velY, velZ);
        if (profile.bowBoostDirectional && shooter != null && (!profile.bowBoostKbOnlyShooter || shooter.equals(victim))) {
            direction = shooter.getLocation().getDirection();
        }
        if (direction.lengthSquared() < 1.0E-6D) {
            direction = shooter == null ? new Vector(0, 0, 1) : shooter.getLocation().getDirection();
        }
        return KnockbackMath.bow(profile, direction.getX(), direction.getY(), direction.getZ(), punch);
    }

    public KnockbackMath.Vec rod(Player owner, double dirX, double dirZ) {
        return KnockbackMath.rod(plugin.profileFor(owner), dirX, dirZ);
    }

    private int combo(UUID id, long now) {
        Long last = comboAt.get(id);
        int count = 0;
        if (last != null && now - last.longValue() < 800L) {
            Integer known = combos.get(id);
            count = known == null ? 0 : known.intValue();
        }
        combos.put(id, Integer.valueOf(count + 1));
        comboAt.put(id, Long.valueOf(now));
        return count;
    }

    static boolean behind(Player attacker, Player victim) {
        Vector look = victim.getLocation().getDirection();
        look.setY(0);
        if (look.lengthSquared() < 1.0E-6D) {
            return false;
        }
        look.normalize();
        Vector towardAttacker = attacker.getLocation().toVector().subtract(victim.getLocation().toVector());
        towardAttacker.setY(0);
        if (towardAttacker.lengthSquared() < 1.0E-6D) {
            return false;
        }
        towardAttacker.normalize();
        return look.dot(towardAttacker) < -0.3D;
    }
}
