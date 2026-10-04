package net.aether.spigot.knockback;

import net.aether.spigot.config.YamlDoc;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnockbackMathTest {

    @Test
    void pushesVictimAwayFromAttacker() {
        KnockbackProfile profile = profile("HCF");
        KnockbackMath.Input input = base();
        input.dirX = 1.0D;
        input.dirZ = 0.0D;
        KnockbackMath.Vec vec = KnockbackMath.apply(profile, input);
        assertTrue(vec.x > 0.2D);
        assertEquals(0.0D, vec.z, 0.001D);
    }

    @Test
    void sprintRaisesAdvancedHorizontal() {
        KnockbackProfile profile = profile("HCF");
        KnockbackMath.Input calm = base();
        calm.sprinting = false;
        KnockbackMath.Input sprint = base();
        sprint.sprinting = true;
        double calmX = KnockbackMath.apply(profile, calm).x;
        double sprintX = KnockbackMath.apply(profile, sprint).x;
        assertTrue(sprintX > calmX);
    }

    @Test
    void backHitUsesOnePointSevenValue() {
        KnockbackProfile profile = profile("HCF");
        KnockbackMath.Input input = base();
        input.backHit = true;
        input.motX = 0.0D;
        KnockbackMath.Vec vec = KnockbackMath.apply(profile, input);
        assertEquals(profile.onePointSevenHorizontal, vec.x, 0.0001D);
    }

    @Test
    void verticalLimitZeroesAdvancedKnockback() {
        KnockbackProfile profile = profile("HCF");
        profile.enableVerticalLimit = true;
        profile.verticalLimit = 0.2D;
        KnockbackMath.Vec vec = KnockbackMath.apply(profile, base());
        assertEquals(0.0D, vec.y, 0.0001D);
    }

    @Test
    void simpleExtraHorizontalAppliesOnSprint() {
        KnockbackProfile profile = profile("Practice");
        assertEquals("SIMPLE", profile.type);
        KnockbackMath.Input sprint = base();
        sprint.sprinting = true;
        sprint.motX = 0.0D;
        KnockbackMath.Vec vec = KnockbackMath.apply(profile, sprint);
        assertEquals(profile.horizontal + profile.extraHorizontal, vec.x, 0.0001D);
    }

    @Test
    void comboModePullsVerticalDown() {
        KnockbackProfile profile = profile("Combo");
        KnockbackMath.Input input = base();
        input.comboTicks = profile.comboTicks;
        KnockbackMath.Vec vec = KnockbackMath.apply(profile, input);
        assertEquals(profile.comboVelocity, vec.y, 0.0001D);
    }

    private static KnockbackMath.Input base() {
        KnockbackMath.Input input = new KnockbackMath.Input();
        input.dirX = 1.0D;
        input.onGround = true;
        return input;
    }

    private static KnockbackProfile profile(String name) {
        try {
            Path path = Path.of("src/main/resources/knockback/" + name + ".yml");
            String raw = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            return KnockbackProfile.from(name, YamlDoc.parse(raw));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
