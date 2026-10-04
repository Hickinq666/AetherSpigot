# AetherSpigot

Moteur de practice HCF pour **Spigot 1.8.8**. Il charge les perles, le knockback, les enchantements, les dégâts d'armure, les patches de combat et un menu qui écrit dans les YAML.

Les clients 1.7 à 1.21 rejoignent le serveur 1.8.8 grâce aux jars ViaVersion déjà placés dans `bundle/plugins/`.

## Lancer le practice

1. Construis un Spigot 1.8.8 avec [BuildTools](https://www.spigotmc.org/wiki/buildtools/) (Java 17 recommandé, ViaVersion 5.11.0 en a besoin).
2. Copie `server-config/server.properties`, `server-config/bukkit.yml` et `server-config/spigot.yml` à la racine du serveur.
3. Copie dans `plugins/` :
   - `bundle/plugins/AetherSpigot.jar` (ou `target/AetherSpigot.jar` après `mvn package`)
   - `bundle/plugins/ViaVersion-5.11.0.jar`
   - `bundle/plugins/ViaBackwards-5.11.0.jar`
   - `bundle/plugins/ViaRewind-4.1.3.jar`
4. Démarre :

```bash
java -Xms4G -Xmx4G -jar spigot-1.8.8.jar nogui
```

Au premier démarrage, le plugin écrit ses YAML dans `plugins/AetherSpigot/`.

Pour retélécharger ViaVersion :

```bash
bash scripts/bundle-via.sh
```

ViaVersion, ViaBackwards et ViaRewind sont des projets GPL. Leurs sources sont sur [github.com/ViaVersion](https://github.com/ViaVersion).

## Menu

`/config` ou `/aether` ouvre le coffre. Tu changes les slots, les items et les actions dans `menus.yml`.

| Clic | Effet |
| --- | --- |
| Gauche | Ouvre une section, bascule un booléen, augmente un nombre, ou fait tourner un choix |
| Droit | Tu écris la valeur dans le chat |
| Shift | Diminue un nombre, ou retire une ligne de liste |

`cancel` dans le chat annule la saisie. Le lore vert veut dire que le réglage est appliqué tout de suite. Le lore rouge est enregistré pour le jar du serveur (threads Netty, tick des chunks, redstone Eigencraft) : BuildTools ne les branche pas depuis un plugin.

## Commandes

| Commande | Rôle |
| --- | --- |
| `/config` | Menu. `/config reload` relit les fichiers |
| `/aether version` | Version, Java, protocole ViaVersion |
| `/knockback` | Créer, voir, activer et éditer les profils |
| `/ping [joueur]` | Ping |
| `/tps` | TPS, mémoire, chunks, entités |
| `/clearlag` | Items, flèches, XP |
| `/unloadchunks` | Chunks sans joueur proche |
| `/setmaxplayers <n>` | Slots affichés |

Les textes de ces commandes sont dans `messages.yml`. Les textes système (kick spam, durabilité) sont dans `language.yml`.

Permission complète : `aether.admin` (op par défaut). `/ping` est ouvert à tout le monde.

## Fichiers

| Fichier | Contenu |
| --- | --- |
| `aether.yml` | Ticks, joueurs, projectiles, patches, factions, practice |
| `pearls.yml` | Perles HCF : fences, slabs, refunds, dégâts |
| `enchants.yml` | Plafonds (Protection II par défaut) |
| `knockback/*.yml` | Profils HCF, Practice, Combo, Default |
| `messages.yml` | Toutes les réponses de commandes |
| `language.yml` | Textes système |
| `menus.yml` | Disposition du coffre |
| `loader.yml` | Version du chargeur |
| `via.yml` | Hook ViaVersion |
| `generator/Default.yml` | Options de génération, lues par un générateur externe |

Profil de knockback global au premier boot : **HCF**. `/knockback setactive Practice` ou `Combo` change le feeling.

Cooldown de perle : `practice.pearlCooldownSeconds` (16). Armure de teamfight : `players.armorDamageDivision` (12, plus de dégâts passent qu'avec 4). Protection sans aléatoire : `players.protRandomness: false` et `protectionModifier: 22`.

## Compiler

Java 17+ et Maven :

```bash
mvn package
```

Le jar est `target/AetherSpigot.jar`. Le code du plugin vise Java 8. Les tests couvrent le YAML, le knockback, les perles, l'armure et les enchantements.
