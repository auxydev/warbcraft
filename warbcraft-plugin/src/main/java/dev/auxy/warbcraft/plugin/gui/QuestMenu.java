package dev.auxy.warbcraft.plugin.gui;

import dev.auxy.warbcraft.core.npc.NpcKing;
import dev.auxy.warbcraft.core.quest.Quest;
import dev.auxy.warbcraft.core.quest.QuestOffer;
import dev.auxy.warbcraft.core.quest.QuestService;
import dev.auxy.warbcraft.core.quest.QuestType;
import dev.auxy.warbcraft.plugin.Game;
import dev.auxy.warbcraft.plugin.Msg;
import dev.auxy.warbcraft.plugin.command.QuestCommand;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Ust yari: bulunulan baskentteki kralin panosu. Alt yari: aktif gorevler. */
final class QuestMenu extends Menu {

    QuestMenu(Menus menus, Player viewer) {
        super(menus, viewer, 6, "<dark_gray>Görevler");
    }

    @Override
    protected void render() {
        Game game = menus.game();
        String me = viewer.getUniqueId().toString();
        Optional<NpcKing> king = game.kingAt(viewer.getLocation());
        if (king.isPresent()) {
            set(4, Icons.icon(Material.GOLDEN_HELMET, "<gold>" + Msg.esc(king.get().name()) + " görev veriyor",
                    "İlişkin: <white>" + game.relations().get(me, king.get().id()),
                    "İlişki görevlerle artar; yeterince yükselince", "sancağı altına girebilirsin."));
            int slot = 9;
            for (QuestOffer offer : game.quests().board(king.get().id())) {
                if (slot > 17) {
                    break;
                }
                set(slot++, Icons.icon(Material.PAPER, QuestCommand.describe(game, offer),
                        "Ödül: <yellow>" + offer.rewardDenars() + " dinar</yellow>, +" + offer.rewardRelation()
                                + " ilişki, +" + offer.rewardRenown() + " ün",
                        "Süre: <white>" + offer.timeLimit().toHours() + " saat",
                        "", "<yellow>Tıkla: görevi al"), () -> run("gorev al " + offer.id()));
            }
        } else {
            String capitals = String.join(", ", game.data().kings().values().stream()
                    .map(k -> game.realm().findSettlement(k.seatId()).map(s -> Msg.esc(s.name())).orElse(k.seatId()))
                    .toList());
            set(4, Icons.icon(Material.MAP, "<gray>Burada görev veren kral yok",
                    "Görev panoları krallık başkentlerindedir:", "<white>" + capitals));
        }

        fillRow(3);
        set(31, Icons.icon(Material.BOOK, "<gold>Aktif görevlerin"));
        List<Quest> active = game.quests().activeFor(me);
        int slot = 36;
        for (Quest quest : active) {
            if (slot > 44) {
                break;
            }
            long hours = Math.max(0, Duration.between(game.now(), quest.deadline()).toHours());
            String kingName = Optional.ofNullable(game.data().kings().get(quest.offer().kingId()))
                    .map(k -> Msg.esc(k.name())).orElse("?");
            boolean delivery = quest.offer().type() == QuestType.DELIVER_GOODS
                    || quest.offer().type() == QuestType.RAISE_TROOPS;
            List<String> lore = new ArrayList<>();
            lore.add("İlerleme: <white>" + quest.progress() + "/" + quest.offer().required());
            lore.add("Kalan süre: <white>" + hours + " saat");
            lore.add("Veren: <white>" + kingName);
            lore.add("");
            if (delivery) {
                lore.add("<yellow>Sol tık: teslim et (kralın başkentinde)");
            } else {
                lore.add("<dark_gray>Sahada tamamlanır.");
            }
            lore.add("<red>Shift+Sağ: bırak (-" + QuestService.FAILURE_RELATION_PENALTY + " ilişki)");
            set(slot++, Icons.icon(Material.FILLED_MAP, QuestCommand.describe(game, quest.offer()), lore), click -> {
                if (click == ClickType.SHIFT_RIGHT) {
                    open(new ConfirmMenu(menus, viewer, "Görev bırakılsın mı? İlişkin düşer.",
                            () -> run("gorev birak " + quest.offer().id()), this));
                } else if (click.isLeftClick() && delivery) {
                    run("gorev teslim " + quest.offer().id());
                }
            });
        }
        if (active.isEmpty()) {
            set(40, Icons.icon(Material.GRAY_DYE, "<gray>Aktif görevin yok"));
        }
        set(49, Icons.back(), () -> open(new MainMenu(menus, viewer)));
    }
}
