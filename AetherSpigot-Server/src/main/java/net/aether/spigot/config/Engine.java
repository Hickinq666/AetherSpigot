package net.aether.spigot.config;

import net.aether.spigot.enchant.EnchantLimits;
import net.aether.spigot.knockback.KnockbackProfile;
import net.aether.spigot.pearl.PearlMath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Engine {

    public String brand = "AetherSpigot";
    public String preset = "HCF_PRACTICE";
    public String activeKnockback = "HCF";
    public boolean overrideKnockback = true;
    public boolean overrideRod = true;
    public boolean overrideArrow = true;
    public Map<String, KnockbackProfile> profiles = new LinkedHashMap<String, KnockbackProfile>();
    public PearlMath.Rules pearls = new PearlMath.Rules();
    public EnchantLimits enchants = EnchantLimits.from(YamlDoc.parse("enchant-limits: {}\n"));
    public double fallModifier = 1.0D;
    public double critModifier = 1.5D;
    public boolean customDamage = true;
    public boolean protRandom = false;
    public double protectionModifier = 22.0D;
    public double armorDivision = 12.0D;
    public boolean patchArrowBounce = true;
    public boolean patchDoubleHit = true;
    public boolean patchDropEat = true;
    public boolean patchMisdirection = true;
    public boolean patchRegen = true;
    public double regenMultiplier = 0.5D;
    public int chatSpamPerSecond = 8;
    public boolean chatSpamKick = false;
    public int pearlCooldownSeconds = 16;
    public boolean blockPearlsInSpawn = false;
    public double spawnRadius = 8.0D;
    public boolean hungerLock = false;
    public boolean autoRespawn = true;
    public double phaseLeniency = 0.0625D;
    public boolean phaseChests = true;
    public boolean trackMove = true;
    public boolean mobAi = false;
    public boolean naturalSpawn = false;
    public boolean babyZombies = false;
    public boolean chickenJockey = false;
    public boolean villagerZombie = false;
    public boolean spiderJockey = false;
    public boolean ironFall = true;
    public boolean snowFall = true;
    public int chunkMobLimit = 0;
    public int nearbySpawner = 0;
    public boolean checkSleep = false;
    public int randomTickSpeed = 3;
    public boolean tickWeather = false;
    public boolean explosionDestroyer = true;
    public boolean damageOnlyTnt = true;
    public String durabilityItem = "STONE_AXE";
    public Map<String, Integer> blockDurability = new LinkedHashMap<String, Integer>();
    public List<String> potionLaunch = new ArrayList<String>();
    public List<String> pearlLaunch = new ArrayList<String>();
    public boolean launchFence = true;
    public String glitchMessage = "";
    public String glitchCommand = "";
    public boolean overrideOnlyHealth = true;
    public double arrowRandomness = 1.0D;
    public double rodRandomness = 1.0D;
    public double snowRandomness = 1.0D;
    public double eggRandomness = 1.0D;
    public double pearlRandomness = 1.0D;
    public double potionRandomness = 1.0D;
    public boolean lagCompensatedPotions = false;
    public boolean fillerEnabled = true;
    public String fillerType = "STAINED_GLASS_PANE";
    public int fillerData = 7;
    public String pageType = "CARPET";
    public int pageData = 2;
    public String pageBack = "&dPage precedente";
    public String pageForward = "&dPage suivante";
    public String backType = "BED";
    public int backData = 0;
    public String backDisplay = "&cRetour";
    public boolean cmdConfig = true;
    public boolean cmdPing = true;
    public boolean cmdTps = true;
    public boolean cmdChunks = true;
    public boolean cmdClearlag = true;
    public boolean cmdSlots = true;
    public boolean cmdVersion = true;
    public boolean cmdKnockback = true;
    public boolean clearItems = true;
    public boolean clearArrows = true;
    public boolean clearXp = true;
    public boolean clearMonsters = false;
    public boolean viaEnabled = true;
    public boolean viaWarn = true;
    public boolean viaShow = true;
    public int viaKickBelow = 0;

    public static Engine load(ConfigStore store) {
        Engine engine = new Engine();
        YamlDoc aether = store.doc("aether.yml");
        YamlDoc pearls = store.doc("pearls.yml");
        YamlDoc enchants = store.doc("enchants.yml");
        YamlDoc loader = store.doc("loader.yml");
        YamlDoc via = store.doc("via.yml");
        if (loader != null) {
            engine.brand = loader.text("brand", engine.brand);
            engine.preset = loader.text("preset", engine.preset);
        }
        if (aether != null) {
            engine.brand = aether.text("serverBranding", engine.brand);
            engine.activeKnockback = aether.text("knockback.globalActiveKnockback", engine.activeKnockback);
            engine.overrideKnockback = aether.bool("knockback.overrideDefaultKnockback", true);
            engine.overrideRod = aether.bool("knockback.overrideRodKnockback", true);
            engine.overrideArrow = aether.bool("knockback.overrideArrowKnockback", true);
            engine.fallModifier = aether.decimal("modifiers.fallDamageModifier", 1.0D);
            engine.critModifier = aether.decimal("modifiers.critDamageModifier", 1.5D);
            engine.customDamage = aether.bool("players.customArmorAndProtection", true);
            engine.protRandom = aether.bool("players.protRandomness", false);
            engine.protectionModifier = aether.decimal("players.protectionModifier", 22.0D);
            engine.armorDivision = aether.decimal("players.armorDamageDivision", 12.0D);
            engine.patchArrowBounce = aether.bool("patches.patchArrowBounceBug", true);
            engine.patchDoubleHit = aether.bool("patches.patchDoubleHitBug", true);
            engine.patchDropEat = aether.bool("patches.patchDropEatBug", true);
            engine.patchMisdirection = aether.bool("patches.patchProjectileMisdirection", true);
            engine.patchRegen = aether.bool("patches.patchHCFRegenBug", true);
            engine.regenMultiplier = aether.decimal("patches.hcfRegenMultiplier", 0.5D);
            engine.chatSpamPerSecond = aether.integer("patches.chatSpamPerSecond", 8);
            engine.chatSpamKick = aether.bool("patches.chatSpamKick", false);
            engine.pearlCooldownSeconds = aether.integer("practice.pearlCooldownSeconds", 16);
            engine.blockPearlsInSpawn = aether.bool("practice.blockPearlsInSpawn", false);
            engine.spawnRadius = aether.decimal("practice.spawnRadius", 8.0D);
            engine.hungerLock = aether.bool("practice.hungerLock", false);
            engine.autoRespawn = aether.bool("players.autoRespawnPlayer", true);
            engine.phaseLeniency = aether.decimal("players.playerPhaseLeniency", 0.0625D);
            engine.phaseChests = aether.bool("players.playerPhaseChestsAndAnvils", true);
            engine.trackMove = aether.bool("events.firePlayerMoveEvent", true);
            engine.mobAi = aether.bool("mobs.mobAI", false);
            engine.naturalSpawn = aether.bool("mobs.naturalMobSpawn", false);
            engine.babyZombies = aether.bool("mobs.babyZombieSpawning", false);
            engine.chickenJockey = aether.bool("mobs.zombieOnChickenSpawning", false);
            engine.villagerZombie = aether.bool("mobs.villagerZombieSpawning", false);
            engine.spiderJockey = aether.bool("mobs.skeletonOnSpiderSpawning", false);
            engine.ironFall = aether.bool("mobs.ironGolemFallDamage", true);
            engine.snowFall = aether.bool("mobs.snowGolemFallDamage", true);
            engine.chunkMobLimit = aether.integer("spawners.chunkMobLimit", 0);
            engine.nearbySpawner = aether.integer("spawners.maximumNearbyEntities", 0);
            engine.checkSleep = aether.bool("players.checkPlayerSleep", false);
            engine.randomTickSpeed = aether.integer("worlds.ticking.randomTickSpeed", 3);
            engine.tickWeather = aether.bool("worlds.ticking.tickWeather", false);
            engine.explosionDestroyer = aether.bool("durability.explosionDestroyer", true);
            engine.damageOnlyTnt = aether.bool("durability.damageOnlyByTNT", true);
            engine.durabilityItem = aether.text("durability.durabilityItemChecker", "STONE_AXE");
            engine.blockDurability = durability(aether.list("durability.blockDurabilities"));
            engine.potionLaunch = upper(aether.list("players.potionLaunchOnClick"));
            engine.cmdConfig = aether.bool("commands.enableSpigotConfigCommand", true);
            engine.cmdPing = aether.bool("commands.enablePingCommand", true);
            engine.cmdTps = aether.bool("commands.enableTPSCommand", true);
            engine.cmdChunks = aether.bool("commands.enableChunkUnloadCommand", true);
            engine.cmdClearlag = aether.bool("commands.enableClearLagCommand", true);
            engine.cmdSlots = aether.bool("commands.enableSetMaxPlayersCommand", true);
            engine.cmdVersion = aether.bool("commands.enableVersionCommand", true);
            engine.cmdKnockback = aether.bool("commands.enableKnockbackCommand", true);
            engine.clearItems = aether.bool("clearlag.items", true);
            engine.clearArrows = aether.bool("clearlag.arrows", true);
            engine.clearXp = aether.bool("clearlag.xp", true);
            engine.clearMonsters = aether.bool("clearlag.monsters", false);
            engine.fillerEnabled = aether.bool("menus.fillerEnabled", true);
            engine.fillerType = aether.text("menus.fillerType", "STAINED_GLASS_PANE");
            engine.fillerData = aether.integer("menus.fillerData", 7);
            engine.pageType = aether.text("menus.pageType", "CARPET");
            engine.pageData = aether.integer("menus.pageData", 2);
            engine.pageBack = aether.text("menus.pageBackDisplay", engine.pageBack);
            engine.pageForward = aether.text("menus.pageForwardDisplay", engine.pageForward);
            engine.backType = aether.text("menus.backButtonType", "BED");
            engine.backData = aether.integer("menus.backButtonData", 0);
            engine.backDisplay = aether.text("menus.backButtonDisplay", "&cRetour");
            engine.arrowRandomness = aether.decimal("projectiles.arrowTrajectoryRandomness", 1.0D);
            engine.rodRandomness = aether.decimal("projectiles.rodTrajectoryRandomness", 1.0D);
            engine.snowRandomness = aether.decimal("projectiles.snowballTrajectoryRandomness", 1.0D);
            engine.eggRandomness = aether.decimal("projectiles.eggTrajectoryRandomness", 1.0D);
            engine.pearlRandomness = aether.decimal("projectiles.pearlTrajectoryRandomness", 1.0D);
            engine.potionRandomness = aether.decimal("projectiles.potionTrajectoryRandomness", 1.0D);
            engine.overrideOnlyHealth = aether.bool("projectiles.overrideOnlyHealthPotion", true);
            engine.lagCompensatedPotions = aether.bool("projectiles.lagCompensatedPotions", false);
        }
        if (pearls != null) {
            engine.pearls = PearlMath.Rules.from(pearls);
            engine.pearlLaunch = upper(pearls.list("teleport.misc.launchOnOtherClick"));
            engine.launchFence = pearls.bool("teleport.misc.launchOnFenceClick", true);
            engine.glitchMessage = pearls.text("antiGlitch.glitchReturnMessage", "");
            engine.glitchCommand = pearls.text("antiGlitch.glitchReturnCommand", "");
        }
        if (enchants != null) {
            engine.enchants = EnchantLimits.from(enchants);
        }
        if (via != null) {
            engine.viaEnabled = via.bool("enabled", true);
            engine.viaWarn = via.bool("warn-if-missing", true);
            engine.viaShow = via.bool("show-client-version", true);
            engine.viaKickBelow = via.integer("kick-below-protocol", 0);
        }
        Map<String, KnockbackProfile> profiles = new LinkedHashMap<String, KnockbackProfile>();
        for (String file : store.files()) {
            if (file.startsWith("knockback/") && file.endsWith(".yml")) {
                String name = file.substring("knockback/".length(), file.length() - 4);
                profiles.put(name.toLowerCase(Locale.ROOT), KnockbackProfile.from(name, store.doc(file)));
            }
        }
        engine.profiles = profiles;
        return engine;
    }

    public KnockbackProfile profile(String name) {
        if (name == null) {
            return null;
        }
        return profiles.get(name.toLowerCase(Locale.ROOT));
    }

    public List<KnockbackProfile> profileList() {
        return new ArrayList<KnockbackProfile>(profiles.values());
    }

    private static Map<String, Integer> durability(List<String> lines) {
        Map<String, Integer> map = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int colon = line.lastIndexOf(':');
            if (colon <= 0) {
                continue;
            }
            try {
                String name = line.substring(0, colon).trim().toUpperCase(Locale.ROOT);
                int hits = Integer.parseInt(line.substring(colon + 1).trim());
                map.put(name, Integer.valueOf(hits));
            } catch (NumberFormatException ignored) {
                continue;
            }
        }
        return map;
    }

    private static List<String> upper(List<String> values) {
        List<String> copy = new ArrayList<String>();
        for (int i = 0; i < values.size(); i++) {
            copy.add(values.get(i).trim().toUpperCase(Locale.ROOT));
        }
        return Collections.unmodifiableList(copy);
    }
}
