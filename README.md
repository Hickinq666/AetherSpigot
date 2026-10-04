# AetherSpigot

Serveur **Spigot 1.8.8** pour le practice HCF. Le jar à lancer est `bundle/AetherSpigot-1.8.8.jar` (21 Mo). Il contient Spigot, CraftBukkit, les patches AetherSpigot, le moteur practice et ViaVersion, déjà compilés.

## Lancer

Pose `AetherSpigot-1.8.8.jar` dans le dossier du serveur. Renomme-le `spigot.jar` si ton hébergeur l'exige. Puis, avec Java 17 :

```bash
java -Xms4G -Xmx4G -jar AetherSpigot-1.8.8.jar
```

Au tout premier lancement, il prend le server.jar 1.8.8 officiel chez Mojang (8 Mo, vérifié par SHA-1) et le patche en 2 à 3 secondes dans `cache/`. Les lancements suivants démarrent directement. Rien à compiler, pas de BuildTools.

Il écrit ensuite `server.properties`, `bukkit.yml` et `spigot.yml` avec le preset HCF (400 slots, view-distance 4, sans mobs) et installe dans `plugins/` le moteur practice et ViaVersion.

Lis l'[EULA Minecraft](https://aka.ms/MinecraftEULA), puis écris `eula=true` dans `eula.txt`. Prends Java 17 : ViaVersion 5 ne tourne pas sur Java 8.

Sous Windows, `pause` dans un `.bat` est normal. Dans un `.sh` sous Linux, `pause` n'existe pas : retire cette ligne. Depuis ce dépôt, `sh run.sh` lance le jar dans `server/`.

### Pourquoi Mojang est téléchargé à part

Le code du serveur Minecraft appartient à Mojang, qui interdit de redistribuer une version modifiée. Paper fait pareil avec son jar « paperclip » : le jar distribué contient seulement notre code et un patch binaire. Le code de Mojang vient de ses propres serveurs.

Le moteur practice (`bundle/plugins/AetherSpigot.jar`) gère les perles, le knockback, les enchantements, l'armure, les patches de combat et un menu qui écrit dans les YAML. Les clients 1.7 à 1.21 rejoignent grâce à ViaVersion, ViaBackwards et ViaRewind.

## Refaire le jar

```bash
sudo apt install git curl openjdk-8-jdk-headless openjdk-17-jdk-headless
pip install bsdiff4
mvn package && cp target/AetherSpigot.jar bundle/plugins/
sh scripts/build-aetherspigot.sh
python3 scripts/make_server_jar.py
```

`build-aetherspigot.sh` lance BuildTools avec Java 8, applique `patches/server/`, embarque `bundle/plugins/` et `server-config/`, et compile `server/AetherSpigot-1.8.8.jar`. `make_server_jar.py` calcule le patch entre ce jar et celui de Mojang, puis écrit `bundle/AetherSpigot-1.8.8.jar` avec le lanceur de `launcher/`.

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
