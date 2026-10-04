package net.aether.spigot.menu;

import net.aether.spigot.AetherPlugin;
import net.aether.spigot.config.YamlDoc;
import net.aether.spigot.knockback.KnockbackProfile;
import net.aether.spigot.runtime.Online;
import net.aether.spigot.runtime.SupportMatrix;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MenuListener implements Listener {

    private final AetherPlugin plugin;

    public MenuListener(AetherPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player) || !(event.getInventory().getHolder() instanceof MenuHolder)) {
            return;
        }
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        if (!player.hasPermission("aether.config") && !player.hasPermission("aether.admin") && !player.isOp()) {
            plugin.messages().send(player, "editor.noPermission", null);
            return;
        }
        MenuHolder holder = (MenuHolder) event.getInventory().getHolder();
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }
        plugin.menus().play(player, "sound-click");
        if ("main".equals(holder.kind)) {
            handleMain(player, slot);
            return;
        }
        if (slot == 45) {
            reopen(player, holder, holder.page - 1);
            return;
        }
        if (slot == 53) {
            reopen(player, holder, holder.page + 1);
            return;
        }
        if (slot == 49) {
            back(player, holder);
            return;
        }
        if ("file".equals(holder.kind) && slot < MenuService.PAGE) {
            handleFile(player, holder, slot, event.isShiftClick(), event.isRightClick());
            return;
        }
        if ("list".equals(holder.kind)) {
            handleList(player, holder, slot, event.isShiftClick(), event.isRightClick());
            return;
        }
        if ("knockback".equals(holder.kind) && slot < MenuService.PAGE) {
            handleProfiles(player, holder, slot, event.isShiftClick());
            return;
        }
        if ("knockback".equals(holder.kind) && slot == 47) {
            ask(player, session("profile-create", "", "", holder));
            return;
        }
        if ("players".equals(holder.kind) && slot < MenuService.PAGE) {
            handlePlayers(player, holder, slot, event.isShiftClick());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        final AetherPlugin.EditSession session = plugin.edits().remove(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        final String message = event.getMessage();
        final Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                applyChat(player, session, message);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.edits().remove(event.getPlayer().getUniqueId());
    }

    private void handleMain(Player player, int slot) {
        String action = plugin.menus().actionAt(slot);
        if (action == null || action.isEmpty()) {
            return;
        }
        if ("knockback".equals(action)) {
            plugin.menus().openKnockback(player, 0);
        } else if ("players".equals(action)) {
            plugin.menus().openPlayers(player, 0);
        } else if ("reload".equals(action)) {
            plugin.reloadAll();
            Map<String, String> values = new HashMap<String, String>();
            values.put("ms", "0");
            plugin.messages().send(player, "command.config-command.configReloaded", values);
            plugin.menus().openMain(player);
        } else if ("close".equals(action)) {
            player.closeInventory();
        } else if (action.startsWith("command:")) {
            player.performCommand(action.substring("command:".length()));
        } else if (action.startsWith("file:")) {
            String rest = action.substring("file:".length());
            int hash = rest.indexOf('#');
            if (hash < 0) {
                plugin.menus().openFile(player, rest, "", 0);
            } else {
                plugin.menus().openFile(player, rest.substring(0, hash), rest.substring(hash + 1), 0);
            }
        }
    }

    private void handleFile(Player player, MenuHolder holder, int slot, boolean shift, boolean right) {
        YamlDoc doc = plugin.store().doc(holder.file);
        if (doc == null) {
            return;
        }
        List<String> keys = doc.childKeys(holder.path);
        int index = holder.page * MenuService.PAGE + slot;
        if (index >= keys.size()) {
            return;
        }
        String key = keys.get(index);
        String path = holder.path.isEmpty() ? key : holder.path + "." + key;
        YamlDoc.Node node = doc.at(path);
        if (node == null) {
            return;
        }
        if (node.kind == YamlDoc.Kind.MAP) {
            plugin.menus().openFile(player, holder.file, path, 0);
            return;
        }
        if (node.kind == YamlDoc.Kind.LIST) {
            plugin.menus().openList(player, holder.file, path, 0);
            return;
        }
        String current = doc.text(path, "");
        if (right || !isNumber(current) && !"true".equalsIgnoreCase(current) && !"false".equalsIgnoreCase(current)) {
            if (!right && cycle(player, holder, path, current)) {
                return;
            }
            AetherPlugin.EditSession session = session("value", holder.file, path, holder);
            ask(player, session);
            return;
        }
        if ("true".equalsIgnoreCase(current) || "false".equalsIgnoreCase(current)) {
            commit(player, holder.file, path, Boolean.valueOf(!"true".equalsIgnoreCase(current)), holder);
            return;
        }
        double value = Double.parseDouble(current);
        double step = step(current, value);
        double next = shift ? value - step : value + step;
        commit(player, holder.file, path, boxed(current, next), holder);
    }

    private void handleList(Player player, MenuHolder holder, int slot, boolean shift, boolean right) {
        if (slot == 50) {
            ask(player, session("list-add", holder.file, holder.path, holder));
            return;
        }
        if (slot >= MenuService.PAGE) {
            return;
        }
        YamlDoc doc = plugin.store().doc(holder.file);
        List<String> values = new ArrayList<String>(doc.list(holder.path));
        int index = holder.page * MenuService.PAGE + slot;
        if (index >= values.size()) {
            return;
        }
        if (shift) {
            values.remove(index);
            commit(player, holder.file, holder.path, values, holder);
            return;
        }
        if (right) {
            AetherPlugin.EditSession session = session("list-set", holder.file, holder.path, holder);
            session.index = index;
            ask(player, session);
        }
    }

    private void handleProfiles(Player player, MenuHolder holder, int slot, boolean shift) {
        List<KnockbackProfile> profiles = plugin.engine().profileList();
        int index = holder.page * MenuService.PAGE + slot;
        if (index >= profiles.size()) {
            return;
        }
        KnockbackProfile profile = profiles.get(index);
        if (shift) {
            plugin.update("aether.yml", "knockback.globalActiveKnockback", profile.name);
            plugin.menus().openKnockback(player, holder.page);
            return;
        }
        plugin.menus().openFile(player, "knockback/" + profile.name + ".yml", "", 0);
    }

    private void handlePlayers(Player player, MenuHolder holder, int slot, boolean shift) {
        List<Player> players = Online.players();
        int index = holder.page * MenuService.PAGE + slot;
        if (index >= players.size()) {
            return;
        }
        Player target = players.get(index);
        if (shift) {
            plugin.personalProfile(target, null);
        } else {
            List<KnockbackProfile> profiles = plugin.engine().profileList();
            if (profiles.isEmpty()) {
                return;
            }
            String current = plugin.personalProfile(target);
            int found = 0;
            for (int i = 0; i < profiles.size(); i++) {
                if (profiles.get(i).name.equalsIgnoreCase(current)) {
                    found = i + 1;
                    break;
                }
            }
            plugin.personalProfile(target, profiles.get(found % profiles.size()).name);
        }
        plugin.menus().openPlayers(player, holder.page);
    }

    private boolean cycle(Player player, MenuHolder holder, String path, String current) {
        String key = path.substring(path.lastIndexOf('.') + 1);
        String[] options = null;
        if ("redstoneOptimization".equals(key)) {
            options = new String[] {"EIGENCRAFT", "PANDAWIRE", "DEFAULT"};
        } else if ("knockbackType".equals(key)) {
            options = new String[] {"SIMPLE", "ADVANCED"};
        } else if ("preset".equals(key)) {
            options = new String[] {"HCF_PRACTICE", "CUSTOM"};
        }
        if (options == null) {
            return false;
        }
        int index = 0;
        for (int i = 0; i < options.length; i++) {
            if (options[i].equalsIgnoreCase(current)) {
                index = (i + 1) % options.length;
                break;
            }
        }
        commit(player, holder.file, path, options[index], holder);
        return true;
    }

    private void applyChat(Player player, AetherPlugin.EditSession session, String message) {
        if ("cancel".equalsIgnoreCase(message)) {
            plugin.messages().send(player, "editor.cancelled", null);
            restore(player, session);
            return;
        }
        if ("profile-create".equals(session.mode)) {
            String name = message.replaceAll("[^A-Za-z0-9_\\-]", "");
            if (name.isEmpty() || name.length() > 24) {
                Map<String, String> values = new HashMap<String, String>();
                values.put("value", message);
                values.put("type", "nom");
                plugin.messages().send(player, "editor.invalid", values);
                return;
            }
            if (!plugin.store().createProfile(name)) {
                Map<String, String> values = new HashMap<String, String>();
                values.put("name", name);
                plugin.messages().send(player, "command.knockback.createExists", values);
            } else {
                plugin.reloadAll();
                Map<String, String> values = new HashMap<String, String>();
                values.put("name", name);
                plugin.messages().send(player, "command.knockback.createProfile", values);
            }
            plugin.menus().openKnockback(player, 0);
            return;
        }
        if ("list-add".equals(session.mode) || "list-set".equals(session.mode)) {
            YamlDoc doc = plugin.store().doc(session.file);
            List<String> values = new ArrayList<String>(doc.list(session.path));
            if ("list-add".equals(session.mode)) {
                values.add(message.trim());
            } else if (session.index >= 0 && session.index < values.size()) {
                values.set(session.index, message.trim());
            }
            plugin.update(session.file, session.path, values);
            tell(player, session.file, session.path, message.trim());
            restore(player, session);
            return;
        }
        Object parsed = parseValue(plugin.store().doc(session.file), session.path, message.trim());
        if (parsed == null) {
            Map<String, String> values = new HashMap<String, String>();
            values.put("value", message);
            values.put("type", "valeur");
            plugin.messages().send(player, "editor.invalid", values);
            return;
        }
        plugin.update(session.file, session.path, parsed);
        tell(player, session.file, session.path, String.valueOf(parsed));
        restore(player, session);
    }

    private void commit(Player player, String file, String path, Object value, MenuHolder holder) {
        plugin.update(file, path, value);
        tell(player, file, path, String.valueOf(value));
        plugin.menus().play(player, "sound-success");
        reopen(player, holder, holder.page);
    }

    private void tell(Player player, String file, String path, String value) {
        Map<String, String> values = new HashMap<String, String>();
        values.put("setting", path);
        values.put("value", value);
        String key = plugin.support(file, path) == SupportMatrix.Level.SERVER ? "editor.savedServer" : "editor.set";
        plugin.messages().send(player, key, values);
    }

    private void ask(Player player, AetherPlugin.EditSession session) {
        plugin.edits().put(player.getUniqueId(), session);
        player.closeInventory();
        Map<String, String> values = new HashMap<String, String>();
        values.put("setting", session.path == null || session.path.isEmpty() ? session.mode : session.path);
        String current = "";
        if (session.file != null && !session.file.isEmpty() && session.path != null) {
            YamlDoc doc = plugin.store().doc(session.file);
            if (doc != null) {
                current = doc.text(session.path, "");
            }
        }
        values.put("current", current);
        plugin.messages().send(player, "editor.typeValue", values);
    }

    private void restore(Player player, AetherPlugin.EditSession session) {
        if ("knockback".equals(session.returnKind)) {
            plugin.menus().openKnockback(player, session.returnPage);
        } else if ("players".equals(session.returnKind)) {
            plugin.menus().openPlayers(player, session.returnPage);
        } else if ("list".equals(session.returnKind)) {
            plugin.menus().openList(player, session.returnFile, session.returnPath, session.returnPage);
        } else if ("file".equals(session.returnKind)) {
            plugin.menus().openFile(player, session.returnFile, session.returnPath, session.returnPage);
        } else {
            plugin.menus().openMain(player);
        }
    }

    private void reopen(Player player, MenuHolder holder, int page) {
        if ("file".equals(holder.kind)) {
            plugin.menus().openFile(player, holder.file, holder.path, page);
        } else if ("list".equals(holder.kind)) {
            plugin.menus().openList(player, holder.file, holder.path, page);
        } else if ("knockback".equals(holder.kind)) {
            plugin.menus().openKnockback(player, page);
        } else if ("players".equals(holder.kind)) {
            plugin.menus().openPlayers(player, page);
        }
    }

    private void back(Player player, MenuHolder holder) {
        if ("list".equals(holder.kind)) {
            int dot = holder.path.lastIndexOf('.');
            String parent = dot < 0 ? "" : holder.path.substring(0, dot);
            plugin.menus().openFile(player, holder.file, parent, 0);
            return;
        }
        if ("file".equals(holder.kind) && holder.path != null && holder.path.indexOf('.') >= 0) {
            plugin.menus().openFile(player, holder.file, holder.path.substring(0, holder.path.lastIndexOf('.')), 0);
            return;
        }
        if ("file".equals(holder.kind) && holder.path != null && !holder.path.isEmpty()) {
            plugin.menus().openFile(player, holder.file, "", 0);
            return;
        }
        if ("file".equals(holder.kind) && holder.file.startsWith("knockback/")) {
            plugin.menus().openKnockback(player, 0);
            return;
        }
        plugin.menus().openMain(player);
    }

    private static AetherPlugin.EditSession session(String mode, String file, String path, MenuHolder holder) {
        AetherPlugin.EditSession session = new AetherPlugin.EditSession();
        session.mode = mode;
        session.file = file;
        session.path = path;
        session.returnKind = holder.kind;
        session.returnFile = holder.file;
        session.returnPath = holder.path;
        session.returnPage = holder.page;
        return session;
    }

    private static Object parseValue(YamlDoc doc, String path, String message) {
        if (doc == null) {
            return message;
        }
        String current = doc.text(path, "");
        if ("true".equalsIgnoreCase(message) || "oui".equalsIgnoreCase(message)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(message) || "non".equalsIgnoreCase(message)) {
            return Boolean.FALSE;
        }
        if (isNumber(message) && (isNumber(current) || current.isEmpty())) {
            return boxed(current.isEmpty() ? message : current, Double.parseDouble(message));
        }
        return message;
    }

    private static Object boxed(String sample, double value) {
        boolean whole = sample.indexOf('.') < 0 && sample.toLowerCase(Locale.ROOT).indexOf('e') < 0;
        if (whole) {
            return Integer.valueOf((int) Math.round(value));
        }
        return Double.valueOf(value);
    }

    private static boolean isNumber(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static double step(String rendered, double value) {
        boolean whole = rendered.indexOf('.') < 0 && rendered.toLowerCase(Locale.ROOT).indexOf('e') < 0;
        if (whole) {
            return 1.0D;
        }
        double abs = Math.abs(value);
        if (abs < 2.0D) {
            return 0.01D;
        }
        if (abs < 20.0D) {
            return 0.05D;
        }
        return 1.0D;
    }
}
