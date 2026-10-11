package dev.auxy.warbcraft.plugin.gui;

import dev.auxy.warbcraft.core.field.FieldFormation;
import dev.auxy.warbcraft.core.field.FieldOrder;
import dev.auxy.warbcraft.core.party.Party;
import dev.auxy.warbcraft.core.party.TroopStack;
import dev.auxy.warbcraft.core.troop.TroopType;
import dev.auxy.warbcraft.plugin.Game;
import dev.auxy.warbcraft.plugin.Msg;
import dev.auxy.warbcraft.plugin.field.Retinue;
import dev.auxy.warbcraft.plugin.field.SoldierKit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Ordu: birlik yiginlari (terfi, terhis) ve sahadaki askerlere emirler. */
final class PartyMenu extends Menu {

    private static final int TROOP_SLOTS = 36;

    PartyMenu(Menus menus, Player viewer) {
        super(menus, viewer, 6, "<dark_gray>Ordu");
    }

    @Override
    protected void render() {
        Game game = menus.game();
        Retinue retinue = menus.retinue();
        Party party = game.profile(viewer).party();

        List<Map.Entry<String, TroopStack>> stacks = new ArrayList<>(party.roster().entrySet());
        stacks.sort(Comparator.comparingInt((Map.Entry<String, TroopStack> e) -> game.tree().get(e.getKey()).tier()).reversed());
        int slot = 0;
        for (Map.Entry<String, TroopStack> entry : stacks) {
            if (slot >= TROOP_SLOTS) {
                break;
            }
            TroopType type = game.tree().get(entry.getKey());
            TroopStack stack = entry.getValue();
            set(slot++, troopIcon(game, party, type, stack), click -> onTroopClick(game, party, type, stack, click));
        }
        if (party.isEmpty()) {
            set(13, Icons.icon(Material.BARRIER, "<red>Partinde asker yok",
                    "Bir köy ya da şehirde asker toplayabilirsin.", "", "<yellow>Tıkla: asker toplama"),
                    () -> open(new RecruitMenu(menus, viewer)));
        }

        fillRow(4);
        set(45, Icons.icon(Material.BOOK, "<gold>Parti",
                "Asker: <white>" + party.totalCount() + "/" + party.sizeLimit()
                        + "</white> (" + (party.totalCount() - party.healthyCount()) + " yaralı)",
                "Moral: <white>" + party.morale(),
                "Günlük maaş: <yellow>" + party.dailyWage(game.tree()) + " dinar",
                "Sahada: <white>" + retinue.fieldCount(viewer) + "</white> / en fazla "
                        + game.data().settings().fieldCap(),
                "Kese: <yellow>" + game.purse(viewer).balance() + " dinar"));

        FieldOrder order = retinue.order(viewer);
        set(47, orderIcon(Material.LEAD, FieldOrder.FOLLOW, order, "Arkanda formasyonda yürürler."),
                () -> run("parti emir takip"));
        set(48, orderIcon(Material.OAK_FENCE, FieldOrder.HOLD, order, "Şu an durduğun yerde beklerler."),
                () -> run("parti emir bekle"));
        set(49, orderIcon(Material.GOLDEN_SWORD, FieldOrder.CHARGE, order, "Çevredeki tüm düşmanlara saldırırlar."),
                () -> run("parti emir hucum"));

        FieldFormation formation = retinue.formation(viewer);
        set(50, Icons.icon(Material.WHITE_BANNER, "<gold>Formasyon: <white>" + formation.displayName(),
                "Sıra: geniş ön cephe · Kol: dar yollar için", "Dağınık: okçulara karşı aralıklı",
                "", "<yellow>Tıkla: " + formation.next().displayName() + " yap"), () -> run("parti formasyon"));

        boolean deployed = retinue.deployed(viewer);
        set(51, Icons.icon(deployed ? Material.ARMOR_STAND : Material.BARRIER,
                deployed ? "<green>Askerler sahada" : "<red>Askerler yedekte",
                deployed ? "Arkanda en güçlü askerlerin yürüyor." : "Kimse seni takip etmiyor.",
                "", "<yellow>Tıkla: " + (deployed ? "geri çek" : "sahaya çıkar")), () -> run("parti saha"));
        set(53, Icons.back(), () -> open(new MainMenu(menus, viewer)));
    }

    private ItemStack troopIcon(Game game, Party party, TroopType type, TroopStack stack) {
        List<String> lore = new ArrayList<>();
        lore.add("Kademe <white>" + type.tier() + "</white> · " + Labels.category(type.category()));
        lore.add("Sağlam: <white>" + stack.healthy() + "</white> · yaralı: <red>" + stack.wounded());
        lore.add("Sahada: <white>" + menus.retinue().fieldCount(viewer, type.id()));
        lore.add("Günlük maaş: <yellow>" + type.wage() * stack.total() + " dinar <dark_gray>(" + type.wage() + " × " + stack.total() + ")");
        if (type.canUpgrade()) {
            int ready = party.upgradeableCount(game.tree(), type.id());
            lore.add("Deneyim: <white>" + stack.xp() + "</white> · terfiye hazır: " + (ready > 0 ? "<green>" : "<white>") + ready);
            lore.add("Terfi: <white>" + String.join(", ", type.upgradesTo().stream()
                    .map(id -> Msg.esc(game.tree().get(id).name())).toList()));
        } else {
            lore.add("<dark_gray>Son kademe");
        }
        lore.add("");
        if (type.canUpgrade()) {
            lore.add("<yellow>Sol tık: terfi et");
        }
        lore.add("<red>Sağ tık: 1 terhis · Shift+Sağ: hepsini terhis");
        return Icons.amount(Icons.icon(SoldierKit.weapon(type), Retinue.tierColor(type.tier()) + Msg.esc(type.name()), lore),
                stack.total());
    }

    private void onTroopClick(Game game, Party party, TroopType type, TroopStack stack, ClickType click) {
        switch (click) {
            case LEFT, SHIFT_LEFT -> {
                if (!type.canUpgrade()) {
                    return;
                }
                if (type.upgradesTo().size() == 1) {
                    run("parti terfi " + type.id() + " " + type.upgradesTo().getFirst());
                } else {
                    open(new UpgradeMenu(menus, viewer, type));
                }
            }
            case RIGHT -> run("parti terhis " + type.id() + " 1");
            case SHIFT_RIGHT -> open(new ConfirmMenu(menus, viewer,
                    stack.total() + " " + Msg.esc(type.name()) + " terhis edilsin mi?",
                    () -> run("parti terhis " + type.id() + " " + stack.total()), this));
            default -> {
                // diger tiklamalar bos
            }
        }
    }

    private static ItemStack orderIcon(Material material, FieldOrder order, FieldOrder current, String what) {
        boolean active = order == current;
        return Icons.glow(Icons.icon(material, (active ? "<green>" : "<gold>") + "Emir: " + order.displayName(),
                what, "", active ? "<green>Şu anki emir" : "<yellow>Tıkla: emri ver"), active);
    }
}
