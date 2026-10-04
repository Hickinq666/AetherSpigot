package net.aether.spigot.enchant;

import net.aether.spigot.AetherCore;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Map;

public final class EnchantListener implements Listener {

    private final AetherCore plugin;

    public EnchantListener(AetherCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        EnchantLimits limits = plugin.engine().enchants;
        Map<Enchantment, Integer> adding = event.getEnchantsToAdd();
        for (Enchantment enchantment : new ArrayList<Enchantment>(adding.keySet())) {
            int level = adding.get(enchantment).intValue();
            int capped = limits.cap(enchantment.getName(), level);
            if (capped <= 0) {
                adding.remove(enchantment);
            } else if (capped != level) {
                adding.put(enchantment, Integer.valueOf(capped));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onAnvil(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.ANVIL) {
            return;
        }
        clamp(event.getCurrentItem());
        clamp(event.getCursor());
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreative(InventoryCreativeEvent event) {
        clamp(event.getCursor());
        clamp(event.getCurrentItem());
    }

    private void clamp(ItemStack stack) {
        if (stack == null || stack.getType().name().equals("AIR") || !stack.hasItemMeta()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasEnchants()) {
            return;
        }
        EnchantLimits limits = plugin.engine().enchants;
        boolean changed = false;
        for (Map.Entry<Enchantment, Integer> entry : new ArrayList<Map.Entry<Enchantment, Integer>>(meta.getEnchants().entrySet())) {
            int capped = limits.cap(entry.getKey().getName(), entry.getValue().intValue());
            if (capped == entry.getValue().intValue()) {
                continue;
            }
            meta.removeEnchant(entry.getKey());
            if (capped > 0) {
                meta.addEnchant(entry.getKey(), capped, true);
            }
            changed = true;
        }
        if (changed) {
            stack.setItemMeta(meta);
        }
    }
}
