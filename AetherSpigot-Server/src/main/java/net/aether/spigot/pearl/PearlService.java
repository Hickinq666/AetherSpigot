package net.aether.spigot.pearl;

import net.aether.spigot.AetherCore;
import net.aether.spigot.config.Engine;
import net.aether.spigot.text.Colors;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Règles d'enderpearl HCF. Appelé par ItemEnderPearl au lancer et par EntityEnderPearl à l'impact,
 * avant toute téléportation : le joueur n'est jamais déplacé puis corrigé.
 */
public final class PearlService {

    private final AetherCore plugin;
    private final Map<UUID, Long> cooldown = new ConcurrentHashMap<UUID, Long>();

    public PearlService(AetherCore plugin) {
        this.plugin = plugin;
    }

    /** Résultat de l'impact : rembourser, ou téléporter à {@link #destination}. */
    public static final class Landing {
        public final boolean refund;
        public final Location destination;

        private Landing(boolean refund, Location destination) {
            this.refund = refund;
            this.destination = destination;
        }
    }

    /** Avant le lancer. Retourne false (et prévient le joueur) si le cooldown ou le spawn l'interdit. */
    public boolean mayThrow(Player player) {
        Engine engine = plugin.engine();
        if (cooling(player)) {
            plugin.messages().send(player, "pearl.cooldown", seconds(player));
            return false;
        }
        if (blockedSpawn(player, engine)) {
            plugin.messages().send(player, "pearl.disabled", null);
            return false;
        }
        return true;
    }

    /** Après que la perle est entrée dans le monde. */
    public void thrown(Player player) {
        Engine engine = plugin.engine();
        if (engine.pearlCooldownSeconds <= 0) {
            return;
        }
        cooldown.put(player.getUniqueId(), Long.valueOf(System.currentTimeMillis() + engine.pearlCooldownSeconds * 1000L));
        Map<String, String> values = new HashMap<String, String>();
        values.put("seconds", Integer.toString(engine.pearlCooldownSeconds));
        plugin.messages().send(player, "pearl.thrown", values);
    }

    /** Un clic droit sur ce bloc lance la perle au lieu d'utiliser le bloc. */
    public boolean forcesLaunch(Material clicked) {
        Engine engine = plugin.engine();
        String name = clicked.name();
        if (engine.launchFence && name.contains("FENCE")) {
            return true;
        }
        return engine.pearlLaunch.contains(name);
    }

    /**
     * Impact de la perle, avant la téléportation. {@code hit} est le bloc touché (null si la perle a
     * touché une entité). Un remboursement est appliqué ici ; null laisse le comportement NachoSpigot.
     */
    public Landing land(Player player, Block hit, Location pearl, Vector velocity) {
        Engine engine = plugin.engine();
        if (velocity.lengthSquared() < 1.0E-6D) {
            velocity = player.getLocation().getDirection();
        }
        Vector direction = velocity.clone().normalize();
        if (hit == null) {
            hit = findHit(pearl, direction);
        }
        boolean risky = liquid(hit) || liquid(pearl.getBlock());
        Location from = player.getLocation();
        PearlMath.Decision decision = PearlMath.resolve(
                hit.getX(), hit.getY(), hit.getZ(), sign(direction.getX()), sign(direction.getY()), sign(direction.getZ()),
                from.getX(), from.getY(), from.getZ(), risky, new WorldView(hit.getWorld()), engine.pearls);
        if (decision.type == PearlMath.Type.REFUND) {
            refund(player, decision, engine);
            return new Landing(true, null);
        }
        if (decision.type == PearlMath.Type.TELEPORT) {
            Location dest = new Location(player.getWorld(), decision.x, decision.y, decision.z, from.getYaw(), from.getPitch());
            return new Landing(false, nudge(player, dest));
        }
        return null;
    }

    /** Après la téléportation : particules pour ceux qui voient le lanceur. */
    public void landed(Player player, Location dest) {
        Engine engine = plugin.engine();
        if (!engine.pearls.pearlParticlesEnabled) {
            return;
        }
        int count = Math.min(48, Math.max(1, engine.pearls.pearlParticleCount));
        for (Player viewer : player.getWorld().getPlayers()) {
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

    public float damage() {
        return (float) Math.max(0.0D, plugin.engine().pearls.pearlDamage);
    }

    public boolean endermite() {
        return plugin.engine().pearls.spawnEndermite;
    }

    private void refund(Player player, PearlMath.Decision decision, Engine engine) {
        if (decision.returnPearl) {
            player.getInventory().addItem(new ItemStack(Material.ENDER_PEARL, 1));
        }
        cooldown.remove(player.getUniqueId());
        String reason = decision.reason == null ? "" : decision.reason;
        if (engine.glitchMessage != null && !engine.glitchMessage.isEmpty()) {
            player.sendMessage(Colors.color(engine.glitchMessage.replace("%player%", player.getName()).replace("%reason%", reason)));
        } else {
            Map<String, String> values = new HashMap<String, String>();
            values.put("reason", reason);
            values.put("player", player.getName());
            plugin.messages().send(player, "pearl.refund", values);
        }
        if (engine.glitchCommand != null && !engine.glitchCommand.isEmpty()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), engine.glitchCommand.replace("%player%", player.getName()));
        }
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

    private boolean cooling(Player player) {
        Long until = cooldown.get(player.getUniqueId());
        return until != null && until.longValue() > System.currentTimeMillis();
    }

    private Map<String, String> seconds(Player player) {
        Long until = cooldown.get(player.getUniqueId());
        long left = until == null ? 0L : Math.max(0L, until.longValue() - System.currentTimeMillis());
        Map<String, String> values = new HashMap<String, String>();
        values.put("seconds", Long.toString((left + 999L) / 1000L));
        values.put("player", player.getName());
        return values;
    }

    private static boolean blockedSpawn(Player player, Engine engine) {
        if (!engine.blockPearlsInSpawn) {
            return false;
        }
        Location spawn = player.getWorld().getSpawnLocation();
        return player.getLocation().distanceSquared(spawn) <= engine.spawnRadius * engine.spawnRadius;
    }

    private static Block findHit(Location start, Vector direction) {
        Block last = start.getBlock();
        for (double distance = 0.0D; distance <= 2.0D; distance += 0.2D) {
            Block block = start.clone().add(direction.clone().multiply(distance)).getBlock();
            if (block.getType() != Material.AIR && !WorldView.kindOf(block.getType()).passable()) {
                return block;
            }
            last = block;
        }
        return last;
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
            if (name.contains("WALL")) {
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
