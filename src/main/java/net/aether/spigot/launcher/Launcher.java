package net.aether.spigot.launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * {@code java -jar AetherSpigot.jar} : construit le vrai serveur AetherSpigot 1.8.8 au premier
 * lancement (Java 8 portable, BuildTools, patches/server), puis le démarre à chaque lancement.
 * Le jar serveur contient du code Mojang : il est produit sur la machine et jamais distribué.
 */
public final class Launcher {

    private static final String TAG = "[AetherSpigot] ";
    private static final String BUILDTOOLS_URL =
            "https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar";
    private static final String[][] VIA = {
            {"ViaVersion-5.11.0.jar", "https://github.com/ViaVersion/ViaVersion/releases/download/5.11.0/ViaVersion-5.11.0.jar"},
            {"ViaBackwards-5.11.0.jar", "https://github.com/ViaVersion/ViaBackwards/releases/download/5.11.0/ViaBackwards-5.11.0.jar"},
            {"ViaRewind-4.1.3.jar", "https://github.com/ViaVersion/ViaRewind/releases/download/4.1.3/ViaRewind-4.1.3.jar"},
    };

    private final File cache;
    private final File self;

    private Launcher(File cache, File self) {
        this.cache = cache;
        this.self = self;
    }

    public static void main(String[] args) {
        File self = selfJar();
        if (self == null) {
            System.out.println(TAG + "Lance ce jar avec java -jar.");
            System.exit(1);
        }
        Launcher launcher = new Launcher(new File("aetherspigot-cache").getAbsoluteFile(), self);
        try {
            File server = launcher.ensureServer();
            System.exit(launcher.start(server, args));
        } catch (BuildException ex) {
            System.out.println();
            System.out.println(TAG + ex.getMessage());
            System.exit(1);
        } catch (Exception ex) {
            System.out.println();
            System.out.println(TAG + "La construction du serveur a échoué : " + ex);
            System.out.println(TAG + "Relance la même commande : les étapes déjà faites sont gardées.");
            System.exit(1);
        }
    }

