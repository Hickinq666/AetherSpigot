package net.aether.spigot.pearl;

import net.aether.spigot.config.YamlDoc;

/**
 * Décide où une enderpearl HCF doit envoyer le joueur.
 * Le monde est lu via {@link BlockView} pour rester testable sans serveur.
 */
public final class PearlMath {

    public enum BlockKind {
        AIR, FENCE, STRING, WEB, PLANT, SLAB, STAIR, CHEST, BED, WALL, PISTON,
        END_FRAME, ENCHANT, ANVIL, DAYLIGHT, TRAPDOOR, HOPPER, SOLID, OTHER;

        public boolean passable() {
            return this == AIR || this == STRING || this == PLANT;
        }
    }

    public interface BlockView {
        BlockKind kind(int x, int y, int z);
    }

    public static final class Rules {
        public boolean fixBlockGlitch = true;
        public boolean fixFenceGlitch = true;
        public boolean refundSuffocation = true;
        public boolean refundRiskyPearl = true;
        public boolean refundOnCritblock = false;
        public boolean refundIfSoClose = true;
        public double refundCloseDistance = 1.0D;
        public boolean glitchReturnPearl = true;
        public boolean pearlThruFence = true;
        public boolean pearlThruString = true;
        public boolean pearlThruWeb = true;
        public boolean pearlThruPlants = true;
        public boolean pearlHopperUnderFence = true;
        public boolean pearlChestUnderFence = true;
        public double crossPearlMoveHelper = 0.35D;
        public boolean hitBlockTeleport = true;
        public boolean beforeBlockTeleport = true;
        public int maxTeleportBlocks = 3;
        public boolean entityTpGetOutOneByOne = true;
        public boolean instantlyTaliTeleport = false;
        public boolean entityTpHitDetection = true;
        public double pearlDamage = 5.0D;
        public boolean pearlParticlesEnabled = true;
        public int pearlParticleCount = 32;
        public boolean spawnEndermite = true;
        public final KindRule slabs = KindRule.of(true, true, true);
        public final KindRule stairs = KindRule.of(true, true, true);
        public final KindRule chests = KindRule.of(true, true, true);
        public final KindRule beds = KindRule.of(true, true, true);
        public final KindRule cobblewalls = KindRule.of(true, true, true);
        public final KindRule pistons = KindRule.of(true, true, true);
        public final KindRule endFrames = KindRule.of(true, true, true);
        public final KindRule enchantTables = KindRule.of(true, true, true);
        public final KindRule anvils = KindRule.of(true, true, true);
        public final KindRule daylightSensors = KindRule.of(true, true, true);
        public final KindRule trapdoors = KindRule.of(true, true, true);

        public static Rules from(YamlDoc doc) {
            Rules rules = new Rules();
            rules.fixBlockGlitch = doc.bool("antiGlitch.fixBlockGlitch", rules.fixBlockGlitch);
            rules.fixFenceGlitch = doc.bool("antiGlitch.fixFenceGlitch", rules.fixFenceGlitch);
            rules.refundSuffocation = doc.bool("antiGlitch.refundSuffocation", rules.refundSuffocation);
            rules.refundRiskyPearl = doc.bool("antiGlitch.refundRiskyPearl", rules.refundRiskyPearl);
            rules.refundOnCritblock = doc.bool("antiGlitch.refundOnCritblock", rules.refundOnCritblock);
            rules.refundIfSoClose = doc.bool("antiGlitch.refundIfSoClose", rules.refundIfSoClose);
            rules.refundCloseDistance = doc.decimal("antiGlitch.refundCloseDistance", rules.refundCloseDistance);
            rules.glitchReturnPearl = doc.bool("antiGlitch.glitchReturnPearl", rules.glitchReturnPearl);
            rules.instantlyTaliTeleport = doc.bool("teleport.misc.instantlyTaliTeleport", rules.instantlyTaliTeleport);
            rules.entityTpHitDetection = doc.bool("teleport.misc.entityTpHitDetection", rules.entityTpHitDetection);
            rules.entityTpGetOutOneByOne = doc.bool("teleport.misc.entityTpGetOutOneByOne", rules.entityTpGetOutOneByOne);
            rules.spawnEndermite = doc.bool("teleport.misc.spawnEndermite", rules.spawnEndermite);
            rules.pearlThruFence = doc.bool("teleport.misc.pearlThruFence", rules.pearlThruFence);
            rules.pearlThruString = doc.bool("teleport.misc.pearlThruString", rules.pearlThruString);
            rules.pearlThruWeb = doc.bool("teleport.misc.pearlThruWeb", rules.pearlThruWeb);
            rules.pearlThruPlants = doc.bool("teleport.misc.pearlThruPlants", rules.pearlThruPlants);
            rules.pearlHopperUnderFence = doc.bool("teleport.misc.pearlHopperUnderFence", rules.pearlHopperUnderFence);
            rules.pearlChestUnderFence = doc.bool("teleport.misc.pearlChestUnderFence", rules.pearlChestUnderFence);
            rules.pearlParticlesEnabled = doc.bool("teleport.misc.pearlParticlesEnabled", rules.pearlParticlesEnabled);
            rules.pearlParticleCount = doc.integer("teleport.misc.pearlParticleCount", rules.pearlParticleCount);
            rules.pearlDamage = doc.decimal("teleport.misc.pearlDamage", rules.pearlDamage);
            rules.crossPearlMoveHelper = doc.decimal("teleport.misc.crossPearlMoveHelper", rules.crossPearlMoveHelper);
            rules.hitBlockTeleport = doc.bool("teleport.misc.hitBlockTeleport", rules.hitBlockTeleport);
            rules.beforeBlockTeleport = doc.bool("teleport.misc.beforeBlockTeleport", rules.beforeBlockTeleport);
            rules.maxTeleportBlocks = doc.integer("teleport.misc.maxTeleportBlocks", rules.maxTeleportBlocks);
            readKind(doc, "slabs", rules.slabs);
            readKind(doc, "stairs", rules.stairs);
            readKind(doc, "chests", rules.chests);
            readKind(doc, "beds", rules.beds);
            readKind(doc, "cobblewalls", rules.cobblewalls);
            readKind(doc, "pistons", rules.pistons);
            readKind(doc, "end-frames", rules.endFrames);
            readKind(doc, "enchant-tables", rules.enchantTables);
            readKind(doc, "anvils", rules.anvils);
            readKind(doc, "daylight-sensors", rules.daylightSensors);
            readKind(doc, "trapdoors", rules.trapdoors);
            return rules;
        }

