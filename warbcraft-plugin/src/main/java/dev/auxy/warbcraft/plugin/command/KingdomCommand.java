package dev.auxy.warbcraft.plugin.command;

import dev.auxy.warbcraft.core.faction.Clan;
import dev.auxy.warbcraft.core.faction.Diplomacy;
import dev.auxy.warbcraft.core.faction.Kingdom;
import dev.auxy.warbcraft.core.faction.Settlement;
import dev.auxy.warbcraft.core.npc.NpcKing;
import dev.auxy.warbcraft.core.party.Party;
import dev.auxy.warbcraft.plugin.Game;
import dev.auxy.warbcraft.plugin.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * /krallik [liste | bilgi <id> | kur <kultur> <ad> | katil <id> | davet <klan> | ayril | savas <id> | baris <id>]
 */
public final class KingdomCommand extends BaseCommand {

    /** krallik -> davet edilen klanlar. */
    private final Map<String, Set<String>> invites = new HashMap<>();
    /** baris teklif eden krallik -> teklif edilen krallik. */
    private final Map<String, Set<String>> peaceOffers = new HashMap<>();

    public KingdomCommand(Game game) {
        super(game);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("liste")) {
            list(sender);
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("bilgi")) {
            info(sender, kingdom(arg(args, 1, "/" + label + " bilgi <krallik>")));
            return;
        }
        Player player = player(sender);
        switch (sub) {
            case "kur" -> found(player, label, args);
            case "katil" -> join(player, kingdom(arg(args, 1, "/" + label + " katil <krallik>")));
            case "davet" -> {
                Kingdom mine = ruledKingdom(player);
                Clan clan = game.realm().findClan(arg(args, 1, "/" + label + " davet <klan>"))
                        .orElseThrow(() -> new CommandError("Böyle bir klan yok."));
                invites.computeIfAbsent(mine.id(), k -> new HashSet<>()).add(clan.id());
                Msg.ok(player, Msg.esc(clan.name()) + " klanı krallığına davet edildi.");
                Player leader = Bukkit.getPlayer(UUID.fromString(clan.leaderId()));
                if (leader != null) {
                    Msg.info(leader, "<gold>" + Msg.esc(mine.name()) + "</gold> seni sancağı altına çağırıyor: <white>/krallik katil "
                            + mine.id() + "</white>");
                }
            }
            case "ayril" -> {
                Clan clan = ledClan(player);
                if (clan.kingdomId() == null) {
                    throw new CommandError("Zaten bağımsızsın.");
                }
                Kingdom kingdom = game.realm().kingdom(clan.kingdomId());
                if (kingdom.rulerId().equals(clan.leaderId())) {
                    throw new CommandError("Hükümdar kendi krallığından ayrılamaz.");
                }
                clan.setKingdomId(null);
                Msg.ok(player, Msg.esc(kingdom.name()) + " krallığından ayrıldın; artık bağımsızsın.");
            }
            case "savas" -> {
                Kingdom mine = ruledKingdom(player);
                Kingdom target = kingdom(arg(args, 1, "/" + label + " savas <krallik>"));
                Diplomacy.Outcome outcome = game.diplomacy().declareWar(mine.id(), target.id(), game.now());
                if (outcome != Diplomacy.Outcome.OK) {
                    throw new CommandError(outcome.message());
                }
                Bukkit.getServer().broadcast(Component.text(
                        "⚔ " + mine.name() + ", " + target.name() + " krallığına savaş ilan etti!",
                        NamedTextColor.RED));
                game.refreshBoards();
            }
            case "baris" -> peace(player, kingdom(arg(args, 1, "/" + label + " baris <krallik>")));
            default -> throw new CommandError("Kullanım: /" + label + " [liste|bilgi|kur|katil|davet|ayril|savas|baris]");
        }
    }

    private void list(CommandSender sender) {
        Msg.info(sender, "<gold>Krallıklar</gold>");
        for (Kingdom kingdom : game.kingdomsSorted()) {
            int fiefs = game.realm().settlementsOfKingdom(kingdom.id()).size();
            Set<String> enemies = game.realm().enemiesOf(kingdom.id());
            String color = kingdom.npc() ? "aqua" : "yellow";
            Msg.line(sender, "  <" + color + ">" + Msg.esc(kingdom.name()) + "</" + color + "> <dark_gray>["
                    + kingdom.id() + "]</dark_gray> · " + fiefs + " yerleşim"
                    + (enemies.isEmpty() ? "" : " · <red>savaşta: " + String.join(", ", enemies) + "</red>"));
        }
    }

    private void info(CommandSender sender, Kingdom kingdom) {
        String ruler = kingdom.npc()
                ? Msg.esc(game.data().kings().get(kingdom.rulerId()).name())
                : Msg.esc(String.valueOf(Bukkit.getOfflinePlayer(UUID.fromString(kingdom.rulerId())).getName()));
        Msg.info(sender, "<gold>" + Msg.esc(kingdom.name()) + "</gold> · hükümdar " + ruler + " · kültür "
                + Msg.esc(game.data().cultureNames().getOrDefault(kingdom.culture(), kingdom.culture())));
        for (Clan clan : game.realm().clansOfKingdom(kingdom.id())) {
            Party lordParty = game.lordParties().get(clan.id());
            String army = lordParty == null ? "" : " · lord ordusu " + lordParty.totalCount();
            Msg.line(sender, "  Klan " + Msg.esc(clan.name()) + " · kademe " + clan.tier() + army);
        }
        for (Settlement settlement : game.realm().settlementsOfKingdom(kingdom.id())) {
            Party garrison = game.garrisons().get(settlement.id());
            Msg.line(sender, "  " + Msg.esc(settlement.name()) + " <dark_gray>(" + settlement.type().name().toLowerCase(Locale.ROOT)
                    + ")</dark_gray> · refah " + settlement.prosperity()
                    + (garrison == null ? "" : " · garnizon " + garrison.totalCount()));
        }
    }

    private void found(Player player, String label, String[] args) {
        String usage = "/" + label + " kur <kultur> <ad>";
        String culture = arg(args, 1, usage);
        String name = joinFrom(args, 2, usage);
        if (!game.data().cultureNames().containsKey(culture)) {
            throw new CommandError("Kültürler: " + String.join(", ", game.data().cultureNames().keySet()));
        }
        Clan clan = ledClan(player);
        if (clan.kingdomId() != null) {
            throw new CommandError("Önce mevcut krallığından ayrılmalısın.");
        }
        int needed = game.data().settings().kingdomMinClanTier();
        if (clan.tier() < needed) {
            throw new CommandError("Krallık kurmak için klan kademen en az " + needed + " olmalı.");
        }
        String id = "o_" + Game.slug(name);
        if (name.length() > 32 || id.length() <= 2 || game.realm().findKingdom(id).isPresent()) {
            throw new CommandError("Bu ad kullanılamaz.");
        }
        game.realm().addKingdom(new Kingdom(id, name, culture, clan.leaderId(), false));
        clan.setKingdomId(id);
        Bukkit.getServer().broadcast(Component.text(
                "👑 " + clan.name() + " klanı yeni bir krallık kurdu: " + name,
                NamedTextColor.GOLD));
    }

    private void join(Player player, Kingdom kingdom) {
        Clan clan = ledClan(player);
        if (clan.kingdomId() != null) {
            throw new CommandError("Önce mevcut krallığından ayrılmalısın.");
        }
        if (kingdom.npc()) {
            NpcKing king = game.data().kings().get(kingdom.rulerId());
            int relation = game.relations().get(player.getUniqueId().toString(), king.id());
            int needed = game.data().settings().vassalMinRelation();
            if (relation < needed) {
                throw new CommandError(Msg.esc(king.name()) + " sana henüz güvenmiyor (ilişki " + relation + "/" + needed
                        + "). Görevlerini yaparak güvenini kazan.");
            }
        } else if (!invites.getOrDefault(kingdom.id(), Set.of()).contains(clan.id())) {
            throw new CommandError("Bu krallıktan davetin yok.");
        }
        clan.setKingdomId(kingdom.id());
        invites.getOrDefault(kingdom.id(), new HashSet<>()).remove(clan.id());
        Msg.ok(player, "<gold>" + Msg.esc(kingdom.name()) + "</gold> sancağı altına girdin.");
    }

    private void peace(Player player, Kingdom target) {
        Kingdom mine = ruledKingdom(player);
        if (!game.realm().atWar(mine.id(), target.id())) {
            throw new CommandError("Bu krallıkla savaşta değilsin.");
        }
        boolean accepted;
        if (target.npc()) {
            // NPC kral, kendisiyle arasi kotu olmayan hukumdarlarla barisir.
            NpcKing king = game.data().kings().get(target.rulerId());
            accepted = game.relations().get(player.getUniqueId().toString(), king.id()) >= 0;
            if (!accepted) {
                throw new CommandError(Msg.esc(king.name()) + " barış teklifini reddetti (ilişkin negatif).");
            }
        } else {
            accepted = peaceOffers.getOrDefault(target.id(), Set.of()).contains(mine.id());
            if (!accepted) {
                peaceOffers.computeIfAbsent(mine.id(), k -> new HashSet<>()).add(target.id());
                Msg.ok(player, "Barış teklifi gönderildi; karşı hükümdar da <white>/krallik baris " + mine.id() + "</white> yazmalı.");
                return;
            }
            peaceOffers.get(target.id()).remove(mine.id());
        }
        game.diplomacy().makePeace(mine.id(), target.id());
        Bukkit.getServer().broadcast(Component.text(
                "🕊 " + mine.name() + " ile " + target.name() + " barış yaptı.",
                NamedTextColor.GREEN));
        game.refreshBoards();
    }

    private Kingdom kingdom(String id) {
        return game.realm().findKingdom(id).orElseThrow(() -> new CommandError("Böyle bir krallık yok: " + Msg.esc(id)));
    }

    private Clan ledClan(Player player) {
        Clan clan = game.clanOf(player.getUniqueId()).orElseThrow(() -> new CommandError("Önce bir klan kurmalısın: /klan kur <ad>"));
        if (!clan.leaderId().equals(player.getUniqueId().toString())) {
            throw new CommandError("Bunu yalnızca klan lideri yapabilir.");
        }
        return clan;
    }

    private Kingdom ruledKingdom(Player player) {
        Clan clan = ledClan(player);
        if (clan.kingdomId() == null) {
            throw new CommandError("Bir krallığın yok.");
        }
        Kingdom kingdom = game.realm().kingdom(clan.kingdomId());
        if (!kingdom.rulerId().equals(clan.leaderId())) {
            throw new CommandError("Bunu yalnızca hükümdar yapabilir.");
        }
        return kingdom;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return complete(List.of("liste", "bilgi", "kur", "katil", "davet", "ayril", "savas", "baris"), args[0]);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "bilgi", "katil", "savas", "baris" ->
                        complete(game.realm().kingdoms().stream().map(Kingdom::id).toList(), args[1]);
                case "kur" -> complete(game.data().cultureNames().keySet(), args[1]);
                case "davet" -> complete(game.realm().clans().stream().filter(c -> !c.npc()).map(Clan::id).toList(), args[1]);
                default -> List.of();
            };
        }
        return List.of();
    }
}
