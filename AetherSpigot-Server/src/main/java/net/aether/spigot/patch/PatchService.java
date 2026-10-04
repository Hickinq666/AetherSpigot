package net.aether.spigot.patch;

import net.aether.spigot.AetherCore;
import net.aether.spigot.config.Engine;
import net.aether.spigot.runtime.Online;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * État et règles des patchs serveur. Le code NMS l'appelle via {@link org.aetherspigot.PatchHooks}
 * au moment de l'action (lancer, déplacement, explosion, apparition…), pas après coup.
 */
public final class PatchService {

    public static final int CHAT_OK = 0;
    public static final int CHAT_REFUSE = 1;
    public static final int CHAT_KICK = 2;

    private final AetherCore plugin;
    private final Map<UUID, long[]> chat = new ConcurrentHashMap<UUID, long[]>();
    private final Map<String, int[]> durability = new ConcurrentHashMap<String, int[]>();

    public PatchService(AetherCore plugin) {
        this.plugin = plugin;
    }

    /** Thread du chat (asynchrone) : compte les messages de la seconde en cours. */
    public int chat(Player player) {
        Engine engine = plugin.engine();
        if (engine.chatSpamPerSecond <= 0) {
            return CHAT_OK;
        }
        long now = System.currentTimeMillis();
        long[] window = chat.get(player.getUniqueId());
        if (window == null || now - window[0] >= 1000L) {
            window = new long[]{now, 0L};
            chat.put(player.getUniqueId(), window);
        }
        synchronized (window) {
            window[1]++;
            if (window[1] <= engine.chatSpamPerSecond) {
                return CHAT_OK;
            }
        }
        if (engine.chatSpamKick) {
            return CHAT_KICK;
        }
        plugin.messages().send(player, "chat.spam", null);
        return CHAT_REFUSE;
    }

    public String spamKickMessage() {
        return plugin.messages().one("player.disconnectSpam", null);
    }

    public boolean durable(Material type) {
        return plugin.engine().blockDurability.containsKey(type.name());
    }

    /**
     * Un rayon d'explosion a atteint ce bloc. Retourne true si le bloc doit casser maintenant.
     * Le compteur repart de zéro quand le bloc à cette position a changé.
     */
    public boolean explosionHit(String world, long position, Material type) {
        Integer max = plugin.engine().blockDurability.get(type.name());
        if (max == null) {
            return true;
        }
        String key = world + ":" + position;
        int[] state = durability.get(key);
        if (state == null || state[0] != type.ordinal()) {
            state = new int[]{type.ordinal(), max.intValue()};
        }
        state[1]--;
        if (state[1] <= 0) {
            durability.remove(key);
            return true;
        }
        durability.put(key, state);
        return false;
    }

    /** Résistance restante d'un bloc, pour l'outil de vérification. */
    public void showDurability(Player player, String world, long position, Material type) {
        Integer max = plugin.engine().blockDurability.get(type.name());
        int left = 0;
        if (max != null) {
            int[] state = durability.get(world + ":" + position);
            left = state == null || state[0] != type.ordinal() ? max.intValue() : state[1];
        }
        Map<String, String> values = new HashMap<String, String>();
        values.put("block", type.name().toLowerCase(Locale.ROOT));
        values.put("durability", Integer.toString(left));
        plugin.messages().send(player, "durability.checker", values);
    }

    public boolean isDurabilityChecker(Material type) {
        return type.name().equalsIgnoreCase(plugin.engine().durabilityItem);
    }

    public boolean launchesPotion(Material clicked) {
        return plugin.engine().potionLaunch.contains(clicked.name());
    }

    /** Bloc plein dans lequel un joueur ne doit pas entrer (anti-phase). */
    public boolean phaseBlock(Material type) {
        if (type == null || type == Material.AIR || !type.isSolid()) {
            return false;
        }
        String name = type.name();
        if (name.contains("FENCE") || name.contains("STEP") || name.contains("STAIR") || name.contains("DOOR")
                || name.contains("PLATE") || name.contains("SIGN") || name.contains("BED") || name.contains("CARPET")
                || name.contains("TRAP") || name.contains("PISTON") || name.contains("WALL")) {
            return false;
        }
        return plugin.engine().phaseChests || !(name.contains("CHEST") || "ANVIL".equals(name));
    }

    public String loginKick(Player player) {
        Engine engine = plugin.engine();
        if (!engine.viaEnabled || engine.viaKickBelow <= 0 || !plugin.via().present()) {
            return null;
        }
        int protocol = plugin.via().protocol(player);
        return protocol >= 0 && protocol < engine.viaKickBelow ? "Client trop ancien pour ce serveur 1.8.8." : null;
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
}
