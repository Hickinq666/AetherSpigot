package net.aether.spigot.combat;

import net.aether.spigot.config.YamlDoc;
import net.aether.spigot.enchant.EnchantLimits;
import net.aether.spigot.message.Texts;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageAndEnchantTest {

    @Test
    void hcfArmorLetsMoreDamageThroughThanTightDivisor() {
        double hcf = DamageMath.remainingAfterArmor(20, 12);
        double tight = DamageMath.remainingAfterArmor(20, 4);
        assertEquals(12.0D / 32.0D, hcf, 0.0001D);
        assertTrue(hcf > tight);
    }

    @Test
    void lowerProtectionModifierIncreasesDamage() {
        double vanilla = DamageMath.protectionFraction(16, 25);
        double softer = DamageMath.protectionFraction(16, 20);
        assertTrue(softer < vanilla);
    }

    @Test
    void critRescaleAndEnchantCap() throws Exception {
        assertEquals(12.0D, DamageMath.rescaleCrit(15.0D, 1.5D, 1.2D), 0.0001D);
        String raw = new String(Files.readAllBytes(Path.of("src/main/resources/enchants.yml")), StandardCharsets.UTF_8);
        EnchantLimits limits = EnchantLimits.from(YamlDoc.parse(raw));
        Map<String, Integer> levels = new LinkedHashMap<String, Integer>();
        levels.put("DAMAGE_ALL", Integer.valueOf(10));
        levels.put("PROTECTION_ENVIRONMENTAL", Integer.valueOf(8));
        assertTrue(limits.clamp(levels));
        assertEquals(Integer.valueOf(5), levels.get("DAMAGE_ALL"));
        assertEquals(Integer.valueOf(2), levels.get("PROTECTION_ENVIRONMENTAL"));
    }

    @Test
    void placeholdersAndVersions() {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("ping", "42");
        values.put("player", "Aether");
        assertEquals("Ping 42 de Aether", Texts.apply("Ping %ping% de %player%", values));
        assertTrue(Texts.compareVersions("1.0.0", "1.2.0") < 0);
        assertEquals(0, Texts.compareVersions("5.11.0", "5.11.0"));
    }
}
