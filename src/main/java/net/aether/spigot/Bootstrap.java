package net.aether.spigot;

import net.aether.spigot.launcher.Launcher;

/**
 * Point d'entrée de {@code java -jar}. Bukkit, lui, charge {@link AetherPlugin} via plugin.yml.
 */
public final class Bootstrap {

    private Bootstrap() {
    }

    public static void main(String[] args) {
        Launcher.main(args);
    }
}
