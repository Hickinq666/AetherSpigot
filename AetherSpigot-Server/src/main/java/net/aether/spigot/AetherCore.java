package net.aether.spigot;

import com.avaje.ebean.EbeanServer;
import net.aether.spigot.command.CommandBridge;
import net.aether.spigot.config.ConfigStore;
import net.aether.spigot.config.Engine;
import net.aether.spigot.config.YamlDoc;
import net.aether.spigot.knockback.KnockbackService;
import net.aether.spigot.knockback.KnockbackProfile;
import net.aether.spigot.menu.MenuListener;
import net.aether.spigot.menu.MenuService;
import net.aether.spigot.message.MessageService;
import net.aether.spigot.patch.PatchListener;
import net.aether.spigot.pearl.PearlService;
import net.aether.spigot.runtime.SupportMatrix;
import net.aether.spigot.runtime.TpsCounter;
import net.aether.spigot.via.ViaHook;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginBase;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.PluginLogger;
import org.bukkit.plugin.java.JavaPluginLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Cœur practice d'AetherSpigot. Il fait partie du serveur : CraftServer l'active avant les plugins
 * et l'arrête après eux. Il n'est pas dans plugins/ et n'apparaît pas dans /plugins.
 * Bukkit exige un {@link org.bukkit.plugin.Plugin} pour les listeners et le scheduler, d'où PluginBase.
 */
public final class AetherCore extends PluginBase {

    public static final String NAME = "AetherSpigot";
    private static final String DEFAULTS = "aether/defaults/";
    private static AetherCore instance;

    private final Server server;
    private final PluginDescriptionFile description;
    private final PluginLoader loader;
    private final Logger logger;
    private final File folder = new File("aether");
    private boolean enabled;
    private boolean naggable = true;

    private ConfigStore store;
    private volatile Engine engine;
    private MessageService messages;
    private final TpsCounter tps = new TpsCounter();
    private ViaHook via;
    private MenuService menus;
    private KnockbackService knockback;
    private net.aether.spigot.combat.CombatService combat;
    private PearlService pearls;
    private final Map<UUID, String> playerKnockback = new ConcurrentHashMap<UUID, String>();
    private final Map<UUID, EditSession> edits = new ConcurrentHashMap<UUID, EditSession>();
    private final long startedAt = System.currentTimeMillis();
    private int forcedSlots = -1;

    private AetherCore(Server server) {
        this.server = server;
        this.description = new PluginDescriptionFile(NAME, "1.8.8", AetherCore.class.getName());
        this.loader = new JavaPluginLoader(server);
        this.logger = new PluginLogger(this);
    }

    public static AetherCore get() {
        return instance;
    }

    public static void start(Server server) {
        if (instance != null && instance.enabled) {
            return;
        }
        instance = new AetherCore(server);
        instance.enabled = true;
        try {
            instance.onEnable();
        } catch (Throwable t) {
            instance.enabled = false;
            instance.logger.log(java.util.logging.Level.SEVERE, "Le cœur practice n'a pas démarré", t);
        }
    }

    public static void stop() {
        if (instance == null || !instance.enabled) {
            return;
        }
        try {
            instance.onDisable();
        } finally {
            instance.enabled = false;
            Bukkit.getScheduler().cancelTasks(instance);
            org.bukkit.event.HandlerList.unregisterAll(instance);
        }
    }

    @Override
    public void onEnable() {
        store = new ConfigStore(this);
        store.load();
        engine = Engine.load(store);
        messages = new MessageService(store);
        via = new ViaHook();
        menus = new MenuService(this);
        knockback = new KnockbackService(this);
        combat = new net.aether.spigot.combat.CombatService(this);
        pearls = new PearlService(this);
        loadPlayerProfiles();
        server.getPluginManager().registerEvents(new MenuListener(this), this);
        server.getPluginManager().registerEvents(new PatchListener(this), this);
        registerCommands(new CommandBridge(this));
        server.getScheduler().runTaskTimer(this, tps, 1L, 1L);
        applyWorlds();
        if (engine.viaWarn && !via.present()) {
            logger.warning("ViaVersion est absent de plugins/. Les clients 1.9+ ne pourront pas rejoindre.");
        } else if (via.present()) {
            logger.info("ViaVersion détecté. Les clients 1.7 à 1.21 peuvent rejoindre ce serveur 1.8.8.");
        }
        logger.info(engine.brand + " prêt. Knockback : " + engine.activeKnockback);
    }

