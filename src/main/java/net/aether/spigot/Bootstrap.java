package net.aether.spigot;

/**
 * Point d'entrée de {@code java -jar}. Bukkit, lui, charge {@link AetherPlugin} via plugin.yml.
 */
public final class Bootstrap {

    private Bootstrap() {
    }

    public static void main(String[] args) {
        System.out.println("Ce jar est le plugin AetherSpigot, pas le serveur.");
        System.out.println("Le serveur est AetherSpigot-1.8.8.jar (dossier bundle/) : java -jar AetherSpigot-1.8.8.jar");
        System.exit(1);
    }
}
