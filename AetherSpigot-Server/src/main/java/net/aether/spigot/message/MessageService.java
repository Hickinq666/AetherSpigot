package net.aether.spigot.message;

import net.aether.spigot.config.ConfigStore;
import net.aether.spigot.config.YamlDoc;
import net.aether.spigot.text.Colors;
import org.bukkit.command.CommandSender;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MessageService {

    private final ConfigStore store;
    // Textes du jar : un messages.yml d'une ancienne version n'a pas les clés ajoutées depuis.
    private final Map<String, YamlDoc> bundled = new HashMap<String, YamlDoc>();

    public MessageService(ConfigStore store) {
        this.store = store;
    }

    public void send(CommandSender sender, String path, Map<String, String> values) {
        List<String> lines = lines(path);
        for (int i = 0; i < lines.size(); i++) {
            String formatted = Colors.color(Texts.apply(lines.get(i), values));
            if (!formatted.isEmpty()) {
                sender.sendMessage(formatted);
            }
        }
    }

    public String one(String path, Map<String, String> values) {
        List<String> lines = lines(path);
        if (lines.isEmpty()) {
            return path;
        }
        return Texts.apply(lines.get(0), values);
    }

    public List<String> lines(String path) {
        YamlDoc messages = store.doc("messages.yml");
        YamlDoc language = store.doc("language.yml");
        List<String> found = read(messages, path);
        if (found != null) {
            return found;
        }
        found = read(language, path);
        if (found != null) {
            return found;
        }
        found = read(language, "messages." + path);
        if (found != null) {
            return found;
        }
        found = read(bundled("messages.yml"), path);
        if (found != null) {
            return found;
        }
        found = read(bundled("language.yml"), "messages." + path);
        if (found != null) {
            return found;
        }
        return Collections.singletonList(path);
    }

    private YamlDoc bundled(String name) {
        if (!bundled.containsKey(name)) {
            bundled.put(name, load(name));
        }
        return bundled.get(name);
    }

    private static YamlDoc load(String name) {
        InputStream in = MessageService.class.getClassLoader().getResourceAsStream("aether/defaults/" + name);
        if (in == null) {
            return null;
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return YamlDoc.parse(out.toString("UTF-8"));
        } catch (IOException ex) {
            return null;
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static List<String> read(YamlDoc doc, String path) {
        if (doc == null) {
            return null;
        }
        YamlDoc.Node node = doc.at(path);
        if (node == null) {
            return null;
        }
        if (node.kind == YamlDoc.Kind.LIST) {
            return doc.list(path);
        }
        if (node.kind == YamlDoc.Kind.SCALAR) {
            String text = doc.text(path, "");
            return Collections.singletonList(text);
        }
        return null;
    }
}
