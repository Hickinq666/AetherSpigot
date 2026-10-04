package net.aether.spigot.runtime;

import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class NmsBridge {

    private NmsBridge() {
    }

    public static int ping(Player player) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Field ping = handle.getClass().getField("ping");
            return ping.getInt(handle);
        } catch (ReflectiveOperationException ex) {
            return -1;
        }
    }

    public static boolean setMaxPlayers(int max) {
        try {
            Object server = org.bukkit.Bukkit.getServer();
            Object handle = server.getClass().getMethod("getHandle").invoke(server);
            Object playerList = handle.getClass().getMethod("getPlayerList").invoke(handle);
            Field field = find(playerList.getClass(), "maxPlayers");
            if (field == null) {
                return false;
            }
            field.setAccessible(true);
            field.setInt(playerList, max);
            return true;
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    public static void respawn(Player player) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object connection = handle.getClass().getField("playerConnection").get(handle);
            String nms = handle.getClass().getPackage().getName();
            Class<?> packetClass = Class.forName(nms + ".PacketPlayInClientCommand");
            Class<?> enumClass = Class.forName(nms + ".PacketPlayInClientCommand$EnumClientCommand");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object perform = Enum.valueOf((Class<? extends Enum>) enumClass.asSubclass(Enum.class), "PERFORM_RESPAWN");
            Object packet = packetClass.getConstructor(enumClass).newInstance(perform);
            Method accept = connection.getClass().getMethod("a", Class.forName(nms + ".Packet"));
            accept.invoke(connection, packet);
        } catch (Throwable ignored) {
            player.spigot();
        }
    }

    private static Field find(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ex) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
