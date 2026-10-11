package dev.auxy.warbcraft.plugin.gui;

import dev.auxy.warbcraft.core.economy.Good;
import dev.auxy.warbcraft.core.economy.Market;
import dev.auxy.warbcraft.core.faction.Settlement;
import dev.auxy.warbcraft.plugin.Game;
import dev.auxy.warbcraft.plugin.Msg;
import dev.auxy.warbcraft.plugin.command.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.Optional;

/** Sehir pazari: tikla al, tikla sat. Fiyatlar her islemde yeniden hesaplanir. */
final class MarketMenu extends Menu {

    private static final int[] GOOD_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final int BULK = 16;

    MarketMenu(Menus menus, Player viewer) {
        super(menus, viewer, 6, "<dark_gray>Pazar");
    }

    @Override
    protected void render() {
        Game game = menus.game();
        set(49, Icons.back(), () -> open(new MainMenu(menus, viewer)));
        Optional<Settlement> town = game.settlementAt(viewer.getLocation()).filter(s -> game.markets().containsKey(s.id()));
        if (town.isEmpty()) {
            set(22, Icons.icon(Material.BARRIER, "<red>Burada pazar yok", "Pazar yalnızca şehirlerde kurulur."));
            return;
        }
        Market market = game.markets().get(town.get().id());
        set(4, Icons.icon(Material.EMERALD, "<gold>" + Msg.esc(town.get().name()) + " pazarı",
                "Kese: <yellow>" + game.purse(viewer).balance() + " dinar",
                "Stok azaldıkça fiyat artar, çoğaldıkça düşer.",
                "Köylerin ürettiği mal şehrin pazarına gelir."));
        int index = 0;
        for (Good good : market.goods().values()) {
            if (index >= GOOD_SLOTS.length) {
                break;
            }
            Material item = game.data().goodItems().get(good.id());
            int have = Items.count(viewer, item);
            set(GOOD_SLOTS[index++], Icons.icon(item, "<gold>" + Msg.esc(good.name()),
                    "Alış: <yellow>" + market.buyPrice(good.id()) + " dinar",
                    "Satış: <green>" + market.sellPrice(good.id()) + " dinar",
                    "Stok: <white>" + market.stock(good.id()),
                    "Üzerinde: <white>" + have,
                    "",
                    "<yellow>Sol tık: 1 al · Shift+Sol: " + BULK + " al",
                    "<green>Sağ tık: 1 sat · Shift+Sağ: hepsini sat"), click -> trade(good, have, click));
        }
    }

    private void trade(Good good, int have, ClickType click) {
        switch (click) {
            case LEFT -> run("pazar al " + good.id() + " 1");
            case SHIFT_LEFT -> run("pazar al " + good.id() + " " + BULK);
            case RIGHT -> run("pazar sat " + good.id() + " 1");
            case SHIFT_RIGHT -> {
                if (have > 0) {
                    run("pazar sat " + good.id() + " " + have);
                }
            }
            default -> {
                // diger tiklamalar bos
            }
        }
    }
}
