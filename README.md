# AetherSpigot

Moteur de practice HCF pour **Spigot 1.8.8**. Il charge les perles, le knockback, les enchantements, les dégâts d'armure, les patches de combat et un menu qui écrit dans les YAML.

Les clients 1.7 à 1.21 rejoignent le serveur 1.8.8 grâce aux jars ViaVersion déjà placés dans `bundle/plugins/`.

## Lancer le practice

`AetherSpigot.jar` est le plugin. Le serveur est un autre fichier, `spigot-1.8.8.jar`, construit avec BuildTools.

Si le terminal affiche :

```text
no main manifest attribute, in spigot.jar
run.sh: 2: pause: not found
```

la première ligne veut dire que `java -jar` a ouvert le plugin (souvent renommé `spigot.jar`). La deuxième vient de `pause`, une commande de l'invite Windows : sous Linux elle n'existe pas. Le `run.sh` de ce dépôt ne l'appelle pas.

```bash
sh scripts/build-spigot.sh
sh run.sh
```

`scripts/build-spigot.sh` télécharge BuildTools et produit `server/spigot-1.8.8.jar`. La compilation de Spigot 1.8.8 passe avec Java 8 ou Java 17. Java 21 casse souvent les vieux plugins Maven de BuildTools. ViaVersion 5.11.0, lui, a besoin de Java 17 ou plus pour tourner : compile avec 17 si tu peux, joue avec 17.

`sh run.sh` copie `server-config/` et `bundle/plugins/` dans `server/`, puis lance le jar dont le manifeste contient `org.bukkit.craftbukkit.Main`. Mémoire par défaut : 4G (`AETHER_XMS` et `AETHER_XMX` pour changer). Au premier arrêt, lis `server/eula.txt` et passe `eula=true`.

Le plugin écrit ses YAML dans `server/plugins/AetherSpigot/` au premier démarrage.

À la main, sans le script : construis un Spigot 1.8.8 avec [BuildTools](https://www.spigotmc.org/wiki/buildtools/), copie `server-config/server.properties`, `bukkit.yml` et `spigot.yml` à la racine du serveur, copie le contenu de `bundle/plugins/` dans `plugins/`, puis `java -Xms4G -Xmx4G -jar spigot-1.8.8.jar nogui`.

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
