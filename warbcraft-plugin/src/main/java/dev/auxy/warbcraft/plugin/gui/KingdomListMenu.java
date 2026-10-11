package dev.auxy.warbcraft.plugin.gui;

import dev.auxy.warbcraft.core.faction.Clan;
import dev.auxy.warbcraft.core.faction.Kingdom;
import dev.auxy.warbcraft.plugin.Game;
import dev.auxy.warbcraft.plugin.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Tum kralliklar; tiklayinca ayrinti ve diplomasi. Klan lideri buradan krallik kurar. */
final class KingdomListMenu extends Menu {

    KingdomListMenu(Menus menus, Player viewer) {
        super(menus, viewer, 6, "<dark_gray>Krallıklar");
    }

    @Override
    protected void render() {
        Game game = menus.game();
        int slot = 10;
        for (Kingdom kingdom : game.kingdomsSorted()) {
            if (slot > 43) {
                break;
            }
            if (slot % 9 == 8) {
                slot += 2;
            }
            List<String> lore = summary(game, kingdom);
            lore.add("");
            lore.add("<yellow>Tıkla: ayrıntı ve diplomasi");
            set(slot++, Icons.icon(Labels.cultureBanner(kingdom.culture()), (kingdom.npc() ? "<aqua>" : "<yellow>")
                    + Msg.esc(kingdom.name()), lore), () -> open(new KingdomMenu(menus, viewer, kingdom.id())));
        }
        Optional<Clan> clan = game.clanOf(viewer.getUniqueId());
        boolean canFound = clan.isPresent() && clan.get().leaderId().equals(viewer.getUniqueId().toString())
                && clan.get().kingdomId() == null;
        set(47, Icons.icon(Material.GOLDEN_HELMET, "<gold>Krallık kur",
                "Klan lideri ve bağımsız olmalısın;",
                "klan kademen en az " + game.data().settings().kingdomMinClanTier() + " olmalı.",
                "", canFound ? "<yellow>Tıkla: kültür seç" : "<red>Şu an kuramazsın"),
                () -> open(new CulturePickerMenu(menus, viewer, this)));
        set(49, Icons.back(), () -> open(new MainMenu(menus, viewer)));
    }

    static List<String> summary(Game game, Kingdom kingdom) {
        List<String> lore = new ArrayList<>();
        String ruler = kingdom.npc()
                ? Optional.ofNullable(game.data().kings().get(kingdom.rulerId())).map(k -> Msg.esc(k.name())).orElse("?")
                : Msg.esc(String.valueOf(Bukkit.getOfflinePlayer(UUID.fromString(kingdom.rulerId())).getName()));
        lore.add("Hükümdar: <white>" + ruler);
        lore.add("Kültür: <white>" + Msg.esc(game.data().cultureNames().getOrDefault(kingdom.culture(), kingdom.culture())));
        lore.add("Yerleşim: <white>" + game.realm().settlementsOfKingdom(kingdom.id()).size()
                + "</white> · klan: <white>" + game.realm().clansOfKingdom(kingdom.id()).size());
        Set<String> enemies = game.realm().enemiesOf(kingdom.id());
        if (!enemies.isEmpty()) {
            lore.add("<red>Savaşta: " + String.join(", ", enemies.stream()
                    .map(id -> game.realm().findKingdom(id).map(k -> Msg.esc(k.name())).orElse(id)).toList()));
        }
        return lore;
    }
}
