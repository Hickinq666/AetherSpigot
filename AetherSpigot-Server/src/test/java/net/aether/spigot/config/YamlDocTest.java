package net.aether.spigot.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlDocTest {

    @Test
    void roundTripKeepsComments() {
        String raw = "# titre\nserverBranding: AetherSpigot # marque\nworlds:\n  ticking:\n    tickWeather: true\n    randomTickSpeed: 3\n";
        YamlDoc doc = YamlDoc.parse(raw);
        assertEquals("AetherSpigot", doc.text("serverBranding", ""));
        assertEquals(3, doc.integer("worlds.ticking.randomTickSpeed", 0));
        assertTrue(doc.bool("worlds.ticking.tickWeather", false));
        assertEquals(raw, doc.save());
    }

    @Test
    void setKeepsSiblingComment() {
        YamlDoc doc = YamlDoc.parse("enchants:\n  # limite\n  sharp: 5\n  prot: 4\n");
        doc.set("enchants.sharp", Integer.valueOf(2));
        String saved = doc.save();
        assertTrue(saved.contains("# limite"));
        assertTrue(saved.contains("sharp: 2"));
        assertTrue(saved.contains("prot: 4"));
    }

    @Test
    void readsQuotedMessageAndList() {
        YamlDoc doc = YamlDoc.parse("messages:\n  ping: '&dPing: &f%ping%'\nblocks:\n  - HOPPER\n  - CHEST\n");
        assertEquals("&dPing: &f%ping%", doc.text("messages.ping", ""));
        assertEquals(Arrays.asList("HOPPER", "CHEST"), doc.list("blocks"));
        doc.set("blocks", Arrays.asList("FENCE", "ANVIL"));
        assertEquals(Arrays.asList("FENCE", "ANVIL"), doc.list("blocks"));
    }

    @Test
    void shippedConfigsRoundTrip() throws Exception {
        Path root = Path.of(System.getProperty("basedir", ".") + "/src/main/aether/aether/defaults");
        List<String> files = Arrays.asList(
                "aether.yml", "enchants.yml", "language.yml", "messages.yml",
                "loader.yml", "pearls.yml", "menus.yml", "via.yml",
                "knockback/Default.yml", "knockback/HCF.yml", "knockback/Combo.yml",
                "knockback/Practice.yml", "generator/Default.yml");
        for (String file : files) {
            String raw = new String(Files.readAllBytes(root.resolve(file)), StandardCharsets.UTF_8).replace("\r\n", "\n");
            YamlDoc doc = YamlDoc.parse(raw);
            assertFalse(doc.childKeys("").isEmpty(), file);
            assertEquals(raw.endsWith("\n") ? raw : raw + "\n", normalize(doc.save()), file);
        }
    }

    private static String normalize(String value) {
        if (!value.endsWith("\n")) {
            return value + "\n";
        }
        return value;
    }
}