    File ensureServer() throws Exception {
        File server = new File(cache, "AetherSpigot-1.8.8.jar");
        File stamp = new File(cache, "server.stamp");
        String wanted = Long.toHexString(crc(self));
        if (server.isFile() && stamp.isFile() && wanted.equals(read(stamp).trim())) {
            return server;
        }
        mkdirs(cache);
        say("Premier lancement : construction du vrai serveur Spigot 1.8.8 (AetherSpigot).");
        say("Compte 5 à 15 minutes. Les lancements suivants démarrent tout de suite.");

        File java8 = Jdk8.find(new File(cache, "jdk8"));
        File tools = new File(cache, "buildtools");
        mkdirs(tools);
        File serverSrc = new File(tools, "Spigot/Spigot-Server");
        File baseFile = new File(tools, "aether-base");
        String base = baseFile.isFile() ? read(baseFile).trim() : "";
        Git git = new Git(tools);
        if (!windows() && !git.available()) {
            throw new BuildException("git est introuvable. BuildTools en a besoin : sudo apt install git");
        }

        if (base.isEmpty() || !new File(serverSrc, ".git").isDirectory() || !git.hasCommit(serverSrc, base)) {
            File buildTools = new File(tools, "BuildTools.jar");
            if (!buildTools.isFile()) {
                say("Téléchargement de BuildTools...");
                download(BUILDTOOLS_URL, buildTools);
            }
            say("BuildTools compile Spigot 1.8.8 avec Java 8...");
            int code = run(tools, java8, Arrays.asList(java8.getPath(), "-Xmx1G", "-jar", buildTools.getPath(), "--rev", "1.8.8"));
            if (code != 0 || !new File(serverSrc, "pom.xml").isFile()) {
                throw new BuildException("BuildTools s'est arrêté (code " + code + "). Le détail est dans "
                        + new File(tools, "BuildTools.log.txt").getPath());
            }
            git = new Git(tools);
            base = git.output(serverSrc, "rev-parse", "HEAD").trim();
            write(baseFile, base + "\n");
        }

        say("Application des patches AetherSpigot...");
        git.run(serverSrc, "am", "--abort");
        git.require(serverSrc, "checkout", "-q", "-f", "-B", "aetherspigot", base);
        git.require(serverSrc, "clean", "-q", "-fdx", "src");
        List<String> am = new ArrayList<String>(Arrays.asList("-c", "user.name=AetherSpigot", "-c", "user.email=build@aether.local", "am", "-q"));
        File patchDir = new File(cache, "patches");
        deleteTree(patchDir);
        for (String patch : extract(self, "aether-server/patches/", patchDir)) {
            am.add(new File(patchDir, patch).getPath());
        }
        git.require(serverSrc, am.toArray(new String[0]));

        File resources = new File(serverSrc, "src/main/resources/aether");
        File defaults = new File(resources, "defaults");
        File plugins = new File(resources, "plugins");
        mkdirs(defaults);
        mkdirs(plugins);
        extract(self, "aether-server/defaults/", defaults);
        copy(new FileInputStream(self), new File(plugins, "AetherSpigot.jar.embed"));
        File via = new File(cache, "via");
        mkdirs(via);
        for (String[] jar : VIA) {
            File file = new File(via, jar[0]);
            if (!file.isFile()) {
                say("Téléchargement de " + jar[0] + "...");
                download(jar[1], file);
            }
            copy(new FileInputStream(file), new File(plugins, jar[0] + ".embed"));
        }

        say("Compilation du serveur AetherSpigot...");
        int code = run(serverSrc, java8, maven(java8, new File(tools, "apache-maven-3.9.6"), serverSrc,
                "-q", "-B", "-DskipTests", "clean", "package"));
        File built = new File(serverSrc, "target/spigot-1.8.8-R0.1-SNAPSHOT.jar");
        if (code != 0 || !built.isFile()) {
            throw new BuildException("Maven n'a pas produit le serveur (code " + code + ").");
        }
        copy(new FileInputStream(built), server);
        write(stamp, wanted + "\n");
        say("Serveur prêt : " + server.getPath());
        return server;
    }

    int start(File server, String[] args) throws Exception {
        List<String> command = new ArrayList<String>();
        command.add(new File(System.getProperty("java.home"), "bin/java").getPath());
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (!arg.startsWith("-agentlib:") && !arg.startsWith("-javaagent:")) {
                command.add(arg);
            }
        }
        command.add("-jar");
        command.add(server.getPath());
        command.addAll(args.length == 0 ? Arrays.asList("nogui") : Arrays.asList(args));

