package dev.auxy.warbcraft.plugin.command;

import dev.auxy.warbcraft.core.npc.NpcKing;
import dev.auxy.warbcraft.core.party.Party;
import dev.auxy.warbcraft.core.quest.Quest;
import dev.auxy.warbcraft.core.quest.QuestOffer;
import dev.auxy.warbcraft.core.quest.QuestService;
import dev.auxy.warbcraft.core.quest.QuestStatus;
import dev.auxy.warbcraft.core.quest.QuestType;
import dev.auxy.warbcraft.core.troop.TroopType;
import dev.auxy.warbcraft.plugin.Game;
import dev.auxy.warbcraft.plugin.Msg;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /gorev [pano | al <no> | teslim <no> | birak <no>] — NPC krallarin gorevleri. */
public final class QuestCommand extends BaseCommand {

    public QuestCommand(Game game) {
        super(game);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        String playerId = player.getUniqueId().toString();
        if (args.length == 0 || args[0].equalsIgnoreCase("liste")) {
            List<Quest> active = game.quests().activeFor(playerId);
            if (active.isEmpty()) {
                Msg.info(player, "Aktif görevin yok. Bir kralın başkentinde <white>/" + label + " pano</white> yaz.");
                return;
            }
            Msg.info(player, "<gold>Görevlerin</gold>");
            for (Quest quest : active) {
                Duration left = Duration.between(game.now(), quest.deadline());
                Msg.line(player, "  <white>" + quest.offer().id() + "</white> " + describe(quest.offer())
                        + " · " + quest.progress() + "/" + quest.offer().required()
                        + " · kalan " + Math.max(0, left.toHours()) + " saat");
            }
            return;
        }
        String usage = "/" + label + " " + args[0] + " <no>";
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "pano" -> {
                NpcKing king = kingHere(player);
                Msg.info(player, "<gold>" + Msg.esc(king.name()) + "</gold> şu işler için adam arıyor:");
                for (QuestOffer offer : game.quests().board(king.id())) {
                    Msg.line(player, "  <white>" + offer.id() + "</white> " + describe(offer) + " · <yellow>"
                            + offer.rewardDenars() + " dinar</yellow>, +" + offer.rewardRelation() + " ilişki · "
                            + offer.timeLimit().toHours() + " saat");
                }
                Msg.line(player, "  İlişkin: " + game.relations().get(playerId, king.id()) + " · kabul: <white>/" + label + " al <no></white>");
            }
            case "al" -> {
                NpcKing king = kingHere(player);
                QuestService.AcceptOutcome outcome = game.quests().accept(playerId, king.id(), arg(args, 1, usage), game.now());
                if (outcome != QuestService.AcceptOutcome.OK) {
                    throw new CommandError(outcome.message());
                }
                Msg.ok(player, outcome.message() + ".");
            }
            case "teslim" -> handIn(player, arg(args, 1, usage));
            case "birak" -> {
                if (!game.quests().abandon(playerId, arg(args, 1, usage))) {
                    throw new CommandError("Böyle bir aktif görevin yok.");
                }
                Msg.ok(player, "Görevi bıraktın (-" + QuestService.FAILURE_RELATION_PENALTY + " ilişki).");
            }
            default -> throw new CommandError("Kullanım: /" + label + " [liste|pano|al|teslim|birak]");
        }
    }

    private void handIn(Player player, String questId) {
        Quest quest = game.quests().find(player.getUniqueId().toString(), questId)
                .orElseThrow(() -> new CommandError("Böyle bir aktif görevin yok."));
        NpcKing king = game.data().kings().get(quest.offer().kingId());
        if (king == null || !kingHere(player).id().equals(king.id())) {
            throw new CommandError("Teslimat görevi veren kralın başkentinde yapılır.");
        }
        if (game.now().isAfter(quest.deadline())) {
            throw new CommandError("Bu görevin süresi dolmuş.");
        }
        switch (quest.offer().type()) {
            case DELIVER_GOODS -> {
                Material item = game.data().goodItems().get(quest.offer().target());
                int taken = Items.take(player, item, Math.min(quest.remaining(), Items.count(player, item)));
                if (taken == 0) {
                    throw new CommandError("Üzerinde istenen mal yok.");
                }
                Msg.info(player, taken + " birim teslim ettin.");
                game.progressQuest(player, QuestType.DELIVER_GOODS, quest.offer().target(), taken);
            }
            case RAISE_TROOPS -> {
                int minTier = Integer.parseInt(quest.offer().target());
                Party party = game.profile(player).party();
                Party garrison = game.garrisons().get(king.seatId());
                int handed = 0;
                for (String troopId : new ArrayList<>(party.roster().keySet())) {
                    if (quest.status() != QuestStatus.ACTIVE) {
                        break;
                    }
                    TroopType type = game.tree().get(troopId);
                    int give = Math.min(party.roster().get(troopId).healthy(), quest.remaining());
                    if (type.tier() < minTier || give <= 0) {
                        continue;
                    }
                    party.removeTroops(troopId, give);
                    if (garrison != null) {
                        garrison.forceAddTroops(troopId, give, 0);
                    }
                    handed += give;
                    game.progressQuest(player, QuestType.RAISE_TROOPS, Integer.toString(type.tier()), give);
                }
                if (handed == 0) {
                    throw new CommandError("Partinde kademe " + minTier + " veya üstü sağlam asker yok.");
                }
                Msg.info(player, handed + " asker garnizona katıldı.");
            }
            default -> throw new CommandError("Bu görev teslimatla değil, sahada tamamlanır.");
        }
    }

    private NpcKing kingHere(Player player) {
        return game.kingAt(player.getLocation())
                .orElseThrow(() -> new CommandError("Burada görev verecek bir kral yok; bir krallığın başkentine git."));
    }

    private String describe(QuestOffer offer) {
        String what = switch (offer.type()) {
            case HUNT_BANDITS -> offer.required() + " haydut öldür";
            case DELIVER_GOODS -> offer.required() + " " + Msg.esc(game.data().goods().containsKey(offer.target())
                    ? game.data().goods().get(offer.target()).name() : offer.target()) + " getir";
            case RAISE_TROOPS -> "kademe " + offer.target() + "+ " + offer.required() + " asker getir";
            case WAR_SERVICE -> Msg.esc(game.realm().findKingdom(offer.target()).map(k -> k.name()).orElse(offer.target()))
                    + " savaşçılarından " + offer.required() + " tanesini yen";
        };
        return "<gold>" + offer.type().displayName() + "</gold>: " + what;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return complete(List.of("liste", "pano", "al", "teslim", "birak"), args[0]);
        }
        if (args.length == 2 && sender instanceof Player player) {
            List<String> ids = new ArrayList<>();
            if (args[0].equalsIgnoreCase("al")) {
                game.kingAt(player.getLocation())
                        .ifPresent(k -> game.quests().board(k.id()).forEach(o -> ids.add(o.id())));
            } else {
                game.quests().activeFor(player.getUniqueId().toString()).forEach(q -> ids.add(q.offer().id()));
            }
            return complete(ids, args[1]);
        }
        return List.of();
    }
}
