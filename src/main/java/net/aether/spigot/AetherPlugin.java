package net.aether.spigot;

import net.aether.spigot.command.CommandBridge;
import net.aether.spigot.config.ConfigStore;
import net.aether.spigot.config.Engine;
import net.aether.spigot.config.YamlDoc;
import net.aether.spigot.enchant.EnchantListener;
import net.aether.spigot.knockback.KnockbackListener;
import net.aether.spigot.knockback.KnockbackProfile;
import net.aether.spigot.menu.MenuListener;
import net.aether.spigot.menu.MenuService;
import net.aether.spigot.message.MessageService;
import net.aether.spigot.patch.PatchListener;
import net.aether.spigot.pearl.PearlListener;
import net.aether.spigot.runtime.SupportMatrix;
import net.aether.spigot.runtime.TpsCounter;
import net.aether.spigot.via.ViaHook;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AetherPlugin extends JavaPlugin {

    private ConfigStore store;
    private volatile Engine engine;
    private MessageService messages;
    private final TpsCounter tps = new TpsCounter();
    private ViaHook via;
    private MenuService menus;
    private final Map<UUID, String> playerKnockback = new ConcurrentHashMap<UUID, String>();
    private final Map<UUID, EditSession> edits = new ConcurrentHashMap<UUID, EditSession>();
    private final long startedAt = System.currentTimeMillis();
    private int forcedSlots = -1;

    @Override
    public void onEnable() {
        store = new ConfigStore(this);
        store.load();
        engine = Engine.load(store);
        messages = new MessageService(store);
        via = new ViaHook();
        menus = new MenuService(this);
        loadPlayerProfiles();
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(new KnockbackListener(this), this);
        getServer().getPluginManager().registerEvents(new PearlListener(this), this);
        getServer().getPluginManager().registerEvents(new net.aether.spigot.combat.CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new PatchListener(this), this);
        getServer().getPluginManager().registerEvents(new EnchantListener(this), this);
        CommandBridge bridge = new CommandBridge(this);
        String[] names = {"aether", "config", "knockback", "ping", "tps", "clearlag", "unloadchunks", "setmaxplayers", "hide", "see"};
        for (int i = 0; i < names.length; i++) {
            PluginCommand command = getCommand(names[i]);
            if (command != null) {
                command.setExecutor(bridge);
                command.setTabCompleter(bridge);
            }
        }
        getServer().getScheduler().runTaskTimer(this, tps, 1L, 1L);
        applyWorlds();
        if (engine.viaWarn && !via.present()) {
            getLogger().warning("ViaVersion est absent. Lance scripts/bundle-via.sh puis place les jars dans plugins/.");
        } else if (via.present()) {
            getLogger().info("ViaVersion detecte. Les clients recents peuvent rejoindre ce serveur 1.8.8.");
        }
        getLogger().info(engine.brand + " " + getDescription().getVersion() + " pret. Knockback: " + engine.activeKnockback);
    }

    @Override
    public void onDisable() {
        if (store != null) {
            store.shutdown();
        }
    }

    public Engine engine() {
        return engine;
    }

    public ConfigStore store() {
        return store;
    }

    public MessageService messages() {
        return messages;
    }

    public TpsCounter tps() {
        return tps;
    }

    public ViaHook via() {
        return via;
    }

    public MenuService menus() {
        return menus;
    }

    public long startedAt() {
        return startedAt;
    }

    public int forcedSlots() {
        return forcedSlots;
    }

    public void forcedSlots(int slots) {
        this.forcedSlots = slots;
    }

    public Map<UUID, EditSession> edits() {
        return edits;
    }

    public void reloadAll() {
        store.load();
        engine = Engine.load(store);
        loadPlayerProfiles();
        applyWorlds();
    }

    public void update(String file, String path, Object value) {
        store.set(file, path, value);
        engine = Engine.load(store);
        applyWorlds();
    }

    public SupportMatrix.Level support(String file, String path) {
        return SupportMatrix.of(file, path);
    }

    public KnockbackProfile profileFor(Player attacker) {
        if (attacker != null) {
            KnockbackProfile personal = engine.profile(playerKnockback.get(attacker.getUniqueId()));
            if (personal != null) {
                return personal;
            }
        }
        KnockbackProfile active = engine.profile(engine.activeKnockback);
        if (active != null) {
            return active;
        }
        List<KnockbackProfile> list = engine.profileList();
        if (!list.isEmpty()) {
            return list.get(0);
        }
        return new KnockbackProfile();
    }

    public String personalProfile(Player player) {
        return playerKnockback.get(player.getUniqueId());
    }

    public void personalProfile(Player player, String profile) {
        if (profile == null) {
            playerKnockback.remove(player.getUniqueId());
            YamlDoc doc = store.doc("player-knockback.yml");
            if (doc != null && doc.at("players." + player.getUniqueId()) != null) {
                doc.set("players." + player.getUniqueId(), "");
                store.persist("player-knockback.yml", doc.save());
            }
            return;
        }
        playerKnockback.put(player.getUniqueId(), profile);
        store.set("player-knockback.yml", "players." + player.getUniqueId(), profile);
    }

    public void applyWorlds() {
        Engine current = engine;
        for (World world : Bukkit.getWorlds()) {
            world.setGameRuleValue("randomTickSpeed", Integer.toString(current.randomTickSpeed));
            world.setGameRuleValue("doWeatherCycle", current.tickWeather ? "true" : "false");
            world.setGameRuleValue("doMobSpawning", current.naturalSpawn ? "true" : "false");
            if (!current.tickWeather) {
                world.setStorm(false);
                world.setThundering(false);
            }
        }
    }

    private void loadPlayerProfiles() {
        playerKnockback.clear();
        YamlDoc doc = store.doc("player-knockback.yml");
        if (doc == null) {
            return;
        }
        List<String> keys = doc.childKeys("players");
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            String name = doc.text("players." + key, "");
            if (name == null || name.isEmpty()) {
                continue;
            }
            try {
                playerKnockback.put(UUID.fromString(key), name);
            } catch (IllegalArgumentException ignored) {
                getLogger().warning("UUID ignore: " + key);
            }
        }
    }

    public static final class EditSession {
        public String file;
        public String path;
        public String mode;
        public int index;
        public String returnKind;
        public String returnFile;
        public String returnPath;
        public int returnPage;
    }
}
