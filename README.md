# AetherSpigot

Serveur **Spigot 1.8.8** pour le practice HCF. `AetherSpigot-1.8.8.jar` est un vrai jar serveur (`org.bukkit.craftbukkit.Main`) : Spigot 1.8.8 compilé par BuildTools, avec les patches de `patches/server/`.

Au premier démarrage, il écrit `server.properties`, `bukkit.yml` et `spigot.yml` avec le preset HCF (400 slots, view-distance 4, sans mobs), puis installe dans `plugins/` le moteur practice et ViaVersion. Il embarque les deux.

Le moteur practice (`bundle/plugins/AetherSpigot.jar`) gère les perles, le knockback, les enchantements, l'armure, les patches de combat et un menu qui écrit dans les YAML. Les clients 1.7 à 1.21 rejoignent grâce à ViaVersion, ViaBackwards et ViaRewind.

## Lancer avec un seul jar

`bundle/plugins/AetherSpigot.jar` (environ 160 Ko) suffit. Pose-le seul dans un dossier, appelle-le comme tu veux (`spigot.jar` marche), puis :

```bash
java -Xms4G -Xmx4G -jar spigot.jar
```

Au premier lancement, il construit le vrai serveur dans `aetherspigot-cache/` : il télécharge un Java 8 portable si la machine n'en a pas, lance BuildTools, applique `patches/server/`, télécharge ViaVersion, puis écrit `aetherspigot-cache/AetherSpigot-1.8.8.jar` (environ 28 Mo). Compte 5 à 15 minutes. Les lancements suivants démarrent le serveur tout de suite, avec la même commande et les mêmes options mémoire.

Sous Linux, il faut `git` (`sudo apt install git`). Sous Windows, BuildTools télécharge Git tout seul. Le serveur tourne avec le Java qui a lancé la commande : prends Java 17 pour que ViaVersion fonctionne.

Lis l'[EULA Minecraft](https://aka.ms/MinecraftEULA), puis écris `eula=true` dans `eula.txt` à côté du jar.

Le jar serveur contient du code Mojang : il se construit sur ta machine et n'est jamais versionné ni partagé.

Sous Windows, `pause` dans un `.bat` est normal. Dans un `.sh` sous Linux, `pause` n'existe pas : retire cette ligne.

## Depuis ce dépôt

```bash
sudo apt install git curl openjdk-8-jdk-headless openjdk-17-jre-headless
sh scripts/build-aetherspigot.sh
echo eula=true > server/eula.txt
sh run.sh
```

`build-aetherspigot.sh` fait la même construction avec les jars de `bundle/plugins/` et écrit `server/AetherSpigot-1.8.8.jar`. `run.sh` le lance depuis `server/` avec Java 17, G1 et 4G (`AETHER_XMS`, `AETHER_XMX`, `AETHER_JAVA` pour changer).

### Ce que change le patch serveur

| Patch | Effet |
| --- | --- |
| Nom | `/version`, F3 et la liste des serveurs affichent AetherSpigot |
| Démarrage | Configs HCF et plugins installés depuis le jar. Un plugin du même nom déjà présent sous un autre fichier est gardé |
| Réseau Java 17 | Le Netty de la 1.8.8 plante en epoll sur Java 9+ (« Unable to access address of buffer »). Le serveur passe en NIO tout seul |

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
