package net.aether.spigot.pearl;

import net.aether.spigot.AetherPlugin;
import net.aether.spigot.config.Engine;
import net.aether.spigot.text.Colors;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PearlListener implements Listener {

    private final AetherPlugin plugin;
    private final Map<UUID, Long> cooldown = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<UUID, Pending>();
    private final Map<UUID, Boolean> forced = new ConcurrentHashMap<UUID, Boolean>();

    public PearlListener(AetherPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == null || event.getItem() == null) {
            return;
        }
        if (event.getItem().getType() != Material.ENDER_PEARL) {
            return;
        }
        if (event.getAction().name().equals("PHYSICAL") || event.getAction().name().equals("LEFT_CLICK_AIR")
                || event.getAction().name().equals("LEFT_CLICK_BLOCK")) {
            return;
        }
        Player player = event.getPlayer();
        Engine engine = plugin.engine();
        if (cooling(player)) {
            event.setCancelled(true);
            plugin.messages().send(player, "pearl.cooldown", seconds(player));
            return;
        }
        if (blockedSpawn(player, engine)) {
            event.setCancelled(true);
            plugin.messages().send(player, "pearl.disabled", null);
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked != null && shouldForce(clicked.getType(), engine)) {
            event.setCancelled(true);
            consume(player);
            forced.put(player.getUniqueId(), Boolean.TRUE);
            player.launchProjectile(EnderPearl.class);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl)) {
            return;
        }
        if (!(event.getEntity().getShooter() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity().getShooter();
        Engine engine = plugin.engine();
        if (forced.remove(player.getUniqueId()) != null) {
            startCooldown(player, engine);
            return;
        }
        if (cooling(player) || blockedSpawn(player, engine)) {
            event.setCancelled(true);
            player.getInventory().addItem(new ItemStack(Material.ENDER_PEARL, 1));
            plugin.messages().send(player, "pearl.cooldown", seconds(player));
            return;
        }
        startCooldown(player, engine);
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof EnderPearl)) {
            return;
        }
        if (!(event.getEntity().getShooter() instanceof Player)) {
            return;
        }
        final Player player = (Player) event.getEntity().getShooter();
        EnderPearl pearl = (EnderPearl) event.getEntity();
        Engine engine = plugin.engine();
        Vector velocity = pearl.getVelocity();
        if (velocity.lengthSquared() < 1.0E-6D) {
            velocity = player.getLocation().getDirection();
        }
        Vector direction = velocity.clone().normalize();
        Block hit = findHit(pearl.getLocation(), direction);
        boolean risky = liquid(hit) || liquid(pearl.getLocation().getBlock());
        int stepX = sign(direction.getX());
        int stepY = sign(direction.getY());
        int stepZ = sign(direction.getZ());
        final PearlMath.Decision decision = PearlMath.resolve(
                hit.getX(), hit.getY(), hit.getZ(), stepX, stepY, stepZ,
                player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ(),
                risky, new WorldView(hit.getWorld()), engine.pearls);
        final Location origin = player.getLocation().clone();
        Pending state = new Pending(decision, origin, System.currentTimeMillis());
        pending.put(player.getUniqueId(), state);
        plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                finish(player, state);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Pending state = pending.get(event.getPlayer().getUniqueId());
        if (state == null || System.currentTimeMillis() - state.at > 1500L) {
            return;
        }
        if (state.decision.type == PearlMath.Type.REFUND) {
            event.setCancelled(true);
            state.handled = true;
            return;
        }
        if (state.decision.type == PearlMath.Type.TELEPORT && plugin.engine().pearls.instantlyTaliTeleport) {
            event.setTo(destination(event.getPlayer(), state.decision));
            state.handled = true;
        }
    }

    private void finish(Player player, Pending state) {
        if (!player.isOnline()) {
            pending.remove(player.getUniqueId());
            return;
        }
        Engine engine = plugin.engine();
        if (state.decision.type == PearlMath.Type.REFUND) {
            if (player.getLocation().distanceSquared(state.origin) > 4.0D) {
                player.teleport(state.origin);
            }
            if (state.decision.returnPearl) {
                player.getInventory().addItem(new ItemStack(Material.ENDER_PEARL, 1));
            }
            cooldown.remove(player.getUniqueId());
            String reason = state.decision.reason == null ? "" : state.decision.reason;
            if (engine.glitchMessage != null && !engine.glitchMessage.isEmpty()) {
                player.sendMessage(Colors.color(engine.glitchMessage.replace("%player%", player.getName()).replace("%reason%", reason)));
            } else {
                Map<String, String> values = new HashMap<String, String>();
                values.put("reason", reason);
                values.put("player", player.getName());
                plugin.messages().send(player, "pearl.refund", values);
            }
            if (engine.glitchCommand != null && !engine.glitchCommand.isEmpty()) {
                String command = engine.glitchCommand.replace("%player%", player.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
        } else if (state.decision.type == PearlMath.Type.TELEPORT) {
            Location dest = nudge(player, destination(player, state.decision));
            if (!state.handled || player.getLocation().distanceSquared(dest) > 1.0D) {
                player.teleport(dest);
            }
            if (engine.pearls.pearlDamage > 0.0D) {
                player.damage(engine.pearls.pearlDamage);
            }
            if (engine.pearls.pearlParticlesEnabled) {
                int count = Math.min(48, Math.max(1, engine.pearls.pearlParticleCount));
                for (Player viewer : player.getWorld().getPlayers()) {
                    // Un joueur qui a caché le lanceur ne doit pas voir ses particules.
                    if (viewer != player && !viewer.canSee(player)) {
                        continue;
                    }
                    if (viewer.getLocation().distanceSquared(dest) > 64.0D * 64.0D) {
                        continue;
                    }
                    for (int i = 0; i < count; i++) {
                        viewer.playEffect(dest, Effect.ENDER_SIGNAL, 0);
                    }
                }
            }
            if (engine.pearls.spawnEndermite) {
                player.getWorld().spawnEntity(dest, EntityType.ENDERMITE);
            }
        }
        pending.remove(player.getUniqueId());
    }

    private Location nudge(Player player, Location dest) {
        if (!plugin.engine().pearls.entityTpHitDetection) {
            return dest;
        }
        List<Player> players = net.aether.spigot.runtime.Online.players();
        for (int i = 0; i < players.size(); i++) {
            Player other = players.get(i);
            if (other.equals(player) || !other.getWorld().equals(dest.getWorld())) {
                continue;
            }
            if (other.getLocation().distanceSquared(dest) < 0.36D) {
                Vector away = dest.toVector().subtract(other.getLocation().toVector());
                if (away.lengthSquared() < 1.0E-4D) {
                    away = new Vector(0.4D, 0.0D, 0.0D);
                }
                dest.add(away.normalize().multiply(0.4D));
            }
        }
        return dest;
    }

    private Location destination(Player player, PearlMath.Decision decision) {
        return new Location(player.getWorld(), decision.x, decision.y, decision.z, player.getLocation().getYaw(), player.getLocation().getPitch());
    }

    private boolean shouldForce(Material type, Engine engine) {
        String name = type.name();
        if (engine.launchFence && (name.contains("FENCE") || "FENCE_GATE".equals(name))) {
            return true;
        }
        return engine.pearlLaunch.contains(name);
    }

    private boolean cooling(Player player) {
        Long until = cooldown.get(player.getUniqueId());
        return until != null && until.longValue() > System.currentTimeMillis();
    }

    private void startCooldown(Player player, Engine engine) {
        if (engine.pearlCooldownSeconds <= 0) {
            return;
        }
        cooldown.put(player.getUniqueId(), Long.valueOf(System.currentTimeMillis() + engine.pearlCooldownSeconds * 1000L));
        Map<String, String> values = new HashMap<String, String>();
        values.put("seconds", Integer.toString(engine.pearlCooldownSeconds));
        plugin.messages().send(player, "pearl.thrown", values);
    }

    private Map<String, String> seconds(Player player) {
        Long until = cooldown.get(player.getUniqueId());
        long left = until == null ? 0L : Math.max(0L, until.longValue() - System.currentTimeMillis());
        Map<String, String> values = new HashMap<String, String>();
        values.put("seconds", Long.toString((left + 999L) / 1000L));
        values.put("player", player.getName());
        return values;
    }

    private boolean blockedSpawn(Player player, Engine engine) {
        if (!engine.blockPearlsInSpawn) {
            return false;
        }
        Location spawn = player.getWorld().getSpawnLocation();
        return player.getLocation().distanceSquared(spawn) <= engine.spawnRadius * engine.spawnRadius;
    }

    private static void consume(Player player) {
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

    private static Block findHit(Location start, Vector direction) {
        World world = start.getWorld();
        Block last = start.getBlock();
        for (double distance = 0.0D; distance <= 2.0D; distance += 0.2D) {
            Location at = start.clone().add(direction.clone().multiply(distance));
            Block block = at.getBlock();
            if (block.getType() != Material.AIR && !passable(block.getType())) {
                return block;
            }
            last = block;
        }
        return last;
    }

    private static boolean passable(Material type) {
        PearlMath.BlockKind kind = WorldView.kindOf(type);
        return kind.passable();
    }

    private static boolean liquid(Block block) {
        if (block == null) {
            return false;
        }
        Material type = block.getType();
        return type == Material.LAVA || type == Material.STATIONARY_LAVA || type == Material.FIRE;
    }

    private static int sign(double value) {
        if (value > 0.15D) {
            return 1;
        }
        if (value < -0.15D) {
            return -1;
        }
        return 0;
    }

    private static final class Pending {
        private final PearlMath.Decision decision;
        private final Location origin;
        private final long at;
        private boolean handled;

        private Pending(PearlMath.Decision decision, Location origin, long at) {
            this.decision = decision;
            this.origin = origin;
            this.at = at;
        }
    }

    private static final class WorldView implements PearlMath.BlockView {
        private final World world;

        private WorldView(World world) {
            this.world = world;
        }

        public PearlMath.BlockKind kind(int x, int y, int z) {
            if (y < 0 || y > 255) {
                return PearlMath.BlockKind.SOLID;
            }
            return kindOf(world.getBlockAt(x, y, z).getType());
        }

        static PearlMath.BlockKind kindOf(Material type) {
            if (type == null || type == Material.AIR) {
                return PearlMath.BlockKind.AIR;
            }
            String name = type.name().toUpperCase(Locale.ROOT);
            if (name.contains("FENCE")) {
                return PearlMath.BlockKind.FENCE;
            }
            if ("WEB".equals(name)) {
                return PearlMath.BlockKind.WEB;
            }
            if ("STRING".equals(name) || "TRIPWIRE".equals(name)) {
                return PearlMath.BlockKind.STRING;
            }
            if (plant(name)) {
                return PearlMath.BlockKind.PLANT;
            }
            if (name.contains("STEP") && !name.contains("DOUBLE")) {
                return PearlMath.BlockKind.SLAB;
            }
            if (name.contains("STAIR")) {
                return PearlMath.BlockKind.STAIR;
            }
            if (name.contains("CHEST")) {
                return PearlMath.BlockKind.CHEST;
            }
            if (name.contains("BED")) {
                return PearlMath.BlockKind.BED;
            }
            if (name.contains("COBBLE_WALL") || name.contains("WALL")) {
                return PearlMath.BlockKind.WALL;
            }
            if (name.contains("PISTON")) {
                return PearlMath.BlockKind.PISTON;
            }
            if (name.contains("ENDER_PORTAL_FRAME")) {
                return PearlMath.BlockKind.END_FRAME;
            }
            if (name.contains("ENCHANT")) {
                return PearlMath.BlockKind.ENCHANT;
            }
            if ("ANVIL".equals(name)) {
                return PearlMath.BlockKind.ANVIL;
            }
            if (name.contains("DAYLIGHT")) {
                return PearlMath.BlockKind.DAYLIGHT;
            }
            if (name.contains("TRAP_DOOR") || name.contains("TRAPDOOR")) {
                return PearlMath.BlockKind.TRAPDOOR;
            }
            if ("HOPPER".equals(name)) {
                return PearlMath.BlockKind.HOPPER;
            }
            if (!type.isSolid()) {
                return PearlMath.BlockKind.OTHER;
            }
            return PearlMath.BlockKind.SOLID;
        }

        private static boolean plant(String name) {
            return "LONG_GRASS".equals(name) || "DOUBLE_PLANT".equals(name) || "YELLOW_FLOWER".equals(name)
                    || "RED_ROSE".equals(name) || "SAPLING".equals(name) || "DEAD_BUSH".equals(name)
                    || "VINE".equals(name) || "WATER_LILY".equals(name) || "CROPS".equals(name)
                    || "CARROT".equals(name) || "POTATO".equals(name) || "NETHER_WARTS".equals(name)
                    || "SUGAR_CANE_BLOCK".equals(name);
        }
    }
}
