package org.aetherspigot;

import net.aether.spigot.AetherCore;
import net.aether.spigot.config.Engine;
import net.aether.spigot.knockback.KnockbackProfile;
import net.aether.spigot.patch.PatchService;
import net.minecraft.server.Block;
import net.minecraft.server.BlockPosition;
import net.minecraft.server.Entity;
import net.minecraft.server.EntityArrow;
import net.minecraft.server.EntityChicken;
import net.minecraft.server.EntityEgg;
import net.minecraft.server.EntityEnderPearl;
import net.minecraft.server.EntityFishingHook;
import net.minecraft.server.EntityHuman;
import net.minecraft.server.EntityInsentient;
import net.minecraft.server.EntityIronGolem;
import net.minecraft.server.EntityLiving;
import net.minecraft.server.EntityPlayer;
import net.minecraft.server.EntityPotion;
import net.minecraft.server.EntitySkeleton;
import net.minecraft.server.EntitySnowball;
import net.minecraft.server.EntitySnowman;
import net.minecraft.server.EntitySpider;
import net.minecraft.server.EntityTNTPrimed;
import net.minecraft.server.EntityZombie;
import net.minecraft.server.IMonster;
import net.minecraft.server.ItemFood;
import net.minecraft.server.ItemPotion;
import net.minecraft.server.ItemStack;
import net.minecraft.server.Items;
import net.minecraft.server.MathHelper;
import net.minecraft.server.PacketPlayInClientCommand;
import net.minecraft.server.World;
import org.bukkit.Material;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

/**
 * Points d'entrée NMS des patchs serveur. Chaque méthode est appelée là où Minecraft décide
 * (création du projectile, paquet de déplacement, rayon d'explosion, apparition…).
 * Sans cœur practice actif, elles laissent passer le comportement d'origine.
 */
public final class PatchHooks {

    private PatchHooks() {
    }

    private static AetherCore core() {
        AetherCore core = AetherCore.get();
        return core == null || !core.isEnabled() ? null : core;
    }

    private static Material material(Block block) {
        return CraftMagicNumbers.getMaterial(block);
    }

    /** Dispersion d'un projectile lancé par un joueur : multiplie l'imprécision vanilla. */
    public static float inaccuracy(Entity projectile, EntityLiving shooter, float vanilla) {
        AetherCore core = core();
        if (core == null || !(shooter instanceof EntityPlayer)) {
            return vanilla;
        }
        Engine engine = core.engine();
        double factor;
        if (projectile instanceof EntityArrow) {
            factor = engine.arrowRandomness;
        } else if (projectile instanceof EntityFishingHook) {
            factor = engine.rodRandomness;
        } else if (projectile instanceof EntitySnowball) {
            factor = engine.snowRandomness;
        } else if (projectile instanceof EntityEgg) {
            factor = engine.eggRandomness;
        } else if (projectile instanceof EntityEnderPearl) {
            factor = engine.pearlRandomness;
        } else if (projectile instanceof EntityPotion) {
            factor = engine.potionRandomness;
        } else {
            return vanilla;
        }
        return (float) (vanilla * Math.max(0.0D, factor));
    }

    /**
     * Constructeur d'EntityPotion, une fois l'objet connu : vitesse et angle du profil de knockback
     * du lanceur, à la place des 0.5 et -20° vanilla.
     */
    public static void aimPotion(EntityPotion potion, EntityLiving shooter) {
        AetherCore core = core();
        if (core == null || !(shooter instanceof EntityPlayer)) {
            return;
        }
        boolean health = potion.item != null && ItemPotion.f(potion.item.getData()) && (potion.item.getData() & 15) == 5;
        if (core.engine().overrideOnlyHealth && !health) {
            return;
        }
        KnockbackProfile profile = core.profileFor(((EntityPlayer) shooter).getBukkitEntity());
        float speed = (float) Math.max(0.05D, profile.potionSpeed * (health && profile.potionFast ? 1.2D : 1.0D));
        float yaw = shooter.yaw / 180.0F * 3.1415927F;
        float pitch = shooter.pitch / 180.0F * 3.1415927F;
        float lifted = (float) ((shooter.pitch + profile.potionVerticalOffset) / 180.0D * Math.PI);
        double x = -MathHelper.sin(yaw) * MathHelper.cos(pitch);
        double z = MathHelper.cos(yaw) * MathHelper.cos(pitch);
        double y = -MathHelper.sin(lifted);
        potion.shoot(x, y, z, speed, inaccuracy(potion, shooter, 1.0F));
    }

