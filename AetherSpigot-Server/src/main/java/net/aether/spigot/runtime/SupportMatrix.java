package net.aether.spigot.runtime;

public final class SupportMatrix {

    public enum Level {
        LIVE, PARTIAL, SERVER
    }

    private SupportMatrix() {
    }

    public static Level of(String file, String path) {
        if (file == null) {
            return Level.PARTIAL;
        }
        if (file.startsWith("knockback/") || "pearls.yml".equals(file) || "enchants.yml".equals(file)
                || "messages.yml".equals(file) || "language.yml".equals(file) || "menus.yml".equals(file)
                || "via.yml".equals(file) || "loader.yml".equals(file)) {
            return Level.LIVE;
        }
        if ("generator/Default.yml".equals(file)) {
            return Level.SERVER;
        }
        if (path == null || path.isEmpty()) {
            return Level.PARTIAL;
        }
        if (starts(path, "chunks.", "async-pathsearch.", "networking.", "sounds.", "particles.", "events.",
                "blocks.", "generating.", "sponges.")) {
            return Level.SERVER;
        }
        if (exact(path,
                "worlds.ticking.disableBlockLighting",
                "worlds.ticking.tickBlocks",
                "worlds.ticking.tickTileEntities",
                "worlds.ticking.tickBeacons",
                "worlds.ticking.tickBrewingStands",
                "worlds.ticking.tickHoppers",
                "worlds.ticking.tickFurnaces",
                "worlds.ticking.tickChunks",
                "worlds.ticking.tickBiomeCache",
                "worlds.ticking.tickVillages",
                "players.playerMovedTooQuicklyCheck",
                "players.playerIdleTimer",
                "players.playerBorderDamage",
                "players.dynamicParalellisedTracking",
                "players.playersNeededParalellisedTracking",
                "players.hideEntitiesThrownHidden",
                "players.optimizePlayerPrinter",
                "spawners.minimumSpawnDelay",
                "spawners.maximumSpawnDelay",
                "spawners.spawnCount",
                "spawners.spawnRange",
                "spawners.playerSpawnRange",
                "spawners.lightLevelCheck",
                "mobs.entityCollisions",
                "mobs.entityCollisionsDelay",
                "factions.fixFreecamCegging",
                "factions.sandMerging",
                "factions.tntMerging",
                "factions.hidePistonAnimations",
                "factions.hideTntEntity",
                "factions.hideSandEntity",
                "factions.fixSandChunkUnloading",
                "factions.optimizeTNTMovement",
                "factions.optimizeSandMovement",
                "factions.fixEastWestCannon",
                "patches.patchGhostBuckets",
                "patches.obfuscateEntityEquipment",
                "modifiers.furnaceModifier",
                "modifiers.brewingModifier")) {
            return Level.SERVER;
        }
        if (starts(path, "projectiles.", "durability.blockResistances")) {
            return Level.PARTIAL;
        }
        return Level.LIVE;
    }

    private static boolean starts(String path, String... prefixes) {
        for (int i = 0; i < prefixes.length; i++) {
            if (path.startsWith(prefixes[i])) {
                return true;
            }
        }
        return false;
    }

    private static boolean exact(String path, String... values) {
        for (int i = 0; i < values.length; i++) {
            if (path.equals(values[i])) {
                return true;
            }
        }
        return false;
    }
}
