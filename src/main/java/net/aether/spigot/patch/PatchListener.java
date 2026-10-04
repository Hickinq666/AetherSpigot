package net.aether.spigot.patch;

import net.aether.spigot.AetherPlugin;
import net.aether.spigot.config.Engine;
import net.aether.spigot.runtime.NmsBridge;
import net.aether.spigot.runtime.Online;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Fish;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Spider;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PatchListener implements Listener {

    private final AetherPlugin plugin;
    private final Map<UUID, Long> eating = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, Integer> chatCount = new ConcurrentHashMap<UUID, Integer>();
    private final Map<UUID, Long> chatWindow = new ConcurrentHashMap<UUID, Long>();
    private final Map<String, Integer> hits = new ConcurrentHashMap<String, Integer>();

    public PatchListener(AetherPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onRegen(EntityRegainHealthEvent event) {
        Engine engine = plugin.engine();
        if (!engine.patchRegen || !(event.getEntity() instanceof Player)) {
            return;
        }
        if (!"REGEN".equals(event.getRegainReason().name())) {
            return;
        }
        event.setAmount(event.getAmount() * engine.regenMultiplier);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!plugin.engine().patchDropEat) {
            return;
        }
        Long started = eating.get(event.getPlayer().getUniqueId());
        if (started != null && System.currentTimeMillis() - started.longValue() < 2000L) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        eating.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || event.getAction() == null) {
            return;
        }
        String action = event.getAction().name();
        if (!action.startsWith("RIGHT_CLICK")) {
            return;
        }
        if (food(item.getType())) {
            eating.put(event.getPlayer().getUniqueId(), Long.valueOf(System.currentTimeMillis()));
        }
        Engine engine = plugin.engine();
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        if (item.getType().name().equalsIgnoreCase(engine.durabilityItem)) {
            event.setCancelled(true);
            Integer max = engine.blockDurability.get(clicked.getType().name());
            int left = max == null ? 0 : hits.getOrDefault(key(clicked), max).intValue();
            Map<String, String> values = new HashMap<String, String>();
            values.put("block", clicked.getType().name().toLowerCase(Locale.ROOT));
            values.put("durability", Integer.toString(left));
            plugin.messages().send(event.getPlayer(), "durability.checker", values);
            return;
        }
        if (item.getType() != Material.POTION || !engine.potionLaunch.contains(clicked.getType().name())) {
            return;
        }
        event.setCancelled(true);
        ThrownPotion potion = event.getPlayer().launchProjectile(ThrownPotion.class);
        potion.setItem(item.clone());
        consumeOne(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        ProjectileSource source = projectile.getShooter();
        if (!(source instanceof Player)) {
            return;
        }
        Player player = (Player) source;
        Engine engine = plugin.engine();
        if (projectile instanceof ThrownPotion) {
            adjustPotion(player, (ThrownPotion) projectile, engine);
            return;
        }
        if (!engine.patchMisdirection && randomness(projectile, engine) == 1.0D) {
            return;
        }
        Vector velocity = projectile.getVelocity();
        double speed = velocity.length();
        if (speed < 1.0E-4D) {
            return;
        }
        Vector look = player.getEyeLocation().getDirection();
        Vector current = velocity.clone().normalize();
        if (engine.patchMisdirection && current.dot(look) < 0.95D) {
            velocity = look.multiply(speed);
            projectile.setVelocity(velocity);
        }
        double factor = randomness(projectile, engine);
        if (Math.abs(factor - 1.0D) < 0.001D) {
            return;
        }
        Vector exact = player.getEyeLocation().getDirection().normalize().multiply(speed);
        Vector mixed = exact.multiply(1.0D - factor).add(projectile.getVelocity().multiply(factor));
        if (factor > 1.0D) {
            mixed.add(new Vector(Math.random() - 0.5D, Math.random() - 0.5D, Math.random() - 0.5D).multiply((factor - 1.0D) * 0.15D));
        }
        projectile.setVelocity(mixed);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Engine engine = plugin.engine();
        if (!engine.trackMove || event.getTo() == null) {
            return;
        }
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to.getBlockX() == from.getBlockX() && to.getBlockY() == from.getBlockY() && to.getBlockZ() == from.getBlockZ()) {
            return;
        }
        Block feet = to.getBlock();
        if (!phaseBlock(feet.getType(), engine)) {
            return;
        }
        double localX = to.getX() - feet.getX();
        double localZ = to.getZ() - feet.getZ();
        double edge = Math.min(Math.min(localX, 1.0D - localX), Math.min(localZ, 1.0D - localZ));
        if (edge > engine.phaseLeniency) {
            event.setTo(from);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Engine engine = plugin.engine();
        if (engine.chatSpamPerSecond <= 0) {
            return;
        }
        UUID id = event.getPlayer().getUniqueId();
        long now = System.currentTimeMillis();
        Long window = chatWindow.get(id);
        int count = 0;
        if (window != null && now - window.longValue() < 1000L) {
            Integer known = chatCount.get(id);
            count = known == null ? 0 : known.intValue();
        } else {
            chatWindow.put(id, Long.valueOf(now));
        }
        count++;
        chatCount.put(id, Integer.valueOf(count));
        if (count <= engine.chatSpamPerSecond) {
            return;
        }
        event.setCancelled(true);
        final Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (plugin.engine().chatSpamKick) {
                    player.kickPlayer(plugin.messages().one("player.disconnectSpam", null));
                } else {
                    plugin.messages().send(player, "chat.spam", null);
                }
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(org.bukkit.event.entity.FoodLevelChangeEvent event) {
        if (!plugin.engine().hungerLock || !(event.getEntity() instanceof Player)) {
            return;
        }
        if (event.getFoodLevel() < ((Player) event.getEntity()).getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        Engine engine = plugin.engine();
        String reason = event.getSpawnReason().name();
        if (!engine.naturalSpawn && ("NATURAL".equals(reason) || "CHUNK_GEN".equals(reason) || "DEFAULT".equals(reason))) {
            event.setCancelled(true);
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof Zombie) {
            Zombie zombie = (Zombie) entity;
            if (zombie.isVillager() && !engine.villagerZombie) {
                event.setCancelled(true);
                return;
            }
            if (zombie.isBaby() && zombie.getVehicle() instanceof Chicken && !engine.chickenJockey) {
                event.setCancelled(true);
                return;
            }
            if (zombie.isBaby() && !engine.babyZombies) {
                event.setCancelled(true);
                return;
            }
        }
        if (entity instanceof Chicken && entity.getPassenger() instanceof Zombie && !engine.chickenJockey) {
            event.setCancelled(true);
            return;
        }
        if (entity instanceof Spider && entity.getPassenger() instanceof Skeleton && !engine.spiderJockey) {
            event.setCancelled(true);
            return;
        }
        if (!"SPAWNER".equals(reason)) {
            return;
        }
        if (engine.chunkMobLimit > 0 && event.getLocation().getChunk().getEntities().length >= engine.chunkMobLimit) {
            event.setCancelled(true);
            return;
        }
        if (engine.nearbySpawner > 0) {
            int nearby = 0;
            Entity[] around = event.getLocation().getChunk().getEntities();
            for (int i = 0; i < around.length; i++) {
                if (around[i] instanceof LivingEntity && around[i].getLocation().distanceSquared(event.getLocation()) < 256.0D) {
                    nearby++;
                }
            }
            if (nearby >= engine.nearbySpawner) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (!plugin.engine().mobAi && event.getEntity() instanceof Monster) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMobHit(EntityDamageByEntityEvent event) {
        if (!plugin.engine().mobAi && event.getDamager() instanceof Monster && event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGolemFall(EntityDamageEvent event) {
        Engine engine = plugin.engine();
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        if (event.getEntity() instanceof IronGolem && !engine.ironFall) {
            event.setCancelled(true);
        }
        if (event.getEntity() instanceof Snowman && !engine.snowFall) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBed(PlayerBedEnterEvent event) {
        if (!plugin.engine().checkSleep) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        Engine engine = plugin.engine();
        if (!engine.explosionDestroyer) {
            return;
        }
        boolean tnt = event.getEntity() instanceof TNTPrimed;
        Iterator<Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            Integer max = engine.blockDurability.get(block.getType().name());
            if (max == null) {
                continue;
            }
            if (engine.damageOnlyTnt && !tnt) {
                iterator.remove();
                continue;
            }
            String id = key(block);
            int left = hits.getOrDefault(id, max).intValue() - 1;
            if (left > 0) {
                hits.put(id, Integer.valueOf(left));
                iterator.remove();
            } else {
                hits.remove(id);
            }
        }
    }

    @EventHandler
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        if (!plugin.engine().autoRespawn) {
            return;
        }
        final Player player = event.getEntity();
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline() && player.isDead()) {
                    NmsBridge.respawn(player);
                }
            }
        }, 2L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        eating.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent event) {
        Engine engine = plugin.engine();
        if (!engine.viaEnabled || engine.viaKickBelow <= 0 || !plugin.via().present()) {
            return;
        }
        int protocol = plugin.via().protocol(event.getPlayer());
        if (protocol >= 0 && protocol < engine.viaKickBelow) {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, "Client trop ancien pour ce serveur 1.8.8.");
        }
    }

    @EventHandler
    public void onPing(ServerListPingEvent event) {
        if (plugin.forcedSlots() > 0) {
            event.setMaxPlayers(plugin.forcedSlots());
        }
    }

    @EventHandler
    public void onWorld(WorldLoadEvent event) {
        plugin.applyWorlds();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        hits.remove(key(event.getBlock()));
    }

    private void adjustPotion(Player player, ThrownPotion potion, Engine engine) {
        ItemStack item = potion.getItem();
        boolean health = false;
        try {
            Potion brewed = Potion.fromItemStack(item);
            health = brewed != null && brewed.isSplash() && brewed.getType() == PotionType.INSTANT_HEAL;
        } catch (Throwable ignored) {
            health = false;
        }
        if (engine.overrideOnlyHealth && !health) {
            return;
        }
        Location eye = player.getEyeLocation().clone();
        eye.setPitch(eye.getPitch() + (float) plugin.profileFor(player).potionVerticalOffset);
        double speed = plugin.profileFor(player).potionSpeed;
        if (health && plugin.profileFor(player).potionFast) {
            speed *= 1.2D;
        }
        potion.setVelocity(eye.getDirection().normalize().multiply(Math.max(0.05D, speed)));
    }

    private static double randomness(Projectile projectile, Engine engine) {
        if (projectile instanceof Arrow) {
            return engine.arrowRandomness;
        }
        if (projectile instanceof Fish) {
            return engine.rodRandomness;
        }
        if (projectile instanceof Snowball) {
            return engine.snowRandomness;
        }
        if (projectile instanceof Egg) {
            return engine.eggRandomness;
        }
        if (projectile instanceof EnderPearl) {
            return engine.pearlRandomness;
        }
        return 1.0D;
    }

    private static boolean phaseBlock(Material type, Engine engine) {
        if (type == null || type == Material.AIR || !type.isSolid()) {
            return false;
        }
        String name = type.name();
        if (name.contains("FENCE") || name.contains("STEP") || name.contains("STAIR") || name.contains("DOOR")
                || name.contains("PLATE") || name.contains("SIGN") || name.contains("BED") || name.contains("CARPET")
                || name.contains("TRAP") || name.contains("PISTON") || name.contains("WALL")) {
            return false;
        }
        if (!engine.phaseChests && (name.contains("CHEST") || "ANVIL".equals(name))) {
            return false;
        }
        return true;
    }

    private static boolean food(Material type) {
        switch (type) {
            case APPLE:
            case BAKED_POTATO:
            case BREAD:
            case CARROT_ITEM:
            case COOKED_BEEF:
            case COOKED_CHICKEN:
            case COOKED_FISH:
            case COOKED_MUTTON:
            case COOKIE:
            case GOLDEN_APPLE:
            case GOLDEN_CARROT:
            case GRILLED_PORK:
            case MELON:
            case MUSHROOM_SOUP:
            case MUTTON:
            case POISONOUS_POTATO:
            case PORK:
            case POTATO_ITEM:
            case PUMPKIN_PIE:
            case RAW_BEEF:
            case RAW_CHICKEN:
            case RAW_FISH:
            case ROTTEN_FLESH:
            case SPIDER_EYE:
                return true;
            default:
                return false;
        }
    }

    private static void consumeOne(Player player) {
        ItemStack hand = player.getItemInHand();
        if (hand == null) {
            return;
        }
        if (hand.getAmount() <= 1) {
            player.setItemInHand(null);
        } else {
            hand.setAmount(hand.getAmount() - 1);
        }
    }

    public static int clear(Engine engine) {
        int removed = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                boolean drop = false;
                if (engine.clearItems && entity instanceof Item) {
                    drop = true;
                } else if (engine.clearArrows && entity instanceof Arrow) {
                    drop = true;
                } else if (engine.clearXp && entity instanceof ExperienceOrb) {
                    drop = true;
                } else if (engine.clearMonsters && entity instanceof Monster && ((Monster) entity).getCustomName() == null) {
                    drop = true;
                }
                if (drop) {
                    entity.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    public static int unloadChunks() {
        int unloaded = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            org.bukkit.Chunk[] chunks = world.getLoadedChunks();
            for (int i = 0; i < chunks.length; i++) {
                if (playersNear(chunks[i])) {
                    continue;
                }
                if (chunks[i].unload(true, true)) {
                    unloaded++;
                }
            }
        }
        return unloaded;
    }

    private static boolean playersNear(org.bukkit.Chunk chunk) {
        for (Player player : Online.players()) {
            if (!player.getWorld().equals(chunk.getWorld())) {
                continue;
            }
            int dx = player.getLocation().getChunk().getX() - chunk.getX();
            int dz = player.getLocation().getChunk().getZ() - chunk.getZ();
            if (dx * dx + dz * dz <= 4) {
                return true;
            }
        }
        return false;
    }

    private static String key(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
}
