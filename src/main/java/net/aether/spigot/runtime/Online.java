package net.aether.spigot.runtime;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class Online {

    private Online() {
    }

    @SuppressWarnings("unchecked")
    public static List<Player> players() {
        try {
            Object raw = Bukkit.class.getMethod("getOnlinePlayers").invoke(null);
            if (raw instanceof Player[]) {
                return Arrays.asList((Player[]) raw);
            }
            if (raw instanceof Collection) {
                return new ArrayList<Player>((Collection<Player>) raw);
            }
        } catch (ReflectiveOperationException ignored) {
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }

    public static Player byName(String name) {
        if (name == null) {
            return null;
        }
        for (Player player : players()) {
            if (player.getName().equalsIgnoreCase(name)) {
                return player;
            }
        }
        return null;
    }
}
