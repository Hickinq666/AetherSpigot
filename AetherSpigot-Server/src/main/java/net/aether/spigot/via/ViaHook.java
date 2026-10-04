package net.aether.spigot.via;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ViaHook {

    private final Map<Integer, String> names = new HashMap<Integer, String>();

    public ViaHook() {
        put(5, "1.7.10");
        put(47, "1.8.x");
        put(107, "1.9");
        put(108, "1.9.1");
        put(109, "1.9.2");
        put(110, "1.9.4");
        put(210, "1.10");
        put(315, "1.11");
        put(316, "1.11.2");
        put(335, "1.12");
        put(338, "1.12.1");
        put(340, "1.12.2");
        put(393, "1.13");
        put(401, "1.13.1");
        put(404, "1.13.2");
        put(477, "1.14");
        put(480, "1.14.1");
        put(485, "1.14.2");
        put(490, "1.14.3");
        put(498, "1.14.4");
        put(573, "1.15");
        put(575, "1.15.1");
        put(578, "1.15.2");
        put(735, "1.16");
        put(736, "1.16.1");
        put(751, "1.16.2");
        put(753, "1.16.3");
        put(754, "1.16.5");
        put(755, "1.17");
        put(756, "1.17.1");
        put(757, "1.18.1");
        put(758, "1.18.2");
        put(759, "1.19");
        put(760, "1.19.2");
        put(761, "1.19.3");
        put(762, "1.19.4");
        put(763, "1.20.1");
        put(764, "1.20.2");
        put(765, "1.20.4");
        put(766, "1.20.6");
        put(767, "1.21.1");
        put(768, "1.21.3");
        put(769, "1.21.4");
        put(770, "1.21.5");
        put(771, "1.21.6");
        put(772, "1.21.8");
    }

    private void put(int protocol, String name) {
        names.put(Integer.valueOf(protocol), name);
    }

    public boolean present() {
        return Bukkit.getPluginManager().getPlugin("ViaVersion") != null;
    }

    public int protocol(Player player) {
        if (player == null || !present()) {
            return -1;
        }
        UUID id = player.getUniqueId();
        int modern = invoke("com.viaversion.viaversion.api.Via", id, player);
        if (modern >= 0) {
            return modern;
        }
        return invoke("us.myles.ViaVersion.api.Via", id, player);
    }

    public String describe(Player player) {
        if (!present()) {
            return "absent";
        }
        int protocol = protocol(player);
        if (protocol < 0) {
            return "present";
        }
        return name(protocol) + " (" + protocol + ")";
    }

    public String name(int protocol) {
        String known = names.get(Integer.valueOf(protocol));
        if (known != null) {
            return known;
        }
        return "protocole " + protocol;
    }

    private int invoke(String className, UUID id, Player player) {
        try {
            Class<?> via = Class.forName(className);
            Object api = via.getMethod("getAPI").invoke(null);
            try {
                Object value = api.getClass().getMethod("getPlayerVersion", UUID.class).invoke(api, id);
                return ((Number) value).intValue();
            } catch (NoSuchMethodException ignored) {
                Object value = api.getClass().getMethod("getPlayerVersion", Object.class).invoke(api, player);
                return ((Number) value).intValue();
            }
        } catch (Throwable ignored) {
            return -1;
        }
    }
}
