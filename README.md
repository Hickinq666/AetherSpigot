# AetherSpigot

Serveur **Spigot 1.8.8** pour le practice HCF d'AetherNetwork. C'est un fork de [NachoSpigot](https://github.com/CobbleSword/NachoSpigot) (lui-même basé sur TacoSpigot, PaperSpigot et Spigot) : toutes les sources du serveur sont dans ce dépôt, et le practice est codé dans le serveur, pas dans un plugin.

Le jar prêt à lancer est `bundle/AetherSpigot.jar` (57 Mo). Il contient tout : Minecraft 1.8.8, CraftBukkit, NachoSpigot, le practice et ViaVersion. Il ne télécharge rien et ne compile rien au démarrage.

## Lancer

Copie `bundle/AetherSpigot.jar` dans le dossier de ton serveur sous le nom `spigot.jar`, puis :

```bash
java -Xms4G -Xmx4G -jar spigot.jar nogui
```

Ou pose `run.sh` à côté de `spigot.jar` et lance `sh run.sh`. Depuis ce dépôt, `sh run.sh` fait tourner le serveur dans `server/`.

Au premier démarrage, le serveur écrit `server.properties`, `bukkit.yml` et `spigot.yml` avec le preset HCF (400 slots, view-distance 4, sans mobs), les configs practice dans `aether/`, et installe ViaVersion, ViaBackwards et ViaRewind dans `plugins/`. Un ancien `plugins/AetherSpigot.jar` est renommé en `.ancien` : le practice est maintenant dans le serveur.

Lis l'[EULA Minecraft](https://aka.ms/MinecraftEULA), puis écris `eula=true` dans `eula.txt`. Prends Java 17 ou 21 : ViaVersion 5 ne tourne pas sur Java 8. Dans un `.sh` sous Linux, `pause` n'existe pas : retire cette ligne de ton script.

Le jar contient le code de Mojang : garde ce dépôt et le jar privés.

## Ce qu'AetherSpigot change dans NachoSpigot

| Partie | Effet |
| --- | --- |
| Nom | `/version`, F3 et la liste des serveurs affichent AetherSpigot. Le blocage « NachoSpigot n'est plus maintenu » au démarrage est retiré |
| Practice dans le serveur | `net.aether.spigot` (configs, menu, perles, combat, enchantements, `/hide`, `/see`) est compilé dans le serveur. `CraftServer` le démarre avant les plugins. Il n'apparaît pas dans `/plugins` |
| Patches dans le code du serveur | Aucun patch gameplay n'est un listener qui corrige après coup : chaque règle est appelée par le code Minecraft au moment où il décide (`org.aetherspigot.AetherHooks` et `PatchHooks`). Seul le menu `/aether` utilise les événements d'inventaire |
| Knockback | Calculé à la place de la formule NachoSpigot dans `EntityLiving` (corps à corps, flèche, bouchon de canne au contact) avec le profil actif de `aether/knockback/`. Le hit delay est posé au début de `damageEntity`. `/kb` de NachoSpigot est remplacé par `/knockback` |
| Dégâts | Critique, armure, Protection sans aléatoire et chute calculés dans `EntityHuman.attack` et `EntityLiving` |
| Perles | Cooldown et spawn vérifiés dans `ItemEnderPearl` avant que la perle existe. À l'impact, `EntityEnderPearl` choisit la destination HCF (fences, slabs, refund) avant de téléporter : le joueur n'est jamais déplacé puis corrigé. Lancer sur clic de fence dans `PlayerConnection` |
| Enchantements | Plafonds appliqués pendant le calcul de la table, de l'enclume et du slot créatif |
| Autres patches | Dispersion des projectiles et vitesse des potions à la création du projectile, anti-phase dans le paquet de déplacement, anti-spam dans le paquet de chat, drop pendant qu'on mange, faim, régénération, lit, apparitions, IA des monstres, chute des golems, durabilité des blocs pendant le calcul de l'explosion, respawn automatique, refus des clients trop anciens au login |
| Isolation | Un joueur caché avec `hidePlayer` est caché entièrement : ses flèches, perles, potions, bouchon de canne, items jetés et TNT n'apparaissent pas, ses sons et ses effets (potion qui se brise, flèche qui touche, bloc posé) ne sont pas envoyés. Ses projectiles et ses potions ne touchent pas les joueurs qui ne le voient pas, et il ne ramasse pas leurs items. Désactivable avec `-Daether.isolation=false` |
| Démarrage | Configs HCF et ViaVersion installés depuis le jar |

Le code AetherSpigot dans les fichiers NMS est marqué `// AetherSpigot`. Le reste vient de NachoSpigot : optimisations réseau, entity tracker, hit detection, explosions, potions compensées (voir la liste dans le [README de NachoSpigot](https://github.com/CobbleSword/NachoSpigot#patches)).

ViaVersion, ViaBackwards et ViaRewind sont des projets GPL. Leurs sources sont sur [github.com/ViaVersion](https://github.com/ViaVersion). `bash scripts/bundle-via.sh` les retélécharge.

## Menu

`/config` ou `/aether` ouvre le coffre. Tu changes les slots, les items et les actions dans `menus.yml`.

| Clic | Effet |
| --- | --- |
| Gauche | Ouvre une section, bascule un booléen, augmente un nombre, ou fait tourner un choix |
| Droit | Tu écris la valeur dans le chat |
| Shift | Diminue un nombre, ou retire une ligne de liste |

`cancel` dans le chat annule la saisie. Le lore vert veut dire que le réglage est appliqué tout de suite. Le lore rouge est enregistré mais pas encore branché dans le serveur (threads Netty, tick des chunks, redstone Eigencraft).

## Commandes

| Commande | Rôle |
| --- | --- |
| `/config` | Menu. `/config reload` relit les fichiers |
| `/aether version` | Version, Java, protocole ViaVersion |
| `/knockback` | Créer, voir, activer et éditer les profils |
| `/ping [joueur]` | Ping |
| `/tps` | TPS de Spigot. `/aetherspigot:tps` donne aussi la mémoire, les chunks et les entités |
| `/clearlag` | Items, flèches, XP |
| `/unloadchunks` | Chunks sans joueur proche |
| `/setmaxplayers <n>` | Slots affichés |
| `/hide <joueur> [observateur]` | Tu ne vois plus ce joueur, ni ses projectiles, ni ses sons. Avec un observateur, c'est lui qui ne le voit plus |
| `/see <joueur> [observateur]` | Annule `/hide` |

Les textes de ces commandes sont dans `messages.yml`. Les textes système (kick spam, durabilité) sont dans `language.yml`.

Permission complète : `aether.admin` (op par défaut). `/hide` et `/see` demandent `aether.isolation`. `/ping` est ouvert à tout le monde.

## Fichiers

Tout est dans le dossier `aether/` du serveur, créé au premier démarrage. Les réglages NachoSpigot restent dans `nacho.yml`, `paper.yml` et `taco.yml`.

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

Profil de knockback global au premier boot : **HCF**. `/knockback setactive Practice` ou `Combo` change le feeling. Le `knockback.yml` de NachoSpigot ne sert que si `overrideKnockback` est coupé dans `aether.yml`, et pour les boules de neige, œufs et perles.

Potions de soin, dans chaque profil de `knockback/` :

| Réglage | Défaut | Effet |
| --- | --- | --- |
| `potionSpeed` / `potionVerticalOffset` | 0.5 / -10 | Vitesse et angle du lancer (vanilla : 0.5 / -20) |
| `potionFast` | true | +20 % de vitesse |
| `potionFall` | 0.05 | Gravité par tick (vanilla 0.05). Plus bas : la potion vole plus loin et plus longtemps |
| `potionDrag` | 0.99 | Vitesse gardée à chaque tick (vanilla 0.99). Plus bas : la potion freine et retombe plus près |
| `potionStartForward` / `potionStartHeight` | 0 / -0.1 | Point de départ : blocs devant les yeux (-0.5 à 2) et hauteur par rapport aux yeux |
| `potionInheritMotion` | 0 | Part de ta vitesse ajoutée à la potion (0 à 1). 1 : elle suit ta course |
| `potionDownEnabled` | false | Réglage à part quand tu regardes vers le bas |
| `potionDownPitch` / `potionDownSpeed` / `potionDownVerticalOffset` | 70 / 0.5 / -20 | Angle à partir duquel il s'applique, vitesse et angle du lancer |

`projectiles.overrideOnlyHealthPotion: false` applique ces réglages à toutes les potions. `projectiles.lagCompensatedPotions: true` active les potions compensées par le ping de NachoSpigot. Les nouveaux réglages d'une mise à jour du jar sont ajoutés tout seuls aux fichiers du dossier `aether/`, sans changer tes valeurs.

Cooldown de perle : `practice.pearlCooldownSeconds` (16). Armure de teamfight : `players.armorDamageDivision` (12, plus de dégâts passent qu'avec 4). Protection sans aléatoire : `players.protRandomness: false` et `protectionModifier: 22`.

## Compiler

Java 17+ et Maven :

```bash
sh scripts/build.sh
```

Le script compile l'API et le serveur, lance les tests du practice, puis copie le jar dans `bundle/AetherSpigot.jar`. La première fois, il installe la dépendance `minecraft-server 1.8.8` avec BuildTools (Java 8 requis) : elle n'existe sur aucun dépôt Maven public.

| Dossier | Contenu |
| --- | --- |
| `AetherSpigot-API/` | API Bukkit de NachoSpigot |
| `AetherSpigot-Server/src/main/java/net/minecraft/server/` | Code serveur Minecraft modifié |
| `AetherSpigot-Server/src/main/java/net/aether/spigot/` | Practice : configs, menu, knockback, perles, combat, commandes |
| `AetherSpigot-Server/src/main/java/org/aetherspigot/` | Démarrage, isolation, liens entre le NMS et le practice |
| `AetherSpigot-Server/src/main/aether/` | Configs par défaut et jars ViaVersion embarqués |
