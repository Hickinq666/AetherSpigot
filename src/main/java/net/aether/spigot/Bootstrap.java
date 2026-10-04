package net.aether.spigot;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * Point d'entrée de {@code java -jar}. Bukkit, lui, charge {@link AetherPlugin} via plugin.yml.
 * Lancé à la place du serveur, ce jar cherche AetherSpigot-1.8.8.jar et le démarre.
 */
public final class Bootstrap {

    private static final String SERVER = "AetherSpigot-1.8.8.jar";
    private static final int DEPTH = 5;

    private Bootstrap() {
    }

    public static void main(String[] args) throws Exception {
        File server = find();
        if (server == null) {
            System.out.println("Ce jar est le plugin practice AetherSpigot, pas le serveur.");
            System.out.println("Copie bundle/" + SERVER + " (21 Mo) dans ce dossier et relance.");
            System.exit(1);
        }
        System.out.println("[AetherSpigot] Serveur trouvé : " + server.getPath());
        List<String> command = new ArrayList<String>();
        command.add(new File(System.getProperty("java.home"), "bin/java").getPath());
        command.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
        command.add("-jar");
        command.add(server.getPath());
        for (String arg : args) {
            command.add(arg);
        }
        final Process process = new ProcessBuilder(command).inheritIO().start();
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                process.destroy();
            }
        }));
        System.exit(process.waitFor());
    }

    private static File find() {
        List<File> roots = new ArrayList<File>();
        roots.add(new File(".").getAbsoluteFile());
        File self = self();
        if (self != null) {
            roots.add(self.getParentFile());
        }
        for (File dir = new File(".").getAbsoluteFile().getParentFile(); dir != null; dir = dir.getParentFile()) {
            roots.add(dir);
        }
        for (File root : roots) {
            File direct = valid(new File(root, SERVER));
            if (direct == null) {
                direct = valid(new File(root, "bundle/" + SERVER));
            }
            if (direct != null) {
                return direct;
            }
        }
        return search(new File(System.getProperty("user.home")), DEPTH);
    }

    private static File search(File dir, int depth) {
        File found = valid(new File(dir, "bundle/" + SERVER));
        if (found != null || depth == 0) {
            return found;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return null;
        }
        for (File child : children) {
            if (child.isDirectory() && !child.getName().startsWith(".") && !child.getName().equals("node_modules")) {
                found = search(child, depth - 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Le vrai serveur pèse des dizaines de Mo ; le plugin, quelques centaines de Ko. */
    private static File valid(File file) {
        if (!file.isFile() || file.length() < 10000000L) {
            return null;
        }
        try {
            File canonical = file.getCanonicalFile();
            File self = self();
            return self != null && canonical.equals(self.getCanonicalFile()) ? null : canonical;
        } catch (IOException e) {
            return null;
        }
    }

    private static File self() {
        try {
            return new File(Bootstrap.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            return null;
        }
    }
}
