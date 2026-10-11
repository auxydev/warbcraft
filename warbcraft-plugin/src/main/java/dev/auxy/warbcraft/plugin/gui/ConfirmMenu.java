package dev.auxy.warbcraft.plugin.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Geri alinamayan islemler icin evet/hayir. Sonra onceki menuye doner. */
final class ConfirmMenu extends Menu {

    private final String question;
    private final Runnable onYes;
    private final Menu parent;

    ConfirmMenu(Menus menus, Player viewer, String question, Runnable onYes, Menu parent) {
        super(menus, viewer, 3, "<dark_gray>Emin misin?");
        this.question = question;
        this.onYes = onYes;
        this.parent = parent;
    }

    @Override
    protected void render() {
        set(13, Icons.icon(Material.PAPER, "<white>" + question));
        set(11, Icons.icon(Material.LIME_CONCRETE, "<green>Evet"), () -> {
            onYes.run();
            open(parent);
        });
        set(15, Icons.icon(Material.RED_CONCRETE, "<red>Vazgeç"), () -> open(parent));
    }
}
