package dev.auxy.warbcraft.plugin.gui;

import dev.auxy.warbcraft.plugin.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Consumer;

/** Cevrimici bir oyuncu secme (dinar gonderme, klana davet). */
final class PlayerPickerMenu extends Menu {

    private static final int MAX = 45;

    private final String question;
    private final Menu parent;
    private final Consumer<Player> onPick;

    PlayerPickerMenu(Menus menus, Player viewer, String question, Menu parent, Consumer<Player> onPick) {
        super(menus, viewer, 6, "<dark_gray>" + question);
        this.question = question;
        this.parent = parent;
        this.onPick = onPick;
    }

    @Override
    protected void render() {
        List<? extends Player> others = Bukkit.getOnlinePlayers().stream().filter(p -> !p.equals(viewer)).toList();
        int slot = 0;
        for (Player other : others) {
            if (slot >= MAX) {
                break;
            }
            set(slot++, Icons.head(other, "<white>" + Msg.esc(other.getName()), List.of("", "<yellow>Tıkla: seç")),
                    () -> onPick.accept(other));
        }
        if (others.isEmpty()) {
            set(22, Icons.icon(Material.BARRIER, "<red>Çevrimiçi başka oyuncu yok", question));
        }
        set(49, Icons.back(), () -> open(parent));
    }
}
