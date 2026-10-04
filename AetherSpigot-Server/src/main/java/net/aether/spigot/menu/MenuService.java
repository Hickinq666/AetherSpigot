package net.aether.spigot.menu;

import net.aether.spigot.AetherCore;
import net.aether.spigot.config.Engine;
import net.aether.spigot.config.YamlDoc;
import net.aether.spigot.knockback.KnockbackProfile;
import net.aether.spigot.runtime.Online;
import net.aether.spigot.runtime.SupportMatrix;
import net.aether.spigot.text.Colors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class MenuService {

    public static final int PAGE = 45;

    private final AetherCore plugin;

    public MenuService(AetherCore plugin) {
        this.plugin = plugin;
    }

    public void openMain(Player player) {
        YamlDoc menus = plugin.store().doc("menus.yml");
        String title = menus == null ? "&5Aether" : menus.text("main.title", "&5Aether");
        int size = menus == null ? 54 : menus.integer("main.size", 54);
        if (size < 9 || size > 54 || size % 9 != 0) {
            size = 54;
        }
        MenuHolder holder = new MenuHolder("main", "menus.yml", "", 0);
        Inventory inventory = Bukkit.createInventory(holder, size, clip(title));
        holder.attach(inventory);
        Engine engine = plugin.engine();
        if (engine.fillerEnabled) {
            ItemStack filler = item(material(engine.fillerType, Material.STAINED_GLASS_PANE), (short) engine.fillerData, " ", new ArrayList<String>());
            for (int slot = 0; slot < size; slot++) {
                inventory.setItem(slot, filler);
            }
        }
        if (menus != null) {
            YamlDoc.Node items = menus.at("main.items");
            if (items != null) {
                for (int i = 0; i < items.children.size(); i++) {
                    YamlDoc.Node node = items.children.get(i);
                    int slot = childInt(node, "slot", -1);
                    if (slot < 0 || slot >= size) {
                        continue;
                    }
                    Material type = material(childText(node, "material", "STONE"), Material.STONE);
                    short data = (short) childInt(node, "data", 0);
                    List<String> lore = childList(node, "lore");
                    inventory.setItem(slot, item(type, data, childText(node, "name", node.key), lore));
                }
            }
        }
        player.openInventory(inventory);
        play(player, "sound-open");
    }

    public void openFile(Player player, String file, String path, int page) {
        YamlDoc doc = plugin.store().doc(file);
        if (doc == null) {
            return;
        }
        List<String> keys = doc.childKeys(path);
        int pages = Math.max(1, (keys.size() + PAGE - 1) / PAGE);
        int safe = Math.max(0, Math.min(page, pages - 1));
        String name = path == null || path.isEmpty() ? file : path.substring(path.lastIndexOf('.') + 1);
        MenuHolder holder = new MenuHolder("file", file, path, safe);
        Inventory inventory = Bukkit.createInventory(holder, 54, clip("&5" + name));
        holder.attach(inventory);
        paint(inventory);
        int start = safe * PAGE;
        for (int i = 0; i < PAGE && start + i < keys.size(); i++) {
            inventory.setItem(i, nodeItem(doc, file, path, keys.get(start + i)));
        }
        navigation(inventory, safe, pages, true);
        player.openInventory(inventory);
        play(player, "sound-open");
    }

    public void openList(Player player, String file, String path, int page) {
        YamlDoc doc = plugin.store().doc(file);
        if (doc == null) {
            return;
        }
        List<String> values = doc.list(path);
        int pages = Math.max(1, (values.size() + PAGE - 1) / PAGE);
        int safe = Math.max(0, Math.min(page, pages - 1));
        MenuHolder holder = new MenuHolder("list", file, path, safe);
        Inventory inventory = Bukkit.createInventory(holder, 54, clip("&5Liste"));
        holder.attach(inventory);
        paint(inventory);
        int start = safe * PAGE;
        for (int i = 0; i < PAGE && start + i < values.size(); i++) {
            List<String> lore = new ArrayList<String>();
            lore.add("&7Shift + clic: retirer");
            lore.add("&eClic droit: modifier");
            inventory.setItem(i, item(Material.PAPER, (short) 0, "&f" + values.get(start + i), lore));
        }
        navigation(inventory, safe, pages, true);
        inventory.setItem(50, item(Material.NAME_TAG, (short) 0, "&aAjouter", new ArrayList<String>()));
        player.openInventory(inventory);
    }

    public void openKnockback(Player player, int page) {
        List<KnockbackProfile> profiles = plugin.engine().profileList();
        int pages = Math.max(1, (profiles.size() + PAGE - 1) / PAGE);
        int safe = Math.max(0, Math.min(page, pages - 1));
        MenuHolder holder = new MenuHolder("knockback", "", "", safe);
        Inventory inventory = Bukkit.createInventory(holder, 54, clip("&5Knockback"));
        holder.attach(inventory);
        paint(inventory);
        int start = safe * PAGE;
        for (int i = 0; i < PAGE && start + i < profiles.size(); i++) {
            KnockbackProfile profile = profiles.get(start + i);
            boolean active = profile.name.equalsIgnoreCase(plugin.engine().activeKnockback);
            List<String> lore = new ArrayList<String>();
            lore.add("&7Type: &f" + profile.type);
            lore.add(active ? "&aProfil global" : "&7Clic pour editer");
            lore.add("&eShift: activer");
            inventory.setItem(i, item(active ? Material.DIAMOND_SWORD : Material.IRON_SWORD, (short) 0, "&f" + profile.name, lore));
        }
        navigation(inventory, safe, pages, true);
        inventory.setItem(47, item(Material.NETHER_STAR, (short) 0, "&aCreer", new ArrayList<String>()));
        player.openInventory(inventory);
    }

    public void openPlayers(Player player, int page) {
        List<org.bukkit.entity.Player> players = Online.players();
        int pages = Math.max(1, (players.size() + PAGE - 1) / PAGE);
        int safe = Math.max(0, Math.min(page, pages - 1));
        MenuHolder holder = new MenuHolder("players", "", "", safe);
        Inventory inventory = Bukkit.createInventory(holder, 54, clip("&5Joueurs"));
        holder.attach(inventory);
        paint(inventory);
        int start = safe * PAGE;
        for (int i = 0; i < PAGE && start + i < players.size(); i++) {
            org.bukkit.entity.Player target = players.get(start + i);
            String personal = plugin.personalProfile(target);
            List<String> lore = new ArrayList<String>();
            lore.add("&7Profil: &f" + (personal == null ? plugin.engine().activeKnockback + " (global)" : personal));
            lore.add("&eClic: profil suivant");
            lore.add("&eShift: retirer");
            inventory.setItem(i, item(Material.SKULL_ITEM, (short) 3, "&f" + target.getName(), lore));
        }
        navigation(inventory, safe, pages, true);
        player.openInventory(inventory);
    }

    public String actionAt(int slot) {
        YamlDoc menus = plugin.store().doc("menus.yml");
        if (menus == null) {
            return "";
        }
        YamlDoc.Node items = menus.at("main.items");
        if (items == null) {
            return "";
        }
        for (int i = 0; i < items.children.size(); i++) {
            YamlDoc.Node node = items.children.get(i);
            if (childInt(node, "slot", -1) == slot) {
                return childText(node, "action", "");
            }
        }
        return "";
    }

    public void play(Player player, String key) {
        YamlDoc menus = plugin.store().doc("menus.yml");
        String name = menus == null ? "CLICK" : menus.text("gui." + key, "CLICK");
        try {
            player.playSound(player.getLocation(), Sound.valueOf(name), 0.6F, 1.2F);
        } catch (IllegalArgumentException ignored) {
            player.playSound(player.getLocation(), Sound.CLICK, 0.6F, 1.2F);
        }
    }

    private void paint(Inventory inventory) {
        Engine engine = plugin.engine();
        if (!engine.fillerEnabled) {
            return;
        }
        ItemStack filler = item(material(engine.fillerType, Material.STAINED_GLASS_PANE), (short) engine.fillerData, " ", new ArrayList<String>());
        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private void navigation(Inventory inventory, int page, int pages, boolean back) {
        Engine engine = plugin.engine();
        List<String> lore = new ArrayList<String>();
        lore.add("&7Page &f" + (page + 1) + "&7/&f" + pages);
        if (page > 0) {
            inventory.setItem(45, item(material(engine.pageType, Material.CARPET), (short) engine.pageData, engine.pageBack, lore));
        }
        if (back) {
            inventory.setItem(49, item(material(engine.backType, Material.BED_BLOCK), (short) engine.backData, engine.backDisplay, new ArrayList<String>()));
        }
        if (page + 1 < pages) {
            inventory.setItem(53, item(material(engine.pageType, Material.CARPET), (short) engine.pageData, engine.pageForward, lore));
        }
    }

    private ItemStack nodeItem(YamlDoc doc, String file, String path, String key) {
        String child = path == null || path.isEmpty() ? key : path + "." + key;
        YamlDoc.Node node = doc.at(child);
        Material material = Material.PAPER;
        short data = 0;
        String value = "";
        if (node != null && node.kind == YamlDoc.Kind.MAP) {
            material = Material.CHEST;
            value = node.children.size() + " entrees";
        } else if (node != null && node.kind == YamlDoc.Kind.LIST) {
            material = Material.HOPPER;
            value = doc.list(child).size() + " valeurs";
        } else {
            value = doc.text(child, "");
            if ("true".equalsIgnoreCase(value)) {
                material = Material.INK_SACK;
                data = 10;
            } else if ("false".equalsIgnoreCase(value)) {
                material = Material.INK_SACK;
                data = 1;
            }
        }
        List<String> lore = new ArrayList<String>();
        lore.add("&7Valeur: &f" + trim(value));
        String inline = doc.inline(child);
        if (inline != null && !inline.isEmpty()) {
            lore.add("&8" + trim(inline));
        }
        lore.add(levelLine(SupportMatrix.of(file, child)));
        lore.add("&eGauche: changer");
        lore.add("&eDroit: ecrire");
        lore.add("&eShift: diminuer");
        return item(material, data, "&f" + key, lore);
    }

    private static String levelLine(SupportMatrix.Level level) {
        if (level == SupportMatrix.Level.LIVE) {
            return "&aActif en jeu";
        }
        if (level == SupportMatrix.Level.PARTIAL) {
            return "&eApplique en partie";
        }
        return "&cEnregistre pour le jar du serveur";
    }

    private static String trim(String value) {
        if (value == null) {
            return "";
        }
        if (value.length() > 42) {
            return value.substring(0, 42);
        }
        return value;
    }

    static ItemStack item(Material material, short data, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material == null ? Material.STONE : material, 1, data);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Colors.color(name));
            if (lore != null && !lore.isEmpty()) {
                List<String> colored = new ArrayList<String>();
                for (int i = 0; i < lore.size(); i++) {
                    colored.add(Colors.color(lore.get(i)));
                }
                meta.setLore(colored);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    static Material material(String name, Material fallback) {
        if (name == null) {
            return fallback;
        }
        String normalized = name.trim().toUpperCase();
        if ("BED".equals(normalized)) {
            normalized = "BED_BLOCK";
        }
        Material material = Material.getMaterial(normalized);
        return material == null ? fallback : material;
    }

    static String clip(String raw) {
        String colored = Colors.color(raw);
        if (colored.length() > 32) {
            return colored.substring(0, 32);
        }
        return colored;
    }

    private static String childText(YamlDoc.Node node, String key, String fallback) {
        YamlDoc.Node child = node.child(key);
        if (child == null || child.rendered == null) {
            return fallback;
        }
        return YamlDoc.unquote(child.rendered);
    }

    private static int childInt(YamlDoc.Node node, String key, int fallback) {
        try {
            return Integer.parseInt(childText(node, key, Integer.toString(fallback)));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static List<String> childList(YamlDoc.Node node, String key) {
        List<String> values = new ArrayList<String>();
        YamlDoc.Node child = node.child(key);
        if (child == null) {
            return values;
        }
        for (int i = 0; i < child.children.size(); i++) {
            YamlDoc.Node item = child.children.get(i);
            if (item.rendered != null) {
                values.add(YamlDoc.unquote(item.rendered));
            }
        }
        return values;
    }
}
