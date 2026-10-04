package net.aether.spigot.combat;

import net.aether.spigot.AetherCore;
import net.aether.spigot.config.Engine;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

public final class CombatListener implements Listener {

    private final AetherCore plugin;

    public CombatListener(AetherCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent event) {
        Engine engine = plugin.engine();
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        if (!event.isApplicable(EntityDamageEvent.DamageModifier.BASE)) {
            return;
        }
        double base = event.getDamage(EntityDamageEvent.DamageModifier.BASE) * engine.fallModifier;
        event.setDamage(EntityDamageEvent.DamageModifier.BASE, base);
        if (engine.customDamage && !engine.protRandom && event.getEntity() instanceof Player
                && event.isApplicable(EntityDamageEvent.DamageModifier.MAGIC)) {
            int epf = epf((Player) event.getEntity(), "FALL");
            double fraction = DamageMath.protectionFraction(epf, engine.protectionModifier);
            event.setDamage(EntityDamageEvent.DamageModifier.MAGIC, -(base * fraction));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        Engine engine = plugin.engine();
        if (!engine.customDamage || !(event.getEntity() instanceof Player)) {
            return;
        }
        if (!(event.getDamager() instanceof Player) && event.getCause() != EntityDamageEvent.DamageCause.PROJECTILE) {
            return;
        }
        if (!event.isApplicable(EntityDamageEvent.DamageModifier.BASE)) {
            return;
        }
        Player victim = (Player) event.getEntity();
        double base = event.getDamage(EntityDamageEvent.DamageModifier.BASE);
        if (event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            if (critical(attacker) && engine.critModifier != 1.5D) {
                base = DamageMath.rescaleCrit(base, 1.5D, engine.critModifier);
            }
        }
        int armor = armorPoints(victim);
        double remain = DamageMath.remainingAfterArmor(armor, engine.armorDivision);
        double armorMod = -(base * (1.0D - remain));
        double afterArmor = Math.max(0.0D, base + armorMod);
        event.setDamage(EntityDamageEvent.DamageModifier.BASE, base);
        if (event.isApplicable(EntityDamageEvent.DamageModifier.ARMOR)) {
            event.setDamage(EntityDamageEvent.DamageModifier.ARMOR, armorMod);
        }
        if (!engine.protRandom && event.isApplicable(EntityDamageEvent.DamageModifier.MAGIC)) {
            String cause = event.getCause() == null ? "" : event.getCause().name();
            int epf = epf(victim, cause);
            double fraction = DamageMath.protectionFraction(epf, engine.protectionModifier);
            double magic = -(afterArmor * fraction);
            if (afterArmor + magic < 0.0D) {
                magic = -afterArmor;
            }
            event.setDamage(EntityDamageEvent.DamageModifier.MAGIC, magic);
        }
    }

    static boolean critical(Player attacker) {
        if (attacker.getFallDistance() <= 0.0F || attacker.isOnGround() || attacker.isInsideVehicle()) {
            return false;
        }
        if (attacker.hasPotionEffect(PotionEffectType.BLINDNESS)) {
            return false;
        }
        Material at = attacker.getLocation().getBlock().getType();
        return at != Material.WATER && at != Material.STATIONARY_WATER && at != Material.LADDER && at != Material.VINE;
    }

    static int armorPoints(Player player) {
        int total = 0;
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            total += points(armor[i]);
        }
        return total;
    }

    private static int points(ItemStack stack) {
        if (stack == null) {
            return 0;
        }
        switch (stack.getType()) {
            case LEATHER_HELMET: return 1;
            case LEATHER_CHESTPLATE: return 3;
            case LEATHER_LEGGINGS: return 2;
            case LEATHER_BOOTS: return 1;
            case GOLD_HELMET: return 2;
            case GOLD_CHESTPLATE: return 5;
            case GOLD_LEGGINGS: return 3;
            case GOLD_BOOTS: return 1;
            case CHAINMAIL_HELMET: return 2;
            case CHAINMAIL_CHESTPLATE: return 5;
            case CHAINMAIL_LEGGINGS: return 4;
            case CHAINMAIL_BOOTS: return 1;
            case IRON_HELMET: return 2;
            case IRON_CHESTPLATE: return 6;
            case IRON_LEGGINGS: return 5;
            case IRON_BOOTS: return 2;
            case DIAMOND_HELMET: return 3;
            case DIAMOND_CHESTPLATE: return 8;
            case DIAMOND_LEGGINGS: return 6;
            case DIAMOND_BOOTS: return 3;
            default: return 0;
        }
    }

    static int epf(Player player, String cause) {
        int protection = 0;
        int fire = 0;
        int feather = 0;
        int blast = 0;
        int projectile = 0;
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            ItemStack piece = armor[i];
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
