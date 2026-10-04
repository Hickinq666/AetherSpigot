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
import net.minecraft.server.ItemStack;
import net.minecraft.server.Items;
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
        if (source.getEntity() instanceof net.minecraft.server.IMonster && !AetherCore.get().engine().mobAi) {
            return false;
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

    /** Niveau d'enchantement autorisé par enchants.yml ; 0 ou moins retire l'enchantement. */
    public static int enchantLevel(int enchantId, int level) {
        AetherCore core = AetherCore.get();
        if (core == null || !core.isEnabled()) {
            return level;
        }
        org.bukkit.enchantments.Enchantment enchantment = org.bukkit.enchantments.Enchantment.getById(enchantId);
        return enchantment == null ? level : core.engine().enchants.cap(enchantment.getName(), level);
    }

    /** Applique les limites à une table id -> niveau. Retourne true si elle a changé. */
    public static boolean capEnchants(java.util.Map<Integer, Integer> map) {
        boolean changed = false;
        java.util.Iterator<java.util.Map.Entry<Integer, Integer>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<Integer, Integer> entry = it.next();
            int level = entry.getValue().intValue();
            int capped = enchantLevel(entry.getKey().intValue(), level);
            if (capped <= 0) {
                it.remove();
                changed = true;
            } else if (capped != level) {
                entry.setValue(Integer.valueOf(capped));
                changed = true;
            }
        }
        return changed;
    }

    /** Applique les limites aux enchantements d'un objet (livres compris). */
    public static void capEnchants(ItemStack item) {
        if (item == null || !item.hasTag()) {
            return;
        }
        java.util.Map<Integer, Integer> map = EnchantmentManager.a(item);
        if (!capEnchants(map)) {
            return;
        }
        if (item.getItem() == Items.ENCHANTED_BOOK) {
            item.getTag().remove("StoredEnchantments");
        }
        EnchantmentManager.a(map, item);
    }

    private static net.aether.spigot.pearl.PearlService pearls() {
        AetherCore core = AetherCore.get();
        return core == null || !core.isEnabled() ? null : core.pearls();
    }

    /** ItemEnderPearl, avant de créer la perle : cooldown et spawn. Le client a déjà retiré la perle, on le resynchronise. */
    public static boolean pearlMayThrow(EntityHuman human) {
        net.aether.spigot.pearl.PearlService service = pearls();
        if (service == null || !(human instanceof EntityPlayer)) {
            return true;
        }
        Player player = ((EntityPlayer) human).getBukkitEntity();
        if (service.mayThrow(player)) {
            return true;
        }
        player.updateInventory();
        return false;
    }

    /** ItemEnderPearl, une fois la perle ajoutée au monde. */
    public static void pearlThrown(EntityHuman human) {
        net.aether.spigot.pearl.PearlService service = pearls();
        if (service != null && human instanceof EntityPlayer) {
            service.thrown(((EntityPlayer) human).getBukkitEntity());
        }
    }

    /** Clic droit sur un bloc (barrière, liste launchOnOtherClick) : la perle part au lieu d'utiliser le bloc. */
    public static boolean pearlForcesLaunch(ItemStack hand, net.minecraft.server.Block clicked) {
        net.aether.spigot.pearl.PearlService service = pearls();
        if (service == null || hand == null || hand.getItem() != Items.ENDER_PEARL) {
            return false;
        }
        org.bukkit.Material type = org.bukkit.craftbukkit.util.CraftMagicNumbers.getMaterial(clicked);
        return type != null && service.forcesLaunch(type);
    }

    /**
     * EntityEnderPearl à l'impact, avant le PlayerTeleportEvent. null : comportement NachoSpigot.
     * Un remboursement est déjà appliqué quand le résultat a {@code refund}.
     */
    public static net.aether.spigot.pearl.PearlService.Landing pearlLanding(net.minecraft.server.EntityEnderPearl pearl,
                                                                            EntityPlayer thrower, net.minecraft.server.MovingObjectPosition hit) {
        net.aether.spigot.pearl.PearlService service = pearls();
        if (service == null) {
            return null;
        }
        org.bukkit.World world = pearl.world.getWorld();
        org.bukkit.block.Block block = null;
        if (hit.type == net.minecraft.server.MovingObjectPosition.EnumMovingObjectType.BLOCK && hit.a() != null) {
            block = world.getBlockAt(hit.a().getX(), hit.a().getY(), hit.a().getZ());
        }
        return service.land(thrower.getBukkitEntity(), block, new org.bukkit.Location(world, pearl.locX, pearl.locY, pearl.locZ),
                new org.bukkit.util.Vector(pearl.motX, pearl.motY, pearl.motZ));
    }

    public static void pearlLanded(EntityPlayer thrower, org.bukkit.Location dest) {
        net.aether.spigot.pearl.PearlService service = pearls();
        if (service != null) {
            service.landed(thrower.getBukkitEntity(), dest);
        }
    }

    public static float pearlDamage(float vanilla) {
        net.aether.spigot.pearl.PearlService service = pearls();
        return service == null ? vanilla : service.damage();
    }

    public static boolean pearlEndermite(boolean vanilla) {
        net.aether.spigot.pearl.PearlService service = pearls();
        return service == null ? vanilla : vanilla && service.endermite();
    }

    /** Le bonus Punch est déjà dans le knockback de flèche du profil. */
    public static boolean arrowHandled(Entity victim) {
        KnockbackService service = knockback();
        return victim instanceof EntityPlayer && service != null && service.overrides(KnockbackService.Kind.ARROW);
    }
}