    @Override
    public void onDisable() {
        if (store != null) {
            store.shutdown();
        }
    }

    private void registerCommands(final CommandBridge bridge) {
        Object[][] table = {
                {"aether", "Ouvre le menu AetherSpigot", "/aether [menu|reload|version]", new String[]{"aetherspigot"}},
                {"config", "Menu ou rechargement des configs", "/config [menu|reload]", new String[0]},
                {"knockback", "Profils de knockback", "/knockback", new String[]{"kb"}},
                {"ping", "Ping", "/ping [joueur]", new String[0]},
                {"tps", "État du serveur", "/tps", new String[0]},
                {"clearlag", "Retire les entités au sol", "/clearlag", new String[0]},
                {"unloadchunks", "Décharge les chunks vides", "/unloadchunks", new String[0]},
                {"setmaxplayers", "Change le nombre de slots", "/setmaxplayers <nombre>", new String[0]},
                {"hide", "Cache un joueur (lui, ses projectiles, ses sons)", "/hide <joueur> [observateur]", new String[0]},
                {"see", "Montre de nouveau un joueur caché", "/see <joueur> [observateur]", new String[0]}
        };
        for (Object[] row : table) {
            Command command = new Command((String) row[0], (String) row[1], (String) row[2], Arrays.asList((String[]) row[3])) {
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    return bridge.onCommand(sender, this, label, args);
                }

                @Override
                public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                    List<String> result = bridge.onTabComplete(sender, this, alias, args);
                    return result == null ? Collections.<String>emptyList() : result;
                }
            };
            server.getCommandMap().register(NAME.toLowerCase(), command);
        }
        registerPermissions();
    }

    private void registerPermissions() {
        Map<String, PermissionDefault> nodes = new LinkedHashMap<String, PermissionDefault>();
        String[] admin = {"aether.config", "aether.knockback", "aether.tps", "aether.clearlag", "aether.chunks",
                "aether.slots", "aether.ping.others", "aether.isolation"};
        for (String node : admin) {
            nodes.put(node, PermissionDefault.OP);
        }
        nodes.put("aether.ping", PermissionDefault.TRUE);
        nodes.put("aether.version", PermissionDefault.TRUE);
        for (Map.Entry<String, PermissionDefault> node : nodes.entrySet()) {
            if (server.getPluginManager().getPermission(node.getKey()) == null) {
                server.getPluginManager().addPermission(new Permission(node.getKey(), node.getValue()));
            }
        }
        if (server.getPluginManager().getPermission("aether.admin") == null) {
            Map<String, Boolean> children = new LinkedHashMap<String, Boolean>();
            for (String node : admin) {
                children.put(node, Boolean.TRUE);
            }
            server.getPluginManager().addPermission(new Permission("aether.admin", "Accès complet au menu et aux commandes", PermissionDefault.OP, children));
        }
    }

    public net.aether.spigot.combat.CombatService combat() {
        return combat;
    }

    public PearlService pearls() {
        return pearls;
    }

    public KnockbackService knockback() {
        return knockback;
    }

    @Override
    public File getDataFolder() {
        return folder;
    }

    @Override
    public PluginDescriptionFile getDescription() {
        return description;
    }

    @Override
    public FileConfiguration getConfig() {
        return new YamlConfiguration();
    }

    @Override
    public InputStream getResource(String filename) {
        return AetherCore.class.getClassLoader().getResourceAsStream(DEFAULTS + filename);
    }

    @Override
    public void saveConfig() {
    }

    @Override
    public void saveDefaultConfig() {
    }

    @Override
    public void saveResource(String resourcePath, boolean replace) {
        File out = new File(folder, resourcePath);
        if (out.exists() && !replace) {
            return;
        }
        InputStream in = getResource(resourcePath);
        if (in == null) {
            logger.warning("Config par défaut absente du jar : " + resourcePath);
            return;
        }
        try {
            File parent = out.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.warning("Écriture " + resourcePath + " : " + e.getMessage());
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void reloadConfig() {
    }

    @Override
    public PluginLoader getPluginLoader() {
        return loader;
    }

    @Override
    public Server getServer() {
        return server;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void onLoad() {
    }

    @Override
    public boolean isNaggable() {
        return naggable;
    }

    @Override
    public void setNaggable(boolean canNag) {
        this.naggable = canNag;
    }

    @Override
    public EbeanServer getDatabase() {
        return null;
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return null;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return null;
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