        File eula = new File("eula.txt");
        if (!eula.isFile() || !read(eula).contains("eula=true")) {
            say("Lis https://aka.ms/MinecraftEULA puis mets eula=true dans eula.txt pour démarrer.");
        }
        say("Démarrage de " + server.getName() + " sur " + System.getProperty("java.version") + ".");
        final Process process = new ProcessBuilder(command).inheritIO().start();
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                if (isAlive(process)) {
                    process.destroy();
                    try {
                        process.waitFor();
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }));
        return process.waitFor();
    }

    private static boolean isAlive(Process process) {
        try {
            process.exitValue();
            return false;
        } catch (IllegalThreadStateException ex) {
            return true;
        }
    }

    static List<String> maven(File java, File home, File project, String... goals) throws BuildException {
        File boot = new File(home, "boot");
        File[] jars = boot.listFiles();
        File classworlds = null;
        if (jars != null) {
            for (File jar : jars) {
                if (jar.getName().startsWith("plexus-classworlds") && jar.getName().endsWith(".jar")) {
                    classworlds = jar;
                }
            }
        }
        if (classworlds == null) {
            throw new BuildException("Maven de BuildTools introuvable dans " + home.getPath());
        }
        List<String> command = new ArrayList<String>(Arrays.asList(
                java.getPath(), "-Xmx1G",
                "-classpath", classworlds.getPath(),
                "-Dclassworlds.conf=" + new File(home, "bin/m2.conf").getPath(),
                "-Dmaven.home=" + home.getPath(),
                "-Dlibrary.jansi.path=" + new File(home, "lib/jansi-native").getPath(),
                "-Dmaven.multiModuleProjectDirectory=" + project.getPath(),
                "org.codehaus.plexus.classworlds.launcher.Launcher"));
        command.addAll(Arrays.asList(goals));
        return command;
    }

    static int run(File dir, File java, List<String> command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command).directory(dir).inheritIO();
        builder.environment().put("JAVA_HOME", java.getParentFile().getParentFile().getPath());
        builder.environment().remove("_JAVA_OPTIONS");
        return builder.start().waitFor();
    }

    static List<String> extract(File jar, String prefix, File target) throws IOException {
        List<String> names = new ArrayList<String>();
        JarFile file = new JarFile(jar);
        try {
            Enumeration<JarEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith(prefix) || name.length() == prefix.length()) {
                    continue;
                }
                String rest = name.substring(prefix.length());
                if (rest.indexOf('/') >= 0) {
                    continue;
                }
                copy(file.getInputStream(entry), new File(target, rest));
                names.add(rest);
            }
        } finally {
            file.close();
        }
        java.util.Collections.sort(names);
        return names;
    }

    static void download(String url, File target) throws IOException {
        File part = new File(target.getPath() + ".part");
        String current = url;
        for (int hop = 0; hop < 8; hop++) {
            HttpURLConnection connection = (HttpURLConnection) new URL(current).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(60000);
            connection.setRequestProperty("User-Agent", "AetherSpigot-Launcher");
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null) {
                    break;
                }
                current = new URL(new URL(current), location).toString();
                continue;
            }
            if (status != 200) {
                connection.disconnect();
                throw new IOException("HTTP " + status + " sur " + current);
            }
            copy(connection.getInputStream(), part);
            if (target.exists() && !target.delete()) {
                throw new IOException("Impossible de remplacer " + target.getPath());
            }
            if (!part.renameTo(target)) {
                throw new IOException("Impossible d'écrire " + target.getPath());
            }
            return;
        }
        throw new IOException("Trop de redirections pour " + url);
    }

    static long crc(File file) throws IOException {
        CRC32 crc = new CRC32();
        InputStream in = new FileInputStream(file);
        try {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) != -1) {
                crc.update(buffer, 0, read);
            }
        } finally {
            in.close();
        }
        return crc.getValue();
    }

    static void copy(InputStream in, File target) throws IOException {
        File parent = target.getAbsoluteFile().getParentFile();
        if (parent != null) {
            mkdirs(parent);
        }
        OutputStream out = new FileOutputStream(target);
        try {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            in.close();
            out.close();
        }
    }

    static String read(File file) throws IOException {
        StringBuilder text = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        try {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                text.append(buffer, 0, read);
            }
        } finally {
            reader.close();
        }
        return text.toString();
    }

    static void write(File file, String text) throws IOException {
        copy(new java.io.ByteArrayInputStream(text.getBytes("UTF-8")), file);
    }

    static void mkdirs(File dir) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Impossible de créer " + dir.getPath());
        }
    }

    static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteTree(child);
            }
        }
        file.delete();
    }

    static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    static void say(String message) {
        System.out.println(TAG + message);
    }

    private static File selfJar() {
        try {
            File file = new File(Launcher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!file.isFile()) {
                return null;
            }
            ZipFile zip = new ZipFile(file);
            try {
                ZipEntry entry = zip.getEntry("aether-server/patches/");
                if (entry == null && zip.getEntry("plugin.yml") == null) {
                    return null;
                }
            } finally {
                zip.close();
            }
            return file;
        } catch (Exception ex) {
            return null;
        }
    }

    static final class BuildException extends Exception {
        BuildException(String message) {
            super(message);
        }
    }
}
