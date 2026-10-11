package dev.auxy.warbcraft.plugin.gui;

import dev.auxy.warbcraft.core.faction.Settlement;
import dev.auxy.warbcraft.core.faction.SettlementType;
import dev.auxy.warbcraft.core.party.Party;
import dev.auxy.warbcraft.core.troop.TroopType;
import dev.auxy.warbcraft.plugin.Game;
import dev.auxy.warbcraft.plugin.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Optional;

/** Bulunulan koy ya da sehirden gonullu toplama. */
final class RecruitMenu extends Menu {

    RecruitMenu(Menus menus, Player viewer) {
        super(menus, viewer, 3, "<dark_gray>Asker Topla");
    }

    @Override
    protected void render() {
        Game game = menus.game();
        set(22, Icons.back(), () -> open(new MainMenu(menus, viewer)));
        Optional<Settlement> here = game.settlementAt(viewer.getLocation());
        if (here.isEmpty() || here.get().type() == SettlementType.CASTLE) {
            set(13, Icons.icon(Material.BARRIER, "<red>Burada gönüllü yok",
                    "Asker toplamak için bir köy ya da şehre git.", "Kalelerde asker toplanmaz."));
            return;
        }
        Settlement settlement = here.get();
        Party party = game.profile(viewer).party();
        int pool = game.recruitPools().getOrDefault(settlement.id(), 0);
        int cost = game.data().settings().recruitCost();
        String recruits = String.join(", ", game.tree().recruits(settlement.culture()).stream()
                .map(TroopType::name).map(Msg::esc).toList());
        set(4, Icons.icon(Labels.typeIcon(settlement), "<gold>" + Labels.settlement(settlement),
                "Bugünkü gönüllüler: <white>" + pool,
                "Asker başına: <yellow>" + cost + " dinar",
                "Gelenler: <white>" + recruits,
                "Partinde yer: <white>" + party.freeSlots(),
                "Kese: <yellow>" + game.purse(viewer).balance() + " dinar"));
        int[] amounts = {1, 5, 10};
        int[] slots = {11, 12, 13};
        for (int i = 0; i < amounts.length; i++) {
            int amount = amounts[i];
            set(slots[i], Icons.amount(Icons.icon(Material.LEATHER_HELMET, "<gold>" + amount + " gönüllü topla",
                    "Bedel: <yellow>" + (long) amount * cost + " dinar"), amount), () -> run("topla " + amount));
        }
        set(15, Icons.icon(Material.GOLDEN_HELMET, "<gold>Gelebilen herkesi topla",
                "Gönüllü, para ve parti yerine göre", "en fazla kaç kişi gelebiliyorsa."), () -> run("topla"));
    }
}