    /** Clic droit d'un joueur sur un bloc, avant que le bloc ou l'objet réagisse. true : clic consommé. */
    public static boolean clickBlock(EntityPlayer player, World world, BlockPosition position, ItemStack hand) {
        AetherCore core = core();
        if (core == null || hand == null) {
            return false;
        }
        PatchService patches = core.patches();
        Material item = CraftMagicNumbers.getMaterial(hand.getItem());
        Material clicked = material(world.getType(position).getBlock());
        if (item != null && patches.isDurabilityChecker(item)) {
            patches.showDurability(player.getBukkitEntity(), world.getWorldData().getName(), position.asLong(), clicked);
            return true;
        }
        if (hand.getItem() == Items.POTION && ItemPotion.f(hand.getData()) && patches.launchesPotion(clicked)) {
            player.playerInteractManager.useItem(player, world, hand);
            return true;
        }
        if (AetherHooks.pearlForcesLaunch(hand, world.getType(position).getBlock())) {
            player.playerInteractManager.useItem(player, world, hand);
            return true;
        }
        return false;
    }

    /** Touche « jeter » pendant qu'on mange : refusée, sinon la nourriture est jetée et mangée. */
    public static boolean dropBlocked(EntityPlayer player) {
        AetherCore core = core();
        if (core == null || !core.engine().patchDropEat || !player.bS()) {
            return false;
        }
        ItemStack hand = player.inventory.getItemInHand();
        if (hand == null || !(hand.getItem() instanceof ItemFood || hand.getItem() == Items.POTION)) {
            return false;
        }
        player.getBukkitEntity().updateInventory();
        return true;
    }

    /** Paquet de déplacement, avant que le serveur l'applique : refuse l'entrée dans un bloc plein. */
    public static boolean phaseBlocked(EntityPlayer player, double x, double y, double z) {
        AetherCore core = core();
        if (core == null || !core.engine().trackMove) {
            return false;
        }
        int bx = MathHelper.floor(x);
        int by = MathHelper.floor(y);
        int bz = MathHelper.floor(z);
        if (bx == MathHelper.floor(player.locX) && by == MathHelper.floor(player.locY) && bz == MathHelper.floor(player.locZ)) {
            return false;
        }
        Material type = material(player.world.getType(new BlockPosition(bx, by, bz)).getBlock());
        if (!core.patches().phaseBlock(type)) {
            return false;
        }
        double localX = x - bx;
        double localZ = z - bz;
        double edge = Math.min(Math.min(localX, 1.0D - localX), Math.min(localZ, 1.0D - localZ));
        return edge > core.engine().phaseLeniency;
    }

    /** Thread du chat. */
    public static int chat(EntityPlayer player) {
        AetherCore core = core();
        return core == null ? PatchService.CHAT_OK : core.patches().chat(player.getBukkitEntity());
    }

    public static String spamKickMessage() {
        AetherCore core = core();
        return core == null ? "disconnect.spam" : core.patches().spamKickMessage();
    }

    /** Régénération naturelle (nourriture, ou mode paisible). */
    public static float naturalRegen(EntityHuman human, float amount) {
        AetherCore core = core();
        if (core == null || !core.engine().patchRegen || !(human instanceof EntityPlayer)) {
            return amount;
        }
        return (float) (amount * core.engine().regenMultiplier);
    }

    /** La faim ne baisse plus. */
    public static boolean hungerLocked(EntityHuman human) {
        AetherCore core = core();
        return core != null && core.engine().hungerLock && human instanceof EntityPlayer;
    }

    /** EntityHuman.a(BlockPosition) : le lit refuse le joueur. */
    public static boolean sleepBlocked(EntityHuman human) {
        AetherCore core = core();
        return core != null && !core.engine().checkSleep && human instanceof EntityPlayer;
    }

    /** World.addEntity, avant CreatureSpawnEvent. */
    public static boolean spawnBlocked(EntityLiving entity, SpawnReason reason) {
        AetherCore core = core();
        if (core == null || entity instanceof EntityHuman) {
            return false;
        }
        Engine engine = core.engine();
        if (!engine.naturalSpawn && (reason == SpawnReason.NATURAL || reason == SpawnReason.CHUNK_GEN || reason == SpawnReason.DEFAULT)) {
            return true;
        }
        if (entity instanceof EntityZombie) {
            EntityZombie zombie = (EntityZombie) entity;
            if (zombie.isVillager() && !engine.villagerZombie) {
                return true;
            }
            if (zombie.isBaby() && zombie.vehicle instanceof EntityChicken && !engine.chickenJockey) {
                return true;
            }
            if (zombie.isBaby() && !engine.babyZombies) {
                return true;
            }
        }
        if (entity instanceof EntityChicken && entity.passenger instanceof EntityZombie && !engine.chickenJockey) {
            return true;
        }
        if (entity instanceof EntitySpider && entity.passenger instanceof EntitySkeleton && !engine.spiderJockey) {
            return true;
        }
        if (reason != SpawnReason.SPAWNER) {
            return false;
        }
        int chunkX = MathHelper.floor(entity.locX) >> 4;
        int chunkZ = MathHelper.floor(entity.locZ) >> 4;
        if (engine.chunkMobLimit > 0 || engine.nearbySpawner > 0) {
            int inChunk = 0;
            int nearby = 0;
            for (Object object : entity.world.entityList) {
                Entity other = (Entity) object;
                if ((MathHelper.floor(other.locX) >> 4) == chunkX && (MathHelper.floor(other.locZ) >> 4) == chunkZ) {
                    inChunk++;
                }
                if (other instanceof EntityLiving && other.h(entity) < 256.0D) {
                    nearby++;
                }
            }
            if (engine.chunkMobLimit > 0 && inChunk >= engine.chunkMobLimit) {
                return true;
            }
            return engine.nearbySpawner > 0 && nearby >= engine.nearbySpawner;
        }
        return false;
    }

