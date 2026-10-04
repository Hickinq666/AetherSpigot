package org.aetherspigot;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import joptsimple.OptionSet;

/**
 * Prépare le dossier du serveur avant le chargement de Minecraft : configs HCF par défaut
 * et ViaVersion, embarqués dans le jar du serveur. Le practice, lui, est dans le serveur (AetherCore).
 */
public final class AetherBoot {

    public static final String NAME = "AetherSpigot";
    private static final String DEFAULTS = "aether/server/";
    private static final String PLUGINS = "aether/plugins/";
    // Les jars embarqués portent .embed : animal-sniffer et shade ne doivent pas les ouvrir.
    private static final String EMBED = ".jar.embed";

    private AetherBoot() {
    }

    public static void prepare(OptionSet options) {
        System.out.println("[" + NAME + "] Spigot 1.8.8 pour le practice HCF - AetherNetwork");
        warnJava();
        JarFile self = openSelf();
        if (self == null) {
            System.out.println("[" + NAME + "] Jar du serveur introuvable, rien n'est extrait.");
            return;
        }
        try {
            writeDefault(self, "server.properties", file(options, "config", "server.properties"));
            writeDefault(self, "bukkit.yml", file(options, "bukkit-settings", "bukkit.yml"));
            writeDefault(self, "spigot.yml", file(options, "spigot-settings", "spigot.yml"));
            installPlugins(self, file(options, "plugins", "plugins"));
        } finally {
            try {
                self.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void warnJava() {
        String spec = System.getProperty("java.specification.version", "1.8");
        int major;
        try {
            major = spec.startsWith("1.") ? Integer.parseInt(spec.substring(2)) : Integer.parseInt(spec.split("\\.")[0]);
        } catch (NumberFormatException ex) {
            return;
        }
        if (major < 17) {
            System.out.println("[" + NAME + "] Java " + spec + " : ViaVersion 5 demande Java 17. Les clients 1.9+ ne pourront pas rejoindre.");
        }
    }

    private static File file(OptionSet options, String key, String fallback) {
        Object value;
        try {
            value = options.valueOf(key);
        } catch (RuntimeException ex) {
            value = null;
        }
        return value instanceof File ? (File) value : new File(fallback);
    }

    private static JarFile openSelf() {
        try {
            URL location = AetherBoot.class.getProtectionDomain().getCodeSource().getLocation();
            File jar = new File(location.toURI());
            if (!jar.isFile()) {
                return null;
            }
            return new JarFile(jar);
        } catch (Exception ex) {
            return null;
        }
    }

    private static void writeDefault(JarFile self, String name, File target) {
        if (target.exists()) {
            return;
        }
        ZipEntry entry = self.getEntry(DEFAULTS + name);
        if (entry == null) {
            return;
        }
        try {
            copy(self.getInputStream(entry), target);
            System.out.println("[" + NAME + "] " + target.getPath() + " créé avec le preset HCF practice.");
        } catch (IOException ex) {
            System.out.println("[" + NAME + "] Impossible d'écrire " + target.getPath() + " : " + ex.getMessage());
        }
    }

    private static void installPlugins(JarFile self, File folder) {
        if (!folder.isDirectory() && !folder.mkdirs()) {
            System.out.println("[" + NAME + "] Impossible de créer " + folder.getPath());
            return;
        }
        Map<String, File> installed = installedPlugins(folder);
        File old = installed.remove(NAME.toLowerCase());
        if (old != null) {
            File renamed = new File(folder, old.getName() + ".ancien");
            if (old.renameTo(renamed)) {
                System.out.println("[" + NAME + "] plugins/" + old.getName() + " renommé en " + renamed.getName() + " : le practice est maintenant dans le serveur.");
            }
        }
        Enumeration<JarEntry> entries = self.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String path = entry.getName();
            if (entry.isDirectory() || !path.startsWith(PLUGINS) || !path.endsWith(EMBED)) {
                continue;
            }
            String fileName = path.substring(PLUGINS.length(), path.length() - ".embed".length());
            if (fileName.indexOf('/') >= 0) {
                continue;
            }
            File target = new File(folder, fileName);
            try {
                File temp = File.createTempFile("aether-", ".jar");
                try {
                    copy(self.getInputStream(entry), temp);
                    String plugin = pluginName(temp);
                    File existing = plugin == null ? null : installed.get(plugin.toLowerCase());
                    if (existing != null && !existing.getName().equals(fileName)) {
                        System.out.println("[" + NAME + "] " + plugin + " déjà présent (" + existing.getName() + "), version embarquée ignorée.");
                        continue;
                    }
                    if (target.isFile() && target.length() == temp.length() && sameBytes(target, temp)) {
                        continue;
                    }
                    copy(new java.io.FileInputStream(temp), target);
                    System.out.println("[" + NAME + "] plugins/" + fileName + " installé.");
                } finally {
                    temp.delete();
                }
            } catch (IOException ex) {
                System.out.println("[" + NAME + "] Impossible d'installer " + fileName + " : " + ex.getMessage());
            }
        }
    }

    private static Map<String, File> installedPlugins(File folder) {
        Map<String, File> result = new HashMap<String, File>();
        File[] files = folder.listFiles();
        if (files == null) {
            return result;
        }
        for (File file : files) {
            if (!file.isFile() || !file.getName().endsWith(".jar")) {
                continue;
            }
            String name = pluginName(file);
            if (name != null) {
                result.put(name.toLowerCase(), file);
            }
        }
        return result;
    }

    static String pluginName(File jar) {
        ZipFile zip = null;
        try {
            zip = new ZipFile(jar);
            ZipEntry entry = zip.getEntry("plugin.yml");
            if (entry == null) {
                return null;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(zip.getInputStream(entry), "UTF-8"));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("name:")) {
                        String value = line.substring(5).trim();
                        if (value.length() >= 2 && (value.charAt(0) == '\'' || value.charAt(0) == '"')) {
                            value = value.substring(1, value.length() - 1);
                        }
                        return value;
                    }
                }
            } finally {
                reader.close();
            }
            return null;
        } catch (IOException ex) {
            return null;
        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static boolean sameBytes(File a, File b) throws IOException {
        InputStream left = new java.io.BufferedInputStream(new java.io.FileInputStream(a));
        InputStream right = new java.io.BufferedInputStream(new java.io.FileInputStream(b));
        try {
            int x;
            int y;
            do {
                x = left.read();
                y = right.read();
                if (x != y) {
                    return false;
                }
            } while (x != -1);
            return true;
        } finally {
            left.close();
            right.close();
        }
    }

    private static void copy(InputStream in, File target) throws IOException {
        File parent = target.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.isDirectory()) {
            parent.mkdirs();
        }
        OutputStream out = new FileOutputStream(target);
        try {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            in.close();
            out.close();
        }
    }
}
