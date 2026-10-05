package net.aether.spigot.config;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ConfigStore {

    private static final String[] RESOURCES = {
            "aether.yml",
            "enchants.yml",
            "language.yml",
            "messages.yml",
            "loader.yml",
            "pearls.yml",
            "menus.yml",
            "via.yml",
            "generator/Default.yml",
            "knockback/Default.yml",
            "knockback/HCF.yml",
            "knockback/Combo.yml",
            "knockback/Practice.yml"
    };

    private final Plugin plugin;
    private final File folder;
    private final Map<String, YamlDoc> docs = new LinkedHashMap<String, YamlDoc>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "aether-config");
        thread.setDaemon(true);
        return thread;
    });

    public ConfigStore(Plugin plugin) {
        this.plugin = plugin;
        this.folder = plugin.getDataFolder();
    }

    public void load() {
        docs.clear();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Dossier de config introuvable.");
        }
        for (String resource : RESOURCES) {
            ensure(resource);
            read(resource);
            if (!"menus.yml".equals(resource)) {
                complete(resource, resource);
            }
        }
        File knockback = new File(folder, "knockback");
        File[] extra = knockback.listFiles();
        if (extra != null) {
            for (File file : extra) {
                String name = "knockback/" + file.getName();
                if (file.getName().endsWith(".yml") && !docs.containsKey(name)) {
                    read(name);
                    complete(name, "knockback/Default.yml");
                }
            }
        }
        File players = new File(folder, "player-knockback.yml");
        if (!players.exists()) {
            try {
                Files.write(players.toPath(), "players:\n".getBytes(StandardCharsets.UTF_8));
            } catch (IOException ex) {
                plugin.getLogger().warning(ex.getMessage());
            }
        }
        read("player-knockback.yml");
        applyChosenCombat();
    }

    /**
     * Une seule fois : active la détection selon le ping et baisse la Force à 0.5.
     * Les changements faits ensuite dans le menu restent.
     */
    private void applyChosenCombat() {
        File marker = new File(folder, ".combat-ping-force");
        YamlDoc doc = docs.get("aether.yml");
        if (doc == null || marker.exists()) {
            return;
        }
        doc.set("players.pingHitDetection", Boolean.TRUE);
        doc.set("modifiers.strengthModifier", Double.valueOf(0.5D));
        writeNow("aether.yml", doc.save());
        try {
            marker.createNewFile();
        } catch (IOException ex) {
            plugin.getLogger().warning(ex.getMessage());
        }
    }

    public YamlDoc doc(String name) {
        return docs.get(name);
    }

    public List<String> files() {
        return new ArrayList<String>(docs.keySet());
    }

    public void set(String file, String path, Object value) {
        YamlDoc doc = docs.get(file);
        if (doc == null) {
            return;
        }
        doc.set(path, value);
        persist(file, doc.save());
    }

    public void writeNow(String file, String rendered) {
        try {
            File target = new File(folder, file);
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Files.write(target.toPath(), rendered.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            plugin.getLogger().warning("Ecriture " + file + " : " + ex.getMessage());
        }
    }

    public void persist(String file, String rendered) {
        File target = new File(folder, file);
        io.execute(() -> {
            try {
                File parent = target.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                Files.write(target.toPath(), rendered.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ex) {
                plugin.getLogger().warning("Ecriture " + file + " : " + ex.getMessage());
            }
        });
    }

    public boolean createProfile(String name) {
        String file = "knockback/" + name + ".yml";
        if (docs.containsKey(file)) {
            return false;
        }
        YamlDoc template = docs.get("knockback/Default.yml");
        String rendered = template == null ? "knockbackType: ADVANCED\n" : template.save();
        docs.put(file, YamlDoc.parse(rendered));
        writeNow(file, rendered);
        return true;
    }

    public boolean deleteProfile(String name) {
        String file = "knockback/" + name + ".yml";
        File target = new File(folder, file);
        docs.remove(file);
        return target.delete();
    }

    public void shutdown() {
        io.shutdown();
    }

    private void ensure(String resource) {
        File out = new File(folder, resource);
        if (out.exists()) {
            return;
        }
        File parent = out.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        plugin.saveResource(resource, false);
    }

    /** Ajoute au fichier du serveur les réglages apparus dans une nouvelle version du jar. */
    private void complete(String file, String bundled) {
        YamlDoc doc = docs.get(file);
        if (doc == null) {
            return;
        }
        java.io.InputStream in = plugin.getResource(bundled);
        if (in == null) {
            return;
        }
        try {
            YamlDoc defaults = YamlDoc.parse(new String(readAll(in), StandardCharsets.UTF_8));
            if (doc.addMissing(defaults)) {
                writeNow(file, doc.save());
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Lecture " + bundled + " : " + ex.getMessage());
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static byte[] readAll(java.io.InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private void read(String resource) {
        File out = new File(folder, resource);
        if (!out.exists()) {
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(out.toPath());
            docs.put(resource, YamlDoc.parse(new String(bytes, StandardCharsets.UTF_8)));
        } catch (IOException ex) {
            plugin.getLogger().warning("Lecture " + resource + " : " + ex.getMessage());
        }
    }
}