    /** EntityInsentient.setGoalTarget : sans IA, les monstres ne prennent pas de cible. */
    public static boolean targetBlocked(EntityInsentient mob) {
        AetherCore core = core();
        return core != null && !core.engine().mobAi && mob instanceof IMonster;
    }

    /** Dégâts de chute : multiplicateur practice et golems. */
    public static float fallDamage(EntityLiving entity, float damage) {
        AetherCore core = core();
        if (core == null) {
            return damage;
        }
        if (entity instanceof EntityIronGolem && !core.engine().ironFall) {
            return 0.0F;
        }
        if (entity instanceof EntitySnowman && !core.engine().snowFall) {
            return 0.0F;
        }
        return AetherHooks.fallDamage(damage);
    }

    /** Explosion : les blocs à durabilité doivent être comptés pendant la recherche des blocs. */
    public static boolean tracksDurability(Entity source) {
        AetherCore core = core();
        if (core == null || !core.engine().explosionDestroyer || core.engine().blockDurability.isEmpty()) {
            return false;
        }
        return !core.engine().damageOnlyTnt || source instanceof EntityTNTPrimed;
    }

    public static boolean durable(Block block) {
        AetherCore core = core();
        Material type = material(block);
        return core != null && type != null && core.patches().durable(type);
    }

    /**
     * Fin de la recherche : chaque bloc à durabilité touché perd un point, et ne reste dans
     * l'explosion que s'il tombe à zéro.
     */
    public static void applyDurability(World world, Entity source, it.unimi.dsi.fastutil.longs.LongSet reached,
                                       it.unimi.dsi.fastutil.longs.LongSet blocks) {
        AetherCore core = core();
        if (core == null) {
            return;
        }
        String name = world.getWorldData().getName();
        for (it.unimi.dsi.fastutil.longs.LongIterator it = reached.iterator(); it.hasNext(); ) {
            long position = it.nextLong();
            blocks.remove(position);
            Material type = material(world.getType(BlockPosition.fromLong(position)).getBlock());
            if (type != null && core.patches().explosionHit(name, position, type)) {
                blocks.add(position);
            }
        }
    }

    /** Sans tracksDurability, les blocs à durabilité ne cassent pas (damageOnlyByTNT). */
    public static void protectDurable(World world, it.unimi.dsi.fastutil.longs.LongSet blocks) {
        AetherCore core = core();
        if (core == null || !core.engine().explosionDestroyer || core.engine().blockDurability.isEmpty()) {
            return;
        }
        for (it.unimi.dsi.fastutil.longs.LongIterator it = blocks.iterator(); it.hasNext(); ) {
            if (durable(world.getType(BlockPosition.fromLong(it.nextLong())).getBlock())) {
                it.remove();
            }
        }
    }

    /** Fin d'EntityPlayer.die : réapparition automatique sans écran de mort. */
    public static void playerDied(final EntityPlayer player) {
        AetherCore core = core();
        if (core == null || !core.engine().autoRespawn) {
            return;
        }
        core.getServer().getScheduler().runTaskLater(core, new Runnable() {
            @Override
            public void run() {
                if (player.playerConnection != null && !player.playerConnection.isDisconnected() && player.getHealth() <= 0.0F) {
                    player.playerConnection.a(new PacketPlayInClientCommand(PacketPlayInClientCommand.EnumClientCommand.PERFORM_RESPAWN));
                }
            }
        }, 2L);
    }

    /** PlayerList.attemptLogin, avant PlayerLoginEvent : refuse les clients trop anciens (ViaVersion). */
    public static String loginKick(org.bukkit.entity.Player player) {
        AetherCore core = core();
        return core == null ? null : core.patches().loginKick(player);
    }

    /** CraftServer.createWorld, avant WorldLoadEvent. */
    public static void worldLoaded() {
        AetherCore core = core();
        if (core != null) {
            core.applyWorlds();
        }
    }
}
