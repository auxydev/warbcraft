package dev.auxy.warbcraft.plugin.gui;

import dev.auxy.warbcraft.plugin.Game;
import dev.auxy.warbcraft.plugin.Msg;
import org.bukkit.entity.Player;

import java.util.Map;

/** Krallik kurarken kultur secimi; sonra ad sohbetten istenir. */
final class CulturePickerMenu extends Menu {

    private final Menu parent;

    CulturePickerMenu(Menus menus, Player viewer, Menu parent) {
        super(menus, viewer, 3, "<dark_gray>Krallığının kültürü");
        this.parent = parent;
    }

    @Override
    protected void render() {
        Game game = menus.game();
        int slot = 10;
        for (Map.Entry<String, String> culture : game.data().cultureNames().entrySet()) {
            if (game.tree().recruits(culture.getKey()).isEmpty()) {
                continue;
            }
            String id = culture.getKey();
            set(slot++, Icons.icon(Labels.cultureBanner(id), "<gold>" + Msg.esc(culture.getValue()),
                    "Toprağındaki köylerden bu kültürün", "askerleri toplanır.", "", "<yellow>Tıkla: seç ve adını yaz"),
                    () -> menus.prompt(viewer, "Krallığının adını yaz:", name -> viewer.performCommand("krallik kur " + id + " " + name)));
        }
        set(22, Icons.back(), () -> open(parent));
    }
}
