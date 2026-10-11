package dev.auxy.warbcraft.plugin.gui;

import dev.auxy.warbcraft.core.troop.TroopType;
import dev.auxy.warbcraft.plugin.Game;
import dev.auxy.warbcraft.plugin.Msg;
import dev.auxy.warbcraft.plugin.field.Retinue;
import dev.auxy.warbcraft.plugin.field.SoldierKit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;

/** Iki dalli terfide hedef birligi secme. */
final class UpgradeMenu extends Menu {

    private final TroopType from;

    UpgradeMenu(Menus menus, Player viewer, TroopType from) {
        super(menus, viewer, 3, "<dark_gray>Terfi: " + Msg.esc(from.name()));
        this.from = from;
    }

    @Override
    protected void render() {
        Game game = menus.game();
        int ready = game.profile(viewer).party().upgradeableCount(game.tree(), from.id());
        List<String> targets = from.upgradesTo();
        int[] slots = targets.size() == 1 ? new int[] {13} : new int[] {11, 15, 13};
        for (int i = 0; i < targets.size() && i < slots.length; i++) {
            TroopType to = game.tree().get(targets.get(i));
            set(slots[i], Icons.icon(SoldierKit.weapon(to), Retinue.tierColor(to.tier()) + Msg.esc(to.name()),
                    "Kademe <white>" + to.tier() + "</white> · " + Labels.category(to.category()),
                    "Güç <white>" + to.power() + "</white> · maaş <yellow>" + to.wage() + " dinar",
                    "Terfiye hazır: " + (ready > 0 ? "<green>" : "<red>") + ready,
                    "",
                    "<yellow>Sol tık: hazır olanların hepsi",
                    "<yellow>Sağ tık: 1 asker"), click -> {
                        String amount = click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT ? " 1" : "";
                        run("parti terfi " + from.id() + " " + to.id() + amount);
                    });
        }
        set(22, Icons.back(), () -> open(new PartyMenu(menus, viewer)));
    }
}
