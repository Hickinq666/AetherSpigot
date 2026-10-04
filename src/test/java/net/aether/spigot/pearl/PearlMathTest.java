package net.aether.spigot.pearl;

import net.aether.spigot.config.YamlDoc;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PearlMathTest {

    @Test
    void crossesFenceIntoAirBehind() {
        Grid grid = new Grid();
        grid.set(0, 64, 0, PearlMath.BlockKind.FENCE);
        PearlMath.Decision decision = PearlMath.resolve(0, 64, 0, 1, 0, 0, -4, 64, 0, false, grid, rules());
        assertEquals(PearlMath.Type.TELEPORT, decision.type);
        assertTrue(decision.x > 1.0D);
    }

    @Test
    void refundsWhenFenceCrossIsDisabled() {
        Grid grid = new Grid();
        grid.set(0, 64, 0, PearlMath.BlockKind.FENCE);
        PearlMath.Rules rules = rules();
        rules.pearlThruFence = false;
        PearlMath.Decision decision = PearlMath.resolve(0, 64, 0, 1, 0, 0, -4, 64, 0, false, grid, rules);
        assertEquals(PearlMath.Type.REFUND, decision.type);
        assertEquals("fence", decision.reason);
        assertTrue(decision.returnPearl);
    }

    @Test
    void refundsClosePearl() {
        Grid grid = new Grid();
        grid.set(0, 64, 0, PearlMath.BlockKind.FENCE);
        PearlMath.Decision decision = PearlMath.resolve(0, 64, 0, 1, 0, 0, 1.2D, 64, 0, false, grid, rules());
        assertEquals(PearlMath.Type.REFUND, decision.type);
        assertEquals("close", decision.reason);
    }

    @Test
    void critblockLandsOnTop() {
        Grid grid = new Grid();
        grid.set(2, 70, 2, PearlMath.BlockKind.SLAB);
        PearlMath.Decision decision = PearlMath.resolve(2, 70, 2, 0, 1, 0, 2, 64, 2, false, grid, rules());
        assertEquals(PearlMath.Type.TELEPORT, decision.type);
        assertEquals(71.0D, decision.y, 0.01D);
    }

    @Test
    void refusesOneByOneHole() {
        Grid grid = new Grid();
        grid.set(0, 64, 0, PearlMath.BlockKind.FENCE);
        grid.set(1, 64, 1, PearlMath.BlockKind.SOLID);
        grid.set(1, 64, -1, PearlMath.BlockKind.SOLID);
        PearlMath.Decision decision = PearlMath.resolve(0, 64, 0, 1, 0, 0, -5, 64, 0, false, grid, rules());
        assertEquals(PearlMath.Type.REFUND, decision.type);
        assertEquals("one-by-one", decision.reason);
    }

    @Test
    void solidWallStopsInFront() {
        Grid grid = new Grid();
        grid.set(3, 64, 0, PearlMath.BlockKind.SOLID);
        PearlMath.Decision decision = PearlMath.resolve(3, 64, 0, 1, 0, 0, -2, 64, 0, false, grid, rules());
        assertEquals(PearlMath.Type.TELEPORT, decision.type);
        assertEquals(2.5D, decision.x, 0.01D);
    }

    private static PearlMath.Rules rules() {
        try {
            String raw = new String(Files.readAllBytes(Path.of("src/main/resources/pearls.yml")), StandardCharsets.UTF_8);
            return PearlMath.Rules.from(YamlDoc.parse(raw));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static final class Grid implements PearlMath.BlockView {
        private final Map<String, PearlMath.BlockKind> blocks = new HashMap<String, PearlMath.BlockKind>();

        void set(int x, int y, int z, PearlMath.BlockKind kind) {
            blocks.put(x + ":" + y + ":" + z, kind);
        }

        public PearlMath.BlockKind kind(int x, int y, int z) {
            PearlMath.BlockKind kind = blocks.get(x + ":" + y + ":" + z);
            return kind == null ? PearlMath.BlockKind.AIR : kind;
        }
    }
}
