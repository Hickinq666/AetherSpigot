package net.aether.spigot.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class MenuHolder implements InventoryHolder {

    public final String kind;
    public final String file;
    public final String path;
    public final int page;
    private Inventory inventory;

    public MenuHolder(String kind, String file, String path, int page) {
        this.kind = kind;
        this.file = file == null ? "" : file;
        this.path = path == null ? "" : path;
        this.page = page;
    }

    public void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
