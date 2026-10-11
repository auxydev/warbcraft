package dev.auxy.warbcraft.plugin.gui;

import dev.auxy.warbcraft.core.faction.Clan;
import dev.auxy.warbcraft.core.faction.Settlement;
import dev.auxy.warbcraft.core.party.Party;
import dev.auxy.warbcraft.core.season.Season;
import dev.auxy.warbcraft.plugin.Game;
import dev.auxy.warbcraft.plugin.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Sefer Defteri: tum sistemlere giris. */
final class MainMenu extends Menu {

    MainMenu(Menus menus, Player viewer) {
        super(menus, viewer, 3, "<dark_gray>Sefer Defteri");
    }

    @Override
    protected void render() {
        Game game = menus.game();
        Party party = game.profile(viewer).party();
        Optional<Settlement> here = game.settlementAt(viewer.getLocation());
        Optional<Clan> clan = game.clanOf(viewer.getUniqueId());

        List<String> status = new ArrayList<>();
        status.add("Kese: <yellow>" + game.purse(viewer).balance() + " dinar");
        status.add("Klan: <white>" + clan.map(c -> Msg.esc(c.name()) + " (kademe " + c.tier() + ")").orElse("yok"));
        status.add("Konum: <white>" + here.map(Labels::settlement).orElse("yaban"));
        status.add("Ordu: <white>" + party.totalCount() + "/" + party.sizeLimit() + "</white> · moral " + party.morale());
        set(4, Icons.head(viewer, "<gold>" + Msg.esc(viewer.getName()), status));

        set(10, Icons.icon(Material.IRON_SWORD, "<gold>Ordu",
                party.totalCount() + " asker · sahada " + menus.retinue().fieldCount(viewer),
                "Birlikler, terfi, terhis ve emirler.", "", "<yellow>Tıkla: aç"), () -> open(new PartyMenu(menus, viewer)));
        set(11, Icons.icon(Material.LEATHER_HELMET, "<gold>Asker Topla",
                here.map(s -> "Burası: " + Labels.settlement(s)).orElse("<red>Bir köy ya da şehirde olmalısın."),
                "", "<yellow>Tıkla: aç"), () -> open(new RecruitMenu(menus, viewer)));
        set(12, Icons.icon(Material.EMERALD, "<gold>Pazar",
                here.filter(s -> game.markets().containsKey(s.id())).map(s -> Msg.esc(s.name()) + " pazarı")
                        .orElse("<red>Pazar yalnızca şehirlerde kurulur."),
                "", "<yellow>Tıkla: aç"), () -> open(new MarketMenu(menus, viewer)));
        set(13, Icons.icon(Material.WRITABLE_BOOK, "<gold>Görevler",
                game.quests().activeFor(viewer.getUniqueId().toString()).size() + " aktif görev",
                game.kingAt(viewer.getLocation()).map(k -> "Burada " + Msg.esc(k.name()) + " görev veriyor.")
                        .orElse("Görev panosu krallık başkentlerinde."),
                "", "<yellow>Tıkla: aç"), () -> open(new QuestMenu(menus, viewer)));
        set(14, Icons.icon(Material.SHIELD, "<gold>Klan",
                clan.map(c -> Msg.esc(c.name()) + " · ün " + c.renown()).orElse("Bir klanın yok."),
                "", "<yellow>Tıkla: aç"), () -> open(new ClanMenu(menus, viewer)));
        set(15, Icons.icon(Material.GOLDEN_HELMET, "<gold>Krallıklar",
                game.realm().kingdoms().size() + " krallık", "Diplomasi, vasallık, savaş ve barış.",
                "", "<yellow>Tıkla: aç"), () -> open(new KingdomListMenu(menus, viewer)));

        Season season = game.season();
        set(16, Icons.icon(Material.CLOCK, "<gold>Sezon " + season.number(),
                "Evre: <white>" + season.phaseAt(game.now()).displayName(),
                season.isWarWindowOpen(game.now()) ? "<red>Savaş penceresi AÇIK" : "Savaş penceresi kapalı",
                "", "<yellow>Tıkla: sıralamayı sohbete yaz"), () -> run("sezon"));
        set(22, Icons.icon(Material.GOLD_INGOT, "<gold>Kese",
                game.purse(viewer).balance() + " dinar", "", "<yellow>Tıkla: bir oyuncuya dinar gönder"),
                () -> open(new PlayerPickerMenu(menus, viewer, "Kime dinar göndereceksin?", this, target ->
                        menus.prompt(viewer, "Kaç dinar göndereceksin? <white>" + Msg.esc(target.getName()), amount ->
                                viewer.performCommand("dinar gonder " + target.getName() + " " + amount)))));
    }
}
