package dev.auxy.warbcraft.plugin.gui;

import dev.auxy.warbcraft.core.faction.Clan;
import dev.auxy.warbcraft.plugin.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Consumer;

/** Kralliga davet icin bagimsiz oyuncu klani secme. */
final class ClanPickerMenu extends Menu {

    private final Menu parent;
    private final Consumer<Clan> onPick;

    ClanPickerMenu(Menus menus, Player viewer, Menu parent, Consumer<Clan> onPick) {
        super(menus, viewer, 6, "<dark_gray>Hangi klan?");
        this.parent = parent;
        this.onPick = onPick;
    }

    @Override
    protected void render() {
        List<Clan> clans = menus.game().realm().clans().stream()
                .filter(c -> !c.npc() && c.kingdomId() == null).toList();
        int slot = 0;
        for (Clan clan : clans) {
            if (slot >= 45) {
                break;
            }
            set(slot++, Icons.icon(Material.SHIELD, "<gold>" + Msg.esc(clan.name()),
                    "Kademe <white>" + clan.tier() + "</white> · " + clan.members().size() + " üye",
                    "", "<yellow>Tıkla: davet et"), () -> {
                        onPick.accept(clan);
                        open(parent);
                    });
        }
        if (clans.isEmpty()) {
            set(22, Icons.icon(Material.BARRIER, "<red>Bağımsız oyuncu klanı yok"));
        }
        set(49, Icons.back(), () -> open(parent));
    }
}
