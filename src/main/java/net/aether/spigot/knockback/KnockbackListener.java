package net.aether.spigot.knockback;

import net.aether.spigot.AetherPlugin;
import net.aether.spigot.config.Engine;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Fish;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class KnockbackListener implements Listener {

    private final AetherPlugin plugin;
    private final Map<UUID, Long> lastMelee = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, Long> lastArrow = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, Integer> combos = new ConcurrentHashMap<UUID, Integer>();
    private final Map<UUID, Long> comboAt = new ConcurrentHashMap<UUID, Long>();

    public KnockbackListener(AetherPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Arrow)) {
            return;
        }
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player)) {
            return;
        }
        Player shooter = (Player) projectile.getShooter();
        ItemStack hand = shooter.getItemInHand();
        int punch = hand == null ? 0 : hand.getEnchantmentLevel(Enchantment.ARROW_KNOCKBACK);
        projectile.setMetadata("aether-punch", new FixedMetadataValue(plugin, Integer.valueOf(punch)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Engine engine = plugin.engine();
        Player victim = (Player) event.getEntity();
        long now = System.currentTimeMillis();
        if (event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            KnockbackProfile profile = plugin.profileFor(attacker);
            if (engine.patchDoubleHit && tooSoon(lastMelee, victim.getUniqueId(), now, profile.hitDelay)) {
                event.setCancelled(true);
                return;
            }
            lastMelee.put(victim.getUniqueId(), Long.valueOf(now));
            arm(victim, profile);
            if (!engine.overrideKnockback) {
                return;
            }
            KnockbackMath.Input input = new KnockbackMath.Input();
            input.motX = victim.getVelocity().getX();
            input.motY = victim.getVelocity().getY();
            input.motZ = victim.getVelocity().getZ();
            input.dirX = victim.getLocation().getX() - attacker.getLocation().getX();
            input.dirZ = victim.getLocation().getZ() - attacker.getLocation().getZ();
            input.onGround = victim.isOnGround();
            input.sprinting = attacker.isSprinting();
            input.backHit = behind(attacker, victim);
            ItemStack hand = attacker.getItemInHand();
            input.knockbackLevel = hand == null ? 0 : hand.getEnchantmentLevel(Enchantment.KNOCKBACK);
            input.comboTicks = combo(victim.getUniqueId(), now);
            KnockbackMath.Vec vec = KnockbackMath.apply(profile, input);
            push(victim, vec);
            if (profile.cancelSprint) {
                attacker.setSprinting(false);
            }
            return;
        }
        if (event.getDamager() instanceof Arrow && engine.overrideArrow) {
            Arrow arrow = (Arrow) event.getDamager();
            Player shooter = arrow.getShooter() instanceof Player ? (Player) arrow.getShooter() : null;
            KnockbackProfile profile = plugin.profileFor(shooter);
            if (engine.patchArrowBounce && tooSoon(lastArrow, victim.getUniqueId(), now, profile.hitDelayArrow)) {
                event.setCancelled(true);
                return;
            }
            lastArrow.put(victim.getUniqueId(), Long.valueOf(now));
            arm(victim, profile);
            Vector direction = arrow.getVelocity();
            boolean directional = profile.bowBoostDirectional && shooter != null
                    && (!profile.bowBoostKbOnlyShooter || shooter.equals(victim));
            if (directional) {
                direction = shooter.getLocation().getDirection();
            }
            if (direction == null || direction.lengthSquared() < 1.0E-6D) {
                direction = shooter == null ? new Vector(0, 0, 1) : shooter.getLocation().getDirection();
            }
            int punch = 0;
            if (arrow.hasMetadata("aether-punch")) {
                punch = arrow.getMetadata("aether-punch").get(0).asInt();
            }
            KnockbackMath.Vec vec = KnockbackMath.bow(profile, direction.getX(), direction.getY(), direction.getZ(), punch);
            push(victim, vec);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRod(PlayerFishEvent event) {
        Engine engine = plugin.engine();
        if (!engine.overrideRod || event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY) {
            return;
        }
        if (!(event.getCaught() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getCaught();
        Player fisher = event.getPlayer();
        KnockbackProfile profile = plugin.profileFor(fisher);
        Vector away = victim.getLocation().toVector().subtract(fisher.getLocation().toVector());
        Fish hook = event.getHook();
        if (hook != null) {
            away = victim.getLocation().toVector().subtract(hook.getLocation().toVector());
        }
        KnockbackMath.Vec vec = KnockbackMath.rod(profile, away.getX(), away.getZ());
        push(victim, vec);
    }

    private void arm(Player victim, KnockbackProfile profile) {
        int ticks = Math.min(Math.max(0, profile.hitDelay), Math.max(0, profile.hitDelayArrow));
        victim.setMaximumNoDamageTicks(ticks);
        victim.setNoDamageTicks(ticks);
    }

    private void push(final Player victim, final KnockbackMath.Vec vec) {
        plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (victim.isOnline() && !victim.isDead()) {
                    victim.setVelocity(new Vector(vec.x, vec.y, vec.z));
                }
            }
        });
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

    private static boolean tooSoon(Map<UUID, Long> stamps, UUID id, long now, int delayTicks) {
        if (delayTicks <= 0) {
            return false;
        }
        Long last = stamps.get(id);
        return last != null && now - last.longValue() < delayTicks * 50L;
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
