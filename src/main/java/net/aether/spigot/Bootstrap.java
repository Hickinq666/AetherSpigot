package net.aether.spigot;

/**
 * Point d'entrée de {@code java -jar}. Bukkit, lui, charge {@link AetherPlugin} via plugin.yml.
 */
public final class Bootstrap {

    private Bootstrap() {
    }

    public static void main(String[] args) {
        System.out.println("Ce jar est le plugin AetherSpigot, pas le serveur.");
        System.out.println("Le serveur est server/AetherSpigot-1.8.8.jar, qui embarque déjà ce plugin.");
        System.out.println();
        System.out.println("Construis le serveur : sh scripts/build-aetherspigot.sh");
        System.out.println("Démarre-le          : sh run.sh");
        System.exit(1);
    }
}