        private static void readKind(YamlDoc doc, String path, KindRule rule) {
            rule.enabled = doc.bool(path + ".enabled", rule.enabled);
            rule.cross = doc.bool(path + ".crosspearl", rule.cross);
            rule.diagonal = doc.bool(path + ".diagonal", rule.diagonal);
            rule.crit = doc.bool(path + ".critblock", rule.crit);
        }
    }

    public static final class KindRule {
        public boolean enabled;
        public boolean cross;
        public boolean diagonal;
        public boolean crit;

        static KindRule of(boolean cross, boolean diagonal, boolean crit) {
            KindRule rule = new KindRule();
            rule.enabled = true;
            rule.cross = cross;
            rule.diagonal = diagonal;
            rule.crit = crit;
            return rule;
        }
    }

    public enum Type {
        TELEPORT, REFUND, VANILLA
    }

    public static final class Decision {
        public final Type type;
        public final double x;
        public final double y;
        public final double z;
        public final String reason;
        public final boolean returnPearl;

        private Decision(Type type, double x, double y, double z, String reason, boolean returnPearl) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.reason = reason;
            this.returnPearl = returnPearl;
        }

        public static Decision teleport(double x, double y, double z) {
            return new Decision(Type.TELEPORT, x, y, z, "", false);
        }

        public static Decision refund(String reason, boolean returnPearl) {
            return new Decision(Type.REFUND, 0.0D, 0.0D, 0.0D, reason, returnPearl);
        }

