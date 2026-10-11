package dev.auxy.warbcraft.plugin.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Sandik gorunumlu menu. Alt siniflar {@link #render()} icinde simgeleri ve tiklama islerini
 * yerlestirir; her islemden sonra menu yeniden cizilir, boylece gosterilen degerler hep guncel kalir.
 * Islemler oyun kurallarini yeniden yazmaz, mevcut komutlari cagirir (bkz. {@link Menus#run}).
 */
public abstract class Menu implements InventoryHolder {

    protected final Menus menus;
    protected final Player viewer;
    private final int rows;
    private final String title;
    private final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();
    private Inventory inventory;

    protected Menu(Menus menus, Player viewer, int rows, String title) {
        this.menus = menus;
        this.viewer = viewer;
        this.rows = rows;
        this.title = title;
    }

    @Override
    public final Inventory getInventory() {
        // Kurucuda degil burada: alt sinif tamamen kurulmadan 'this' disari verilmesin.
        if (inventory == null) {
            inventory = Bukkit.createInventory(this, rows * 9, Icons.text(title));
        }
        return inventory;
    }

    protected abstract void render();

    final void redraw() {
        getInventory().clear();
        actions.clear();
        render();
    }

    final void click(int slot, ClickType type) {
        Consumer<ClickType> action = actions.get(slot);
        if (action != null) {
            action.accept(type);
        }
    }

    protected final void set(int slot, ItemStack icon) {
        getInventory().setItem(slot, icon);
    }

    protected final void set(int slot, ItemStack icon, Consumer<ClickType> action) {
        getInventory().setItem(slot, icon);
        actions.put(slot, action);
    }

    protected final void set(int slot, ItemStack icon, Runnable action) {
        set(slot, icon, type -> action.run());
    }

    /** Bir satiri cam panoyla doldurur. */
    protected final void fillRow(int row) {
        for (int slot = row * 9; slot < row * 9 + 9; slot++) {
            set(slot, Icons.filler());
        }
    }

    /** Bu menudeyken bir komut calistirir ve menuyu yeniler. */
    protected final void run(String command) {
        menus.run(viewer, this, command);
    }

    protected final void open(Menu menu) {
        menus.open(menu);
    }

    protected final int rows() {
        return rows;
    }
}
