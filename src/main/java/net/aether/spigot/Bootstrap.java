package net.aether.spigot;

/**
 * Point d'entrée de {@code java -jar}. Bukkit, lui, charge {@link AetherPlugin} via plugin.yml.
 */
public final class Bootstrap {

    private Bootstrap() {
    }

    public static void main(String[] args) {
        System.out.println("AetherSpigot est le plugin du practice HCF.");
        System.out.println("Ce jar se place dans plugins/ d'un serveur Spigot 1.8.8.");
        System.out.println();
        System.out.println("« no main manifest attribute, in spigot.jar » s'affiche quand");
        System.out.println("ce plugin, parfois renommé spigot.jar, est ouvert avec java -jar.");
        System.out.println("« pause: not found » vient d'un script Windows : sous Linux, pause n'existe pas.");
        System.out.println();
        System.out.println("Construis le serveur : sh scripts/build-spigot.sh");
        System.out.println("Démarre-le          : sh run.sh");
        System.exit(1);
    }
}