        public static Decision vanilla() {
            return new Decision(Type.VANILLA, 0.0D, 0.0D, 0.0D, "", false);
        }
    }

    private PearlMath() {
    }

    public static Decision resolve(int blockX, int blockY, int blockZ, int stepX, int stepY, int stepZ,
                                   double shooterX, double shooterY, double shooterZ, boolean risky,
                                   BlockView view, Rules rules) {
        if (stepX == 0 && stepY == 0 && stepZ == 0) {
            stepX = 1;
        }
        BlockKind kind = view.kind(blockX, blockY, blockZ);
        boolean diagonal = (stepX != 0 ? 1 : 0) + (stepY != 0 ? 1 : 0) + (stepZ != 0 ? 1 : 0) > 1;
        KindRule partial = ruleFor(kind, rules);
        boolean cross = wantsCross(kind, rules, partial);
        if (diagonal && partial != null && !partial.diagonal) {
            cross = false;
        }
        if (partial != null && partial.enabled && partial.crit && airColumn(view, blockX, blockY + 1, blockZ)) {
            if (rules.refundOnCritblock) {
                return Decision.refund("critblock", rules.glitchReturnPearl);
            }
            Decision onTop = Decision.teleport(blockX + 0.5D, blockY + 1.0D, blockZ + 0.5D);
            Decision close = tooClose(onTop, shooterX, shooterY, shooterZ, rules);
            if (close != null) {
                return close;
            }
            if (risky && rules.refundRiskyPearl) {
                return Decision.refund("risky", rules.glitchReturnPearl);
            }
            return onTop;
        }
        if (cross) {
            int depth = Math.max(1, rules.maxTeleportBlocks);
            for (int step = 1; step <= depth; step++) {
                int x = blockX + stepX * step;
                int y = blockY + stepY * step;
                int z = blockZ + stepZ * step;
                if (!spaceFree(view, x, y, z)) {
                    continue;
                }
                if (rules.entityTpGetOutOneByOne && oneByOne(view, x, y, z, stepX, stepZ)) {
                    return Decision.refund("one-by-one", rules.glitchReturnPearl);
                }
                double destX = x + 0.5D - stepX * rules.crossPearlMoveHelper;
                double destZ = z + 0.5D - stepZ * rules.crossPearlMoveHelper;
                Decision decision = Decision.teleport(destX, y, destZ);
                Decision close = tooClose(decision, shooterX, shooterY, shooterZ, rules);
                if (close != null) {
                    return close;
                }
                if (risky && rules.refundRiskyPearl) {
                    return Decision.refund("risky", rules.glitchReturnPearl);
                }
                return decision;
            }
            if (rules.refundSuffocation) {
                return Decision.refund("suffocation", rules.glitchReturnPearl);
            }
        }
        if ((kind == BlockKind.FENCE || kind == BlockKind.WALL) && rules.fixFenceGlitch && !cross) {
            return Decision.refund("fence", rules.glitchReturnPearl);
        }
        if (rules.fixBlockGlitch && !kind.passable() && rules.beforeBlockTeleport) {
            int x = blockX - stepX;
            int y = blockY - stepY;
            int z = blockZ - stepZ;
            if (spaceFree(view, x, y, z)) {
                Decision decision = Decision.teleport(x + 0.5D, y, z + 0.5D);
                Decision close = tooClose(decision, shooterX, shooterY, shooterZ, rules);
                if (close != null) {
                    return close;
                }
                if (risky && rules.refundRiskyPearl) {
                    return Decision.refund("risky", rules.glitchReturnPearl);
                }
                return decision;
            }
        }
        if (rules.fixBlockGlitch && !kind.passable() && !rules.hitBlockTeleport && rules.refundSuffocation) {
            return Decision.refund("block", rules.glitchReturnPearl);
        }
        if (risky && rules.refundRiskyPearl) {
            return Decision.refund("risky", rules.glitchReturnPearl);
        }
        return Decision.vanilla();
    }

    private static boolean wantsCross(BlockKind kind, Rules rules, KindRule partial) {
        if (kind == BlockKind.FENCE) {
            return rules.pearlThruFence;
        }
        if (kind == BlockKind.STRING) {
            return rules.pearlThruString;
        }
        if (kind == BlockKind.WEB) {
            return rules.pearlThruWeb;
        }
        if (kind == BlockKind.PLANT) {
            return rules.pearlThruPlants;
        }
        if (kind == BlockKind.HOPPER) {
            return rules.pearlHopperUnderFence;
        }
        if (kind == BlockKind.CHEST) {
            return rules.pearlChestUnderFence && partial != null && partial.enabled && partial.cross;
        }
        return partial != null && partial.enabled && partial.cross;
    }

    private static KindRule ruleFor(BlockKind kind, Rules rules) {
        switch (kind) {
            case SLAB: return rules.slabs;
            case STAIR: return rules.stairs;
            case CHEST: return rules.chests;
            case BED: return rules.beds;
            case WALL: return rules.cobblewalls;
            case PISTON: return rules.pistons;
            case END_FRAME: return rules.endFrames;
            case ENCHANT: return rules.enchantTables;
            case ANVIL: return rules.anvils;
            case DAYLIGHT: return rules.daylightSensors;
            case TRAPDOOR: return rules.trapdoors;
            default: return null;
        }
    }

    private static boolean airColumn(BlockView view, int x, int y, int z) {
        return view.kind(x, y, z).passable() && view.kind(x, y + 1, z).passable();
    }

    public static boolean spaceFree(BlockView view, int x, int y, int z) {
        BlockKind feet = view.kind(x, y, z);
        BlockKind head = view.kind(x, y + 1, z);
        return feet.passable() && head.passable();
    }

    private static boolean oneByOne(BlockView view, int x, int y, int z, int stepX, int stepZ) {
        int sideX = stepZ == 0 ? 0 : 1;
        int sideZ = stepX == 0 ? 0 : 1;
        if (stepX != 0 && stepZ != 0) {
            sideX = 1;
            sideZ = 0;
        }
        if (sideX == 0 && sideZ == 0) {
            sideX = 1;
        }
        BlockKind left = view.kind(x + sideX, y, z + sideZ);
        BlockKind right = view.kind(x - sideX, y, z - sideZ);
        return !left.passable() && !right.passable();
    }

    private static Decision tooClose(Decision decision, double shooterX, double shooterY, double shooterZ, Rules rules) {
        if (!rules.refundIfSoClose) {
            return null;
        }
        double dx = decision.x - shooterX;
        double dy = decision.y - shooterY;
        double dz = decision.z - shooterZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance <= rules.refundCloseDistance) {
            return Decision.refund("close", rules.glitchReturnPearl);
        }
        return null;
    }
}
