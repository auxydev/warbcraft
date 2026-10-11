package dev.auxy.warbcraft.plugin.gui;

import dev.auxy.warbcraft.core.faction.Clan;
import dev.auxy.warbcraft.core.faction.Kingdom;
import dev.auxy.warbcraft.core.faction.Settlement;
import dev.auxy.warbcraft.core.npc.NpcKing;
import dev.auxy.warbcraft.core.party.Party;
import dev.auxy.warbcraft.plugin.Game;
import dev.auxy.warbcraft.plugin.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Optional;

/** Bir kralligin klanlari, yerlesimleri ve oyuncunun yapabilecegi diplomasi islemleri. */
final class KingdomMenu extends Menu {

    private final String kingdomId;

    KingdomMenu(Menus menus, Player viewer, String kingdomId) {
        super(menus, viewer, 6, "<dark_gray>Krallık");
        this.kingdomId = kingdomId;
    }

    @Override
    protected void render() {
        Game game = menus.game();
        set(53, Icons.back(), () -> open(new KingdomListMenu(menus, viewer)));
        Optional<Kingdom> found = game.realm().findKingdom(kingdomId);
        if (found.isEmpty()) {
            set(22, Icons.icon(Material.BARRIER, "<red>Bu krallık artık yok"));
            return;
        }
        Kingdom kingdom = found.get();
        set(4, Icons.icon(Labels.cultureBanner(kingdom.culture()), "<gold>" + Msg.esc(kingdom.name()),
                KingdomListMenu.summary(game, kingdom)));

        int slot = 9;
        for (Clan clan : game.realm().clansOfKingdom(kingdom.id())) {
            if (slot > 17) {
                break;
            }
            Party army = game.lordParties().get(clan.id());
            set(slot++, Icons.icon(Material.SHIELD, "<white>" + Msg.esc(clan.name()),
                    "Kademe <white>" + clan.tier() + "</white> · ün " + clan.renown(),
                    army == null ? (clan.npc() ? "" : "<yellow>Oyuncu klanı") : "Lord ordusu: <white>" + army.totalCount()));
        }
        slot = 18;
        for (Settlement settlement : game.realm().settlementsOfKingdom(kingdom.id())) {
            if (slot > 35) {
                break;
            }
            Party garrison = game.garrisons().get(settlement.id());
            String owner = game.realm().findClan(settlement.ownerClanId()).map(c -> Msg.esc(c.name())).orElse("?");
            set(slot++, Icons.icon(Labels.typeIcon(settlement), "<white>" + Labels.settlement(settlement),
                    "Sahibi: <white>" + owner,
                    "Refah: <white>" + settlement.prosperity(),
                    garrison == null ? "" : "Garnizon: <white>" + garrison.totalCount()));
        }

        fillRow(4);
        actions(game, kingdom);
    }

    private void actions(Game game, Kingdom kingdom) {
        String me = viewer.getUniqueId().toString();
        Optional<Clan> clan = game.clanOf(viewer.getUniqueId());
        boolean leader = clan.isPresent() && clan.get().leaderId().equals(me);
        String myKingdomId = clan.map(Clan::kingdomId).orElse(null);
        boolean member = kingdom.id().equals(myKingdomId);
        Optional<Kingdom> ruled = Optional.ofNullable(myKingdomId).flatMap(game.realm()::findKingdom)
                .filter(k -> k.rulerId().equals(me));

        if (leader && myKingdomId == null) {
            String need = kingdom.npc() ? needRelation(game, kingdom) : "Hükümdarın daveti gerekir.";
            set(45, Icons.icon(Material.LIME_BANNER, "<green>Sancağına gir", need, "", "<yellow>Tıkla: katıl"),
                    () -> run("krallik katil " + kingdom.id()));
        }
        if (ruled.isPresent() && !member) {
            if (game.realm().atWar(ruled.get().id(), kingdom.id())) {
                set(46, Icons.icon(Material.WHITE_BANNER, "<green>Barış teklif et",
                        kingdom.npc() ? "NPC kral, ilişkin negatif değilse kabul eder." : "Karşı hükümdar da kabul etmeli.",
                        "", "<yellow>Tıkla: teklif et"), () -> run("krallik baris " + kingdom.id()));
            } else {
                set(46, Icons.icon(Material.RED_BANNER, "<red>Savaş ilan et",
                        "Sezonun hazırlık evresinde savaş yoktur.", "", "<yellow>Tıkla: onayla"),
                        () -> open(new ConfirmMenu(menus, viewer, Msg.esc(kingdom.name()) + " krallığına savaş ilan edilsin mi?",
                                () -> run("krallik savas " + kingdom.id()), this)));
            }
        }
        if (ruled.isPresent() && member) {
            set(47, Icons.icon(Material.WRITABLE_BOOK, "<gold>Klan davet et", "Bağımsız bir oyuncu klanını sancağına çağır.",
                    "", "<yellow>Tıkla: klan seç"), () -> open(new ClanPickerMenu(menus, viewer, this,
                            target -> run("krallik davet " + target.id()))));
        }
        if (leader && member && ruled.isEmpty()) {
            set(48, Icons.icon(Material.IRON_DOOR, "<red>Krallıktan ayrıl", "", "<yellow>Tıkla: onayla"),
                    () -> open(new ConfirmMenu(menus, viewer, Msg.esc(kingdom.name()) + " krallığından ayrılıyor musun?",
                            () -> run("krallik ayril"), this)));
        }
        if (!leader) {
            set(49, Icons.icon(Material.BOOK, "<gray>Diplomasi klan liderlerinindir",
                    clan.isEmpty() ? "Önce bir klan kur ya da bir klana katıl." : "Klan liderin karar verir."));
        }
    }

    private String needRelation(Game game, Kingdom kingdom) {
        NpcKing king = game.data().kings().get(kingdom.rulerId());
        if (king == null) {
            return "";
        }
        int relation = game.relations().get(viewer.getUniqueId().toString(), king.id());
        int needed = game.data().settings().vassalMinRelation();
        return "İlişki: " + (relation >= needed ? "<green>" : "<red>") + relation + "<gray>/" + needed
                + " (görevlerle artar)";
    }
}
