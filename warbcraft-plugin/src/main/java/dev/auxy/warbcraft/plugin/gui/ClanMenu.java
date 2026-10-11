package dev.auxy.warbcraft.plugin.gui;

import dev.auxy.warbcraft.core.faction.Clan;
import dev.auxy.warbcraft.core.faction.Settlement;
import dev.auxy.warbcraft.plugin.Game;
import dev.auxy.warbcraft.plugin.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Klan: kurma ve davetler (klansizken), bilgi, uyeler, davet ve ayrilma (klandayken). */
final class ClanMenu extends Menu {

    ClanMenu(Menus menus, Player viewer) {
        super(menus, viewer, 6, "<dark_gray>Klan");
    }

    @Override
    protected void render() {
        Game game = menus.game();
        set(49, Icons.back(), () -> open(new MainMenu(menus, viewer)));
        Optional<Clan> mine = game.clanOf(viewer.getUniqueId());
        if (mine.isEmpty()) {
            renderClanless(game);
        } else {
            renderClan(game, mine.get());
        }
    }

    private void renderClanless(Game game) {
        int cost = game.data().settings().clanCost();
        set(11, Icons.icon(Material.WHITE_BANNER, "<gold>Klan kur",
                "Bedel: <yellow>" + cost + " dinar",
                "Klan ün kazandıkça kademesi ve parti sınırın artar.",
                "", "<yellow>Tıkla: adını sohbete yaz"),
                () -> menus.prompt(viewer, "Klanının adını yaz:", name -> viewer.performCommand("klan kur " + name)));
        List<String> invites = new ArrayList<>(menus.clans().invitesFor(viewer.getUniqueId()));
        set(15, Icons.icon(Material.PAPER, "<gold>Davetlerin",
                invites.isEmpty() ? "Bekleyen davetin yok." : invites.size() + " davet; aşağıda.",
                "Bir klan lideri seni davet edebilir."));
        int slot = 27;
        for (String clanId : invites) {
            if (slot > 44) {
                break;
            }
            Optional<Clan> clan = game.realm().findClan(clanId);
            if (clan.isEmpty()) {
                continue;
            }
            set(slot++, Icons.icon(Material.SHIELD, "<gold>" + Msg.esc(clan.get().name()),
                    "Kademe <white>" + clan.get().tier() + "</white> · " + clan.get().members().size() + " üye",
                    "", "<yellow>Tıkla: katıl"), () -> run("klan katil " + clanId));
        }
    }

    private void renderClan(Game game, Clan clan) {
        boolean leader = clan.leaderId().equals(viewer.getUniqueId().toString());
        List<String> info = new ArrayList<>();
        info.add("Kademe <white>" + clan.tier() + "</white> · ün <white>" + clan.renown());
        if (clan.tier() < Clan.MAX_TIER) {
            info.add("Sonraki kademe: <white>" + Clan.renownForTier(clan.tier() + 1) + " ün");
        }
        info.add("Krallık: <white>" + (clan.kingdomId() == null ? "bağımsız"
                : Msg.esc(game.realm().kingdom(clan.kingdomId()).name())));
        List<String> fiefs = new ArrayList<>();
        for (Settlement settlement : game.realm().settlements()) {
            if (settlement.ownerClanId().equals(clan.id())) {
                fiefs.add(Msg.esc(settlement.name()));
            }
        }
        info.add("Topraklar: <white>" + (fiefs.isEmpty() ? "yok" : String.join(", ", fiefs)));
        info.add("Parti sınırı: <white>" + game.partyLimit(viewer.getUniqueId()));
        set(4, Icons.icon(Material.SHIELD, "<gold>" + Msg.esc(clan.name()), info));

        int slot = 9;
        for (String member : clan.members()) {
            if (slot > 35) {
                break;
            }
            OfflinePlayer offline = Bukkit.getOfflinePlayer(UUID.fromString(member));
            boolean isLeader = member.equals(clan.leaderId());
            set(slot++, Icons.head(offline, (isLeader ? "<gold>👑 " : "<white>") + Msg.esc(String.valueOf(offline.getName())),
                    List.of(offline.isOnline() ? "<green>çevrimiçi" : "<dark_gray>çevrimdışı")));
        }

        fillRow(4);
        if (leader) {
            set(45, Icons.icon(Material.WRITABLE_BOOK, "<gold>Üye davet et", "", "<yellow>Tıkla: oyuncu seç"),
                    () -> open(new PlayerPickerMenu(menus, viewer, "Kimi davet edeceksin?", this,
                            target -> run("klan davet " + target.getName()))));
        } else {
            set(45, Icons.icon(Material.IRON_DOOR, "<red>Klandan ayrıl", "", "<yellow>Tıkla: onayla"),
                    () -> open(new ConfirmMenu(menus, viewer, Msg.esc(clan.name()) + " klanından ayrılıyor musun?",
                            () -> run("klan ayril"), this)));
        }
        set(47, Icons.icon(Material.GOLDEN_HELMET, "<gold>Krallıklar", "Vasallık, krallık kurma, savaş ve barış.",
                "", "<yellow>Tıkla: aç"), () -> open(new KingdomListMenu(menus, viewer)));
    }
}
