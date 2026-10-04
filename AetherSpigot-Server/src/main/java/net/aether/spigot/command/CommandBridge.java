package net.aether.spigot.command;

import net.aether.spigot.AetherCore;
import net.aether.spigot.config.YamlDoc;
import net.aether.spigot.knockback.KnockbackProfile;
import net.aether.spigot.message.Texts;
import net.aether.spigot.patch.PatchService;
import net.aether.spigot.runtime.NmsBridge;
import net.aether.spigot.runtime.Online;
import net.aether.spigot.text.Colors;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CommandBridge implements CommandExecutor, TabCompleter {

    private final AetherCore plugin;

    public CommandBridge(AetherCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if ("aether".equals(name)) {
            return aether(sender, args);
        }
        if ("config".equals(name)) {
            return config(sender, args);
        }
        if ("knockback".equals(name)) {
            return knockback(sender, args);
        }
        if ("ping".equals(name)) {
            return ping(sender, args);
        }
        if ("tps".equals(name)) {
            return tps(sender);
        }
        if ("clearlag".equals(name)) {
            return clearlag(sender);
        }
        if ("unloadchunks".equals(name)) {
            return chunks(sender);
        }
        if ("setmaxplayers".equals(name)) {
            return slots(sender, args);
        }
        if ("hide".equals(name) || "see".equals(name)) {
            return isolation(sender, name, args);
        }
        return false;
    }

    private boolean aether(CommandSender sender, String[] args) {
        if (args.length > 0 && "reload".equalsIgnoreCase(args[0])) {
            return reload(sender);
        }
        if (args.length > 0 && "version".equalsIgnoreCase(args[0])) {
            return version(sender);
        }
        if (sender instanceof Player) {
            if (!allowed(sender, "aether.config")) {
                return true;
            }
            plugin.menus().openMain((Player) sender);
            return true;
        }
        plugin.messages().send(sender, "command.config-command.usage", null);
        return true;
    }

    private boolean config(CommandSender sender, String[] args) {
        if (!plugin.engine().cmdConfig) {
            plugin.messages().send(sender, "editor.noPermission", null);
            return true;
        }
        if (args.length > 0 && "reload".equalsIgnoreCase(args[0])) {
            return reload(sender);
        }
        if (!(sender instanceof Player)) {
            plugin.messages().send(sender, "editor.onlyPlayer", null);
            return true;
        }
        if (!allowed(sender, "aether.config")) {
            return true;
        }
        plugin.menus().openMain((Player) sender);
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!allowed(sender, "aether.config")) {
            return true;
        }
        long start = System.nanoTime();
        plugin.reloadAll();
        long ms = (System.nanoTime() - start) / 1_000_000L;
        Map<String, String> values = map();
        values.put("ms", Long.toString(ms));
        plugin.messages().send(sender, "command.config-command.configReloaded", values);
        return true;
    }

    private boolean version(CommandSender sender) {
        if (!plugin.engine().cmdVersion) {
            plugin.messages().send(sender, "editor.noPermission", null);
            return true;
        }
        if (!allowed(sender, "aether.version") && !allowed(sender, "aether.config")) {
            return true;
        }
        Map<String, String> values = map();
        values.put("brand", plugin.engine().brand);
        values.put("version", plugin.getDescription().getVersion());
        values.put("java", System.getProperty("java.version"));
        String via = plugin.via().present() ? "installe" : "absent - lance scripts/bundle-via.sh";
        String protocol = "1.8.8";
        if (sender instanceof Player && plugin.engine().viaShow) {
            via = plugin.via().describe((Player) sender);
            int id = plugin.via().protocol((Player) sender);
            protocol = id < 0 ? "1.8.8" : plugin.via().name(id);
        }
        values.put("via", via);
        values.put("protocol", protocol);
        plugin.messages().send(sender, "command.version-command.versionMessage", values);
        return true;
    }

    private boolean ping(CommandSender sender, String[] args) {
        if (!plugin.engine().cmdPing) {
            plugin.messages().send(sender, "editor.noPermission", null);
            return true;
        }
        Player target;
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                plugin.messages().send(sender, "editor.onlyPlayer", null);
                return true;
            }
            if (!allowed(sender, "aether.ping")) {
                return true;
            }
            target = (Player) sender;
            Map<String, String> values = map();
            values.put("ping", Integer.toString(NmsBridge.ping(target)));
            plugin.messages().send(sender, "command.ping-command.playerPing", values);
            return true;
        }
        if (!allowed(sender, "aether.ping.others")) {
            return true;
        }
        target = Online.byName(args[0]);
        if (target == null) {
            Map<String, String> values = map();
            values.put("player", args[0]);
            plugin.messages().send(sender, "player.playerNotFound", values);
            return true;
        }
        Map<String, String> values = map();
        values.put("player", target.getName());
        values.put("ping", Integer.toString(NmsBridge.ping(target)));
        plugin.messages().send(sender, "command.ping-command.otherPlayerPing", values);
        return true;
    }

    private boolean isolation(CommandSender sender, String name, String[] args) {
        if (!allowed(sender, "aether.isolation")) {
            return true;
        }
        Map<String, String> values = map();
        values.put("command", name);
        if (args.length == 0 || args.length > 2 || (args.length == 1 && !(sender instanceof Player))) {
            plugin.messages().send(sender, "command.isolation-command.usage", values);
            return true;
        }
        Player target = Online.byName(args[0]);
        Player viewer = args.length == 2 ? Online.byName(args[1]) : (Player) sender;
        if (target == null || viewer == null) {
            values.put("player", target == null ? args[0] : args[1]);
            plugin.messages().send(sender, "player.playerNotFound", values);
            return true;
        }
        values.put("player", target.getName());
        values.put("viewer", viewer.getName());
        if (target.equals(viewer)) {
            plugin.messages().send(sender, "command.isolation-command.self", values);
            return true;
        }
        boolean hide = "hide".equals(name);
        if (hide != viewer.canSee(target)) {
            plugin.messages().send(sender, hide ? "command.isolation-command.alreadyHidden" : "command.isolation-command.alreadyShown", values);
            return true;
        }
        if (hide) {
            viewer.hidePlayer(target);
        } else {
            viewer.showPlayer(target);
        }
        boolean forSelf = viewer.equals(sender);
        String key = hide ? (forSelf ? "hidden" : "hiddenFor") : (forSelf ? "shown" : "shownFor");
        plugin.messages().send(sender, "command.isolation-command." + key, values);
        return true;
    }

    private boolean tps(CommandSender sender) {
        if (!plugin.engine().cmdTps || !allowed(sender, "aether.tps")) {
            plugin.messages().send(sender, "editor.noPermission", null);
            return true;
        }
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory() / 1024L / 1024L;
        long free = (runtime.freeMemory() + (runtime.maxMemory() - runtime.totalMemory())) / 1024L / 1024L;
        int chunks = 0;
        int entities = 0;
        int tiles = 0;
        for (World world : Bukkit.getWorlds()) {
            Chunk[] loaded = world.getLoadedChunks();
            chunks += loaded.length;
            entities += world.getEntities().size();
            for (int i = 0; i < loaded.length; i++) {
                tiles += loaded[i].getTileEntities().length;
            }
        }
        int threads = ManagementFactory.getThreadMXBean().getThreadCount();
        int daemon = ManagementFactory.getThreadMXBean().getDaemonThreadCount();
        Map<String, String> values = map();
        values.put("server_tps", plugin.tps().format());
        values.put("free_memory", Long.toString(free));
        values.put("max_memory", Long.toString(max));
        values.put("uptime", uptime());
        values.put("threads", Integer.toString(threads));
        values.put("alive", Integer.toString(threads));
        values.put("interrupted", "0");
        values.put("daemon", Integer.toString(daemon));
        values.put("chunks", Integer.toString(chunks));
        values.put("players", Integer.toString(Online.players().size()));
        values.put("max_players", Integer.toString(plugin.forcedSlots() > 0 ? plugin.forcedSlots() : Bukkit.getMaxPlayers()));
        values.put("entities", Integer.toString(entities));
        values.put("tiles", Integer.toString(tiles));
        plugin.messages().send(sender, "command.tps-command.tpsMessage", values);
        return true;
    }

    private boolean clearlag(CommandSender sender) {
        if (!plugin.engine().cmdClearlag) {
            plugin.messages().send(sender, "command.clear-lag-command.disabled", null);
            return true;
        }
        if (!allowed(sender, "aether.clearlag")) {
            return true;
        }
        int removed = PatchService.clear(plugin.engine());
        Map<String, String> values = map();
        values.put("entities", Integer.toString(removed));
        plugin.messages().send(sender, "command.clear-lag-command.clearedLag", values);
        return true;
    }

    private boolean chunks(CommandSender sender) {
        if (!plugin.engine().cmdChunks) {
            plugin.messages().send(sender, "command.unload-chunks-command.disabled", null);
            return true;
        }
        if (!allowed(sender, "aether.chunks")) {
            return true;
        }
        int amount = PatchService.unloadChunks();
        Map<String, String> values = map();
        values.put("amount", Integer.toString(amount));
        plugin.messages().send(sender, "command.unload-chunks-command.unloadedChunks", values);
        return true;
    }

    private boolean slots(CommandSender sender, String[] args) {
        if (!plugin.engine().cmdSlots || !allowed(sender, "aether.slots")) {
            plugin.messages().send(sender, "editor.noPermission", null);
            return true;
        }
        if (args.length == 0) {
            plugin.messages().send(sender, "command.set-max-players-command.setMaxPlayersUsage", null);
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[0]);
        } catch (NumberFormatException ex) {
            plugin.messages().send(sender, "command.set-max-players-command.setMaxPlayersUsage", null);
            return true;
        }
        if (amount < 1) {
            plugin.messages().send(sender, "command.set-max-players-command.setMaxPlayersUsage", null);
            return true;
        }
        if (!NmsBridge.setMaxPlayers(amount)) {
            plugin.messages().send(sender, "command.set-max-players-command.failed", null);
            return true;
        }
        plugin.forcedSlots(amount);
        Map<String, String> values = map();
        values.put("amount", Integer.toString(amount));
        plugin.messages().send(sender, "command.set-max-players-command.setMaxPlayers", values);
        return true;
    }

    private boolean knockback(CommandSender sender, String[] args) {
        if (!plugin.engine().cmdKnockback || !allowed(sender, "aether.knockback")) {
            plugin.messages().send(sender, "editor.noPermission", null);
            return true;
        }
        if (args.length == 0) {
            usage(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("list".equals(sub)) {
            return list(sender);
        }
        if ("create".equals(sub)) {
            return create(sender, args);
        }
        if ("delete".equals(sub)) {
            return delete(sender, args);
        }
        if ("setactive".equals(sub)) {
            return setActive(sender, args);
        }
        if ("settype".equals(sub)) {
            return setType(sender, args);
        }
        if ("view".equals(sub)) {
            return view(sender, args);
        }
        if ("editmenu".equals(sub)) {
            return editMenu(sender, args);
        }
        if ("edit".equals(sub)) {
            return edit(sender, args);
        }
        if ("set".equals(sub)) {
            return setPlayer(sender, args);
        }
        if ("clear".equals(sub)) {
            return clearPlayer(sender, args);
        }
        usage(sender);
        return true;
    }

    private void usage(CommandSender sender) {
        String[][] rows = {
                {"command.knockback.listUsage", "command.knockback.listHover", "/knockback list"},
                {"command.knockback.createUsage", "command.knockback.createHover", "/knockback create "},
                {"command.knockback.deleteUsage", "command.knockback.deleteHover", "/knockback delete "},
                {"command.knockback.setActiveUsage", "command.knockback.setActiveHover", "/knockback setactive "},
                {"command.knockback.setTypeUsage", "command.knockback.setTypeHover", "/knockback settype "},
                {"command.knockback.viewUsage", "command.knockback.viewHover", "/knockback view "},
                {"command.knockback.editUsage", "command.knockback.editHover", "/knockback edit "},
                {"command.knockback.editMenuUsage", "command.knockback.editMenuHover", "/knockback editmenu "},
                {"command.knockback.setUsage", "command.knockback.setHover", "/knockback set "},
                {"command.knockback.clearUsage", "command.knockback.clearHover", "/knockback clear "}
        };
        List<String> lines = plugin.messages().lines("command.knockback.knockbackUsage");
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("%commands%")) {
                for (int r = 0; r < rows.length; r++) {
                    click(sender, plugin.messages().one(rows[r][0], null), plugin.messages().one(rows[r][1], null), rows[r][2], false);
                }
            } else {
                sender.sendMessage(Colors.color(line));
            }
        }
    }

    private boolean list(CommandSender sender) {
        List<String> lines = plugin.messages().lines("command.knockback.listMessage");
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).contains("%profiles%")) {
                sender.sendMessage(Colors.color(lines.get(i)));
                continue;
            }
            for (KnockbackProfile profile : plugin.engine().profileList()) {
                boolean active = profile.name.equalsIgnoreCase(plugin.engine().activeKnockback);
                Map<String, String> values = map();
                values.put("name", profile.name);
                String text = plugin.messages().one(active ? "command.knockback.listNameActive" : "command.knockback.listName", values);
                String hover = plugin.messages().one("command.knockback.listNameHover", values);
                click(sender, text, hover, "/knockback view " + profile.name, true);
            }
        }
        return true;
    }

    private boolean create(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "command.knockback.createUsage", null);
            return true;
        }
        String name = args[1].replaceAll("[^A-Za-z0-9_\\-]", "");
        if (name.isEmpty()) {
            plugin.messages().send(sender, "command.knockback.createUsage", null);
            return true;
        }
        if (!plugin.store().createProfile(name)) {
            Map<String, String> values = map();
            values.put("name", name);
            plugin.messages().send(sender, "command.knockback.createExists", values);
            return true;
        }
        plugin.reloadAll();
        Map<String, String> values = map();
        values.put("name", name);
        plugin.messages().send(sender, "command.knockback.createProfile", values);
        return true;
    }

    private boolean delete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "command.knockback.deleteUsage", null);
            return true;
        }
        KnockbackProfile profile = plugin.engine().profile(args[1]);
        if (profile == null) {
            missing(sender, args[1]);
            return true;
        }
        if (profile.name.equalsIgnoreCase(plugin.engine().activeKnockback)) {
            Map<String, String> values = map();
            values.put("name", profile.name);
            plugin.messages().send(sender, "command.knockback.deleteActive", values);
            return true;
        }
        if ("default".equalsIgnoreCase(profile.name)) {
            Map<String, String> values = map();
            values.put("name", profile.name);
            plugin.messages().send(sender, "command.knockback.deleteProtected", values);
            return true;
        }
        plugin.store().deleteProfile(profile.name);
        plugin.reloadAll();
        Map<String, String> values = map();
        values.put("name", profile.name);
        plugin.messages().send(sender, "command.knockback.deleteProfile", values);
        return true;
    }

    private boolean setActive(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "command.knockback.setActiveUsage", null);
            return true;
        }
        KnockbackProfile profile = plugin.engine().profile(args[1]);
        if (profile == null) {
            missing(sender, args[1]);
            return true;
        }
        if (profile.name.equalsIgnoreCase(plugin.engine().activeKnockback)) {
            Map<String, String> values = map();
            values.put("name", profile.name);
            plugin.messages().send(sender, "command.knockback.setActiveAlready", values);
            return true;
        }
        plugin.update("aether.yml", "knockback.globalActiveKnockback", profile.name);
        Map<String, String> values = map();
        values.put("name", profile.name);
        plugin.messages().send(sender, "command.knockback.setActive", values);
        return true;
    }

    private boolean setType(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.messages().send(sender, "command.knockback.setTypeUsage", null);
            return true;
        }
        KnockbackProfile profile = plugin.engine().profile(args[1]);
        if (profile == null) {
            missing(sender, args[1]);
            return true;
        }
        String type = args[2].toUpperCase(Locale.ROOT);
        if (!"SIMPLE".equals(type) && !"ADVANCED".equals(type)) {
            plugin.messages().send(sender, "command.knockback.setTypeNotFound", null);
            return true;
        }
        plugin.update("knockback/" + profile.name + ".yml", "knockbackType", type);
        Map<String, String> values = map();
        values.put("name", profile.name);
        values.put("type", type);
        plugin.messages().send(sender, "command.knockback.setType", values);
        return true;
    }

    private boolean view(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "command.knockback.viewUsage", null);
            return true;
        }
        KnockbackProfile profile = plugin.engine().profile(args[1]);
        if (profile == null) {
            missing(sender, args[1]);
            return true;
        }
        YamlDoc doc = plugin.store().doc("knockback/" + profile.name + ".yml");
        List<String> lines = plugin.messages().lines("command.knockback.viewMessage");
        Map<String, String> header = map();
        header.put("name", profile.name);
        header.put("type", profile.type);
        for (int i = 0; i < lines.size(); i++) {
            String line = Texts.apply(lines.get(i), header);
            if (line.contains("%values%")) {
                if (doc != null) {
                    List<String> keys = doc.childKeys("");
                    for (int k = 0; k < keys.size(); k++) {
                        YamlDoc.Node node = doc.at(keys.get(k));
                        if (node == null || node.kind != YamlDoc.Kind.SCALAR) {
                            continue;
                        }
                        Map<String, String> values = map();
                        values.put("setting", keys.get(k));
                        values.put("value", doc.text(keys.get(k), ""));
                        values.put("name", profile.name);
                        click(sender, plugin.messages().one("command.knockback.viewSetting", values),
                                plugin.messages().one("command.knockback.viewSettingHover", values),
                                "/knockback edit " + profile.name + " " + keys.get(k) + " ", false);
                    }
                }
            } else if (line.contains("%clickables%")) {
                Map<String, String> values = map();
                values.put("name", profile.name);
                click(sender, plugin.messages().one("command.knockback.viewSetActiveClick", values),
                        plugin.messages().one("command.knockback.viewSetActiveClickHover", values),
                        "/knockback setactive " + profile.name, true);
                click(sender, plugin.messages().one("command.knockback.viewEditClick", values),
                        plugin.messages().one("command.knockback.viewEditHover", values),
                        "/knockback editmenu " + profile.name, true);
                click(sender, plugin.messages().one("command.knockback.viewDeleteClick", values),
                        plugin.messages().one("command.knockback.viewDeleteClickHover", values),
                        "/knockback delete " + profile.name, true);
            } else {
                sender.sendMessage(Colors.color(line));
            }
        }
        return true;
    }

    private boolean editMenu(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.messages().send(sender, "editor.onlyPlayer", null);
            return true;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "command.knockback.editMenuUsage", null);
            return true;
        }
        KnockbackProfile profile = plugin.engine().profile(args[1]);
        if (profile == null) {
            missing(sender, args[1]);
            return true;
        }
        plugin.menus().openFile((Player) sender, "knockback/" + profile.name + ".yml", "", 0);
        return true;
    }

    private boolean edit(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.messages().send(sender, "command.knockback.editUsage", null);
            return true;
        }
        KnockbackProfile profile = plugin.engine().profile(args[1]);
        if (profile == null) {
            missing(sender, args[1]);
            return true;
        }
        String file = "knockback/" + profile.name + ".yml";
        YamlDoc doc = plugin.store().doc(file);
        if (doc == null || doc.at(args[2]) == null || doc.at(args[2]).kind != YamlDoc.Kind.SCALAR) {
            Map<String, String> values = map();
            values.put("setting", args[2]);
            plugin.messages().send(sender, "command.knockback.editSettingNotFound", values);
            return true;
        }
        if (args.length == 3) {
            if (!(sender instanceof Player)) {
                plugin.messages().send(sender, "command.knockback.editUsage", null);
                return true;
            }
            AetherCore.EditSession session = new AetherCore.EditSession();
            session.mode = "value";
            session.file = file;
            session.path = args[2];
            session.returnKind = "file";
            session.returnFile = file;
            session.returnPath = "";
            plugin.edits().put(((Player) sender).getUniqueId(), session);
            Map<String, String> values = map();
            values.put("setting", args[2]);
            values.put("current", doc.text(args[2], ""));
            plugin.messages().send(sender, "command.knockback.editTypeNewValue", values);
            return true;
        }
        StringBuilder joined = new StringBuilder();
        for (int i = 3; i < args.length; i++) {
            if (i > 3) {
                joined.append(' ');
            }
            joined.append(args[i]);
        }
        Object value = coerce(doc.text(args[2], ""), joined.toString());
        plugin.update(file, args[2], value);
        Map<String, String> values = map();
        values.put("setting", args[2]);
        values.put("value", String.valueOf(value));
        values.put("profile", profile.name);
        plugin.messages().send(sender, "command.knockback.editSettingValue", values);
        return true;
    }

    private boolean setPlayer(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.messages().send(sender, "command.knockback.setUsage", null);
            return true;
        }
        KnockbackProfile profile = plugin.engine().profile(args[1]);
        Player target = Online.byName(args[2]);
        if (profile == null) {
            missing(sender, args[1]);
            return true;
        }
        if (target == null) {
            Map<String, String> values = map();
            values.put("player", args[2]);
            plugin.messages().send(sender, "player.playerNotFound", values);
            return true;
        }
        plugin.personalProfile(target, profile.name);
        Map<String, String> values = map();
        values.put("profile", profile.name);
        values.put("player", target.getName());
        plugin.messages().send(sender, "command.knockback.setProfile", values);
        return true;
    }

    private boolean clearPlayer(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "command.knockback.clearUsage", null);
            return true;
        }
        Player target = Online.byName(args[1]);
        if (target == null) {
            Map<String, String> values = map();
            values.put("player", args[1]);
            plugin.messages().send(sender, "player.playerNotFound", values);
            return true;
        }
        plugin.personalProfile(target, null);
        Map<String, String> values = map();
        values.put("player", target.getName());
        plugin.messages().send(sender, "command.knockback.clearProfile", values);
        return true;
    }

    private void missing(CommandSender sender, String name) {
        Map<String, String> values = map();
        values.put("name", name);
        plugin.messages().send(sender, "command.knockback.profileNotFound", values);
    }

    private boolean allowed(CommandSender sender, String node) {
        if (sender.isOp() || sender.hasPermission("aether.admin") || sender.hasPermission(node)) {
            return true;
        }
        plugin.messages().send(sender, "editor.noPermission", null);
        return false;
    }

    private void click(CommandSender sender, String text, String hover, String command, boolean run) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Colors.color(text));
            return;
        }
        TextComponent component = new TextComponent(TextComponent.fromLegacyText(Colors.color(text)));
        component.setClickEvent(new ClickEvent(run ? ClickEvent.Action.RUN_COMMAND : ClickEvent.Action.SUGGEST_COMMAND, command));
        component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(Colors.color(hover)).create()));
        ((Player) sender).spigot().sendMessage(component);
    }

    private static Object coerce(String sample, String raw) {
        String text = raw.trim();
        if ("true".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text)) {
            return Boolean.FALSE;
        }
        try {
            double value = Double.parseDouble(text);
            boolean whole = sample.indexOf('.') < 0 && sample.toLowerCase(Locale.ROOT).indexOf('e') < 0;
            if (whole) {
                return Integer.valueOf((int) Math.round(value));
            }
            return Double.valueOf(value);
        } catch (NumberFormatException ex) {
            return text;
        }
    }

    private String uptime() {
        long seconds = Math.max(0L, (System.currentTimeMillis() - plugin.startedAt()) / 1000L);
        long minutes = seconds / 60L;
        long hours = minutes / 60L;
        long days = hours / 24L;
        return days + "j " + (hours % 24L) + "h " + (minutes % 60L) + "m";
    }

    private static Map<String, String> map() {
        return new LinkedHashMap<String, String>();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if ("knockback".equals(name)) {
            if (args.length == 1) {
                return filter(args[0], Arrays.asList("list", "create", "delete", "setactive", "settype", "view", "edit", "editmenu", "set", "clear"));
            }
            if (args.length == 2 && !"create".equalsIgnoreCase(args[0]) && !"clear".equalsIgnoreCase(args[0])) {
                List<String> names = new ArrayList<String>();
                for (KnockbackProfile profile : plugin.engine().profileList()) {
                    names.add(profile.name);
                }
                if ("set".equalsIgnoreCase(args[0])) {
                    return filter(args[1], names);
                }
                return filter(args[1], names);
            }
            if (args.length == 2 && "clear".equalsIgnoreCase(args[0])) {
                return playerNames(args[1]);
            }
            if (args.length == 3 && "set".equalsIgnoreCase(args[0])) {
                return playerNames(args[2]);
            }
            if (args.length == 3 && "settype".equalsIgnoreCase(args[0])) {
                return filter(args[2], Arrays.asList("SIMPLE", "ADVANCED"));
            }
        }
        if (("hide".equals(name) || "see".equals(name)) && (args.length == 1 || args.length == 2)) {
            return playerNames(args[args.length - 1]);
        }
        if (("ping".equals(name) || "aether".equals(name) || "config".equals(name)) && args.length == 1) {
            if ("ping".equals(name)) {
                return playerNames(args[0]);
            }
            return filter(args[0], Arrays.asList("menu", "reload", "version"));
        }
        return Collections.emptyList();
    }

    private static List<String> playerNames(String prefix) {
        List<String> names = new ArrayList<String>();
        for (Player player : Online.players()) {
            names.add(player.getName());
        }
        return filter(prefix, names);
    }

    private static List<String> filter(String prefix, List<String> options) {
        String needle = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> found = new ArrayList<String>();
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).toLowerCase(Locale.ROOT).startsWith(needle)) {
                found.add(options.get(i));
            }
        }
        return found;
    }
}
